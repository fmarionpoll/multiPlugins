"""Read-only exploratory gulp benchmark. Requires numpy and Pillow.

Outputs go only to --output; never writes experiment files. This is not a
production detector. Native, regularly sampled green kymographs only.
"""
import argparse
import csv
import json
from pathlib import Path
import xml.etree.ElementTree as ET

import numpy as np
from PIL import Image


def measures(path):
    result, section = {}, None
    for row in csv.reader(path.read_text(encoding="utf-8-sig").splitlines(), delimiter=";"):
        if not row:
            continue
        if row[0] == "#":
            section = row[1] if len(row) > 1 else None
            continue
        if section in ("TOPRAW", "TOPDERIVATIVE", "GULPS_FLAT", "BOTTOMLEVEL"):
            n = int(row[2])
            values = np.asarray(row[3:3 + n], dtype=float)
            if len(values) != n:
                raise ValueError(f"Incomplete {section}: {row[0]}")
            result.setdefault(section, {})[row[0]] = values
    return result


def descriptions(path):
    result, active = {}, False
    for row in csv.reader(path.read_text(encoding="utf-8-sig").splitlines(), delimiter=";"):
        if row[:2] == ["#", "CAPILLARIES"]:
            active = True
        elif row and row[0] == "#":
            active = False
        elif active and row and row[0] != "cap_prefix":
            # Fixed prefix precedes variable-length ROI geometry; later fields
            # cannot be parsed by simply zipping the CSV header and record.
            result[row[0]] = dict(kymo=row[3], cage=int(row[4]), flies=int(row[5]),
                                  volume=float(row[6]), pixels=float(row[7]))
    return result


def fit_evaporation(y):
    """Numerical port of EvaporationCurveFitter.fit (pixel units, index time)."""
    y = np.asarray(y, dtype=float)
    if len(y) < 8 or not np.isfinite(y).all():
        raise ValueError("Need >=8 finite evaporation samples")
    n = len(y)
    t = np.arange(n)
    weights = np.ones(n)
    tau_min = max(2., n / 50.)
    tau_max = max(tau_min * 2., 5. * n)
    for round_ in range(3):
        lo, hi = tau_min, tau_max
        best_cost = float("inf")
        for refine in range(3):
            steps = 40 if refine == 0 else 20
            step = (hi - lo) / steps
            for s in range(steps + 1):
                tau = lo + s * step
                u = 1 - np.exp(-t / tau)
                a = np.sum(weights * y * u) / np.sum(weights * u * u)
                cost = np.sum(weights * (y - a * u) ** 2)
                if cost < best_cost:
                    best_cost, best_tau, best_a = cost, tau, a
            lo = max(tau_min, best_tau - 2 * step)
            hi = min(tau_max, best_tau + 2 * step)
        model = best_a * (1 - np.exp(-t / best_tau))
        if round_ < 2:
            residual = np.abs(y - model)
            delta = 2.5 * max(np.sqrt(np.mean(residual ** 2)), 1e-6)
            weights = np.minimum(1., delta / np.maximum(residual, 1e-15))
    return model, dict(a_pixels=float(best_a), tau_samples=float(best_tau))


def evaporation(traces):
    mean = np.mean(traces, axis=0)
    return fit_evaporation(mean - mean[0])


def step_scores(y, reference, windows=(3, 4, 6)):
    """Compare local linear drift with drift + step at each sample boundary.

    Score is sqrt(SSE reduction), in pixels, NOT a probability. Common local
    slope absorbs modest evaporation mismatch. Windows are samples per side.
    No gaps are bridged, and at least two samples per side are required.
    """
    y = np.asarray(y) - reference
    n = len(y)
    scores, amplitudes, widths = np.zeros(n), np.zeros(n), np.zeros(n, dtype=int)
    for t in range(2, n - 1):
        for w in windows:
            lo, hi = max(0, t - w), min(n, t + w)
            z = y[lo:hi]
            if not np.isfinite(z).all():
                continue
            if widths[t] == 0:
                widths[t] = w  # Marks an evaluable boundary, even if score=0.
            x = np.arange(lo, hi) - (t - .5)
            base = np.column_stack((np.ones(len(x)), x))
            design = np.column_stack((base, (x > 0).astype(float)))
            b0 = np.linalg.lstsq(base, z, rcond=None)[0]
            b1 = np.linalg.lstsq(design, z, rcond=None)[0]
            improvement = np.sum((z - base @ b0) ** 2) - np.sum((z - design @ b1) ** 2)
            score = np.sqrt(max(0., improvement)) if b1[2] > 0 else 0.
            # Reject one-sample excursions by requiring positive median change.
            if np.median(z[x > 0]) <= np.median(z[x < 0]):
                continue
            if score > scores[t]:
                scores[t], amplitudes[t], widths[t] = score, b1[2], w
    return scores, amplitudes, widths


