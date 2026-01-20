# Persistence Unification Analysis
## Spot/Spots and Capillary/Capillaries CSV Persistence

**Date:** 2026-01-19  
**Author:** Analysis by AI Assistant  
**Purpose:** Analyze current persistence implementation and propose unified approach

---

## Executive Summary

This document analyzes the current persistence classes for Spot/Spots and Capillary/Capillaries, addresses concerns about ROI persistence, and proposes a unified approach with common method names and structure.

### Key Findings

1. ✅ **Both use CSV-based persistence** with v2 format
2. ✅ **Both separate descriptions and measures** into different files/directories
3. ⚠️ **Spot ROIs are NOT saved to CSV** - they are regenerated from coordinates
4. ⚠️ **Capillary ROIs ARE saved to CSV** - includes full ROI coordinate data
5. ⚠️ **Different patterns for ROI-to-sequence transfer** - Spots has mapper class, Capillaries doesn't

---

## Current State Analysis

### File Structure

#### Spots
```
results/
  └── SpotsDescription.csv      # Descriptions (name, cage, position, coordinates, ROI type, etc.)
results/bin_60/                  # or bin_120, etc.
  └── SpotsMeasures.csv         # Measures (area_sum, area_clean, flypresent)
```

#### Capillaries
```
results/
  └── CapillariesDescription.csv    # Descriptions + ROI coordinates + ROI type
results/bin_60/                      # or bin_120, etc.
  └── CapillariesMeasures.csv       # Measures (toplevel, bottom, derivative, gulps)
```

### Persistence Class Structure

Both follow similar patterns:

```
SpotsPersistence                      CapillariesPersistence
├── Persistence (v2 format)          ├── Persistence (v2 format)
│   ├── loadDescription()            │   ├── loadDescription()
│   ├── saveDescription()            │   ├── saveDescription()
│   ├── loadMeasures()               │   ├── loadMeasures()
│   └── saveMeasures()               │   └── saveMeasures()
└── SpotsPersistenceLegacy           └── CapillariesPersistenceLegacy
    └── (fallback handlers)              └── (fallback handlers)

SpotPersistence                       CapillaryPersistence
├── xmlLoadSpot()                    ├── xmlLoadCapillary()
├── xmlSaveSpot()                    ├── xmlSaveCapillary()
├── csvExportSpotDescription()       ├── csvExportCapillaryDescription()
└── csvImportSpotDescription()       └── csvImportCapillaryDescription()
```

### ROI Handling: Critical Difference

#### Spots ROI Persistence

**Current Behavior:**
- ROIs are **NOT** saved to CSV files
- Only coordinates (x, y, radius) are saved
- ROIs are regenerated on demand using `regenerateROIFromCoordinates()`
- ROI transfer uses `SpotsSequenceMapper` class

**Code Example:**
```java
// Spot.java line 214-249
public boolean regenerateROIFromCoordinates() {
    int x = properties.getSpotXCoord();
    int y = properties.getSpotYCoord();
    int radius = properties.getSpotRadius();
    
    // Create ellipse ROI from center coordinates and radius
    Ellipse2D ellipse = new Ellipse2D.Double(x - radius, y - radius, 2 * radius, 2 * radius);
    ROI2DEllipse roiEllipse = new ROI2DEllipse(ellipse);
    // ... set name ...
    this.spotROI2D = roiEllipse;
    return true;
}
```

**CSV Format (SpotsDescription.csv):**
```
#;version;2.1
#;SPOTS;multiSPOTS data
name;index;cageID;cagePos;cageColumn;cageRow;volume;npixels;radius;stim;conc;roiType;roiData
spot_001_000;0;1;0;0;0;0.5;100;10;water;0;ellipse;150.0;200.0;10.0;10.0
```

#### Capillaries ROI Persistence

**Current Behavior:**
- ROIs **ARE** saved to CSV files
- Full ROI coordinates saved in CAPILLARIES section
- No regeneration needed - ROIs loaded directly from CSV
- No dedicated mapper class for sequence transfer

