package plugins.fmp.multiSPOTS96.canvas2D;

import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.logging.Logger;

import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JToolBar;

import icy.canvas.Canvas2D;
import icy.gui.component.button.IcyButton;
import icy.gui.viewer.Viewer;
import icy.image.IcyBufferedImage;
import icy.resource.icon.IcyIcon;
import icy.sequence.Sequence;
import plugins.fmp.multiSPOTS96.resource.ResourceUtilFMP;
import plugins.fmp.multitools.tools.imageTransform.ImageTransformEnums;
import plugins.fmp.multitools.tools.imageTransform.ImageTransformInterface;
import plugins.fmp.multitools.tools.imageTransform.CanvasImageTransformOptions;

/**
 * Enhanced Canvas2D implementation with dual-step image transformations.
 * 
 * <p>
 * This canvas provides a two-step transformation pipeline:
 * <ul>
 * <li>Step 1: Color channel and RGB operations</li>
 * <li>Step 2: Column sorting and arrangement operations</li>
 * </ul>
 * 
 * <p>
 * Key improvements:
 * <ul>
 * <li>Separated concerns with dedicated action handlers</li>
 * <li>Centralized constants management</li>
 * <li>Input validation and error handling</li>
 * <li>Better encapsulation with private fields</li>
 * <li>Comprehensive documentation</li>
 * </ul>
 * 
 * @author MultiSPOTS96 Team
 * @version 2.0
 */
public class Canvas2D_3Transforms extends Canvas2D {

	private static final long serialVersionUID = 8827595503996677250L;
	private static final Logger logger = Logger.getLogger(Canvas2D_3Transforms.class.getName());

	// UI Components - properly encapsulated
	private final JComboBox<ImageTransformEnums> transformsComboStep1;
	private final JComboBox<ImageTransformEnums> transformsComboStep2;

	// Transform state
	private ImageTransformInterface transformStep1;
	private ImageTransformInterface transformStep2;
	private final CanvasImageTransformOptions optionsStep1;
	private final CanvasImageTransformOptions optionsStep2;

	// Action handlers as inner classes for better organization
	private final TransformStep1Handler transformStep1Handler;
	private final TransformStep2Handler transformStep2Handler;
	private final ScalingHandler scalingHandler;
	private final NavigationHandler navigationHandler;

	/**
	 * Creates a new enhanced Canvas2D with transformation capabilities.
	 * 
	 * @param viewer The parent viewer
	 */
	public Canvas2D_3Transforms(Viewer viewer) {
		super(viewer);

		// Initialize transform options
		this.optionsStep1 = new CanvasImageTransformOptions();
		this.optionsStep2 = new CanvasImageTransformOptions();

		// Initialize UI components
		this.transformsComboStep1 = new JComboBox<>(Canvas2DConstants.DefaultTransforms.STEP1_TRANSFORMS);
		this.transformsComboStep2 = new JComboBox<>(Canvas2DConstants.DefaultTransforms.STEP2_TRANSFORMS);

		// Initialize transforms
		this.transformStep1 = ImageTransformEnums.NONE.getFunction();
		this.transformStep2 = ImageTransformEnums.NONE.getFunction();

		// Initialize action handlers
		this.transformStep1Handler = new TransformStep1Handler();
		this.transformStep2Handler = new TransformStep2Handler();
		this.scalingHandler = new ScalingHandler();
		this.navigationHandler = new NavigationHandler();
	}

	@Override
	public void customizeToolbar(JToolBar toolBar) {
		try {
			setupToolbarStep1(toolBar);
			super.customizeToolbar(toolBar);
		} catch (Exception e) {
			logger.severe(String.format(Canvas2DConstants.ErrorMessages.TOOLBAR_SETUP_FAILED, e.getMessage()));
		}
	}

	/**
	 * Sets up the first step of the toolbar with transform selection and scaling
	 * controls.
	 * 
	 * @param toolBar The toolbar to customize
	 */
	private void setupToolbarStep1(JToolBar toolBar) {
		// Remove default items
		for (int i = Canvas2DConstants.Toolbar.REMOVE_ITEMS_COUNT - 1; i >= 0; i--) {
			if (toolBar.getComponentCount() > i) {
				toolBar.remove(i);
			}
		}

		// Add step 1 components
		toolBar.addSeparator();
		toolBar.add(new JLabel(Canvas2DConstants.Toolbar.STEP1_LABEL));
		toolBar.add(transformsComboStep1);
		transformsComboStep1.setToolTipText(Canvas2DConstants.Toolbar.STEP1_TOOLTIP);

		// Add scaling buttons
		addScalingButtons(toolBar);

		// Add listeners
		transformsComboStep1.addActionListener(transformStep1Handler);
		transformsComboStep1.setSelectedIndex(0);

		refresh();
	}