def events(scores, threshold, radius=2):
    """Nonmaximum suppression; nearby true events may merge (prototype limit)."""
    chosen = []
    for t in sorted(np.flatnonzero(scores > threshold), key=lambda i: (-scores[i], i)):
        if all(abs(t - other) > radius for other in chosen):
            chosen.append(int(t))
    return sorted(chosen)


def refine_from_image(path, tracked):
    """Exploratory chromatic sigmoid fit, guided by the existing track.

    Independent offset/contrast for G-R and B-R, common center/blur width.
    No temporal smoothing. Flags constrained fits rather than trusting them.
    """
    with Image.open(path) as image:
        pixels = np.asarray(image.convert("RGB"), dtype=float)
    if pixels.shape[1] != len(tracked):
        raise ValueError("Kymograph width does not match native TOPRAW")
    offsets = np.arange(-8, 9)
    centers = np.arange(-4., 4.001, .1)
    blur_widths = np.array([.5, .75, 1., 1.5, 2., 3.])
    center_grid, width_grid = np.meshgrid(centers, blur_widths)
    c, b = center_grid.ravel(), width_grid.ravel()
    templates = 1 / (1 + np.exp(-(offsets[None, :] - c[:, None]) / b[:, None]))
    templates -= np.mean(templates, axis=1, keepdims=True)
    energy = np.sum(templates ** 2, axis=1)
    refined, rmse, boundary = [], [], []
    for t, yy in enumerate(tracked):
        anchor = int(round(yy))
        rows = anchor + offsets
        if rows[0] < 0 or rows[-1] >= pixels.shape[0]:
            refined.append(float(yy)); rmse.append(None); boundary.append(True)
            continue
        rgb = pixels[rows, t, :]
        # Cyan liquid: chromatic differences suppress neutral dark lines and
        # illumination changes that can dominate a raw RGB intensity fit.
        values = np.column_stack((rgb[:, 1] - rgb[:, 0], rgb[:, 2] - rgb[:, 0]))
        values = values - values.mean(axis=0)
        cov = templates @ values
        errors = np.sum(values ** 2) - np.sum(cov ** 2, axis=1) / energy
        best = int(np.argmin(errors))
        refined.append(float(anchor + c[best]))
        rmse.append(float(np.sqrt(max(0., errors[best]) / values.size)))
        boundary.append(bool(abs(c[best]) >= 3.99 or b[best] == 3.))
    return np.asarray(refined), dict(rmse_color_difference=rmse, constrained=boundary)


def load_experiment(path):
    root = path / "results"
    data = measures(root / "CapillariesMeasures.csv")
    meta = descriptions(root / "CapillariesDescription.csv")
    # Match production: cage descriptions are authoritative for nFlies.
    active = False
    for row in csv.reader((root / "CagesDescription.csv").read_text(encoding="utf-8-sig").splitlines(), delimiter=";"):
        if row[:2] == ["#", "CAGE"]:
            active = True
        elif row and row[0] == "#":
            active = False
        elif active and row and row[0] != "cageID":
            for cap in meta.values():
                if cap["cage"] == int(row[0]):
                    cap["flies"] = int(row[1])
    times = []
    for row in csv.reader((root / "FrameTimeScale.csv").read_text(encoding="utf-8-sig").splitlines(), delimiter=";"):
        if row and row[0].isdigit():
            times.append(float(row[1]) / 60000.)
    times = np.asarray(times)
    if len(times) < 8 or not np.allclose(np.diff(times), np.diff(times)[0]) or np.diff(times)[0] <= 0:
        raise ValueError("Prototype requires regular increasing native timestamps")
    top = data["TOPRAW"]
    if any(len(y) != len(times) or not np.isfinite(y).all() for y in top.values()):
        raise ValueError("Missing/nonfinite or non-native TOPRAW samples")
    bin_path = root / "bin_60"
    xml = ET.parse(bin_path / "BinDescription.xml").getroot()
    if xml.findtext(".//subsampleFactor") != "1" or xml.findtext(".//kymoFromNormedBlue") != "false":
        raise ValueError("Prototype requires native, non-normalized kymographs")
    return data, meta, times, bin_path


