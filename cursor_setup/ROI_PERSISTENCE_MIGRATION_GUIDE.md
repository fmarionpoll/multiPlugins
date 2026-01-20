# ROI Persistence Migration Guide
## CSV Format v2.0 → v2.1

**Date:** 2026-01-19  
**Version:** 2.3.3  
**Status:** IMPLEMENTED

---

## Overview

This guide documents the CSV format changes from v2.0 to v2.1, which introduces unified ROI persistence with type indicators across Spot, Capillary, and Cage entities.

---

## What Changed

### Version Identifier

All CSV files now include a version header:

```csv
#;version;2.1
#;SPOTS;multiSPOTS data
...
```

**Location:**
- In filename: `SpotsDescription.csv` (PascalCase, version-agnostic)
- In header: First line of every CSV file contains `#;version;2.1`

### ROI Type Indicators

All entities now save ROI type before coordinate data:

| Entity | ROI Types Supported |
|--------|---------------------|
| Spot | ELLIPSE, POLYGON, RECTANGLE, POLYLINE |
| Capillary | LINE, POLYLINE |
| Cage | POLYGON, RECTANGLE, ELLIPSE |

---

## File Format Changes

### Spots Description (v2.0 → v2.1)

**Filename:** `v2_spots_description.csv` → `SpotsDescription.csv`

**Old Format (v2.0):**
```csv
#;SPOTS;multiSPOTS data
name;index;cageID;cagePos;cageColumn;cageRow;volume;npixels;radius;stim;conc
spot_001_000;0;1;0;0;0;0.5;100;10;water;0
```

**New Format (v2.1):**
```csv
#;version;2.1
#;SPOTS;multiSPOTS data
name;index;cageID;cagePos;cageColumn;cageRow;volume;npixels;radius;stim;conc;roiType;roiData
spot_001_000;0;1;0;0;0;0.5;100;10;water;0;ellipse;150.0;200.0;10.0;10.0
```

**ROI Data Formats:**
- `ELLIPSE`: centerX;centerY;radiusX;radiusY
- If ROI was user-modified, full coordinates are saved

### Capillaries Description (v2.0 → v2.1)

**Filename:** `v2_capillaries_description.csv` → `CapillariesDescription.csv`

**Old Format (v2.0):**
```csv
#;CAPILLARIES;describe each capillary
cap_prefix;kymoIndex;kymographName;...;ROIname;npoints
0L;0;line01;...;line0L;5
50;100;55;105;60;110;65;115;70;120
```

**New Format (v2.1):**
```csv
#;version;2.1
#;CAPILLARIES;describe each capillary
cap_prefix;kymoIndex;kymographName;...;ROIname;roiType;npoints
0L;0;line01;...;line0L;polyline;5
50;100;55;105;60;110;65;115;70;120
```

**ROI Data Formats:**
- `LINE`: npoints=2, followed by x1;y1;x2;y2
- `POLYLINE`: npoints=N, followed by x1;y1;x2;y2;...;xN;yN

### Cages Description (v2.0 → v2.1)

**Filename:** `v2_cages_description.csv` → `CagesDescription.csv`

**Old Format (v2.0):**
```csv
#;CAGE;Cage properties
cageID;nFlies;age;Comment;strain;sect;ROIname;npoints
0;10;3;control;w1118;F;cage000;4
100;100;200;100;200;200;100;200
```

**New Format (v2.1):**
```csv
#;version;2.1
#;CAGE;Cage properties
cageID;nFlies;age;Comment;strain;sect;ROIname;roiType;npoints
0;10;3;control;w1118;F;cage000;polygon;4
100;100;200;100;200;200;100;200
```

**ROI Data Formats:**
- `POLYGON`: npoints=N, followed by x1;y1;x2;y2;...;xN;yN
- `RECTANGLE`: npoints=4, followed by x;y;width;height

### Measures Files (All Entities)

All measures files now include version headers:

```csv
#;version;2.1
#;AREA_SUM;v0
name;index;npts;yi
...
```

**Filenames:**
- `SpotsMeasures.csv` (PascalCase, version-agnostic)
- `CapillariesMeasures.csv` (PascalCase, version-agnostic)
- `CagesMeasures.csv` (PascalCase, version-agnostic)

---

## ROI Type Storage Details

### ELLIPSE
**Parameters:** centerX, centerY, radiusX, radiusY

**When Used:**
- Spots with circular or elliptical shape
- Unmodified spots (efficient storage)

**CSV Example:**
```
roiType;roiData
ellipse;150.0;200.0;10.0;10.0
```

