"""Read-only development study: step registration and lower-interface diagnostics.

No automatic intake search, correction, save or export to experiment directories.
Prespecified events are already detected in the reviewed archive.
"""
import csv
import hashlib
import io
import json
from pathlib import Path
from zipfile import ZipFile

import numpy as np
from PIL import Image

from intake_local_position_trial import read_archive, localize, CONFIG as RIDGE_CONFIG, clean

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'multiCAFE/target/intake-143-step-study'
EXPERIMENT=Path('F:/2020_Perrine/article_fmp/article1_court/data/CS/30+_vs_30/17-10-26_Caffeine/cam01_1mM/results')
CONFIG=dict(events=[39,52],comparisonWindows=[33,46],flank=5,halfWidth=8,searchCorrection=5.,shiftGrid=.05,withinFlankCorrection=2.,
            minCorrelation=.9,study='143/6L inspected development example; no independent ground truth')


def robust_fit(values,times,first):
    before=times<first
    X=np.column_stack([np.ones(len(times)),times-first,(~before).astype(float)])
    w=np.ones(len(times))
    for _ in range(4):
        coef=np.linalg.lstsq(X*np.sqrt(w[:,None]),values*np.sqrt(w),rcond=None)[0]
        residual=values-X@coef
        sigma=max(1e-7,1.4826*np.median(np.abs(residual-np.median(residual))))
        w=np.minimum(1.,1.5*sigma/np.maximum(np.abs(residual),1e-10))
    return dict(step=float(coef[2]),slope=float(coef[1]),residualSigma=float(sigma))


def sample_profile(rgb,columns,rows):
    if min(rows)<0 or max(rows)>rgb.shape[0]-1:
        return None
    grid=np.arange(rgb.shape[0])
    samples=[np.column_stack([np.interp(rows,grid,rgb[:,x,c]) for c in range(3)]) for x in columns]
    return np.median(samples,axis=0)


def correlation(a,b):
    a=(a-np.mean(a,axis=0)).ravel();b=(b-np.mean(b,axis=0)).ravel()
    denom=np.linalg.norm(a)*np.linalg.norm(b)
    return float(a@b/denom) if denom>1e-10 else 0.


