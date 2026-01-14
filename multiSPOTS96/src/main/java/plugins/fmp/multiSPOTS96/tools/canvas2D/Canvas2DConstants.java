package plugins.fmp.multiSPOTS96.tools.canvas2D;

import plugins.fmp.multiSPOTS96.tools.imageTransform.ImageTransformEnums;

/**
 * Constants used throughout the Canvas2D subsystem. Centralizes configuration
 * values to improve maintainability.
 * 
 * @author MultiSPOTS96 Team
 * @version 1.0
 */
public final class Canvas2DConstants {

	// Toolbar configuration
	public static final class Toolbar {
		public static final int REMOVE_ITEMS_COUNT = 4;
		public static final int PREVIOUS_BUTTON_POSITION = 0;
		public static final int NEXT_BUTTON_POSITION = 1;
		public static final int STEP2_LABEL_POSITION = 6;
		public static final int STEP2_COMBO_POSITION = 7;

		// Tooltip texts
		public static final String STEP1_TOOLTIP = "transform image step 1";
		public static final String STEP2_TOOLTIP = "transform image step 2";
		public static final String FIT_Y_AXIS_TOOLTIP = "Set image scale ratio to 1:1 and fit Y axis to the window height";
		public static final String FIT_XY_AXIS_TOOLTIP = "Fit X and Y axis to the window size";
		public static final String PREVIOUS_TOOLTIP = "Previous";
		public static final String NEXT_TOOLTIP = "Next";

		// Labels
		public static final String STEP1_LABEL = "step1";
		public static final String STEP2_LABEL = "step2";

		private Toolbar() {
			// Prevent instantiation
		}
	}

	// Default transform options
	public static final class DefaultTransforms {

		// Step 1 transforms - RGB and color channel operations
		public static final ImageTransformEnums[] STEP1_TRANSFORMS = { ImageTransformEnums.NONE,
				ImageTransformEnums.R_RGB, ImageTransformEnums.G_RGB, ImageTransformEnums.B_RGB,
				ImageTransformEnums.R2MINUS_GB, ImageTransformEnums.G2MINUS_RB, ImageTransformEnums.B2MINUS_RG,
				ImageTransformEnums.RGB, ImageTransformEnums.GBMINUS_2R, ImageTransformEnums.RBMINUS_2G,
				ImageTransformEnums.RGMINUS_2B, ImageTransformEnums.RGB_DIFFS, ImageTransformEnums.H_HSB,
				ImageTransformEnums.S_HSB, ImageTransformEnums.B_HSB, ImageTransformEnums.DERICHE,
				ImageTransformEnums.DERICHE_COLOR };

		// Step 2 transforms - Column sorting operations
		public static final ImageTransformEnums[] STEP2_TRANSFORMS = { ImageTransformEnums.NONE,
				ImageTransformEnums.SORT_SUMDIFFCOLS, ImageTransformEnums.SORT_CHAN0COLS };

		private DefaultTransforms() {
			// Prevent instantiation
		}
	}

	// Image scaling constants
	public static final class Scaling {
		public static final double CENTER_RATIO = 2.0;

		private Scaling() {
			// Prevent instantiation
		}
	}

	// Error messages
	public static final class ErrorMessages {
		public static final String INVALID_TRANSFORM_INDEX = "Invalid transform index: %d";
		public static final String NULL_TRANSFORM_ENUM = "Transform enum cannot be null";
		public static final String TOOLBAR_SETUP_FAILED = "Failed to setup toolbar: %s";
		public static final String IMAGE_TRANSFORM_FAILED = "Image transformation failed: %s";

		private ErrorMessages() {
			// Prevent instantiation
		}
	}

	private Canvas2DConstants() {
		// Prevent instantiation
	}
}