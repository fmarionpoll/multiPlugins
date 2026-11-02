"""Create source-image comparisons for a frozen offline trial, never a deployment."""
import base64
import collections
import csv
import hashlib
import html
import io
import json
import math
from pathlib import Path
import sys
from PIL import Image, ImageDraw, ImageFont


def aggregate(results):
    keys = ("added_reference", "recovered_exact", "recovered_within_one", "new_unedited", "positive_zero_overrides", "new_control_bins", "baseline_bins_lost")
    return {key: sum(r[key] for r in results) for key in keys}


def rows_for(root, experiment, cap):
    with (root / "archives" / experiment / f"correlation-{cap}.csv").open(encoding="utf-8-sig") as stream:
        return list(csv.DictReader((line for line in stream if not line.startswith("#")), delimiter=";"))


def image_comparison(root, result, first, last, target, title):
    experiment, cap = result["experiment"], result["cap"]
    rows = rows_for(root, experiment, cap)
    source = Image.open(root / "archives" / experiment / f"kymograph-{cap}.png").convert("RGB")
    start, stop = max(0, first - 8), min(len(rows), last + 9)
    positions = [float(rows[i]["raw_px"]) for i in range(start, stop)]
    for i in range(start, stop):
        raw = float(rows[i]["raw_px"])
        positions.extend([raw, raw - result["trial"][i] - (float(rows[i]["evaporation_increment_px"]) if i else 0)])
        if float(rows[i]["edited_net_intake_px"]) > 0:
            bottom = float(rows[i]["intake_bottom_px"])
            positions.extend([bottom, bottom - float(rows[i]["candidate_amplitude_px"])])
    positions = [v for v in positions if math.isfinite(v)]
    top, bottom = max(0, math.floor(min(positions)) - 5), min(source.height, math.ceil(max(positions)) + 6)
    panel_width, panel_height = 290, max(150, min(260, (bottom - top) * 7))
    canvas = Image.new("RGB", (920, panel_height + 112), "white")
    draw = ImageDraw.Draw(canvas)
    font = ImageFont.truetype("C:/Windows/Fonts/arial.ttf", 13)
    small = ImageFont.truetype("C:/Windows/Fonts/arial.ttf", 11)
    draw.text((12, 6), title, fill="black", font=font)
    auto_net = sum(float(rows[i]["automatic_net_intake_px"]) for i in range(first, last + 1))
    edited_net = sum(float(rows[i]["edited_net_intake_px"]) for i in range(first, last + 1))
    trial_net = sum(result["trial"][first:last + 1])
    draw.text((12, 25), f"{experiment} / {cap}; bins {first}-{last}; {result['split']} set; net px: current {auto_net:.2f}, trial {trial_net:.2f}, edited {edited_net:.2f}", fill="black", font=small)
    labels = ("Current automatic", "Trial: grey existing + blue new", "Your edited reference")
    dx, dy = panel_width / (stop - start), panel_height / (bottom - top)
    for panel, label in enumerate(labels):
        px, py = 12 + panel * 304, 64
        crop = source.crop((start, top, stop, bottom)).resize((panel_width, panel_height), Image.Resampling.NEAREST)
        canvas.paste(crop, (px, py))
        draw.text((px, 46), label, fill="black", font=small)
        def x(i): return px + (i - start + .5) * dx
        def y(v): return py + (v - top) * dy
        for i in range(start + 1, stop):
            a, b = float(rows[i - 1]["raw_px"]), float(rows[i]["raw_px"])
            if math.isfinite(a) and math.isfinite(b):
                draw.line((x(i - 1), y(a), x(i), y(b)), fill="orange", width=1)
        for i in range(start, stop):
            row = rows[i]
            auto, edited, trial = float(row["automatic_net_intake_px"]), float(row["edited_net_intake_px"]), result["trial"][i]
            evap = float(row["evaporation_increment_px"]) if i else 0
            if panel < 2 and auto > 0:
                b = float(row["fitted_meniscus_px"])
                draw.line((x(i), y(b), x(i), y(b - auto - evap)), fill=(85, 85, 85), width=2)
            if panel == 1 and trial > auto:
                b = float(row["raw_px"])
                draw.line((x(i), y(b), x(i), y(b - (trial - auto) - evap)), fill="royalblue", width=2)
            if panel == 2 and edited > 0:
                b = float(row["intake_bottom_px"])
                draw.line((x(i), y(b), x(i), y(b - float(row["candidate_amplitude_px"]))), fill="royalblue", width=2)
        for i in (first, last):
            xx = x(i)
            draw.line((xx, py + panel_height - 4, xx, py + panel_height + 3), fill="black", width=1)
            draw.text((xx - 8, py + panel_height + 4), str(i), fill="black", font=small)
    draw.text((12, panel_height + 88), f"Native columns {start}-{stop-1}; image rows {top}-{bottom-1}. Orange: accepted level. All intake bars are vertical.", fill="black", font=small)
    canvas.save(target)
    return "data:image/png;base64," + base64.b64encode(target.read_bytes()).decode("ascii")


