"""Read-only analysis of exported intake reviews. Never edits experimental data.

Input: inventory.json created from the user-supplied archives; output: a workspace
analysis directory. Counts are native time bins, not independently resolved gulps.
"""
import argparse
import collections
import csv
import hashlib
import io
import json
import math
from pathlib import Path
import re
import statistics
import zipfile


def number(value):
    try:
        x = float(value)
        return x if math.isfinite(x) else None
    except (TypeError, ValueError):
        return None


def median(values):
    values = [v for v in values if v is not None]
    return statistics.median(values) if values else None


def quantiles(values):
    values = sorted(v for v in values if v is not None)
    def at(q):
        if not values:
            return None
        index = q * (len(values) - 1)
        a = int(index)
        return values[a] + (values[min(a + 1, len(values) - 1)] - values[a]) * (index - a)
    return {"n": len(values), "min": at(0), "q25": at(.25), "median": at(.5), "q75": at(.75), "max": at(1)}


def analyze(inventory, output):
    output.mkdir(parents=True, exist_ok=True)
    cohort = {"experiments": [], "changes": [], "capillaries": [], "integrityIssues": [],
              "unit": "native time bins; pixel displacement, not microlitres", "source": str(inventory)}
    records = json.loads(inventory.read_text(encoding="utf-8-sig"))
    for record in sorted(records, key=lambda x: int(re.match(r"\d+", x["archive"])[0])):
        source = Path(record["path"])
        digest = hashlib.sha256(source.read_bytes()).hexdigest().upper()
        if digest != record["sha256"]:
            raise ValueError(f"Archive changed since inventory: {source}")
        id_ = re.match(r"\d+", source.name)[0].zfill(3)
        folder = output / "archives" / id_
        folder.mkdir(parents=True, exist_ok=True)
        chemical = "Denatonium" if "Dénatonium" in record["experiment"] else "Nicotine" if "Nicotine" in record["experiment"] else "Caffeine"
        experiment = {"id": id_, "archive": source.name, "path": record["experiment"], "chemical": chemical,
                      "concentration": re.search(r"cam\d+_([^\\/]+)", record["experiment"])[1],
                      "sha256": digest, "counts": {}, "flyCounts": {}, "controlCounts": {}}
        annotations = []
        with zipfile.ZipFile(source) as archive:
            readme = archive.read("README.txt").decode("utf-8-sig")
            controls = set(re.findall(r"(?m)^(\d+[LR]) \(no-fly control\)", readme))
            for entry in archive.infolist():
                if not re.fullmatch(r"README\.txt|correlation-\d+[LR]\.csv|kymograph-\d+[LR]\.png", entry.filename):
                    raise ValueError(f"Unexpected archive entry: {entry.filename}")
                (folder / entry.filename).write_bytes(archive.read(entry))
            for entry in archive.infolist():
                match = re.fullmatch(r"correlation-(\d+[LR])\.csv", entry.filename)
                if not match:
                    continue
                cap = match[1]
                text = archive.read(entry).decode("utf-8-sig")
                rows = list(csv.DictReader((line for line in text.splitlines() if not line.startswith("#")), delimiter=";"))
                control = cap in controls
                meta = "\n".join(line for line in text.splitlines() if line.startswith("#"))
                parsed = []
                for i, row in enumerate(rows):
                    r = {"frame": int(row["frame"]), "time": float(row["elapsed_min"]), "raw": number(row["raw_px"]),
                         "auto": number(row["automatic_net_intake_px"]), "before": number(row["detector_net_before_edits_px"]),
                         "edited": number(row["edited_net_intake_px"]), "rate": number(row["excess_px_per_min"]),
                         "threshold": number(row["chosen_threshold_px_per_min"]), "autoThreshold": number(row["automatic_threshold_px_per_min"]),
                         "reliable": row["reliable"] == "true", "reason": row["reason"], "override": row["manual_override"] == "true",
                         "action": row["edit_action"], "bottom": number(row["intake_bottom_px"]),
                         "fullAmplitude": number(row["candidate_amplitude_px"])}
                    if r["frame"] != i or i and r["time"] <= parsed[-1]["time"]:
                        cohort["integrityIssues"].append(f"{id_}/{cap}: native frame/time mismatch")
                    parsed.append(r)
                actions = collections.Counter(r["action"] for r in parsed)
                auto_total = sum(r["auto"] or 0 for r in parsed)
                edited_total = sum(r["edited"] or 0 for r in parsed)
                c = {"experiment": id_, "cap": cap, "control": control, "rows": len(parsed),
                     "duration": parsed[-1]["time"], "counts": dict(actions), "autoTotal": auto_total, "editedTotal": edited_total,
                     "relativeChange": (edited_total / auto_total - 1) if auto_total > 0 else None,
                     "autoPositive": sum((r["auto"] or 0) > 0 for r in parsed),
                     "editedPositive": sum((r["edited"] or 0) > 0 for r in parsed),
                     "threshold": parsed[-1]["threshold"], "thresholdAdjusted": any(r["threshold"] != r["autoThreshold"] for r in parsed),
                     "localEvaporationUncertain": "local evaporation uncertain" in meta,
                     "series": [[r[k] for k in ("time", "raw", "auto", "edited", "rate", "reliable")] for r in parsed]}
                cohort["capillaries"].append(c)
                for r in parsed:
                    if not r["override"]:
                        continue
                    near = [k for k in range(max(1, r["frame"] - 1), min(len(parsed), r["frame"] + 2))
                            if k != r["frame"] and (parsed[k]["auto"] or 0) > 0]
                    cause = "unreliable" if not r["reliable"] else "below_rate_threshold" if r["rate"] is not None and r["threshold"] is not None and r["rate"] <= r["threshold"] else "above_rate_threshold"
                    change = dict(r, experiment=id_, cap=cap, control=control, nearbyAutomatic=near, category=cause)
                    cohort["changes"].append(change)
                    annotations.append(f"{cap};{r['frame']};{r['action']};{r['edited'] or 0}")
                for key in ("counts", "controlCounts" if control else "flyCounts"):
                    experiment[key] = dict(collections.Counter(experiment[key]) + actions)
        (folder / "annotations.txt").write_text("\n".join(annotations) + "\n", encoding="utf-8")
        caps = [c for c in cohort["capillaries"] if c["experiment"] == id_]
        flies = [c for c in caps if not c["control"]]
        control_caps = [c for c in caps if c["control"]]
        additions = [c for c in cohort["changes"] if c["experiment"] == id_ and c["action"] == "added"]
        experiment.update(capillaries=len(caps), flyCapillaries=len(flies), controls=len(control_caps),
                          autoPositive=sum(c["autoPositive"] for c in flies), editedPositive=sum(c["editedPositive"] for c in flies),
                          autoControlPositive=sum(c["autoPositive"] for c in control_caps), editedControlPositive=sum(c["editedPositive"] for c in control_caps),
                          medianCapillaryChange=median(c["relativeChange"] for c in flies),
                          addedCategories=dict(collections.Counter(c["category"] for c in additions)),
                          addedNearAutomatic=sum(bool(c["nearbyAutomatic"]) for c in additions),
                          duration=quantiles(c["duration"] for c in caps))
        cohort["experiments"].append(experiment)
    additions = [c for c in cohort["changes"] if c["action"] == "added"]
    resized = [c for c in cohort["changes"] if c["action"] == "resized"]
    fly_caps = [c for c in cohort["capillaries"] if not c["control"]]
    cohort["summary"] = {
        "experiments": len(cohort["experiments"]), "capillaries": len(cohort["capillaries"]),
        "flyCapillaries": len(fly_caps), "controls": sum(c["control"] for c in cohort["capillaries"]),
        "counts": dict(sum((collections.Counter(e["counts"]) for e in cohort["experiments"]), collections.Counter())),
        "addedCategories": dict(collections.Counter(c["category"] for c in additions)),
        "flyAddedCategories": dict(collections.Counter(c["category"] for c in additions if not c["control"])),
        "addedNearAutomatic": sum(bool(c["nearbyAutomatic"]) for c in additions),
        "addedNegativeRate": sum(c["rate"] is not None and c["rate"] < 0 for c in additions),
        "additionSizes": quantiles(c["edited"] for c in additions),
        "resizedUp": sum(c["edited"] > c["before"] for c in resized), "resizedDown": sum(c["edited"] < c["before"] for c in resized),
        "capillaryRelativeChange": quantiles(c["relativeChange"] for c in fly_caps),
        "relativeChangeExcludedZeroBaseline": sum(c["relativeChange"] is None for c in fly_caps),
        "autoFlyPositiveBins": sum(c["autoPositive"] for c in fly_caps), "editedFlyPositiveBins": sum(c["editedPositive"] for c in fly_caps),
        "autoControlPositiveBins": sum(c["autoPositive"] for c in cohort["capillaries"] if c["control"]),
        "editedControlPositiveBins": sum(c["editedPositive"] for c in cohort["capillaries"] if c["control"]),
        "thresholdAdjustedCaps": sum(c["thresholdAdjusted"] for c in cohort["capillaries"]),
        "localUncertainCaps": sum(c["localEvaporationUncertain"] for c in cohort["capillaries"]),
        "chemicals": dict(collections.Counter(e["chemical"] for e in cohort["experiments"]))}
    (output / "cohort.json").write_text(json.dumps(cohort, ensure_ascii=False, allow_nan=False), encoding="utf-8")
    print(json.dumps(cohort["summary"], ensure_ascii=False, indent=2))


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("inventory", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    analyze(args.inventory, args.output)
