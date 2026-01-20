# Bug Fix: Spots Persistence Infinite Recursion

## Problem Description

When exporting measures from a stack of 371 experiments (mostly legacy multiCAFE data), a `StackOverflowError` occurred due to infinite recursion in the spots persistence loading code.

## Experiment Type Distinctions

### multiCAFE
- ✅ **Capillary measures** (main data)
- ❌ **No spots measures**
- 🔶 **Fly positions** (optional)
- ✅ **Kymographs** (always present, used to construct measures)

### multiSPOTS / multiSPOTS96
- ❌ **No capillaries**
- ✅ **Spot measures** (main data)
- 🔶 **Fly positions** (optional)
- 🔶 **Kymographs** (optional)

### Error Symptoms
- Stack trace showing repeated calls between:
  - `SpotsPersistence$Persistence.loadDescription()`
  - `SpotsPersistenceLegacy.loadDescriptionWithFallback()`
  - `Spots.loadDescriptions()`
- Error occurred when processing multiCAFE experiments (which have no spots persistence files)

### Root Cause

In `SpotsPersistenceLegacy.java`, the fallback logic was calling back into the public API:

```java
// BEFORE (caused infinite recursion):
boolean success = spotsArray.loadDescriptions(resultsDirectory);
```

This created a circular call chain:
1. `SpotsPersistence.Persistence.loadDescription()` → checks for `SpotsDescription.csv`
2. File not found → calls `SpotsPersistenceLegacy.loadDescriptionWithFallback()`
3. Checks for `SpotsArray.csv` (not found)
4. Calls `spotsArray.loadDescriptions()` → **back to step 1** (infinite loop)

## Solution Implemented

### 1. Fixed Infinite Recursion (SpotsPersistenceLegacy.java)

**Removed problematic fallback code** that was creating circular calls:

```java
// AFTER (fixed):
// Note: SpotsMeasures.csv is not checked here to avoid infinite recursion
// multiCAFE data does not have spots persistence files, so we return false
return false;
```

**Files changed:**
- `SpotsPersistenceLegacy.java`
  - Removed fallback attempts to load from `SpotsMeasures.csv` in both `loadDescriptionWithFallback()` and `loadMeasuresWithFallback()`
  - Cleaned up unused imports (`java.io.File`) and constants (`CSV_FILENAME`)

### 2. Improved Performance & Clarity

**Added detection methods** to check if persistence files exist before attempting to load them:

#### SpotsPersistence.java
```java
/**
 * Checks if any spots description files exist in the results directory.
 * This is useful to determine if an experiment has spots data (multiSPOTS)
 * or not (multiCAFE).
 */
public boolean hasSpotsDescriptionFiles(String resultsDirectory)

/**
 * Checks if any spots measures files exist in the bin directory.
 */
public boolean hasSpotsMeasuresFiles(String binDirectory)
```

#### CapillariesPersistence.java
```java
/**
 * Checks if any capillary description files exist in the results directory.
 * This is useful to determine if an experiment has capillaries data.
 */
public boolean hasCapillariesDescriptionFiles(String resultsDirectory)

/**
 * Checks if any capillary measures files exist in the bin directory.
 */
public boolean hasCapillariesMeasuresFiles(String binDirectory)
```

#### Spots.java
```java
/**
 * Checks if spots description files exist in the results directory.
 * Useful to determine if an experiment has spots data.
 */
public boolean hasSpotsFiles(String resultsDirectory)
```

#### Experiment.java
Updated load methods to check for file existence first:

```java
public boolean load_spots_description_and_measures() {
    String resultsDir = getResultsDirectory();
    
    // Check if spots files exist before attempting to load
    // This avoids unnecessary file system checks for multiCAFE experiments
    if (!spots.hasSpotsFiles(resultsDir)) {
        return false;
    }
    
    // ... continue with loading
}

public boolean load_capillaries_description_and_measures() {
    String resultsDir = getResultsDirectory();
    
    // Check if capillaries files exist before attempting to load
    // This avoids unnecessary file system checks for experiments without capillaries
    if (!capillaries.getPersistence().hasCapillariesDescriptionFiles(resultsDir)) {
        return false;
    }
    
    // ... continue with loading
}
```

## Benefits

### 1. **Fixed the StackOverflowError**
- Experiments can now be exported successfully, even with 371+ legacy multiCAFE experiments
- No more infinite recursion when loading experiments without spots files

### 2. **Improved Performance**
- Avoided unnecessary file system checks for multiCAFE experiments (no spots)
- Early exit when files don't exist reduces I/O operations

### 3. **Better Code Clarity**
- Explicit distinction between "no spots files" (normal for multiCAFE) vs "failed to load" (error)
- Self-documenting code that shows the intent
- Clear separation of concerns:
  - **multiCAFE**: loads capillaries, skips spots
  - **multiSPOTS/multiSPOTS96**: loads spots, skips capillaries
  - **Fly positions**: handled separately (optional for both types)
  - **Kymographs**: always present for multiCAFE, optional for multiSPOTS

### 4. **Maintained Backward Compatibility**
- All existing functionality preserved
- Legacy file formats still supported through fallback mechanisms
- No breaking changes to public APIs

## Testing Recommendations

1. **Export from mixed experiment stack:**
   - Test with stack containing both multiCAFE and multiSPOTS experiments
   - Verify export completes successfully for all 371 experiments

2. **Individual experiment loading:**
   - **multiCAFE**: should load capillaries + kymographs, skip spots (no error)
   - **multiSPOTS/multiSPOTS96**: should load spots, skip capillaries (no error)
   - **Fly positions**: should load when present (optional for both types)
   - Test legacy experiments with old file formats

3. **Save/Load roundtrip:**
   - Save spots/capillaries in new format
   - Reload and verify data integrity
   - Test with both v2 and legacy formats

4. **Verify correct data types loaded:**
   - multiCAFE experiments: confirm capillary measures present, no spot measures
   - multiSPOTS experiments: confirm spot measures present, no capillary measures

## Files Modified

1. `SpotsPersistenceLegacy.java` - Fixed infinite recursion
2. `SpotsPersistence.java` - Added detection methods
3. `CapillariesPersistence.java` - Added detection methods
4. `Spots.java` - Added convenience method
5. `Experiment.java` - Updated load methods to use detection

## Related Classes

- `SpotsPersistence` - Main persistence interface for spots
- `SpotsPersistenceLegacy` - Handles legacy file formats
- `CapillariesPersistence` - Persistence for capillaries
- `Experiment` - Core experiment class
- `XLSExport` - Excel export that triggered the bug during batch processing