def choose_cases(root, profiles, border_candidates):
    primary = profiles["conservative"]["results"]
    sensitive = profiles["sensitive"]["results"]
    pool = []
    for result in primary:
        rows = rows_for(root, result["experiment"], result["cap"])
        for proposal in result["proposals"]:
            added = sum(rows[i]["edit_action"] == "added" for i in range(proposal["first"], proposal["last"] + 1))
            uncertain = any(rows[i]["reliable"] != "true" for i in range(proposal["first"], proposal["last"] + 1))
            below = any(float(rows[i]["excess_px_per_min"]) < float(rows[i]["chosen_threshold_px_per_min"]) for i in range(proposal["first"], proposal["last"] + 1))
            pool.append((result, proposal, added, uncertain, below))
    cases, used = [], set()
    def pick(candidates, title, interpretation):
        candidates = [p for p in candidates if (p[0]["experiment"], p[0]["cap"], p[1]["first"]) not in used]
        if not candidates:
            return
        result, proposal, added, uncertain, below = sorted(candidates, key=lambda p: (-p[2], p[0]["split"] != "check", -p[1]["score"]))[0]
        used.add((result["experiment"], result["cap"], proposal["first"]))
        cases.append({"result": result, "first": proposal["first"], "last": proposal["last"], "profile": "conservative",
                      "title": title, "interpretation": interpretation, "proposal": proposal})
    pick([p for p in pool if p[2] and p[3] and p[1]["kind"] == "step"], "Recovered step with uncertain source tracking",
         "Reliable flanks and a similar meniscus image profile support this proposal despite an uncertain boundary. Check the event's position and size.")
    pick([p for p in pool if p[2] >= 2 and p[1]["kind"] == "episode"], "A faster-decline episode across several intervals",
         "The trial fits a temporary faster decline with a lasting accumulated displacement. The bars allocate one unresolved episode; they are not independently resolved gulps.")
    pick([p for p in pool if p[2] and p[4] and p[1]["kind"] == "step"], "A step below the original per-bin rate threshold",
         "The original rate threshold is not lowered. This separate proposal is supported by a local background fit and image profile. Decide whether the added bar matches feeding.")
    pick([p for p in pool if not p[2] and not p[0]["control"]], "An additional proposal without an edited counterpart",
         "This is not counted as a recovered annotation. It could be an overlooked event, a timing difference, drift or an artifact. This is an important negative check.")
    controls = [(r, p) for r in primary if r["control"] for p in r["proposals"]]
    control_profile = "conservative"
    if not controls:
        controls = [(r, p) for r in sensitive if r["control"] for p in r["proposals"]]
        control_profile = "sensitive"
    if controls:
        result, proposal = sorted(controls, key=lambda p: (p[0]["split"] != "check", -p[1]["amount"]))[0]
        cases.append({"result": result, "first": proposal["first"], "last": proposal["last"], "profile": control_profile,
                      "title": "Trial failure: a new no-fly-control detection",
                      "interpretation": "There was no fly in this control. This proposal fails the negative-control check and is a reason not to deploy the trial. A plausible changing image is not enough to establish feeding.", "proposal": proposal})
    # Include a failure, rather than showing only successful recoveries.
    misses = []
    for result in primary:
        if result["control"]:
            continue
        rows = rows_for(root, result["experiment"], result["cap"])
        for i, row in enumerate(rows):
            if row["edit_action"] == "added" and result["trial"][i] == 0 and 10 <= i < len(rows) - 10:
                if result["trial"][max(0, i - 1):i + 2] and any(v > 0 for v in result["trial"][max(0, i - 1):i + 2]):
                    continue
                misses.append((result, i, float(row["edited_net_intake_px"])))
    if misses:
        result, i, _ = sorted(misses, key=lambda m: (m[0]["split"] != "check", -m[2]))[0]
        cases.append({"result": result, "first": i, "last": i, "profile": "conservative", "title": "An edited intake still missed by the trial",
                      "interpretation": "The trial has not solved this example. Compare the meniscus trajectory and any interfering border before deciding how the model should change.", "proposal": None})
    if border_candidates:
        profile, proposal = max(border_candidates, key=lambda p: p[1]["score"] / p[1]["limit"])
        result = next(r for r in profiles[profile]["results"] if r["experiment"] == proposal["experiment"] and r["cap"] == proposal["cap"])
        extra = " It also fails the fit-score limit." if proposal["score"] <= proposal["limit"] else ""
        cases.append({"result": result, "first": proposal["first"], "last": proposal["last"], "profile": profile,
                      "title": "Dark-band warning: tracked level near a stationary dark row",
                      "interpretation": "A proposal in this region was flagged by the image mask and was not added. Please judge whether this is the glass/tape interference you described; the mask does not identify the material." + extra,
                      "proposal": proposal})
    return cases


