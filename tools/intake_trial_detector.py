"""Offline experimental intake detector. Not imported by the multiCAFE plugin.

Proposes persistent steps on drifting backgrounds and 5--10-bin ramp episodes.
Candidate limits are learned from development no-fly controls only. Archived
automatic intake is preserved; adjacent proposals are held out for review.
"""
from dataclasses import dataclass, asdict
import csv
import hashlib
import json
import math
from pathlib import Path
import sys
import time
import numpy as np
from PIL import Image

DEVELOPMENT = ("006", "007", "010", "011", "012", "014", "015", "016", "143", "146")
CHECK = ("008", "009", "013", "017", "021", "147")
WIDTHS = (1, 5, 6, 7, 8, 9, 10)
MIN_FLANK = 5
FLANK = 10
MIN_AMOUNT = .5
MIN_SCORE = 5.
NULL_QUANTILE = .999


@dataclass
class Candidate:
    first: int
    last: int
    kind: str
    amount: float
    score: float
    fit_noise: float
    observed_change: float = 0.
    peak_fraction: float = 1.
    moving_bins: int = 0
    image_correlation: float = 0.
    image_contrast: float = 0.
    border_confounded: bool = False
    limit: float = 0.
    accepted: bool = False
    reason: str = ""


def fit_change(levels, times, reliable, first, last, noise):
    """Robust quadratic drift plus a persistent step or a finite-duration ramp."""
    if first < 1 or last >= len(levels):
        return None
    if not np.all(np.isfinite(levels[first - 1:last + 1])):
        return None
    lo, hi = max(0, first - FLANK), min(len(levels), last + FLANK + 1)
    indices = np.arange(lo, hi)
    indices = indices[np.isfinite(levels[indices]) & (reliable[indices] | (indices == 0))]
    before, after = indices[indices < first], indices[indices >= last]
    if len(before) < MIN_FLANK or len(after) < MIN_FLANK:
        return None
    duration = times[last] - times[first - 1]
    if not duration > 0:
        return None
    origin = (times[first - 1] + times[last]) / 2
    span = times[indices[-1]] - times[indices[0]]
    x = (times[indices] - origin) / span
    event = (indices >= first).astype(float) if first == last else np.clip((times[indices] - times[first - 1]) / duration, 0, 1)
    design = np.column_stack((np.ones(len(x)), x, x * x, event))
    values = levels[indices]
    weights = np.ones(len(x))
    for _ in range(3):
        weighted = design * np.sqrt(weights[:, None])
        coef, _, rank, _ = np.linalg.lstsq(weighted, values * np.sqrt(weights), rcond=None)
        if rank < 4:
            return None
        residual = values - design @ coef
        scale = max(noise, 1.4826 * np.median(np.abs(residual - np.median(residual))))
        weights = np.minimum(1., 1.5 * scale / np.maximum(np.abs(residual), 1e-9))
    amount = float(coef[-1])
    information = np.linalg.pinv(design.T @ (weights[:, None] * design))[-1, -1]
    sigma = max(noise, 1.4826 * np.median(np.abs(residual - np.median(residual))))
    score = amount / (sigma * math.sqrt(information)) if information > 0 else 0.
    event_time = (times[first - 1:last + 1] - origin) / span
    background = coef[0] + coef[1] * event_time + coef[2] * event_time ** 2
    increments = np.diff(levels[first - 1:last + 1]) - np.diff(background)
    positive = np.maximum(0., increments)
    peak_fraction = float(positive.max() / positive.sum()) if positive.sum() > 0 else 1.
    moving = int(np.sum(positive >= max(.08, .08 * max(0, amount))))
    return Candidate(first, last, "step" if first == last else "episode", amount, float(score), float(sigma),
                     observed_change=float(increments.sum()), peak_fraction=peak_fraction, moving_bins=moving)


