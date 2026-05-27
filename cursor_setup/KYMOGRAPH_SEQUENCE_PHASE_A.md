# Phase A — Kymograph sequence contract and inventory

**Goal:** Freeze the minimal API that callers rely on via `Experiment.getSeqKymos()`, and classify every **public** method on [`SequenceKymos`](../multiTools/src/main/java/plugins/fmp/multitools/experiment/sequence/SequenceKymos.java) for the refactor (Shared vs DiskLayout vs MeasureBridge).

**Generated:** inventory from repository grep of `getSeqKymos().` / `seqKymos.` and `SequenceKymos` public members. Inherited [`SequenceCamData`](../multiTools/src/main/java/plugins/fmp/multitools/experiment/sequence/SequenceCamData.java) methods are listed separately when used from outside without going through `Sequence`-only APIs.

---

## 1. Minimal contract (keep stable through refactor)

These operations appear across **multiTools**, **multiCAFE**, and **multiSPOTS96** and should remain available on whatever type `getSeqKymos()` returns (same signatures or thin adapters).

| Concern | Operations (effective contract) |
|--------|-----------------------------------|
| **ICY sequence** | `getSequence()` — size, bounds, viewers, ROI add/remove, begin/endUpdate, close, id |
| **Frame list / paths** | `getImagesList()`, `getFileNameFromImageList(t)` |
| **Pixels** | `getSeqImage(t, c)` (via `SequenceCamData`) |
| **Loader / time** | `getImageLoader()`, `getCurrentFrame()` |
| **Load pipeline** | `loadKymographs(List<ImageFileData>, ImageAdjustmentOptions)` and overload |
| **Sizing** | `calculateMaxDimensions(List<ImageFileData>)` |
| **Metadata** | `getKymographInfo()`, `updateMaxDimensionsFromSequence()` |
| **Lifecycle** | `closeSequence()` |
| **Viewer** | `displayViewerAtRectangle(...)` (multiTools `DetectLevels`) |

**Disk layout:** `SequenceKymos` still exposes the same methods; implementation lives in [`KymographDiskLayout`](../multiTools/src/main/java/plugins/fmp/multitools/experiment/sequence/KymographDiskLayout.java) (`PerSpotRoiTiffDiskLayout`, `CageStackTiffDiskLayout`) and [`CapillaryKymographNameResolution`](../multiTools/src/main/java/plugins/fmp/multitools/experiment/sequence/CapillaryKymographNameResolution.java) for frame-path → capillary.

- `createCageSpotKymographFileList(baseDir, cages)` — SPOTS cage stack TIFFs (`CageStackTiffDiskLayout`).
- `createKymographFileList` / deprecated `loadListOfPotentialKymographsFromSpots` — per-spot TIFF naming (`PerSpotRoiTiffDiskLayout`).

**Measure bridge — capillary (today on `SequenceKymos`, target extract):**

- `getCapillaryForFrame`, `syncROIsForCurrentFrame`, `replaceCapillaryMeasureRoisAtT`
- `validateROIs`, `validateRois`, `validateRoisAtT`, `removeROIsPolylineAtT`
- `transferKymosRoisToCapillaries_Measures`, `transferKymosRoi_at_T_To_Capillaries_Measures` (overloads)
- `validateLinearROIsAtT`, `validateGulpROIsAtT` (overloads)
- `transferCapillariesMeasuresToKymos`, `updateROIFromCapillaryMeasure`, `saveKymosCurvesToCapillariesMeasures`

---

## 2. `SequenceKymos` public API — classification

