# Phase B — Disk layout extraction (done)

**Goal:** Move on-disk kymograph path discovery and capillary filename resolution out of [`SequenceKymos`](../multiTools/src/main/java/plugins/fmp/multitools/experiment/sequence/SequenceKymos.java) while keeping the same public API and `processingLock` behavior on `SequenceKymos` entry points.

## New types (multiTools `experiment.sequence`)

| Type | Role |
|------|------|
| [`KymographDiskLayout`](../multiTools/src/main/java/plugins/fmp/multitools/experiment/sequence/KymographDiskLayout.java) | Interface: `listImageDescriptors(baseDirectory, cages, allSpots)` |
| [`PerSpotRoiTiffDiskLayout`](../multiTools/src/main/java/plugins/fmp/multitools/experiment/sequence/PerSpotRoiTiffDiskLayout.java) | Per-spot ROI name + `.tiff` (legacy SPOTS-style) |
| [`CageStackTiffDiskLayout`](../multiTools/src/main/java/plugins/fmp/multitools/experiment/sequence/CageStackTiffDiskLayout.java) | `kymocage_<id>.tif(f)` per cage |
| [`CapillaryKymographNameResolution`](../multiTools/src/main/java/plugins/fmp/multitools/experiment/sequence/CapillaryKymographNameResolution.java) | `resolve(path, t, capillaries)` — basename / L-R / `line…` prefix rules |

**Delegates:** `createKymographFileList`, `createCageSpotKymographFileList`, `getCapillaryForFrame` on `SequenceKymos` call the types above (file lists still wrapped in `processingLock`).

**Next (Phase C):** ~~Extract capillary measure-bridge / polyline sync cluster from `SequenceKymos` per plan.~~ See [KYMOGRAPH_SEQUENCE_PHASE_C.md](KYMOGRAPH_SEQUENCE_PHASE_C.md).

**Phase D:** [KYMOGRAPH_SEQUENCE_PHASE_D.md](KYMOGRAPH_SEQUENCE_PHASE_D.md) — `KymographKind` + `Experiment` listing router.