**Reconstruction:**
- Create Ellipse2D from center and radii
- Wrap in ROI2DEllipse

### LINE
**Parameters:** x1, y1, x2, y2

**When Used:**
- Capillaries with straight-line ROI
- Any entity with 2-point linear ROI

**CSV Example:**
```
roiType;npoints;roiData
line;2;50.0;100.0;55.0;105.0
```

**Reconstruction:**
- Create Line2D from endpoints
- Wrap in ROI2DLine

### POLYLINE
**Parameters:** npoints, x1, y1, x2, y2, ..., xN, yN

**When Used:**
- Capillaries with curved path
- Multi-segment line ROIs

**CSV Example:**
```
roiType;npoints;roiData
polyline;5;50;100;55;105;60;110;65;115;70;120
```

**Reconstruction:**
- Extract n points
- Create Polyline2D from point array
- Wrap in ROI2DPolyLine

### POLYGON
**Parameters:** npoints, x1, y1, x2, y2, ..., xN, yN

**When Used:**
- Cages with irregular shape
- Any closed polygonal ROI

**CSV Example:**
```
roiType;npoints;roiData
polygon;4;100;100;200;100;200;200;100;200
```

**Reconstruction:**
- Extract n points
- Create Polygon2D from point array
- Wrap in ROI2DPolygon

### RECTANGLE
**Parameters:** x, y, width, height

**When Used:**
- Cages with rectangular shape
- Grid-aligned cage arrays

**CSV Example:**
```
roiType;npoints;roiData
rectangle;4;100;100;50;75
```

**Reconstruction:**
- Create Rectangle2D from bounds
- Wrap in ROI2DRectangle

---

## Backward Compatibility

### Reading Old Files (v2.0)

The v2.1 loaders automatically handle v2.0 files:

**Spots:**
- Missing `roiType` column → calls `spot.regenerateROIFromCoordinates()`
- Uses stored (centerX, centerY, radius) to create ellipse
- User edits are lost (limitation of v2.0 format)

**Capillaries:**
- Missing `roiType` column → infers type from npoints
  - npoints=2 → assumes LINE
  - npoints>2 → assumes POLYLINE
- Coordinates are still loaded correctly

**Cages:**
- Missing `roiType` column → assumes POLYGON
- Coordinates are loaded as polygon vertices
- Works for most cage shapes

### Fallback Chain

All loaders follow this priority:
1. Try current format file (e.g., `SpotsDescription.csv`) with version header validation
2. If no version header found, fall back to v2.0 format file (e.g., `v2_spots_description.csv`)
3. Fall back to legacy CSV format (e.g., `SpotsArray.csv`)
4. Fall back to XML format (deprecated, if available)

**Example in code:**
```java
// SpotsPersistence.Persistence.loadDescription()
Path csvPath = Paths.get(resultsDirectory, ID_V2_SPOTSARRAY_CSV);
if (!Files.exists(csvPath)) {
    // Delegate to Legacy class for all fallback logic
    return SpotsPersistenceLegacy.loadDescriptionWithFallback(spotsArray, resultsDirectory);
}
```

### Writing Files

All saves use current format exclusively:
- Always creates PascalCase files (e.g., `SpotsDescription.csv`)
- Includes version header (`#;version;2.1`) as first line
- Includes roiType column
- Old format files are not overwritten (different filenames)

---

## Storage Efficiency

### Spots: Smart ROI Storage

Spots use efficient storage based on ROI modification status:

**Unmodified Spot (geometric params):**
```
roiType;roiData
ellipse;150.0;200.0;10.0;10.0
```
- Storage: 4 numbers
- Preserves: center and radii

**Modified Spot (full coordinates):**
```
roiType;npoints;roiData
polyline;20;150;200;151;201;...
```
- Storage: 1 + (2 * npoints) numbers
- Preserves: exact shape

**Detection Logic:**
```java
// In SpotPersistence.csvExportSpotDescription():
boolean modified = ROIPersistenceUtils.isModifiedEllipseROI(
    spot.getRoi(), 
    spot.getProperties().getSpotXCoord(),
    spot.getProperties().getSpotYCoord(),
    spot.getProperties().getSpotRadius()
);

if (!modified) {
    // Save as ELLIPSE with 4 parameters
} else {
    // Save full coordinates
}
```

---

## Common Utilities

### ROIPersistenceUtils Class

**Location:** `multiTools/src/main/java/plugins/fmp/multitools/tools/ROI2D/ROIPersistenceUtils.java`

**Key Methods:**

