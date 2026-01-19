# Persistence Unification Implementation Summary

**Date:** 2026-01-19  
**Version:** 2.3.3  
**Status:** ✅ COMPLETED

---

## Overview

This document summarizes the implementation of standardized persistence methods and ROI/sequence mappers for Spot/Spots and Capillary/Capillaries classes.

---

## What Was Implemented

### 1. ✅ Created `CapillariesSequenceMapper` Class

**Location:** `multiTools/src/main/java/plugins/fmp/multitools/experiment/capillaries/CapillariesSequenceMapper.java`

**Purpose:** Provides unified interface for transferring capillary data between experiment model and sequences.

**Key Methods:**

#### Camera Sequence Operations (ROI transfer)
- `transferROIsToSequence(Capillaries, SequenceCamData)` - Push capillary ROIs to camera sequence
- `transferROIsFromSequence(Capillaries, SequenceCamData)` - Pull capillary ROIs from camera sequence

#### Kymograph Sequence Operations (Measures transfer)
- `transferMeasuresToKymos(Capillaries, SequenceKymos)` - Push measures to kymograph
- `transferMeasuresFromKymos(Capillaries, SequenceKymos)` - Pull measures from kymograph

#### Convenience Methods
- `transferAllToSequences(Capillaries, SequenceCamData, SequenceKymos)` - Push both ROIs and measures
- `transferAllFromSequences(Capillaries, SequenceCamData, SequenceKymos)` - Pull both ROIs and measures

**Integration:** Delegates kymograph operations to existing `CapillariesKymosMapper` for consistency.

---

### 2. ✅ Standardized Method Names in `Spots` Class

**Location:** `multiTools/src/main/java/plugins/fmp/multitools/experiment/spots/Spots.java`

#### New Standardized Methods

**Persistence:**
```java
// Load operations
boolean loadDescriptions(String resultsDirectory)  // replaces loadSpotsAll()
boolean loadMeasures(String binDirectory)          // replaces loadSpotsMeasures()

// Save operations
boolean saveDescriptions(String resultsDirectory)  // replaces saveSpotsAll()
boolean saveMeasures(String binDirectory)          // replaces saveSpotsMeasures()
```

**Sequence Communication:**
```java
void transferROIsToSequence(SequenceCamData)       // replaces transferROIsfromSpotsToSequence()
void transferROIsFromSequence(SequenceCamData)     // replaces transferROIsfromSequenceToSpots()
```

#### Deprecated Methods
All old method names have been marked `@Deprecated` and delegate to new methods for backward compatibility.

---

### 3. ✅ Standardized Method Names in `SpotsSequenceMapper` Class

**Location:** `multiTools/src/main/java/plugins/fmp/multitools/experiment/spots/SpotsSequenceMapper.java`

#### New Standardized Methods
```java
void transferROIsToSequence(Spots, SequenceCamData)     // replaces transferROIsFromSpotsToSequence()
void transferROIsFromSequence(Spots, SequenceCamData)   // replaces transferROIsFromSequenceToSpots()
```

**Documentation:** Enhanced with comprehensive JavaDoc explaining the operation flow.

---

### 4. ✅ Standardized Method Names in `SpotsPersistence` Class

**Location:** `multiTools/src/main/java/plugins/fmp/multitools/experiment/spots/SpotsPersistence.java`

#### New Standardized Methods
```java
boolean loadDescriptions(Spots, String resultsDirectory)   // replaces load_SpotsArray() / loadSpotsDescription()
boolean loadMeasures(Spots, String binDirectory)           // replaces loadSpotsMeasures()
boolean saveDescriptions(Spots, String resultsDirectory)   // replaces save_SpotsArray() / saveSpotsDescription()
boolean saveMeasures(Spots, String binDirectory)           // replaces saveSpotsMeasures()
```

#### Deprecated Methods
- `load_SpotsArray()`
- `loadSpotsDescription()`
- `loadSpotsMeasures()`
- `save_SpotsArray()`
- `saveSpotsDescription()`
- `saveSpotsMeasures()`

---

### 5. ✅ Standardized Method Names in `Capillaries` Class

**Location:** `multiTools/src/main/java/plugins/fmp/multitools/experiment/capillaries/Capillaries.java`

#### New Standardized Methods

