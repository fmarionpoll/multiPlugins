const fs=require('fs');
const prefix=fs.readFileSync('.cleanup-spot-measures.cjs','utf8').split('const dead=')[0];
const helper={};new Function('require','module',prefix+'module.exports={edit,mt,ms,removeMembers,mask,blockEnd};')(require,helper);
const {edit,mt,ms,removeMembers,mask,blockEnd}=helper.exports;
const dead=['FILTER_GREY_CONTRAST','FILTER_GREY_CONTRAST_MASKED','FILTER_GREY_CONTRAST_SMOOTHED','FILTER_GREY_RESIDUAL','FILTER_V5_PIXEL_COUNT','FILTER_V5_GREY','FILTER_V5_GREY_PRE_GATE','FILTER_V5_GREY_SMOOTHED','AGG_FILTER_V5_DEPLETION','AGG_FILTER_V5_PIXEL_SUM','AREA_OUT','AREA_DIFF'];
function walk(d){return fs.readdirSync(d,{withFileTypes:true}).flatMap(e=>e.isDirectory()?walk(d+'/'+e.name):e.name.endsWith('.java')?[d+'/'+e.name]:[]);}
const files=[...walk('multiTools/src'),...walk('multiSPOTS/src')];
function prune(s, expr) {while(true){const re=new RegExp('(?:else\\s+)?if \\('+expr+'\\) \\{','g'),m=re.exec(s);if(!m)break;const end=blockEnd(s,s.indexOf('{',m.index));let tail=s.slice(end);const next=/^\s*else\s*/.exec(tail);if(next) {tail=tail.slice(next[0].length);s=s.slice(0,m.index)+(m[0].startsWith('else')?'else ':'')+tail;}else s=s.slice(0,m.index)+tail;}return s;}
const removedOptions=['sumV2','spotSumNoFlyV2','spotSumCleanV2','spotAreaCountV5','spotGreySumV5','spotGreySumCleanV5'];
for(const f of files)edit(f,s=>{
 s=s.replace(/\/\*\*[\s\S]*?\*\//g,doc=>/FILTER_V5|AGG_FILTER_V5|FILTER_GREY_CONTRAST|FILTER_GREY_RESIDUAL|AREA_OUT|AREA_DIFF|V[235] (?:spot|channel|Tier|diagnostic|parallel)|V5:|sumNoFlyV2|sumCleanV3/.test(doc)?'':doc);
 for(const n of dead){s=s.replace(new RegExp('\\b[\\w.]+\\s*==\\s*EnumResults\\.'+n+'\\b','g'),'false').replace(new RegExp('\\b[\\w.]+\\s*!=\\s*EnumResults\\.'+n+'\\b','g'),'true');s=s.replace(new RegExp('EnumResults\\.'+n+'\\s*,\\s*','g'),'').replace(new RegExp(',\\s*EnumResults\\.'+n+'\\b','g'),'');}
 s=s.replace(/false\s*\|\|\s*/g,'').replace(/\s*\|\|\s*false/g,'').replace(/true\s*&&\s*/g,'').replace(/\s*&&\s*true/g,'');
 s=prune(s,'false');
 for(const n of removedOptions){s=prune(s,'o\\.'+n);s=s.replace(new RegExp('\\s*\\|\\|\\s*o\\.'+n+'\\b','g'),'');}
 return s;
});
for(const file of ['SpotLevelDetectorFromCam.java','SpotLevelDetectorFromCamBasic.java'])edit(mt+'service/'+file,s=>{
 s=s.replace(/^.*(?:V2_BG_ANNULUS|applyFlyPlateauOnSumNoFlyV2ForSpots|rebuildSumCleanOnlyForSpotsV2|rebuildV3ResidualFromSumCleanMedian).*\n/gm,'');
 for(const name of ['getBackgroundCorrectedForegroundGrey','getFlyPixelExcludedForegroundContrast','getSmoothedForegroundContrast','getGreySignalPooledMedianResidual']) {
 const re=new RegExp('\\t\\t\\tif \\(spot\\.'+name+'\\(\\) != null\\) \\{[^]*?\\n\\t\\t\\t\\}\\n','g');s=s.replace(re,''); }
 const a=s.indexOf('\t\t\t// V2: estimate foreground'); const b=s.indexOf('\t\t\tspot.getFlyCoveredPixelCount().setIsPresentAt',a);if(a>=0)s=s.slice(0,a)+s.slice(b);
 const c=s.indexOf('\t\t// Ellipse parameters');const d=s.indexOf('\t\tfor (int i = 0; i < maskX.length; i++)',c);if(c>=0)s=s.slice(0,c)+s.slice(d);
 const e=s.indexOf('\t\t\t// Annulus background:');if(e>=0){const start=s.indexOf('\t\t\tif (rx',e),end=blockEnd(s,s.indexOf('{',start));s=s.slice(0,e)+s.slice(end);}
 s=s.replace(/^.*(?:double sumUnderThreshold|int nUnder|double sumBgAnnulus|int nBgAnnulus|int nOver|nOver\+\+|nOverNoFly\+\+).*\n/gm,'');
 s=s.replace(/\s*else \{\n\s*sumUnderThreshold \+= valueSpot;\n[^]*?\n\t\t\t\}/g,'');
 return s;
});
edit(mt+'experiment/spot/SpotPreConsumedSupport.java',s=>s.split('\n').filter(l=>!(/getSmoothedForegroundContrast|getBackgroundCorrectedForegroundGrey|getFlyPixelExcludedForegroundContrast/.test(l))).join('\n'));
edit(mt+'tools/results/ResultsOptions.java',s=>s.split('\n').filter(l=>!removedOptions.some(n=>new RegExp('\\b'+n+'\\b').test(l))).join('\n'));
edit(mt+'experiment/spot/SpotDetectionProvenance.java',s=>s.replace(/\t\tFILTER_V5\([^]*?\),\n/,'').replace('family == Family.FILTER_V5 || ',''));
edit(mt+'experiment/cage/CageSpotStimulusAggregation.java',s=>removeMembers(s,['buildAreaCountV5AggregatesOnNativeSamples']).replace(/isV5DepletionAggregate/g,'isColorDepletionAggregate'));
edit(ms+'dlg/measure_imageFilters/_DlgMeasureUsingFilters_.java',s=>{
 s=s.replace(/^.*(?:ConsumptionV3Panel|ThresholdV5Panel|ChartsV5Panel|idThresholdTabV5|idKymographsTab).*\n/gm,'');s=s.replace(/^\/\/.*\n/gm,'');
 const a=s.indexOf('\t\t} else if ("SPOTS_ROIS_OPEN"');const b=s.indexOf('\n\t\t}\n\t}',a);if(a>=0)s=s.slice(0,a)+s.slice(b);
 s=s.replace(/\t\t\telse \{[^]*?\n\t\t\t\}\n(?=\t\t\texp.getSeqCamData)/,'');return s;
});
edit(ms+'MultiSPOTS.java',s=>s.replace(/\t\tif \(dlgMeasure != null && dlgMeasure.chartsV5Panel != null\)\n\t\t\tdlgMeasure.chartsV5Panel.closeAllCharts\(\);\n/,''));
edit(ms+'dlg/measure_imageFilters/EditSpotMeasuresPanel.java',s=>s.replace(/^.*rebuildGreySumCleanV5OnlyForSpots.*\n/gm,'').replace('sumClean + GREY_SUM_CLEAN_V5 rebuilt','sumClean rebuilt'));
edit(mt+'tools/chart/ChartSpotsOverlayFrame.java',s=>s.split('\n').filter(l=>!l.includes('cbCleanV3')).join('\n'));
// All active graph/export lists must omit removed outputs (including list tails).
for(const f of files)edit(f,s=>s.replace(/^.*\/\/.*(?:FILTER_GREY_CONTRAST|FILTER_GREY_RESIDUAL|V5).*$\n?/gm,''));
module.exports={edit,mt,ms,removeMembers,prune,files,dead};
