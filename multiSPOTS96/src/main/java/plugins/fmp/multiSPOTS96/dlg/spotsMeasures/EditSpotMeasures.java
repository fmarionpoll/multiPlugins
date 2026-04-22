package plugins.fmp.multiSPOTS96.dlg.spotsMeasures;

import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

import plugins.fmp.multiSPOTS96.MultiSPOTS96;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.spot.Spot;
import plugins.fmp.multitools.tools.chart.ChartSpotsOverlayFrame;

public class EditSpotMeasures extends JPanel implements PropertyChangeListener {
	private static final long serialVersionUID = 2580935598417087197L;

	private enum SpotScopeMode {
		ALL, SINGLE
	}

	private static final class SpotScopeChoice {
		final SpotScopeMode mode;
		final String roiName;

		private SpotScopeChoice(SpotScopeMode mode, String roiName) {
			this.mode = Objects.requireNonNull(mode, "mode");
			this.roiName = roiName;
		}

		static SpotScopeChoice all() {
			return new SpotScopeChoice(SpotScopeMode.ALL, null);
		}

		static SpotScopeChoice singleRoi(String roiName) {
			return new SpotScopeChoice(SpotScopeMode.SINGLE, Objects.requireNonNull(roiName, "roiName"));
		}

		String selectionKey() {
			return mode == SpotScopeMode.ALL ? "ALL" : "ROI:" + roiName;
		}

		boolean matchesKey(String key) {
			return key != null && key.equals(selectionKey());
		}

		@Override
		public String toString() {
			if (mode == SpotScopeMode.ALL) {
				return "All spots";
			}
			return roiName;
		}
	}

	private MultiSPOTS96 parent0 = null;
	private JComboBox<SpotScopeChoice> spotScopeCombo = new JComboBox<>();
	private JButton rebuildSumCleanButton = new JButton("Rebuild sumClean");
	private JButton reconstructSumNoFlyButton = new JButton("Reconstruct sumNoFly + sumClean");
	private JLabel statusLabel = new JLabel(" ", SwingConstants.LEFT);

	void init(GridLayout capLayout, MultiSPOTS96 parent0) {
		setLayout(capLayout);
		this.parent0 = parent0;
		FlowLayout layoutLeft = new FlowLayout(FlowLayout.LEFT);
		layoutLeft.setVgap(0);

		JPanel panel1 = new JPanel(layoutLeft);
		panel1.add(new JLabel("Spots:"));
		panel1.add(spotScopeCombo);
		panel1.add(rebuildSumCleanButton);
		panel1.add(reconstructSumNoFlyButton);
		rebuildSumCleanButton.setToolTipText(
				"Only recomputes sumClean as the running median of the current sumNoFly (leaves sumIn and sumNoFly unchanged).");
		reconstructSumNoFlyButton.setToolTipText(
				"sumNoFly = extrapolate sumIn across flyPresent>0 bins; sumClean = running median of sumNoFly (same order as after detection).");
		add(panel1);

		JPanel panel2 = new JPanel(layoutLeft);
		panel2.add(statusLabel);
		add(panel2);

		Experiment exp = parent0 != null ? (Experiment) parent0.expListComboLazy.getSelectedItem() : null;
		refreshSpotScopeCombo(exp, false);

		defineListeners();
	}