| Method | Category | Notes |
|--------|----------|--------|
| Constructors + `kymographBuilder()` | Shared | Wiring |
| `getKymographInfo()` | Shared | Stats |
| `updateMaxDimensionsFromSequence()` | Shared | |
| `loadKymographs(...)` | Shared | Delegates to `loadImageList`; uses `KymographConfiguration` |
| `calculateMaxDimensions(...)` | Shared | Used before load; touches files |
| `updateConfiguration` / `getConfiguration` | Shared | Default extensions `tif`/`tiff` |
| `createKymographFileList` | **DiskLayout** | Per-spot ROI name TIFF paths |
| `createCageSpotKymographFileList` | **DiskLayout** | `kymocage_<id>.tif*` |
| `loadListOfPotentialKymographsFromSpots` | **DiskLayout** | Deprecated delegate |
| `loadKymographImagesFromList` | Shared | Deprecated; forwards to `loadKymographs` |
| `validateRois()` | **MeasureBridge** | Delegates to `validateROIs()` |
| `validateROIs()` | **MeasureBridge** | Capillary polyline ROI rules |
| `validateRoisAtT` | **MeasureBridge** | |
| `removeROIsPolylineAtT` | **MeasureBridge** | |
| `updateROIFromCapillaryMeasure` | **MeasureBridge** | |
| `transferKymosRoisToCapillaries_Measures` | **MeasureBridge** | |
| `transferKymosRoi_at_T_To_Capillaries_Measures` (both) | **MeasureBridge** | |
| `validateLinearROIsAtT` (both) | **MeasureBridge** | |
| `validateGulpROIsAtT` (all overloads) | **MeasureBridge** | |
| `transferCapillariesMeasuresToKymos` | **MeasureBridge** | |
| `getCapillaryForFrame` | **DiskLayout** (naming) + **MeasureBridge** (consumer) | `SequenceKymos` delegates to [`CapillaryKymographMeasureBridge`](../multiTools/src/main/java/plugins/fmp/multitools/experiment/sequence/CapillaryKymographMeasureBridge.java) → `CapillaryKymographNameResolution` |
| `syncROIsForCurrentFrame` | **MeasureBridge** | |
| `replaceCapillaryMeasureRoisAtT` | **MeasureBridge** | |
| `saveKymosCurvesToCapillariesMeasures` | **MeasureBridge** | Pulls via `CapillariesKymosMapper` |
| Inner `Builder` | Shared | |

---

## 3. Call-site inventory (by module)

### multiTools

| File | Uses (on seqKymos) |
|------|---------------------|
| [`Experiment.java`](../multiTools/src/main/java/plugins/fmp/multitools/experiment/Experiment.java) | `getImageLoader`, `calculateMaxDimensions`, `loadKymographs`, `getKymographKind` / `setKymographKind`, `listPotentialKymographFrames`, `getSequence`, `closeSequence`, `getKymographInfo`, `updateMaxDimensionsFromSequence`, capillary sync helpers |
| [`ExperimentService.java`](../multiTools/src/main/java/plugins/fmp/multitools/service/ExperimentService.java) | `closeSequence` |
| [`KymographService.java`](../multiTools/src/main/java/plugins/fmp/multitools/service/KymographService.java) | (loads via service; uses `SequenceKymos` parameter) |
| [`CageKymoAnalyzer.java`](../multiTools/src/main/java/plugins/fmp/multitools/service/CageKymoAnalyzer.java) | `getFileNameFromImageList` |
| [`CageKymographViewerUtil.java`](../multiTools/src/main/java/plugins/fmp/multitools/series/CageKymographViewerUtil.java) | local `SequenceKymos` ref |
| [`LevelDetectorFromKymo.java`](../multiTools/src/main/java/plugins/fmp/multitools/service/LevelDetectorFromKymo.java) | `getSequence`, `getFileNameFromImageList` |
| [`GulpDetector.java`](../multiTools/src/main/java/plugins/fmp/multitools/service/GulpDetector.java) | `getSequence`, `getFileNameFromImageList` |
| [`CapillariesKymosMapper.java`](../multiTools/src/main/java/plugins/fmp/multitools/experiment/capillaries/CapillariesKymosMapper.java) | `getSequence`, `getCurrentFrame`, `syncROIsForCurrentFrame`, `validateRoisAtT`, `transferKymosRoi_at_T_To_Capillaries_Measures` |
| [`TimestepResolver.java`](../multiTools/src/main/java/plugins/fmp/multitools/experiment/timebase/TimestepResolver.java) | `getImageLoader` |
| [`ResultsArrayFromCapillaries.java`](../multiTools/src/main/java/plugins/fmp/multitools/tools/results/ResultsArrayFromCapillaries.java) | `getImageLoader` |
| [`XLSExportMeasuresFromGulp.java`](../multiTools/src/main/java/plugins/fmp/multitools/tools/toExcel/XLSExportMeasuresFromGulp.java) | `getImageLoader` |
| [`CapillaryChartInteractionHandler.java`](../multiTools/src/main/java/plugins/fmp/multitools/tools/chart/interaction/CapillaryChartInteractionHandler.java) | `getSequence`, `getImagesList` |
| [`KymoMergedRegionsOverlay.java`](../multiTools/src/main/java/plugins/fmp/multitools/tools/overlay/KymoMergedRegionsOverlay.java) | `getFileNameFromImageList` |
| [`KymoMetricThresholdOverlay.java`](../multiTools/src/main/java/plugins/fmp/multitools/tools/overlay/KymoMetricThresholdOverlay.java) | `getFileNameFromImageList` |
| [`DetectLevels.java`](../multiTools/src/main/java/plugins/fmp/multitools/series/DetectLevels.java) | `displayViewerAtRectangle` |