def register(rgb,raw,times,first,flank=5,half_width=8,position_noise=.3):
    before=np.arange(first-flank,first);after=np.arange(first,first+flank)
    if before[0]<0 or after[-1]>=len(raw):return None
    ix=np.r_[before,after]
    boundary=.5*(times[first]+times[first-1])
    fit=robust_fit(raw[ix],times[ix],boundary)
    centre=float(np.median(raw[before]));expected=float(np.median(raw[after])-np.median(raw[before]))
    rows=centre+np.arange(-half_width,half_width+1)
    template=sample_profile(rgb,before,rows)
    if template is None:return None
    shifts=np.arange(expected-CONFIG['searchCorrection'],expected+CONFIG['searchCorrection']+.001,CONFIG['shiftGrid'])
    scores=[];intensities=[];gradients=[]
    for shift in shifts:
        profile=sample_profile(rgb,after,rows+shift)
        if profile is None:scores.append(-1.);intensities.append(-1.);gradients.append(-1.);continue
        intensity=correlation(template,profile);gradient=correlation(np.diff(template,axis=0),np.diff(profile,axis=0))
        scores.append(.5*(intensity+gradient));intensities.append(intensity);gradients.append(gradient)
    best=int(np.argmax(scores));shift=float(shifts[best]);score=float(scores[best])
    dt=float(np.median(times[after])-np.median(times[before]))
    raw_drift_amount=shift-fit['slope']*dt
    # Width of the near-best score is an ambiguity diagnostic, not a confidence interval.
    near=shifts[np.array(scores)>=score-.005]
    matched=sample_profile(rgb,after,rows+shift)
    # Estimate within-flank drift from image registration as well. The saved
    # trace supplies search centres only, not the resulting displacement.
    within=[];within_scores=[]
    for columns,anchor,locations in [(before,template,rows),(after,matched,rows+shift)]:
        seed_centre=np.median(raw[columns])
        for column in columns:
            guess=raw[column]-seed_centre
            offsets=np.arange(guess-CONFIG['withinFlankCorrection'],guess+CONFIG['withinFlankCorrection']+.001,CONFIG['shiftGrid'])
            cs=[]
            for offset in offsets:
                p=sample_profile(rgb,[column],locations+offset)
                cs.append(.5*(correlation(anchor,p)+correlation(np.diff(anchor,axis=0),np.diff(p,axis=0))) if p is not None else -1.)
            j=int(np.argmax(cs));within.append(float(offsets[j]));within_scores.append(float(cs[j]))
    image_fit=robust_fit(np.array(within),times[ix],boundary)
    amount=shift-image_fit['slope']*dt
    slopes=[];sxx=[]
    for indices,offsets in [(before,within[:flank]),(after,within[flank:])]:
        t=times[indices]-np.mean(times[indices])
        slopes.append(float(t@np.array(offsets)/(t@t)))
        sxx.append(float(t@t))
    slope_difference=slopes[1]-slopes[0]
    # Diagnostic tolerance informed by position scatter, not a calibrated
    # confidence test or feeding-acceptance threshold.
    slope_tolerance=3*position_noise*np.sqrt(1/sxx[0]+1/sxx[1])
    gradnorm=np.linalg.norm(np.diff(template,axis=0),axis=1)
    peak=int(np.argmax(gradnorm));feature=float((rows[peak]+rows[peak+1])/2)
    reasons=[]
    if score<CONFIG['minCorrelation']:reasons.append('weak_profile_match')
    if min(within_scores)<.85:reasons.append('weak_within_flank_match')
    if abs(slope_difference)>slope_tolerance:reasons.append('flank_drifts_disagree')
    if best<2 or best>len(shifts)-3:reasons.append('search_boundary')
    alternative=[v for s,v in zip(shifts,scores) if abs(s-shift)>1.]
    margin=score-max(alternative) if alternative else None
    if margin is not None and margin<.01:reasons.append('broad_or_competing_match')
    return dict(first=first,flank=flank,halfWidth=half_width,before=before.tolist(),after=after.tolist(),expectedShift=expected,
                imageShift=shift,stepAfterLocalDrift=float(amount),stepAfterRawDrift=float(raw_drift_amount),rawStepFit=fit['step'],
                localDrift=image_fit['slope'],rawLocalDrift=fit['slope'],rawFitSigma=fit['residualSigma'],withinFlankMinScore=min(within_scores),
                beforeDrift=slopes[0],afterDrift=slopes[1],driftDifference=slope_difference,driftTolerance=float(slope_tolerance),
                score=score,intensityCorrelation=float(intensities[best]),gradientCorrelation=float(gradients[best]),
                nearBestWidth=float(near[-1]-near[0]),alternativeMargin=margin,reasons=reasons,
                featureBefore=feature,featureAtBoundary=float(feature+image_fit['slope']*(boundary-np.median(times[before]))),
                profile=[[float(r),*a.tolist(),*b.tolist()] for r,a,b in zip(rows,template,matched)],
                scan=[[float(s),float(v)] for s,v in zip(shifts,scores)])


def parse_measures(blob):
    result={};section=None
    for line in blob.decode('utf-8-sig').splitlines():
        if line.startswith('#;'):
            section=line.split(';')[1]
        elif section in ['TOPRAW','BOTTOMLEVEL'] and line and not line.startswith('#'):
            parts=line.split(';')
            if len(parts)<3 or not parts[2]:continue
            n=int(parts[2]);values=np.array([float(v) for v in parts[3:3+n]])
            assert len(values)==n
            result.setdefault(parts[0],{})[section]=values
    return result


def lower_summary(raw,height):
    start=float(np.median(raw[:10]));end=float(np.median(raw[-10:]))
    return dict(firstMedian=start,lastMedian=end,change=end-start,min=float(raw.min()),max=float(raw.max()),
                selectedStartMedian=float(np.median(raw[20:30])),selectedEndMedian=float(np.median(raw[55:66])),
                nearImageBorderFraction=float(np.mean(raw>=height-3)),constant=bool(np.ptp(raw)==0))