def stationary_dark_rows(rgb):
    """Conservative image mask, not a claim about physical glass/tape identity."""
    grey = rgb.astype(float).mean(axis=2)
    return np.mean(grey < 75, axis=1) >= .8


def image_support(rgb, raw, reliable, first, last, dark_rows):
    height = rgb.shape[0]
    selected = raw[first - 1:last + 1]
    near = np.flatnonzero(dark_rows)
    border = bool(len(near) and np.any(np.min(np.abs(selected[:, None] - near[None, :]), axis=1) <= 3))
    before = [i for i in range(max(0, first - 5), first) if (i == 0 or reliable[i]) and np.isfinite(raw[i])]
    after = [i for i in range(last, min(len(raw), last + 6)) if reliable[i] and np.isfinite(raw[i])]
    if len(before) < 2 or len(after) < 2:
        return 0., 0., border
    offsets = np.arange(-6, 7, dtype=float)
    def profile(indices):
        profiles = []
        for i in indices:
            if raw[i] < 6 or raw[i] > height - 7:
                continue
            locations = raw[i] + offsets
            rows = np.arange(height)
            profiles.append(np.column_stack([np.interp(locations, rows, rgb[:, i, channel]) for channel in range(3)]))
        return np.median(profiles, axis=0) if len(profiles) >= 2 else None
    a, b = profile(before[-3:]), profile(after[:3])
    if a is None or b is None:
        return 0., 0., border
    contrast = min(float(np.linalg.norm(a[-2:].mean(axis=0) - a[:2].mean(axis=0))),
                   float(np.linalg.norm(b[-2:].mean(axis=0) - b[:2].mean(axis=0))))
    av, bv = (a - a.mean(axis=0)).ravel(), (b - b.mean(axis=0)).ravel()
    denominator = np.linalg.norm(av) * np.linalg.norm(bv)
    correlation = float(av @ bv / denominator) if denominator > 1e-9 else 0.
    return correlation, contrast, border


def read_capillary(root, cap, noise_divisor=3):
    folder = root / "archives" / cap["experiment"]
    with (folder / f"correlation-{cap['cap']}.csv").open(encoding="utf-8-sig") as stream:
        rows = list(csv.DictReader((line for line in stream if not line.startswith("#")), delimiter=";"))
    times = np.array([float(r["elapsed_min"]) for r in rows])
    raw = np.array([float(r["raw_px"]) for r in rows])
    evaporation = np.zeros(len(rows))
    for i in range(1, len(rows)):
        evaporation[i] = evaporation[i - 1] + float(rows[i]["evaporation_increment_px"])
    reliable = np.array([r["reliable"] == "true" for r in rows])
    baseline = np.array([float(r["automatic_net_intake_px"]) for r in rows])
    edited = np.array([float(r["edited_net_intake_px"]) for r in rows])
    rgb = np.asarray(Image.open(folder / f"kymograph-{cap['cap']}.png").convert("RGB"))
    # Local residuals determine uncertainty. A weak prior avoids reusing the
    # full record-wide detection threshold as a veto on quiet local steps.
    noise = max(.08, cap["threshold"] * float(np.median(np.diff(times))) / noise_divisor)
    return {"times": times, "raw": raw, "levels": raw - evaporation, "evaporation": evaporation,
            "reliable": reliable, "baseline": baseline, "edited": edited, "rows": rows, "rgb": rgb, "noise": noise}


def proposals(data, calibrating=False):
    raw, levels, times = data["raw"], data["levels"], data["times"]
    dark = stationary_dark_rows(data["rgb"])
    candidates = []
    for width in WIDTHS:
        for first in range(MIN_FLANK, len(raw) - width - MIN_FLANK + 2):
            last = first + width - 1
            if not calibrating and np.any(data["baseline"][max(1, first - 1):min(len(raw), last + 2)] > 0):
                continue
            fit = fit_change(levels, times, data["reliable"], first, last, data["noise"])
            if fit is None:
                continue
            if not calibrating and (fit.amount < MIN_AMOUNT or fit.score < MIN_SCORE):
                continue
            if fit.amount > 0:
                fit.image_correlation, fit.image_contrast, fit.border_confounded = image_support(
                    data["rgb"], raw, data["reliable"], first, last, dark)
            candidates.append(fit)
    return candidates


