# Capillary tracking: template matching and Hough transform

Alternative tracking approach for fixed-size rigid capillaries, addressing occlusion (fly at tip) and illumination changes.

## Context

- **Object:** Fixed-size rigid capillaries (rectilinear).
- **Goal:** Track extremities of the capillary.
- **Difficulties:**
  - A living fly may temporarily sit near or over the tip (occlusion).
  - Background illumination may change.
  - Capillaries are rectilinear objects.

## Template matching

**Pros:** Well suited for rigid objects; straightforward.

**Cons:**
- Sensitive to illumination (unless NCC is used).
- Sensitive to occlusion: if the fly covers the tip, the template fails.

**Mitigations:** Use normalized cross-correlation (NCC) for illumination. Track both extremities separately so the top can still update when the tip is occluded (or extrapolate).

## Hough transform

**Pros:**
- Robust to local occlusions: a fly at the tip only removes part of the line; the line can still be detected from other points.
- Edge-based, so less sensitive to illumination.
- Geometrically natural for rectilinear objects.

**Cons:**
- Returns line parameters, not endpoints; endpoints need extra logic.
- May need a prior (previous position) to choose the right line in cluttered images.

## Suggested hybrid approach

1. **Use Hough to find the capillary axis**  
   - Detect the main line in the ROI around the previous position.  
   - Use the previous frame’s angle/position as a prior to pick the capillary line.

2. **Derive extremities from the line**  
   - Either find where the capillary ends along the line, or  
   - Use small templates at both ends, but constrain search along the line.

3. **Handle occlusion at the tip**  
   - If the tip template match is poor (low NCC), keep the previous tip or extrapolate from the axis and known length instead of trusting the match.

4. **Illumination robustness**  
   - Use edge-based inputs (gradient magnitude, Canny) for Hough and optionally for matching instead of raw intensity.

This makes the line the stable backbone, uses it to constrain extremity search, and degrades gracefully when one end is occluded.
