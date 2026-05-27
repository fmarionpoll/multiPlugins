package plugins.fmp.multiSPOTS96.dlg.kymograph;

import java.awt.FlowLayout;
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
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import icy.canvas.IcyCanvas;
import icy.gui.viewer.Viewer;
import icy.util.StringUtil;
import plugins.fmp.multiSPOTS96.MultiSPOTS96;
import plugins.fmp.multitools.canvas2D.Canvas2D_3Transforms;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.series.AnalyzeCageKymographs;
import plugins.fmp.multitools.series.CageKymographViewerUtil;
import plugins.fmp.multitools.series.options.BuildSeriesOptions;
import plugins.fmp.multitools.service.CageKymoAnalyzer.Params;
import plugins.fmp.multitools.service.KymoAnalysisResult;
import plugins.fmp.multitools.service.KymoImageTransforms;
import plugins.fmp.multitools.tools.imageTransform.ImageTransformEnums;
import plugins.fmp.multitools.tools.overlay.KymoInsectMetricOverlay;
import plugins.fmp.multitools.tools.overlay.KymoMergedRegionsOverlay;
import plugins.fmp.multitools.tools.overlay.KymoMetricThresholdOverlay;

/**
 * Kymograph metric: row-wise occlusion lift, preview overlays, and analysis.
 */
public class AnalysisPanel extends JPanel implements PropertyChangeListener {

	private static final long serialVersionUID = 1L;

	private static final String ANALYZE_LABEL = "Analyze";
	private static final String STOP_LABEL = "STOP";

	public static final String PROPERTY_KYMO_RESULT_UPDATED = "kymoAnalysisResultUpdated";