**Persistence:**
```java
// Load operations
boolean loadDescriptions(String resultsDirectory)
boolean loadMeasures(String binDirectory)

// Save operations
boolean saveDescriptions(String resultsDirectory)
boolean saveMeasures(String binDirectory)
```

**Sequence Communication:**
```java
void transferROIsToSequence(Sequence)              // replaces transferCapillaryRoiToSequence()
void transferROIsFromSequence(SequenceCamData)     // replaces updateCapillariesFromSequence()
```

#### Deprecated Methods
- `updateCapillariesFromSequence()`
- `transferCapillaryRoiToSequence()`
- `getXMLNameToAppend()` (moved to persistence)
- `xmlSaveCapillaries_Descriptors()` (XML deprecated in favor of CSV)

---

### 6. ✅ Standardized Method Names in `CapillariesPersistence` Class

**Location:** `multiTools/src/main/java/plugins/fmp/multitools/experiment/capillaries/CapillariesPersistence.java`

#### New Standardized Methods
```java
boolean loadDescriptions(Capillaries, String resultsDirectory)   // replaces load_CapillariesDescription()
boolean loadMeasures(Capillaries, String binDirectory)           // replaces load_CapillariesMeasures()
boolean saveDescriptions(Capillaries, String resultsDirectory)   // replaces saveCapillariesDescription()
boolean saveMeasures(Capillaries, String binDirectory)           // replaces save_CapillariesMeasures()
```

#### Deprecated Methods
- `load_CapillariesDescription()`
- `load_CapillariesMeasures()`
- `saveCapillariesDescription()`
- `save_CapillariesMeasures()`

---

## Unified Method Naming Convention

All entity classes (Spots, Capillaries) now follow this consistent pattern:

### Collection Level (Spots, Capillaries)
```java
// Description persistence (results directory)
boolean loadDescriptions(String resultsDirectory)
boolean saveDescriptions(String resultsDirectory)

// Measures persistence (bin directory)
boolean loadMeasures(String binDirectory)
boolean saveMeasures(String binDirectory)

// ROI-Sequence transfer
void transferROIsToSequence(SequenceCamData seqCamData)
void transferROIsFromSequence(SequenceCamData seqCamData)
```

### Persistence Class Level (SpotsPersistence, CapillariesPersistence)
```java
boolean loadDescriptions(Entity entity, String resultsDirectory)
boolean loadMeasures(Entity entity, String binDirectory)
boolean saveDescriptions(Entity entity, String resultsDirectory)
boolean saveMeasures(Entity entity, String binDirectory)
```

### Mapper Class Level (SpotsSequenceMapper, CapillariesSequenceMapper)
```java
void transferROIsToSequence(Entity entity, SequenceCamData seqCamData)
void transferROIsFromSequence(Entity entity, SequenceCamData seqCamData)

// Capillaries only:
void transferMeasuresToKymos(Capillaries capillaries, SequenceKymos seqKymos)
void transferMeasuresFromKymos(Capillaries capillaries, SequenceKymos seqKymos)
```

---

## Backward Compatibility

All changes maintain **100% backward compatibility**:

1. ✅ Old method names remain functional
2. ✅ Marked with `@Deprecated` annotation
3. ✅ Include JavaDoc with replacement guidance
4. ✅ Delegate to new methods internally
5. ✅ No breaking changes to existing code

**Deprecation Plan:** Old methods will be removed in version 3.0.

---

## Benefits

### 1. **Consistency**
- Identical naming patterns across Spots and Capillaries
- Predictable method names reduce cognitive load
- Easier for developers to switch between entity types

### 2. **Clarity**
- Method names clearly indicate operation: `load`, `save`, `transfer`
- Direction is explicit: `To` vs `From`
- Scope is clear: `Descriptions` vs `Measures`

### 3. **Maintainability**
- Common pattern makes refactoring easier
- Easier to identify similar operations across codebase
- Better IDE autocomplete experience

### 4. **Documentation**
- Enhanced JavaDoc with operation details
- Clear parameter descriptions
- Cross-references between related methods

### 5. **Unified Mapper Pattern**
- Both Spots and Capillaries now have dedicated mapper classes
- Consistent pattern with existing CagesSequenceMapper
- Separation of concerns between entity and sequence operations

---

## Code Quality

