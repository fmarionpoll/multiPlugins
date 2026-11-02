"""Report full-trace offline scans with source images and honest annotation metrics."""
import base64
import json
from pathlib import Path
from zipfile import ZipFile
from collections import Counter

import numpy as np

from intake_local_position_trial import read_archive,clean
from intake_profile_registration import register

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'multiCAFE/target/intake-profile-scan'


def main():
    development=json.loads((OUT/'development.json').read_text(encoding='utf-8'))
    check=json.loads((OUT/'check.json').read_text(encoding='utf-8'))
    frozen=json.loads((OUT/'frozen-config.json').read_text(encoding='utf-8'))
    payload=dict(development=development,check=check,frozen=frozen,examples=[])
    sources={s['id']:s for phase in [development,check] for s in phase['sources']}
    loaded={}
    def data(exp):
        if exp not in loaded:loaded[exp]=read_archive(Path(sources[exp]['file']))
        return loaded[exp]
    examples=[];used=set()
    def add(exp,cap,first,title,phase='check'):
        key=(exp,cap,int(first))
        if key in used:return
        used.add(key);examples.append((exp,cap,int(first),title,phase))
    # A known already-detected step, plus diagnostic examples selected AFTER evaluation.
    add('143','6L',39,'Clear step already detected by the baseline','development')
    for phase_name,phase in [('check',check),('development',development)]:
        matches=[]
        for r in phase['results']['tail']:
            if r['control']:continue
            rows=data(r['experiment'])[r['cap']]['rows']
            for c in r['chosen']:
                if rows[c['first']]['edit_action']=='added':matches.append((c['step'],r,c))
        if matches:
            _,r,c=min(matches,key=lambda p:abs(p[0]-1.))
            add(r['experiment'],r['cap'],c['first'],'Recovered edited addition',phase_name)
            break
    strict=[]
    for r in check['results']['maximum']:
        if r['control']:continue
        rows=data(r['experiment'])[r['cap']]['rows']
        for c in r['chosen']:
            if rows[c['first']]['edit_action']=='added':strict.append((abs(c['step']-1.),r,c))
    if strict:
        _,r,c=min(strict,key=lambda p:p[0]);add(r['experiment'],r['cap'],c['first'],'Recovered addition under both control limits')
    controls=[(c['normalisedStep'],r,c) for r in check['results']['tail'] if r['control'] for c in r['chosen']]
    if controls:
        _,r,c=max(controls,key=lambda p:p[0]);add(r['experiment'],r['cap'],c['first'],'New control proposal: feeding must not be inferred')
    unknown=[];zero=[];missed=[]
    for r in check['results']['tail']:
        a=data(r['experiment'])[r['cap']];chosen={c['first'] for c in r['chosen']}
        if r['control']:continue
        for c in r['chosen']:
            row=a['rows'][c['first']]
            if row['manual_override']=='true' and float(row['edited_net_intake_px'])<=0:zero.append((r,c))
            elif row['edit_action']!='added':unknown.append((r,c))
        for i,row in enumerate(a['rows']):
            if row['edit_action']=='added' and float(row['automatic_net_intake_px'])<=0 and i not in chosen and 8<=i<len(a['raw'])-8:
                missed.append((float(row['edited_net_intake_px']),r,i))
    if zero:
        r,c=zero[0];add(r['experiment'],r['cap'],c['first'],'Proposal at an explicit zero override')
    if unknown:
        r,c=min(unknown,key=lambda p:abs(p[1]['step']-1.));add(r['experiment'],r['cap'],c['first'],'New unannotated proposal: status unknown')
    if missed:
        _,r,i=min(missed,key=lambda p:abs(p[0]-1.));add(r['experiment'],r['cap'],i,'Edited addition still missed by this scan')
    for exp,cap,first,title,phase_name in examples:
        a=data(exp)[cap];phase=check if phase_name=='check' else development
        r=next(r for r in phase['results']['tail'] if r['experiment']==exp and r['cap']==cap)
        scan=json.loads((OUT/'scan'/f'{exp}-{cap}.json').read_text(encoding='utf-8'))
        c=next((c for c in scan['candidates'] if c['first']==first),None)
        baseline=np.array([float(row['automatic_net_intake_px'])>0 for row in a['rows']])
        def status(profile):
            rr=next(x for x in phase['results'][profile] if x['experiment']==exp and x['cap']==cap)
            if any(x['first']==first for x in rr['chosen']):return 'Supplement selected'
            if c is None:return 'No full image window'
            if c['gates']:return ', '.join(c['gates'])
            if c['normalisedStep']<frozen['limits'][profile]:return 'Below control limit'
            if baseline[max(0,first-1):first+2].any():return 'Existing automatic neighbour retained'
            return 'Neighbouring supplement selected'
        times=np.array([float(row['elapsed_min']) for row in a['rows']])
        detail=register(a['image'],a['raw'],times,first,r['noise'],details=True)
        with ZipFile(sources[exp]['file']) as z:png=z.read(f'kymograph-{cap}.png')
        first_view=max(0,first-8);last_view=min(len(a['raw'])-1,first+8)
        series=[]
        for i in range(first_view,last_view+1):
            row=a['rows'][i]
            val=lambda k:float(row[k]) if row[k] else float('nan')
            series.append([i,float(times[i]),float(a['raw'][i]),val('intake_bottom_px'),val('candidate_amplitude_px'),
                           val('edited_net_intake_px'),row['manual_override']=='true',val('automatic_net_intake_px')])
        payload['examples'].append(dict(id=f'{exp}/{cap}/{first}',title=title,phase=phase_name,experiment=exp,cap=cap,first=first,
            control=r['control'],candidate=c,detail=detail,statuses={p:status(p) for p in ['tail','maximum']},series=series,
            firstView=first_view,lastView=last_view,image='data:image/png;base64,'+base64.b64encode(png).decode(),
            width=a['image'].shape[1],height=a['image'].shape[0],row=a['rows'][first]))
    outcomes=Counter();overlap=Counter()
    for r in check['results']['tail']:
        a=data(r['experiment'])[r['cap']]
        scan=json.loads((OUT/'scan'/f'{r["experiment"]}-{r["cap"]}.json').read_text(encoding='utf-8'))
        candidates={c['first']:c for c in scan['candidates']};chosen={c['first'] for c in r['chosen']}
        baseline=np.array([float(row['automatic_net_intake_px'])>0 for row in a['rows']])
        for i,row in enumerate(a['rows']):
            if row['edit_action']!='added' or baseline[i]:continue
            c=candidates.get(i)
            if i in chosen:outcomes['Recovered']+=1
            elif c is None:outcomes['No full usable image window']+=1
            elif c['gates']:outcomes['Image or movement checks failed']+=1;overlap.update(c['gates'])
            elif c['normalisedStep']<frozen['limits']['tail']:outcomes['Below control limit']+=1
            elif baseline[max(0,i-1):i+2].any():outcomes['Existing automatic neighbour']+=1
            else:outcomes['Neighbouring supplement']+=1
    payload['addedDiagnostics']=dict(outcomes=dict(outcomes),overlappingGates=dict(overlap))
    (OUT/'check-added-diagnostics.json').write_text(json.dumps(payload['addedDiagnostics'],indent=2),encoding='utf-8')
    # Keep final app compact: per-cap metrics, accepted proposals and rejection counts,
    # while the complete all-boundary cache remains reproducible in the workspace.
    for phase in [development,check]:
        for results in phase['results'].values():
            for r in results:r.pop('chosen',None)
    target=Path('C:/Users/fred/.codex/visualizations/2026/10/31/01a19132-23ad-7752-bebf-1afce8ba8fde/intake-profile-scan.html')
    # Keep example markers tied to the selected limit, while retaining both
    # unmodified statuses in the side-by-side diagnostic table.
    template=TEMPLATE.replace("e.statuses.tail==='Supplement selected'", "e.statuses[$('limit').value||'tail']==='Supplement selected'")
    target.write_text(template.replace('__DATA__',json.dumps(clean(payload),ensure_ascii=False,allow_nan=False).replace('<','\\u003c')),encoding='utf-8')
    print(target)


