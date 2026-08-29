package plugins.fmp.multitools.tools.JComponents;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;

import plugins.fmp.multitools.service.CapillaryLengthDetectorOptions;

/**
 * Options shown before auto-measuring capillary lengths: experiment scope
 * (current only, or current through last in the browse list) and the detector
 * parameters that are safe to expose in the UI.
 */
public class CapillaryLengthOptionsDialog {

	private CapillaryLengthOptionsDialog() {
	}

	public static class Choice {
		public final boolean allFromCurrent;
		public final CapillaryLengthDetectorOptions options;

		public Choice(boolean allFromCurrent, CapillaryLengthDetectorOptions options) {
			this.allFromCurrent = allFromCurrent;
			this.options = options;
		}
	}

	/**
	 * @return the chosen scope and options, or {@code null} if the user cancelled
	 */
	public static Choice show(Component parent, String title, CapillaryLengthDetectorOptions defaults) {
		if (defaults == null)
			defaults = new CapillaryLengthDetectorOptions();

		JRadioButton currentButton = new JRadioButton("Current experiment", true);
		JRadioButton allFromCurrentButton = new JRadioButton("ALL (current to last)", false);
		currentButton.setToolTipText("Measure only the experiment selected in the browse list.");
		allFromCurrentButton.setToolTipText(
				"Measure from the selected experiment through the last one in the browse list. "
						+ "Reliable measures are applied automatically; a report is written to the log.");
		ButtonGroup scopeGroup = new ButtonGroup();
		scopeGroup.add(currentButton);
		scopeGroup.add(allFromCurrentButton);

		JPanel scopePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
		scopePanel.setBorder(BorderFactory.createTitledBorder("Experiments"));
		scopePanel.add(currentButton);
		scopePanel.add(allFromCurrentButton);

		JSpinner frameSpinner = new JSpinner(new SpinnerNumberModel(Math.max(0, defaults.frameIndex), 0, 999999, 1));
		JSpinner nFramesSpinner = new JSpinner(
				new SpinnerNumberModel(Math.max(1, defaults.nFramesAveraged), 1, 51, 2));
		JSpinner strideSpinner = new JSpinner(new SpinnerNumberModel(Math.max(1, defaults.frameStride), 1, 50, 1));
		JSpinner lengthMmSpinner = new JSpinner(
				new SpinnerNumberModel(defaults.physicalLengthMm > 0 ? defaults.physicalLengthMm : 32., 1., 200., 0.5));
		JLabel frameLabel = new JLabel("First frame");
		JLabel nFramesLabel = new JLabel("Frames combined");
		JLabel strideLabel = new JLabel("Frame stride");
		JLabel lengthMmLabel = new JLabel("Physical length (mm)");
		frameSpinner.setToolTipText("First camera frame used for the measurement.");
		nFramesSpinner.setToolTipText("Number of frames combined (median) before tip detection.");
		String strideTip = "<html>Spacing between the frames that are averaged.<br>"
				+ "Example with first frame 0, 7 frames combined, stride 3:<br>"
				+ "frames used = 0, 3, 6, 9, 12, 15, 18.<br>"
				+ "A larger stride means frames further apart in time,<br>"
				+ "so a fly that only sits briefly is less likely to affect the tips.</html>";
		strideSpinner.setToolTipText(strideTip);
		strideLabel.setToolTipText(strideTip);
		lengthMmSpinner.setToolTipText("Physical capillary length used to report mm/pixel.");
		frameLabel.setToolTipText(frameSpinner.getToolTipText());
		nFramesLabel.setToolTipText(nFramesSpinner.getToolTipText());
		lengthMmLabel.setToolTipText(lengthMmSpinner.getToolTipText());

		JPanel paramsPanel = new JPanel(new GridBagLayout());
		paramsPanel.setBorder(BorderFactory.createTitledBorder("Detection"));
		GridBagConstraints c = new GridBagConstraints();
		c.anchor = GridBagConstraints.WEST;
		c.insets = new Insets(2, 4, 2, 4);
		c.gridy = 0;
		c.gridx = 0;
		paramsPanel.add(frameLabel, c);
		c.gridx = 1;
		paramsPanel.add(frameSpinner, c);
		c.gridy = 1;
		c.gridx = 0;
		paramsPanel.add(nFramesLabel, c);
		c.gridx = 1;
		paramsPanel.add(nFramesSpinner, c);
		c.gridy = 2;
		c.gridx = 0;
		paramsPanel.add(strideLabel, c);
		c.gridx = 1;
		paramsPanel.add(strideSpinner, c);
		c.gridy = 3;
		c.gridx = 0;
		paramsPanel.add(lengthMmLabel, c);
		c.gridx = 1;
		paramsPanel.add(lengthMmSpinner, c);

		JPanel panel = new JPanel(new BorderLayout(4, 6));
		panel.add(scopePanel, BorderLayout.NORTH);
		panel.add(paramsPanel, BorderLayout.CENTER);

		int answer = JOptionPane.showConfirmDialog(parent, panel, title, JOptionPane.OK_CANCEL_OPTION,
				JOptionPane.PLAIN_MESSAGE);
		if (answer != JOptionPane.OK_OPTION)
			return null;

		CapillaryLengthDetectorOptions options = new CapillaryLengthDetectorOptions();
		options.frameIndex = ((Number) frameSpinner.getValue()).intValue();
		options.nFramesAveraged = ((Number) nFramesSpinner.getValue()).intValue();
		options.frameStride = ((Number) strideSpinner.getValue()).intValue();
		options.physicalLengthMm = ((Number) lengthMmSpinner.getValue()).doubleValue();
		return new Choice(allFromCurrentButton.isSelected(), options);
	}
}
