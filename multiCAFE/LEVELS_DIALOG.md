# Unified kymograph level detection

The **Levels** tab now contains both former kymograph detection methods.

- **Track level across time** selects the former Levels v2 detector. When off,
  the original independent threshold pass and optional refinement pass are used.
- **Smooth detected curve** applies median and spike filtering to either method.
  Leave it off when preserving short level changes is important for gulp analysis.
- **Detect bottom level** is independent of the top detection method. It uses the
  first colour transform and threshold, including when temporal tracking is on.
- **Advanced** reveals refinement controls for independent detection, tracking
  and tape controls for temporal detection, and filtering settings when smoothing
  is selected. It changes visibility only, never the saved detection recipe.

Tracking and smoothing default to off for old recipes. Bottom detection retains
the original default of on. Experiment defaults are stored in Experiment.xml;
per-capillary settings use an optional unified_level_recipe column in
CapillariesDescription.csv. Existing CSV geometry and ground-truth endpoints
remain readable. CSV experiment exports also include the unified recipe.

The old separate Levels v2 dialog did not persist its algorithm settings, so
those historical choices cannot be reconstructed automatically. The direct-image
Levels and independent Bottom tabs remain available.

Rerunning top-level detection clears derivative and gulp results for affected
capillaries; rerun gulp detection afterward.