def choose(candidates, limits, baseline):
    chosen, diagnostics = [], []
    for candidate in candidates:
        width = candidate.last - candidate.first + 1
        candidate.limit = limits[str(width)]
        if candidate.border_confounded:
            candidate.reason = "stationary_dark_band_near_track"
        elif candidate.image_correlation < .8 or candidate.image_contrast < 10:
            candidate.reason = "image_support_insufficient"
        elif candidate.observed_change < max(.3, .5 * candidate.amount):
            candidate.reason = "movement_not_at_selected_interval"
        elif candidate.kind == "episode" and (candidate.peak_fraction > .6 or candidate.moving_bins < 3):
            candidate.reason = "single_step_not_a_sustained_episode"
        elif candidate.amount < MIN_AMOUNT or candidate.score <= candidate.limit:
            candidate.reason = "control_calibrated_fit_limit"
        else:
            candidate.reason = "eligible"
        diagnostics.append(candidate)
    eligible = sorted((c for c in diagnostics if c.reason == "eligible"), key=lambda c: (-c.score / c.limit, c.last - c.first))
    occupied = baseline > 0
    for candidate in eligible:
        a, b = candidate.first, candidate.last
        if np.any(occupied[max(1, a - 1):min(len(occupied), b + 2)]):
            candidate.reason = "overlap_or_adjacent_episode"
            continue
        occupied[a:b + 1] = True
        candidate.accepted = True
        candidate.reason = "trial_supplement"
        chosen.append(candidate)
    return chosen, diagnostics


def allocate(candidate, data):
    a, b = candidate.first, candidate.last
    if a == b:
        return np.array([candidate.amount])
    weights = np.maximum(0., np.diff(data["levels"][a - 1:b + 1]))
    if weights.sum() <= 0:
        weights = np.diff(data["times"][a - 1:b + 1])
    return candidate.amount * weights / weights.sum()


def evaluate(cap, data, chosen):
    trial = data["baseline"].copy()
    for candidate in chosen:
        trial[candidate.first:candidate.last + 1] += allocate(candidate, data)
    added = np.array([r["edit_action"] == "added" for r in data["rows"]])
    zero_overrides = np.array([r["manual_override"] == "true" and float(r["edited_net_intake_px"]) == 0 for r in data["rows"]])
    newly_positive = (trial > 0) & ~(data["baseline"] > 0)
    adjacent = np.convolve((trial > 0).astype(int), np.ones(3, dtype=int), mode="same") > 0
    return {"experiment": cap["experiment"], "cap": cap["cap"], "control": cap["control"],
            "split": "development" if cap["experiment"] in DEVELOPMENT else "check",
            "added_reference": int(added.sum()), "recovered_exact": int(np.sum(added & (trial > 0))),
            "recovered_within_one": int(np.sum(added & adjacent)),
            "new_unedited": int(np.sum(newly_positive & ~added & ~zero_overrides)),
            "positive_zero_overrides": int(np.sum((trial > 0) & zero_overrides)),
            "new_control_bins": int(newly_positive.sum()) if cap["control"] else 0,
            "baseline_bins_lost": int(np.sum((data["baseline"] > 0) & ~(trial > 0))),
            "proposals": [asdict(c) for c in chosen], "trial": trial.tolist()}


