package plugins.fmp.multicafe.dlg.levels;

import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.EnumMap;
import java.util.Map;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JToggleButton;
import javax.swing.SpinnerNumberModel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import icy.util.StringUtil;
import plugins.fmp.multicafe.MultiCAFE;
import plugins.fmp.multitools.canvas2D.Canvas2D_3Transforms;
import plugins.fmp.multitools.experiment.capillaries.DetectionProvenanceSupport;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.capillary.Capillary;
import plugins.fmp.multitools.series.DetectGulps;
import plugins.fmp.multitools.series.options.BuildSeriesOptions;
import plugins.fmp.multitools.series.options.BuildSeriesOptions.GulpDetectionMethod;
import plugins.fmp.multitools.series.options.GulpThresholdMethod;
import plugins.fmp.multitools.series.options.GulpThresholdSmoothing;
import plugins.fmp.multitools.tools.imageTransform.CanvasImageTransformOptions;
import plugins.fmp.multitools.tools.imageTransform.ImageTransformEnums;

public class DetectGulpsDlgFromKymo extends JPanel implements PropertyChangeListener {
	/**
	 * 
	 */
	private static final long serialVersionUID = -5590697762090397890L;

	JCheckBox selectedKymoCheckBox = new JCheckBox("selected", false);
	ImageTransformEnums[] gulpTransforms = new ImageTransformEnums[] { ImageTransformEnums.XDIFFN,
			ImageTransformEnums.YDIFFN, ImageTransformEnums.YDIFFN2, ImageTransformEnums.XYDIFFN };

	JComboBox<ImageTransformEnums> gulpTransforms_comboBox = new JComboBox<ImageTransformEnums>(gulpTransforms);
	/** Diffn span (≥2). Value remembered per transform for the session. */
	private JSpinner thresholdXDiffnSpinner = new JSpinner(new SpinnerNumberModel(3, 2, 10, 1));
	private final Map<ImageTransformEnums, Integer> spanByTransform = new EnumMap<>(ImageTransformEnums.class);
	private ImageTransformEnums lastSpanTransform = null;
	private boolean syncingSpanSpinner = false;

	JSpinner start_spinner = new JSpinner(new SpinnerNumberModel(0, 0, 100000, 1));
	JSpinner end_spinner = new JSpinner(new SpinnerNumberModel(5, 1, 100000, 1));
	JCheckBox derivative_checkbox = new JCheckBox("temporal change", true);
	JCheckBox gulps_checkbox = new JCheckBox("gulps", true);

	private JCheckBox from_pixel_checkbox = new JCheckBox("from pixel", false);
	private JToggleButton display_button = new JToggleButton("Display");

	private JComboBox<GulpDetectionMethod> gulpDetectionMethodCombo = new JComboBox<>(GulpDetectionMethod.values());
	private JComboBox<GulpThresholdMethod> thresholdMethodCombo = new JComboBox<>(GulpThresholdMethod.values());
	private JSpinner thresholdMultiplierSpinner = new JSpinner(new SpinnerNumberModel(5.0, 1.0, 10.0, 0.5));
	private JComboBox<GulpThresholdSmoothing> thresholdSmoothingCombo = new JComboBox<>(
			GulpThresholdSmoothing.values());
	private JSpinner thresholdSmoothingWindowSpinner = new JSpinner(new SpinnerNumberModel(5, 3, 21, 2));
	private JSpinner thresholdSmoothingAlphaSpinner = new JSpinner(new SpinnerNumberModel(0.3, 0.01, 0.99, 0.05));
	private String detectString = " Detect ";
	private JButton detectButton = new JButton(detectString);
	private JCheckBox all_checkbox = new JCheckBox("ALL", false);
	private DetectGulps threadDetectGulps = null;
	private MultiCAFE parent0 = null;
	private boolean suppressDisplayUpdate = false;

