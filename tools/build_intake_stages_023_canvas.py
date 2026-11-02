"""Create a self-contained report of the 023 comparison; no experimental writes."""
import base64
import io
import json
from pathlib import Path
from zipfile import ZipFile
from PIL import Image

ROOT=Path(__file__).resolve().parents[1]
REPORT=ROOT/'multiCAFE/target/intake-023-comparison'
VIS=Path('C:/Users/fred/.codex/visualizations/2026/10/31/01a19132-23ad-7752-bebf-1afce8ba8fde')
d=json.loads((REPORT/'comparison.json').read_text(encoding='utf-8'))
with ZipFile('C:/Users/fred/Documents/023-original.zip') as z:
    for c in d['capillaries']:
        raw=z.read(f'kymograph-{c["cap"]}.png')
        image=Image.open(io.BytesIO(raw))
        assert image.width==c['rows']
        c['imageHeight']=image.height
        c['image']='data:image/png;base64,'+base64.b64encode(raw).decode()
template=r'''import {H1,H2,Text,Stack,Row,Select,Table,Callout,Checkbox,useState,useHostTheme} from "cursor/canvas";
const D=__DATA__;
const fmt=(x:number|null,d=2)=>x==null?"unavailable":x.toLocaleString(undefined,{maximumFractionDigits:d});
const names=["Original","Recalculated + automatic","Reviewed"];
function Plots({cap}:{cap:typeof D.capillaries[number]}) {
 const t=useHostTheme();const [overlay,setOverlay]=useState(true);
 const s=cap.series,W=940,H=270,L=62,R=18,T=18,B=42;
 const last=s[s.length-1][0],x=(v:number)=>L+(W-L-R)*v/last;
 const vals=s.flatMap(p=>[p[1],p[2]]).filter((v):v is number=>v!==null);
 const low=Math.floor(Math.min(...vals)-7),high=Math.ceil(Math.max(...vals)+7);
 const y=(v:number)=>T+(H-T-B)*(v-low)/(high-low);
 const axis=(yfn:(v:number)=>number,min:number,max:number,label:string)=><g>
  {[0,.25,.5,.75,1].map(f=><g key={f}><line x1={L} x2={W-R} y1={yfn(min+f*(max-min))} y2={yfn(min+f*(max-min))} stroke={t.stroke.tertiary}/><text x={L-8} y={yfn(min+f*(max-min))+4} textAnchor="end" fill={t.text.secondary} fontSize={11}>{fmt(min+f*(max-min),1)}</text></g>)}
  {[0,.25,.5,.75,1].map(f=><text key={f} x={x(last*f)} y={H-23} textAnchor="middle" fill={t.text.secondary} fontSize={11}>{fmt(last*f,1)}</text>)}
  <text x={W/2} y={H-3} textAnchor="middle" fill={t.text.secondary} fontSize={12}>Elapsed time (min)</text><text x={15} y={H/2} transform={`rotate(-90 15 ${H/2})`} textAnchor="middle" fill={t.text.secondary} fontSize={12}>{label}</text>
 </g>;
 const path=(col:number,yfn:(v:number)=>number)=>{let p="";let gap=true;for(const row of s){const v=row[col];if(v===null){gap=true;continue;}p+=`${gap?"M":"L"}${x(row[0])},${yfn(v)} `;gap=false;}return p;};
 const delta=s.map(p=>p[1]==null||p[2]==null?null:p[2]-p[1]);
 const dlo=Math.min(0,...delta.filter((v):v is number=>v!==null))-.2,dhi=Math.max(0,...delta.filter((v):v is number=>v!==null))+.2;
 const yd=(v:number)=>H-B-(H-T-B)*(v-dlo)/(dhi-dlo);
 const max=Math.max(1,...s.flatMap(p=>p.slice(3).map(v=>v||0)))*1.1,yn=(v:number)=>H-B-(H-T-B)*v/max;
 return <Stack gap={12}>
 <H2>Where recalculating topraw moved the trace</H2>
 <Checkbox checked={overlay} onChange={setOverlay} label="Show traces over the original kymograph"/>
 <svg viewBox={`0 0 ${W} ${H}`} style={{width:"100%",background:t.bg.editor}} role="img" aria-label="Original and recalculated topraw over source kymograph">
  {overlay&&<svg x={L} y={T} width={W-L-R} height={H-T-B} viewBox={`0 ${low} ${s.length} ${high-low}`} preserveAspectRatio="none"><image href={cap.image} x={0} y={0} width={s.length} height={cap.imageHeight}/></svg>}
  {axis(y,low,high,"Image row (px; down)")}
  <path d={path(1,y)} fill="none" stroke={t.text.primary} strokeWidth={1.6} strokeDasharray="5 3"/>
  <path d={path(2,y)} fill="none" stroke={t.accent.primary} strokeWidth={1.8}/>
 </svg>
 <Text size="small">Dashed: original topraw. Solid accent: recalculated topraw. The image is stretched horizontally to show the native columns; image rows increase downward. These are exported raw positions, not the grey reconstruction.</Text>
 <svg viewBox={`0 0 ${W} ${H}`} style={{width:"100%",background:t.bg.editor}} role="img" aria-label="Change in topraw position after recalculation">
  {axis(yd,dlo,dhi,"New − original (px)")}
  <path d={delta.map((v,i)=>v==null?"":`${i&&delta[i-1]!=null?"L":"M"}${x(s[i][0])},${yd(v)} `).join("")} fill="none" stroke={t.accent.primary} strokeWidth={1.5}/>
 </svg>
 <Text size="small">Negative values mean an upward correction. Median {fmt(cap.rawCorrection.median)} px; range {fmt(cap.rawCorrection.min)} to {fmt(cap.rawCorrection.max)} px. Standard deviation of the change in consecutive increments: {fmt(cap.incrementCorrection.sd,3)} px.</Text>
 <H2>Intake after recalculation and after review</H2>
 <svg viewBox={`0 0 ${W} ${H}`} style={{width:"100%",background:t.bg.editor}} role="img" aria-label="Automatic and reviewed net intake per bin">
  {axis(yn,0,max,"Net intake per bin (px)")}
  {s.map((p,i)=><g key={i}>{(p[4]||0)>0&&<line x1={x(p[0])-1.6} x2={x(p[0])-1.6} y1={yn(0)} y2={yn(p[4]!)} stroke={t.text.secondary} strokeWidth={2}/>} {(p[5]||0)>0&&<line x1={x(p[0])+1.6} x2={x(p[0])+1.6} y1={yn(0)} y2={yn(p[5]!)} stroke={t.accent.primary} strokeWidth={2}/>}</g>)}
 </svg>
 <Text size="small">Grey: recalculated automatic intake. Accent: reviewed intake. Source for all plots: 023 exports, capillary {cap.cap}, 0–{fmt(last,2)} min. Amounts are net displacement after evaporation subtraction, in pixels.</Text>
 </Stack>;
}
export default function Report(){
 const t=useHostTheme(),[id,setId]=useState("1L");const cap=D.capillaries.find(c=>c.cap===id)!;
 const added=cap.edits.filter(e=>e.action==="added");
 return <Stack gap={22} style={{padding:24,maxWidth:1140,margin:"0 auto",color:t.text.primary}}>
 <Stack gap={8}><H1>Experiment 023: levels, automatic intake, and review</H1>
 <Text>Nicotine 0.1 mM · cam11 · 20 capillaries, including four no-fly controls · 182.05 minutes</Text>
 <Callout tone="warning" title="The first comparison changes two inputs">Local evaporation was enabled in the original export and disabled in both later exports. Thresholds were also recalculated. Differences between original and automatic results cannot be attributed entirely to topraw correction.</Callout>
 <Text>The corrected-auto and reviewed snapshots have identical topraw, evaporation, thresholds and automatic results. Their differences isolate manual intake editing. All source kymograph PNGs match byte for byte across the three archives.</Text></Stack>
 <H2>What changed across all capillaries</H2>
 <Table headers={["Population","Original positive bins","Recalculated automatic bins","Reviewed positive bins","Added during review","Removed during review","Resized during review"]} rows={["flies","controls"].map(k=>{const g=D.summaries[k as "flies"|"controls"];return [k==="flies"?"16 fly capillaries":"4 no-fly controls",...g.stages.map(s=>s.bins),g.reviewChanges.added||0,g.reviewChanges.removed||0,g.reviewChanges.resized||0];})}/>
 <Text size="small">Counts refer to native time bins, not distinct feeding bouts. The 18 other fly-capillary overrides leave net amplitude unchanged. Unchanged bins are not assumed individually confirmed by review.</Text>
 <Row gap={12}><Text weight="semibold">Inspect capillary</Text><Select value={id} onChange={setId} options={D.capillaries.map(c=>({value:c.cap,label:c.cap+(c.control?" (no-fly control)":"")}))}/></Row>
 <Table headers={["Stage","Positive intake bins","Sum of net intake (px)","Evaporation (px)","Threshold (px/min)","Local evaporation"]} rows={cap.stages.map((s,i)=>[names[i],s.bins,fmt(s.net),fmt(s.evaporation),fmt(cap.settings[i].threshold,3),cap.settings[i].local?"Enabled":"Disabled"])}/>
 <Plots cap={cap}/>
 {cap.edits.length>0&&<Stack gap={10}><H2>Manual changes for {cap.cap}</H2>
 <Text>{added.length} added bins. Review can change event position as well as amplitude; a zero-rate or negative-rate addition therefore warrants image inspection.</Text>
 <Table headers={["Bin","Time (min)","Action","Before (net px)","After (net px)","Raw excess (px/min)","Threshold (px/min)","Level quality"]} rows={cap.edits.map(e=>[e.frame,fmt(e.time),e.action,fmt(e.before),fmt(e.after),fmt(e.rate,3),fmt(e.threshold,3),e.reliable?"Reliable":"Uncertain"])}/></Stack>}
 <Stack gap={8}><H2>Implication for the next detection trial</H2>
 <Text>For 1L, the median upward correction is 2.78 px. The correction varies by about 0.28 px around its mean, so much of it is a position offset, with smaller changes to individual increments. The recalculated automatic net intake is 50.07 px versus 49.65 px originally. Reviewed intake is 55.56 px, with six added bins.</Text>
 <Text>Use the original and recalculated topraw pair to assess a local image search independently of intake classification. Use the corrected-auto and reviewed pair to assess remaining missed bins. Recalculated topraw is a comparison trace, not proof of the true edge at every column.</Text>
 <Text size="small">No detector code or experiment files were changed. One experiment does not establish general detection accuracy. Pixel displacements are not converted to consumption volumes or pooled across capillaries as a biological outcome.</Text></Stack>
 <details><summary>Sources and reproducibility</summary><Stack gap={8}>{D.sources.map(s=><Text size="small" key={s.stage}>{s.file} · SHA-256 {s.sha256}</Text>)}<Text size="small">Matched records by capillary and native frame; verified identical elapsed times. All 20 images, level series, evaporation series, thresholds and automatic outputs were compared. Numerical comparisons use a 10⁻⁶ px tolerance for reported amplitude changes and 10⁻⁹ for unchanged review inputs.</Text></Stack></details>
 </Stack>;
}
'''
target=VIS/'intake-023-comparison.canvas.tsx'
target.write_text(template.replace('__DATA__',json.dumps(d,ensure_ascii=False,allow_nan=False)),encoding='utf-8')
config=json.loads((ROOT/'multiCAFE/target/intake-trial-tsconfig.json').read_text())
config['files']=[str(target).replace('\\','/')]
(REPORT/'tsconfig.json').write_text(json.dumps(config),encoding='utf-8')
print(target)
