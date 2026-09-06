# Temporal rack tracking prototype

In multiCAFE's **Track capillaries along time** dialog, use **Track rack from image 0 (full stack)**.
Load/validate image-0 blue measurements first. The command uses stored image-0 physical endpoints;
it does not read green ROIs as physical reference geometry or automatically load ground-truth files.
The existing ground-truth reader remains responsible for filename precedence.

The command scans every image in the experiment's camera image list, regardless of the legacy
tracking range controls. It replaces later blue phases of capillaries with image-0 measurements.
Capillaries without a measured reference are skipped. Green intervals remain independent.
This prototype updates the physical blue overlay; it does not regenerate kymographs or move
their green sampling corridors.

## Registration and confidence

The existing frame detector finds the image-0 support and nine internal dividers. Blue midpoint
positions grouped by cage provide only a coarse rack grid guide. Small reference image patches
at divider/support junctions provide the actual motion evidence. Normalized correlation tolerates
brightness changes. Every frame is matched directly against image 0, avoiding accumulated drift.

At least six visible junctions must match with correlation >= 0.8. At least 70% of reference
junctions must agree within 1.5 pixels of the median translation. Border maxima, weak contrast,
missing images, dimension changes and inconsistent motion are flagged UNCERTAIN. Their overlays
hold the last accepted phase; these positions must not be interpreted as measured motion.
The CSV records NaN for unavailable estimates and the phase actually displayed.

The bounded search is the smaller of 3% of image width and 30% of cage pitch. Large jumps may
therefore remain uncertain. Registration uses integer-pixel patch matches; median consensus can
produce half-pixel translations. It does not fit affine motion, local capillary movement, or
changing perspective. Static tilt/perspective is retained in the image-0 reference, subject to
the existing rack detector finding at least six usable junctions. Population accuracy is unvalidated.

## Geometry and output

A new blue phase is emitted when accepted displacement differs from the last stored phase by
the **Blue phase change (px)** threshold (default 2 pixels, Euclidean distance). Thus gradual
motion accumulates against the stored pose and stable frames do not duplicate geometry.
Translations always apply to image 0, retaining length, angle and lateral offsets.
Rerunning replaces obsolete temporal blue keyframes. It preserves corridor extension ratios.

Scanning stages all geometry. Stopping, closing the dialog, changing experiment or failing the
scan does not apply staged geometry. On completion, scrub the viewer and use **Plot endpoint
trajectories** to inspect physical endpoints and fixed lengths. **Save** persists the blue phases
through the existing `CapillaryPhaseGeometry.csv` sidecar and normal operational save workflow.
No tracking code writes ground-truth CSVs. Every completed scan creates a uniquely named
`results/RackTracking-*.csv` containing every frame's displacement, confidence, status and phase.

## Validation

`RackTranslationTrackerTest` covers slow drift followed by stabilization, stationary images with
brightness change, tip clutter, blank frames, dimension changes, search overflow, incompatible
shear, sparse phase storage, fixed lengths, displaced green geometry, cancellation, sidecar
round-trip and ground-truth byte preservation. A JPEG stack integration test exercises image
loading, frame reporting and deferred application. The visual fixture is generated at
`multiTools/target/rack-tracking-validation.png` (synthetic data, not a real experiment).

Run the focused suite from the repository root (quote Maven properties in PowerShell):

```powershell
mvn -o "-Dmaven.repo.local=C:\Users\fred\.m2\repository" -pl multiCAFE -am test "-Dtest=RackTranslationTrackerTest,FrameSupportBarDetectorTest,CapillaryPhaseGeometryModelTest,CapillaryPhaseGeometryPersistenceTest,ExperimentMovementPrescannerTest" "-Dsurefire.failIfNoSpecifiedTests=false"
```

The supplied `F:\DGRP\multiCAFE\data_final\data\liste_71_groundtruth_candidates.txt` was unavailable
in the implementation environment. Real drift/stable/tilted records still need manual temporal
validation before population use. Image-0 ground truth alone cannot establish temporal accuracy.
