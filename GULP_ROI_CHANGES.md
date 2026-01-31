# Gulp ROI Export Changes - Individual Vertical Line ROIs

## Summary

Modified gulp export in multiCAFE to create **individual vertical line ROI2DPolyLine ROIs** (one per gulp) instead of a single ROI2DArea containing all gulp points. Each gulp is now visualized as a **vertical red bar** from bottom level to top level at its x position.

## Changes Made

### 1. Modified Export Logic

**File**: `multiTools/src/main/java/plugins/fmp/multitools/experiment/capillary/Capillary.java`

**Method**: `getROIsFromCapillaryGulps()` (lines 895-943)

**Changes**:
- **Before**: Created one `ROI2DArea` with all gulp points as dots at top level
- **After**: Creates individual `ROI2DPolyLine` ROIs (one per gulp), each with 2 points:
  - Point 1: `(x, bottomLevel[x])` - bottom of vertical bar
  - Point 2: `(x, topLevel[x])` - top of vertical bar
- **Naming**: Each gulp ROI is named `"{prefix}_gulp{x:07d}"` (e.g., `"0L_gulp0000123"`)
- **Color**: Red (consistent with previous behavior)
- **Frame assignment**: All gulp ROIs assigned to same frame `t` as the capillary

### 2. Updated Import Logic

**File**: `multiTools/src/main/java/plugins/fmp/multitools/experiment/capillary/CapillaryGulps.java`

**Method**: `transferROIsToMeasures()` (line 398)
- Uncommented call to `buildGulpsFromROIs(rois)` to enable proper import

**Method**: `buildGulpsFromROIs()` (lines 354-418)
- Enhanced to handle individual vertical line ROI2DPolyLine format
- Extracts x position from each gulp ROI
- Calculates amplitude from vertical line height (distance between 2 points)
- Maintains backward compatibility with legacy ROI2DArea format

## Benefits

1. **Frame-optimized loading**: seqKymo viewer loads only ROIs for displayed frame - faster performance
2. **Better visualization**: Vertical bars show gulp height/amplitude visually across meniscus
3. **Individual editing**: Each gulp can be selected, moved, or deleted independently
4. **Consistent with ICY patterns**: Uses standard ROI2DPolyLine class

## Testing Instructions

### Prerequisites
- Build the project: `mvn clean install -pl multiTools,multiCAFE -am`
- Launch MultiCAFE plugin in ICY

### Test Steps

1. **Load an experiment** with existing gulp detections
2. **Open kymograph viewer** (Kymos tab → Display)
3. **Verify gulp display**:
   - Gulps should appear as **vertical red lines** (not dots)
   - Each line spans from bottom level to top level
   - Toggle "gulps (red)" checkbox - gulps should show/hide

4. **Test frame switching**:
   - Navigate between different kymograph frames using arrow buttons
   - Verify that only gulps for current frame are displayed
   - Performance should be good even with many gulps

5. **Test round-trip**:
   - Navigate through frames (gulps are synced to capillaries)
   - Save experiment
   - Close and reload experiment
   - Verify gulps display correctly after reload

6. **Test editing**:
   - Click on individual gulp ROI in kymograph viewer
   - Try moving or deleting individual gulps
   - Verify changes are reflected in the data

### Expected Results

✅ Gulps display as vertical red lines
✅ Individual gulps can be selected and edited
✅ Toggle checkbox works correctly
✅ Frame switching shows correct gulps for each frame
✅ Data round-trips correctly (save/load maintains gulp data)
✅ Performance is acceptable with many gulps

## Backward Compatibility

The import logic maintains backward compatibility:
- **New format**: Individual `ROI2DPolyLine` vertical lines (current implementation)
- **Legacy format**: Single `ROI2DArea` with all gulp points (still supported)

Both formats are correctly converted to the internal amplitude series representation.

## Technical Details

### ROI Structure

Each gulp ROI is a `ROI2DPolyLine` with:
- **2 points**: `[(x, yBottom), (x, yTop)]`
- **Name**: `"{prefix}_gulp{x:07d}"` where x is the frame position
- **Color**: Red
- **Frame (T)**: Set to capillary's kymograph index

### Data Flow

1. **Detection**: `detectGulps()` → amplitude series in `CapillaryGulps`
2. **Export**: `transferMeasuresToROIs()` → individual vertical line ROIs
3. **Display**: seqKymo viewer loads ROIs for current frame only
4. **Import**: `transferROIsToMeasures()` → rebuild amplitude series from ROIs

## Files Modified

1. `multiTools/src/main/java/plugins/fmp/multitools/experiment/capillary/Capillary.java`
   - Modified `getROIsFromCapillaryGulps()` method
   - Removed unused import `ROI2DArea`

2. `multiTools/src/main/java/plugins/fmp/multitools/experiment/capillary/CapillaryGulps.java`
   - Updated `transferROIsToMeasures()` method
   - Enhanced `buildGulpsFromROIs()` method

## Build Status

✅ Project compiles successfully
✅ No linter errors
✅ Changes follow existing code patterns