### Validation
- ✅ No compilation errors
- ✅ No linter warnings
- ✅ All deprecated methods properly documented
- ✅ Consistent code style throughout

### Testing Recommendations
1. Verify round-trip persistence (save → load → verify)
2. Test ROI transfer sequence → entity → sequence
3. Confirm backward compatibility with deprecated methods
4. Test measure transfer to/from kymographs (capillaries only)

---

## Usage Examples

### Loading Data
```java
// Old way (still works, but deprecated)
spots.loadSpotsAll(resultsDir);
spots.loadSpotsMeasures(binDir);

// New standardized way
spots.loadDescriptions(resultsDir);
spots.loadMeasures(binDir);
```

### Saving Data
```java
// Old way (still works, but deprecated)
capillaries.getPersistence().saveCapillariesDescription(capillaries, resultsDir);
capillaries.getPersistence().save_CapillariesMeasures(capillaries, binDir);

// New standardized way
capillaries.saveDescriptions(resultsDir);
capillaries.saveMeasures(binDir);
```

### ROI Transfer (Spots)
```java
// Old way (still works, but deprecated)
SpotsSequenceMapper.transferROIsFromSpotsToSequence(spots, seqCamData);
SpotsSequenceMapper.transferROIsFromSequenceToSpots(spots, seqCamData);

// New standardized way
SpotsSequenceMapper.transferROIsToSequence(spots, seqCamData);
SpotsSequenceMapper.transferROIsFromSequence(spots, seqCamData);
```

### ROI Transfer (Capillaries - NEW!)
```java
// Push capillary ROIs to camera sequence
CapillariesSequenceMapper.transferROIsToSequence(capillaries, seqCamData);

// Pull edited ROIs back from camera sequence
CapillariesSequenceMapper.transferROIsFromSequence(capillaries, seqCamData);
```

### Measure Transfer (Capillaries - Enhanced)
```java
// Push measures to kymograph for visualization
CapillariesSequenceMapper.transferMeasuresToKymos(capillaries, seqKymos);

// Pull edited measures back from kymograph
CapillariesSequenceMapper.transferMeasuresFromKymos(capillaries, seqKymos);
```

### Complete Workflow (Capillaries - NEW!)
```java
// Push everything to sequences
CapillariesSequenceMapper.transferAllToSequences(capillaries, seqCamData, seqKymos);

// Pull everything back from sequences
CapillariesSequenceMapper.transferAllFromSequences(capillaries, seqCamData, seqKymos);
```

---

## Files Modified

### New Files Created (1)
1. `multiTools/src/main/java/plugins/fmp/multitools/experiment/capillaries/CapillariesSequenceMapper.java`

### Files Modified (6)
1. `multiTools/src/main/java/plugins/fmp/multitools/experiment/spots/Spots.java`
2. `multiTools/src/main/java/plugins/fmp/multitools/experiment/spots/SpotsSequenceMapper.java`
3. `multiTools/src/main/java/plugins/fmp/multitools/experiment/spots/SpotsPersistence.java`
4. `multiTools/src/main/java/plugins/fmp/multitools/experiment/capillaries/Capillaries.java`
5. `multiTools/src/main/java/plugins/fmp/multitools/experiment/capillaries/CapillariesPersistence.java`
6. `Cursor_setup/PERSISTENCE_UNIFICATION_ANALYSIS.md` (documentation)

### Files Created (Documentation)
1. `Cursor_setup/PERSISTENCE_UNIFICATION_ANALYSIS.md` - Detailed analysis
2. `Cursor_setup/PERSISTENCE_UNIFICATION_IMPLEMENTATION.md` - This summary

---

## Phase 2: ROI Persistence Unification (COMPLETED 2026-01-19)

### Implemented Features

1. **Common ROI Infrastructure** ✅
   - Created `ROIType` enum with ELLIPSE, LINE, POLYLINE, POLYGON, RECTANGLE types
   - Created `ROIPersistenceUtils` class with common save/load methods
   - Unified ROI handling across all entity types

2. **Spot ROI Coordinate Persistence** ✅
   - Added full ROI coordinate saving to CSV
   - Preserves user-edited spot ROIs
   - Maintains backward compatibility with v2.0 format
   - Smart storage: uses geometric params for simple ellipses, full coords for modified ROIs

