"""Experimental local image registration and anchored meniscus refinement.

Reads original measurements/TIFFs, writes only a separate output directory.
No evaporation subtraction is applied to the refined physical level curve.
Requires numpy and Pillow. Run --help for options.
"""
import argparse
import csv
import json
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFont

from gulp_prototype import load_experiment, evaporation


def normalized_correlation(previous, current, center, radius=10, max_shift=6., spacing=.1):
    """Positive shift means the image pattern moved DOWN in current.

    Both profiles share the previous image's coordinate center. Every shift
    uses the same 2*radius+1 sample positions. A full search region is required.
    Offset is removed per channel; normalization uses combined channel energy.
    """
    previous = np.asarray(previous, dtype=float)
    current = np.asarray(current, dtype=float)
    if previous.ndim == 1:
        previous = previous[:, None]
        current = current[:, None]
    if previous.shape != current.shape or radius < 2 or max_shift <= 0 or spacing <= 0:
        raise ValueError("Invalid profiles or registration parameters")
    height = len(previous)
    if center - radius - max_shift < 0 or center + radius + max_shift > height - 1:
        return None
    rows = center + np.arange(-radius, radius + 1, dtype=float)
    positions = np.arange(height)
    reference = np.column_stack([np.interp(rows, positions, previous[:, c]) for c in range(previous.shape[1])])
    reference -= reference.mean(axis=0)
    contrast = float(np.sqrt(np.mean(reference ** 2)))
    energy = np.sum(reference ** 2)
    if not np.isfinite(reference).all() or energy < 1e-12:
        return None
    shifts = np.linspace(-max_shift, max_shift, int(round(2 * max_shift / spacing)) + 1)
    candidates = np.stack([np.interp((rows[None, :] + shifts[:, None]).ravel(), positions, current[:, c])
                           .reshape(len(shifts), len(rows)) for c in range(current.shape[1])], axis=2)
    candidates -= candidates.mean(axis=1, keepdims=True)
    denom = np.sqrt(energy * np.sum(candidates ** 2, axis=(1, 2)))
    corr = np.divide(np.sum(candidates * reference, axis=(1, 2)), denom,
                     out=np.full(len(shifts), -1.), where=denom > 1e-12)
    if not np.isfinite(corr).all():
        return None
    best = int(np.argmax(corr))
    far = np.abs(shifts - shifts[best]) >= 1.
    margin = float(corr[best] - max(corr[far])) if np.any(far) else 0.
    return dict(shift=float(shifts[best]), correlation=float(corr[best]),
                contrast=contrast, separation_at_one_pixel=margin,
                at_limit=best in (0, len(shifts) - 1))


def register_image(rgb, tracked, radius=10, max_shift=6.):
    rgb = np.asarray(rgb, dtype=float)
    if rgb.ndim != 3 or rgb.shape[2] != 3 or rgb.shape[1] != len(tracked):
        raise ValueError("Expected native RGB kymograph matching the level trace")
    # Chromatic contrast of cyan liquid, with reduced sensitivity to neutral
    # dark bands. This channel choice must be validated for other dyes.
    signal = np.stack((rgb[:, :, 1] - rgb[:, :, 0], rgb[:, :, 2] - rgb[:, :, 0]), axis=2)
    records = []
    for t in range(1, len(tracked)):
        forward = normalized_correlation(signal[:, t - 1], signal[:, t], tracked[t - 1], radius, max_shift)
        reverse = normalized_correlation(signal[:, t], signal[:, t - 1], tracked[t], radius, max_shift)
        reasons = []
        if forward is None or reverse is None:
            reasons.append("insufficient profile or image border")
        else:
            if forward['at_limit'] or reverse['at_limit']:
                reasons.append("search limit")
            if min(forward['contrast'], reverse['contrast']) < 2.:
                reasons.append("low contrast")
            if min(forward['correlation'], reverse['correlation']) < .9:
                reasons.append("weak correlation")
            if min(forward['separation_at_one_pixel'], reverse['separation_at_one_pixel']) < .005:
                reasons.append("broad or ambiguous peak")
            if abs(forward['shift'] + reverse['shift']) > .75:
                reasons.append("forward/reverse disagreement")
        accepted = not reasons
        # Symmetric estimate reduces dependence on which column is the template.
        displacement = (forward['shift'] - reverse['shift']) / 2 if forward and reverse else None
        records.append(dict(frame=t, shift=displacement, accepted=accepted, reasons=reasons,
                            forward=forward, reverse=reverse))
    return records