TEMPLATE=r'''<!doctype html><html lang="en"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Full-trace intake profile scan</title>
<style>body{font:16px/1.55 system-ui,sans-serif;color:#222;background:white;margin:0}main{padding:28px;max-width:1160px;margin:auto}h1{font-size:25px}h2{font-size:20px;margin-top:28px}.caption{font-size:13px;color:#555}.note{padding:14px 18px;background:#f2f4f6;border-left:3px solid #52789d}select{font:inherit;padding:5px 9px;margin:5px 14px 5px 7px;max-width:100%}table{border-collapse:collapse;width:100%;font-size:14px}th,td{padding:8px;border-bottom:1px solid #ddd;text-align:right}th:first-child,td:first-child{text-align:left}th{background:#f2f4f6}.table{overflow-x:auto}svg{display:block;width:100%;height:auto}summary{cursor:pointer;font-weight:600;padding:10px 0}.legend span{margin-right:20px}code{font-size:12px;overflow-wrap:anywhere}</style><main>
<h1>Full-trace search for additional abrupt intakes</h1>
<p id="scope"></p><p class="note"><strong>This is an offline detector check.</strong> Existing automatic bins remain present. The experiment edits are evaluation annotations, not confirmed truth for every unchanged bin. New unannotated proposals require image review; new proposals in no-fly controls are failures or artefacts.</p>
<h2>Separate-experiment check</h2><div class="table" id="comparison"></div>
<p class="caption">Recovery counts refer to previously missing added time bins, not biological feeding bouts. The within-one-bin count uses one-to-one matching. “New control bins” excludes control events already in the baseline. No original amplitude was replaced.</p>
<h2>Inspect the results</h2><label>Phase <select id="phase"><option value="check">Separate-experiment check</option><option value="development">Development</option></select></label><label>Control limit <select id="limit"><option value="tail">Control tail</option><option value="maximum">Control maximum</option></select></label>
<div class="table" id="summary"></div><div class="table" id="experiments"></div>
<h2>Why edited additions are still missed</h2><div class="table" id="addedReasons"></div><p class="caption">Control-tail configuration, check experiments. These categories are exclusive. Lowering the control limit only affects the “Below control limit” category; it does not resolve image or movement failures. Failure to concentrate movement at a boundary may reflect a distributed decline, timing mismatch or unstable tracking.</p><details><summary>Individual image/movement checks (overlapping counts)</summary><div class="table" id="gateCounts"></div></details>
<h2>Original-image examples</h2><p>These examples were selected after evaluating the scan, to show successes and limitations. They are not a random validation sample.</p><select id="example"></select><label><input id="pixels" type="checkbox" checked> Show native pixel blocks</label><style id="pixelStyle">image{image-rendering:pixelated}</style>
<div class="legend"><span style="color:#555">Grey: saved topraw</span><span style="color:#327947">Green: positive manual override</span><span style="color:#265faa">Blue: image-based step proposal</span></div><h3 id="exampleTitle"></h3><div id="image"></div><div class="table" id="exampleMetrics"></div>
<p class="caption">Native source pixels cropped and stretched horizontally. Pixel-block display prevents browser interpolation from adding apparent blur; it does not restore information absent from the source. Green bars use exported reviewed full amplitudes and explicit bottom positions. The blue bar uses an image-gradient anchor and the image-derived step after local trend removal; a dashed blue bar is not selected under the chosen limit. These amplitudes use different background conventions and do not establish which volume is correct.</p>
<h2>What was tested</h2><p>Search every boundary with five preceding and five following columns. Match median RGB profiles, then estimate within-flank drift from images. Reject weak or competing matches, disagreement in flank drifts, search-boundary hits, persistent horizontal-edge proximity, and movement not concentrated at the selected boundary. Rank the remaining positive steps using image-fit residual scatter with a small regularization floor. This score is not a calibrated probability.</p>
<p>Calibrate two limits from development no-fly controls: the 99.9th percentile of image-gated scores and a stricter limit just above the largest control score. Rejected control windows contribute zero; both limits have a minimum score of five. Freeze both limits and all source-code hashes before the separate-experiment check. Feeding edits are not used to estimate noise or select candidates.</p>
<p>Preserve existing automatic events and defer additions within one bin of them. Keep at most one new proposal in neighbouring bins. This avoids duplicate counting but deliberately misses some adjacent edited bins. This scan is for abrupt steps; distributed declines over 5–10 intervals need a separate episode model.</p>
<h2>Limits of the comparison</h2><p>The check experiments have been examined in earlier descriptive analyses, so they are not untouched external validation. Settings were not retuned after this check. A known tape-influence region may extend beyond the persistent-edge warning; brightness and profile similarity alone do not resolve it.</p>
<p>The existing baseline may include events removed in the review; those are retained and counted separately. A low number of new control proposals therefore does not mean the complete trial is free of control detections. Whole-series early and late boundaries without full flanks are unsearched.</p>
<p>The archived baseline retains its historical evaporation settings. New proposed locations use local image drift, not optional local evaporation, and are not exported as net-intake amplitudes. Lower-meniscus movement has not been turned into a feeding or evaporation correction.</p>
<details><summary>Per-capillary results for the selected phase and limit</summary><div class="table" id="caps"></div></details>
<details><summary>Configuration and source versions</summary><div id="sources"></div></details><noscript>Enable JavaScript to use the report selectors.</noscript>
</main><script>
const D=__DATA__, $=id=>document.getElementById(id), f=(v,n=2)=>v==null?'unavailable':Number(v).toLocaleString(undefined,{maximumFractionDigits:n});
function table(id,heads,rows){const t=document.createElement('table'),h=document.createElement('tr');for(const v of heads){const c=document.createElement('th');c.textContent=v;h.append(c);}t.append(h);for(const row of rows){const tr=document.createElement('tr');for(const v of row){const td=document.createElement('td');td.textContent=v;tr.append(td);}t.append(tr);}$(id).replaceChildren(t);}
function reportData(){return D[$('phase').value||'check'];}
function render(){const phase=reportData(),name=$('limit').value||'tail',s=phase.summaries[name];table('summary',['Population','Missing edited bins','Recovered exactly','Recovered within 1 bin','New bins','New unannotated','New at explicit zero overrides','Existing automatic bins retained'],['flies','controls'].map(k=>{const r=s[k];return [k,r.addedMissing,r.recoveredExact,r.recoveredWithinOne,r.newBins,r.newUnannotated,r.newAtZeroOverride,r.baselineBins];}));const rs=phase.results[name];const ids=[...new Set(rs.map(r=>r.experiment))];table('experiments',['Experiment','Missing added bins','Exact recoveries','New fly-capillary proposals','New control proposals'],ids.map(id=>{const a=rs.filter(r=>r.experiment===id),sum=(key,control=null)=>a.filter(r=>control===null||r.control===control).reduce((v,r)=>v+r.metrics[key],0);return [id,sum('addedMissing',false),sum('recoveredExact',false),sum('newBins',false),sum('newBins',true)];}));table('caps',['Experiment / capillary','Role','Missing adds','Searchable missing adds','Exact recovered','Within 1 bin','New bins','New at zero overrides','Baseline at zero overrides'],rs.map(r=>[`${r.experiment}/${r.cap}`,r.control?'Control':'Fly',r.metrics.addedMissing,r.metrics.addedSearchable,r.metrics.recoveredExact,r.metrics.recoveredWithinOne,r.metrics.newBins,r.metrics.newAtZeroOverride,r.metrics.baselineAtZeroOverride]));}
function renderExample(){const e=D.examples.find(e=>e.id===$('example').value)||D.examples[0],s=e.series,first=e.firstView,last=e.lastView,W=1000,H=340,L=66,R=18,T=18,B=45,levels=s.map(p=>p[2]),detail=e.detail;for(const p of s)if(p[6]&&p[5]>0&&p[3]!=null&&p[4]!=null)levels.push(p[3],p[3]-p[4]);if(detail)levels.push(detail.featureAtBoundary,detail.featureAtBoundary+detail.step);const lo=Math.max(-.5,Math.floor(Math.min(...levels)-5)),hi=Math.min(e.height-.5,Math.ceil(Math.max(...levels)+5)),x=v=>L+(W-L-R)*(v-first)/(last-first),y=v=>T+(H-T-B)*(v-lo)/(hi-lo);let svg=`<svg viewBox="0 0 ${W} ${H}" role="img" aria-label="Source kymograph with saved topraw and reviewed and trial step markers"><svg x="${L}" y="${T}" width="${W-L-R}" height="${H-T-B}" viewBox="${first} ${lo} ${last-first} ${hi-lo}" preserveAspectRatio="none"><image href="${e.image}" x="-.5" y="-.5" width="${e.width}" height="${e.height}"/></svg>`;for(const q of [0,.25,.5,.75,1])svg+=`<text x="${L-8}" y="${y(lo+q*(hi-lo))+4}" text-anchor="end" font-size="11">${f(lo+q*(hi-lo),1)}</text><text x="${x(first+q*(last-first))}" y="${H-24}" text-anchor="middle" font-size="11">${f(first+q*(last-first),0)}</text>`;svg+=`<path d="${s.map((p,i)=>`${i?'L':'M'}${x(p[0])},${y(p[2])}`).join(' ')}" fill="none" stroke="#555" stroke-width="1.4"/>`;for(const p of s)if(p[6]&&p[5]>0&&p[3]!=null&&p[4]!=null)svg+=`<line x1="${x(p[0])-.9}" x2="${x(p[0])-.9}" y1="${y(p[3]-p[4])}" y2="${y(p[3])}" stroke="#327947" stroke-width="3"/>`;if(detail)svg+=`<line x1="${x(e.first)+.9}" x2="${x(e.first)+.9}" y1="${y(detail.featureAtBoundary)}" y2="${y(detail.featureAtBoundary+detail.step)}" stroke="#265faa" stroke-width="3" ${e.statuses.tail==='Supplement selected'?'':'stroke-dasharray="4 3"'}/>`;svg+=`<text x="${W/2}" y="${H-3}" text-anchor="middle" font-size="12">Native time bin (approximately one minute)</text><text x="16" y="${H/2}" transform="rotate(-90 16 ${H/2})" text-anchor="middle" font-size="12">Image row (px; down)</text></svg>`;$('image').innerHTML=svg;$('exampleTitle').textContent=`${e.id}: ${e.title} (${e.phase})`;table('exampleMetrics',['Item','Value'],[['Control-tail result',e.statuses.tail],['Control-maximum result',e.statuses.maximum],['Image-derived step (px)',f(detail?.step,3)],['Image match score',f(detail?.score,3)],['Normalized step score',f(detail?.normalisedStep,2)],['Boundary movement (px)',f(detail?.observedBoundary,3)],['Archived edit action',e.row.edit_action],['Archived edited net intake (px)',f(Number(e.row.edited_net_intake_px),3)]]);}
$('phase').value='check';$('limit').value='tail';$('phase').addEventListener('change',render);$('limit').addEventListener('change',render);for(const e of D.examples){const o=document.createElement('option');o.value=e.id;o.textContent=`${e.id}: ${e.title}`;$('example').append(o);}$('example').value=D.examples[0].id;$('example').addEventListener('change',renderExample);const c=D.check.summaries.tail.all,d=D.development.summaries.tail.all;$('scope').textContent=`${D.development.sources.length} development experiments (${d.capillaries} capillaries); ${D.check.sources.length} separate-experiment checks (${c.capillaries} capillaries).`;
table('comparison',['Control limit','Learned score limit','Check: missing added bins','Exact recoveries','Within 1 bin','New unannotated fly bins','New control bins'],['tail','maximum'].map(name=>{const s=D.check.summaries[name];return [name==='tail'?'Control tail':'Control maximum',f(D.frozen.limits[name],2),s.flies.addedMissing,s.flies.recoveredExact,s.flies.recoveredWithinOne,s.flies.newUnannotated,s.controls.newBins];}));const pre=document.createElement('pre');pre.textContent=JSON.stringify(D.frozen,null,2);$('sources').append(pre);for(const source of [...D.development.sources,...D.check.sources]){const p=document.createElement('p');p.textContent=`${source.id}: ${source.file}; SHA-256 ${source.sha256}`;$('sources').append(p);}render();renderExample();
table('addedReasons',['Outcome for missing edited bins','Count'],Object.entries(D.addedDiagnostics.outcomes));table('gateCounts',['Check','Affected edited bins'],Object.entries(D.addedDiagnostics.overlappingGates).map(([k,v])=>[k.replaceAll('_',' '),v]));$('limit').addEventListener('change',renderExample);$('pixels').addEventListener('change',()=>{$('pixelStyle').textContent=`image{image-rendering:${$('pixels').checked?'pixelated':'auto'}}`;});
</script></html>'''


if __name__=='__main__':main()