def main():
    OUT.mkdir(parents=True,exist_ok=True)
    archive=Path('C:/Users/fred/Documents/143-intake-experiment-review.zip')
    noise=json.loads((ROOT/'multiCAFE/target/intake-measurement-noise/noise.json').read_text(encoding='utf-8'))
    provenance=next(s for s in noise['sources'] if s['id']=='143')
    assert hashlib.sha256(archive.read_bytes()).hexdigest()==provenance['sha256']
    frozen=dict(config=CONFIG,sourceHash=hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
                ridgeSourceHash=hashlib.sha256((ROOT/'tools/intake_local_position_trial.py').read_bytes()).hexdigest(),ridgeConfig=RIDGE_CONFIG)
    (OUT/'frozen-config.json').write_text(json.dumps(frozen,indent=2),encoding='utf-8')
    data=read_archive(archive);target=data['6L'];raw=target['raw']
    times=np.array([float(r['elapsed_min']) for r in target['rows']])
    sigma=next(c['positionSigma'] for c in noise['capillaries'] if c['experiment']=='143' and c['cap']=='6L')
    pointwise=localize(target['image'],raw,sigma)
    events=[]
    for first in CONFIG['events']+CONFIG['comparisonWindows']:
        main_fit=register(target['image'],raw,times,first,position_noise=sigma)
        main_fit['kind']='visible_step' if first in CONFIG['events'] else 'comparison_window'
        main_fit['rawImmediate']=float(raw[first]-raw[first-1])
        main_fit['pointwiseImmediate']=float(pointwise['proposed'][first]-pointwise['proposed'][first-1])
        main_fit['archiveGrossAmplitude']=float(target['rows'][first]['candidate_amplitude_px'])
        main_fit['archiveEditedNet']=float(target['rows'][first]['edited_net_intake_px'])
        main_fit['archiveAction']=target['rows'][first]['edit_action']
        main_fit['sensitivity']=[{k:v for k,v in register(target['image'],raw,times,first,f,h,sigma).items() if k not in ['profile','scan']}
                                 for f,h in [(3,8),(7,8),(5,6),(5,10)]]
        events.append(main_fit)
    blob=(EXPERIMENT/'CapillariesMeasures.csv').read_bytes();saved=parse_measures(blob)
    timing=(EXPERIMENT/'FrameTimeScale.csv').read_bytes()
    table=list(csv.reader((l for l in timing.decode('utf-8-sig').splitlines() if not l.startswith('#')),delimiter=';'))
    saved_times=np.array([float(r[1])/60000 for r in table])
    assert np.array_equal(saved_times,times)
    lower=[]
    for cap in ['6L']+[c for c in data if data[c]['control']]:
        a=data[cap];bottom=saved[cap]['BOTTOMLEVEL'];top=saved[cap]['TOPRAW'];n=len(a['raw'])
        assert len(bottom)==len(top)==n
        # Check every source image used for lower overlays against archived pixels.
        filename=f'line{cap[:-1]}{1 if cap[-1]=="L" else 2}.tiff'
        source_image=np.asarray(Image.open(EXPERIMENT/'bin_60'/filename).convert('RGB'))
        assert np.array_equal(source_image,a['image'])
        lower.append(dict(cap=cap,control=a['control'],height=a['image'].shape[0],**lower_summary(bottom,a['image'].shape[0]),
                    savedTopToArchiveMedian=float(np.median(np.abs(top-a['raw']))),savedTopToArchiveMax=float(np.max(np.abs(top-a['raw']))),
                    savedTopMatchesArchive=bool(np.allclose(top,a['raw'],rtol=0,atol=1e-9)),imagePixelsMatch=True,
                    series=[[float(times[i]),float(bottom[i]),float(top[i]),float(a['raw'][i])] for i in range(n)]))
    for event in events:
        y=saved['6L']['BOTTOMLEVEL']
        event['lowerBefore']=float(np.median(y[event['before']]))
        event['lowerAfter']=float(np.median(y[event['after']]))
    control_windows=[]
    for cap in data:
        if not data[cap]['control']:continue
        for first in CONFIG['events']:
            control_sigma=next(c['positionSigma'] for c in noise['capillaries'] if c['experiment']=='143' and c['cap']==cap)
            r=register(data[cap]['image'],data[cap]['raw'],times,first,position_noise=control_sigma or .3)
            control_windows.append(dict(cap=cap,**{k:v for k,v in r.items() if k not in ['profile','scan','before','after']}))
    result=dict(frozen=frozen,archive=provenance,sourceDirectory=str(EXPERIMENT),controlWindows=control_windows,
                measureFileHash=hashlib.sha256(blob).hexdigest(),timeFileHash=hashlib.sha256(timing).hexdigest(),
                caveat='Current saved bottom levels are not frozen inside the review ZIP. Images, native times and saved upper levels are checked against the archive. Constant or border-adjacent bottom tracks do not establish physical stability.',
                localEvaporationInArchive='local=true' in ZipFile(archive).read('correlation-6L.csv').decode().split('frame;')[0],
                events=events,lower=lower,topSeries=[[float(times[i]),float(raw[i]),float(pointwise['proposed'][i]),bool(pointwise['accepted'][i])] for i in range(len(raw))])
    (OUT/'results.json').write_text(json.dumps(clean(result),ensure_ascii=False,allow_nan=False),encoding='utf-8')
    print('Steps:',json.dumps([{k:e[k] for k in ['first','kind','rawImmediate','rawStepFit','imageShift','stepAfterLocalDrift','score','reasons','archiveGrossAmplitude']} for e in events],indent=2))
    print('Lower:',json.dumps([{k:v for k,v in c.items() if k!='series'} for c in lower],indent=2))


if __name__=='__main__':main()