```java
// Export
public static String exportROITypeAndData(ROI2D roi, String separator)
public static String extractEllipseParams(ROI2D roi, String separator)
public static String extractLineParams(ROI2D roi, String separator)
public static String extractPolylineParams(ROI2D roi, String separator)
public static String extractPolygonParams(ROI2D roi, String separator)
public static String extractRectangleParams(ROI2D roi, String separator)

// Import
public static ROI2D importROIFromCSV(String roiTypeStr, String roiDataStr, String roiName)
public static ROI2D reconstructEllipse(String[] params, String name)
public static ROI2D reconstructLine(String[] params, String name)
public static ROI2D reconstructPolyline(String[] params, String name)
public static ROI2D reconstructPolygon(String[] params, String name)
public static ROI2D reconstructRectangle(String[] params, String name)

// Utilities
public static ROIType detectROIType(ROI2D roi)
public static boolean isModifiedEllipseROI(ROI2D currentROI, int x, int y, int radius)
```

### ROIType Enum

**Location:** `multiTools/src/main/java/plugins/fmp/multitools/tools/ROI2D/ROIType.java`

```java
public enum ROIType {
    ELLIPSE("ellipse"),
    LINE("line"),
    POLYLINE("polyline"),
    POLYGON("polygon"),
    RECTANGLE("rectangle"),
    UNKNOWN("unknown");
    
    public static ROIType fromString(String csvString)
    public static ROIType fromROI2D(ROI2D roi)
    public String toCsvString()
    public boolean isPointBased()
    public boolean isParametric()
}
```

---

## Migration Checklist

### For Existing Experiments

- [ ] **No action required** - Old files are still readable
- [ ] New saves create v2.1 files automatically
- [ ] Old v2.0 files remain unchanged (different filename)
- [ ] Can manually delete old v2.0 files after verifying v2.1 works

### For Developers

- [ ] Update code to use new standardized method names:
  - `loadDescriptions()` instead of `load_SpotsArray()`, `load_CapillariesDescription()`, etc.
  - `saveDescriptions()` instead of `save_SpotsArray()`, `saveCapillariesDescription()`, etc.
  - `loadMeasures()` instead of `loadSpotsMeasures()`, `load_CapillariesMeasures()`, etc.
  - `saveMeasures()` instead of `saveSpotsMeasures()`, `save_CapillariesMeasures()`, etc.

- [ ] Update ROI transfer code to use mapper classes:
  - `SpotsSequenceMapper.transferROIsToSequence()` instead of old methods
  - `CapillariesSequenceMapper.transferROIsToSequence()` instead of old methods
  - `CagesSequenceMapper.transferROIsToSequence()` instead of old methods

- [ ] Watch for deprecation warnings and update accordingly

### Testing Recommendations

1. **Backward Compatibility:**
   - Load existing experiments with v2.0 files
   - Verify all ROIs are loaded correctly
   - Verify measures are loaded correctly

2. **Round-trip Persistence:**
   - Create new experiment
   - Add/edit ROIs for spots, capillaries, cages
   - Save to v2.1 format
   - Close and reload
   - Verify ROI shapes match exactly

3. **ROI Modification Preservation:**
   - Load spots from v2.0 (regenerated ROIs)
   - Modify spot ROI shapes in GUI
   - Save to v2.1
   - Reload and verify modifications are preserved

4. **Mixed Versions:**
   - Have both v2.0 and v2.1 files in same directory
   - Loader should prefer v2.1
   - Both should be readable

---

## Troubleshooting

### Problem: Spot ROIs Not Preserved

**Symptom:** After save/load, spot ROI modifications are lost.

**Diagnosis:**
- Check if file is v2.0 format (no roiType column)
- v2.0 files regenerate ROIs from center + radius

**Solution:**
- Resave experiment (will create v2.1 file)
- Edit ROIs again
- v2.1 file will preserve modifications

### Problem: Capillary ROIs Wrong Type

**Symptom:** Capillary loads as wrong ROI type.

**Diagnosis:**
- Check roiType column in CSV
- Verify npoints matches coordinate count

**Solution:**
- Edit CSV file manually to fix roiType
- Or delete capillary and recreate in GUI

### Problem: Version Not Recognized

**Symptom:** Error loading v2.1 file, or loads as wrong version.

**Diagnosis:**
- Check version header: `#;version;2.1` (must be first line)
- Check filename: `*Description.csv` or `*Measures.csv` (PascalCase)

**Solution:**
- Verify version header is first line
- Ensure no extra characters in version string
- Check file encoding (should be UTF-8 or ASCII)

