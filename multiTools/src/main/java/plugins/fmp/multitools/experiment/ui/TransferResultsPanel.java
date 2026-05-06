package plugins.fmp.multitools.experiment.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.Window;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import plugins.fmp.multitools.tools.JComponents.JComboBoxExperimentLazy;

public class TransferResultsPanel extends JPanel {
	private static final long serialVersionUID = 1L;

	private final JComboBoxExperimentLazy experimentsCombo;
	private final TransferResultsHost host;

	private final JButton openButton = new JButton("Transfer results...");

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
		Window owner = SwingUtilities.getWindowAncestor(this);
		JDialog dlg = new JDialog(owner, "Transfer results");
		dlg.setModal(false);
		dlg.setLayout(new BorderLayout());
		dlg.add(new TransferResultsDialogPanel(experimentsCombo, host, dlg::dispose), BorderLayout.CENTER);
		dlg.pack();
		Point pt = getPreferredDialogLocation(dlg.getSize(), owner);
		if (pt != null)
			dlg.setLocation(pt);
		dlg.setVisible(true);
	}

	private Point getPreferredDialogLocation(Dimension dialogSize, Window owner) {
		try {
			Point p = openButton.getLocationOnScreen();
			int x = p.x;
			int y = p.y + openButton.getHeight() + 6;

			int w = dialogSize != null ? dialogSize.width : 0;
			int h = dialogSize != null ? dialogSize.height : 0;

			Rectangle bounds = null;
			if (owner != null && owner.isShowing()) {
				Point ow = owner.getLocationOnScreen();
				bounds = new Rectangle(ow.x, ow.y, owner.getWidth(), owner.getHeight());
			} else if (openButton.getGraphicsConfiguration() != null) {
				Rectangle b = openButton.getGraphicsConfiguration().getBounds();
				Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(openButton.getGraphicsConfiguration());
				bounds = new Rectangle(b.x + insets.left, b.y + insets.top, b.width - insets.left - insets.right,
						b.height - insets.top - insets.bottom);
			} else {
				bounds = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
			}

			if (x + w > bounds.x + bounds.width)
				x = Math.max(bounds.x, bounds.x + bounds.width - w);
			if (x < bounds.x)
				x = bounds.x;

			if (y + h > bounds.y + bounds.height)
				y = Math.max(bounds.y, p.y - h - 6);
			if (y < bounds.y)
				y = bounds.y;

			return new Point(x, y);
		} catch (Exception e) {
			return null;
		}
	}
}