def anchored_refinement(raw, records, anchor_weight=.05, huber_pixels=1.):
    """Fit image displacements with weak robust absolute raw-level anchors.

    There is no smoothness penalty and no monotonicity constraint. Rejected
    pairs contribute no motion equation. Anchors bound accumulation drift;
    regions without usable image evidence consequently revert toward raw.
    """
    raw = np.asarray(raw, dtype=float)
    if not np.isfinite(raw).all() or anchor_weight <= 0:
        raise ValueError("Finite raw values and positive anchor weight required")
    n = len(raw)
    motion = np.zeros((n, n))
    rhs_motion = np.zeros(n)
    for record in records:
        if not record['accepted']:
            continue
        t, d = record['frame'], record['shift']
        if not 1 <= t < n or not np.isfinite(d):
            raise ValueError("Invalid accepted motion constraint")
        motion[t, t] += 1
        motion[t - 1, t - 1] += 1
        motion[t, t - 1] -= 1
        motion[t - 1, t] -= 1
        rhs_motion[t] += d
        rhs_motion[t - 1] -= d
    fitted = raw.copy()
    for _ in range(8):
        residual = np.abs(fitted - raw)
        weights = anchor_weight * np.minimum(1., huber_pixels / np.maximum(residual, 1e-12))
        fitted = np.linalg.solve(motion + np.diag(weights), rhs_motion + weights * raw)
    return fitted


def comparison_plot(result, path):
    """Raster diagnostic plots from numerical arrays; no resampled source data."""
    width, ph = 1450, 255
    names = result['selected']
    image = Image.new('RGB', (width, 130 + ph * len(names)), 'white')
    draw = ImageDraw.Draw(image)
    try:
        regular = ImageFont.truetype('C:/Windows/Fonts/arial.ttf', 16)
        title = ImageFont.truetype('C:/Windows/Fonts/arial.ttf', 22)
    except OSError:
        regular = ImageFont.load_default()
        title = regular
    draw.text((65, 12), 'Meniscus refinement using local image correlation', fill='#222222', font=title)
    draw.text((65, 43), 'Grey: saved topraw   Blue: anchored correlation refinement   Orange ticks: rejected image pairs', fill='#333333', font=regular)
    draw.text((65, 67), 'Both curves relative to the saved first position; evaporation is retained. Each panel has its own vertical scale.', fill='#333333', font=regular)
    draw.text((65, 90), 'Source: 17-10-26_Quinine/cam01_1mM, original bin_60 TIFFs and root CSV; 0-140 minutes.', fill='#555555', font=regular)
    times = result['minutes']
    for i, name in enumerate(names):
        data = result['capillaries'][name]
        raw = np.asarray(data['raw']); refined = np.asarray(data['refined'])
        origin = raw[0]
        raw -= origin; refined -= origin
        low = float(min(raw.min(), refined.min())) - .5
        high = float(max(raw.max(), refined.max())) + .5
        top = 145 + ph * i
        def xx(t):
            return 80 + 1300 * times[t] / times[-1]
        def yy(value):
            return top + 170 * (high - value) / (high - low)
        draw.text((80, top - 25), f'{name} | downward displacement (pixels) | accepted pairs {data["metrics"]["accepted_pairs"]}/{len(times)-1}', fill='#222222', font=regular)
        for value in np.linspace(low, high, 5):
            y = yy(value)
            draw.line((80, y, 1380, y), fill='#dddddd')
            draw.text((15, y - 8), f'{value:.1f}', fill='#444444', font=regular)
        draw.line([(xx(t), yy(v)) for t, v in enumerate(raw)], fill='#888888', width=2)
        draw.line([(xx(t), yy(v)) for t, v in enumerate(refined)], fill='#1264a3', width=2)
        for record in data['registration']:
            if not record['accepted']:
                x = xx(record['frame'])
                draw.line((x, top + 177, x, top + 185), fill='#ba5a10', width=2)
        for t in range(0, len(times), 20):
            draw.text((xx(t) - 9, top + 190), f'{times[t]:g}', fill='#444444', font=regular)
        draw.text((610, top + 215), 'Elapsed time (minutes)', fill='#333333', font=regular)
    image.save(path)


