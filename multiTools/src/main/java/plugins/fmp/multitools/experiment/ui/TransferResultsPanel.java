package plugins.fmp.multitools.experiment.ui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;

import icy.gui.frame.IcyFrame;
import plugins.fmp.multitools.tools.JComponents.JComboBoxExperimentLazy;

public class TransferResultsPanel extends JPanel {
	private static final long serialVersionUID = 1L;

	private final JComboBoxExperimentLazy experimentsCombo;
	private final TransferResultsHost host;

	private final JButton openButton = new JButton("Open transfer dialog...");

	public TransferResultsPanel(JComboBoxExperimentLazy experimentsCombo, TransferResultsHost host) {
		this.experimentsCombo = experimentsCombo;
		this.host = host;
		buildUi();
		wireUi();
	}

	private void buildUi() {
		GridLayout capLayout = new GridLayout(3, 2);
		setLayout(capLayout);

		// setLayout(new BorderLayout());
		FlowLayout flowLayout0 = new FlowLayout(FlowLayout.LEFT);
		flowLayout0.setVgap(0);

		// JPanel panel01 = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
		// panel01.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
		JPanel panel0 = new JPanel(flowLayout0);
		panel0.add(new JLabel("Transfer analysis results to/from another location (server, external disk, etc)."));
		add(panel0);

		JPanel panel1 = new JPanel(flowLayout0);
		panel1.add(openButton);
		add(panel1);
	}

	private void wireUi() {
		openButton.addActionListener(e -> openDialogFrame());
	}

	private void openDialogFrame() {
		IcyFrame frame = new IcyFrame("Transfer results", true, true);
		frame.add(new TransferResultsDialogPanel(experimentsCombo, host, frame), BorderLayout.CENTER);
		frame.pack();
		Point pt = getPreferredDialogLocation(frame);
		if (pt != null)
			frame.setLocation(pt);
		frame.addToDesktopPane();
		frame.setVisible(true);
		frame.requestFocus();
	}

	private Point getPreferredDialogLocation(IcyFrame frame) {
		try {
			Point p = openButton.getLocationOnScreen();
			int x = p.x;
			int y = p.y + openButton.getHeight() + 6;

			Rectangle screen = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
			int w = frame.getWidth();
			int h = frame.getHeight();

			if (x + w > screen.x + screen.width)
				x = Math.max(screen.x, screen.x + screen.width - w);
			if (y + h > screen.y + screen.height)
				y = Math.max(screen.y, p.y - h - 6);

			return new Point(x, y);
		} catch (Exception e) {
			return null;
		}
	}
}
