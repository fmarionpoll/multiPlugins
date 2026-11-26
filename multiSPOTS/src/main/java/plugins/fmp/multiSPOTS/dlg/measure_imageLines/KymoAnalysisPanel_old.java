package plugins.fmp.multiSPOTS.dlg.measure_imageLines;

import java.awt.FlowLayout;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import icy.canvas.IcyCanvas;
import icy.gui.viewer.Viewer;
import icy.util.StringUtil;
import plugins.fmp.multiSPOTS.MultiSPOTS;
import plugins.fmp.multitools.canvas2D.Canvas2D_3Transforms;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.series.AnalyzeCageKymographs;
import plugins.fmp.multitools.series.options.BuildSeriesOptions;
import plugins.fmp.multitools.service.CageKymoAnalyzer.Params;
import plugins.fmp.multitools.service.KymoAnalysisResult;
import plugins.fmp.multitools.service.KymoImageTransforms;
import plugins.fmp.multitools.tools.imageTransform.ImageTransformEnums;
import plugins.fmp.multitools.tools.overlay.KymoInsectMetricOverlay;
import plugins.fmp.multitools.tools.overlay.KymoMetricThresholdOverlay;

/**
 * Raw kymograph acquisition and image previews. References and ratios belong to section 5.
 */
public class KymoAnalysisPanel_old extends JPanel implements PropertyChangeListener {

	private static final long serialVersionUID = 1L;

	private static final String ANALYZE_LABEL = "Measure raw";
	private static final String STOP_LABEL = "STOP";

	public static final String PROPERTY_KYMO_RESULT_UPDATED = "kymoAnalysisResultUpdated";