def detail_plot(result, bin_path, meta, path):
    """Inspect small steps against unmarked original TIFF crops and motion."""
    examples = [('1L', 3, 12), ('1L', 23, 32), ('1L', 52, 63), ('0R', 23, 32)]
    canvas = Image.new('RGB', (1440, 1050), 'white')
    draw = ImageDraw.Draw(canvas)
    try:
        font = ImageFont.truetype('C:/Windows/Fonts/arial.ttf', 16)
        title = ImageFont.truetype('C:/Windows/Fonts/arial.ttf', 22)
    except OSError:
        font = title = ImageFont.load_default()
    draw.text((30, 12), 'Small changes: original meniscus image and estimated motion', fill='#222222', font=title)
    draw.text((30, 43), 'Grey: topraw difference   Blue: correlation shift   Orange: expected evaporation per interval', fill='#333333', font=font)
    draw.text((30, 67), 'First experiment, native one-minute samples. Positive = downward. These are measurements, not confirmed gulps.', fill='#444444', font=font)
    for i, (name, start, stop) in enumerate(examples):
        top = 110 + i * 230
        data = result['capillaries'][name]
        raw = np.asarray(data['raw'])
        low = max(0, int(np.floor(min(raw[start:stop+1]))) - 5)
        high = int(np.ceil(max(raw[start:stop+1]))) + 6
        with Image.open(bin_path / meta[name]['kymo']) as image:
            crop = image.convert('RGB').crop((start, low, stop+1, high))
        crop = crop.resize((400, 150), Image.Resampling.NEAREST)
        canvas.paste(crop, (30, top + 20))
        draw.text((30, top - 5), f'{name}: {start}-{stop} minutes | original TIFF crop', fill='#222222', font=font)
        draw.text((30, top + 180), f'Image rows {low}-{high-1}; horizontal axis = time', fill='#444444', font=font)
        frame = np.arange(start+1, stop+1)
        d_raw = np.diff(raw)[frame-1]
        d_corr = np.array([data['registration'][t-1]['shift'] for t in frame])
        e = np.diff(result['evaporation_pixels'])[frame-1]
        ymin = min(-.5, min(d_raw), min(d_corr)) - .1
        ymax = max(.8, max(d_raw), max(d_corr)) + .2
        def xx(t):
            return 535 + 840 * (t-(start+1)) / max(1, stop-start-1)
        def yy(v):
            return top + 165 - 135 * (v-ymin) / (ymax-ymin)
        draw.text((515, top - 5), 'Vertical displacement per one-minute interval (pixels)', fill='#222222', font=font)
        for value in np.linspace(ymin, ymax, 4):
            y = yy(value)
            draw.line((535,y,1375,y), fill='#dddddd')
            draw.text((475,y-8), f'{value:.1f}', fill='#444444',font=font)
        for values, color in [(d_raw,'#888888'),(d_corr,'#1264a3'),(e,'#ba5a10')]:
            draw.line([(xx(t),yy(v)) for t,v in zip(frame,values)],fill=color,width=2)
            for t,v in zip(frame,values):
                x,y=xx(t),yy(v)
                draw.ellipse((x-3,y-3,x+3,y+3),fill=color)
        for t in frame:
            draw.text((xx(t)-8,top+175),str(t),fill='#444444',font=font)
        draw.text((850,top+199),'Elapsed time (minutes)',fill='#333333',font=font)
    canvas.save(path)