def main(root, output, phase="development", profile="conservative"):
    root, output = Path(root), Path(output)
    output.mkdir(parents=True, exist_ok=True)
    cohort = json.loads((root / "cohort.json").read_text(encoding="utf-8"))
    if profile not in ("conservative", "sensitive"):
        raise ValueError("Use conservative or sensitive trial profile")
    noise_divisor = 3 if profile == "conservative" else 10
    started = time.monotonic()
    if phase == "development":
        scores = {str(w): [] for w in WIDTHS}
        for cap in cohort["capillaries"]:
            if not cap["control"] or cap["experiment"] not in DEVELOPMENT:
                continue
            data = read_capillary(root, cap, noise_divisor)
            for candidate in proposals(data, calibrating=True):
                if not candidate.border_confounded and candidate.image_correlation >= .8 and candidate.image_contrast >= 10:
                    scores[str(candidate.last - candidate.first + 1)].append(max(0., candidate.score))
        limits = {w: max(MIN_SCORE, float(np.quantile(values, NULL_QUANTILE))) if len(values) >= 30 else float("inf")
                  for w, values in scores.items()}
        config = {"profile": profile, "noise_divisor": noise_divisor, "development": DEVELOPMENT, "check": CHECK, "widths": WIDTHS,
                  "min_flank": MIN_FLANK, "flank": FLANK, "min_amount_px": MIN_AMOUNT,
                  "min_score": MIN_SCORE, "control_quantile": NULL_QUANTILE,
                  "local_noise_prior_fraction_of_rate_limit": 1 / noise_divisor, "minimum_local_noise_px": .08,
                  "limits": limits, "control_samples": {w: len(v) for w, v in scores.items()},
                  "image_correlation": .8, "image_contrast": 10, "border_darkness": 75,
                  "border_persistence": .8, "border_distance_px": 3,
                  "minimum_observed_fraction": .5, "minimum_observed_px": .3,
                  "episode_maximum_peak_fraction": .6, "episode_minimum_moving_bins": 3,
                  "detector_source_sha256": hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
                  "automatic_baseline_preserved": True}
        encoded = json.dumps(config, sort_keys=True, indent=2)
        (output / "frozen-config.json").write_text(encoded, encoding="utf-8")
        print("CALIBRATION " + json.dumps(config), flush=True)
    else:
        config = json.loads((output / "frozen-config.json").read_text(encoding="utf-8"))
        if config["detector_source_sha256"] != hashlib.sha256(Path(__file__).read_bytes()).hexdigest():
            raise ValueError("Detector code changed after development settings were frozen")
        if config["profile"] != profile or config["noise_divisor"] != noise_divisor:
            raise ValueError("Requested trial profile differs from the frozen development profile")
        limits = config["limits"]
    results, rejected = [], []
    selected = DEVELOPMENT if phase == "development" else CHECK
    for cap in cohort["capillaries"]:
        if cap["experiment"] not in selected:
            continue
        data = read_capillary(root, cap, noise_divisor)
        accepted, diagnostics = choose(proposals(data), limits, data["baseline"])
        result = evaluate(cap, data, accepted)
        results.append(result)
        rejected.extend({"experiment": cap["experiment"], "cap": cap["cap"], **asdict(c)} for c in diagnostics if not c.accepted)
        print(f"{cap['experiment']}/{cap['cap']}: proposals={len(accepted)} recovered={result['recovered_exact']}/{result['added_reference']} new_control={result['new_control_bins']}", flush=True)
    payload = {"phase": phase, "configuration_sha256": hashlib.sha256((output / "frozen-config.json").read_bytes()).hexdigest(),
               "seconds": round(time.monotonic() - started, 1), "results": results, "rejected": rejected}
    (output / f"{phase}.json").write_text(json.dumps(payload, allow_nan=False), encoding="utf-8")
    keys = ("added_reference", "recovered_exact", "recovered_within_one", "new_unedited", "positive_zero_overrides", "new_control_bins", "baseline_bins_lost")
    print("SUMMARY " + json.dumps({k: sum(r[k] for r in results) for k in keys}), flush=True)


if __name__ == "__main__":
    main(*sys.argv[1:])