def benchmark(path, quantile=.995, image_refinement=False):
    data, meta, times, bin_path = load_experiment(path)
    top = data["TOPRAW"]
    image_quality = {}
    if image_refinement:
        top = dict(top)
        for name in top:
            top[name], image_quality[name] = refine_from_image(bin_path / meta[name]["kymo"], top[name])
    controls = [name for name in top if meta[name]["flies"] == 0]
    if len(controls) < 3:
        raise ValueError("Need at least three empty capillaries")
    # Preserve the production evaporation model, estimated from saved levels.
    original_top = data["TOPRAW"]
    ref, fit = evaporation([original_top[name] for name in controls])
    scoring_top = {name: y.copy() for name, y in top.items()}
    if image_refinement:
        for name, y in scoring_top.items():
            y[np.asarray(image_quality[name]["constrained"])] = np.nan
    # Every control is scored against an evaporation curve fitted without it.
    control_scores = {}
    control_valid = {}
    control_references = {}
    for name in controls:
        own_ref, _ = evaporation([original_top[c] for c in controls if c != name])
        control_references[name] = own_ref
        score, _, widths = step_scores(scoring_top[name], own_ref)
        control_scores[name], control_valid[name] = score, widths > 0
    calibration = np.concatenate([s[control_valid[c]] for c, s in control_scores.items()])
    if not len(calibration):
        raise ValueError("No evaluable control windows")
    threshold = float(np.quantile(calibration, quantile))
    dy = np.concatenate([np.abs(np.diff(data["TOPRAW"][name])) for name in controls])
    theta_dy = float(np.mean(dy) + 3 * np.std(dy))
    results, detail = [], {}
    for name, y in top.items():
        scores, amplitude, widths = step_scores(scoring_top[name], control_references.get(name, ref))
        found = events(scores, threshold)
        classic_saved = data.get("GULPS_FLAT", {}).get(name, np.zeros(len(y)))
        v2 = np.flatnonzero(np.r_[0., np.diff(data["TOPRAW"][name])] > theta_dy).tolist()
        # These are marked samples, not necessarily independent bouts/gulps.
        results.append(dict(capillary=name, flies=meta[name]["flies"],
                            saved_marked_samples=int(np.count_nonzero(classic_saved)),
                            v2_marked_samples=len(v2), prototype_candidates=len(found),
                            evaluated_boundaries=int(np.count_nonzero(widths))))
        detail[name] = dict(top=y.tolist(), score=scores.tolist(),
                            amplitude_pixels=amplitude.tolist(), window_samples=widths.tolist(),
                            candidates=found, v2=v2,
                            saved=np.flatnonzero(classic_saved).tolist())
    # Nested holdout: even threshold-training curves exclude the test control.
    holdout = []
    for held in controls:
        training = [c for c in controls if c != held]
        training_scores = []
        for c in training:
            train_ref, _ = evaporation([original_top[k] for k in training if k != c])
            train_score, _, train_width = step_scores(scoring_top[c], train_ref)
            training_scores.extend(train_score[train_width > 0])
        if not training_scores:
            raise ValueError("No evaluable held-out calibration windows")
        held_threshold = float(np.quantile(training_scores, quantile))
        held_ref, _ = evaporation([original_top[c] for c in training])
        held_scores, _, held_width = step_scores(scoring_top[held], held_ref)
        holdout.append(dict(capillary=held, threshold=held_threshold,
                            candidates=events(held_scores, held_threshold),
                            evaluated_boundaries=int(np.count_nonzero(held_width))))
    return dict(experiment=str(path), samples=len(times), minutes=times.tolist(),
                controls=controls, evaporation_pixels=ref.tolist(), fit=fit,
                threshold=threshold, calibration_quantile=quantile, theta_dy=theta_dy,
                summary=results, holdout=holdout, detail=detail,
                kymograph_dir=str(bin_path), image_refinement=image_refinement,
                image_quality=image_quality)


