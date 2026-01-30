# Kymos Display & ROI – multiCAFE vs xMultiCAFE0

## 1. Canvas2DWithTransforms

**Conclusion: effectively the same.**

- Same structure: extends `Canvas2D`, same toolbar customization (`customizeToolbar`, `customizeToolbarStep2`), same `getImage` override for transforms, same helpers (`zoomImage_1_1`, `shrinkImage_to_fit`, `updateTransformsCombo*`, `selectIndexStep1`, etc.).
- Only differences: package (`multicafe0.tools.Canvas2D` vs `multicafe.canvas2D`) and imports (`multicafe0.tools.ImageTransform` vs `multitools.tools.imageTransform`, `multicafe0.resource` vs `multicafe.resource`).
- No extra overrides that would affect ROI/layer painting. So the custom canvas class is not the cause of ROIs not showing.

---

## 2. displayON() – when to create vs reuse viewer

### xMultiCAFE0 (lines 199–256)

- Uses **`if (vList.size() == 0)`** to decide:
  - **Create new viewer** (set Canvas2DWithTransforms, toolbar, bounds, visible, then `selectKymographImage` / `selectKymographComboItem`).
  - **Else**: reuse **existing** viewer (`vList.get(0)`): only reposition, set fit-to-canvas, add listener. **Does not set the canvas.** So the existing viewer keeps whatever canvas it had (typically ICY default if it was auto-created when the sequence was loaded).

### multiCAFE (current)

- Uses **`reuseViewer = existingViewer != null && existingViewer.isVisible()`**:
  - **Create** when `!reuseViewer` (no viewer or existing not visible).
  - **Reuse** when an existing visible viewer is present.
- In addition, **ImageLoader.loadSequenceFromImagesList** (multiTools) **closes all auto-created viewers** on the sequence after loading (lines 195–203). So when kymos are loaded, the sequence always has **0** viewers when `displayON()` runs. So multiCAFE **always** creates a new viewer and **always** sets Canvas2DWithTransforms for kymos.

### Important difference

- In xMultiCAFE0, **SequenceLoaderService.loadSequenceFromImagesList** does **not** close viewers. So the sequence can still have an auto-created viewer (default canvas) when `displayON()` runs. Then `vList.size() != 0`, the code **reuses** that viewer and never sets Canvas2DWithTransforms, so ROIs are shown with the default canvas.
- In multiCAFE, viewers are closed during load, so we always create a new viewer and force Canvas2DWithTransforms; we never get the “reuse default canvas” path that works in xMultiCAFE0.

So the behavioral difference for ROIs likely comes from **when we create vs reuse** and **whether we close viewers on load**, not from Canvas2DWithTransforms itself.

---

## 3. selectKymographImage() – frame change and ROIs

### xMultiCAFE0 (lines 352–381)

- Wraps in **`seqKymos.getSeq().beginUpdate()`** / **`endUpdate()`**.
- Gets viewer `v = seqKymos.getSeq().getFirstViewer()`.
- If `v != null`:
  - `v.setPositionT(isel)` (viewer position first).
  - **`seqKymos.validateRoisAtT(seqKymos.getCurrentFrame())`** – validates existing ROIs at current frame (interpolate, names, etc.); **does not add/remove ROIs**.
  - `seqKymos.setCurrentFrame(isel)`.
- Then `displayROIsAccordingToUserSelection()` and `selectCapillary()`.
- No `syncROIsForCurrentFrame`, no per-frame ROI replace, no explicit canvas refresh.

So in xMultiCAFE0 the sequence **keeps all ROIs** (for all frames); on frame change they only **validate** ROIs and update the viewer position.

### multiCAFE (current)

- No `beginUpdate`/`endUpdate` in Display.
- Transfers measures from previous frame to capillaries if needed, then **`seqKymos.syncROIsForCurrentFrame(isel, exp.getCapillaries())`**, which:
  - Removes current “measure” ROIs and adds ROIs only for frame `isel` (MeasureRoiSync).
- Then `v.setPositionT(isel)` (in current flow, position is set before sync in one path).
- Then `v.getCanvas().refresh()` and `displayROIsAccordingToUserSelection()` / `selectCapillary()`.

So in multiCAFE we **replace** ROIs on every frame change (only ROIs for the current frame are on the sequence). That is a different model from xMultiCAFE0 (all ROIs on sequence, validate on change).

---

## 4. Summary

| Aspect | xMultiCAFE0 | multiCAFE |
|--------|-------------|-----------|
| **Canvas2DWithTransforms** | Same logic, different packages | Same |
| **Viewer on load** | Viewers not closed → can reuse 1 viewer (default canvas) | Viewers closed in ImageLoader → always 0 viewers at displayON |
| **displayON create condition** | Create only if `vList.size() == 0` | Create if `!reuseViewer` (no visible viewer); in practice always create for kymos |
| **Reused viewer** | Canvas left unchanged (default) | N/A (never reuse for kymos) |
| **Frame change** | beginUpdate → set position → validateRoisAtT → setCurrentFrame → endUpdate | syncROIsForCurrentFrame (replace ROIs) → set position → refresh |

So:

1. **Canvas2DWithTransforms** is not the root cause; it matches xMultiCAFE0.
2. **displayON** differs because we always create a new viewer (we close viewers on load) and always set the custom canvas; xMultiCAFE0 often reuses an existing viewer with default canvas.
3. **selectKymographImage** differs: xMultiCAFE0 validates existing ROIs and uses beginUpdate/endUpdate; multiCAFE replaces ROIs each time and refreshes the canvas.

Possible next steps:

- Align **displayON** with xMultiCAFE0: use **`(vList == null \|\| vList.isEmpty())`** for “create new viewer” so the condition matches (still creates in multiCAFE as long as we keep closing viewers).
- Optionally **stop closing auto-created viewers** when loading the **kymos** sequence only, so that displayON can reuse one viewer (with default canvas) and ROIs can appear as in xMultiCAFE0; that would require a kymos-specific load path or flag.
- Or rework **selectKymographImage** to be closer to xMultiCAFE0 (e.g. beginUpdate/endUpdate, set position, validate-only ROI path instead of full replace) and see if that restores ROI display with the current canvas.
