"""Render selected time snippets using the same alignment as the full displays."""
import argparse
import json
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

from gulp_alignment import aligned_strip, font, guide
from gulp_prototype import descriptions


def build(input_json, output):
    result = json.loads(input_json.read_text(encoding='utf-8'))
    root = Path(result['experiment'])/'results'
    meta = descriptions(root/'CapillariesDescription.csv')
    snippets = [('2L',20,50),('3L',40,70),('7L',80,110),
                ('8R',60,90),('0L',70,100),('9R',40,70)]
    output.mkdir(parents=True,exist_ok=True)
    offsets=np.linspace(-8,8,65)
    width,height=1530,170+len(snippets)*245
    gallery=Image.new('RGB',(width,height),'white')
    draw=ImageDraw.Draw(gallery)
    draw.text((30,16),'Meniscus alignment: six additional snippets',font=font(27),fill='#222222')
    draw.text((30,54),'Same experiment: cam01_1mM, 26 October 2017. Every snippet spans 30 minutes; colors are unchanged.',font=font(18),fill='#333333')
    draw.text((30,82),'Aligned panels show +/-8 native pixels. Dashed line = shared center; orange ticks = rejected correlation pairs.',font=font(18),fill='#333333')
    positions=[65,560,1055]
    for x,label in zip(positions,['A. Original crop','B. Aligned using topraw','C. Aligned using refined levels']):
        draw.text((x,120),label,font=font(20),fill='#222222')
    manifest=[]
    for i,(name,start,end) in enumerate(snippets):
        cap=result['capillaries'][name]
        raw=np.asarray(cap['raw'])[start:end+1]
        refined=np.asarray(cap['refined'])[start:end+1]
        with Image.open(root/'bin_60'/meta[name]['kymo']) as image:
            rgb=np.asarray(image.convert('RGB'))[:,start:end+1]
        initial=aligned_strip(rgb,raw,offsets)
        profile=(initial[:,:,1].astype(float)-initial[:,:,0]).mean(axis=1)
        mid=(offsets[:-1]+offsets[1:])/2
        ids=np.flatnonzero(abs(mid)<=4)
        origin=float(mid[ids[np.argmax(np.diff(profile)[ids])]])
        strips=[aligned_strip(rgb,level+origin,offsets) for level in (raw,refined)]
        lo=max(0,int(np.floor(min(raw.min(),refined.min())))-5)
        hi=min(len(rgb),int(np.ceil(max(raw.max(),refined.max())))+6)
        bitmaps=[rgb[lo:hi]]+strips
        top=177+i*245
        label=f'{name} | {start}-{end} min | '+('empty cage' if meta[name]['flies']==0 else 'occupied cage')
        draw.text((65,top-28),label,font=font(19),fill='#222222')
        rejected=[r['frame'] for r in cap['registration'] if start<r['frame']<=end and not r['accepted']]
        for j,(x,bitmap) in enumerate(zip(positions,bitmaps)):
            native=Image.fromarray(bitmap)
            native.save(output/f'{name}_{start}-{end}_{["original","topraw","refined"][j]}.png')
            gallery.paste(native.resize((440,156),Image.Resampling.NEAREST),(x,top))
            if j:
                guide(draw,x,top+78,440)
            for t in range(start,end+1,5):
                xx=x+(t-start+.5)*440/len(raw)
                draw.text((xx-10,top+168),str(t),font=font(15),fill='#444444')
            draw.text((x+130,top+190),'Elapsed time (minutes)',font=font(15),fill='#444444')
        for t in rejected:
            x=positions[2]+(t-start+.5)*440/len(raw)
            draw.line((x,top+157,x,top+165),fill='#bd5d0b',width=2)
        for x in positions[1:]:
            for relative,text in [(0,'-8'),(78,'0'),(156,'+8')]:
                draw.text((x-29,top+relative-9),text,font=font(13),fill='#555555')
        manifest.append(dict(capillary=name,start_minute=start,end_minute=end,
                             original_rows=[lo,hi-1],shared_offset_pixels=origin,
                             rejected_correlation_pairs=rejected))
    draw.text((30,height-28),'Original crops are fitted to their panels; compare B and C at identical scales. No smoothing along time is used in this display.',font=font(16),fill='#555555')
    gallery.save(output/'alignment_gallery.png')
    (output/'snippets.json').write_text(json.dumps(manifest,indent=2),encoding='utf-8')


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--input',type=Path,required=True)
    parser.add_argument('--output',type=Path,required=True)
    args=parser.parse_args()
    build(args.input,args.output)