**CSV Format (CapillariesDescription.csv):**
```
#;version;2.1
#;CAPILLARIES;describe each capillary
cap_prefix;kymoIndex;kymographName;kymoFile;cap_cage;...;ROIname;roiType;npoints
0L;0;line01;kymo.tif;0;1;0.5;100;water;0;L;line0L;polyline;5
50;100;55;105;60;110;65;115;70;120  # ROI x,y coordinates follow
```

### ROI-to-Sequence Transfer

#### Spots: Using Mapper Pattern

```java
// SpotsSequenceMapper.java (dedicated class)
public static void transferROIsFromSpotsToSequence(Spots spots, SequenceCamData seqCamData) {
    seqCamData.removeROIsContainingString("spot");
    spots.transferROIsfromSpotsToSequence(seqCamData);
}

public static void transferROIsFromSequenceToSpots(Spots spots, SequenceCamData seqCamData) {
    spots.transferROIsfromSequenceToSpots(seqCamData);
}

// Spots.java (implementation)
public void transferROIsfromSpotsToSequence(SequenceCamData seqCamData) {
    seqCamData.processROIs(ROIOperation.removeROIs("spot"));
    // ... collect and add ROIs to sequence ...
}

public void transferROIsfromSequenceToSpots(SequenceCamData seqCamData) {
    List<ROI2D> roiList = seqCamData.findROIsMatchingNamePattern("spot");
    transferROIsToSpots(roiList);
}
```

#### Capillaries: Direct Methods in Capillaries Class

```java
// Capillaries.java (no dedicated mapper)
public void updateCapillariesFromSequence(SequenceCamData seqCamData) {
    List<ROI2D> listROISCap = seqCamData.findROIsMatchingNamePattern("line");
    // ... update capillaries with ROIs ...
}

public void transferCapillaryRoiToSequence(Sequence seq) {
    // Remove capillary ROIs, then add them back
    // ... direct sequence manipulation ...
}
```

---

## Issues and Concerns

### 1. Spot ROI Persistence ⚠️

**Issue:** Spot ROIs are not saved/restored from CSV

**Impact:**
- User-modified ROIs (shapes, positions) are lost on save/load
- Only elliptical ROIs with fixed radius can be regenerated
- Complex or custom ROI shapes cannot be preserved

**Evidence:**
- `Spot.java` line 209 comment: "ROIs are not persisted (CSV-only persistence) and need to be recreated"
- `Spots.java` line 171 comment: "NOTE: XML operations are deprecated. Spots now use CSV-only persistence."
- Only coordinates (x, y, radius) are saved, not full ROI data

### 2. Inconsistent ROI Handling

**Issue:** Spots and Capillaries handle ROIs differently

| Aspect | Spots | Capillaries |
|--------|-------|-------------|
| ROI saved to CSV? | ❌ No | ✅ Yes |
| ROI regeneration? | ✅ Yes (from coordinates) | ❌ No (loaded directly) |
| Dedicated mapper? | ✅ SpotsSequenceMapper | ❌ No |
| ROI complexity | Simple (ellipse only) | Complex (polylines) |

### 3. Inconsistent Method Names

**Issue:** Similar operations have different names

| Operation | Spots | Capillaries |
|-----------|-------|-------------|
| Load descriptions | `load_SpotsArray()` | `load_CapillariesDescription()` |
| Save descriptions | `save_SpotsArray()` | `saveCapillariesDescription()` |
| Load measures | `loadSpotsMeasures()` | `load_CapillariesMeasures()` |
| Save measures | `saveSpotsMeasures()` | `save_CapillariesMeasures()` |
| ROI to sequence | `transferROIsfromSpotsToSequence()` | `transferCapillaryRoiToSequence()` |
| ROI from sequence | `transferROIsfromSequenceToSpots()` | `updateCapillariesFromSequence()` |

---

## Recommendations

### 1. Unify Method Naming Convention

Adopt consistent naming across both classes:

```java
// Common interface or pattern:
loadDescriptions(String resultsDirectory)
saveDescriptions(String resultsDirectory)
loadMeasures(String binDirectory)
saveMeasures(String binDirectory)
transferROIsToSequence(SequenceCamData seqCamData)
transferROIsFromSequence(SequenceCamData seqCamData)
```

