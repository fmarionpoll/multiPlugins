"""Summarize detector probes, using only previews that reproduce the archive."""
import collections
import csv
import json
from pathlib import Path
import sys


def main(root):
    root = Path(root)
    cohort = json.loads((root / "cohort.json").read_text(encoding="utf-8"))
    validation = json.loads((root / "replay-validation.json").read_text(encoding="utf-8"))
    matched = {(e["id"], c["cap"]) for e in validation for c in e["caps"] if c["matched"]}
    skipped = [{"experiment": e["id"], **c} for e in validation for c in e["caps"] if not c["matched"]]
    rows, windows = [], {}
    for experiment in cohort["experiments"]:
        id_ = experiment["id"]
        folder = root / "replay" / id_
        with (folder / "threshold-sweep.csv").open(encoding="utf-8-sig") as stream:
            for row in csv.DictReader(stream, delimiter=";"):
                if (id_, row["capillary"]) in matched:
                    rows.append({"id": id_, "cap": row["capillary"], "control": row["flies"] == "0",
                                 **{k: float(v) for k, v in row.items() if k not in ("experiment", "capillary", "flies")}})
        with (folder / "windows.csv").open(encoding="utf-8-sig") as stream:
            for row in csv.DictReader(stream, delimiter=";"):
                if row["first"] == row["last"]:
                    windows[(id_, row["capillary"], int(row["first"]))] = row
    details = []
    for change in cohort["changes"]:
        change["replayMatched"] = (change["experiment"], change["cap"]) in matched
        if change["action"] != "added" or not change["replayMatched"]:
            continue
        window = windows.get((change["experiment"], change["cap"], change["frame"]))
        change["singleGate"] = window["gate"] if window else "unavailable"
        change["singleLimit"] = float(window["limit"]) if window else None
        # Passing a local proposal is not sufficient for a jointly fitted event.
        change["singlePersistence"] = float(window["persistent"]) if window and window["persistent"] != "NaN" else None
        details.append(change)
    sweeps = []
    for factor in (1., .8, .6):
        selected = [r for r in rows if r["threshold_factor"] == factor]
        def total(key, subset=selected):
            return int(sum(r[key] for r in subset))
        controls = [r for r in selected if r["control"]]
        summary = {"factor": factor, "addedExact": total("added_bins_recovered_exact"),
                   "addedWithinOne": total("added_bins_recovered_within_one"), "annotatedAdded": total("annotated_added_bins"),
                   "newUneditedBins": total("extra_unedited_bins"), "newControlBins": total("extra_unedited_bins", controls),
                   "knownRemovedStillPresent": total("removed_bins_still_detected"),
                   "baselineBinsLost": total("baseline_positive_bins_lost"),
                   "positiveOnZeroOverrides": total("positive_bins_with_zero_edited_reference"),
                   "perExperiment": []}
        for experiment in cohort["experiments"]:
            selection = [r for r in selected if r["id"] == experiment["id"]]
            summary["perExperiment"].append({"id": experiment["id"], "addedExact": total("added_bins_recovered_exact", selection),
                                              "addedReference": total("annotated_added_bins", selection),
                                              "newControlBins": total("extra_unedited_bins", [r for r in selection if r["control"]]),
                                              "newUneditedBins": total("extra_unedited_bins", selection)})
        sweeps.append(summary)
    cohort["probes"] = {"matchedCaps": len(matched), "excluded": skipped,
                        "matchedAddedBins": len(details), "singleBinGates": dict(collections.Counter(c["singleGate"] for c in details)),
                        "sweeps": sweeps}
    # JSON emitted by Java uses NaN for unavailable numerical diagnostics.
    (root / "cohort.json").write_text(json.dumps(cohort, ensure_ascii=False, allow_nan=False), encoding="utf-8")
    print(json.dumps({"matchedCaps": len(matched), "excludedCaps": len(skipped), "matchedAddedBins": len(details),
                      "singleBinGates": cohort["probes"]["singleBinGates"],
                      "sweeps": [{k: v for k, v in s.items() if k != "perExperiment"} for s in sweeps]}, indent=2))


if __name__ == "__main__":
    main(sys.argv[1])
