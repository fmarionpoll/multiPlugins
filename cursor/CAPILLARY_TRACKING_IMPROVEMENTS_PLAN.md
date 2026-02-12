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

**Action:** Add button and wire to use viewer position as tStart.

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