### Problem: Backward Compatibility Broken

**Symptom:** Old v2.0 files won't load.

**Diagnosis:**
- Check fallback logic in persistence classes
- Verify legacy file still exists

**Solution:**
- Ensure both v2.1 and v2.0 loaders are in code
- Check console for error messages
- Try loading from legacy CSV or XML

---

## Code Examples

### Loading Experiment (v2.1)

```java
// Load spots
spots.loadDescriptions(resultsDir);          // SpotsDescription.csv (with version header validation)
spots.loadMeasures(binDir);                  // SpotsMeasures.csv

// Load capillaries
capillaries.loadDescriptions(resultsDir);    // CapillariesDescription.csv
capillaries.loadMeasures(binDir);            // CapillariesMeasures.csv

// Load cages
cages.loadDescriptions(resultsDir);          // CagesDescription.csv
cages.loadMeasures(binDir);                  // CagesMeasures.csv
```

### Saving Experiment (v2.1)

```java
// Save spots
spots.saveDescriptions(resultsDir);
spots.saveMeasures(binDir);

// Save capillaries
capillaries.saveDescriptions(resultsDir);
capillaries.saveMeasures(binDir);

// Save cages
cages.saveDescriptions(resultsDir);
cages.saveMeasures(binDir);
```

### Using Common ROI Utilities

```java
// Export ROI to CSV
ROI2D spotROI = spot.getRoi();
String roiData = ROIPersistenceUtils.exportROITypeAndData(spotROI, ";");
// Result: "ellipse;150.0;200.0;10.0;10.0"

// Import ROI from CSV
ROI2D reconstructed = ROIPersistenceUtils.importROIFromCSV(
    "polyline", 
    "5;50;100;55;105;60;110;65;115;70;120", 
    "spot_001_000"
);
spot.setRoi((ROI2DShape) reconstructed);

// Detect ROI type
ROIType type = ROIPersistenceUtils.detectROIType(cage.getRoi());
// Returns: ROIType.POLYGON, ROIType.RECTANGLE, etc.
```

---

## Benefits of v2.1 Format

### 1. User Edits Preserved
- ROI modifications in GUI are saved
- Exact shapes are restored on load
- No loss of precision

### 2. Format Clarity
- Explicit type indicators (no guessing)
- Self-documenting CSV files
- Easier manual inspection/editing

### 3. Extensibility
- Easy to add new ROI types
- Common infrastructure for all entities
- Consistent parsing logic

### 4. Efficiency
- Simple shapes use compact parameters
- Complex shapes use full coordinates
- Best of both worlds

### 5. Consistency
- Same pattern across Spots, Capillaries, Cages
- Common utility methods
- Unified API

---

## Version History

### v2.1 (2026-01-19)
- Added roiType column to all entity descriptions
- Added version header to all CSV files
- Separated measures into bin directory
- Standardized method names across all entities
- Created common ROI persistence utilities

### v2.0 (Previous)
- CSV-based persistence
- Separate description and measures
- Capillaries and Cages had ROI coordinates
- Spots regenerated ROIs from parameters

### v1.x (Legacy)
- XML-based persistence
- Combined description and measures
- Less flexible format

---

## FAQ

**Q: Do I need to convert my old files?**  
A: No, v2.1 loaders automatically read v2.0 files. Files are upgraded on next save.

**Q: Can I mix v2.0 and v2.1 files?**  
A: Yes, loaders prefer v2.1 but fall back to v2.0. However, save operations create v2.1 only.

**Q: What happens to my spot ROI edits in v2.0 files?**  
A: They're lost because v2.0 doesn't save full ROI data for spots. Resave as v2.1 to preserve future edits.

**Q: Will old plugins still work?**  
A: Yes, all old method names are deprecated but still functional. Update to new names when convenient.

**Q: When will deprecated methods be removed?**  
A: In version 3.0 (future major release). You have time to migrate.

**Q: Can I force regeneration instead of loading saved ROIs?**  
A: Yes, delete the roiType and roiData columns from CSV, or call `spot.regenerateROIFromCoordinates()` manually.

---

## Summary

v2.1 format provides:
- **Better preservation** - User ROI edits are saved
- **More consistency** - Same pattern for all entities
- **Easier maintenance** - Common utility methods
- **Full compatibility** - Old files still work
- **Clear documentation** - Explicit type indicators

The migration is transparent for users and provides a solid foundation for future enhancements.

---

**Last Updated:** 2026-01-19  
**Implemented in:** multiTools v2.3.3  
**Breaking Changes:** None (fully backward compatible)