	private final MultiSPOTS parent0;
	private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);

	private final JComboBox<ImageTransformEnums> metricTransformCombo = new JComboBox<>(
			KymoImageTransforms.METRIC_CHOICES);
	private final JSpinner metricThresholdSpinner = new JSpinner(new SpinnerNumberModel(35, 0, 512, 1));
	private final JSpinner minSumRgbSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 765, 1));
	private final JSpinner minValidRowsSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 500, 1));
	private final JToggleButton viewKymoButton = new JToggleButton("View");
	private final JCheckBox metricOverlayCheckBox = new JCheckBox("metric (red)");
	private final JCheckBox insectMetricGateCheckBox = new JCheckBox("Insect filter (exclude)", true);
	private final JComboBox<ImageTransformEnums> insectMetricTransformCombo = new JComboBox<>(
			KymoImageTransforms.METRIC_CHOICES);
	private final String[] insectDirections = new String[] { "metric ≤", "metric >" };
	private final JComboBox<String> insectDirectionComboBox = new JComboBox<>(insectDirections);
	private final JSpinner insectMetricThresholdSpinner = new JSpinner(new SpinnerNumberModel(50, 0, 512, 1));
	private final JCheckBox insectOverlayCheckBox = new JCheckBox("insect (magenta)", false);
	protected final JButton analyzeButton = new JButton(ANALYZE_LABEL);
	protected final JCheckBox allSeriesCheckBox = new JCheckBox("ALL series (current to last)", false);

	private final JLabel statusLabel = new JLabel(" ", SwingConstants.LEFT);

	private KymoAnalysisResult lastResult;
	private AnalyzeCageKymographs analyzeThread;
	private KymoMetricThresholdOverlay kymoMetricOverlay;
	private KymoInsectMetricOverlay kymoInsectOverlay;

	public KymoAnalysisPanel_old(MultiSPOTS parent0) {
		super(new GridLayout(5, 1));
		this.parent0 = parent0;
		FlowLayout left = new FlowLayout(FlowLayout.LEFT);
		left.setVgap(0);
		JComponent ed = metricThresholdSpinner.getEditor();
		if (ed instanceof JSpinner.DefaultEditor) {
			JFormattedTextField tf = ((JSpinner.DefaultEditor) ed).getTextField();
			tf.setColumns(4);
			tf.setHorizontalAlignment(JTextField.TRAILING);
		}
		JComponent edIns = insectMetricThresholdSpinner.getEditor();
		if (edIns instanceof JSpinner.DefaultEditor) {
			JFormattedTextField tf = ((JSpinner.DefaultEditor) edIns).getTextField();
			tf.setColumns(4);
			tf.setHorizontalAlignment(JTextField.TRAILING);
		}

		JPanel p3 = new JPanel(new BorderLayout(4, 0));
		JPanel actions = new JPanel(left);
		actions.add(analyzeButton);
		actions.add(allSeriesCheckBox);
		p3.add(actions, BorderLayout.WEST);
		allSeriesCheckBox.setToolTipText("Measure raw height and color integral from the current experiment through the last.");
		analyzeButton.setToolTipText("Save observed height, color integral, background and occlusion flags. Calculate references and ratios in Calculate spot fractions.");

		p3.add(statusLabel, BorderLayout.CENTER);
		add(p3);

		JPanel p0 = new JPanel(left);
		p0.add(new JLabel("Metric"));
		p0.add(metricTransformCombo);
		p0.add(new JLabel(" > "));
		p0.add(metricThresholdSpinner);
		p0.add(viewKymoButton);
		p0.add(metricOverlayCheckBox);
		add(p0);

		JPanel p1 = new JPanel(left);
		p1.add(new JLabel("  min R+G+B (valid px) "));
		p1.add(minSumRgbSpinner);
		p1.add(new JLabel("  min valid rows/col "));
		p1.add(minValidRowsSpinner);

		add(p1);

		JPanel pInsect = new JPanel(left);
		pInsect.add(insectMetricGateCheckBox);
		pInsect.add(new JLabel("  insect "));
		pInsect.add(insectMetricTransformCombo);
		pInsect.add(insectDirectionComboBox);
		pInsect.add(insectMetricThresholdSpinner);
		pInsect.add(insectOverlayCheckBox);
		add(pInsect);

		JPanel calculationNotice = new JPanel(left);
		calculationNotice.add(new JLabel("References, gap filling and ratios: Calculate spot fractions (section 5)."));
		add(calculationNotice);

		metricTransformCombo.setSelectedItem(ImageTransformEnums.RGB_DIFFS);
		insectMetricTransformCombo.setSelectedItem(ImageTransformEnums.B_RGB);
		insectDirectionComboBox.setSelectedIndex(0);
		metricOverlayCheckBox.setEnabled(false);
		insectOverlayCheckBox.setEnabled(false);
		defineToolTips();

		metricOverlayCheckBox.setSelected(true);

		ChangeListener previewParamsListener = new ChangeListener() {
			@Override
			public void stateChanged(ChangeEvent e) {
				if (viewKymoButton.isSelected()) {
					refreshKymoPreview();
				}
			}
		};
		metricThresholdSpinner.addChangeListener(previewParamsListener);
		minSumRgbSpinner.addChangeListener(previewParamsListener);
		minValidRowsSpinner.addChangeListener(previewParamsListener);
		insectMetricThresholdSpinner.addChangeListener(previewParamsListener);
		defineActionListeners();
		syncInsectControlsEnabled();
	}

	private void defineActionListeners() {

		insectOverlayCheckBox.addActionListener(e -> {
			if (viewKymoButton.isSelected()) {
				refreshKymoPreview();
			}
		});

		metricTransformCombo.addActionListener(e -> {
			if (viewKymoButton.isSelected()) {
				refreshKymoPreview();
			}
		});
		insectMetricTransformCombo.addActionListener(e -> {
			if (viewKymoButton.isSelected()) {
				refreshKymoPreview();
			}
		});
		insectDirectionComboBox.addActionListener(e -> {
			if (viewKymoButton.isSelected()) {
				refreshKymoPreview();
			}
		});

		viewKymoButton.addActionListener(e -> onViewKymoToggled());
		metricOverlayCheckBox.addActionListener(e -> {
			if (viewKymoButton.isSelected()) {
				refreshKymoPreview();
			}
		});

		insectMetricGateCheckBox.addActionListener(e -> {
			syncInsectControlsEnabled();
			if (viewKymoButton.isSelected()) {
				refreshKymoPreview();
			}
		});

		analyzeButton.addActionListener(e -> {
			if (ANALYZE_LABEL.equals(analyzeButton.getText())) {
				startAnalyze();
			} else {
				stopAnalyze();
			}
		});
		allSeriesCheckBox.addActionListener(e -> {
			java.awt.Color color = java.awt.Color.BLACK;
			if (allSeriesCheckBox.isSelected()) {
				color = java.awt.Color.RED;
			}
			allSeriesCheckBox.setForeground(color);
			analyzeButton.setForeground(color);
		});
	}

	private void defineToolTips() {
		metricOverlayCheckBox.setToolTipText(
				"Per strip: spot metric passes min R+G+B and threshold; optional insect filter removes insect-like pixels (same idea as spots vs flies filters).");
		insectMetricGateCheckBox.setToolTipText(
				"When on: pixels that pass the insect transform + direction + threshold are excluded from spot-on (parallel to Flies filter in Spots measures / ThresholdLightPanel).");
		insectMetricTransformCombo
				.setToolTipText("Second transform on the same kymograph RGB (e.g. B_RGB for fly body).");
		insectDirectionComboBox.setToolTipText(
				"metric ≤ : insect-like when transformed value ≤ threshold (flies-style). metric > : insect-like when value > threshold.");
		insectMetricThresholdSpinner
				.setToolTipText("Threshold for the insect transform channel (same units as spot metric threshold).");
		insectOverlayCheckBox.setToolTipText(
				"Magenta overlay on pixels classified as insect-like by the insect transform + direction + threshold (same rule as exclusion). Uses current insect controls even when 'Insect filter' is off.");
	}

	private void syncInsectControlsEnabled() {
		boolean on = insectMetricGateCheckBox.isSelected();
		insectMetricTransformCombo.setEnabled(on);
		insectDirectionComboBox.setEnabled(on);
		insectMetricThresholdSpinner.setEnabled(on);
	}

	public void addKymoResultListener(java.beans.PropertyChangeListener l) {
		pcs.addPropertyChangeListener(PROPERTY_KYMO_RESULT_UPDATED, l);
	}

	public KymoAnalysisResult getLastResult() {
		return lastResult;
	}

	public Params readParams() {
		Object tf = metricTransformCombo.getSelectedItem();
		ImageTransformEnums transform = tf instanceof ImageTransformEnums ? (ImageTransformEnums) tf
				: ImageTransformEnums.RGB_DIFFS;
		Object itf = insectMetricTransformCombo.getSelectedItem();
		ImageTransformEnums insectTf = itf instanceof ImageTransformEnums ? (ImageTransformEnums) itf
				: ImageTransformEnums.B_RGB;
		boolean insectThrUp = insectDirectionComboBox.getSelectedIndex() == 1;
		return new Params(transform, ((Number) metricThresholdSpinner.getValue()).intValue(),
				((Number) minSumRgbSpinner.getValue()).intValue(), ((Number) minValidRowsSpinner.getValue()).intValue(),
				false, 0,
				false, false,
				insectMetricGateCheckBox.isSelected(), insectTf,
				((Number) insectMetricThresholdSpinner.getValue()).intValue(), insectThrUp);
	}

	private void onViewKymoToggled() {
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		if (!viewKymoButton.isSelected()) {
			metricOverlayCheckBox.setEnabled(false);
			insectMetricTransformCombo.setEnabled(false);
			insectDirectionComboBox.setEnabled(false);
			insectMetricThresholdSpinner.setEnabled(false);
			insectOverlayCheckBox.setEnabled(false);
			removeKymoMetricOverlay(exp);
			removeKymoInsectOverlay(exp);
			clearKymographCanvasTransform(exp);
			return;
		}
		if (exp == null || exp.getSeqKymos() == null || exp.getSeqKymos().getSequence() == null) {
			statusLabel.setText("Load cage kymographs before using View.");
			viewKymoButton.setSelected(false);
			return;
		}
		parent0.openCageKymographViewer(exp);
		metricOverlayCheckBox.setEnabled(true);
		insectOverlayCheckBox.setEnabled(true);
		syncInsectControlsEnabled();
		refreshKymoPreview();
	}

	private void refreshKymoPreview() {
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		if (exp == null || !viewKymoButton.isSelected()) {
			return;
		}
		updateKymographCanvasTransform(exp);
		syncKymoOverlays(exp);
	}

	private void updateKymographCanvasTransform(Experiment exp) {
		if (exp == null || exp.getSeqKymos() == null || exp.getSeqKymos().getSequence() == null) {
			return;
		}
		Viewer v = exp.getSeqKymos().getSequence().getFirstViewer();
		if (v == null) {
			return;
		}
		IcyCanvas canvas = v.getCanvas();
		if (!(canvas instanceof Canvas2D_3Transforms)) {
			return;
		}
		Canvas2D_3Transforms c3 = (Canvas2D_3Transforms) canvas;
		ImageTransformEnums[] choices = KymoImageTransforms.METRIC_CHOICES;
		if (c3.getTransformStep1ItemCount() < choices.length + 1) {
			c3.updateTransformsStep1(choices);
		}
		Object sel = metricTransformCombo.getSelectedItem();
		int index = sel instanceof ImageTransformEnums ? KymoImageTransforms.indexOfMetric((ImageTransformEnums) sel)
				: 0;
		c3.setTransformStep1Index(index + 1);
	}

	private void clearKymographCanvasTransform(Experiment exp) {
		if (exp == null || exp.getSeqKymos() == null || exp.getSeqKymos().getSequence() == null) {
			return;
		}
		Viewer v = exp.getSeqKymos().getSequence().getFirstViewer();
		if (v == null) {
			return;
		}
		IcyCanvas canvas = v.getCanvas();
		if (canvas instanceof Canvas2D_3Transforms) {
			((Canvas2D_3Transforms) canvas).setTransformStep1Index(0);
		}
	}

	private void syncKymoOverlays(Experiment exp) {
		if (exp == null || exp.getSeqKymos() == null || exp.getSeqKymos().getSequence() == null) {
			removeKymoMetricOverlay(exp);
			removeKymoInsectOverlay(exp);
			return;
		}
		icy.sequence.Sequence seq = exp.getSeqKymos().getSequence();
		removeKymoMetricOverlay(exp);
		removeKymoInsectOverlay(exp);
		if (metricOverlayCheckBox.isSelected()) {
			kymoMetricOverlay = new KymoMetricThresholdOverlay(seq, this::readParams,
					() -> (Experiment) parent0.expListComboLazy.getSelectedItem());
			seq.addOverlay(kymoMetricOverlay);
			kymoMetricOverlay.painterChanged();
		}
		if (insectOverlayCheckBox.isSelected()) {
			kymoInsectOverlay = new KymoInsectMetricOverlay(seq, this::readParams,
					() -> (Experiment) parent0.expListComboLazy.getSelectedItem());
			seq.addOverlay(kymoInsectOverlay);
			kymoInsectOverlay.painterChanged();
		}
	}

	private void removeKymoMetricOverlay(Experiment exp) {
		if (kymoMetricOverlay == null) {
			return;
		}
		if (exp != null && exp.getSeqKymos() != null && exp.getSeqKymos().getSequence() != null) {
			exp.getSeqKymos().getSequence().removeOverlay(kymoMetricOverlay);
		}
		kymoMetricOverlay = null;
	}

	private void removeKymoInsectOverlay(Experiment exp) {
		if (kymoInsectOverlay == null) {
			return;
		}
		if (exp != null && exp.getSeqKymos() != null && exp.getSeqKymos().getSequence() != null) {
			exp.getSeqKymos().getSequence().removeOverlay(kymoInsectOverlay);
		}
		kymoInsectOverlay = null;
	}

	protected boolean isLegacyAnalysisRunning() {
		return analyzeThread != null;
	}

	private void startAnalyze() {
		if (analyzeThread != null) {
			statusLabel.setText("Analysis already running.");
			return;
		}
		int index0 = parent0.expListComboLazy.getSelectedIndex();
		if (index0 < 0) {
			statusLabel.setText("No experiment selected.");
			return;
		}
		analyzeThread = new AnalyzeCageKymographs();
		analyzeThread.analyzerParams = readParams();
		analyzeThread.options = initAnalyzeOptions();
		analyzeThread.lastResult = null;
		analyzeThread.addPropertyChangeListener(this);
		analyzeThread.execute();
		analyzeButton.setText(STOP_LABEL);
		allSeriesCheckBox.setEnabled(false);
		statusLabel.setText("Measuring raw height and color integral…");
	}

	private void stopAnalyze() {
		if (analyzeThread != null && !analyzeThread.stopFlag) {
			analyzeThread.stopFlag = true;
			statusLabel.setText("Stopping…");
		}
	}

	protected BuildSeriesOptions initAnalyzeOptions() {
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
		if ("progress".equals(name) && analyzeThread != null) {
			statusLabel.setText("Measuring raw: " + evt.getNewValue() + "%");
			return;
		}
		if (!StringUtil.equals("thread_ended", name) && !StringUtil.equals("thread_done", name)) {
			return;
		}
		analyzeButton.setText(ANALYZE_LABEL);
		allSeriesCheckBox.setEnabled(true);
		final AnalyzeCageKymographs finished = analyzeThread;
		analyzeThread = null;
		if (finished != null) {
			lastResult = finished.lastResult;
		}
		final int recallIndex = finished != null ? finished.getSelectedExperimentIndex() : -1;
		// BuildSeries restores the combo in doInBackground finally before this runs;
		// each experiment
		// was closeSequences()'d in the batch — explicitly reopen the first experiment
		// in the series
		// (same pattern as BrowsePanel.openExperimentAtIndex after transfer
		// reload).
		SwingUtilities.invokeLater(() -> onAnalyzeBatchFinished(recallIndex, finished));
	}

	private void onAnalyzeBatchFinished(int recallIndex, AnalyzeCageKymographs finished) {
		if (recallIndex >= 0) {
			parent0.dlgBrowse.browsePanel.openExperimentAtIndex(recallIndex);
			Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
			if (exp != null && viewKymoButton.isSelected()) exp.loadCageSpotKymographs();
		}
		if (finished == null) {
			statusLabel.setText("No measurement results.");
		} else {
			statusLabel.setText((finished.stopFlag ? "Stopped. " : "Done. ") + "Saved raw: "
					+ finished.savedExperiments + " experiment(s); failed " + finished.failedExperiments + ".");
			statusLabel.setToolTipText(finished.lastFailure);
		}
		pcs.firePropertyChange(PROPERTY_KYMO_RESULT_UPDATED, false, true);
		if (viewKymoButton.isSelected()) {
			refreshKymoPreview();
		}
	}
}