### multiCAFE

| File | Uses |
|------|------|
| `DetectLevelsDlgFromKymo`, `DetectGulpsDlgFromKymo`, `DetectLevelsDlgKMeans`, `Chart`, `Adjust`, `Filter` | `getSequence`, viewers, ROI ops |
| [`Intervals.java`](../multiCAFE/src/main/java/plugins/fmp/multicafe/dlg/kymos/Intervals.java) | `getSequence`, `getCapillaryForFrame`, `getFileNameFromImageList`, `validateGulpROIsAtT`, `validateRoisAtT`, `transferKymosRoi_at_T_To_Capillaries_Measures`, `syncROIsForCurrentFrame`, `getCurrentFrame` |
| [`LoadSave.java`](../multiCAFE/src/main/java/plugins/fmp/multicafe/dlg/kymos/LoadSave.java) | `getSeqImage`, `getSequence`, `closeSequence` |
| [`LoadSaveLevels.java`](../multiCAFE/src/main/java/plugins/fmp/multicafe/dlg/levels/LoadSaveLevels.java) | `syncROIsForCurrentFrame` |
| [`Binsize.java`](../multiCAFE/src/main/java/plugins/fmp/multicafe/dlg/kymos/Binsize.java) | `getSequence`, `getKymographInfo` |
| [`Options.java`](../multiCAFE/src/main/java/plugins/fmp/multicafe/dlg/experiment/Options.java) | `getSequence` viewer |
| [`CafeExperimentOpenPipeline.java`](../multiCAFE/src/main/java/plugins/fmp/multicafe/dlg/browse/CafeExperimentOpenPipeline.java) | `getSequence().addListener` |

### multiSPOTS96

| File | Uses |
|------|------|
| [`AnalysisPanel.java`](../multiSPOTS96/src/main/java/plugins/fmp/multiSPOTS96/dlg/kymograph/AnalysisPanel.java) | `getSequence` (viewer, overlays) |
| [`LoadSavePanel.java`](../multiSPOTS96/src/main/java/plugins/fmp/multiSPOTS96/dlg/kymograph/LoadSavePanel.java) | `getSequence`, `getFileNameFromImageList`, `getSeqImage`, `createCageSpotKymographFileList`, `closeSequence` |
| [`Spots96ExperimentOpenPipeline.java`](../multiSPOTS96/src/main/java/plugins/fmp/multiSPOTS96/dlg/browse/Spots96ExperimentOpenPipeline.java) | `getSequence().addListener` |

---

## 4. Related types (not on `SequenceKymos` but part of the story)

- [`SequenceKymosUtils`](../multiTools/src/main/java/plugins/fmp/multitools/experiment/sequence/SequenceKymosUtils.java) — camera line ROI ↔ `Capillaries` list sync (**MeasureBridge** / setup).
- [`KymographService`](../multiTools/src/main/java/plugins/fmp/multitools/service/KymographService.java) — loading orchestration (**Shared**).

---

## 5. Phase B hint

- Move **`createKymographFileList` / `createCageSpotKymographFileList`** first into a `KymographDiskLayout` implementation; keep `SequenceKymos` delegating.
- Split **`getCapillaryForFrame`** into layout (name parsing) vs optional thin wrapper on capillary model, or keep it with capillary bridge if you want one hop.
