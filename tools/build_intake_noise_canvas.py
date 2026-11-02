"""Build the standalone illumination and measurement-noise assessment."""
import json
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'multiCAFE/target/intake-measurement-noise'
VIS=Path('C:/Users/fred/.codex/visualizations/2026/10/31/01a19132-23ad-7752-bebf-1afce8ba8fde')
d=json.loads((OUT/'noise.json').read_text(encoding='utf-8'))
template=r'''import {H1,H2,Text,Stack,Row,Grid,Select,Table,Callout,useState,useHostTheme} from "cursor/canvas";
type Description={n:number;median?:number;q25?:number;q75?:number};
type Cap={experiment:string;cap:string;position:number;control:boolean;duration:number;interval:number;rows:number;points:number;positionSigma:number|null;differenceSigma:number|null;incrementSigma:number|null;incrementAbsP95:number|null;incrementAbsMax:number|null;largeIncrementFraction:number|null;residualLag1:number|null;brightness:Description;contrast:Description;uncertainFraction:number;blocks:{first:number;last:number;points:number;slope:number}[];sensitivity:{guard:number;block:number;points:number;positionSigma:number|null}[];reliableOnly:{positionSigma:number|null;points:number};series:[number,number,boolean,boolean,boolean][];};
type Experiment={id:string;caps:number;usable:number;darkBrightNoiseRatio:number|null;brightnessNoiseRankCorrelation:number|null};
type Data={capillaries:Cap[];experiments:Experiment[];sources:{id:string;file:string;sha256:string;experiment:string}[];excludedSources:{id:string;reason:string;expected:string;actual:string}[];summary:{experiments:number;caps:number;usable:number;positionSigma:Description;differenceSigma:Description;moreDataPositionSigma:Description;block15PositionSigma:Description;darkBrightRatio:Description;darkerNoisierExperiments:number};};
const D=JSON.parse(__DATA__) as Data;
const fmt=(v:number|null|undefined,n=2)=>v==null?"unavailable":v.toLocaleString(undefined,{maximumFractionDigits:n});
function Profile({caps,metric}:{caps:Cap[];metric:"brightness"|"positionSigma"}){
 const t=useHostTheme(),W=790,H=230,L=58,R=18,T=18,B=46;
 const val=(c:Cap)=>metric==="brightness"?c.brightness.median:c.positionSigma;
 const points=caps.filter(c=>val(c)!=null);const max=metric==="brightness"?255:Math.max(.5,...points.map(c=>val(c)!))*1.15;
 const x=(p:number)=>L+(W-L-R)*p/19,y=(v:number)=>H-B-(H-T-B)*v/max;
 return <Stack gap={6}><Text weight="semibold">{metric==="brightness"?"Image brightness near the meniscus":"Typical position scatter after removing drift"}</Text>
 <svg viewBox={`0 0 ${W} ${H}`} role="img" aria-label={metric==="brightness"?"Median brightness by capillary position":"Position uncertainty by capillary position"} style={{width:"100%",background:t.bg.editor}}>
 {[0,.25,.5,.75,1].map(f=><g key={f}><line x1={L} x2={W-R} y1={y(f*max)} y2={y(f*max)} stroke={t.stroke.tertiary}/><text x={L-8} y={y(f*max)+4} textAnchor="end" fontSize={10} fill={t.text.secondary}>{fmt(f*max,metric==="brightness"?0:2)}</text></g>)}
 {caps.map(c=><text key={c.cap} x={x(c.position)} y={H-26} textAnchor="middle" fontSize={9} fill={t.text.secondary}>{c.cap}</text>)}
 {points.map(c=><g key={c.cap}><circle cx={x(c.position)} cy={y(val(c)!)} r={4} fill={c.control?t.bg.editor:t.accent.primary} stroke={t.accent.primary} strokeWidth={1.7}><title>{c.cap}: {fmt(val(c),3)}; {c.points} quiet points; {c.control?"no-fly control":"fly capillary"}</title></circle>{metric==="positionSigma"&&c.differenceSigma!=null&&<path d={`M${x(c.position)-4},${y(c.differenceSigma)} h8`} stroke={t.text.secondary} strokeWidth={2}/>}</g>)}
 <text x={W/2} y={H-4} textAnchor="middle" fontSize={11} fill={t.text.secondary}>Capillary in exported order (0L to 9R)</text><text x={14} y={H/2} transform={`rotate(-90 14 ${H/2})`} textAnchor="middle" fontSize={11} fill={t.text.secondary}>{metric==="brightness"?"Digital RGB mean (0–255)":"Robust scatter (kymograph px)"}</text>
 </svg><Text size="small" tone="secondary">Circles: {metric==="brightness"?"median brightness within ±10 rows of topraw":"direct detrended position estimate; grey ticks: difference-based estimate"}. Hollow circles are no-fly controls. Source: exported experiment {caps[0].experiment}, 0–{fmt(Math.max(...caps.map(c=>c.duration)))} min; selected quiet columns.</Text></Stack>;
}
function Trace({cap}:{cap:Cap}){
 const t=useHostTheme(),W=900,H=240,L=58,R=16,T=16,B=42;
 const s=cap.series,lo=Math.min(...s.map(p=>p[1]))-1,hi=Math.max(...s.map(p=>p[1]))+1,last=s[s.length-1][0];
 const x=(v:number)=>L+(W-L-R)*v/last,y=(v:number)=>T+(H-T-B)*(v-lo)/(hi-lo);
 return <Stack gap={6}><svg viewBox={`0 0 ${W} ${H}`} style={{width:"100%",background:t.bg.editor}} role="img" aria-label="Saved topraw with the samples selected for uncertainty estimation">
 {[0,.5,1].map(f=><g key={f}><line x1={L} x2={W-R} y1={y(lo+f*(hi-lo))} y2={y(lo+f*(hi-lo))} stroke={t.stroke.tertiary}/><text x={L-7} y={y(lo+f*(hi-lo))+4} textAnchor="end" fontSize={10} fill={t.text.secondary}>{fmt(lo+f*(hi-lo),1)}</text></g>)}
 <path d={s.map((p,i)=>`${i?"L":"M"}${x(p[0])},${y(p[1])}`).join(" ")} fill="none" stroke={t.text.tertiary} strokeWidth={1.2}/>
 {s.filter(p=>p[2]).map((p,i)=><circle key={i} cx={x(p[0])} cy={y(p[1])} r={2.2} fill={t.accent.primary}/>)}
 {[0,.25,.5,.75,1].map(f=><text key={f} x={x(last*f)} y={H-23} textAnchor="middle" fontSize={10} fill={t.text.secondary}>{fmt(last*f,1)}</text>)}
 <text x={W/2} y={H-3} textAnchor="middle" fontSize={11} fill={t.text.secondary}>Elapsed time (min)</text><text x={14} y={H/2} transform={`rotate(-90 14 ${H/2})`} textAnchor="middle" fontSize={11} fill={t.text.secondary}>Saved topraw row (px; down)</text>
 </svg><Text size="small" tone="secondary">Grey: saved trace. Accent dots: points used for estimation. Source: {cap.experiment}/{cap.cap}, 0–{fmt(last)} min. Separate linear trends are fitted within contiguous blocks; the displayed trace is unchanged.</Text></Stack>;
}
export default function IntakeNoise(){
 const t=useHostTheme(),[exp,setExp]=useState("023"),[id,setId]=useState("0L");
 const caps=D.capillaries.filter(c=>c.experiment===exp).sort((a,b)=>a.position-b.position),cap=caps.find(c=>c.cap===id)||caps[0];
 return <Stack gap={22} style={{padding:24,maxWidth:1200,margin:"0 auto",color:t.text.primary}}>
 <Stack gap={8}><H1>Step 2: illumination and measurement uncertainty</H1><Text>{D.summary.experiments} experiments · {D.summary.caps} capillary traces · {D.summary.usable} estimates · native intervals approximately one minute</Text>
 <Text weight="semibold">Typical position scatter is approximately {fmt(D.summary.positionSigma.median)} pixel. The middle half of estimates spans {fmt(D.summary.positionSigma.q25)}–{fmt(D.summary.positionSigma.q75)} pixel.</Text>
 <Callout tone="info" title="This measures the complete measurement chain">These are descriptive estimates from exported topraw and kymographs, combining image noise, tracking, processing, illumination changes and residual physical movement. They are not isolated camera read noise or an EMVA calibration. A quiet trace can also be consistently misplaced.</Callout></Stack>
 <Stack gap={8}><H2>What the illumination comparison shows</H2>
 <Text>In experiment 023, 0L and 9R have median brightness values of 102 and 115 digital levels, compared with 148 for 1L. Their position scatter is 0.42 and 0.34 px, compared with 0.21 px for 1L. These measurements support checking each capillary separately.</Text>
 <Text>Across all 16 experiments, the darkest quarter has more scatter than the brightest quarter in 10 experiments. The median within-experiment ratio is 1.07. This is a modest, inconsistent association, not evidence that brightness alone predicts uncertainty. Contrast, tracking conditions and the different roles of controls and fly capillaries remain mixed.</Text>
 <Text>For 023/1L, the robust scatter of consecutive detrended increments is 0.25 px, substantially larger than the approximately 0.02 px local-evaporation adjustment discussed for an early one-minute interval. Only 34 quiet points are available for this capillary, so this is a preliminary comparison.</Text></Stack>
 <Row gap={12}><Text weight="semibold">Experiment</Text><Select value={exp} onChange={setExp} options={D.experiments.map(e=>({value:e.id,label:`${e.id} (${e.usable}/${e.caps} estimates)`}))}/></Row>
 <Profile caps={caps} metric="brightness"/><Profile caps={caps} metric="positionSigma"/>
 <Row gap={12}><Text weight="semibold">Inspect trace</Text><Select value={cap.cap} onChange={setId} options={caps.map(c=>({value:c.cap,label:c.cap+(c.control?" (control)":"")}))}/></Row>
 <Trace cap={cap}/>
 <Table headers={["Capillary","Quiet points / blocks","Position scatter (px)","Difference estimate (px)","95th percentile increment fluctuation (px)","Residual correlation at lag 1"]} rows={[[cap.cap,`${cap.points} / ${cap.blocks.length}`,fmt(cap.positionSigma,3),fmt(cap.differenceSigma,3),fmt(cap.incrementAbsP95,3),fmt(cap.residualLag1,2)]]}/>
 <Text size="small">The difference estimate divides robust increment scatter by √2 and assumes independent position errors. Correlation and fitting can invalidate that interpretation; the direct residual estimate is shown alongside it. The 95th percentile describes occasional larger fluctuations after removing each block’s fitted slope.</Text>
 {cap.positionSigma!=null&&<Stack gap={8}><H2>Sensitivity for {cap.cap}</H2><Table headers={["Selection / detrending","Points","Position scatter (px)"]} rows={[["Main: 2-bin guard, up to 21-point blocks",cap.points,fmt(cap.positionSigma,3)],...cap.sensitivity.map(s=>[`${s.guard}-bin guard, up to ${s.block}-point blocks`,s.points,fmt(s.positionSigma,3)]),["Reliable-flag points only",cap.reliableOnly.points,fmt(cap.reliableOnly.positionSigma,3)]]}/></Stack>}
 <details><summary>All capillaries in experiment {exp}</summary><Table headers={["Capillary","Role","Brightness (0–255)","RGB contrast (digital levels)","Position scatter (px)","Quiet points","Data coverage"]} rows={caps.map(c=>[c.cap,c.control?"Control":"Fly",fmt(c.brightness.median,1),fmt(c.contrast.median,1),fmt(c.positionSigma,3),c.points,c.positionSigma==null?"Insufficient quiet stretches":c.points>=40&&c.blocks.length>=3?"≥40 points and ≥3 blocks":"Limited; preliminary estimate"])}/></details>
 <Stack gap={8}><H2>Method and limits</H2><Text>Use finite positions at least 10 image rows from the borders. In fly capillaries, exclude automatic and edited positive bins, both endpoints, and two additional neighbouring columns on each side. Retain contiguous runs of at least eight points and split them into blocks of at most 21 points. Fit robust linear drift separately in each block, then calculate MAD × 1.4826, with an approximate correction for the two fitted trend parameters. Individual residual fluctuations are not clipped.</Text>
 <Text>No-fly controls keep all eligible points, including apparent intakes. This prevents known control failures from disappearing through event exclusion. Their robust scatter describes typical fluctuations; the percentile and maximum increment values retain information about larger errors. Fly quiet stretches remain candidates, not independently confirmed absence of feeding.</Text>
 <Text>The median estimate is {fmt(D.summary.moreDataPositionSigma.median)} px among {D.summary.moreDataPositionSigma.n} traces with at least 40 points and three blocks. Using at most 15-point blocks instead gives a median of {fmt(D.summary.block15PositionSigma.median)} px. Three traces have insufficient quiet data.</Text>
 <Text>The current kymograph builder spatially averages camera pixels and can subsample source frames. The checked 023 source images are JPEGs about one minute apart; the inspected first image has no EXIF camera settings. Neither the exports nor those JPEGs can isolate photon noise, read noise, compression, light-panel fluctuation and tracking without additional assumptions.</Text>
 <Text>Capillary order here is the export naming order 0L, 0R, …, 9L, 9R. The outermost exported names are 0L and 9R. Their correspondence to physical camera positions is not independently established by these ZIPs. Outer position and no-fly control status are confounded.</Text></Stack>
 <Stack gap={8}><H2>How this informs detection</H2><Text>Estimate uncertainty per capillary and inspect contrast and interference locally. Do not set a single sensor-noise value from the darkest controls or translate brightness directly into an intake threshold. This stage characterizes existing measurements; no thresholds or detector code have been changed.</Text>
 <Text>Record 25/370 onward: Local evaporation will be off consistently. Earlier records were not consistently monitored; exported metadata remains the authority for their settings. This noise analysis detrends the raw trace directly and does not subtract the optional local evaporation model.</Text>
 <Text>To isolate camera noise later, use repeated images of a stationary scene at fixed exposure and gain, with dark frames and several uniform illumination levels. Preserve original pixel values and reproduce the spatial averaging used for detection. Camera characterization methodology: <a href="https://www.emva.org/standards-technology/emva-1288/">EMVA 1288</a>.</Text></Stack>
 <details><summary>Archive provenance and excluded version</summary><Stack gap={8}>{D.sources.map(s=><Text size="small" key={s.id}>{s.id}: {s.file} · SHA-256 {s.sha256}</Text>)}{D.excludedSources.map(s=><Text size="small" key={s.id}>{s.id}: {s.reason} Expected {s.expected}; found {s.actual}.</Text>)}<Text size="small">Experiment 023 uses the reviewed export once. The original and corrected-auto snapshots are not counted as independent experiments. This is descriptive analysis across development and previously examined check examples, not an independent validation of a changed detector.</Text></Stack></details>
 </Stack>;
}
'''
target=VIS/'intake-measurement-noise.canvas.tsx'
target.write_text(template.replace('__DATA__',json.dumps(json.dumps(d,ensure_ascii=False))),encoding='utf-8')
config=json.loads((ROOT/'multiCAFE/target/intake-trial-tsconfig.json').read_text())
config['files']=[str(target).replace('\\','/')]
(OUT/'tsconfig.json').write_text(json.dumps(config),encoding='utf-8')
print(target)