	/**
	 * Adds scaling control buttons to the toolbar.
	 * 
	 * @param toolBar The toolbar to add buttons to
	 */
	private void addScalingButtons(JToolBar toolBar) {
		// Fit Y axis button
		IcyButton fitYAxisButton = createToolbarButton(ResourceUtilFMP.ICON_FIT_YAXIS,
				Canvas2DConstants.Toolbar.FIT_Y_AXIS_TOOLTIP, scalingHandler::fitYAxis);
		toolBar.add(fitYAxisButton);

		// Fit X and Y axis button
		IcyButton fitXYAxisButton = createToolbarButton(ResourceUtilFMP.ICON_FIT_XAXIS,
				Canvas2DConstants.Toolbar.FIT_XY_AXIS_TOOLTIP, scalingHandler::fitXYAxis);
		toolBar.add(fitXYAxisButton);
	}

	/**
	 * Creates a toolbar button with consistent styling and behavior.
	 * 
	 * @param icon    The button icon
	 * @param tooltip The tooltip text
	 * @param action  The action to perform when clicked
	 * @return The configured button
	 */
	private IcyButton createToolbarButton(IcyIcon icon, String tooltip, Runnable action) {
		IcyButton button = new IcyButton(icon);
		button.setSelected(false);
		button.setFocusable(false);
		button.setToolTipText(tooltip);
		button.addActionListener(e -> action.run());
		return button;
	}

	// Legacy methods kept for backward compatibility but deprecated
	@Deprecated
	void zoomImage_1_1() {
		scalingHandler.fitYAxis();
	}

	@Deprecated
	void shrinkImage_to_fit() {
		scalingHandler.fitXYAxis();
	}

	@Override
	public IcyBufferedImage getImage(int t, int z, int c) {
		try {
			IcyBufferedImage originalImage = super.getImage(t, z, c);
			if (originalImage == null) {
				return null;
			}

			// Apply step 1 transformation
			if (transformStep1 != null) {
				IcyBufferedImage step1Result = transformStep1.getTransformedImage(originalImage, optionsStep1);
				// Apply step 2 transformation if available
				if (transformStep2 != null) {
					return transformStep2.getTransformedImage(step1Result, optionsStep2);
				}
				return step1Result;
			}
			return super.getImage(t, z, c); // Fallback to original image

		} catch (Exception e) {
			logger.severe(String.format(Canvas2DConstants.ErrorMessages.IMAGE_TRANSFORM_FAILED, e.getMessage()));
			return super.getImage(t, z, c); // Fallback to original image
		}
	}

	/**
	 * Adds a transform to step 1 combo box if not already present.
	 * 
	 * @param transform The transform to add
	 * @return The index of the transform in the combo box
	 * @throws IllegalArgumentException if transform is null
	 */
	public int addTransformStep1(ImageTransformEnums transform) {
		if (transform == null) {
			throw new IllegalArgumentException(Canvas2DConstants.ErrorMessages.NULL_TRANSFORM_ENUM);
		}

		int indexFound = -1;
		for (int index = 0; index < transformsComboStep1.getItemCount(); index++) {
			if (transform == transformsComboStep1.getItemAt(index)) {
				indexFound = index;
				break;
			}
		}

		if (indexFound < 0) {
			transformsComboStep1.addItem(transform);
			transformsComboStep1.setSelectedItem(transform);
			indexFound = transformsComboStep1.getSelectedIndex();
		}
		return indexFound;
	}

	/**
	 * Updates the available transforms for step 1.
	 * 
	 * @param transforms Array of available transforms
	 */
	public void updateTransformsStep1(ImageTransformEnums[] transforms) {
		updateTransformsCombo(transforms, transformsComboStep1);
	}

	/**
	 * Updates the available transforms for step 2.
	 * 
	 * @param transforms Array of available transforms
	 */
	public void updateTransformsStep2(ImageTransformEnums[] transforms) {
		updateTransformsCombo(transforms, transformsComboStep2);
	}