	void init(GridLayout capLayout, MultiCAFE parent0) {
		setLayout(capLayout);
		this.parent0 = parent0;

		FlowLayout layoutLeft = new FlowLayout(FlowLayout.LEFT);
		layoutLeft.setVgap(0);

		JPanel panel0 = new JPanel(layoutLeft);
		panel0.add(detectButton);
		panel0.add(gulpDetectionMethodCombo);
		panel0.add(all_checkbox);
		panel0.add(selectedKymoCheckBox);
		panel0.add(from_pixel_checkbox);
		panel0.add(start_spinner);
		JSpinner.DefaultEditor editor1 = (JSpinner.DefaultEditor) start_spinner.getEditor();
		JFormattedTextField textField1 = editor1.getTextField();
		textField1.setColumns(3);
		panel0.add(new JLabel("to"));
		panel0.add(end_spinner);
		JSpinner.DefaultEditor editor2 = (JSpinner.DefaultEditor) end_spinner.getEditor();
		JFormattedTextField textField2 = editor2.getTextField();
		textField2.setColumns(4);

		add(panel0);

		JPanel panel01 = new JPanel(layoutLeft);
		panel01.add(derivative_checkbox);
		panel01.add(gulpTransforms_comboBox);
		panel01.add(new JLabel("span"));
		panel01.add(thresholdXDiffnSpinner);
		panel01.add(display_button);
		add(panel01);

		JPanel panel01a = new JPanel(layoutLeft);
		panel01a.add(gulps_checkbox);
		panel01a.add(new JLabel("ref curve"));
		panel01a.add(thresholdMethodCombo);
		panel01a.add(new JLabel("k"));
		panel01a.add(thresholdMultiplierSpinner);
		add(panel01a);

		JPanel panel01b = new JPanel(layoutLeft);
		panel01b.add(new JLabel("smooth"));
		panel01b.add(thresholdSmoothingCombo);
		panel01b.add(new JLabel("win"));
		panel01b.add(thresholdSmoothingWindowSpinner);
		panel01b.add(new JLabel("alpha"));
		panel01b.add(thresholdSmoothingAlphaSpinner);
		add(panel01b);

		gulpTransforms_comboBox.setSelectedItem(ImageTransformEnums.XDIFFN);
		gulpDetectionMethodCombo.setSelectedItem(GulpDetectionMethod.TOPRAW_DY);
		thresholdMethodCombo.setSelectedItem(GulpThresholdMethod.MEAN_PLUS_SD);
		initSpanMemoryDefaults();
		syncSpanSpinnerToSelectedTransform();
		defineActionListeners();
		syncClassicOnlyControls();
	}

	private void initSpanMemoryDefaults() {
		for (ImageTransformEnums t : gulpTransforms) {
			spanByTransform.put(t, t.getDefaultSpanDiff());
		}
	}

	private void rememberCurrentSpan() {
		if (lastSpanTransform == null) {
			return;
		}
		spanByTransform.put(lastSpanTransform, (Integer) thresholdXDiffnSpinner.getValue());
	}

	private void syncSpanSpinnerToSelectedTransform() {
		ImageTransformEnums selected = (ImageTransformEnums) gulpTransforms_comboBox.getSelectedItem();
		if (selected == null) {
			return;
		}
		rememberCurrentSpan();
		int span = spanByTransform.getOrDefault(selected, selected.getDefaultSpanDiff());
		syncingSpanSpinner = true;
		try {
			thresholdXDiffnSpinner.setValue(span);
		} finally {
			syncingSpanSpinner = false;
		}
		lastSpanTransform = selected;
	}

	private int currentSpanDiff() {
		return (Integer) thresholdXDiffnSpinner.getValue();
	}

	private CanvasImageTransformOptions spanTransformOptions() {
		CanvasImageTransformOptions opt = new CanvasImageTransformOptions();
		ImageTransformEnums selected = (ImageTransformEnums) gulpTransforms_comboBox.getSelectedItem();
		opt.transformOption = selected;
		opt.spanDiff = currentSpanDiff();
		return opt;
	}

	private void applySpanToDisplayCanvasIfNeeded() {
		if (!display_button.isSelected()) {
			return;
		}
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		if (exp == null) {
			return;
		}
		Canvas2D_3Transforms canvas = getKymosCanvas(exp);
		if (canvas == null) {
			return;
		}
		int index = gulpTransforms_comboBox.getSelectedIndex();
		canvas.setTransformStep1(index + 1, spanTransformOptions());
	}

