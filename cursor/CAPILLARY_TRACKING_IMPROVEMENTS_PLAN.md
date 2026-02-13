# Capillary tracking improvements plan

## 1. Integer vs double precision

**Status:** Already using double precision.

- `GaspardRigidRegistration.findTranslation2D` returns `Vector2d` (double) and uses sub-pixel parabolic fit.
- `ROI2DUtilities.translateROI(roi, double dx, double dy)` uses `Point2D.Double`.
- `applyTranslation2D` uses sub-pixel `AffineTransform` when shift is not near-integer.

**Action:** None.

---

## 2. Restart detection after sudden movement

**Problem:** Tracking performs poorly with sudden movement; user needs to relocate capillaries and restart from that frame.

**Solution:** Add "Restart from current frame" in Track capillaries dialog.

- Button (e.g. "Run tracking from current T") that sets `tStart = viewer.getPositionT()`.
- User moves to the frame where tracking failed, manually repositions the capillary, then runs tracking from that frame.
- Uses the corrected ROI at current T as the new seed.

**Status:** Done. "Run from current T" and "Run backwards from current T" added; backward uses seed at current T and fills down to From.

**Length check when running backwards:** After a jump the user corrects ROIs at e.g. t+4 but may have (1) altered length or (2) slightly misplaced vertical position. We now:
- **(1) Length:** Before running backwards, compare each capillary’s current ROI (path length and point count) to the reference at tCurr−1. If any differ beyond tolerance (2 px or 3%), a dialog offers: Cancel, Continue anyway, or **Normalize length and run** (resample current ROIs to reference point count via arc-length resampling). Implemented in `TrackCapillaries.runBackwardFromCurrentT` and `ROI2DUtilities.getPolylinePathLength` / `resamplePolylineToNPoints`.
- **(2) Vertical position:** No automatic correction yet. A possible future improvement is optional recentering: sample intensity in a narrow band perpendicular to the line and shift each point to the intensity centroid or peak (see also §4).

---

## 3. Constant capillary length for kymograph

**Problem:** Kymograph height varies if the user inadvertently changes capillary length when moving it. Need constant kymograph height over the whole record.

**Current:** Kymograph height comes from `buildMasks()` (number of points along capillary). Each AlongT ROI can have different length; max is used.

**Solutions:**

- **A. Reference length:** Store a reference length (e.g. from first interval or user setting). When building the kymograph, resample each AlongT ROI to that length (e.g. via `Level2D.expandPolylineToNewWidth`).
- **B. Constrain ROI editing:** When the user edits the ROI, keep line length fixed.
- **C. Post-processing:** After tracking, normalize ROI length to the reference before saving.

**Action:** Implement A (reference length + resample to fixed length during kymograph build).

---

## 4. Keep capillary line centered

**Problem:** Need to keep the capillary line centered over the capillary image.

**Ideas:**

- During tracking: Crop is already centered on the previous ROI. Optionally add a recentering step based on the capillary axis (e.g. centroid of a narrow band along the line).
- After tracking/editing: Optional correction step that recenters the line on the capillary axis.

**Action:** Add optional recentering post-processing (e.g. centroid or profile-based center along the line).