	/**
	 * Updates a transform combo box with new options while preserving listeners.
	 * 
	 * @param transforms The new transform options
	 * @param comboBox   The combo box to update
	 */
	private void updateTransformsCombo(ImageTransformEnums[] transforms, JComboBox<ImageTransformEnums> comboBox) {
		// Temporarily remove listeners
		ActionListener[] listeners = comboBox.getActionListeners();
		for (ActionListener listener : listeners) {
			comboBox.removeActionListener(listener);
		}

		// Update contents
		comboBox.removeAllItems();
		comboBox.addItem(ImageTransformEnums.NONE);
		for (ImageTransformEnums transform : transforms) {
			comboBox.addItem(transform);
		}

		// Restore listeners
		for (ActionListener listener : listeners) {
			comboBox.addActionListener(listener);
		}
	}

	/**
	 * Gets the step 1 transform options.
	 * 
	 * @return Step 1 options
	 */
	public CanvasImageTransformOptions getOptionsStep1() {
		return optionsStep1;
	}

	/**
	 * Sets the step 1 transform options.
	 * 
	 * @param options The new options (can be null)
	 */
	public void setOptionsStep1(CanvasImageTransformOptions options) {
		if (options != null) {
			copyOptionsFields(options, this.optionsStep1);
		}
	}

	/**
	 * Gets the step 2 transform options.
	 * 
	 * @return Step 2 options
	 */
	public CanvasImageTransformOptions getOptionsStep2() {
		return optionsStep2;
	}

	/**
	 * Sets the step 2 transform options.
	 * 
	 * @param options The new options (can be null)
	 */
	public void setOptionsStep2(CanvasImageTransformOptions options) {
		if (options != null) {
			copyOptionsFields(options, this.optionsStep2);
		}
	}

	/**
	 * Sets the transform selection for step 1.
	 * 
	 * @param index   The transform index
	 * @param options Optional transform options
	 * @throws IllegalArgumentException If index is invalid
	 */
	public void setTransformStep1(int index, CanvasImageTransformOptions options) {
		validateTransformIndex(index, transformsComboStep1.getItemCount());
		transformsComboStep1.setSelectedIndex(index);
		if (options != null) {
			copyOptionsFields(options, this.optionsStep1);
		}
	}

	/**
	 * Sets the transform selection for step 1 by enum value.
	 * 
	 * @param transform The transform enum
	 * @param options   Optional transform options
	 * @throws IllegalArgumentException If transform is null
	 */
	public void setTransformStep1(ImageTransformEnums transform, CanvasImageTransformOptions options) {
		if (transform == null) {
			throw new IllegalArgumentException(Canvas2DConstants.ErrorMessages.NULL_TRANSFORM_ENUM);
		}
		transformsComboStep1.setSelectedItem(transform);
		if (options != null) {
			copyOptionsFields(options, this.optionsStep1);
		}
	}

	/**
	 * Sets the transform selection for step 1 by index without options.
	 * 
	 * @param index The transform index
	 * @throws IllegalArgumentException If index is invalid
	 */
	public void setTransformStep1Index(int index) {
		setTransformStep1(index, null);
	}

	/**
	 * Sets the transform selection for step 2 by index without options.
	 * 
	 * @param index The transform index
	 * @throws IllegalArgumentException If index is invalid
	 */
	public void setTransformStep2Index(int index) {
		setTransformStep2(index, null);
	}

	/**
	 * Gets the number of items in the step 1 transform combo box.
	 * 
	 * @return The item count
	 */
	public int getTransformStep1ItemCount() {
		return transformsComboStep1.getItemCount();
	}

	/**
	 * Gets the number of items in the step 2 transform combo box.
	 * 
	 * @return The item count
	 */
	public int getTransformStep2ItemCount() {
		return transformsComboStep2.getItemCount();
	}

	/**
	 * Sets the transform selection for step 2.
	 * 
	 * @param index   The transform index
	 * @param options Optional transform options
	 * @throws IllegalArgumentException If index is invalid
	 */
	public void setTransformStep2(int index, CanvasImageTransformOptions options) {
		validateTransformIndex(index, transformsComboStep2.getItemCount());
		transformsComboStep2.setSelectedIndex(index);
		if (options != null) {
			copyOptionsFields(options, this.optionsStep2);
		}
	}

	/**
	 * Sets the transform selection for step 2 by enum value.
	 * 
	 * @param transform The transform enum
	 * @param options   Optional transform options
	 * @throws IllegalArgumentException If transform is null
	 */
	public void setTransformStep2(ImageTransformEnums transform, CanvasImageTransformOptions options) {
		if (transform == null) {
			throw new IllegalArgumentException(Canvas2DConstants.ErrorMessages.NULL_TRANSFORM_ENUM);
		}
		transformsComboStep2.setSelectedItem(transform);
		if (options != null) {
			copyOptionsFields(options, this.optionsStep2);
		}
	}

