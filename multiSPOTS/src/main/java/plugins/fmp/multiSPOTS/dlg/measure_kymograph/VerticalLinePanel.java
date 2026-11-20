package plugins.fmp.multiSPOTS.dlg.measure_kymograph;

import java.awt.Dialog;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.Point;
import java.awt.Window;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

/**
 * One tab for the vertical-line kymograph: build the cage TIFF, then measure it.
 * Each button opens its controls in a modeless window over this dialog.
 */
public class VerticalLinePanel extends JPanel {

	private static final long serialVersionUID = 1L;

	private static final String TIP_BUILD = "Open the controls that build a cage kymograph from a vertical line through each spot.";
	private static final String TIP_MEASURE = "Open the controls that measure that vertical-line kymograph.";

	private final BuildPanel buildPanel;
	private final AnalysisPanel analysisPanel;
	private final JButton buildButton = new JButton("Build kymographs");
	private final JButton measureButton = new JButton("Measure");

	private JDialog buildDialog;
	private JDialog measureDialog;

	public VerticalLinePanel(BuildPanel buildPanel, AnalysisPanel analysisPanel) {
		super(new FlowLayout(FlowLayout.LEFT));
		this.buildPanel = buildPanel;
		this.analysisPanel = analysisPanel;
		buildButton.setToolTipText(TIP_BUILD);
		measureButton.setToolTipText(TIP_MEASURE);
		add(buildButton);
		add(measureButton);
		buildButton.addActionListener(e -> showDialog(true));
		measureButton.addActionListener(e -> showDialog(false));
	}

	private void showDialog(boolean build) {
		JDialog dialog = build ? buildDialog() : measureDialog();
		dialog.setVisible(true);
		dialog.toFront();
	}

	private JDialog buildDialog() {
		if (buildDialog == null || !buildDialog.isDisplayable()) {
			buildDialog = floating("Build kymographs", buildPanel, 0, 0);
		}
		return buildDialog;
	}

	private JDialog measureDialog() {
		if (measureDialog == null || !measureDialog.isDisplayable()) {
			measureDialog = floating("Measure kymograph", analysisPanel, 24, 24);
		}
		return measureDialog;
	}

	private JDialog floating(String title, JPanel body, int offsetX, int offsetY) {
		Window owner = SwingUtilities.getWindowAncestor(this);
		JDialog dialog;
		if (owner != null) {
			dialog = new JDialog(owner, title, Dialog.ModalityType.MODELESS);
		} else {
			dialog = new JDialog((Frame) null, title);
			dialog.setModal(false);
		}
		dialog.setContentPane(body);
		dialog.setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
		dialog.pack();
		dialog.setLocationRelativeTo(this.isShowing() ? this : owner);
		if (offsetX != 0 || offsetY != 0) {
			Point p = dialog.getLocation();
			dialog.setLocation(p.x + offsetX, p.y + offsetY);
		}
		return dialog;
	}
}