package plugins.fmp.multitools.experiment.cage;

/**
 * Where distance 0 is measured from along the cage / tube axis.
 * <p>
 * {@link #FIRST_SHORT_SIDE_AT_FIRST_VERTEX} and {@link #SECOND_SHORT_SIDE_OPPOSITE} use the long
 * axis through midpoints of the two short sides of a quadrilateral ROI, with the short side that
 * contains the <b>first stored polygon vertex</b> (index 0) as &quot;first&quot;. For
 * {@link plugins.kernel.roi.roi2d.ROI2DRectangle} the vertex order is top-left, top-right,
 * bottom-right, bottom-left of the axis-aligned bounds.
 * <p>
 * Legacy modes use only the axis-aligned bounding box and image Y (no tilt).
 */
public enum FlyPositionAxisReference {

	LEGACY_IMAGE_TOP("Image top (AABB, vertical)"),

	LEGACY_IMAGE_BOTTOM("Image bottom (AABB, vertical)"),

	FIRST_SHORT_SIDE_AT_FIRST_VERTEX("0 at first ROI corner side (short mid)"),

	SECOND_SHORT_SIDE_OPPOSITE("0 at opposite short side");

	private final String uiLabel;

	FlyPositionAxisReference(String uiLabel) {
		this.uiLabel = uiLabel;
	}

	public String getUiLabel() {
		return uiLabel;
	}

	public boolean isLegacyAabb() {
		return this == LEGACY_IMAGE_TOP || this == LEGACY_IMAGE_BOTTOM;
	}

	public boolean usesVertexOrderedLongAxis() {
		return this == FIRST_SHORT_SIDE_AT_FIRST_VERTEX || this == SECOND_SHORT_SIDE_OPPOSITE;
	}
}
