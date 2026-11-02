"""Group-level context and original-image crops for the cohort review.

Visual criteria supplied by the user remain qualitative: this script does not
label a trajectory as feeding or a dark band as glass/tape automatically.
"""
import base64
import csv
import io
import json
import math
from pathlib import Path
import statistics
import sys
from PIL import Image, ImageDraw


def read_rows(root, experiment, cap):
    with (root / "archives" / experiment / f"correlation-{cap}.csv").open(encoding="utf-8-sig") as stream:
        rows = list(csv.DictReader((line for line in stream if not line.startswith("#")), delimiter=";"))
    return rows


def finite(value):
    x = float(value)
    return x if math.isfinite(x) else None


def median(values):
    values = [v for v in values if v is not None]
    return statistics.median(values) if values else None


def main(root):
    root = Path(root)
    cohort = json.loads((root / "cohort.json").read_text(encoding="utf-8"))
    runs = []
    for cap in cohort["capillaries"]:
        rows = read_rows(root, cap["experiment"], cap["cap"])
        positive = [i for i, row in enumerate(rows) if float(row["edited_net_intake_px"]) > 0]
        blocks = []
        for i in positive:
            if blocks and i == blocks[-1][-1] + 1:
                blocks[-1].append(i)
            else:
                blocks.append([i])
        for block in blocks:
            first, last = block[0], block[-1]
            added = sum(rows[i]["edit_action"] == "added" for i in block)
            if len(block) < 5 or not added or cap["control"]:
                continue
            quiet_before = [finite(rows[i]["excess_px_per_min"]) for i in range(max(1, first - 12), first)
                            if float(rows[i]["edited_net_intake_px"]) == 0]
            quiet_after = [finite(rows[i]["excess_px_per_min"]) for i in range(last + 1, min(len(rows), last + 13))
                           if float(rows[i]["edited_net_intake_px"]) == 0]
            dt = float(rows[last]["elapsed_min"]) - float(rows[first - 1]["elapsed_min"])
            signed = sum((float(rows[i]["raw_px"]) - float(rows[i - 1]["raw_px"]))
                         - float(rows[i]["evaporation_increment_px"]) for i in block)
            runs.append({"experiment": cap["experiment"], "cap": cap["cap"], "first": first, "last": last,
                         "bins": len(block), "addedBins": added, "duration": dt,
                         "signedNetRate": signed / dt, "quietBeforeRate": median(quiet_before),
                         "quietAfterRate": median(quiet_after), "beforeSamples": sum(v is not None for v in quiet_before),
                         "afterSamples": sum(v is not None for v in quiet_after)})
    cohort["runs"] = runs
    cohort["summary"]["editedRunsAtLeastFiveBins"] = len(runs)
    cohort["summary"]["editedRunsFiveToTenBins"] = sum(r["bins"] <= 10 for r in runs)
    cohort["summary"]["addedBinsInFiveToTenRuns"] = sum(r["addedBins"] for r in runs if r["bins"] <= 10)
    # Representative cases, not a random accuracy sample.
    cases = [("006", "3L", 74, 74, "Uncertain tracking at a sustained step"),
             ("007", "7L", 64, 64, "Above the rate threshold, absent in the automatic result"),
             ("015", "1L", 49, 55, "Several small movements: inspect the whole trajectory"),
             ("012", "3R", 36, 36, "Edited amplitude is smaller than the fitted automatic amplitude"),
             ("021", "5R", 89, 89, "Large manually added displacement with uncertain tracking"),
             ("147", "8L", 3, 3, "An early step with little preceding context"),
             ("006", "6R", 13, 13, "An added bin with a negative accepted residual rate"),
             ("017", "9L", 139, 144, "Removed detections in a no-fly control")]
    if runs:
        best = sorted((r for r in runs if 5 <= r["bins"] <= 10), key=lambda r: -r["addedBins"])
        if best:
            r = best[0]
            cases.insert(2, (r["experiment"], r["cap"], r["first"], r["last"], "Edited consecutive run: compare its slope with the flanks"))
    examples = []
    for exp, cap, first, last, title in cases:
        rows = read_rows(root, exp, cap)
        start, stop = max(0, first - 10), min(len(rows), last + 11)
        original = Image.open(root / "archives" / exp / f"kymograph-{cap}.png").convert("RGB")
        positions = [float(rows[i]["raw_px"]) for i in range(start, stop)]
        for i in range(first, last + 1):
            row = rows[i]
            bottom = finite(row["intake_bottom_px"])
            if bottom is None:
                bottom = finite(row["fitted_meniscus_px"])
            if bottom is not None:
                positions.extend([bottom, bottom - float(row["candidate_amplitude_px"])])
        positions = [v for v in positions if math.isfinite(v)]
        top = max(0, math.floor(min(positions)) - 7)
        bottom_row = min(original.height, math.ceil(max(positions)) + 8)
        crop = original.crop((start, top, stop, bottom_row)).resize(((stop - start) * 16, (bottom_row - top) * 7), Image.Resampling.NEAREST)
        draw = ImageDraw.Draw(crop)
        def x(i): return (i - start + .5) * 16
        def y(v): return (v - top) * 7
        for i in range(start + 1, stop):
            a, b = finite(rows[i - 1]["raw_px"]), finite(rows[i]["raw_px"])
            if a is not None and b is not None:
                draw.line((x(i - 1), y(a), x(i), y(b)), fill="orange", width=1)
        for i in range(start, stop):
            row = rows[i]
            position = finite(row["intake_bottom_px"])
            auto = float(row["automatic_net_intake_px"])
            if auto > 0:
                fitted = float(row["fitted_meniscus_px"])
                draw.line((x(i) - 2, y(fitted), x(i) - 2, y(fitted - auto - float(row["evaporation_increment_px"]))), fill="crimson", width=2)
            if float(row["edited_net_intake_px"]) > 0 and position is not None:
                draw.line((x(i) + 2, y(position), x(i) + 2, y(position - float(row["candidate_amplitude_px"]))), fill="royalblue", width=2)
        buffer = io.BytesIO()
        crop.save(buffer, format="PNG")
        examples.append({"experiment": exp, "cap": cap, "first": first, "last": last, "title": title,
                         "caption": f"Native columns {start}–{stop - 1}; image rows {top}–{bottom_row - 1}. Orange: accepted level; red: automatic intake; blue: edited intake. Display enlarged independently in time and level.",
                         "image": "data:image/png;base64," + base64.b64encode(buffer.getvalue()).decode("ascii")})
        if exp == "017" and cap == "9L":
            examples[-1]["fullImage"] = "data:image/png;base64," + base64.b64encode(
                    (root / "archives" / exp / f"kymograph-{cap}.png").read_bytes()).decode("ascii")
    cohort["examples"] = examples
    (root / "cohort.json").write_text(json.dumps(cohort, ensure_ascii=False, allow_nan=False), encoding="utf-8")
    print(json.dumps({"runs": runs, "examples": [{k: v for k, v in e.items() if k not in ("image", "fullImage")} for e in examples]}, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main(sys.argv[1])
