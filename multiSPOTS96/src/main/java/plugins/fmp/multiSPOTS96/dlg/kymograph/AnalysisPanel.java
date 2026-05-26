package plugins.fmp.multiSPOTS96.dlg.kymograph;

import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.beans.PropertyChangeSupport;
import java.util.List;
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
import plugins.fmp.multitools.service.KymoAnalysisResult.SpotKymoSeries;
import plugins.fmp.multitools.service.KymoImageTransforms;
import plugins.fmp.multitools.tools.imageTransform.ImageTransformEnums;
import plugins.fmp.multitools.tools.overlay.KymoGapFillColumnOverlay;
import plugins.fmp.multitools.tools.overlay.KymoMetricThresholdOverlay;

/**
 * Kymograph metric: detection parameters, postprocess, preview on kymograph
 * sequence, and analysis worker.
 */
public class AnalysisPanel extends JPanel {

	private static final long serialVersionUID = 1L;

	/** Fired when an Analyze run finishes (success, empty, error, or interrupt). */
	public static final String PROPERTY_KYMO_RESULT_UPDATED = "kymoAnalysisResultUpdated";

	private final MultiSPOTS96 parent0;
	private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);

	private final JComboBox<ImageTransformEnums> metricTransformCombo = new JComboBox<>(
			KymoImageTransforms.METRIC_CHOICES);
	private final JSpinner metricThresholdSpinner = new JSpinner(new SpinnerNumberModel(35, 0, 512, 1));
	private final JSpinner minSumRgbSpinner = new JSpinner(new SpinnerNumberModel(30, 0, 765, 1));
	private final JSpinner minValidRowsSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 500, 1));
	private final JCheckBox restrictSignalBandCheckBox = new JCheckBox("Restrict rows to signal (per column)");
	private final JSpinner effectiveBandMinRunSpinner = new JSpinner(new SpinnerNumberModel(3, 1, 500, 1));
	private final JSpinner signalMinMaxRgbSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 255, 1));
	private final JCheckBox fillLocfCheckBox = new JCheckBox("Fill gaps (interpolate)");
	private final JSpinner locfMaxGapSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 10000, 1));
	private final JToggleButton viewKymoButton = new JToggleButton("View");
	private final JCheckBox kymoOverlayCheckBox = new JCheckBox("overlay");
	private final JButton analyzeButton = new JButton("Analyze");
	private final JLabel statusLabel = new JLabel(" ", SwingConstants.LEFT);

	private KymoAnalysisResult lastResult;
	private SwingWorker<KymoAnalysisResult, Void> analyzeWorker;
	private KymoMetricThresholdOverlay kymoMetricOverlay;
	private KymoGapFillColumnOverlay kymoGapFillOverlay;

	public AnalysisPanel(MultiSPOTS96 parent0) {
		super(new GridLayout(5, 1));
		this.parent0 = parent0;
		FlowLayout left = new FlowLayout(FlowLayout.LEFT);
		left.setVgap(0);
		JComponent ed = metricThresholdSpinner.getEditor();
		if (ed instanceof JSpinner.DefaultEditor) {
			JFormattedTextField tf = ((JSpinner.DefaultEditor) ed).getTextField();
			tf.setColumns(4); // “em” width ≈ 4 characters (not pixel-perfect)
			tf.setHorizontalAlignment(JTextField.TRAILING);
		}

		JPanel p0 = new JPanel(left);
		p0.add(analyzeButton);
		JButton diagnosticsButton = new JButton("Diagnostics…");
		p0.add(diagnosticsButton);
		p0.add(new JLabel("Metric"));
		p0.add(metricTransformCombo);
		p0.add(new JLabel(" > "));
		p0.add(metricThresholdSpinner);
		add(p0);

		JPanel p1 = new JPanel(left);
		p1.add(new JLabel("  min R+G+B (valid px) "));
		p1.add(minSumRgbSpinner);
		p1.add(new JLabel("  min valid rows/col "));
		p1.add(minValidRowsSpinner);
		p1.add(viewKymoButton);
		p1.add(kymoOverlayCheckBox);
		add(p1);

		JPanel p1post = new JPanel(left);
		p1post.add(new JLabel("Postprocess"));
		p1post.add(restrictSignalBandCheckBox);
		p1post.add(new JLabel("min run"));
		p1post.add(effectiveBandMinRunSpinner);
		add(p1post);

		JPanel p1bpost = new JPanel(left);
		p1bpost.add(new JLabel("min max(R,G,B)"));
		p1bpost.add(signalMinMaxRgbSpinner);
		p1bpost.add(fillLocfCheckBox);
		p1bpost.add(new JLabel("max gap cols"));
		p1bpost.add(locfMaxGapSpinner);
		add(p1bpost);

		JPanel p3 = new JPanel(left);
		p3.add(new JLabel("Status: "));
		p3.add(statusLabel);
		add(p3);

		metricTransformCombo.setSelectedItem(ImageTransformEnums.RGB_DIFFS);
		kymoOverlayCheckBox.setEnabled(false);
		restrictSignalBandCheckBox.setToolTipText(
				"Per time column, use the longest contiguous vertical run of bright pixels inside the spot band.");
		effectiveBandMinRunSpinner.setToolTipText("Minimum run length (rows) to adopt that run; else use full band.");
		signalMinMaxRgbSpinner.setToolTipText("Extra row filter: max(R,G,B) must reach this; 0 = off.");
		fillLocfCheckBox.setToolTipText(
				"NaN columns between two valid time bins are filled by linear interpolation (same value if neighbors match).");
		locfMaxGapSpinner.setToolTipText(
				"Max length of a NaN run to fill when it is bounded by valid bins on both sides; 0 = unlimited.");
		syncPostprocessControlsEnabled();

		restrictSignalBandCheckBox.addActionListener(e -> {
			syncPostprocessControlsEnabled();
			if (viewKymoButton.isSelected()) {
				refreshKymoPreview();
			}
		});
		fillLocfCheckBox.addActionListener(e -> {
			syncPostprocessControlsEnabled();
			if (viewKymoButton.isSelected()) {
				refreshKymoPreview();
			}
		});
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
		effectiveBandMinRunSpinner.addChangeListener(previewParamsListener);
		signalMinMaxRgbSpinner.addChangeListener(previewParamsListener);
		locfMaxGapSpinner.addChangeListener(previewParamsListener);

		metricTransformCombo.addActionListener(e -> {
			if (viewKymoButton.isSelected()) {
				refreshKymoPreview();
			}
		});

		viewKymoButton.addActionListener(e -> onViewKymoToggled());
		kymoOverlayCheckBox.addActionListener(e -> {
			if (viewKymoButton.isSelected()) {
				refreshKymoPreview();
			}
		});

		analyzeButton.addActionListener(e -> onAnalyze());
		diagnosticsButton
				.addActionListener(e -> KymoDiagnosticsDialog.show(this, parent0.kymoDiagnosticsOptions, () -> {
					if (viewKymoButton.isSelected()) {
						refreshKymoPreview();
					}
				}));
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
				false, restrictSignalBandCheckBox.isSelected(),
				((Number) effectiveBandMinRunSpinner.getValue()).intValue(),
				((Number) signalMinMaxRgbSpinner.getValue()).intValue(), fillLocfCheckBox.isSelected(),
				((Number) locfMaxGapSpinner.getValue()).intValue(),
				parent0.kymoDiagnosticsOptions.isIncludeDiagnosticsOnAnalyze());
	}

	private void syncPostprocessControlsEnabled() {
		boolean r = restrictSignalBandCheckBox.isSelected();
		effectiveBandMinRunSpinner.setEnabled(r);
		signalMinMaxRgbSpinner.setEnabled(r);
		locfMaxGapSpinner.setEnabled(fillLocfCheckBox.isSelected());
	}

	private void onViewKymoToggled() {
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		if (!viewKymoButton.isSelected()) {
			kymoOverlayCheckBox.setEnabled(false);
			removeKymoMetricOverlay(exp);
			removeKymoGapFillOverlay(exp);
			clearKymographCanvasTransform(exp);
			return;
		}
		if (exp == null || exp.getSeqKymos() == null || exp.getSeqKymos().getSequence() == null) {
			statusLabel.setText("Load cage kymographs before using View.");
			viewKymoButton.setSelected(false);
			return;
		}
		CageKymographViewerUtil.openIfPresent(exp);
		kymoOverlayCheckBox.setEnabled(true);
		refreshKymoPreview();
	}

	private void refreshKymoPreview() {
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		if (exp == null || !viewKymoButton.isSelected()) {
			return;
		}
		updateKymographCanvasTransform(exp);
		syncKymoMetricOverlay(exp);
		syncKymoGapFillOverlay(exp);
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

	private void syncKymoMetricOverlay(Experiment exp) {
		if (exp == null || exp.getSeqKymos() == null || exp.getSeqKymos().getSequence() == null) {
			removeKymoMetricOverlay(exp);
			removeKymoGapFillOverlay(exp);
			return;
		}
		icy.sequence.Sequence seq = exp.getSeqKymos().getSequence();
		if (!kymoOverlayCheckBox.isSelected()) {
			removeKymoMetricOverlay(exp);
			removeKymoGapFillOverlay(exp);
			return;
		}
		if (kymoMetricOverlay == null) {
			kymoMetricOverlay = new KymoMetricThresholdOverlay(seq, this::readParams,
					() -> (Experiment) parent0.expListComboLazy.getSelectedItem());
			seq.addOverlay(kymoMetricOverlay);
		} else {
			kymoMetricOverlay.setSequence(seq);
		}
		kymoMetricOverlay.painterChanged();
		syncKymoGapFillOverlay(exp);
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

	private void syncKymoGapFillOverlay(Experiment exp) {
		if (exp == null || exp.getSeqKymos() == null || exp.getSeqKymos().getSequence() == null) {
			removeKymoGapFillOverlay(exp);
			return;
		}
		icy.sequence.Sequence seq = exp.getSeqKymos().getSequence();
		if (!parent0.kymoDiagnosticsOptions.isShowGapFillColumnsOnKymograph() || lastResult == null
				|| !resultHasGapFillDiagnostics(lastResult)) {
			removeKymoGapFillOverlay(exp);
			return;
		}
		if (kymoGapFillOverlay == null) {
			kymoGapFillOverlay = new KymoGapFillColumnOverlay(seq,
					() -> (Experiment) parent0.expListComboLazy.getSelectedItem(), () -> lastResult,
					() -> parent0.kymoDiagnosticsOptions.isShowGapFillColumnsOnKymograph());
			seq.addOverlay(kymoGapFillOverlay);
		} else {
			kymoGapFillOverlay.setSequence(seq);
		}
		kymoGapFillOverlay.painterChanged();
	}

	private void removeKymoGapFillOverlay(Experiment exp) {
		if (kymoGapFillOverlay == null) {
			return;
		}
		if (exp != null && exp.getSeqKymos() != null && exp.getSeqKymos().getSequence() != null) {
			exp.getSeqKymos().getSequence().removeOverlay(kymoGapFillOverlay);
		}
		kymoGapFillOverlay = null;
	}

	private static boolean resultHasGapFillDiagnostics(KymoAnalysisResult r) {
		if (r == null) {
			return false;
		}
		for (List<SpotKymoSeries> list : r.byCageId.values()) {
			if (list == null) {
				continue;
			}
			for (SpotKymoSeries row : list) {
				if (row != null && row.fractionBeforeGapFill != null) {
					return true;
				}
			}
		}
		return false;
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
