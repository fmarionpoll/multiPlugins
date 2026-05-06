package plugins.fmp.multitools.experiment.ui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Point;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;

import icy.gui.frame.IcyFrame;
import plugins.fmp.multitools.tools.JComponents.JComboBoxExperimentLazy;

public class TransferResultsPanel extends JPanel {
	private static final long serialVersionUID = 1L;

	private final JComboBoxExperimentLazy experimentsCombo;

	private final JButton openButton = new JButton("Open transfer dialog...");

	public TransferResultsPanel(JComboBoxExperimentLazy experimentsCombo, Object unusedParent) {
		this.experimentsCombo = experimentsCombo;
		buildUi();
		wireUi();
	}

	private void buildUi() {
		setLayout(new BorderLayout());
		JPanel content = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
		content.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
		content.add(new JLabel("Transfer analysis results to/from another location (server, external disk, etc)."));
		content.add(openButton);
		add(content, BorderLayout.NORTH);
	}

	private void wireUi() {
		openButton.addActionListener(e -> openDialogFrame());
	}

	private void openDialogFrame() {
		IcyFrame frame = new IcyFrame("Transfer results", true, true);
		frame.add(new TransferResultsDialogPanel(experimentsCombo), BorderLayout.CENTER);
		Point pt = getLocationOnScreenSafe();
		if (pt != null) {
			frame.setLocation(pt);
		}
		frame.pack();
		frame.addToDesktopPane();
		frame.setVisible(true);
		frame.requestFocus();
	}

	private Point getLocationOnScreenSafe() {
		try {
			return getLocationOnScreen();
		} catch (Exception e) {
			return null;
		}
	}
}

