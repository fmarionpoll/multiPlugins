"""Offline local dark-meniscus localization. Never writes experimental measures.

Development study on 023: recalculated topraw is a comparison trajectory, not truth.
The locator sees only the original seed/image and a previously estimated noise scale.
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

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'multiCAFE/target/intake-local-position'
CONFIG=dict(radius=8,blurSigma=.65,flank=3,minProminence=3.,maxCandidates=4,
            offsetPenalty=.12,ambiguityRatio=.85,stationaryFraction=.7,stationaryMargin=4,
            feature='local dark-ridge centre, not an assertion of the physical liquid edge')


def ridge_response(rgb):
    grey=rgb.astype(float).mean(axis=2)
    sigma=CONFIG['blurSigma'];r=2
    k=np.exp(-np.arange(-r,r+1)**2/(2*sigma*sigma));k/=k.sum()
    padded=np.pad(grey,((r,r),(0,0)),mode='edge')
    smoothed=sum(k[i]*padded[i:i+len(grey)] for i in range(len(k)))
    f=CONFIG['flank']
    # Positive when the central line is darker than both flanks on average.
    response=np.zeros_like(grey)
    response[f:-f]=(smoothed[:-2*f]+smoothed[2*f:])/2-smoothed[f:-f]
    return grey,response


def localize(rgb,seeds,noise=.3):
    grey,response=ridge_response(rgb)
    height,n=grey.shape
    assert len(seeds)==n
    # Row persistence is a warning only: a genuinely near-stationary meniscus
    # can also satisfy it, so it is not proof of a physical tape border.
    line_rows=np.mean(response>=CONFIG['minProminence'],axis=1)>=CONFIG['stationaryFraction']
    dark_rows=np.mean(grey<65,axis=1)>=CONFIG['stationaryFraction']
    stationary=np.flatnonzero(line_rows|dark_rows)
    lists=[]
    for x,seed in enumerate(seeds):
        if not np.isfinite(seed): lists.append([]);continue
        lo=max(4,int(np.ceil(seed-CONFIG['radius'])))
        hi=min(height-5,int(np.floor(seed+CONFIG['radius'])))
        candidates=[]
        for y in range(lo,hi+1):
            a,b,c=response[y-1:y+2,x]
            if b<a or b<c or b<=0: continue
            denom=a-2*b+c
            shift=np.clip(.5*(a-c)/denom,-.5,.5) if denom<0 else 0.
            row=float(y+shift)
            near=bool(len(stationary) and np.min(np.abs(stationary-row))<=CONFIG['stationaryMargin'])
            candidates.append(dict(row=row,strength=float(b),stationary=near))
        candidates.sort(key=lambda c:c['strength'],reverse=True)
        lists.append(candidates[:CONFIG['maxCandidates']])
    proposed=np.array(seeds,dtype=float).copy()
    # A small bounded cost encourages continuity of offset, not zero motion.
    # Original step sizes are a soft guide only; image evidence can override it.
    scale=max(.5,2*noise)
    costs,parents=[],[]
    for x,candidates in enumerate(lists):
        if not candidates: costs.append(np.array([]));parents.append([]);continue
        strengths=np.array([c['strength'] for c in candidates])
        unary=-np.log(np.maximum(strengths/strengths.max(),1e-6))
        cost=unary.copy();back=[-1]*len(candidates)
        if x and len(costs[x-1]):
            previous=np.array([c['row']-seeds[x-1] for c in lists[x-1]])
            for j,c in enumerate(candidates):
                delta=(c['row']-seeds[x]-previous)/scale
                transition=CONFIG['offsetPenalty']*np.minimum(delta*delta,9.)
                scores=costs[x-1]+transition
                back[j]=int(np.argmin(scores));cost[j]+=scores[back[j]]
        costs.append(cost);parents.append(back)
    chosen=[None]*n
    x=n-1
    while x>=0:
        if not lists[x]: x-=1;continue
        j=int(np.argmin(costs[x]))
        while x>=0 and lists[x]:
            chosen[x]=lists[x][j]
            proposed[x]=chosen[x]['row']
            j=parents[x][j];x-=1
            if j<0: break
    accepted=np.zeros(n,dtype=bool);reasons=[]
    margins=[]
    for x,c in enumerate(chosen):
        reasons_=[]
        if c is None: reasons_.append('no_local_ridge')
        else:
            if c['strength']<CONFIG['minProminence']:reasons_.append('weak_ridge')
            if c['stationary']:reasons_.append('persistent_horizontal_feature_nearby')
            if abs(c['row']-seeds[x])>CONFIG['radius']-.75:reasons_.append('search_boundary')
            alternatives=[q for q in lists[x] if abs(q['row']-c['row'])>2. and q['strength']>=CONFIG['ambiguityRatio']*c['strength']]
            if alternatives: reasons_.append('competing_local_ridge')
        accepted[x]=not reasons_
        reasons.append(reasons_ or ['image_ridge_supported'])
        margins.append(float(c['strength']) if c else None)
    # No unsupported position is silently substituted for saved topraw.
    safe=np.where(accepted,proposed,seeds)
    return dict(proposed=proposed,safe=safe,accepted=accepted,reasons=reasons,strength=margins,
                stationaryRows=stationary.tolist())


def read_archive(path):
    data={}
    with ZipFile(path) as z:
        readme=z.read('README.txt').decode('utf-8-sig')
        controls=set(re.findall(r'(?m)^(\d+[LR]) \(no-fly control\)',readme))
        for name in z.namelist():
            match=re.fullmatch(r'correlation-(\d+[LR])\.csv',name)
            if not match:continue
            text=z.read(name).decode('utf-8-sig')
            rows=list(csv.DictReader((l for l in text.splitlines() if not l.startswith('#')),delimiter=';'))
            png=z.read(f'kymograph-{match[1]}.png')
            data[match[1]]=dict(rows=rows,raw=np.array([float(r['raw_px']) for r in rows]),
                image=np.asarray(Image.open(io.BytesIO(png)).convert('RGB')),imageHash=hashlib.sha256(png).hexdigest(),control=match[1] in controls)
    return data


def metric(a,b,mask=None):
    mask=np.isfinite(a)&np.isfinite(b) if mask is None else mask&np.isfinite(a)&np.isfinite(b)
    error=a[mask]-b[mask]
    return dict(n=int(mask.sum()),medianAbsolute=float(np.median(np.abs(error))),meanAbsolute=float(np.mean(np.abs(error))),
                signedMedian=float(np.median(error)),p95Absolute=float(np.quantile(np.abs(error),.95))) if len(error) else dict(n=0)


def clean(x):
    if isinstance(x,dict):return {k:clean(v) for k,v in x.items()}
    if isinstance(x,list):return [clean(v) for v in x]
    if isinstance(x,np.ndarray):return clean(x.tolist())
    if isinstance(x,np.generic):return clean(x.item())
    if isinstance(x,float) and not np.isfinite(x):return None
    return x


def main():
    OUT.mkdir(parents=True,exist_ok=True)
    sources=[Path('C:/Users/fred/Documents')/f'023-{s}.zip' for s in ['original','corrected-auto','reviewed']]
    previous=json.loads((ROOT/'multiCAFE/target/intake-023-comparison/comparison.json').read_text(encoding='utf-8'))
    for p,record in zip(sources,previous['sources']):assert hashlib.sha256(p.read_bytes()).hexdigest()==record['sha256']
    # Record the exact configuration/source before reading comparison traces.
    frozen=dict(config=CONFIG,sourceHash=hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),study='023 development study, not held-out validation')
    (OUT/'frozen-config.json').write_text(json.dumps(frozen,indent=2),encoding='utf-8')
    original,corrected,reviewed=[read_archive(p) for p in sources]
    noises=json.loads((ROOT/'multiCAFE/target/intake-measurement-noise/noise.json').read_text(encoding='utf-8'))
    noise_map={c['cap']:c for c in noises['capillaries'] if c['experiment']=='023'}
    result=dict(frozen=frozen,sources=previous['sources'],capillaries=[])
    from estimate_intake_measurement_noise import estimate
    for cap in original:
        a,b,c=original[cap],corrected[cap],reviewed[cap]
        assert a['imageHash']==b['imageHash']==c['imageHash'] and np.array_equal(b['raw'],c['raw'])
        times=np.array([float(r['elapsed_min']) for r in a['rows']])
        trial=localize(a['image'],a['raw'],noise_map[cap]['positionSigma'] or .3)
        flags=trial['accepted'];pair=flags[1:]&flags[:-1]
        # Diagnostic placement-only baseline: a single image-derived translation
        # preserves every displacement exactly. It is not an accepted correction.
        offset=float(np.median(trial['proposed'][flags]-a['raw'][flags])) if flags.any() else 0.
        translated=a['raw']+offset
        edits=np.array([r['manual_override']=='true' and float(r['edited_net_intake_px'])>0 for r in c['rows']])
        bottoms=np.array([float(r['intake_bottom_px']) for r in c['rows']])
        quiet=np.array([p[2] for p in noise_map[cap]['series']],dtype=bool)
        def scatter(level):return estimate(level,times,quiet)[0]['positionSigma']
        entry=dict(cap=cap,control=a['control'],rows=len(times),supported=int(flags.sum()),constantOffset=offset,
            toRecalculated=dict(original=metric(a['raw'],b['raw']),proposed=metric(trial['proposed'],b['raw']),
                translated=metric(translated,b['raw']),
                supportedOriginal=metric(a['raw'],b['raw'],flags),supportedTrial=metric(trial['proposed'],b['raw'],flags),
                incrementsOriginal=metric(np.diff(a['raw']),np.diff(b['raw']),pair),incrementsTrial=metric(np.diff(trial['proposed']),np.diff(b['raw']),pair)),
            toEditedBottom=dict(original=metric(a['raw'],bottoms,edits),recalculated=metric(b['raw'],bottoms,edits),
                translated=metric(translated,bottoms,edits),
                safeTrial=metric(trial['safe'],bottoms,edits),supportedTrial=metric(trial['proposed'],bottoms,edits&flags)),
            quietScatter=dict(original=scatter(a['raw']),recalculated=scatter(b['raw']),safeTrial=scatter(trial['safe'])),
            reasons=dict(),stationaryRows=trial['stationaryRows'],
            series=[[times[i],a['raw'][i],b['raw'][i],trial['proposed'][i],trial['safe'][i],bool(flags[i]),trial['reasons'][i],bottoms[i] if edits[i] else None,translated[i]] for i in range(len(times))])
        from collections import Counter
        entry['reasons']=dict(Counter(r for rs in trial['reasons'] for r in rs if r!='image_ridge_supported'))
        result['capillaries'].append(entry)
    total=sum(c['rows'] for c in result['capillaries']);supported=sum(c['supported'] for c in result['capillaries'])
    result['summary']=dict(capillaries=len(original),points=total,supported=supported,coverage=supported/total,
        medianOriginalError=float(np.median([c['toRecalculated']['original']['medianAbsolute'] for c in result['capillaries']])),
        medianProposedError=float(np.median([c['toRecalculated']['proposed']['medianAbsolute'] for c in result['capillaries']])),
        medianSupportedTrialError=float(np.median([c['toRecalculated']['supportedTrial']['medianAbsolute'] for c in result['capillaries'] if c['toRecalculated']['supportedTrial']['n']])))
    (OUT/'results.json').write_text(json.dumps(clean(result),ensure_ascii=False,allow_nan=False),encoding='utf-8')
    print(json.dumps(result['summary'],indent=2))
    print('1L',json.dumps(next({k:v for k,v in c.items() if k not in ['series','stationaryRows']} for c in result['capillaries'] if c['cap']=='1L'),indent=2))


if __name__=='__main__':main()
