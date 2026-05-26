# Phase C — Capillary measure bridge (done)

**Goal:** Move capillary ROI ↔ model logic off [`SequenceKymos`](../multiTools/src/main/java/plugins/fmp/multitools/experiment/sequence/SequenceKymos.java) into a dedicated collaborator while keeping the same public API on `SequenceKymos`.

## New type

[`CapillaryKymographMeasureBridge`](../multiTools/src/main/java/plugins/fmp/multitools/experiment/sequence/CapillaryKymographMeasureBridge.java) — constructed with the owning `SequenceKymos`; implements validate/transfer/sync/save for capillary measures on the ICY sequence.

**Delegates on `SequenceKymos`:** `validateROIs` (still wrapped in `processingLock` + null check on `SequenceKymos`), `validateRoisAtT`, `removeROIsPolylineAtT`, `updateROIFromCapillaryMeasure`, `transferKymosRoisToCapillaries_Measures`, `transferKymosRoi_at_T_To_Capillaries_Measures` (both overloads), `validateLinearROIsAtT` (both), `validateGulpROIsAtT` (all three), `transferCapillariesMeasuresToKymos`, `getCapillaryForFrame`, `syncROIsForCurrentFrame`, `replaceCapillaryMeasureRoisAtT`, `saveKymosCurvesToCapillariesMeasures`.

**Note:** `saveKymosCurvesToCapillariesMeasures` uses the bridge’s `SequenceKymos` instance (same as `this` when called on the experiment’s seq kymos).

**Earlier phases:** [Phase A](KYMOGRAPH_SEQUENCE_PHASE_A.md) · [Phase B](KYMOGRAPH_SEQUENCE_PHASE_B.md)