	private final MultiSPOTS96 parent0;
	private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);

	private final JComboBox<ImageTransformEnums> metricTransformCombo = new JComboBox<>(
			KymoImageTransforms.METRIC_CHOICES);
	private final JSpinner metricThresholdSpinner = new JSpinner(new SpinnerNumberModel(35, 0, 512, 1));
	private final JSpinner minSumRgbSpinner = new JSpinner(new SpinnerNumberModel(30, 0, 765, 1));
	private final JSpinner minValidRowsSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 500, 1));
	private final JSpinner maxRowOcclusionGapColsSpinner = new JSpinner(new SpinnerNumberModel(15, 0, 10000, 1));
	private final JCheckBox rowOcclusionLiftCheckBox = new JCheckBox("Lift occlusions (row-wise)", true);
	private final JToggleButton viewKymoButton = new JToggleButton("View");
	private final JCheckBox metricOverlayCheckBox = new JCheckBox("metric (red)");
	private final JCheckBox mergedOverlayCheckBox = new JCheckBox("row lift overlay");
	private final JCheckBox previewLiftedBandsCheckBox = new JCheckBox("Preview lifted bands", false);
	private final JCheckBox insectMetricGateCheckBox = new JCheckBox("Insect filter (exclude)", true);
	private final JComboBox<ImageTransformEnums> insectMetricTransformCombo = new JComboBox<>(
			KymoImageTransforms.METRIC_CHOICES);
	private final String[] insectDirections = new String[] { "metric ≤", "metric >" };
	private final JComboBox<String> insectDirectionComboBox = new JComboBox<>(insectDirections);
	private final JSpinner insectMetricThresholdSpinner = new JSpinner(new SpinnerNumberModel(50, 0, 512, 1));
	private final JCheckBox insectOverlayCheckBox = new JCheckBox("insect (magenta)", false);
	private final JButton analyzeButton = new JButton("Analyze");
	private JCheckBox allSeriesCheckBox = new JCheckBox("ALL series (current to last)", false);

	private final JLabel statusLabel = new JLabel(" ", SwingConstants.LEFT);

	private KymoAnalysisResult lastResult;
	private AnalyzeCageKymographs analyzeThread;
	private KymoMergedRegionsOverlay kymoMergedOverlay;
	private KymoMetricThresholdOverlay kymoMetricOverlay;
	private KymoInsectMetricOverlay kymoInsectOverlay;

	public AnalysisPanel(MultiSPOTS96 parent0) {
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

		JPanel p3 = new JPanel(left);
		p3.add(analyzeButton);
		p3.add(allSeriesCheckBox);

		p3.add(new JLabel("Status: "));
		p3.add(statusLabel);
		add(p3);

		JPanel p0 = new JPanel(left);
		p0.add(new JLabel("Metric"));
		p0.add(metricTransformCombo);
		p0.add(new JLabel(" > "));
		p0.add(metricThresholdSpinner);
		p0.add(viewKymoButton);
		p0.add(metricOverlayCheckBox);
		p0.add(mergedOverlayCheckBox);
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

		JPanel pLift = new JPanel(left);
		pLift.add(rowOcclusionLiftCheckBox);
		pLift.add(new JLabel("  max row gap (cols) "));
		pLift.add(maxRowOcclusionGapColsSpinner);
		pLift.add(previewLiftedBandsCheckBox);
		add(pLift);

		metricTransformCombo.setSelectedItem(ImageTransformEnums.RGB_DIFFS);
		insectMetricTransformCombo.setSelectedItem(ImageTransformEnums.B_RGB);
		insectDirectionComboBox.setSelectedIndex(0);
		metricOverlayCheckBox.setEnabled(false);
		mergedOverlayCheckBox.setEnabled(false);
		insectOverlayCheckBox.setEnabled(false);
		metricOverlayCheckBox.setToolTipText(
				"Per strip: spot metric passes min R+G+B and threshold; optional insect filter removes insect-like pixels (same idea as spots vs flies filters).");
		mergedOverlayCheckBox.setToolTipText(
				"Green = cleaned post-lift spot mask (insect gate on: jump across and before insect-only columns when spot resumes; then temporal gap fill ≤ max row gap, vertical bridge, left-anchored trace, 1-px speck removal). Same mask drives Analyze when row lift is on. Optional: 'Preview lifted bands' blends corrected RGB in bands.");
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
		previewLiftedBandsCheckBox.setToolTipText(
				"When row lift is on: blend the row-lifted image over each band so you see corrected intensities (does not change saved TIFFs).");
		rowOcclusionLiftCheckBox.setToolTipText(
				"Per strip and each row y, bad runs along time between good pixels are filled with mean RGB of bracketing good pixels (length ≤ max row gap). When insect filter is on, insect-like pixels are not treated as good bracket anchors.");
		maxRowOcclusionGapColsSpinner.setToolTipText(
				"Maximum width (columns) of a bad run along one row that may be filled when good pixels lie on both sides. 0 disables row fill.");

		metricOverlayCheckBox.setSelected(true);
		mergedOverlayCheckBox.setSelected(true);

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
		maxRowOcclusionGapColsSpinner.addChangeListener(previewParamsListener);
		insectMetricThresholdSpinner.addChangeListener(previewParamsListener);

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
		mergedOverlayCheckBox.addActionListener(e -> {
			if (viewKymoButton.isSelected()) {
				refreshKymoPreview();
			}
		});

		previewLiftedBandsCheckBox.addActionListener(e -> {
			if (viewKymoButton.isSelected()) {
				refreshKymoPreview();
			}
		});

		rowOcclusionLiftCheckBox.addActionListener(e -> {
			syncPreviewLiftedEnabled();
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

		syncPreviewLiftedEnabled();
		syncInsectControlsEnabled();
	}

	private void syncPreviewLiftedEnabled() {
		boolean row = rowOcclusionLiftCheckBox.isSelected();
		previewLiftedBandsCheckBox.setEnabled(row && viewKymoButton.isSelected());
		if (!row) {
			previewLiftedBandsCheckBox.setSelected(false);
		}
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
				false, ((Number) maxRowOcclusionGapColsSpinner.getValue()).intValue(),
				rowOcclusionLiftCheckBox.isSelected(), previewLiftedBandsCheckBox.isSelected(),
				insectMetricGateCheckBox.isSelected(), insectTf,
				((Number) insectMetricThresholdSpinner.getValue()).intValue(), insectThrUp);
	}

	private void onViewKymoToggled() {
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		if (!viewKymoButton.isSelected()) {
			metricOverlayCheckBox.setEnabled(false);
			mergedOverlayCheckBox.setEnabled(false);
			previewLiftedBandsCheckBox.setEnabled(false);
			insectMetricTransformCombo.setEnabled(false);
			insectDirectionComboBox.setEnabled(false);
			insectMetricThresholdSpinner.setEnabled(false);
			insectOverlayCheckBox.setEnabled(false);
			removeKymoMergedOverlay(exp);
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
		CageKymographViewerUtil.openIfPresent(exp);
		metricOverlayCheckBox.setEnabled(true);
		mergedOverlayCheckBox.setEnabled(true);
		insectOverlayCheckBox.setEnabled(true);
		syncPreviewLiftedEnabled();
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
			removeKymoMergedOverlay(exp);
			removeKymoMetricOverlay(exp);
			removeKymoInsectOverlay(exp);
			return;
		}
		icy.sequence.Sequence seq = exp.getSeqKymos().getSequence();
		removeKymoMergedOverlay(exp);
		removeKymoMetricOverlay(exp);
		removeKymoInsectOverlay(exp);
		if (mergedOverlayCheckBox.isSelected()) {
			kymoMergedOverlay = new KymoMergedRegionsOverlay(seq, this::readParams,
					() -> (Experiment) parent0.expListComboLazy.getSelectedItem());
			seq.addOverlay(kymoMergedOverlay);
			kymoMergedOverlay.painterChanged();
		}
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

	private void removeKymoMergedOverlay(Experiment exp) {
		if (kymoMergedOverlay == null) {
			return;
		}
		if (exp != null && exp.getSeqKymos() != null && exp.getSeqKymos().getSequence() != null) {
			exp.getSeqKymos().getSequence().removeOverlay(kymoMergedOverlay);
		}
		kymoMergedOverlay = null;
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
		analyzeThread = new AnalyzeCageKymographs();
		analyzeThread.analyzerParams = readParams();
		analyzeThread.options = initAnalyzeOptions();
		analyzeThread.lastResult = null;
		analyzeThread.addPropertyChangeListener(this);
		parent0.setSuppressExperimentOpenOnComboProgrammaticChange(true);
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
		parent0.setSuppressExperimentOpenOnComboProgrammaticChange(false);
		analyzeButton.setText(ANALYZE_LABEL);
		AnalyzeCageKymographs finished = analyzeThread;
		analyzeThread = null;
		if (finished != null) {
			lastResult = finished.lastResult;
		}
		if (lastResult == null || lastResult.byCageId.isEmpty()) {
			statusLabel.setText("No data: build/load kymographs and check ROI bounds.");
		} else {
			statusLabel
					.setText("Done: " + lastResult.byCageId.size() + " cage(s), " + lastResult.widthBins + " bin(s).");
		}
		pcs.firePropertyChange(PROPERTY_KYMO_RESULT_UPDATED, false, true);
		if (viewKymoButton.isSelected()) {
			refreshKymoPreview();
		}
	}
}
