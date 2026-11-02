"""Offline abrupt-step supplement. Calibrate controls, freeze, then check other ZIPs.

Edited events are evaluation annotations only. No saved amplitudes are replaced.
"""
import hashlib
import json
from pathlib import Path
import sys
import time
from collections import Counter

import numpy as np

from intake_local_position_trial import read_archive,clean
from intake_profile_registration import register,position_scatter,stationary_edges

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'multiCAFE/target/intake-profile-scan'
DEVELOPMENT=['006','007','010','011','012','014','015','016','143','146']
CHECK=['008','009','013','017','147','023']
CONFIG=dict(development=DEVELOPMENT,check=CHECK,flank=5,halfWidth=8,minAmount=.4,minBoundary=.25,boundaryFraction=.5,
            minimumNormalisedStep=5.,controlQuantile=.999,controlMaxMargin=1.01,
            stationaryGradient=6.,stationaryFraction=.8,stationaryDistance=3.,automaticNeighbour=1,supplementNeighbour=1,
            scope='Abrupt step locations only. Baseline automatic bins retained; adjacent and distributed movement deferred.',
            annotationPolicy='Edits are used only after candidate registration/selection, never to set noise, gates or limits.',
            limitPolicy='Compare an empirical control-tail limit with a stricter maximum-control limit; both frozen before check.')


def hashes():
    names=['intake_profile_scan.py','intake_profile_registration.py','intake_143_step_study.py','intake_local_position_trial.py']
    return {name:hashlib.sha256((ROOT/'tools'/name).read_bytes()).hexdigest() for name in names}


def image_gate(candidate,stationary):
    reasons=candidate['reasons'].copy()
    if candidate['step']<CONFIG['minAmount']:reasons.append('small_or_nonpositive_step')
    if candidate['observedBoundary']<max(CONFIG['minBoundary'],CONFIG['boundaryFraction']*candidate['step']):
        reasons.append('movement_not_concentrated_at_boundary')
    if len(stationary) and min(np.min(np.abs(stationary-candidate['featureBefore'])),np.min(np.abs(stationary-candidate['featureAfter'])))<=CONFIG['stationaryDistance']:
        reasons.append('persistent_horizontal_edge_near_feature')
    return reasons


def select(candidates,baseline,limit):
    selected=[];rejected=Counter()
    for candidate in sorted(candidates,key=lambda c:(-c['normalisedStep'],c['first'])):
        first=candidate['first']
        if candidate['gates']:
            rejected.update(candidate['gates']);continue
        if candidate['normalisedStep']<limit:rejected['control_limit']+=1;continue
        lo=max(0,first-CONFIG['automaticNeighbour']);hi=min(len(baseline),first+CONFIG['automaticNeighbour']+1)
        if np.any(baseline[lo:hi]):rejected['automatic_neighbour']+=1;continue
        if any(abs(first-c['first'])<=CONFIG['supplementNeighbour'] for c in selected):
            rejected['neighbouring_supplement']+=1;continue
        selected.append(candidate)
    return sorted(selected,key=lambda c:c['first']),dict(rejected)


def evaluate(rows,baseline,chosen):
    added=np.array([r['edit_action']=='added' for r in rows])&~baseline
    zero=np.array([r['manual_override']=='true' and float(r['edited_net_intake_px'])<=0 for r in rows])
    new=np.zeros(len(rows),bool)
    for c in chosen:new[c['first']]=True
    unmatched=set(np.flatnonzero(added))
    matches=[]
    # Exact matches get priority, then a one-to-one match within one bin.
    for radius in [0,1]:
        for i in np.flatnonzero(new):
            if any(m[0]==i for m in matches):continue
            eligible=[j for j in unmatched if abs(i-j)<=radius]
            if eligible:
                j=min(eligible,key=lambda j:(abs(i-j),j));unmatched.remove(j);matches.append((int(i),int(j)))
    searchable=np.zeros(len(rows),bool);searchable[5:len(rows)-4]=True
    return dict(addedMissing=int(added.sum()),addedSearchable=int(np.sum(added&searchable)),
                recoveredExact=int(np.sum(added&new)),recoveredWithinOne=len(matches),
                newBins=int(new.sum()),newUnannotated=int(np.sum(new&~added&~zero)),newAtZeroOverride=int(np.sum(new&zero)),
                baselineBins=int(baseline.sum()),baselineAtZeroOverride=int(np.sum(baseline&zero)),baselineLost=0,
                oneToOneMatches=matches)


def sources():
    metadata=json.loads((ROOT/'multiCAFE/target/intake-measurement-noise/noise.json').read_text(encoding='utf-8'))
    return {s['id']:s for s in metadata['sources']}


