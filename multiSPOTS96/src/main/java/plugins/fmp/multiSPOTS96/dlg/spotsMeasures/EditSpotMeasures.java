package plugins.fmp.multiSPOTS96.dlg.spotsMeasures;

import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

import plugins.fmp.multiSPOTS96.MultiSPOTS96;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.spot.Spot;
import plugins.fmp.multitools.tools.chart.ChartSpotsOverlayFrame;

public class EditSpotMeasures extends JPanel implements PropertyChangeListener {
	private static final long serialVersionUID = 2580935598417087197L;

	private MultiSPOTS96 parent0 = null;
	private JButton rebuildButton = new JButton("Rebuild no-fly + clean (seq spot ROIs)");
	private JCheckBox onlyIfSumNoFlyEmptyCheckBox = new JCheckBox("Only if sumNoFly empty", false);
	private JLabel statusLabel = new JLabel(" ", SwingConstants.LEFT);

	void init(GridLayout capLayout, MultiSPOTS96 parent0) {
		setLayout(capLayout);
		this.parent0 = parent0;
		FlowLayout layoutLeft = new FlowLayout(FlowLayout.LEFT);
		layoutLeft.setVgap(0);

		JPanel panel1 = new JPanel(layoutLeft);
		panel1.add(rebuildButton);
		panel1.add(onlyIfSumNoFlyEmptyCheckBox);
		add(panel1);

		JPanel panel2 = new JPanel(layoutLeft);
		panel2.add(statusLabel);
		add(panel2);

		defineListeners();
	}

	private void defineListeners() {
		rebuildButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				onRebuildClicked();
			}
		});
	}

	private void onRebuildClicked() {
		if (parent0 == null) {
			return;
		}
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		if (exp == null || exp.getSpots() == null) {
			statusLabel.setText("No experiment.");
			return;
		}
		List<Spot> spots = ChartSpotsOverlayFrame.dedupeSpots(SpotSequenceRois.allSpotsFromSequence(exp));
		if (spots.isEmpty()) {
			statusLabel.setText("No spot ROIs on sequence.");
			return;
		}
		boolean force = !onlyIfSumNoFlyEmptyCheckBox.isSelected();
		exp.getSpots().rebuildNoFlyAndCleanForSpots(spots, force);
		exp.getSpots().transferMeasuresToLevel2D(spots);
		statusLabel.setText("Updated " + spots.size() + " spot(s).");
		if (parent0.dlgMeasure != null && parent0.dlgMeasure.tabCharts != null) {
			parent0.dlgMeasure.tabCharts.displayChartPanels(exp);
		}
	}

	@Override
	public void propertyChange(PropertyChangeEvent evt) {
	}

}