### 2. Create Base Persistence Interface/Abstract Class

```java
public interface EntityPersistence<T> {
    // Description persistence (results directory)
    boolean loadDescriptions(T entity, String resultsDirectory);
    boolean saveDescriptions(T entity, String resultsDirectory);
    
    // Measures persistence (bin directory)
    boolean loadMeasures(T entity, String binDirectory);
    boolean saveMeasures(T entity, String binDirectory);
    
    // File naming
    String getDescriptionFilename();
    String getMeasuresFilename();
}

// Implementations:
class SpotsPersistence implements EntityPersistence<Spots> { ... }
class CapillariesPersistence implements EntityPersistence<Capillaries> { ... }
```

### 3. Address Spot ROI Persistence Issue

**Option A: Add ROI Coordinates to CSV (Recommended)**

Save spot ROI coordinates similarly to capillaries:

```
name;index;cageID;...;radius;stim;conc;npoints
spot_001_000;0;1;...;10;water;0;4
50;50;50;60;60;60;60;50  # ROI x,y coordinates
```

**Pros:**
- Preserves user-modified ROIs
- Consistent with capillaries approach
- Supports complex ROI shapes
- No breaking changes (fallback to regeneration if no coordinates)

**Cons:**
- Larger file sizes
- More complex parsing

**Option B: Keep Current Approach with Documentation**

Document that spot ROIs are regenerated and only simple ellipses are supported.

**Pros:**
- No changes needed
- Simpler files
- Good for programmatic workflows

**Cons:**
- User edits are lost
- Limited to elliptical ROIs

**Recommendation:** Option A - Add ROI coordinates to CSV with fallback to regeneration for backward compatibility.

### 4. Create CapillariesSequenceMapper

Create a dedicated mapper class like spots have:

```java
public final class CapillariesSequenceMapper {
    private CapillariesSequenceMapper() {}
    
    public static void transferROIsFromCapillariesToSequence(
            Capillaries capillaries, SequenceCamData seqCamData) {
        if (capillaries == null || seqCamData == null) return;
        seqCamData.removeROIsContainingString("line");
        capillaries.transferCapillaryRoiToSequence(seqCamData.getSequence());
    }
    
    public static void transferROIsFromSequenceToCapillaries(
            Capillaries capillaries, SequenceCamData seqCamData) {
        if (capillaries == null || seqCamData == null) return;
        capillaries.updateCapillariesFromSequence(seqCamData);
    }
}
```

**Benefits:**
- Consistent pattern with spots
- Cleaner separation of concerns
- Easier to understand and maintain
- Similar to existing CagesSequenceMapper

### 5. Unified Directory Structure

Both already follow the same pattern - keep it:

```
results/
  ├── SpotsDescription.csv
  ├── CapillariesDescription.csv
  └── bin_60/  (or bin_120, etc.)
      ├── SpotsMeasures.csv
      └── CapillariesMeasures.csv
```

---

## Proposed Common Method Names

### Collection Level (Spots, Capillaries)

```java
// Description persistence (results directory)
public boolean loadDescriptions(String resultsDirectory)
public boolean saveDescriptions(String resultsDirectory)

// Measures persistence (bin directory)  
public boolean loadMeasures(String binDirectory)
public boolean saveMeasures(String binDirectory)

// ROI-Sequence transfer
public void transferROIsToSequence(SequenceCamData seqCamData)
public void transferROIsFromSequence(SequenceCamData seqCamData)
```

### Entity Level (Spot, Capillary)

```java
// CSV export/import
public String exportDescription(String separator)
public void importDescription(String[] data)

public String exportMeasuresHeader(EnumMeasureType type, String separator)
public String exportMeasures(EnumMeasureType type, String separator)
public void importMeasures(EnumMeasureType type, String[] data, boolean includeX, boolean includeY)

// XML (legacy, keep for backward compatibility)
public boolean loadFromXml(Node node)
public boolean saveToXml(Node node)
```

### Persistence Class Level

