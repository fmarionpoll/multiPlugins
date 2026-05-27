package plugins.fmp.multiSPOTS96.dlg.kymograph;

import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.beans.PropertyChangeSupport;
import java.util.concurrent.ExecutionException;

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
import javax.swing.SwingWorker;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import icy.canvas.IcyCanvas;
import icy.gui.viewer.Viewer;
import plugins.fmp.multiSPOTS96.MultiSPOTS96;
import plugins.fmp.multitools.canvas2D.Canvas2D_3Transforms;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.series.CageKymographViewerUtil;
import plugins.fmp.multitools.service.CageKymoAnalyzer;
import plugins.fmp.multitools.service.CageKymoAnalyzer.Params;
import plugins.fmp.multitools.service.KymoAnalysisResult;
import plugins.fmp.multitools.service.KymoImageTransforms;
import plugins.fmp.multitools.tools.imageTransform.ImageTransformEnums;
import plugins.fmp.multitools.tools.overlay.KymoMergedRegionsOverlay;
import plugins.fmp.multitools.tools.overlay.KymoMetricThresholdOverlay;

/**
 * Kymograph metric: row-wise occlusion lift, preview overlays, and analysis.
 */
public class AnalysisPanel extends JPanel {

	private static final long serialVersionUID = 1L;

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
	private final JButton analyzeButton = new JButton("Analyze");
	private final JLabel statusLabel = new JLabel(" ", SwingConstants.LEFT);

	private KymoAnalysisResult lastResult;
	private SwingWorker<KymoAnalysisResult, Void> analyzeWorker;
	private KymoMergedRegionsOverlay kymoMergedOverlay;
	private KymoMetricThresholdOverlay kymoMetricOverlay;

	public AnalysisPanel(MultiSPOTS96 parent0) {
		super(new GridLayout(4, 1));
		this.parent0 = parent0;
		FlowLayout left = new FlowLayout(FlowLayout.LEFT);
		left.setVgap(0);
		JComponent ed = metricThresholdSpinner.getEditor();
		if (ed instanceof JSpinner.DefaultEditor) {
			JFormattedTextField tf = ((JSpinner.DefaultEditor) ed).getTextField();
			tf.setColumns(4);
			tf.setHorizontalAlignment(JTextField.TRAILING);
		}

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

		JPanel pLift = new JPanel(left);
		pLift.add(rowOcclusionLiftCheckBox);
		pLift.add(new JLabel("  max row gap (cols) "));
		pLift.add(maxRowOcclusionGapColsSpinner);
		pLift.add(previewLiftedBandsCheckBox);
		add(pLift);

		JPanel p3 = new JPanel(left);
		p3.add(analyzeButton);
		p3.add(new JLabel("Status: "));
		p3.add(statusLabel);
		add(p3);

		metricTransformCombo.setSelectedItem(ImageTransformEnums.RGB_DIFFS);
		metricOverlayCheckBox.setEnabled(false);
		mergedOverlayCheckBox.setEnabled(false);
		metricOverlayCheckBox.setToolTipText(
				"Per strip: pixels where R+G+B passes the valid gate and the metric exceeds the threshold.");
		mergedOverlayCheckBox.setToolTipText(
				"Green = cleaned post-lift metric mask per strip: short OFF gaps along time are filled (≤ max row gap), single-row vertical holes bridged, then only pixels connected to the main trace from the left are kept (removes stray spots/lines). Same mask drives Analyze when row lift is on. Optional: 'Preview lifted bands' blends corrected RGB in bands.");
		previewLiftedBandsCheckBox.setToolTipText(
				"When row lift is on: blend the row-lifted image over each band so you see corrected intensities (does not change saved TIFFs).");
		rowOcclusionLiftCheckBox.setToolTipText(
				"Per strip and each row y, bad runs along time between good pixels are filled with mean RGB of bracketing good pixels (length ≤ max row gap).");
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

		metricTransformCombo.addActionListener(e -> {
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

		analyzeButton.addActionListener(e -> onAnalyze());

		syncPreviewLiftedEnabled();
	}

	private void syncPreviewLiftedEnabled() {
		boolean row = rowOcclusionLiftCheckBox.isSelected();
		previewLiftedBandsCheckBox.setEnabled(row && viewKymoButton.isSelected());
		if (!row) {
			previewLiftedBandsCheckBox.setSelected(false);
		}
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
		return new Params(transform, ((Number) metricThresholdSpinner.getValue()).intValue(),
				((Number) minSumRgbSpinner.getValue()).intValue(), ((Number) minValidRowsSpinner.getValue()).intValue(),
				false, ((Number) maxRowOcclusionGapColsSpinner.getValue()).intValue(),
				rowOcclusionLiftCheckBox.isSelected(), previewLiftedBandsCheckBox.isSelected());
	}

	private void onViewKymoToggled() {
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		if (!viewKymoButton.isSelected()) {
			metricOverlayCheckBox.setEnabled(false);
			mergedOverlayCheckBox.setEnabled(false);
			previewLiftedBandsCheckBox.setEnabled(false);
			removeKymoMergedOverlay(exp);
			removeKymoMetricOverlay(exp);
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
		syncPreviewLiftedEnabled();
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
			return;
		}
		icy.sequence.Sequence seq = exp.getSeqKymos().getSequence();
		removeKymoMergedOverlay(exp);
		removeKymoMetricOverlay(exp);
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

	private void onAnalyze() {
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		if (exp == null) {
			statusLabel.setText("No experiment selected.");
			return;
		}
		if (exp.getSeqKymos() == null || exp.getSeqKymos().getSequence() == null) {
			statusLabel.setText("Load cage kymographs first (Load/Save or experiment open).");
			return;
		}
		if (analyzeWorker != null && !analyzeWorker.isDone()) {
			statusLabel.setText("Analysis already running.");
			return;
		}
		analyzeButton.setEnabled(false);
		statusLabel.setText("Analyzing…");
		final Params params = readParams();
		analyzeWorker = new SwingWorker<KymoAnalysisResult, Void>() {
			@Override
			protected KymoAnalysisResult doInBackground() {
				return CageKymoAnalyzer.analyze(exp, params);
			}

			@Override
			protected void done() {
				analyzeButton.setEnabled(true);
				try {
					lastResult = get();
					int n = lastResult.byCageId.size();
					if (n == 0) {
						statusLabel.setText("No data: check kymograph files and camera sequence for ROI bounds.");
					} else {
						statusLabel.setText("Done: " + n + " cage(s), " + lastResult.widthBins + " bin(s).");
					}
				} catch (InterruptedException ex) {
					Thread.currentThread().interrupt();
					lastResult = null;
					statusLabel.setText("Interrupted.");
				} catch (ExecutionException ex) {
					lastResult = null;
					Throwable c = ex.getCause();
					statusLabel.setText("Error: " + (c != null ? c.getMessage() : ex.getMessage()));
				}
				pcs.firePropertyChange(PROPERTY_KYMO_RESULT_UPDATED, false, true);
				if (viewKymoButton.isSelected()) {
					refreshKymoPreview();
				}
			}
		};
		analyzeWorker.execute();
	}
}
