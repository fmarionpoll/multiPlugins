package plugins.fmp.multiSPOTS96.dlg.spotsMeasures;

import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
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
		final Spot spot;

		private SpotScopeChoice(SpotScopeMode mode, Spot spot) {
			this.mode = Objects.requireNonNull(mode, "mode");
			this.spot = spot;
		}

		static SpotScopeChoice all() {
			return new SpotScopeChoice(SpotScopeMode.ALL, null);
		}

		static SpotScopeChoice single(Spot spot) {
			return new SpotScopeChoice(SpotScopeMode.SINGLE, spot);
		}

		String selectionKey() {
			return mode == SpotScopeMode.ALL ? "ALL" : "SPOT:" + (spot != null && spot.getName() != null ? spot.getName() : "");
		}

		boolean matchesKey(String key) {
			return key != null && key.equals(selectionKey());
		}

		@Override
		public String toString() {
			if (mode == SpotScopeMode.ALL) {
				return "All spots";
			}
			return spot != null && spot.getName() != null ? spot.getName() : "(spot)";
		}
	}

	private MultiSPOTS96 parent0 = null;
	private JComboBox<SpotScopeChoice> spotScopeCombo = new JComboBox<>();
	private JButton rebuildButton = new JButton("Rebuild no-fly + clean");
	private JLabel statusLabel = new JLabel(" ", SwingConstants.LEFT);

	void init(GridLayout capLayout, MultiSPOTS96 parent0) {
		setLayout(capLayout);
		this.parent0 = parent0;
		FlowLayout layoutLeft = new FlowLayout(FlowLayout.LEFT);
		layoutLeft.setVgap(0);

		JPanel panel1 = new JPanel(layoutLeft);
		panel1.add(new JLabel("Spots:"));
		panel1.add(spotScopeCombo);
		panel1.add(rebuildButton);
		add(panel1);

		JPanel panel2 = new JPanel(layoutLeft);
		panel2.add(statusLabel);
		add(panel2);

		Experiment exp = parent0 != null ? (Experiment) parent0.expListComboLazy.getSelectedItem() : null;
		refreshSpotScopeCombo(exp, false);

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
			List<Spot> spots = ChartSpotsOverlayFrame.dedupeSpots(SpotSequenceRois.allSpotsFromSequence(exp));
			List<Spot> sorted = new ArrayList<>(spots);
			Collections.sort(sorted, Comparator.comparing(
					s -> s != null && s.getName() != null ? s.getName().toLowerCase() : "", String::compareTo));
			for (Spot s : sorted) {
				if (s != null) {
					spotScopeCombo.addItem(SpotScopeChoice.single(s));
				}
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

	private void onRebuildClicked() {
		if (parent0 == null) {
			return;
		}
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		if (exp == null || exp.getSpots() == null) {
			statusLabel.setText("No experiment.");
			return;
		}
		refreshSpotScopeCombo(exp, true);

		List<Spot> allSpots = ChartSpotsOverlayFrame.dedupeSpots(SpotSequenceRois.allSpotsFromSequence(exp));
		if (allSpots.isEmpty()) {
			statusLabel.setText("No spot ROIs on sequence.");
			return;
		}

		SpotScopeChoice scope = (SpotScopeChoice) spotScopeCombo.getSelectedItem();
		List<Spot> targets;
		if (scope == null || scope.mode == SpotScopeMode.ALL) {
			targets = allSpots;
		} else if (scope.spot != null) {
			targets = Collections.singletonList(scope.spot);
		} else {
			targets = allSpots;
		}

		exp.getSpots().rebuildNoFlyAndCleanForSpots(targets, true);
		exp.getSpots().transferMeasuresToLevel2D(targets);
		statusLabel.setText("Updated " + targets.size() + " spot(s).");
		if (parent0.dlgMeasure != null && parent0.dlgMeasure.tabCharts != null) {
			parent0.dlgMeasure.tabCharts.displayChartPanels(exp);
		}
	}

	@Override
	public void propertyChange(PropertyChangeEvent evt) {
	}

}
