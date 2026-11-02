"""Read-only comparison of experiment 023's three user-exported snapshots."""
import csv
import hashlib
import io
import json
import re
from collections import Counter
from pathlib import Path
from zipfile import ZipFile

import numpy as np

OUT = Path(__file__).resolve().parents[1] / 'multiCAFE/target/intake-023-comparison'
OUT.mkdir(parents=True, exist_ok=True)
STAGES = ['original', 'corrected-auto', 'reviewed']
data, sources = {}, []
for stage in STAGES:
    path = Path('C:/Users/fred/Documents') / f'023-{stage}.zip'
    with ZipFile(path) as z:
        readme = z.read('README.txt').decode('utf-8-sig')
        controls = set(re.findall(r'(?m)^(\d+[LR]) \(no-fly control\)', readme))
        caps = {}
        for name in z.namelist():
            m = re.fullmatch(r'correlation-(\d+[LR])\.csv', name)
            if not m:
                continue
            text = z.read(name).decode('utf-8-sig')
            rows = list(csv.DictReader((s for s in text.splitlines() if not s.startswith('#')), delimiter=';'))
            cols = {}
            for key in rows[0]:
                try:
                    cols[key] = np.array([float(r[key]) if r[key] else np.nan for r in rows])
                except ValueError:
                    cols[key] = [r[key] for r in rows]
            meta = '\n'.join(s for s in text.splitlines() if s.startswith('#'))
            caps[m[1]] = dict(cols=cols, meta=meta, rows=rows,
                             imageHash=hashlib.sha256(z.read(f'kymograph-{m[1]}.png')).hexdigest())
        data[stage] = caps
        sources.append(dict(stage=stage, file=str(path), sha256=hashlib.sha256(path.read_bytes()).hexdigest(), experiment=readme.splitlines()[0]))
assert set(data[STAGES[0]]) == set(data[STAGES[1]]) == set(data[STAGES[2]])
def stats(x):
    x = np.asarray(x)
    x = x[np.isfinite(x)]
    return dict(n=len(x), min=float(x.min()), median=float(np.median(x)), max=float(x.max()), mean=float(np.mean(x)), sd=float(np.std(x))) if len(x) else None
def positive(x):
    return int(np.sum(np.nan_to_num(x) > 1e-9))
def changes(a, b):
    pa, pb = np.nan_to_num(a)>1e-9, np.nan_to_num(b)>1e-9
    return dict(added=int(np.sum(~pa & pb)), removed=int(np.sum(pa & ~pb)), resized=int(np.sum(pa & pb & (np.abs(a-b)>1e-6))))
