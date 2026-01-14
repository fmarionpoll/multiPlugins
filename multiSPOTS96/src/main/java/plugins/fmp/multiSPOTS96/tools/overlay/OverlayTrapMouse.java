package plugins.fmp.multiSPOTS96.tools.overlay;

import java.awt.Color;
import java.awt.Point;
import java.awt.event.MouseEvent;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.JButton;
import javax.swing.JComboBox;

import icy.canvas.IcyCanvas;
import icy.canvas.IcyCanvas2D;
import icy.image.IcyBufferedImage;
import icy.painter.Overlay;
import icy.painter.OverlayListener;
import icy.sequence.Sequence;
import icy.type.point.Point5D;

/**
 * Mouse interaction overlay that captures mouse events and extracts color
 * information from images. This overlay can be used to pick colors from images
 * and update UI components with the selected colors.
 * 
 * <p>
 * The overlay supports:
 * <ul>
 * <li>Color picking on mouse click</li>
 * <li>Real-time color display on mouse movement</li>
 * <li>Integration with Swing UI components (JButton and JComboBox)</li>
 * <li>Automatic color collection and management</li>
 * </ul>
 * 
 * <p>
 * Usage modes:
 * <ul>
 * <li>Simple overlay for mouse event capture</li>
 * <li>UI-integrated overlay for color picking</li>
 * <li>Listener-based overlay for custom event handling</li>
 * </ul>
 * 
 * @author MultiSPOTS96
 */
public class OverlayTrapMouse extends Overlay {

	/** Logger for this class */
	private static final Logger LOGGER = Logger.getLogger(OverlayTrapMouse.class.getName());

	/** Default overlay name */
	private static final String DEFAULT_OVERLAY_NAME = "Mouse Trap Overlay";

	/** Overlay name for UI integration */
	private static final String UI_OVERLAY_NAME = "Color Picker Overlay";

	/** Color component shift for red channel */
	private static final int RED_SHIFT = 16;

	/** Color component shift for green channel */
	private static final int GREEN_SHIFT = 8;

	/** Color component shift for blue channel */
	private static final int BLUE_SHIFT = 0;

	/** Color component mask */
	private static final int COLOR_MASK = 0xFF;

	/** Color component separator for display */
	private static final String COLOR_SEPARATOR = ":";

	/** Default button background color */
	private static final Color DEFAULT_BUTTON_COLOR = Color.LIGHT_GRAY;

	/** Current mouse position */
	private Point5D.Double currentPoint;

	/** Button for color picking UI */
	private JButton pickColorButton;

	/** Combo box for color selection UI */
	private JComboBox<Color> colorPickComboBox;

	/** Original text of the pick color button */
	private String originalButtonText;

	/**
	 * Creates a simple overlay for mouse event capture.
	 */
	public OverlayTrapMouse() {
		super(DEFAULT_OVERLAY_NAME);
	}

	/**
	 * Creates an overlay with UI integration for color picking.
	 * 
	 * @param pickColorButton   the button to update with picked colors
	 * @param colorPickComboBox the combo box to populate with colors
	 * @throws IllegalArgumentException if either parameter is null
	 */
	public OverlayTrapMouse(JButton pickColorButton, JComboBox<Color> colorPickComboBox) {
		super(UI_OVERLAY_NAME);
		attachInterfaceElements(pickColorButton, colorPickComboBox);
	}

	/**
	 * Creates an overlay with a custom overlay listener.
	 * 
	 * @param listener the overlay listener to attach
	 * @throws IllegalArgumentException if listener is null
	 */
	public OverlayTrapMouse(OverlayListener listener) {
		super(DEFAULT_OVERLAY_NAME);
		if (listener == null) {
			throw new IllegalArgumentException("Overlay listener cannot be null");
		}
		addOverlayListener(listener);
	}