	/**
	 * Validates a transform index.
	 * 
	 * @param index    The index to validate
	 * @param maxIndex The maximum valid index
	 * @throws IllegalArgumentException If index is invalid
	 */
	private void validateTransformIndex(int index, int maxIndex) {
		if (index < 0 || index >= maxIndex) {
			throw new IllegalArgumentException(
					String.format(Canvas2DConstants.ErrorMessages.INVALID_TRANSFORM_INDEX, index));
		}
	}

	/**
	 * Copies relevant fields from source options to target options. This method
	 * manually copies the fields since ImageTransformOptions doesn't have a copy
	 * method.
	 * 
	 * @param source The source options to copy from
	 * @param target The target options to copy to
	 */
	private void copyOptionsFields(CanvasImageTransformOptions source, CanvasImageTransformOptions target) {
		if (source == null || target == null) {
			return;
		}

		// Copy the most commonly used fields for canvas transforms
		target.transformOption = source.transformOption;
		target.backgroundImage = source.backgroundImage;
		target.secondImage = source.secondImage;
		target.copyResultsToThe3planes = source.copyResultsToThe3planes;
		target.simplethreshold = source.simplethreshold;
		target.background_delta = source.background_delta;
		target.background_jitter = source.background_jitter;
		target.colorthreshold = source.colorthreshold;
		target.colordistanceType = source.colordistanceType;
		target.ifGreater = source.ifGreater;
		target.colorarray = source.colorarray;

		// Copy region fields
		target.xfirst = source.xfirst;
		target.xlast = source.xlast;
		target.yfirst = source.yfirst;
		target.ylast = source.ylast;

		// Copy channel and weight fields
		target.channel0 = source.channel0;
		target.channel1 = source.channel1;
		target.channel2 = source.channel2;
		target.w0 = source.w0;
		target.w1 = source.w1;
		target.w2 = source.w2;
		target.spanDiff = source.spanDiff;

		// Note: npixels_changed is not copied as it's typically an output field
	}

	// Legacy methods kept for backward compatibility but deprecated
	@Deprecated
	public void selectImageTransformFunctionStep1(int iselected, CanvasImageTransformOptions options) {
		setTransformStep1(iselected, options);
	}

	@Deprecated
	public void selectImageTransformFunctionStep2(int iselected, CanvasImageTransformOptions options) {
		setTransformStep2(iselected, options);
	}

	@Deprecated
	public void selectIndexStep1(int iselected, CanvasImageTransformOptions options) {
		setTransformStep1(iselected, options);
	}

	@Deprecated
	public void selectItemStep1(ImageTransformEnums item, CanvasImageTransformOptions options) {
		setTransformStep1(item, options);
	}

	@Deprecated
	public void selectIndexStep2(int iselected, CanvasImageTransformOptions options) {
		setTransformStep2(iselected, options);
	}

	@Deprecated
	public void selectItemStep2(ImageTransformEnums item, CanvasImageTransformOptions options) {
		setTransformStep2(item, options);
	}

	// Additional legacy methods for backward compatibility
	@Deprecated
	public int addTransformsComboStep1(ImageTransformEnums transform) {
		return addTransformStep1(transform);
	}

	@Deprecated
	public void updateTransformsComboStep1(ImageTransformEnums[] transformArray) {
		updateTransformsStep1(transformArray);
	}

	@Deprecated
	public void updateTransformsComboStep2(ImageTransformEnums[] transformArray) {
		updateTransformsStep2(transformArray);
	}

	/**
	 * Sets up the second step of the toolbar with additional transform options and
	 * navigation.
	 * 
	 * @param toolBar The toolbar to customize
	 */
	public void customizeToolbarStep2(JToolBar toolBar) {
		try {
			addNavigationButtons(toolBar);
			super.customizeToolbar(toolBar);
			addStep2Controls(toolBar);
		} catch (Exception e) {
			logger.severe(String.format(Canvas2DConstants.ErrorMessages.TOOLBAR_SETUP_FAILED, e.getMessage()));
		}
	}

	/**
	 * Adds navigation buttons to the toolbar.
	 * 
	 * @param toolBar The toolbar to add buttons to
	 */
	private void addNavigationButtons(JToolBar toolBar) {
		toolBar.addSeparator();

		// Previous button
		IcyButton previousButton = createToolbarButton(ResourceUtilFMP.ICON_PREVIOUS_IMAGE,
				Canvas2DConstants.Toolbar.PREVIOUS_TOOLTIP, navigationHandler::goToPrevious);
		toolBar.add(previousButton, Canvas2DConstants.Toolbar.PREVIOUS_BUTTON_POSITION);

		// Next button
		IcyButton nextButton = createToolbarButton(ResourceUtilFMP.ICON_NEXT_IMAGE,
				Canvas2DConstants.Toolbar.NEXT_TOOLTIP, navigationHandler::goToNext);
		toolBar.add(nextButton, Canvas2DConstants.Toolbar.NEXT_BUTTON_POSITION);
	}

