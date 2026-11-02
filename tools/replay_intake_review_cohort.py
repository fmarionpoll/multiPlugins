"""Run independent read-only detector probes and verify sources against exports."""
import concurrent.futures
import csv
import json
import math
from pathlib import Path
import subprocess
import sys
import time


def equal(a, b):
    if a is None or b is None:
        return a is None and b is None
    a, b = float(a), float(b)
    return (not math.isfinite(a) and not math.isfinite(b)) or abs(a - b) <= 1e-8


def main(root):
    root = Path(root).resolve()
    cohort = json.loads((root / "cohort.json").read_text(encoding="utf-8"))
    workspace = Path.cwd()
    cp = str(workspace / "multiCAFE/target/classes") + ";" + str(workspace / "multiCAFE/target") + ";" + (workspace / "multiCAFE/target/neighbour-classpath.txt").read_text().strip()
    def run(experiment):
        id_ = experiment["id"]
        output = root / "replay" / id_
        output.mkdir(parents=True, exist_ok=True)
        annotations = root / "archives" / id_ / "annotations.txt"
        start = time.monotonic()
        args = ["java", "-Xmx512m", "-Djava.awt.headless=true", "-Dintake.local=true", "-Dintake.windows.all=true",
                f"-Dintake.annotations={annotations}", "-cp", cp, "IntakeMissInvestigation", str(output), str(Path(experiment["path"]).parent)]
        with (output / "replay.log").open("w", encoding="utf-8") as log:
            completed = subprocess.run(args, stdout=log, stderr=subprocess.STDOUT, timeout=180)
        result = {"id": id_, "exitCode": completed.returncode, "seconds": round(time.monotonic() - start, 1), "caps": []}
        if completed.returncode == 0:
            for cap in [c for c in cohort["capillaries"] if c["experiment"] == id_]:
                archive_csv = root / "archives" / id_ / f"correlation-{cap['cap']}.csv"
                with archive_csv.open(encoding="utf-8-sig") as stream:
                    rows = list(csv.DictReader((line for line in stream if not line.startswith("#")), delimiter=";"))
                path = output / f"{Path(experiment['path']).parent.name}-{cap['cap']}.json"
                issues = []
                if not path.exists():
                    issues.append("source preview unavailable")
                else:
                    p = json.loads(path.read_text(encoding="utf-8"))
                    if len(rows) != len(p["raw"]):
                        issues.append("native length changed")
                    else:
                        if not equal(rows[0]["automatic_threshold_px_per_min"], p["threshold"]):
                            issues.append("automatic threshold changed")
                        for i, row in enumerate(rows):
                            if not equal(row["raw_px"], p["raw"][i]): issues.append("accepted levels changed"); break
                            if not equal(row["elapsed_min"], p["minutes"][i]): issues.append("timestamps changed"); break
                            if (row["reliable"] == "true") != p["valid"][i]: issues.append("quality flags changed"); break
                            if not equal(row["automatic_net_intake_px"], p["net"][i]): issues.append("automatic intake mismatch"); break
                result["caps"].append({"cap": cap["cap"], "matched": not issues, "issues": issues})
        return result
    results = []
    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as executor:
        futures = {executor.submit(run, e): e for e in cohort["experiments"]}
        for future in concurrent.futures.as_completed(futures):
            try:
                result = future.result()
            except Exception as error:
                result = {"id": futures[future]["id"], "error": str(error), "caps": []}
            results.append(result)
            print(json.dumps({"id": result["id"], "matched": sum(c["matched"] for c in result["caps"]), "total": len(result["caps"]), "seconds": result.get("seconds"), "error": result.get("error")}), flush=True)
    (root / "replay-validation.json").write_text(json.dumps(results, indent=2), encoding="utf-8")


if __name__ == "__main__":
    main(sys.argv[1])