	/**
	 * Attaches UI elements for color picking functionality.
	 * 
	 * @param pickColorButton   the button to update with picked colors
	 * @param colorPickComboBox the combo box to populate with colors
	 * @throws IllegalArgumentException if either parameter is null
	 */
	public void attachInterfaceElements(JButton pickColorButton, JComboBox<Color> colorPickComboBox) {
		if (pickColorButton == null) {
			throw new IllegalArgumentException("Pick color button cannot be null");
		}
		if (colorPickComboBox == null) {
			throw new IllegalArgumentException("Color pick combo box cannot be null");
		}

		this.pickColorButton = pickColorButton;
		this.colorPickComboBox = colorPickComboBox;
		this.originalButtonText = pickColorButton.getText();

//        LOGGER.info("UI elements attached to overlay");
	}

	/**
	 * Detaches UI elements from the overlay.
	 */
	public void detachInterfaceElements() {
		if (pickColorButton != null && originalButtonText != null) {
			pickColorButton.setText(originalButtonText);
			pickColorButton.setBackground(DEFAULT_BUTTON_COLOR);
		}

		this.pickColorButton = null;
		this.colorPickComboBox = null;
		this.originalButtonText = null;

//        LOGGER.info("UI elements detached from overlay");
	}

	/**
	 * Checks if UI elements are attached.
	 * 
	 * @return true if UI elements are attached, false otherwise
	 */
	public boolean hasUIElements() {
		return pickColorButton != null && colorPickComboBox != null;
	}

	@Override
	public void mouseClick(MouseEvent event, Point5D.Double imagePoint, IcyCanvas canvas) {
		if (event == null || imagePoint == null || canvas == null) {
			return;
		}

		if (!(canvas instanceof IcyCanvas2D)) {
			return;
		}

		try {
			onMouseClicked(canvas.getSequence(), canvas.getPositionT(), imagePoint);
		} catch (Exception e) {
			LOGGER.log(Level.WARNING, "Error handling mouse click", e);
		}
	}

	@Override
	public void mouseMove(MouseEvent event, Point5D.Double imagePoint, IcyCanvas canvas) {
		if (event == null || imagePoint == null || canvas == null) {
			return;
		}

		if (!(canvas instanceof IcyCanvas2D)) {
			return;
		}

		try {
			onMouseMoved(canvas.getSequence(), canvas.getPositionT(), imagePoint);
		} catch (Exception e) {
			LOGGER.log(Level.WARNING, "Error handling mouse move", e);
		}
	}

	/**
	 * Handles mouse click events by picking colors and updating UI components.
	 * 
	 * @param sequence     the sequence containing the image
	 * @param timePosition the time position in the sequence
	 * @param imagePoint   the clicked point in image coordinates
	 */
	private void onMouseClicked(Sequence sequence, int timePosition, Point5D.Double imagePoint) {
		if (sequence == null || imagePoint == null) {
			return;
		}

		Color pickedColor = getColorAtPoint(sequence, timePosition, imagePoint);
		if (pickedColor != null && hasUIElements()) {
			updateUIWithColor(pickedColor);
		}
	}

	/**
	 * Handles mouse movement events by updating the button display with current
	 * color.
	 * 
	 * @param sequence     the sequence containing the image
	 * @param timePosition the time position in the sequence
	 * @param imagePoint   the current mouse position in image coordinates
	 */
	private void onMouseMoved(Sequence sequence, int timePosition, Point5D.Double imagePoint) {
		if (sequence == null || imagePoint == null) {
			return;
		}

		Color currentColor = getColorAtPoint(sequence, timePosition, imagePoint);
		if (currentColor != null && pickColorButton != null) {
			updateButtonDisplay(currentColor);
		}
	}

	/**
	 * Updates UI components with the picked color.
	 * 
	 * @param pickedColor the color that was picked
	 */
	private void updateUIWithColor(Color pickedColor) {
		if (pickedColor == null) {
			return;
		}

		try {
			// Update button background
			pickColorButton.setBackground(pickedColor);

			// Find or add color to combo box
			int selectedIndex = findOrAddColorToComboBox(pickedColor);
			if (selectedIndex >= 0) {
				colorPickComboBox.setSelectedIndex(selectedIndex);
			}

			// Reset button appearance
			pickColorButton.setBackground(DEFAULT_BUTTON_COLOR);
			if (originalButtonText != null) {
				pickColorButton.setText(originalButtonText);
			}

		} catch (Exception e) {
			LOGGER.log(Level.WARNING, "Error updating UI with color", e);
		}
	}

