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
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;

import plugins.fmp.multiSPOTS96.MultiSPOTS96;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.spot.Spot;
import plugins.fmp.multitools.tools.chart.ChartSpotsOverlayFrame;

public class EditSpotMeasuresPanel extends JPanel implements PropertyChangeListener {
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
	private JButton rebuildSumCleanButton = new JButton("2 Rebuild sumClean");
	private JButton reconstructSumNoFlyButton = new JButton("1 Reconstruct sumNoFly + sumClean");
	private JButton rebuildV3Button = new JButton("3 Rebuild cleanV3");
	private JSpinner v3SmoothBinsSpinner = new JSpinner(new SpinnerNumberModel(10, 1, 501, 2));
	private JSpinner v3LambdaSpinner = new JSpinner(new SpinnerNumberModel(3.0, 0.1, 99.0, 0.1));
	private JSpinner v3StepBinsSpinner = new JSpinner(new SpinnerNumberModel(2, 1, 60, 1));
	private JLabel statusLabel = new JLabel(" ", SwingConstants.LEFT);

	void init(GridLayout capLayout, MultiSPOTS96 parent0) {
		setLayout(capLayout);
		this.parent0 = parent0;
		FlowLayout layoutLeft = new FlowLayout(FlowLayout.LEFT);
		layoutLeft.setVgap(0);

		JPanel panel1 = new JPanel(layoutLeft);
		panel1.add(new JLabel("Spots:"));
		panel1.add(spotScopeCombo);
		panel1.add(reconstructSumNoFlyButton);
		panel1.add(rebuildSumCleanButton);
		rebuildSumCleanButton.setToolTipText(
				"Only recomputes sumClean as the running median of the current sumNoFly (leaves sumIn and sumNoFly unchanged).");
		reconstructSumNoFlyButton.setToolTipText(
				"sumNoFly = extrapolate sumIn across flyPresent>0 bins; sumClean = running median of sumNoFly (same order as after detection).");
		add(panel1);

		JPanel panel2 = new JPanel(layoutLeft);
		panel2.add(statusLabel);
		add(panel2);

		JPanel panel3 = new JPanel(layoutLeft);
		panel3.add(new JLabel("V3 smooth W (bins):"));
		panel3.add(v3SmoothBinsSpinner);
		panel3.add(new JLabel("\u03bb (reserved):"));
		panel3.add(v3LambdaSpinner);
		panel3.add(new JLabel("step bins (reserved):"));
		panel3.add(v3StepBinsSpinner);
		panel3.add(rebuildV3Button);
		rebuildV3Button.setToolTipText(
				"Recomputes cleanV3 = sumClean minus experiment-wide median of sumClean (per bin), after a running median over W bins (odd W, enforced internally).");
		v3LambdaSpinner.setToolTipText("Reserved for automated step detection (Tier B).");
		v3StepBinsSpinner.setToolTipText("Reserved for automated step detection (Tier B).");
		add(panel3);

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
		rebuildV3Button.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				onRebuildV3Clicked();
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

	private void onRebuildV3Clicked() {
		if (parent0 == null) {
			return;
		}
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		if (exp == null || exp.getSpots() == null) {
			statusLabel.setText("No experiment.");
			return;
		}
		int w = ((Number) v3SmoothBinsSpinner.getValue()).intValue();
		exp.getSpots().rebuildV3ResidualFromSumCleanExperimentMedian(w);
		exp.getSpots().transferMeasuresToLevel2D();
		statusLabel.setText("cleanV3 rebuilt (W=" + w + ", experiment-wide median residual).");
		refreshChartsIfPresent(exp);
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
		double pct = parent0 != null && parent0.dlgMeasure != null
				? parent0.dlgMeasure.thresholdLightPanel.getFlyOccupancyPercentForSpotSumNoFly()
				: 8.0;
		exp.getSpots().rebuildNoFlyAndCleanForSpots(targets, true, pct / 100.0);
		exp.getSpots().transferMeasuresToLevel2D(targets);
		statusLabel.setText("sumNoFly+sumClean rebuilt for " + targets.size() + " spot(s).");
		refreshChartsIfPresent(exp);
	}

	private void refreshChartsIfPresent(Experiment exp) {
		if (parent0.dlgMeasure != null && parent0.dlgMeasure.chartsPanel != null) {
			parent0.dlgMeasure.chartsPanel.displayChartPanels(exp);
		}
	}

	@Override
	public void propertyChange(PropertyChangeEvent evt) {
	}

}