	private void defineActionListeners() {
		gulpTransforms_comboBox.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				syncSpanSpinnerToSelectedTransform();
				if (suppressDisplayUpdate || !display_button.isSelected()) {
					return;
				}
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp != null && exp.getSeqKymos() != null) {
					Canvas2D_3Transforms canvas = getKymosCanvas(exp);
					if (canvas != null) {
						int index = gulpTransforms_comboBox.getSelectedIndex();
						canvas.setTransformStep1(index + 1, spanTransformOptions());
					}
				}
			}
		});

		thresholdXDiffnSpinner.addChangeListener(new ChangeListener() {
			@Override
			public void stateChanged(ChangeEvent e) {
				if (syncingSpanSpinner) {
					return;
				}
				rememberCurrentSpan();
				applySpanToDisplayCanvasIfNeeded();
			}
		});

		detectButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				if (detectButton.getText().equals(detectString))
					startComputation(true);
				else
					stopComputation();
			}
		});

		display_button.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp != null && exp.getSeqKymos() != null) {
					if (display_button.isSelected()) {
						Canvas2D_3Transforms canvas = getKymosCanvas(exp);
						if (canvas != null) {
							canvas.updateTransformsStep1(gulpTransforms);
							int index = gulpTransforms_comboBox.getSelectedIndex();
							canvas.setTransformStep1(index + 1, spanTransformOptions());
						}
					} else {
						Canvas2D_3Transforms canvas = getKymosCanvas(exp);
						if (canvas != null)
							canvas.setTransformStep1Index(0);
					}
				}
			}
		});

		all_checkbox.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Color color = Color.BLACK;
				if (all_checkbox.isSelected())
					color = Color.RED;
				all_checkbox.setForeground(color);
				detectButton.setForeground(color);
			}
		});

		gulpDetectionMethodCombo.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				syncClassicOnlyControls();
			}
		});

	}

	private void syncClassicOnlyControls() {
		boolean classic = gulpDetectionMethodCombo.getSelectedItem() == GulpDetectionMethod.XDIFFN_REF;
		thresholdSmoothingCombo.setEnabled(classic);
		thresholdSmoothingWindowSpinner.setEnabled(classic);
		thresholdSmoothingAlphaSpinner.setEnabled(classic);
	}

	private BuildSeriesOptions initBuildParameters(Experiment exp) {
		BuildSeriesOptions options = threadDetectGulps.options;
		options.expList = parent0.expListComboLazy;
		options.expList.index0 = parent0.expListComboLazy.getSelectedIndex();

		if (all_checkbox.isSelected())
			options.expList.index1 = options.expList.getItemCount() - 1;
		else
			options.expList.index1 = parent0.expListComboLazy.getSelectedIndex();

		options.detectSelectedKymo = selectedKymoCheckBox.isSelected();
		options.gulpDetectionMethod = (GulpDetectionMethod) gulpDetectionMethodCombo.getSelectedItem();
		if (options.gulpDetectionMethod == null) {
			options.gulpDetectionMethod = GulpDetectionMethod.TOPRAW_DY;
		}

		if (selectedKymoCheckBox.isSelected() && exp.getSeqKymos() != null && exp.getSeqKymos().getSequence() != null
				&& exp.getSeqKymos().getSequence().getFirstViewer() != null) {
			int t = exp.getSeqKymos().getSequence().getFirstViewer().getPositionT();
			options.kymoFirst = t;
			options.kymoLast = t;
		} else if (exp.getSeqKymos() != null && exp.getSeqKymos().getSequence() != null) {
			options.kymoFirst = 0;
			options.kymoLast = exp.getSeqKymos().getSequence().getSizeT() - 1;
		} else {
			options.kymoFirst = 0;
			options.kymoLast = 0;
		}
		options.transformForGulps = (ImageTransformEnums) gulpTransforms_comboBox.getSelectedItem();
		rememberCurrentSpan();
		options.spanDiffForGulps = currentSpanDiff();
		options.detectSelectedKymo = selectedKymoCheckBox.isSelected();
		options.thresholdMethod = (GulpThresholdMethod) thresholdMethodCombo.getSelectedItem();
		options.thresholdSdMultiplier = (double) thresholdMultiplierSpinner.getValue();
		options.thresholdSmoothing = (GulpThresholdSmoothing) thresholdSmoothingCombo.getSelectedItem();
		options.thresholdSmoothingWindow = (int) thresholdSmoothingWindowSpinner.getValue();
		options.thresholdSmoothingAlpha = (double) thresholdSmoothingAlphaSpinner.getValue();
		options.buildGulps = gulps_checkbox.isSelected();
		options.buildDerivative = derivative_checkbox.isSelected();
		options.analyzePartOnly = from_pixel_checkbox.isSelected();
		options.searchArea.x = (int) start_spinner.getValue();
		options.searchArea.width = (int) end_spinner.getValue() + (int) start_spinner.getValue();
		options.parent0Rect = parent0.mainFrame.getBoundsInternal();
		options.binSubDirectory = exp.getBinSubDirectory();
		return options;
	}

	void startComputation(boolean bDetectGulps) {
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		if (exp != null) {
			exp.saveCapillariesMeasures(exp.getKymosBinFullDirectory());
			threadDetectGulps = new DetectGulps();
			threadDetectGulps.options = initBuildParameters(exp);
			if (!bDetectGulps)
				threadDetectGulps.options.buildGulps = false;
			threadDetectGulps.addPropertyChangeListener(this);
			threadDetectGulps.execute();
			detectButton.setText("STOP");
		}
	}

	void setInfos(Capillary cap) {
		if (cap == null) {
			return;
		}
		applyGulpOptionsToDialog(cap.getGulpsOptions());
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		resetDisplayToRaw(exp);
	}

	public void loadGulpDefaultsFromExperiment(Experiment exp) {
		if (exp != null) {
			applyGulpOptionsToDialog(exp.getGulpDetectionDefaults());
			resetDisplayToRaw(exp);
		}
	}

	void resetDisplayToRaw(Experiment exp) {
		boolean wasViewing = display_button.isSelected();
		display_button.setSelected(false);
		if (wasViewing && exp != null && exp.getSeqKymos() != null) {
			Canvas2D_3Transforms canvas = getKymosCanvas(exp);
			if (canvas != null) {
				canvas.setTransformStep1Index(0);
			}
		}
	}

	private void applyGulpOptionsToDialog(BuildSeriesOptions options) {
		if (options == null) {
			return;
		}
		suppressDisplayUpdate = true;
		try {
			spanByTransform.put(options.transformForGulps, Math.max(2, options.spanDiffForGulps));
			lastSpanTransform = null;
			gulpTransforms_comboBox.setSelectedItem(options.transformForGulps);
			selectedKymoCheckBox.setSelected(options.detectSelectedKymo);
			if (options.gulpDetectionMethod != null) {
				gulpDetectionMethodCombo.setSelectedItem(options.gulpDetectionMethod);
			} else {
				gulpDetectionMethodCombo.setSelectedItem(GulpDetectionMethod.TOPRAW_DY);
			}
			thresholdMethodCombo.setSelectedItem(options.thresholdMethod);
			thresholdMultiplierSpinner.setValue(options.thresholdSdMultiplier);
			thresholdSmoothingCombo.setSelectedItem(options.thresholdSmoothing);
			thresholdSmoothingWindowSpinner.setValue(options.thresholdSmoothingWindow);
			thresholdSmoothingAlphaSpinner.setValue(options.thresholdSmoothingAlpha);
		} finally {
			suppressDisplayUpdate = false;
		}
		syncSpanSpinnerToSelectedTransform();
		syncClassicOnlyControls();
	}

	void restoreDialogFromSessionAfterDetection(BuildSeriesOptions session, Experiment exp) {
		if (session == null) {
			return;
		}
		applyGulpOptionsToDialog(session);
		resetDisplayToRaw(exp);
	}

	private void stopComputation() {
		if (threadDetectGulps != null && !threadDetectGulps.stopFlag) {
			threadDetectGulps.stopFlag = true;
		}
	}

	@Override
	public void propertyChange(PropertyChangeEvent evt) {
		if (StringUtil.equals("thread_ended", evt.getPropertyName())) {
			detectButton.setText(detectString);
			Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
			if (exp != null && threadDetectGulps != null && threadDetectGulps.options != null) {
				boolean fullBatch = DetectionProvenanceSupport.isFullBatchGulpDetection(threadDetectGulps.options);
				exp.applyGulpDetectionDefaultsFrom(threadDetectGulps.options, fullBatch);
				exp.saveExperimentDescriptors();
			}
			int kymoIndex = parent0.paneKymos.tabIntervals.indexImagesCombo;
			parent0.paneKymos.tabIntervals.selectKymographImage(kymoIndex, false);
			parent0.paneKymos.tabIntervals.indexImagesCombo = -1;
			if (exp != null && threadDetectGulps != null && threadDetectGulps.options != null) {
				restoreDialogFromSessionAfterDetection(threadDetectGulps.options, exp);
				start_spinner.setValue(threadDetectGulps.options.searchArea.x);
				end_spinner.setValue(
						threadDetectGulps.options.searchArea.width + threadDetectGulps.options.searchArea.x);
			}

		}
	}

	protected Canvas2D_3Transforms getKymosCanvas(Experiment exp) {
		if (exp.getSeqKymos() == null || exp.getSeqKymos().getSequence() == null)
			return null;
		if (exp.getSeqKymos().getSequence().getFirstViewer() == null)
			return null;
		if (exp.getSeqKymos().getSequence().getFirstViewer().getCanvas() instanceof Canvas2D_3Transforms)
			return (Canvas2D_3Transforms) exp.getSeqKymos().getSequence().getFirstViewer().getCanvas();
		return null;
	}

}