```java
// Static utility methods
public static String csvExportSubSectionHeader(String sep)
public static String csvExportDescription(Entity entity, String sep)
public static String csvExportMeasureSectionHeader(EnumMeasureType type, String sep)
public static String csvExportMeasuresOneType(Entity entity, EnumMeasureType type, String sep)
public static void csvImportDescription(Entity entity, String[] data)
public static void csvImportData(Entity entity, EnumMeasureType type, String[] data, boolean x, boolean y)
```

---

## Migration Plan

### Phase 1: Add ROI Coordinates to Spot CSV (Optional)

1. Extend `SpotPersistence.csvExportSpotDescription()` to optionally include ROI coordinates
2. Update `SpotPersistence.csvImportSpotDescription()` to handle optional ROI coordinates
3. Maintain backward compatibility: regenerate ROIs if coordinates not present
4. Update file format version to indicate ROI support

### Phase 2: Create CapillariesSequenceMapper

1. Create new `CapillariesSequenceMapper` class in `experiment.capillaries` package
2. Move ROI transfer logic from `Capillaries` to mapper
3. Update all calling code to use mapper
4. Keep delegate methods in `Capillaries` for compatibility (marked as deprecated)

### Phase 3: Unify Method Names

1. Add new standardized method names alongside existing ones
2. Mark old methods as `@Deprecated`
3. Update internal code to use new names
4. Document migration in comments
5. After 1-2 release cycles, remove deprecated methods

### Phase 4: Extract Common Interface/Base Class

1. Define `EntityPersistence<T>` interface
2. Refactor existing persistence classes to implement interface
3. Extract common logic to abstract base class or utility methods
4. Update documentation

---

## Implementation Priority

### High Priority (Do First)
1. ✅ Create CapillariesSequenceMapper - improves code organization
2. ✅ Standardize method names - reduces confusion

### Medium Priority
3. ⚠️ Add ROI coordinate persistence for Spots - addresses user concern
4. ✅ Extract common interface - better architecture

### Low Priority
5. Comprehensive documentation of persistence format
6. Migration of legacy XML code to CSV

---

## Code Examples

### Example 1: Unified Method Names in Spots

```java
// Spots.java - proposed changes
public class Spots {
    private SpotsPersistence persistence = new SpotsPersistence();
    
    // NEW: Standardized method names
    public boolean loadDescriptions(String resultsDirectory) {
        return persistence.loadDescription(this, resultsDirectory);
    }
    
    public boolean saveDescriptions(String resultsDirectory) {
        return persistence.saveDescription(this, resultsDirectory);
    }
    
    public boolean loadMeasures(String binDirectory) {
        return persistence.loadMeasures(this, binDirectory);
    }
    
    public boolean saveMeasures(String binDirectory) {
        return persistence.saveMeasures(this, binDirectory);
    }
    
    // OLD: Keep for compatibility, mark deprecated
    @Deprecated
    public boolean loadSpotsAll(String directory) {
        return loadDescriptions(directory);
    }
    
    @Deprecated
    public boolean saveSpotsAll(String directory) {
        return saveDescriptions(directory);
    }
}
```

### Example 2: CapillariesSequenceMapper

```java
// NEW FILE: CapillariesSequenceMapper.java
package plugins.fmp.multitools.experiment.capillaries;

import plugins.fmp.multitools.experiment.sequence.SequenceCamData;

public final class CapillariesSequenceMapper {
    private CapillariesSequenceMapper() {
        // Utility class - prevent instantiation
    }
    
    /**
     * Transfer capillary ROIs from capillaries to sequence.
     * Removes existing "line" ROIs first.
     */
    public static void transferROIsToSequence(
            Capillaries capillaries, SequenceCamData seqCamData) {
        if (capillaries == null || seqCamData == null || seqCamData.getSequence() == null) {
            return;
        }
        seqCamData.removeROIsContainingString("line");
        capillaries.transferCapillaryRoiToSequence(seqCamData.getSequence());
    }
    
    /**
     * Transfer ROIs from sequence back to capillaries.
     * Updates existing capillaries based on ROI matches.
     */
    public static void transferROIsFromSequence(
            Capillaries capillaries, SequenceCamData seqCamData) {
        if (capillaries == null || seqCamData == null || seqCamData.getSequence() == null) {
            return;
        }
        capillaries.updateCapillariesFromSequence(seqCamData);
    }
}
```

