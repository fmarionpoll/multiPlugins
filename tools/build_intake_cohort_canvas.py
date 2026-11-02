"""Build a single self-contained canvas from the read-only cohort analysis."""
import json
from pathlib import Path
import sys


TEMPLATE = r'''import { H1, H2, Text, Stack, Row, Grid, Stat, Table, Select, Button, BarChart, Callout, useState, useHostTheme } from "cursor/canvas";

type Experiment = {id:string;archive:string;path:string;chemical:string;concentration:string;capillaries:number;controls:number;counts:Record<string,number>;autoControlPositive:number;editedControlPositive:number;medianCapillaryChange:number|null;};
type Capillary = {experiment:string;cap:string;control:boolean;autoPositive:number;editedPositive:number;autoTotal:number;editedTotal:number;relativeChange:number|null;threshold:number;series:[number,number|null,number|null,number|null,number|null,boolean][];};
type Change = {experiment:string;cap:string;frame:number;time:number;edited:number;before:number;rate:number|null;threshold:number;action:string;reason:string;control:boolean;category:string;nearbyAutomatic:number[];singleGate?:string;replayMatched?:boolean;};
type Sweep = {factor:number;addedExact:number;addedWithinOne:number;annotatedAdded:number;newUneditedBins:number;newControlBins:number;baselineBinsLost:number;knownRemovedStillPresent:number;};
type Example = {experiment:string;cap:string;first:number;last:number;title:string;caption:string;image:string;fullImage?:string;};
type Run = {experiment:string;cap:string;first:number;last:number;bins:number;addedBins:number;signedNetRate:number;quietBeforeRate:number|null;quietAfterRate:number|null;};
type Cohort = {summary:{experiments:number;capillaries:number;flyCapillaries:number;controls:number;counts:Record<string,number>;addedCategories:Record<string,number>;capillaryRelativeChange:{median:number;n:number};relativeChangeExcludedZeroBaseline:number;autoControlPositiveBins:number;editedControlPositiveBins:number;chemicals:Record<string,number>;};experiments:Experiment[];capillaries:Capillary[];changes:Change[];examples:Example[];runs:Run[];probes:{matchedCaps:number;matchedAddedBins:number;excluded:{experiment:string;cap:string;issues:string[]}[];singleBinGates:Record<string,number>;sweeps:Sweep[]};};
const D = JSON.parse(__DATA__) as Cohort;
const fmt=(n:number|null|undefined,d=2)=>n==null?"unavailable":n.toLocaleString(undefined,{maximumFractionDigits:d});
const pct=(n:number|null)=>n==null?"zero baseline":`${(100*n).toFixed(1)}%`;
const count=(e:Experiment,k:string)=>e.counts[k]||0;

function Trajectory({cap}:{cap:Capillary}) {
 const theme=useHostTheme();
 const W=820,H=225,L=62,R=18,T=18,B=40;
 const data=cap.series;
 const start=data[0][0],duration=Math.max(1,data[data.length-1][0]-start);
 const finite=data.map(p=>p[1]).filter((v):v is number=>v!==null);
 const low=Math.min(...finite)-1,high=Math.max(...finite)+1;
 const x=(t:number)=>L+(W-L-R)*(t-start)/duration;
 const y=(v:number)=>T+(H-T-B)*(v-low)/(high-low);
 const amountMax=Math.max(1,...data.flatMap(p=>[p[2]||0,p[3]||0]))*1.1;
 const yn=(v:number)=>H-B-(H-T-B)*v/amountMax;
 const paths:string[]=[];let path="";
 for (const p of data) {if(p[1]===null){if(path)paths.push(path);path="";}else{path+=`${path?"L":"M"}${x(p[0])},${y(p[1])} `;}} if(path)paths.push(path);
 const base={width:"100%",height:"auto",background:theme.bg.editor};
 return <Stack gap={8}>
  <Text weight="semibold">Accepted meniscus position — native image rows (downward on screen)</Text>
  <svg viewBox={`0 0 ${W} ${H}`} role="img" aria-label="Meniscus position in image rows against elapsed minutes" style={base}>
   {[0,.25,.5,.75,1].map(f=><g key={f}><line x1={L} x2={W-R} y1={y(low+f*(high-low))} y2={y(low+f*(high-low))} stroke={theme.stroke.tertiary}/><text x={L-8} y={y(low+f*(high-low))+4} textAnchor="end" fill={theme.text.secondary} fontSize={10}>{fmt(low+f*(high-low),1)}</text></g>)}
   {paths.map((p,i)=><path key={i} d={p} fill="none" stroke={theme.text.secondary} strokeWidth={1.5}/>)}
   {data.filter((p,i)=>i>0&&!p[5]&&p[1]!==null).map((p,i)=><circle key={i} cx={x(p[0])} cy={y(p[1]!)} r={3} fill={theme.accent.primary}/>)}
   {[0,.25,.5,.75,1].map(f=><text key={f} x={x(start+duration*f)} y={H-23} textAnchor="middle" fill={theme.text.secondary} fontSize={10}>{fmt(start+duration*f,1)}</text>)}
   <text x={W/2} y={H-4} textAnchor="middle" fill={theme.text.secondary} fontSize={11}>Elapsed time (min)</text>
   <text x={14} y={H/2} transform={`rotate(-90 14 ${H/2})`} textAnchor="middle" fill={theme.text.secondary} fontSize={11}>Image row (px)</text>
  </svg>
  <Text size="small" tone="secondary">Line: accepted topraw. Blue dots: increments marked unreliable, not automatically confirmed intakes.</Text>
  <Text weight="semibold">Net displacement per native time bin — automatic and edited</Text>
  <svg viewBox={`0 0 ${W} ${H}`} role="img" aria-label="Automatic versus edited net intake in pixels against elapsed minutes" style={base}>
   {[0,.25,.5,.75,1].map(f=><g key={f}><line x1={L} x2={W-R} y1={yn(f*amountMax)} y2={yn(f*amountMax)} stroke={theme.stroke.tertiary}/><text x={L-8} y={yn(f*amountMax)+4} textAnchor="end" fill={theme.text.secondary} fontSize={10}>{fmt(f*amountMax,1)}</text></g>)}
   {data.map((p,i)=><g key={i}>{(p[2]||0)>0&&<line x1={x(p[0])-1.5} x2={x(p[0])-1.5} y1={yn(0)} y2={yn(p[2]!)} stroke={theme.text.tertiary} strokeWidth={2}/>} {(p[3]||0)>0&&<line x1={x(p[0])+1.5} x2={x(p[0])+1.5} y1={yn(0)} y2={yn(p[3]!)} stroke={theme.accent.primary} strokeWidth={2}/>}</g>)}
   {[0,.25,.5,.75,1].map(f=><text key={f} x={x(start+duration*f)} y={H-23} textAnchor="middle" fill={theme.text.secondary} fontSize={10}>{fmt(start+duration*f,1)}</text>)}
   <text x={W/2} y={H-4} textAnchor="middle" fill={theme.text.secondary} fontSize={11}>Elapsed time (min)</text>
   <text x={14} y={H/2} transform={`rotate(-90 14 ${H/2})`} textAnchor="middle" fill={theme.text.secondary} fontSize={11}>Net intake (px)</text>
  </svg>
  <Text size="small" tone="secondary">Grey: automatic; blue: edited. Source: archive {cap.experiment}, {cap.cap}; {fmt(start,1)}–{fmt(start+duration,1)} min. Pixel values are not pooled consumption volumes.</Text>
 </Stack>;
}

export default function IntakeCohortReview() {
 const theme=useHostTheme();
 const [exp,setExp]=useState("006"),[capId,setCap]=useState("3L"),[action,setAction]=useState("all"),[scope,setScope]=useState("fly"),[limit,setLimit]=useState(30),[exampleId,setExample]=useState("0");
 const experiments=D.experiments;
 const selectedExperiment=experiments.find(e=>e.id===exp)!;
 const caps=D.capillaries.filter(c=>c.experiment===exp&&(scope==="all"||(scope==="control"?c.control:!c.control)));
 const selectedCap=caps.find(c=>c.cap===capId)||caps[0];
 const changes=D.changes.filter(c=>c.experiment===exp&&c.cap===selectedCap.cap&&(action==="all"||c.action===action));
 const example=D.examples[Number(exampleId)];
 const note={fontSize:12,color:theme.text.secondary};
 return <Stack gap={18} style={{padding:24,maxWidth:1160,margin:"0 auto",background:theme.bg.editor,color:theme.text.primary}}>
  <H1>Intake detection: review of 16 edited experiments</H1>
  <Text weight="semibold">Enough data for focused detector development. Further broad sampling can wait.</Text>
  <Text>Keep smooth-drift steps, short faster-decline episodes and dark-border interference separate. The files support mechanism analysis and regression tests; they do not by themselves establish accuracy across all 370 experiments.</Text>
  <Row gap={30} wrap>
   <Stat value="16" label="unique experiments"/>
   <Stat value="318" label="capillaries; 70 no-fly controls"/>
   <Stat value="702" label="added native time bins"/>
   <Stat value={pct(D.summary.capillaryRelativeChange.median)} label="median within-capillary net change"/>
  </Row>
  <Text size="small" tone="secondary">Net-change median uses 219 fly capillaries with nonzero automatic totals; 29 zero-baseline capillaries are excluded from that ratio. No microlitre total is inferred by summing pixels across capillaries. There are 126 resized and 12 removed bins; 126 further overrides leave amplitude unchanged.</Text>

  <H2>What the edits show</H2>
  <Grid columns="repeat(auto-fit,minmax(300px,1fr))" gap={20}>
   <Stack gap={6}><Text weight="semibold">Single-bin rate versus threshold</Text>
    <BarChart categories={["Below rate threshold","Above rate threshold","Unreliable tracking"]} series={[{name:"Added bins",data:[551,84,67],tone:"info"}]} height={210} horizontal showValues/>
    <Text size="small" tone="secondary">X: added native bins (count). Y: source-quality/rate category. Source: all 16 archives, all recorded times. These categories are descriptions, not exclusive causes of a missed episode.</Text>
   </Stack>
   <Stack gap={10}><Text weight="semibold">Visual criteria supplied by the experimenter</Text>
    <Text>1. A lasting step interrupting a long, smoothly changing meniscus trajectory. Fit the background drift on both sides; do not require perfectly flat plateaus.</Text>
    <Text>2. A distinctly faster decline lasting roughly 5–10 intervals. Assess the cumulative signed movement and local slope change, including small individual increments.</Text>
    <Text>3. A black glass/tape border can attract tracking. A physical border needs image evidence and an identity distinct from the meniscus; a dark line alone is not enough to classify it.</Text>
    <Text size="small" tone="secondary">Current v6.2 baseline searches 1–6 bins. Its local recovery starts from a threshold-sized adjacent increment and covers at most 3 bins. Extending the time window alone would not remove those other gates.</Text>
   </Stack>
  </Grid>

  <H2>Why a global threshold reduction is not a sufficient fix</H2>
  <Table headers={["Threshold setting","Added bins recovered at exact bin","Within ±1 bin","New unedited bins","New control bins","Original positive bins no longer at same bin"]}
   rows={D.probes.sweeps.map(s=>[`${Math.round(s.factor*100)}% of current`,`${s.addedExact} / ${s.annotatedAdded}`,s.addedWithinOne,s.newUneditedBins,s.newControlBins,s.baselineBinsLost])}
   columnAlign={["left","right","right","right","right","right"]}/>
  <Text size="small" tone="secondary">Read-only probes, not changes to the plugin. {D.probes.matchedCaps}/318 source previews reproduce the archived levels, times, quality, threshold and automatic intake. Twelve capillaries in experiment 009 do not reproduce the archived threshold and/or automatic intake and are excluded from probes; 673 of 702 additions remain. New control bins are included in new unedited bins. Unedited does not mean incorrect. ±1-bin matches are not one-to-one event matches; regrouping can change timing and remove an original positive bin without losing the whole episode.</Text>
  <Text>The 20% reduction recovers 177/673 exact-bin additions, but introduces 36 additional control detections and substantially reorganizes bin allocation. This is evidence to test local episode/step fits and image-supported tracking recovery, rather than select a universally lower threshold.</Text>

  <H2>Experiment-by-experiment evidence</H2>
  <Table headers={["Experiment","Condition","Caps / controls","Added","Resized","Removed","Median paired net change","Control bins auto → edited","Inspect"]}
   rows={experiments.map(e=>[e.id,`${e.chemical} ${e.concentration}`,`${e.capillaries} / ${e.controls}`,count(e,"added"),count(e,"resized"),count(e,"removed"),pct(e.medianCapillaryChange),`${e.autoControlPositive} → ${e.editedControlPositive}`,<Button key={e.id} onClick={()=>{setExp(e.id);setScope("fly");setCap("1L");setAction("all");setLimit(30);}}>Inspect</Button>])}
   columnAlign={["left","left","right","right","right","right","right","right","left"]}/>
  <Text size="small" tone="secondary">Counts are time bins, not distinct gulps. Conditions: 8 caffeine, 7 denatonium and 1 nicotine experiment. Experiment 006 is counted once; 016 lacks 3L and 3R. Recorded no-fly control signals decrease from 21 to 11 positive bins after editing; the remaining 11 are confined to 010 (0R, 9R) and 021 (8R, 9R) and warrant image review, not automatic acceptance.</Text>

  <H2>Inspect a capillary</H2>
  <Row gap={12} wrap>
   <Text as="span">Experiment</Text><Select value={exp} onChange={v=>{setExp(v);setLimit(30);}} options={experiments.map(e=>({value:e.id,label:`${e.id} · ${e.chemical} ${e.concentration}`}))}/>
   <Text as="span">Scope</Text><Select value={scope} onChange={v=>{setScope(v);setLimit(30);}} options={[{value:"fly",label:"Fly capillaries"},{value:"control",label:"No-fly controls"},{value:"all",label:"All"}]}/>
   <Text as="span">Capillary</Text><Select value={selectedCap.cap} onChange={v=>{setCap(v);setLimit(30);}} options={caps.map(c=>({value:c.cap,label:c.cap}))}/>
   <Select value={action} onChange={v=>{setAction(v);setLimit(30);}} options={[{value:"all",label:"All recorded overrides"},{value:"added",label:"Added"},{value:"resized",label:"Resized"},{value:"removed",label:"Removed"},{value:"manual_override",label:"Other overrides"}]}/>
  </Row>
  <Text size="small" tone="secondary">{selectedExperiment.archive} · {selectedCap.autoPositive} automatic positive bins → {selectedCap.editedPositive} edited · net change {pct(selectedCap.relativeChange)} · rate threshold {fmt(selectedCap.threshold,3)} px/min.</Text>
  <Trajectory cap={selectedCap}/>
  {changes.length>0&&<><Table headers={["Bin","Time (min)","Edit","Rate / threshold (px/min)","Automatic → edited net (px)","Quality / local proposal","Auto within ±1 bin"]}
   rows={changes.slice(0,limit).map(c=>[c.frame,fmt(c.time),c.action,`${fmt(c.rate,3)} / ${fmt(c.threshold,3)}`,`${fmt(c.before,3)} → ${fmt(c.edited,3)}`,`${c.reason}${c.singleGate?`; local check: ${c.singleGate}`:""}`,c.nearbyAutomatic.join(", ")||"—"])}
   columnAlign={["right","right","left","right","right","left","right"]}/>
   {changes.length>limit&&<Button onClick={()=>setLimit(limit+30)}>Show 30 more ({changes.length-limit} remaining)</Button>}</>}

  <H2>Original-image examples</H2>
  <Select value={exampleId} onChange={v=>{setExample(v);const e=D.examples[Number(v)];setExp(e.experiment);setScope(e.cap.startsWith("9")?"all":"fly");setCap(e.cap);}} options={D.examples.map((e,i)=>({value:String(i),label:`${e.experiment} · ${e.cap} · ${e.first}${e.last!==e.first?`–${e.last}`:""}: ${e.title}`}))}/>
  <Text weight="semibold">{example.experiment} / {example.cap}, bins {example.first}–{example.last}: {example.title}</Text>
  <img src={example.image} alt={example.title} style={{display:"block",maxWidth:"100%",width:Math.min(900,Math.max(420,(example.last-example.first+21)*16)),height:"auto",imageRendering:"pixelated"}}/>
  <Text size="small" tone="secondary">{example.caption} These are selected illustrations, not a random validation sample. Dark-band identity (glass/tape versus liquid) is not annotated in these files.</Text>
  {example.fullImage&&<Row gap={20} align="start"><img src={example.fullImage} alt="Full 017/9L kymograph showing stationary bands below the moving meniscus" style={{width:240,height:"auto",maxWidth:"45%",imageRendering:"pixelated"}}/><Text style={{maxWidth:600}}>Full source image: the strong stationary dark band is visible well below the meniscus in this example. Its presence alone does not explain the removed events near the top. Border-related errors need the spatial relation between the tracked meniscus and the band, and must be checked where they approach or cross. The export does not identify the physical material of the band.</Text></Row>}

  <H2>Examples of consecutive small-bin episodes</H2>
  <Table headers={["Experiment / capillary","Selected bins","Added bins","Signed excess rate (px/min)","Quiet before","Quiet after"]}
   rows={D.runs.map(r=>[`${r.experiment} / ${r.cap}`,`${r.first}–${r.last}`,r.addedBins,fmt(r.signedNetRate,3),fmt(r.quietBeforeRate,3),fmt(r.quietAfterRate,3)])}
   columnAlign={["left","right","right","right","right","right"]}/>
  <Text size="small" tone="secondary">Strict runs of at least five consecutive edited-positive bins containing an addition; this definition misses bursts interrupted by zero bins. Quiet references are median reliable residual rates in edited-zero bins up to 12 intervals before/after. This describes context, not an acceptance rule. The 015/4R and 015/8R six-bin examples have faster signed residual movement than their flanks despite small individual steps.</Text>

  <H2>What to do next</H2>
  <Text>Use this set to build three separate regression families: persistent steps on drifting backgrounds; 5–10-bin faster-decline episodes; and tracking near glass/tape borders. Keep cumulative amount and temporal allocation separate, since 42 added bins already have a neighbouring automatic positive bin. Test step/episode candidates against quiet stretches and no-fly controls before changing acceptance rules.</Text>
  <Text>Further general sampling is not needed before that work. Additional annotation would be most valuable later as an independent validation set, with more nicotine experiments and a few explicitly identified border-related examples from existing images.</Text>
  <Text size="small" tone="secondary">Limits: user edits are a reference for development, not independently adjudicated ground truth. Unchanged bins are not confirmed positives or negatives. Range-generated measurements and hand-set amplitudes are not separately identified by the export. Border identity is not stored. No overall sensitivity, specificity or treatment effect is claimed. Automatic detection rules were not changed.</Text>
  <Text style={note}>Source: the 16 named review ZIPs supplied in this chat, parsed once per unique experiment; complete native time ranges in each archive. Analysis date: 1 November 2026. Archive hashes and source-match validation are retained in the workspace analysis record.</Text>
 </Stack>;
}
'''


def main(root, destination):
    root = Path(root)
    data = json.loads((root / "cohort.json").read_text(encoding="utf-8"))
    for cap in data["capillaries"]:
        cap["series"] = [[round(v, 5) if isinstance(v, float) else v for v in row] for row in cap["series"]]
    embedded = json.dumps(json.dumps(data, ensure_ascii=False, separators=(",", ":")), ensure_ascii=False)
    destination = Path(destination)
    destination.write_text(TEMPLATE.replace("__DATA__", embedded), encoding="utf-8")
    print(f"Canvas prepared: {destination} ({destination.stat().st_size:,} bytes)")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
