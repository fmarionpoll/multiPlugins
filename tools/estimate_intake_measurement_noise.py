"""Describe exported measurement uncertainty and illumination, without detector changes.

Reads original review ZIPs. These estimates include tracking and illumination effects;
they are not sensor read-noise measurements and do not assume independent raw frames.
"""
import csv
import hashlib
import io
import json
from pathlib import Path
import re
from zipfile import ZipFile

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'multiCAFE/target/intake-measurement-noise'


def mad(x):
    x = np.asarray(x, dtype=float)
    x = x[np.isfinite(x)]
    return float(1.4826 * np.median(np.abs(x - np.median(x)))) if len(x) else None


def describe(x):
    x = np.asarray([v for v in x if v is not None], dtype=float)
    x = x[np.isfinite(x)]
    return dict(n=len(x), median=float(np.median(x)), q25=float(np.quantile(x,.25)), q75=float(np.quantile(x,.75))) if len(x) else dict(n=0)


def run_indices(mask):
    indices = np.flatnonzero(mask)
    return np.split(indices, np.flatnonzero(np.diff(indices) != 1) + 1) if len(indices) else []


def estimate(raw, times, eligible, block_size=21):
    """Robust local linear detrending, with no clipping of individual fluctuations."""
    residuals, increments, left, right, blocks = [], [], [], [], []
    selected = np.zeros(len(raw), dtype=bool)
    for run in run_indices(eligible):
        # Balance chunks so a small trailing chunk is not silently discarded.
        if len(run) < 8:
            continue
        for ix in np.array_split(run, max(1,int(np.ceil(len(run)/block_size)))):
            if len(ix) < 8:
                continue
            t = times[ix] - np.mean(times[ix])
            X = np.column_stack([np.ones(len(ix)),t])
            weights = np.ones(len(ix))
            for _ in range(4):
                w = np.sqrt(weights)
                coef = np.linalg.lstsq(X*w[:,None],raw[ix]*w,rcond=None)[0]
                r = raw[ix] - X@coef
                scale = max(mad(r),1e-8)
                weights = np.minimum(1,1.5*scale/np.maximum(np.abs(r),1e-10))
            residuals.extend(r)
            increments.extend(np.diff(raw[ix])-coef[1]*np.diff(times[ix]))
            left.extend(r[:-1]);right.extend(r[1:])
            selected[ix] = True
            blocks.append(dict(first=int(ix[0]),last=int(ix[-1]),points=len(ix),slope=float(coef[1])))
    n = len(residuals)
    sigma = mad(residuals)
    adjusted = sigma * np.sqrt(n/(n-2*len(blocks))) if sigma is not None and n>2*len(blocks) else None
    lag1 = float(np.corrcoef(left,right)[0,1]) if len(left)>=20 and np.std(left)>0 and np.std(right)>0 else None
    inc_sigma=mad(increments)
    absolute=np.abs(increments-np.median(increments)) if increments else np.array([])
    return dict(points=n,blocks=blocks,positionSigma=adjusted,differenceSigma=inc_sigma/np.sqrt(2) if increments else None,
                incrementSigma=inc_sigma,residualLag1=lag1,incrementAbsP95=float(np.quantile(absolute,.95)) if increments else None,
                incrementAbsP99=float(np.quantile(absolute,.99)) if increments else None,
                incrementAbsMax=float(np.max(absolute)) if increments else None,
                largeIncrementFraction=float(np.mean(absolute>3*inc_sigma)) if increments and inc_sigma>0 else None), selected


def eligible_points(raw, image_height, event, control, guard=2, quality=None):
    mask = np.isfinite(raw) & (raw>=10) & (raw<image_height-10)
    if not control:
        for i in np.flatnonzero(event):
            # An event at i involves positions i-1 and i, plus guard columns.
            mask[max(0,i-1-guard):min(len(mask),i+guard+1)] = False
    if quality is not None:
        mask &= quality
    return mask