CANVAS = r'''import { H1,H2,Text,Stack,Row,Table,Select,Stat,useState,useCanvasState } from "cursor/canvas";
type Metric={added_reference:number;recovered_exact:number;recovered_within_one:number;new_unedited:number;positive_zero_overrides:number;new_control_bins:number;baseline_bins_lost:number};
type Case={id:string;title:string;interpretation:string;profile:string;experiment:string;cap:string;first:number;last:number;split:string;image:string;humanReview?:string;contextImage?:string};
type Data={profiles:Record<string,{development:Metric;check:Metric}>;cases:Case[];config:Record<string,unknown>;borderRejections:number;};
const D=JSON.parse(__DATA__) as Data;
export default function IntakeTrialReview(){const [which,setWhich]=useState("1");const [judgments,setJudgments]=useCanvasState<Record<string,string>>("intake-trial-judgments",{});const current=D.cases.find(c=>c.id===which)||D.cases[0];const m=D.profiles.conservative.check;
return <Stack gap={16} style={{padding:24,maxWidth:1100,margin:"0 auto"}}>
<H1>Trial intake detector: before / after review</H1>
<Text weight="semibold">Offline prototype only. The automatic detector and saved experimental measures are unchanged.</Text>
<Text weight="semibold">This first trial is not ready for use: it still creates detections in no-fly controls.</Text>
<Text>The trial adds persistent steps on a smooth local drift and temporary faster-decline episodes over 5–10 bins. It uses image-profile support, an explicit timing check, and a conservative mask near persistent black bands. It preserves all original automatic events.</Text>
<Row gap={28} wrap><Stat value={`${m.recovered_exact}/${m.added_reference}`} label="edited additions recovered at the exact bin in six reserved experiments"/><Stat value={m.new_control_bins} label="new control bins in the conservative reserved check"/><Stat value={m.new_unedited} label="additional unedited bins needing judgment"/></Row>
<H2>Comparison on experiments reserved before trial development</H2>
<Table headers={["Trial profile","Recovered exact-bin additions","Within ±1 bin","New unedited bins","New control bins","Original automatic bins lost"]} rows={Object.entries(D.profiles).map(([name,p])=>[name,`${p.check.recovered_exact}/${p.check.added_reference}`,p.check.recovered_within_one,p.check.new_unedited,p.check.new_control_bins,p.check.baseline_bins_lost])}/>
<Text size="small" tone="secondary">Development: 006, 007, 010, 011, 012, 014, 015, 016, 143, 146. Reserved check: 008, 009, 013, 017, 021, 147. These experiments had earlier descriptive analysis, so this is an experiment-level development check, not a wholly unseen external validation. Trial settings were frozen before running the check. No-fly development controls set the score limits. Profiles were not selected or retuned using the reserved check.</Text>
<Text size="small" tone="secondary">Counts refer to native time bins, not independent gulps. Unedited does not mean false; ±1-bin matches are not one-to-one. Original automatic control detections remain because this supplement does not delete existing events. The more permissive profile is a stress test, not a proposed default.</Text>
<H2>Images to judge</H2>
<Select value={current.id} onChange={setWhich} options={D.cases.map(c=>({value:c.id,label:`${c.id}. ${c.title} · ${c.experiment}/${c.cap} · ${c.first}–${c.last}`}))}/>
<Text weight="semibold">{current.id}. {current.title}</Text>
<Text size="small" tone="secondary">{current.profile} profile; {current.split} set; experiment {current.experiment}, capillary {current.cap}, bins {current.first}–{current.last}.</Text>
<img src={current.image} alt={current.title} style={{width:"100%",height:"auto",maxWidth:1000,imageRendering:"pixelated"}}/>
<Text>{current.interpretation}</Text>
{current.humanReview&&<Text weight="semibold">Human review: {current.humanReview}</Text>}
{current.contextImage&&<><Text weight="semibold">Wider context: earlier edited intake and the next step</Text><img src={current.contextImage} alt="Unchanged trial and edited results in wider temporal context" style={{width:"100%",height:"auto",maxWidth:1000,imageRendering:"pixelated"}}/><Text size="small" tone="secondary">The earlier edited bottom is only a candidate trusted anchor. The latest positive bin may itself follow the contaminated trace. This image adds context; no detector was rerun.</Text></>}
<Row gap={12} wrap><Text as="span">Your judgment:</Text><Select value={judgments[current.id]||"unreviewed"} onChange={v=>setJudgments(p=>({...p,[current.id]:v}))} options={[{value:"unreviewed",label:"Not reviewed yet"},{value:"feeding",label:"Looks like feeding"},{value:"artifact",label:"Looks like an artifact"},{value:"timing",label:"Feeding, but timing/size needs adjustment"},{value:"uncertain",label:"Uncertain"}]}/></Row>
<Text>You can also just reply with the case numbers and your judgment. No spreadsheet editing or statistics are needed.</Text>
<H2>What remains unresolved</H2>
<Text>The conservative trial deliberately leaves many small or ambiguous edits unresolved. Proposals beside an existing automatic event are deferred to avoid double counting. Early events without five usable flank observations are also deferred. Presence recovery does not establish accurate amplitude: the net sums printed on each image must also be judged.</Text>
<Text>The dark-band safeguard flags proximity to persistent dark image rows; it cannot identify glass versus tape or prove that a particular tracking failure was caused by either. {D.borderRejections} scored candidates were flagged by that rule across both profiles. Synthetic tests check the mechanism, but this does not establish real-world border-detection accuracy.</Text>
<Text>Existing manually added or resized events are treated as a development reference, not independently adjudicated truth. Trial proposals are not copied into gulps or exports used for biological conclusions.</Text>
<H2>Development-set results</H2><Table headers={["Profile","Recovered exact-bin additions","New control bins","New unedited bins"]} rows={Object.entries(D.profiles).map(([name,p])=>[name,`${p.development.recovered_exact}/${p.development.added_reference}`,p.development.new_control_bins,p.development.new_unedited])}/>
<Text size="small" tone="secondary">Source: the 16 supplied review archives. Results and configuration hashes are retained in the workspace. All images are crops of the original exported kymographs with measured overlays; no image content was generated.</Text>
</Stack>}
'''