def scan_experiment(source):
    digest=hashlib.sha256(Path(source['file']).read_bytes()).hexdigest()
    if digest!=source['sha256']:raise ValueError('Archive changed: '+source['file'])
    data=read_archive(Path(source['file']));result=[]
    for cap,a in data.items():
        path=OUT/'scan'/f'{source["id"]}-{cap}.json'
        key=dict(archive=digest,engines=hashes(),config=CONFIG)
        if path.exists():
            cached=json.loads(path.read_text(encoding='utf-8'))
            if cached['key']==key:result.append(cached);continue
        times=np.array([float(r['elapsed_min']) for r in a['rows']]);raw=a['raw']
        noise=position_scatter(raw,times)
        edges=stationary_edges(a['image'],CONFIG['stationaryGradient'],CONFIG['stationaryFraction'])
        candidates=[];unavailable=0
        for first in range(CONFIG['flank'],len(raw)-CONFIG['flank']+1):
            c=register(a['image'],raw,times,first,noise)
            if c is None:unavailable+=1;continue
            c['gates']=image_gate(c,edges);candidates.append(c)
        record=dict(key=key,experiment=source['id'],cap=cap,control=a['control'],rows=len(raw),noise=noise,
                    unavailable=unavailable,candidates=candidates)
        path.parent.mkdir(parents=True,exist_ok=True)
        path.write_text(json.dumps(clean(record),allow_nan=False),encoding='utf-8')
        result.append(record)
    print(f'{source["id"]}: scanned {len(result)} capillaries',flush=True)
    return result


def summarize(results):
    keys=['addedMissing','addedSearchable','recoveredExact','recoveredWithinOne','newBins','newUnannotated','newAtZeroOverride','baselineBins','baselineAtZeroOverride','baselineLost']
    result={}
    for scope in ['all','flies','controls']:
        selected=[r for r in results if scope=='all' or r['control']==(scope=='controls')]
        result[scope]=dict(capillaries=len(selected),**{k:sum(r['metrics'][k] for r in selected) for k in keys})
    return result


def main(phase):
    if phase not in ['development','check']:raise ValueError('Use development or check')
    OUT.mkdir(parents=True,exist_ok=True);start=time.monotonic();provenance=sources()
    selected=DEVELOPMENT if phase=='development' else CHECK
    if phase=='development':
        manifest=dict(config=CONFIG,engines=hashes(),sources=[provenance[e] for e in DEVELOPMENT],
                      validation='Previously inspected experiments; separate detector check, not untouched external validation.')
        (OUT/'development-manifest.json').write_text(json.dumps(manifest,indent=2),encoding='utf-8')
    else:
        frozen=json.loads((OUT/'frozen-config.json').read_text(encoding='utf-8'))
        if frozen['engines']!=hashes() or frozen['config']!=CONFIG:raise ValueError('Implementation changed after calibration')
    scans=[]
    for experiment in selected:scans.extend(scan_experiment(provenance[experiment]))
    if phase=='development':
        scores=[max(0,c['normalisedStep']) if not c['gates'] else 0. for s in scans if s['control'] for c in s['candidates']]
        if not scores:raise ValueError('No control windows')
        limits=dict(tail=max(CONFIG['minimumNormalisedStep'],float(np.quantile(scores,CONFIG['controlQuantile']))),
                    maximum=max(CONFIG['minimumNormalisedStep'],float(max(scores))*CONFIG['controlMaxMargin']))
        frozen=dict(**manifest,limits=limits,controlWindows=len(scores),positiveGatedControls=sum(v>0 for v in scores),
                    calibration='Image-gated control scores include zero for rejected windows; no event-edit labels used.')
        (OUT/'frozen-config.json').write_text(json.dumps(frozen,indent=2),encoding='utf-8')
        print('FROZEN LIMITS',limits,flush=True)
    results={name:[] for name in frozen['limits']}
    for experiment in selected:
        if hashlib.sha256(Path(provenance[experiment]['file']).read_bytes()).hexdigest()!=provenance[experiment]['sha256']:
            raise ValueError('Archive changed during scan: '+experiment)
        data=read_archive(Path(provenance[experiment]['file']))
        for scan in [s for s in scans if s['experiment']==experiment]:
            cap=scan['cap'];a=data[cap];baseline=np.array([float(r['automatic_net_intake_px'])>0 for r in a['rows']])
            for name,limit in frozen['limits'].items():
                chosen,rejected=select(scan['candidates'],baseline,limit)
                metrics=evaluate(a['rows'],baseline,chosen)
                results[name].append(dict(experiment=experiment,cap=cap,control=scan['control'],rows=scan['rows'],
                    noise=scan['noise'],unavailable=scan['unavailable'],metrics=metrics,chosen=chosen,rejected=rejected))
    summaries={name:summarize(rs) for name,rs in results.items()}
    payload=dict(phase=phase,frozenHash=hashlib.sha256((OUT/'frozen-config.json').read_bytes()).hexdigest(),
                 sources=[provenance[e] for e in selected],results=results,summaries=summaries,seconds=time.monotonic()-start)
    (OUT/f'{phase}.json').write_text(json.dumps(clean(payload),ensure_ascii=False,allow_nan=False),encoding='utf-8')
    print('SUMMARY',json.dumps(summaries),flush=True)


if __name__=='__main__':main(sys.argv[1])