def svg_review(result, output):
    """Self-contained diagnostic plot for development review, not a UI feature."""
    from html import escape
    w, panel_h = 1200, 210
    names = ["1L", "1R"] + result["controls"]
    names = list(dict.fromkeys(n for n in names if n in result["detail"]))
    height = 80 + panel_h * len(names)
    svg = [f'<svg xmlns="http://www.w3.org/2000/svg" width="{w}" height="{height}" viewBox="0 0 {w} {height}">',
           '<rect width="100%" height="100%" fill="white"/>',
           '<style>text{font-family:Arial,sans-serif;font-size:13px;fill:#20252a}</style>',
           '<text x="75" y="24" font-size="19">Gulp prototype: local drift versus persistent step</text>',
           f'<text x="75" y="47">Blue: {"image-refined level" if result["image_refinement"] else "saved topraw"} minus evaporation; orange: prototype candidates; grey: saved marked samples</text>']
    for i, name in enumerate(names):
        d = result["detail"][name]
        y = np.asarray(d["top"]) - result["evaporation_pixels"]
        y -= y[0]
        ymin, ymax = float(min(y)) - 1, float(max(y)) + 1
        top = 85 + i * panel_h
        def xcoord(t):
            return 75 + 1090 * t / (result["samples"] - 1)
        def ycoord(v):
            return top + 145 * (ymax - v) / (ymax - ymin)
        for tick in np.linspace(ymin, ymax, 4):
            yy = ycoord(tick)
            svg.append(f'<path d="M75 {yy}H1165" stroke="#e0e3e5"/><text x="30" y="{yy+4}">{tick:.1f}</text>')
        points = ' '.join(f'{xcoord(t):.2f},{ycoord(v):.2f}' for t,v in enumerate(y))
        svg.append(f'<polyline points="{points}" fill="none" stroke="#235e9c" stroke-width="1.6"/>')
        for t in d["saved"]:
            xx = xcoord(t)
            svg.append(f'<path d="M{xx} {top+151}v8" stroke="#777"/>')
        for t in d["candidates"]:
            svg.append(f'<circle cx="{xcoord(t)}" cy="{ycoord(y[t])}" r="4" fill="white" stroke="#c55e10" stroke-width="2"/>')
        svg.append(f'<text x="80" y="{top-9}">{escape(name)} — displacement after evaporation correction (pixels)</text>')
        for t in range(0, result["samples"], 20):
            svg.append(f'<text x="{xcoord(t)}" y="{top+177}" text-anchor="middle">{result["minutes"][t]:g}</text>')
        svg.append(f'<text x="560" y="{top+197}">Elapsed time (minutes)</text>')
    svg.append('</svg>')
    output.write_text('\n'.join(svg), encoding="utf-8")


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--list", required=True, type=Path)
    p.add_argument("--output", required=True, type=Path)
    p.add_argument("--limit", type=int, default=1)
    p.add_argument("--refine-image", action="store_true")
    args = p.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    paths = [Path(line.strip()) for line in args.list.read_text(encoding="utf-8-sig").splitlines() if line.strip()]
    inventory, all_results = [], []
    for i, path in enumerate(paths):
        entry = dict(index=i, experiment=str(path), exists=path.exists(),
                     root_measures=(path / "results/CapillariesMeasures.csv").exists(),
                     bin_measures=(path / "results/bin_60/CapillariesMeasures.csv").exists())
        if i < args.limit:
            try:
                result = benchmark(path, image_refinement=args.refine_image)
                result["experiment_index"] = i
                all_results.append(result)
                (args.output / f"experiment_{i:02d}.json").write_text(json.dumps(result, indent=2, allow_nan=False), encoding="utf-8")
                if i == 0:
                    svg_review(result, args.output / "first_experiment.svg")
                entry["status"] = "benchmarked"
            except (OSError, ValueError, KeyError, ET.ParseError) as e:
                entry["status"] = str(e)
        inventory.append(entry)
    (args.output / "inventory.json").write_text(json.dumps(inventory, indent=2), encoding="utf-8")
    rows = [dict(experiment_index=r["experiment_index"], experiment=r["experiment"], **row)
            for r in all_results for row in r["summary"]]
    if rows:
        with (args.output / "counts.csv").open("w", newline="", encoding="utf-8") as f:
            writer = csv.DictWriter(f, fieldnames=list(rows[0]))
            writer.writeheader()
            writer.writerows(rows)
    print(json.dumps(dict(experiments_listed=len(inventory), benchmarked=len(all_results),
                          skipped=[r for r in inventory if r.get("status") not in (None, "benchmarked")],
                          first_summary=all_results[0]["summary"] if all_results else [],
                          first_holdout=all_results[0]["holdout"] if all_results else []), indent=2))


if __name__ == "__main__":
    main()
