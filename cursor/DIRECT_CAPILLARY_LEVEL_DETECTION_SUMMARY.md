# Direct capillary level detection – implementation summary

This document summarizes the implementation of **Option A** from the plan “Direct capillary level detection from cam”: an additional way to detect liquid levels in capillaries directly from cam images, without building or loading kymographs.

## Goal

- **Existing pipeline:** (1) Build kymographs from cam images + capillary ROI lines. (2) Detect liquid/air transition on the kymograph (pass1 + pass2).
- **New option:** Detect the transition **per time bin** from the cam image: extract a 1D profile along the capillary ROI at each time, run the same pass1/pass2 logic on that profile, and store the same `polylineLevel` result. No kymograph build/load.

## What was implemented

### 1. CapillaryProfileExtractor (multiTools)

**File:** `multiTools/src/main/java/plugins/fmp/multitools/service/CapillaryProfileExtractor.java`

- **Role:** Extract a 1D intensity profile along a capillary ROI from a cam image (same sampling idea as one column of the kymograph).
- **API:**
  - `buildMasksAlongRoi(ROI2D roi, imageWidth, imageHeight, diskRadius)` → list of pixel masks along the line (Bresenham + horizontal segment per position, matching KymographBuilder).
  - `extractProfileFromMasks(IcyBufferedImage sourceImage, masks)` → `int[]` profile (one value per position, channel 0).
- **Reuse:** Uses `ROI2DUtilities.getCapillaryPoints(roi)` and `Bresenham.getPixelsAlongLineFromROI2D(points)`.

### 2. LevelDetectorFromCam (multiTools)

**File:** `multiTools/src/main/java/plugins/fmp/multitools/service/LevelDetectorFromCam.java`

- **Role:** Run level detection from cam only: one time bin at a time, one 1D profile per capillary, same pass1/pass2 and storage format as LevelDetector.
- **Flow:**
  - Uses `exp.getKymoFirst_ms()`, `getKymoLast_ms()`, `getKymoBin_ms()` for the time range (same as kymograph build).
  - For each time bin: `findNearestIntervalWithBinarySearch(timeMs, …)` → cam frame index; load cam image; for each capillary (filtered by detectL/detectR): get ROI at that frame via `cap.getAlongTAtT(camFrameIndex)`, extract 1D profile with `CapillaryProfileExtractor`, build thin image (1×profileLength, 3 channels), apply pass1 transform and find top/bottom transition, optionally apply pass2 (same switch as LevelDetector: DERICHE, DERICHE_COLOR, YDIFFN, etc.) and refine with `LevelDetector.findBestPosition` / `detectThresholdUp`.
  - Writes results into `cap.getTopLevel().limit[timeIndex]` and `cap.getBottomLevel().limit[timeIndex]`, then `setPolylineLevelFromTempData(..., 0, nTimeBins-1)` and save.
- **Reuse:** Same `BuildSeriesOptions` (pass1, pass2, transforms, thresholds, jitter2, directionUp, etc.), same `LevelDetector` for pass2 refinement, same capillary measure storage.

### 3. BuildSeriesOptions (multiTools)

**File:** `multiTools/src/main/java/plugins/fmp/multitools/series/options/BuildSeriesOptions.java`

- **Change:** Added `public boolean sourceCamDirect = false`. When `true`, level detection uses the direct-from-cam path instead of kymograph.

### 4. DetectLevels (multiTools)

**File:** `multiTools/src/main/java/plugins/fmp/multitools/series/DetectLevels.java`

- **Change:** In `analyzeExperiment(Experiment exp)`:
  - If `options.sourceCamDirect`: `exp.xmlLoad_MCExperiment()`, `exp.load_capillaries_description_and_measures()`, then `new LevelDetectorFromCam().detectLevels(exp, options)` (no `loadKymographs()`).
  - Else: unchanged (load kymographs, display viewer, `new LevelDetector().detectLevels(exp, options)`).
  - In both cases `exp.closeSequences()` is called at the end.

### 5. DetectLevelsDlg (multiCAFE)

**File:** `multiCAFE/src/main/java/plugins/fmp/multicafe/dlg/levels/DetectLevelsDlg.java`

- **UI:** New checkbox **“Source: Cam (direct)”** next to the Detect button.
- **Logic:**
  - In `initBuildParameters(exp)`: `options.sourceCamDirect = sourceCamDirectCheckBox.isSelected()`.
  - When “Source: Cam (direct)” is selected: `options.kymoFirst = 0`, `options.kymoLast = nTimeBins - 1` (with `nTimeBins` from `getKymoFirst_ms()`, `getKymoLast_ms()`, `getKymoBin_ms()`), so `clearAllMeasures` clears the correct range before detection.
  - When not selected, kymo range is unchanged (selected kymo or full kymograph range).

## Usage

1. Open the Levels tab (DetectLevelsDlg).
2. Check **“Source: Cam (direct)”** to use direct detection from cam.
3. Configure pass1/pass2, transforms, thresholds, L/R as usual.
4. Click **Detect**. Levels are computed from cam images only (no kymograph build/load) and stored in the same capillary measures.
5. Downstream (gulps, charts, export) use the same data as for kymograph-based detection.

## Notes

- **Time range and binning** are those of the experiment (same as for kymograph build). Cam frame list and timing must be available (e.g. after opening the experiment and using cam data).
- **“Detection from ROI rectangle”** and kymograph viewer are not used in the direct path; the full profile along the capillary ROI at each time is used.
- **Output format** is unchanged: `polylineLevel` with x = time index, y = level, so existing tools and persistence remain compatible.

## Files touched

| Location   | File                         | Change |
|-----------|------------------------------|--------|
| multiTools | service/CapillaryProfileExtractor.java | New: profile extraction from cam + ROI. |
| multiTools | service/LevelDetectorFromCam.java      | New: detect levels from cam (pass1/pass2 on 1D profiles). |
| multiTools | series/options/BuildSeriesOptions.java | Added `sourceCamDirect`. |
| multiTools | series/DetectLevels.java               | Branch: sourceCamDirect → LevelDetectorFromCam; else kymograph + LevelDetector. |
| multiCAFE  | dlg/levels/DetectLevelsDlg.java        | Checkbox “Source: Cam (direct)” and initBuildParameters logic for direct mode. |
