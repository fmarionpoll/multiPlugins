package plugins.fmp.multiSPOTS.dlg.export;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.util.ArrayList;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;

import plugins.fmp.multitools.tools.results.EnumResults;
import plugins.fmp.multitools.tools.results.ResultsOptions;

/**
 * Excel and CSV export for spot series grouped by stimulus and concentration
 * within each cage.
 */
public class AggregatedSpotsAreasPanel extends JPanel {
	private static final long serialVersionUID = 1L;

	private static final EnumResults[] FRAME_MEASURES = { EnumResults.AGG_FILTER_DEPLETION,
			EnumResults.AGG_COLOR_DEPLETION,
			EnumResults.AGG_COLOR_PIXEL_SUM,
			EnumResults.AGG_X_LINE_CONSO, EnumResults.AGG_RIM_LINE_CONSO };
	private static final EnumResults[] KYMO_MEASURES = { EnumResults.AGG_KYMO_HEIGHT_LOSS };

	private final JButton saveButton = new JButton("save");
	private MeasureList frameMeasuresList;
	private MeasureList kymoMeasuresList;
	JSpinner baselineMinutesSpinner = new JSpinner(new SpinnerNumberModel(2, 0, 120, 1));
	JCheckBox stopWhenStableCheckBox = new JCheckBox("stop when max stable", false);
	JSpinner stableBinsSpinner = new JSpinner(new SpinnerNumberModel(3, 1, 60, 1));
	JCheckBox discardNoFlyCageCheckBox = new JCheckBox("discard cages with no fly", true);

	void init(GridLayout capLayout) {
		setLayout(new BorderLayout(0, 6));
		frameMeasuresList = new MeasureList(FRAME_MEASURES, AggregatedSpotsAreasPanel::measureTooltip);
		kymoMeasuresList = new MeasureList(KYMO_MEASURES, AggregatedSpotsAreasPanel::measureTooltip);
		frameMeasuresList.setSelectionInterval(0, FRAME_MEASURES.length - 1);
		kymoMeasuresList.setSelectionInterval(0, KYMO_MEASURES.length - 1);
		JPanel measures = new JPanel(new GridLayout(1, 2, 8, 0));
		measures.add(wrapList("Aggregates on each frame", frameMeasuresList));
		measures.add(wrapList("Aggregates on kymographs", kymoMeasuresList));
		add(measures, BorderLayout.CENTER);
		FlowLayout flow = new FlowLayout(FlowLayout.LEFT);
		flow.setVgap(0);

		JPanel panel0 = new JPanel(flow);
		JLabel baselineLabel = new JLabel("baseline (min)");
		String baselineTip = "Initial time window, in minutes, used to find each spot's maximum signal for normalized depletion.";
		baselineLabel.setToolTipText(baselineTip);
		setSpinnerToolTip(baselineMinutesSpinner, baselineTip);
		panel0.add(baselineLabel);
		panel0.add(baselineMinutesSpinner);
		panel0.add(stopWhenStableCheckBox);
		stopWhenStableCheckBox.setToolTipText("Stop the baseline search early when its running maximum has not increased for the specified number of consecutive bins.");
		JLabel stableBinsLabel = new JLabel("stable bins");
		String stableBinsTip = "Number of consecutive bins without a new maximum before stopping the baseline search; used when 'stop when max stable' is checked.";
		stableBinsLabel.setToolTipText(stableBinsTip);
		setSpinnerToolTip(stableBinsSpinner, stableBinsTip);
		panel0.add(stableBinsLabel);
		panel0.add(stableBinsSpinner);
		JPanel options = new JPanel(new GridLayout(0, 1));
		options.setBorder(BorderFactory.createTitledBorder("Options"));
		options.add(panel0);

		JPanel panel1 = new JPanel(flow);
		discardNoFlyCageCheckBox.setToolTipText("Exclude cages with no fly from the exported results.");
		panel1.add(discardNoFlyCageCheckBox);
		options.add(panel1);
		JPanel optionsRow = new JPanel(new BorderLayout(8, 0));
		optionsRow.add(options, BorderLayout.CENTER);
		optionsRow.add(saveButton, BorderLayout.EAST);
		add(optionsRow, BorderLayout.SOUTH);
		defineActionListeners();
		setCsvMode(false);
	}

	void setCsvMode(boolean csv) {
		saveButton.setToolTipText(csv
				? "Save checked aggregates with SUM, PI and PInorm to CSV."
				: "Save checked aggregates to Excel.");
		stableBinsSpinner.setEnabled(stopWhenStableCheckBox.isSelected());
	}

	private void defineActionListeners() {

		saveButton.addActionListener(e -> firePropertyChange("EXPORT_AGGREGATED_SPOTSMEASURES", false, true));
		stopWhenStableCheckBox.addActionListener(e -> stableBinsSpinner.setEnabled(stopWhenStableCheckBox.isSelected()));
	}

	void applyTo(ResultsOptions options) {
		options.spotAreas = true;
		options.spotAggregateByStimulusConc = true;
		options.relativeToMaximum = false;
		options.onlyalive = discardNoFlyCageCheckBox.isSelected();
		options.spotAggregateMeasures = new ArrayList<>(frameMeasuresList.getSelectedValuesList());
		options.spotAggregateMeasures.addAll(kymoMeasuresList.getSelectedValuesList());
		applyBaselineTo(options);
	}

	void applyBaselineTo(ResultsOptions options) {
		options.spotBaselineWindowMinutes = ((Number) baselineMinutesSpinner.getValue()).intValue();
		options.spotBaselineStopWhenStable = stopWhenStableCheckBox.isSelected();
		options.spotBaselineStableBins = ((Number) stableBinsSpinner.getValue()).intValue();
	}

	private static JPanel wrapList(String title, MeasureList list) {
		JPanel panel = new JPanel(new BorderLayout());
		panel.setBorder(BorderFactory.createTitledBorder(title));
		panel.add(new JScrollPane(list), BorderLayout.CENTER);
		return panel;
	}

	private static String measureTooltip(EnumResults measure) {
		return "<html><body style='width: 320px'>" + measure.toTitle() + "</body></html>";
	}

	private static void setSpinnerToolTip(JSpinner spinner, String text) {
		spinner.setToolTipText(text);
		for (Component child : spinner.getComponents()) {
			if (child instanceof JComponent)
				((JComponent) child).setToolTipText(text);
		}
		if (spinner.getEditor() instanceof JSpinner.DefaultEditor)
			((JSpinner.DefaultEditor) spinner.getEditor()).getTextField().setToolTipText(text);
	}
}