result = dict(sources=sources, capillaries=[], summaries={}, checks={})
for cap in sorted(data[STAGES[0]], key=lambda c: (int(c[:-1]), c[-1])):
    a,b,c = [data[s][cap]['cols'] for s in STAGES]
    for key in ['frame', 'elapsed_min']:
        assert np.array_equal(a[key], b[key]) and np.array_equal(b[key], c[key]), (cap,key)
    raw_delta = b['raw_px']-a['raw_px']
    raw_same = np.allclose(b['raw_px'], c['raw_px'], atol=1e-9, rtol=0, equal_nan=True)
    details = []
    for i,(br,cr) in enumerate(zip(data['corrected-auto'][cap]['rows'],data['reviewed'][cap]['rows'])):
        before, after = b['edited_net_intake_px'][i],c['edited_net_intake_px'][i]
        if cr['manual_override']=='true' or abs(before-after)>1e-6:
            details.append(dict(frame=int(cr['frame']),time=c['elapsed_min'][i],action=cr['edit_action'],before=before,after=after,
                                rate=c['excess_px_per_min'][i],threshold=c['chosen_threshold_px_per_min'][i],reliable=cr['reliable']=='true',
                                raw=c['raw_px'][i],bottom=c['intake_bottom_px'][i]))
    settings = []
    stage_stats = []
    for stage,cols in zip(STAGES,[a,b,c]):
        meta = data[stage][cap]['meta']
        settings.append(dict(stage=stage,persistent='persistent=true' in meta,local='local=true' in meta,
                             threshold=cols['chosen_threshold_px_per_min'][0],autoThreshold=cols['automatic_threshold_px_per_min'][0]))
        stage_stats.append(dict(stage=stage, bins=positive(cols['edited_net_intake_px']),net=float(np.nansum(cols['edited_net_intake_px'])),
                                autoBins=positive(cols['automatic_net_intake_px']),autoNet=float(np.nansum(cols['automatic_net_intake_px'])),
                                evaporation=float(np.nansum(cols['evaporation_increment_px'])),overrides=sum(x=='true' for x in cols['manual_override'])))
    result['capillaries'].append(dict(cap=cap,control=cap in controls,rows=len(a['frame']),rawCorrection=stats(raw_delta),
        incrementCorrection=stats(np.diff(raw_delta)),rawChangedBins=int(np.sum(np.abs(raw_delta)>1e-6)),rawStableDuringReview=bool(raw_same),
        imageStable=len({data[s][cap]['imageHash'] for s in STAGES})==1,
        automaticStableDuringReview=bool(np.allclose(b['automatic_net_intake_px'],c['automatic_net_intake_px'],atol=1e-9,rtol=0,equal_nan=True)),
        evaporationStableDuringReview=bool(np.allclose(b['evaporation_increment_px'],c['evaporation_increment_px'],atol=1e-9,rtol=0,equal_nan=True)),
        thresholdsStableDuringReview=bool(np.allclose(b['chosen_threshold_px_per_min'],c['chosen_threshold_px_per_min'],atol=1e-9,rtol=0,equal_nan=True)),
        detectionChanges=changes(a['edited_net_intake_px'],b['edited_net_intake_px']),reviewChanges=changes(b['edited_net_intake_px'],c['edited_net_intake_px']),
        settings=settings,stages=stage_stats,edits=details,
        series=[[float(a['elapsed_min'][i]),float(a['raw_px'][i]),float(b['raw_px'][i]),float(a['edited_net_intake_px'][i]),float(b['edited_net_intake_px'][i]),float(c['edited_net_intake_px'][i])] for i in range(len(a['frame']))]))
for group in ['flies','controls','all']:
    caps=[c for c in result['capillaries'] if group=='all' or c['control']==(group=='controls')]
    result['summaries'][group]=dict(capillaries=len(caps), stages=[dict(stage=s,bins=sum(c['stages'][i]['bins'] for c in caps),net=sum(c['stages'][i]['net'] for c in caps),overrides=sum(c['stages'][i]['overrides'] for c in caps)) for i,s in enumerate(STAGES)],
        detectionChanges={k:sum(c['detectionChanges'][k] for c in caps) for k in ['added','removed','resized']},reviewChanges={k:sum(c['reviewChanges'][k] for c in caps) for k in ['added','removed','resized']},
        actions=dict(Counter(e['action'] for c in caps for e in c['edits'])))
result['checks']=dict(rawStableDuringReview=all(c['rawStableDuringReview'] for c in result['capillaries']), imagesStable=all(c['imageStable'] for c in result['capillaries']),
    automaticStableDuringReview=all(c['automaticStableDuringReview'] for c in result['capillaries']),
    evaporationStableDuringReview=all(c['evaporationStableDuringReview'] for c in result['capillaries']),
    thresholdsStableDuringReview=all(c['thresholdsStableDuringReview'] for c in result['capillaries']),
    settingsChanges=[dict(cap=c['cap'],settings=c['settings']) for c in result['capillaries'] if len({(s['persistent'],s['local']) for s in c['settings']})>1])
def clean(x):
    if isinstance(x,dict): return {k:clean(v) for k,v in x.items()}
    if isinstance(x,list): return [clean(v) for v in x]
    if isinstance(x,float) and not np.isfinite(x): return None
    return x
result=clean(result)
(OUT/'comparison.json').write_text(json.dumps(result,ensure_ascii=False,allow_nan=False),encoding='utf-8')
print(json.dumps(dict(summaries=result['summaries'],checks={k:v for k,v in result['checks'].items() if k!='settingsChanges'}),ensure_ascii=False,indent=2))