	private void defineListeners() {
		rebuildSumCleanButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				onRebuildSumCleanClicked();
			}
		});
		reconstructSumNoFlyButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				onReconstructSumNoFlyClicked();
			}
		});
	}

	private static List<Spot> resolveSpotsForRoiNames(Experiment exp, List<String> roiNames) {
		if (exp == null || exp.getSpots() == null || roiNames == null || roiNames.isEmpty()) {
			return Collections.emptyList();
		}
		List<Spot> out = new ArrayList<>();
		for (String roiName : roiNames) {
			Spot s = exp.getCages().getSpotFromROIName(roiName, exp.getSpots());
			if (s != null) {
				out.add(s);
			}
		}
		return ChartSpotsOverlayFrame.dedupeSpots(out);
	}

	private void refreshSpotScopeCombo(Experiment exp, boolean keepSelection) {
		String previousKey = null;
		if (keepSelection) {
			SpotScopeChoice cur = (SpotScopeChoice) spotScopeCombo.getSelectedItem();
			if (cur != null) {
				previousKey = cur.selectionKey();
			}
		}

		spotScopeCombo.removeAllItems();
		spotScopeCombo.addItem(SpotScopeChoice.all());

		if (exp != null) {
			for (String roiName : SpotSequenceRois.spotRoiNamesFromSequence(exp)) {
				spotScopeCombo.addItem(SpotScopeChoice.singleRoi(roiName));
			}
		}

		if (previousKey != null) {
			for (int i = 0; i < spotScopeCombo.getItemCount(); i++) {
				SpotScopeChoice c = spotScopeCombo.getItemAt(i);
				if (c != null && c.matchesKey(previousKey)) {
					spotScopeCombo.setSelectedIndex(i);
					return;
				}
			}
		}
		spotScopeCombo.setSelectedIndex(0);
	}

	private List<Spot> resolveTargetsOrSetStatus(Experiment exp) {
		refreshSpotScopeCombo(exp, true);
		List<String> roiNames = SpotSequenceRois.spotRoiNamesFromSequence(exp);
		if (roiNames.isEmpty()) {
			statusLabel.setText("No spot ROIs on sequence (names starting with \"spot\", any case).");
			return Collections.emptyList();
		}
		SpotScopeChoice scope = (SpotScopeChoice) spotScopeCombo.getSelectedItem();
		List<Spot> targets;
		if (scope == null || scope.mode == SpotScopeMode.ALL) {
			targets = resolveSpotsForRoiNames(exp, roiNames);
		} else {
			Spot s = exp.getCages().getSpotFromROIName(scope.roiName, exp.getSpots());
			if (s == null) {
				statusLabel.setText("No experiment spot matches ROI \"" + scope.roiName + "\".");
				return Collections.emptyList();
			}
			targets = Collections.singletonList(s);
		}
		if (targets.isEmpty()) {
			statusLabel.setText("No spots linked to these sequence ROIs (names present but no cage match).");
			return Collections.emptyList();
		}
		return targets;
	}

	private void onRebuildSumCleanClicked() {
		if (parent0 == null) {
			return;
		}
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		if (exp == null || exp.getSpots() == null) {
			statusLabel.setText("No experiment.");
			return;
		}
		List<Spot> targets = resolveTargetsOrSetStatus(exp);
		if (targets.isEmpty()) {
			return;
		}
		exp.getSpots().rebuildSumCleanOnlyForSpots(targets);
		exp.getSpots().transferMeasuresToLevel2D(targets);
		statusLabel.setText("sumClean rebuilt for " + targets.size() + " spot(s).");
		refreshChartsIfPresent(exp);
	}

	private void onReconstructSumNoFlyClicked() {
		if (parent0 == null) {
			return;
		}
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		if (exp == null || exp.getSpots() == null) {
			statusLabel.setText("No experiment.");
			return;
		}
		List<Spot> targets = resolveTargetsOrSetStatus(exp);
		if (targets.isEmpty()) {
			return;
		}
		exp.getSpots().rebuildNoFlyAndCleanForSpots(targets, true);
		exp.getSpots().transferMeasuresToLevel2D(targets);
		statusLabel.setText("sumNoFly+sumClean rebuilt for " + targets.size() + " spot(s).");
		refreshChartsIfPresent(exp);
	}

	private void refreshChartsIfPresent(Experiment exp) {
		if (parent0.dlgMeasure != null && parent0.dlgMeasure.tabCharts != null) {
			parent0.dlgMeasure.tabCharts.displayChartPanels(exp);
		}
	}

	@Override
	public void propertyChange(PropertyChangeEvent evt) {
	}

}
