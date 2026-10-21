# Event Inspector prototype

Open one experiment and load its existing capillary kymographs and saved measurements.
In **Kymographs → Event Inspector**, click **Open Event Inspector...**. The tab contains
only this launch button. A separate resizable window contains the navigation, review
controls, and meniscus chart. Choose a capillary there (the selector includes its cage).
The inspector continues to use the existing Icy kymograph viewer.
Use **Inspect / refresh** if kymographs or measurements were reloaded while the window was open.
Clicking the launch button again brings the existing window forward. Closing the window,
switching to another Kymographs tab, or changing experiments ends inspection and restores
the normal measurement display.

- Click either trajectory or the kymograph to select a time column. Both cursors move together.
- Use previous/next, the slider, zero-based column entry, or minutes plus **Go to time**.
- **Window +/- min** defaults to 10 minutes. The chart uses this time range; a pale blue
  band marks the corresponding columns on the kymograph. The viewer can include extra
  context to preserve image aspect ratio. A requested time navigates to the nearest column.
- **Previous/Next gulp** visits nonzero stored gulp amplitudes, including negative detections.
  A detected gulp is a navigation aid, not a validated feeding event.
- Toggle the measurement and cursor overlays independently.
- Choose a classification and enter a comment. Drafts remain in the current Experiment
  object while navigating. **Save review CSV...** persists the selected point; other draft
  points must be saved individually. **Load reviews...** reloads saved annotations for
  this experiment. It does not import an external event queue.

## Coordinates and timing

Viewer **T selects a capillary kymograph**, not an acquisition frame. Horizontal **X is a
zero-based time column**. The first prototype accepts only subsampling factor 1. It uses
the existing capillary-to-kymograph resolver and the same measurement-to-ROI construction
as the normal display. It does not calculate or detect menisci.

The chart preserves stored Y values in pixels, with Y increasing downward as in the image.
For normalized kymographs these are normalized-image row coordinates, not camera Y or
unscaled capillary distances. Missing values remain missing. Measurements with noninteger,
unordered, or out-of-bounds X coordinates are rejected rather than resampled.

The inspector reads the experiment's frame timestamp array without rebuilding or saving it.
When the kymograph has fewer columns than the frame array, its first-column time must match
a timestamp exactly and its width must fit the remaining contiguous frames. Duplicate or
unordered times, incompatible windows, and downsampled kymographs are rejected. When no
timestamp array exists, the saved kymograph interval supplies an explicitly labeled nominal
clock; camera-frame identifiers and acquisition dates are then left blank. Existing timestamp
arrays may themselves originate from filename or filesystem fallback timing in multiCAFE;
the inspector does not independently authenticate acquisition metadata.

## Measurement preservation and review CSV

Inspector measurement overlays are detached, read-only ROIs produced by the existing
capillary methods. The normal editable measurement layers are temporarily hidden and
restored on exit. Inspection bypasses the normal viewer-change validation/write-back path.
On exit it returns to the entry capillary so any preexisting editable ROI state is preserved.
It does not run detection, evaporation correction, interpolation, or measurement saves.
Original camera images are optional and are not loaded by the prototype.

The separate UTF-8 CSV defaults to `results/EventInspectorReviews.csv`. It contains:
experiment directory identifier, cage, capillary, source kymograph path, zero-based column,
analysis-frame and original-frame indices when mapped, relative milliseconds, acquisition
time in ISO UTC when available, timing source, original image reference when available,
classification, comment, stored upper/lower positions, their current-minus-previous-column
deltas, and stored gulp amplitude. Empty numeric fields mean unavailable data.

Saving updates the record keyed by experiment, cage, capillary, source kymograph, and column.
Other saved observations are preserved. Quoted multiline comments are supported; incompatible
CSV headers are refused. A temporary sibling file and atomic replacement (where supported)
protect the existing review CSV during saving. Loading saved reviews merges annotations for
the exact experiment-directory identifier; moving a dataset may require reconciling identifiers.

External P06 review-queue import, interval annotations, automatic event detection, consumption
plots, camera-frame navigation, and temporary kymograph generation are deferred.

## Validation

Build and focused tests:

```powershell
mvn -o -pl multiCAFE -am test '-Dtest=EventInspectorTimeAxisTest,EventReviewCsvTest' '-Dsurefire.failIfNoSpecifiedTests=false'
```

Before scientific use, verify a known event on a real recording: compare the chart's selected
column with the kymograph's upper/lower overlay vertices, navigate across an irregular timing
gap, save/reload a classification, and confirm canonical measurement files remain unchanged.