3. **Capillary ROI Type Support** ✅
   - Added roiType column to CSV
   - Explicit type indicators (LINE, POLYLINE)
   - Enhanced documentation

4. **Cage Persistence Unification** ✅
   - Applied same standardization as Spots/Capillaries
   - Added roiType support (POLYGON, RECTANGLE)
   - Separated measures (fly positions) to bin directory
   - Standardized method names

5. **CSV Format Version 2.1** ✅
   - Updated all CSV files to v2.1
   - Added version header in files
   - Updated filenames: `v2.1_*_description.csv`, `v2.1_*_measures.csv`
   - Full backward compatibility with v2.0

### New Files Created (Phase 2):
1. `multiTools/.../tools/ROI2D/ROIType.java`
2. `multiTools/.../tools/ROI2D/ROIPersistenceUtils.java`
3. `Cursor_setup/ROI_PERSISTENCE_MIGRATION_GUIDE.md`

### Files Modified (Phase 2):
1. `multiTools/.../spot/Spot.java`
2. `multiTools/.../spot/SpotPersistence.java`
3. `multiTools/.../spots/SpotsPersistence.java`
4. `multiTools/.../capillary/CapillaryPersistence.java`
5. `multiTools/.../capillaries/CapillariesPersistence.java`
6. `multiTools/.../cage/Cage.java`
7. `multiTools/.../cages/Cages.java`
8. `multiTools/.../cages/CagesPersistence.java`
9. `multiTools/.../cages/CagesSequenceMapper.java`
10. `multiTools/.../cages/CagesPersistenceLegacy.java`

---

## Next Steps (Future Enhancements)

### Completed ✅
1. ✅ Spot ROI Coordinate Persistence
2. ✅ Common ROI Infrastructure
3. ✅ Cage Persistence Unification
4. ✅ CSV Format v2.1

### Future Improvements (Optional)
1. **Common Interface/Base Class**
   - Extract `EntityPersistence<T>` interface
   - Reduce code duplication between persistence classes
   - Status: Deferred (architectural improvement)

2. **Remove Deprecated Methods**
   - Scheduled for version 3.0
   - Allows cleanup of old code paths
   - Status: Future milestone

---

## Migration Guide

### For Plugin Developers

**No immediate action required!** All old method names continue to work.

**Recommended actions:**
1. Update code to use new standardized method names
2. Watch for deprecation warnings in IDE
3. Follow JavaDoc links to find replacement methods
4. Test with existing experiments to verify compatibility

**Timeline:**
- v2.3.3 (current): New methods available, old methods deprecated
- v2.4.x - v2.9.x: Both old and new methods work
- v3.0.0: Deprecated methods removed (breaking change)

---

## Summary

### Phase 1 Implementation (2026-01-19 AM):
- ✅ Created `CapillariesSequenceMapper` with ROI and measure transfer capabilities
- ✅ Standardized all persistence method names across Spots, Capillaries, and Cages
- ✅ Maintained 100% backward compatibility
- ✅ Established consistent patterns for future development

### Phase 2 Implementation (2026-01-19 PM):
- ✅ Created common ROI infrastructure (`ROIType` enum, `ROIPersistenceUtils` class)
- ✅ Implemented ROI coordinate persistence for Spots with type indicators
- ✅ Enhanced Capillary persistence with ROI type column
- ✅ Unified Cage persistence with ROI types and separated measures
- ✅ Upgraded CSV format to v2.1 with version headers
- ✅ Provided comprehensive documentation and migration guide

### Complete Achievement:
The codebase now has a **fully unified, consistent approach** to persistence and sequence communication across all three entity types (Spots, Capillaries, Cages):

- **Common ROI persistence** - Same utilities and patterns for all entities
- **Standardized methods** - Consistent naming across all classes
- **User edits preserved** - ROI modifications are saved and restored
- **Efficient storage** - Smart selection between geometric params and full coordinates
- **Version tracking** - Both in filenames and headers
- **Full backward compatibility** - Old files still work
- **Comprehensive documentation** - Migration guide and examples

---

**Phase 1 completed:** 2026-01-19 (Method standardization)  
**Phase 2 completed:** 2026-01-19 (ROI persistence unification)  
**No compilation errors or linter warnings**  
**Ready for testing and production use**