### Example 3: Spot ROI Persistence with Coordinates

```java
// SpotPersistence.java - enhanced CSV export/import

public static String csvExportSpotDescription(Spot spot, String sep, boolean includeROI) {
    SpotProperties props = spot.getProperties();
    StringBuilder sbf = new StringBuilder();
    
    // Basic properties
    List<String> row = Arrays.asList(
        props.getName() != null ? props.getName() : "",
        String.valueOf(props.getSpotArrayIndex()),
        String.valueOf(props.getCageID()),
        // ... other properties ...
        String.valueOf(props.getSpotRadius()),
        props.getStimulus() != null ? props.getStimulus().replace(",", ".") : "",
        props.getConcentration() != null ? props.getConcentration().replace(",", ".") : ""
    );
    sbf.append(String.join(sep, row));
    
    // Optional: include ROI coordinates
    if (includeROI && spot.getRoi() != null) {
        ROI2D roi = spot.getRoi();
        Rectangle bounds = roi.getBounds();
        // Save ROI type and coordinates
        sbf.append(sep).append("roi_ellipse");
        sbf.append(sep).append(bounds.npoints);
        // Append x,y coordinates...
    } else {
        sbf.append(sep).append("0"); // No ROI coordinates
    }
    
    sbf.append("\n");
    return sbf.toString();
}

public static void csvImportSpotDescription(Spot spot, String[] data) {
    // ... import basic properties ...
    
    // Check if ROI coordinates are present
    if (data.length > 11) {
        int npoints = Integer.parseInt(data[11]);
        if (npoints > 0 && data.length >= 12 + npoints * 2) {
            // Parse and reconstruct ROI from coordinates
            // ...
            return;
        }
    }
    
    // Fallback: regenerate ROI from coordinates
    spot.regenerateROIFromCoordinates();
}
```

---

## Testing Strategy

### Unit Tests

1. Test CSV export/import with ROI coordinates
2. Test backward compatibility (old format without ROI coordinates)
3. Test mapper classes for both spots and capillaries
4. Test standardized method names

### Integration Tests

1. Round-trip persistence (save → load → verify)
2. ROI transfer sequence → entity → sequence
3. Bin directory measures persistence
4. Legacy format fallback

### Regression Tests

1. Ensure existing experiments still load correctly
2. Verify backward compatibility with old CSV files
3. Test that deprecated methods still work

---

## Questions for Discussion

1. **ROI Persistence for Spots**: Should we save full ROI coordinates or keep regenerating from center+radius?
2. **Breaking Changes**: Are we okay with deprecating old method names and requiring code updates?
3. **Interface vs. Base Class**: Should common persistence logic be in an interface or abstract base class?
4. **Migration Timeline**: How many release cycles should deprecated methods remain before removal?
5. **File Format Version**: Should we increment version number in CSV files when adding ROI coordinates?

---

## Conclusion

The current persistence implementation for Spots and Capillaries is functional but could benefit from:

1. **Consistency** - Standardize method names and patterns
2. **Completeness** - Add ROI coordinate persistence for spots
3. **Organization** - Use mapper classes consistently
4. **Maintainability** - Extract common interfaces/base classes

The recommendations above provide a roadmap for gradual improvement while maintaining backward compatibility.

---

## References

- `Spot.java` - Lines 214-249 (ROI regeneration)
- `Spots.java` - Lines 146-162, 536-580 (persistence and ROI transfer)
- `SpotsPersistence.java` - Full file (CSV persistence)
- `SpotPersistence.java` - Full file (individual spot persistence)
- `SpotsSequenceMapper.java` - Full file (ROI-sequence mapping)
- `Capillary.java` - Lines 807-866 (ROI transfer methods)
- `Capillaries.java` - Lines 191-252 (ROI update from sequence)
- `CapillariesPersistence.java` - Full file (CSV persistence)
- `CapillaryPersistence.java` - Full file (individual capillary persistence)
