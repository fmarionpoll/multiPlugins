package plugins.fmp.multiSPOTS96.tools.ROI2D;

/**
 * Exception thrown when geometric operations fail.
 */
public class GeometryException extends ROI2DException {

	private static final long serialVersionUID = 1L;

	private final String geometryOperation;

	public GeometryException(String geometryOperation, String message) {
		super(String.format("Geometry operation '%s' failed: %s", geometryOperation, message));
		this.geometryOperation = geometryOperation;
	}

	public GeometryException(String geometryOperation, String message, Throwable cause) {
		super(String.format("Geometry operation '%s' failed: %s", geometryOperation, message), cause);
		this.geometryOperation = geometryOperation;
	}

	public String getGeometryOperation() {
		return geometryOperation;
	}
}