	/**
	 * Adds step 2 transform controls to the toolbar.
	 * 
	 * @param toolBar The toolbar to add controls to
	 */
	private void addStep2Controls(JToolBar toolBar) {
		toolBar.add(new JLabel(Canvas2DConstants.Toolbar.STEP2_LABEL), Canvas2DConstants.Toolbar.STEP2_LABEL_POSITION);
		toolBar.add(transformsComboStep2, Canvas2DConstants.Toolbar.STEP2_COMBO_POSITION);

		transformsComboStep2.setToolTipText(Canvas2DConstants.Toolbar.STEP2_TOOLTIP);
		transformsComboStep2.addActionListener(transformStep2Handler);
	}

	/**
	 * Sets the reference image for step 1 transformations.
	 * 
	 * @param referenceImage The reference image
	 */
	public void setReferenceImage(IcyBufferedImage referenceImage) {
		optionsStep1.backgroundImage = referenceImage;
	}

	// Legacy method kept for backward compatibility but deprecated
	@Deprecated
	public void setTransformStep1ReferenceImage(IcyBufferedImage refImage) {
		setReferenceImage(refImage);
	}

	// Action handler inner classes

	/**
	 * Handles step 1 transform selection changes.
	 */
	private class TransformStep1Handler implements ActionListener {
		@Override
		public void actionPerformed(ActionEvent e) {
			ImageTransformEnums selectedTransform = (ImageTransformEnums) transformsComboStep1.getSelectedItem();
			if (selectedTransform != null) {
				optionsStep1.transformOption = selectedTransform;
				transformStep1 = selectedTransform.getFunction();
				refresh();
			}
		}
	}

	/**
	 * Handles step 2 transform selection changes.
	 */
	private class TransformStep2Handler implements ActionListener {
		@Override
		public void actionPerformed(ActionEvent e) {
			ImageTransformEnums selectedTransform = (ImageTransformEnums) transformsComboStep2.getSelectedItem();
			if (selectedTransform != null) {
				transformStep2 = selectedTransform.getFunction();
				refresh();
			}
		}
	}

	/**
	 * Handles image scaling operations.
	 */
	private class ScalingHandler {

		/**
		 * Fits the image to Y axis with 1:1 scale ratio.
		 */
		public void fitYAxis() {
			try {
				Sequence sequence = getSequence();
				if (sequence == null)
					return;

				Rectangle imageRect = sequence.getBounds2D();
				Rectangle canvasRect = getCanvasVisibleRect();

				double scaleY = canvasRect.getHeight() / imageRect.getHeight();
				double scaleX = scaleY;

				int offsetX = (int) (canvasRect.width / getScaleX() / Canvas2DConstants.Scaling.CENTER_RATIO);
				setMouseImagePos(offsetX, imageRect.height / 2);
				setScale(scaleX, scaleY, true, true);

			} catch (Exception e) {
				logger.warning("Failed to fit Y axis: " + e.getMessage());
			}
		}

		/**
		 * Fits the image to both X and Y axes.
		 */
		public void fitXYAxis() {
			try {
				Sequence sequence = getSequence();
				if (sequence == null)
					return;

				Rectangle imageRect = sequence.getBounds2D();
				Rectangle canvasRect = getCanvasVisibleRect();

				double scaleX = canvasRect.getWidth() / imageRect.getWidth();
				double scaleY = canvasRect.getHeight() / imageRect.getHeight();

				setMouseImagePos(imageRect.width / 2, imageRect.height / 2);
				setScale(scaleX, scaleY, true, true);

			} catch (Exception e) {
				logger.warning("Failed to fit XY axis: " + e.getMessage());
			}
		}
	}

	/**
	 * Handles navigation operations.
	 */
	private class NavigationHandler {

		/**
		 * Navigates to the previous time point.
		 */
		public void goToPrevious() {
			int currentT = getPositionT();
			if (currentT > 0) {
				setPositionT(currentT - 1);
			}
		}

		/**
		 * Navigates to the next time point.
		 */
		public void goToNext() {
			int currentT = getPositionT();
			setPositionT(currentT + 1);
		}
	}
}
