package plugins.fmp.multiSPOTS.dlg.measure_kymograph;

import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

import icy.util.StringUtil;
import plugins.fmp.multiSPOTS.MultiSPOTS;
import plugins.fmp.multitools.series.AnalyzeSpotLineDeficits;
import plugins.fmp.multitools.series.options.BuildSeriesOptions;
import plugins.fmp.multitools.service.SpotLineDeficitAnalyzer;

/**
 * Line analysis: extended horizontal lines, floor outside the circles as zero.
 */
public class AnalysisPanel2 extends JPanel implements PropertyChangeListener {

	private static final long serialVersionUID = 1L;

	private static final String ANALYZE_LABEL = "Analyze";
	private static final String STOP_LABEL = "STOP";

	public static final String PROPERTY_KYMO_RESULT_UPDATED = "kymoLineResultUpdated";

	private final MultiSPOTS parent0;
	private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);

	private final JSpinner madMultiplierSpinner = new JSpinner(new SpinnerNumberModel(5.0, 0.5, 30.0, 0.5));
	private final JSpinner initialBinsSpinner = new JSpinner(new SpinnerNumberModel(5, 1, 500, 1));
	private final JSpinner flankPxSpinner = new JSpinner(new SpinnerNumberModel(40, 0, 2000, 1));
	private final JButton analyzeButton = new JButton(ANALYZE_LABEL);
	private final JCheckBox allSeriesCheckBox = new JCheckBox("ALL series (current to last)", false);
	private final JLabel statusLabel = new JLabel(" ", SwingConstants.LEFT);

	private AnalyzeSpotLineDeficits analyzeThread;

	public AnalysisPanel2(MultiSPOTS parent0) {
		super(new GridLayout(3, 1));
		this.parent0 = parent0;
		FlowLayout left = new FlowLayout(FlowLayout.LEFT);
		left.setVgap(0);
		narrowSpinner(madMultiplierSpinner, 4);
		narrowSpinner(initialBinsSpinner, 4);
		narrowSpinner(flankPxSpinner, 4);

		JPanel actions = new JPanel(left);
		actions.add(analyzeButton);
		actions.add(allSeriesCheckBox);
		actions.add(new JLabel("Status: "));
		actions.add(statusLabel);
		add(actions);

		JPanel params = new JPanel(left);
		params.add(new JLabel("k (MAD)"));
		params.add(madMultiplierSpinner);
		params.add(new JLabel("initial bins"));
		params.add(initialBinsSpinner);
		params.add(new JLabel("flank (px)"));
		params.add(flankPxSpinner);
		add(params);

		JPanel hint = new JPanel(left);
		hint.add(new JLabel("Zero = floor outside the circles. Charts: KYMO_LINE_RATIO."));
		add(hint);

		analyzeButton.addActionListener(e -> {
			if (ANALYZE_LABEL.equals(analyzeButton.getText())) {
				startAnalyze();
			} else {
				stopAnalyze();
			}
		});
	}

	public void addKymoResultListener(java.beans.PropertyChangeListener listener) {
		pcs.addPropertyChangeListener(PROPERTY_KYMO_RESULT_UPDATED, listener);
	}

	public SpotLineDeficitAnalyzer.Params readParams() {
		return new SpotLineDeficitAnalyzer.Params(((Number) madMultiplierSpinner.getValue()).doubleValue(),
				((Number) initialBinsSpinner.getValue()).intValue(),
				((Number) flankPxSpinner.getValue()).intValue());
	}

	private void startAnalyze() {
		if (analyzeThread != null && analyzeThread.threadRunning) {
			statusLabel.setText("Analysis already running.");
			return;
		}
		int index0 = parent0.expListComboLazy.getSelectedIndex();
		if (index0 < 0) {
			statusLabel.setText("No experiment selected.");
			return;
		}
		analyzeThread = new AnalyzeSpotLineDeficits();
		analyzeThread.analyzerParams = readParams();
		analyzeThread.options = initAnalyzeOptions();
		analyzeThread.lastCageCount = 0;
		analyzeThread.lastBinCount = 0;
		analyzeThread.addPropertyChangeListener(this);
		analyzeThread.execute();
		analyzeButton.setText(STOP_LABEL);
		statusLabel.setText("Analyzing…");
	}

	private void stopAnalyze() {
		if (analyzeThread != null && !analyzeThread.stopFlag) {
			analyzeThread.stopFlag = true;
			statusLabel.setText("Stopping…");
		}
	}

	private BuildSeriesOptions initAnalyzeOptions() {
		BuildSeriesOptions options = new BuildSeriesOptions();
		options.expList = parent0.expListComboLazy;
		int last = Math.max(0, parent0.expListComboLazy.getItemCount() - 1);
		int sel = Math.max(0, parent0.expListComboLazy.getSelectedIndex());
		options.expList.index0 = sel;
		if (allSeriesCheckBox.isSelected()) {
			options.expList.index1 = last;
		} else {
			options.expList.index1 = sel;
		}
		if (options.expList.index0 > options.expList.index1) {
			options.expList.index1 = options.expList.index0;
		}
		options.detectAllSeries = allSeriesCheckBox.isSelected();
		options.concurrentDisplay = false;
		return options;
	}

	@Override
	public void propertyChange(PropertyChangeEvent evt) {
		String name = evt.getPropertyName();
		if (!StringUtil.equals("thread_ended", name) && !StringUtil.equals("thread_done", name)) {
			return;
		}
		analyzeButton.setText(ANALYZE_LABEL);
		final AnalyzeSpotLineDeficits finished = analyzeThread;
		analyzeThread = null;
		final int recallIndex = finished != null ? finished.getSelectedExperimentIndex() : -1;
		final int cages = finished != null ? finished.lastCageCount : 0;
		final int bins = finished != null ? finished.lastBinCount : 0;
		final boolean saved = finished != null && finished.lastSaved;
		SwingUtilities.invokeLater(() -> onAnalyzeBatchFinished(recallIndex, cages, bins, saved));
	}

	private void onAnalyzeBatchFinished(int recallIndex, int cages, int bins, boolean saved) {
		if (recallIndex >= 0) {
			parent0.dlgBrowse.browsePanel.openExperimentAtIndex(recallIndex);
		}
		if (cages <= 0 || bins <= 0) {
			statusLabel.setText("No data: open an experiment with spot circles and camera frames.");
		} else if (!saved) {
			statusLabel.setText("Done: " + cages + " cage(s), " + bins
					+ " bin(s). SpotsMeasures.csv was not saved (no kymograph bin).");
		} else {
			statusLabel.setText("Done: " + cages + " cage(s), " + bins + " bin(s). Chart KYMO_LINE_RATIO.");
		}
		pcs.firePropertyChange(PROPERTY_KYMO_RESULT_UPDATED, false, true);
	}

	private static void narrowSpinner(JSpinner spinner, int columns) {
		JComponent editor = spinner.getEditor();
		if (editor instanceof JSpinner.DefaultEditor) {
			JFormattedTextField field = ((JSpinner.DefaultEditor) editor).getTextField();
			field.setColumns(columns);
			field.setHorizontalAlignment(JTextField.TRAILING);
		}
	}
}