def illumination(rgb, raw, selected):
    brightness,contrast = [],[]
    for x in np.flatnonzero(selected):
        y = int(round(raw[x]))
        if y-10<0 or y+11>rgb.shape[0]:
            continue
        above = np.mean(rgb[y-10:y-5,x,:],axis=0)
        below = np.mean(rgb[y+6:y+11,x,:],axis=0)
        # Digital RGB means, not linear irradiance or photon counts.
        brightness.append(float(np.mean(rgb[y-10:y+11,x,:])))
        contrast.append(float(np.linalg.norm(above-below)/np.sqrt(3)))
    return dict(brightness=describe(brightness),contrast=describe(contrast))


def rank_correlation(x,y):
    def ranks(v):
        order=np.argsort(v); result=np.empty(len(v),dtype=float)
        for value in np.unique(v):
            ix=np.flatnonzero(np.asarray(v)==value)
            result[ix]=np.mean(np.flatnonzero(np.isin(order,ix)))
        return result
    if len(x)<4 or np.std(x)==0 or np.std(y)==0:
        return None
    return float(np.corrcoef(ranks(x),ranks(y))[0,1])


def main():
    OUT.mkdir(parents=True,exist_ok=True)
    inventory=json.loads((ROOT/'multiCAFE/target/intake-review-cohort/inventory.json').read_text(encoding='utf-8-sig'))
    inputs=[dict(id=re.match(r'\d+',r['archive'])[0].zfill(3),path=r['path'],expectedHash=r['sha256'].lower()) for r in inventory]
    inputs.append(dict(id='023',path='C:/Users/fred/Documents/023-reviewed.zip'))
    result=dict(unit='native kymograph pixels',sources=[],excludedSources=[],capillaries=[],experiments=[],settingsPolicy=dict(fromRecord=25,totalRecords=370,localEvaporation=False,earlier='Not consistently monitored; user believes mostly enabled. Use exported metadata.'),
                method=dict(minRun=8,maximumBlock=21,eventGuard=2,qualityFilter=False,
                    definition='Robust scatter after local linear detrending. Controls keep all finite interior points including apparent intake. Fly capillaries exclude all exported automatic or edited positive bins and their neighbours. No per-fluctuation clipping.',
                    independence='Difference-based sigma assumes independent position errors; lag-one correlation and a direct residual estimator are reported because saved topraw may be smoothed.',
                    illumination='Median digital RGB brightness within ±10 rows of topraw and RMS RGB contrast between rows 6–10 above and below. Not calibrated illumination.',
                    limitations='Residual movement, tracking errors, drift curvature, processing and illumination remain mixed with camera noise. Candidate quiet stretches in fly capillaries are not confirmed non-feeding. Edge and control role are confounded.'))
    for source in sorted(inputs,key=lambda s:s['id']):
        path=Path(source['path']); digest=hashlib.sha256(path.read_bytes()).hexdigest()
        if 'expectedHash' in source and digest!=source['expectedHash']:
            result['excludedSources'].append(dict(id=source['id'],file=str(path),expected=source['expectedHash'],actual=digest,reason='Archive differs from the earlier cohort inventory; excluded rather than mixing versions.'))
            continue
        with ZipFile(path) as z:
            readme=z.read('README.txt').decode('utf-8-sig')
            controls=set(re.findall(r'(?m)^(\d+[LR]) \(no-fly control\)',readme))
            result['sources'].append(dict(id=source['id'],file=str(path),sha256=digest,experiment=readme.splitlines()[0]))
            for name in z.namelist():
                match=re.fullmatch(r'correlation-(\d+[LR])\.csv',name)
                if not match: continue
                cap=match[1];control=cap in controls
                text=z.read(name).decode('utf-8-sig')
                rows=list(csv.DictReader((s for s in text.splitlines() if not s.startswith('#')),delimiter=';'))
                values=lambda key: np.array([float(r[key]) if r[key] else np.nan for r in rows])
                raw=values('raw_px');times=values('elapsed_min')
                event=(values('automatic_net_intake_px')>1e-9)|(values('edited_net_intake_px')>1e-9)|(values('detector_net_before_edits_px')>1e-9)
                quality=np.array([r['reliable']=='true' for r in rows]);quality[0]=np.isfinite(raw[0])
                rgb=np.asarray(Image.open(io.BytesIO(z.read(f'kymograph-{cap}.png'))).convert('RGB'),dtype=float)
                assert rgb.shape[1]==len(raw) and np.all(np.diff(times)>0)
                estimate_ = lambda guard,block,quality_=None: estimate(raw,times,eligible_points(raw,rgb.shape[0],event,control,guard,quality_),block)
                base,selected=estimate_(2,21)
                reliable,_=estimate_(2,21,quality)
                sensitivity=[dict(guard=g,block=b,**{k:v for k,v in estimate_(g,b)[0].items() if k!='blocks'}) for g,b in [(1,21),(3,21),(2,15)]]
                c=dict(experiment=source['id'],cap=cap,position=2*int(cap[:-1])+(cap[-1]=='R'),control=control,
                    duration=float(times[-1]),interval=float(np.median(np.diff(times))),rows=len(rows),
                    localEvaporation='local=true' in text.split('frame;')[0],**base,**illumination(rgb,raw,selected),
                    reliableOnly={k:v for k,v in reliable.items() if k!='blocks'},sensitivity=sensitivity,
                    uncertainFraction=float(np.mean(~quality)),quietUncertainFraction=float(np.mean(~quality[selected])) if selected.any() else None,
                    series=[[float(times[i]),float(raw[i]),bool(selected[i]),bool(quality[i]),bool(event[i])] for i in range(len(raw))])
                result['capillaries'].append(c)
    for source in result['sources']:
        caps=[c for c in result['capillaries'] if c['experiment']==source['id']]
        usable=[c for c in caps if c['positionSigma'] is not None and c['brightness'].get('median') is not None]
        for c in usable:
            c['relativeBrightness']=c['brightness']['median']/np.median([x['brightness']['median'] for x in usable])
            c['relativeNoise']=c['positionSigma']/np.median([x['positionSigma'] for x in usable])
        ordered=sorted(usable,key=lambda c:c['brightness']['median']);q=max(1,len(ordered)//4)
        dark,bright=ordered[:q],ordered[-q:]
        result['experiments'].append(dict(id=source['id'],caps=len(caps),usable=len(usable),
            darkBrightness=describe([c['brightness']['median'] for c in dark]),brightBrightness=describe([c['brightness']['median'] for c in bright]),
            darkNoise=describe([c['positionSigma'] for c in dark]),brightNoise=describe([c['positionSigma'] for c in bright]),
            darkBrightNoiseRatio=float(np.median([c['positionSigma'] for c in dark])/np.median([c['positionSigma'] for c in bright])) if dark and bright else None,
            brightnessNoiseRankCorrelation=rank_correlation([c['brightness']['median'] for c in usable],[c['positionSigma'] for c in usable])))
    for c in result['capillaries']:
        c.setdefault('relativeBrightness',None);c.setdefault('relativeNoise',None)
    usable=[c for c in result['capillaries'] if c['positionSigma'] is not None]
    adequate=[c for c in usable if c['points']>=40 and len(c['blocks'])>=3]
    result['summary']=dict(experiments=len(result['sources']),caps=len(result['capillaries']),usable=len(usable),
        positionSigma=describe([c['positionSigma'] for c in usable]),differenceSigma=describe([c['differenceSigma'] for c in usable]),
        controls=describe([c['positionSigma'] for c in usable if c['control']]),flies=describe([c['positionSigma'] for c in usable if not c['control']]),
        residualLag1=describe([c['residualLag1'] for c in usable]),
        moreDataPositionSigma=describe([c['positionSigma'] for c in adequate]),
        block15PositionSigma=describe([c['sensitivity'][2]['positionSigma'] for c in usable]),
        darkBrightRatio=describe([e['darkBrightNoiseRatio'] for e in result['experiments']]),
        darkerNoisierExperiments=sum(e['darkBrightNoiseRatio'] is not None and e['darkBrightNoiseRatio']>1 for e in result['experiments']))
    (OUT/'noise.json').write_text(json.dumps(result,ensure_ascii=False,allow_nan=False),encoding='utf-8')
    print(json.dumps(result['summary'],indent=2))
    print('Excluded sources:',[(s['id'],s['reason']) for s in result['excludedSources']])


if __name__=='__main__': main()
