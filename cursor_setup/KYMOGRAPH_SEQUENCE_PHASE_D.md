# Phase D — `KymographKind` (wire-experiment)

**Goal:** Declare how kymograph frame files are laid out under the bin (`plugins.fmp.multitools.experiment.KymographKind`) and route listing through `Experiment` so plugins do not each infer layout from disk.

**Interchange format (for now):** All kinds above assume **TIFF (or `.tif`) files on disk** and the existing ICY load path (`SequenceKymos#loadKymographs`). HDF5 or other containers stay out of scope until a dedicated `RasterSource` / storage phase; the flip‑flop and lock‑avoidance patterns for TIFF remain the operational reality for multiCAFE.

## Types and API

| Symbol | Role |
|--------|------|
| [`KymographKind`](../multiTools/src/main/java/plugins/fmp/multitools/experiment/KymographKind.java) | `CAPILLARY_MODEL_TIFF`, `CAGE_STACKED_TIFF`, `SPOT_ROI_NAME_TIFF` |
| [`Experiment#getKymographKind` / `setKymographKind`](../multiTools/src/main/java/plugins/fmp/multitools/experiment/Experiment.java) | Default **`CAPILLARY_MODEL_TIFF`** (multiCAFE-style) |
| [`Experiment#listPotentialKymographFrames()`](../multiTools/src/main/java/plugins/fmp/multitools/experiment/Experiment.java) | Uses current kind |
| [`Experiment#listPotentialKymographFrames(KymographKind)`](../multiTools/src/main/java/plugins/fmp/multitools/experiment/Experiment.java) | Explicit layout without mutating kind |
| [`Experiment#loadKymographs(boolean)`](../multiTools/src/main/java/plugins/fmp/multitools/experiment/Experiment.java) | Loads using **`listPotentialKymographFrames()`** (honours kind) |
| [`Experiment#loadCageSpotKymographs(boolean)`](../multiTools/src/main/java/plugins/fmp/multitools/experiment/Experiment.java) | Still loads cage stack layout via **`listPotentialKymographFrames(CAGE_STACKED_TIFF)`** (independent of kind) |

## Call-site updates

- **multiSPOTS96** [`Spots96ExperimentOpenPipeline`](../multiSPOTS96/src/main/java/plugins/fmp/multiSPOTS96/dlg/browse/Spots96ExperimentOpenPipeline.java): `setKymographKind(CAGE_STACKED_TIFF)` before loading cage kymographs.
- **multiSPOTS96** [`LoadSavePanel`](../multiSPOTS96/src/main/java/plugins/fmp/multiSPOTS96/dlg/kymograph/LoadSavePanel.java): `listPotentialKymographFrames(CAGE_STACKED_TIFF)` instead of calling `SequenceKymos` directly.
- **multiTools** [`ExperimentService#loadKymographs`](../multiTools/src/main/java/plugins/fmp/multitools/service/ExperimentService.java): uses `exp.listPotentialKymographFrames()`.

## Not in this phase

- Persisting `kymographKind` in experiment XML (could be added when descriptors carry format version).
- Auto-detect kind from bin contents (optional helper later).

**Earlier phases:** [Phase A](KYMOGRAPH_SEQUENCE_PHASE_A.md) · [Phase B](KYMOGRAPH_SEQUENCE_PHASE_B.md) · [Phase C](KYMOGRAPH_SEQUENCE_PHASE_C.md)