	/**
	 * Finds an existing color in the combo box or adds it if not found.
	 * 
	 * @param color the color to find or add
	 * @return the index of the color in the combo box, or -1 if an error occurred
	 */
	private int findOrAddColorToComboBox(Color color) {
		if (color == null || colorPickComboBox == null) {
			return -1;
		}

		try {
			// Search for existing color
			for (int i = 0; i < colorPickComboBox.getItemCount(); i++) {
				Color existingColor = colorPickComboBox.getItemAt(i);
				if (color.equals(existingColor)) {
					return i;
				}
			}

			// Add new color if not found
			colorPickComboBox.addItem(color);
			return colorPickComboBox.getItemCount() - 1;

		} catch (Exception e) {
			LOGGER.log(Level.WARNING, "Error managing color in combo box", e);
			return -1;
		}
	}

	/**
	 * Updates the button display with the current color information.
	 * 
	 * @param color the color to display
	 */
	private void updateButtonDisplay(Color color) {
		if (color == null || pickColorButton == null) {
			return;
		}

		try {
			pickColorButton.setBackground(color);

			// Create RGB display string
			StringBuilder colorText = new StringBuilder();
			colorText.append(color.getRed()).append(COLOR_SEPARATOR).append(color.getGreen()).append(COLOR_SEPARATOR)
					.append(color.getBlue());

			pickColorButton.setText(colorText.toString());

		} catch (Exception e) {
			LOGGER.log(Level.WARNING, "Error updating button display", e);
		}
	}

	/**
	 * Extracts the RGB color at the specified point in the image.
	 * 
	 * @param sequence     the sequence containing the image
	 * @param timePosition the time position in the sequence
	 * @param imagePoint   the point to sample
	 * @return the color at the specified point, or null if not available
	 */
	private Color getColorAtPoint(Sequence sequence, int timePosition, Point5D.Double imagePoint) {
		if (sequence == null || imagePoint == null) {
			return null;
		}

		try {
			int x = (int) imagePoint.getX();
			int y = (int) imagePoint.getY();

			// Update current point
			setCurrentPoint(imagePoint);

			// Get image at specified time
			IcyBufferedImage image = sequence.getImage(timePosition, 0);
			if (image == null) {
				LOGGER.warning("No image available at time position: " + timePosition);
				return null;
			}

			// Check if point is within image bounds
			if (!image.isInside(new Point(x, y))) {
				return null;
			}

			// Extract RGB components
			int argb = image.getRGB(x, y);
			int red = (argb >> RED_SHIFT) & COLOR_MASK;
			int green = (argb >> GREEN_SHIFT) & COLOR_MASK;
			int blue = (argb >> BLUE_SHIFT) & COLOR_MASK;

			return new Color(red, green, blue);

		} catch (Exception e) {
			LOGGER.log(Level.WARNING, "Error extracting color at point", e);
			return null;
		}
	}

	/**
	 * Gets the current mouse position.
	 * 
	 * @return the current mouse position or null if not set
	 */
	public Point5D.Double getCurrentPoint() {
		return currentPoint;
	}

	/**
	 * Sets the current mouse position.
	 * 
	 * @param point the new current position
	 */
	private void setCurrentPoint(Point5D.Double point) {
		this.currentPoint = point;
	}

	/**
	 * Gets the attached pick color button.
	 * 
	 * @return the pick color button or null if not attached
	 */
	public JButton getPickColorButton() {
		return pickColorButton;
	}

	/**
	 * Gets the attached color pick combo box.
	 * 
	 * @return the color pick combo box or null if not attached
	 */
	public JComboBox<Color> getColorPickComboBox() {
		return colorPickComboBox;
	}

	/**
	 * Gets the original button text.
	 * 
	 * @return the original button text or null if not set
	 */
	public String getOriginalButtonText() {
		return originalButtonText;
	}
}
