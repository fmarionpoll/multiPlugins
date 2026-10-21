"""Exact column-wise resampling for visual QA of meniscus level estimates.

Produces scientific bitmap diagnostics from original TIFF pixel data. No
content generation, intensity normalization, or source-file modification.
"""
import argparse
import json
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFont

from gulp_prototype import descriptions


def aligned_strip(rgb, levels, offsets):
    """At output row offset r, sample input y=level[t]+r, no time interpolation."""
    height, width, channels = rgb.shape
    if len(levels) != width:
        raise ValueError('Level count must equal kymograph width')
    output = np.empty((len(offsets), width, channels), dtype=float)
    positions = np.arange(height)
    for t, level in enumerate(levels):
        rows = level + offsets
        if not np.isfinite(rows).all() or rows.min() < 0 or rows.max() > height - 1:
            raise ValueError('Requested alignment strip extends outside image')
        for c in range(channels):
            output[:, t, c] = np.interp(rows, positions, rgb[:, t, c])
    return np.clip(np.round(output), 0, 255).astype('uint8')


def font(size):
    return ImageFont.truetype('C:/Windows/Fonts/arial.ttf', size)


def guide(draw, x, y, width):
    # Short white/black marks keep most of the meniscus visible.
    for start in range(x, x + width, 50):
        draw.line((start,y,start+10,y),fill='white',width=1)
        draw.line((start,y+1,start+10,y+1),fill='black',width=1)


def build(input_json, output):
    result = json.loads(input_json.read_text(encoding='utf-8'))
    root = Path(result['experiment']) / 'results'
    meta = descriptions(root / 'CapillariesDescription.csv')
    output.mkdir(parents=True, exist_ok=True)
    offsets = np.linspace(-10,10,81)
    manifest = dict(source=str(input_json), row_spacing_pixels=.25,
                    interpolation='linear in vertical direction only', capillaries={})
    panels = {}
    for name, data in result['capillaries'].items():
        with Image.open(root / 'bin_60' / meta[name]['kymo']) as image:
            rgb = np.asarray(image.convert('RGB'))
        raw = np.asarray(data['raw']); refined = np.asarray(data['refined'])
        # One shared display-origin adjustment, estimated from the average
        # RAW-aligned chromatic profile. It never changes per time column and
        # is not a new tracking estimate. This places the mean edge near zero.
        initial = aligned_strip(rgb, raw, offsets)
        chroma = initial[:,:,1].astype(float) - initial[:,:,0]
        profile = chroma.mean(axis=1)
        slope = np.diff(profile)
        midpoint = (offsets[:-1]+offsets[1:])/2
        valid = abs(midpoint) <= 4
        location = np.flatnonzero(valid)[np.argmax(slope[valid])]
        origin = float(midpoint[location])
        raw_strip = aligned_strip(rgb, raw+origin, offsets)
        refined_strip = aligned_strip(rgb, refined+origin, offsets)
        Image.fromarray(raw_strip).save(output / f'{name}_topraw_aligned.png')
        Image.fromarray(refined_strip).save(output / f'{name}_correlation_aligned.png')
        panels[name] = (raw_strip,refined_strip)
        manifest['capillaries'][name] = dict(shared_display_offset_pixels=origin,
                                            original_tiff=str(root/'bin_60'/meta[name]['kymo']))

        width = rgb.shape[1] * 8
        left, top = 100, 145
        crop_low = max(0,int(np.floor(min(raw.min(),refined.min()))) - 8)
        crop_high = min(len(rgb),int(np.ceil(max(raw.max(),refined.max()))) + 9)
        original = Image.fromarray(rgb[crop_low:crop_high]).resize((width,(crop_high-crop_low)*3),Image.Resampling.NEAREST)
        panel_height = 162
        original_h = original.height
        height = top+original_h+90+2*(panel_height+90)+35
        canvas = Image.new('RGB',(width+155,height),'white')
        draw = ImageDraw.Draw(canvas)
        draw.text((30,15),f'{name}: does the air-liquid boundary become horizontal?',font=font(25),fill='#222222')
        draw.text((30,52),'Original TIFF colors. Only the vertical position of each column changes in the aligned views.',font=font(17),fill='#333333')
        draw.text((30,78),'Dashed line: shared center. Waviness indicates residual tracking variation; a steady offset is not jitter.',font=font(17),fill='#333333')
        draw.text((left,top-30),f'A. Original kymograph crop (image rows {crop_low}-{crop_high-1}; vertical magnification 3x)',font=font(18),fill='#222222')
        canvas.paste(original,(left,top))
        y0=top+original_h+80
        for index,(strip,label) in enumerate(zip((raw_strip,refined_strip),('B. Columns aligned using saved topraw','C. Columns aligned using correlation-refined levels'))):
            y=y0+index*(panel_height+90)
            draw.text((left,y-31),label,font=font(19),fill='#222222')
            canvas.paste(Image.fromarray(strip).resize((width,panel_height),Image.Resampling.NEAREST),(left,y))
            guide(draw,left,y+panel_height//2,width)
            for val in (-10,0,10):
                yy=y+(val+10)/20*(panel_height-1)
                draw.text((36,yy-9),f'{val:+d} px',font=font(15),fill='#444444')
            for t in range(0,len(raw),20):
                draw.text((left+t*8-8,y+panel_height+8),str(t),font=font(15),fill='#444444')
            draw.text((left+width/2-85,y+panel_height+31),'Elapsed time (minutes)',font=font(16),fill='#333333')
        draw.text((30,height-31),'Aligned views: same vertical scale and interpolation; +/-10 original pixels. Each original time column is 8 display pixels.',font=font(15),fill='#555555')
        canvas.save(output/f'{name}_alignment_comparison.png')
    (output/'alignment_metadata.json').write_text(json.dumps(manifest,indent=2),encoding='utf-8')


if __name__ == '__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--input',required=True,type=Path)
    parser.add_argument('--output',required=True,type=Path)
    args=parser.parse_args()
    build(args.input,args.output)