def run(experiment, output, radius=10, anchor_weight=.05, all_capillaries=False):
    data, meta, times, bin_path = load_experiment(experiment)
    controls = [name for name in data['TOPRAW'] if meta[name]['flies'] == 0]
    selected = list(dict.fromkeys(['1L', '1R'] + controls))
    if all_capillaries:
        selected = list(data['TOPRAW'])
    ref, _ = evaporation([data['TOPRAW'][name] for name in controls])
    result = dict(experiment=str(experiment), minutes=times.tolist(), selected=selected,
                  radius=radius, max_shift=6, anchor_weight=anchor_weight,
                  evaporation_pixels=ref.tolist(), capillaries={})
    csv_rows = []
    for name in selected:
        raw = data['TOPRAW'][name]
        with Image.open(bin_path / meta[name]['kymo']) as image:
            rgb = np.asarray(image.convert('RGB'))
        records = register_image(rgb, raw, radius=radius)
        refined = anchored_refinement(raw, records, anchor_weight)
        accepted = np.array([r['accepted'] for r in records])
        shifts = np.array([r['shift'] if r['shift'] is not None else np.nan for r in records])
        d_raw, d_refined = np.diff(raw), np.diff(refined)
        evaporation_rate = np.diff(ref)
        valid_shifts = shifts[accepted]
        metrics = dict(accepted_pairs=int(sum(accepted)),
                       raw_diff_sd=float(np.std(d_raw)), refined_diff_sd=float(np.std(d_refined)),
                       raw_evap_residual_diff_sd=float(np.std((d_raw - evaporation_rate)[accepted])) if any(accepted) else None,
                       correlation_evap_residual_diff_sd=float(np.std((shifts - evaporation_rate)[accepted])) if any(accepted) else None,
                       raw_negative_steps=int(sum(d_raw < -.25)), refined_negative_steps=int(sum(d_refined < -.25)),
                       raw_refined_rmse=float(np.sqrt(np.mean((refined - raw) ** 2))),
                       max_absolute_adjustment=float(max(abs(refined - raw))),
                       motion_constraint_rmse=float(np.sqrt(np.mean((d_refined[accepted] - valid_shifts) ** 2))) if any(accepted) else None)
        result['capillaries'][name] = dict(raw=raw.tolist(), refined=refined.tolist(),
                                         registration=records, metrics=metrics)
        for t in range(len(times)):
            record = records[t - 1] if t else None
            csv_rows.append(dict(capillary=name, frame=t, elapsed_minutes=times[t], raw_pixels=raw[t],
                                 refined_pixels=refined[t], correlation_shift=record['shift'] if record else '',
                                 accepted=record['accepted'] if record else '',
                                 rejection='; '.join(record['reasons']) if record else '',
                                 evaporation_increment_pixels=evaporation_rate[t-1] if t else '',
                                 saved_gulp_pixels=data['GULPS_FLAT'][name][t]))
    output.mkdir(parents=True, exist_ok=True)
    (output / 'correlation.json').write_text(json.dumps(result, indent=2, allow_nan=False), encoding='utf-8')
    with (output / 'refined_levels.csv').open('w', newline='', encoding='utf-8') as f:
        writer = csv.DictWriter(f, fieldnames=list(csv_rows[0]))
        writer.writeheader(); writer.writerows(csv_rows)
    comparison_plot(result, output / 'refined_levels.png')
    detail_plot(result, bin_path, meta, output / 'small_changes.png')
    print(json.dumps({k: v['metrics'] for k, v in result['capillaries'].items()}, indent=2))
    return result


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--experiment', required=True, type=Path)
    parser.add_argument('--output', required=True, type=Path)
    parser.add_argument('--radius', type=int, default=10)
    parser.add_argument('--anchor-weight', type=float, default=.05)
    parser.add_argument('--all-capillaries', action='store_true')
    args = parser.parse_args()
    run(args.experiment, args.output, args.radius, args.anchor_weight, args.all_capillaries)