def main(root, trial_root, destination):
    root, trial_root, destination = Path(root), Path(trial_root), Path(destination)
    destination.mkdir(parents=True, exist_ok=True)
    profiles, configs, border, border_candidates = {}, {}, 0, []
    for profile in ("conservative", "sensitive"):
        phases = {phase: json.loads((trial_root / profile / f"{phase}.json").read_text(encoding="utf-8")) for phase in ("development", "check")}
        config_bytes = (trial_root / profile / "frozen-config.json").read_bytes()
        digest = hashlib.sha256(config_bytes).hexdigest()
        for phase in phases.values():
            assert phase["configuration_sha256"] == digest, "Configuration changed across evaluation splits"
            border += sum(c["reason"] == "stationary_dark_band_near_track" for c in phase["rejected"])
            border_candidates.extend((profile, c) for c in phase["rejected"] if c["reason"] == "stationary_dark_band_near_track")
        configs[profile] = json.loads(config_bytes)
        profiles[profile] = {"results": phases["development"]["results"] + phases["check"]["results"],
                             "development": aggregate(phases["development"]["results"]), "check": aggregate(phases["check"]["results"])}
    raw_cases = choose_cases(root, profiles, border_candidates)
    feedback_file = Path(__file__).with_name("intake_trial_feedback.json")
    feedback = json.loads(feedback_file.read_text(encoding="utf-8"))["reviews"] if feedback_file.exists() else []
    cases = []
    for i, case in enumerate(raw_cases, 1):
        result = case["result"]
        review = next((r for r in feedback if r["experiment"] == result["experiment"] and r["capillary"] == result["cap"]
                       and r["first_bin"] == case["first"] and r["last_bin"] == case["last"]), None)
        if review:
            case["title"] = "Step found, but trial size includes tape-related bleeding"
            case["interpretation"] = "The user prefers the third panel's edited measurement. The trial located the event but included a contaminated upper region. This is not a confirmed correct amplitude."
        target = destination / f"case-{i}.png"
        image = image_comparison(root, result, case["first"], case["last"], target, f"{i}. {case['title']}")
        cases.append({"id": str(i), "title": case["title"], "interpretation": case["interpretation"],
                      "profile": case["profile"], "experiment": result["experiment"], "cap": result["cap"],
                      "first": case["first"], "last": case["last"], "split": result["split"], "image": image})
        if review:
            cases[-1]["humanReview"] = review["user_explanation"]
            cases[-1]["contextImage"] = image_comparison(root, result, review["candidate_previous_anchor_bin"], review["first_bin"],
                    destination / f"case-{i}-context.png", "Wider context only: candidate earlier anchor and the next intake")
    payload = {"profiles": {name: {k: v for k, v in value.items() if k != "results"} for name, value in profiles.items()},
               "cases": cases, "config": configs, "borderRejections": border}
    (trial_root / "review.json").write_text(json.dumps(payload, allow_nan=False), encoding="utf-8")
    canvas = CANVAS.replace("__DATA__", json.dumps(json.dumps(payload, separators=(",", ":"))))
    (destination / "intake-trial-review.canvas.tsx").write_text(canvas, encoding="utf-8")
    panels = []
    for case in cases:
        reviewed = ''
        if case.get("humanReview"):
            reviewed = f'<p><b>Human review:</b> {html.escape(case["humanReview"])}</p><h3>Wider temporal context</h3><img src="{case["contextImage"]}" alt="Earlier edited intake and next step"><p><small>The earlier edited bottom is a candidate trusted anchor, not verified ground truth. The latest positive bin can itself follow the contaminated raw trace. This adds context without rerunning the detector.</small></p>'
        panels.append(f'<section id="case-{case["id"]}"><h2>{case["id"]}. {html.escape(case["title"])}</h2><p>{case["profile"]} · {case["split"]} · {case["experiment"]}/{case["cap"]}, bins {case["first"]}–{case["last"]}</p><img src="{case["image"]}" alt="{html.escape(case["title"])}"><p>{html.escape(case["interpretation"])}</p>{reviewed}</section>')
    summaries = []
    for name, value in profiles.items():
        m = value["check"]
        summaries.append(f'<tr><td>{name}</td><td>{m["recovered_exact"]}/{m["added_reference"]}</td><td>{m["recovered_within_one"]}</td><td>{m["new_unedited"]}</td><td>{m["new_control_bins"]}</td><td>{m["baseline_bins_lost"]}</td></tr>')
    page = '<!doctype html><meta charset="utf-8"><title>Intake trial review</title><style>body{font:16px/1.5 Arial,sans-serif;max-width:1000px;margin:32px auto;padding:0 20px;color:#202124;background:white}h1{font-size:26px}h2{font-size:20px}img{width:100%;height:auto}table{border-collapse:collapse;width:100%;font-size:14px}td,th{padding:8px;border-bottom:1px solid #ccc;text-align:left}section{margin-top:32px;border-top:1px solid #ddd;padding-top:12px}small{color:#555}</style>'
    page += '<h1>Offline intake-detector trial</h1><p><b>The plugin detector and your saved measures are unchanged.</b> The trial proposes persistent steps and 5–10-bin faster-decline episodes, with image and stationary-dark-band checks.</p>'
    page += '<p><b>This first trial is not ready for use: it still creates detections in no-fly controls.</b></p>'
    page += '<h2>Six reserved experiments</h2><table><thead><tr><th>Profile</th><th>Exact-bin recoveries</th><th>Within ±1 bin</th><th>New unedited bins</th><th>New control bins</th><th>Original bins lost</th></tr></thead><tbody>' + ''.join(summaries) + '</tbody></table>'
    page += '<p><small>Check experiments: 008, 009, 013, 017, 021, 147. Settings were frozen after development on the other ten. Earlier descriptive analysis used this cohort; this is not wholly unseen external validation. Counts are time bins, not independently resolved gulps. Unedited predictions are unconfirmed, not necessarily false. Original automatic detections are preserved.</small></p><p>Please judge the numbered cases as feeding, artifact, uncertain, or feeding with timing/size corrections. You can reply with the numbers; no other work is needed.</p>'
    page += ''.join(panels)
    page += f'<section><h2>Limits</h2><p>The stationary-dark-band rule flagged {border} scored candidates across both profiles. It is a conservative proximity safeguard, not a validated glass/tape classifier. It has synthetic coverage; real border-specific accuracy still needs review. This supplement leaves existing automatic false detections unchanged and defers ambiguous or adjacent proposals.</p><p>No measures were installed, copied to gulps or saved to any experiment. Source: the 16 provided archives.</p></section>'
    (destination / "intake-trial-review.html").write_text(page, encoding="utf-8")
    print(json.dumps({"profiles": payload["profiles"], "borderRejections": border, "cases": [{k: v for k, v in c.items() if k not in ("image", "contextImage")} for c in cases]}, indent=2))


if __name__ == "__main__":
    main(*sys.argv[1:])
