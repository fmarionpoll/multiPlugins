package plugins.fmp.multicafe.dlg.levels;

import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JToggleButton;
import javax.swing.SpinnerNumberModel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import icy.canvas.IcyCanvas;
import icy.gui.viewer.Viewer;
import icy.roi.ROI;
import icy.sequence.Sequence;
import icy.util.StringUtil;
import plugins.fmp.multicafe.MultiCAFE;
import plugins.fmp.multitools.canvas2D.Canvas2D_3Transforms;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.capillaries.CapillariesSequenceMapper;
import plugins.fmp.multitools.experiment.capillary.BottomBaselineEstimator;
import plugins.fmp.multitools.experiment.capillary.Capillary;
import plugins.fmp.multitools.series.DetectLevels;
import plugins.fmp.multitools.series.options.BuildSeriesOptions;
import plugins.fmp.multitools.tools.Logger;
import plugins.fmp.multitools.tools.imageTransform.ImageTransformEnums;
import plugins.fmp.multitools.tools.overlay.OverlayThreshold;
import plugins.kernel.roi.roi2d.ROI2DLine;

public class DetectBottomDlg extends JPanel implements PropertyChangeListener {
	private static final long serialVersionUID = 1L;
	private static final String BASELINE_ROI_NAME = "bottom_baseline";

	private ImageTransformEnums[] transformChoices = new ImageTransformEnums[] { ImageTransformEnums.R_RGB,
			ImageTransformEnums.G_RGB, ImageTransformEnums.B_RGB, ImageTransformEnums.B_MINUS_MINRG,
			ImageTransformEnums.B_MINUS_MEANGREY_CTR, ImageTransformEnums.R2MINUS_GB, ImageTransformEnums.G2MINUS_RB,
			ImageTransformEnums.B2MINUS_RG, ImageTransformEnums.RGB, ImageTransformEnums.GBMINUS_2R,
			ImageTransformEnums.RBMINUS_2G, ImageTransformEnums.RGMINUS_2B, ImageTransformEnums.RGB_DIFFS,
			ImageTransformEnums.H_HSB, ImageTransformEnums.S_HSB, ImageTransformEnums.B_HSB };

	private JComboBox<String> directionCombo = new JComboBox<String>(new String[] { " threshold >", " threshold <" });
	private JSpinner thresholdSpinner = new JSpinner(new SpinnerNumberModel(35, 1, 255, 1));
	private JComboBox<ImageTransformEnums> transformCombo = new JComboBox<ImageTransformEnums>(transformChoices);
	private JToggleButton transformViewButton = new JToggleButton("View");
	private JSpinner searchFromBottomSpinner = new JSpinner(new SpinnerNumberModel(80, 0, 2000, 5));
	private JSpinner baselineYSpinner = new JSpinner(new SpinnerNumberModel(0.0, 0.0, 10000.0, 1.0));
	private JCheckBox selectedKymoCheckBox = new JCheckBox("selected kymograph", false);
	private JCheckBox allSeriesCheckBox = new JCheckBox("ALL (current to last)", false);
	private JCheckBox leftCheckBox = new JCheckBox("L", true);
	private JCheckBox rightCheckBox = new JCheckBox("R", true);

	private String detectString = "Detect bottom";
	private JButton detectButton = new JButton(detectString);
	private JButton computeAllButton = new JButton("Baseline all");
	private JButton computeCurrentButton = new JButton("Baseline current");
	private JButton clearBaselineButton = new JButton("Clear baseline");
	private JButton setBaselineButton = new JButton("Set Y");
	private JButton prevNoiseButton = new JButton("< noisy");
	private JButton nextNoiseButton = new JButton("noisy >");
	private JLabel qcLabel = new JLabel("QC: --");

	private MultiCAFE parent0 = null;
	private DetectLevels threadDetectLevels = null;
	private int currentKymographImage = 0;
	private List<Capillary> rankedCaps = new ArrayList<>();
	private int rankedIndex = -1;
	private OverlayThreshold overlayThreshold = null;
	private boolean suppressDisplayUpdate = false;

	void init(GridLayout ignored, MultiCAFE parent0) {
		this.parent0 = parent0;
		setLayout(new GridLayout(5, 1));
		FlowLayout layoutLeft = new FlowLayout(FlowLayout.LEFT);
		layoutLeft.setVgap(0);
		JPanel panel0 = new JPanel(layoutLeft);
		detectButton.setToolTipText("Re-detect bottom level only (does not overwrite top).");
		panel0.add(detectButton);
		panel0.add(allSeriesCheckBox);
		panel0.add(selectedKymoCheckBox);
		panel0.add(leftCheckBox);
		panel0.add(rightCheckBox);
		add(panel0);
		JPanel panel1 = new JPanel(layoutLeft);
		panel1.add(directionCombo);
		panel1.add(thresholdSpinner);
		panel1.add(transformCombo);
		panel1.add(transformViewButton);
		panel1.add(new JLabel("search from bottom px"));
		searchFromBottomSpinner.setToolTipText("0 = full height; greater than 0 restricts tip search band.");
		panel1.add(searchFromBottomSpinner);
		add(panel1);
		JPanel panel2 = new JPanel(layoutLeft);
		panel2.add(computeCurrentButton);
		panel2.add(computeAllButton);
		panel2.add(clearBaselineButton);
		panel2.add(new JLabel("Y"));
		panel2.add(baselineYSpinner);
		panel2.add(setBaselineButton);
		add(panel2);
		JPanel panel3 = new JPanel(layoutLeft);
		panel3.add(prevNoiseButton);
		panel3.add(nextNoiseButton);
		panel3.add(qcLabel);
		add(panel3);
		JPanel panel4 = new JPanel(layoutLeft);
		panel4.add(new JLabel("Tip marker = median of bottom series after MAD outlier rejection."));
		add(panel4);
		transformCombo.setSelectedItem(ImageTransformEnums.RGB_DIFFS);
		defineActionListeners();
		defineItemListeners();
	}

	private void defineItemListeners() {
		thresholdSpinner.addChangeListener(new ChangeListener() {
			public void stateChanged(ChangeEvent e) {
				updateOverlayThreshold();
			}
		});

		searchFromBottomSpinner.addChangeListener(new ChangeListener() {
			public void stateChanged(ChangeEvent e) {
				updateOverlayThreshold();
			}
		});
	}

	private void defineActionListeners() {
		detectButton.addActionListener(e -> {
			if (detectButton.getText().equals(detectString))
				startBottomDetection();
			else
				stopDetection();
		});
		allSeriesCheckBox.addActionListener(e -> {
			Color color = allSeriesCheckBox.isSelected() ? Color.RED : Color.BLACK;
			allSeriesCheckBox.setForeground(color);
			detectButton.setForeground(color);
		});
		computeCurrentButton.addActionListener(e -> computeBaseline(false));
		computeAllButton.addActionListener(e -> computeBaseline(true));
		clearBaselineButton.addActionListener(e -> clearBaseline(true));
		setBaselineButton.addActionListener(e -> setManualBaseline());
		prevNoiseButton.addActionListener(e -> navigateNoise(-1));
		nextNoiseButton.addActionListener(e -> navigateNoise(1));

		transformCombo.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				if (suppressDisplayUpdate)
					return;
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp != null && exp.getSeqKymos() != null) {
					Canvas2D_3Transforms canvas = getKymosCanvas(exp);
					if (canvas != null && transformViewButton.isSelected()) {
						int index = transformCombo.getSelectedIndex();
						canvas.updateTransformsStep1(transformChoices);
						canvas.setTransformStep1(index + 1, null);
						updateOverlayThreshold();
					}
				}
			}
		});

		transformViewButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp == null)
					return;

				if (transformViewButton.isSelected()) {
					Canvas2D_3Transforms canvas = getKymosCanvas(exp);
					if (canvas != null) {
						canvas.updateTransformsStep1(transformChoices);
						int index = transformCombo.getSelectedIndex();
						canvas.setTransformStep1(index + 1, null);
					}
					addOverlayToSequence(exp);
				} else {
					removeOverlay(exp);
					Canvas2D_3Transforms canvas = getKymosCanvas(exp);
					if (canvas != null)
						canvas.setTransformStep1Index(0);
				}
			}
		});

		directionCombo.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				updateOverlayThreshold();
			}
		});
	}

	private BuildSeriesOptions initBuildParameters(Experiment exp) {
		BuildSeriesOptions options = new BuildSeriesOptions();
		options.expList = parent0.expListComboLazy;
		options.expList.index0 = parent0.expListComboLazy.getSelectedIndex();
		options.expList.index1 = allSeriesCheckBox.isSelected() ? options.expList.getItemCount() - 1
				: parent0.expListComboLazy.getSelectedIndex();
		options.detectSelectedKymo = selectedKymoCheckBox.isSelected();
		if (selectedKymoCheckBox.isSelected() && exp.getSeqKymos() != null && exp.getSeqKymos().getSequence() != null
				&& exp.getSeqKymos().getSequence().getFirstViewer() != null) {
			options.kymoFirst = exp.getSeqKymos().getSequence().getFirstViewer().getPositionT();
			options.kymoLast = options.kymoFirst;
			currentKymographImage = options.kymoFirst;
		} else {
			options.kymoFirst = 0;
			options.kymoLast = exp.getSeqKymos().getSequence().getSizeT() - 1;
			currentKymographImage = 0;
		}
		options.pass1 = true;
		options.pass2 = false;
		options.detectTop = false;
		options.detectBottom = true;
		options.transformBottom = (ImageTransformEnums) transformCombo.getSelectedItem();
		options.directionUpBottom = directionCombo.getSelectedIndex() == 0;
		options.detectLevelBottomThreshold = (int) thresholdSpinner.getValue();
		options.bottomSearchFromBottomPx = (int) searchFromBottomSpinner.getValue();
		options.transform01 = options.transformBottom;
		options.directionUp1 = options.directionUpBottom;
		options.detectLevel1Threshold = options.detectLevelBottomThreshold;
		options.detectL = leftCheckBox.isSelected();
		options.detectR = rightCheckBox.isSelected();
		options.analyzePartOnly = false;
		if (exp.getSeqKymos() != null && exp.getSeqKymos().getSequence() != null) {
			Rectangle bounds = exp.getSeqKymos().getSequence().getBounds2D();
			options.searchArea = new Rectangle(0, 0, Math.max(0, bounds.width), Math.max(0, bounds.height));
		}
		options.parent0Rect = parent0.mainFrame.getBoundsInternal();
		options.binSubDirectory = parent0.expListComboLazy.expListBinSubDirectory;
		options.sourceCamDirect = false;
		return options;
	}

	private void startBottomDetection() {
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		if (exp == null)
			return;
		threadDetectLevels = new DetectLevels();
		threadDetectLevels.options = initBuildParameters(exp);
		exp.getCapillaries().clearKymoMeasuresOnly(threadDetectLevels.options.kymoFirst,
				threadDetectLevels.options.kymoLast, threadDetectLevels.options.detectL,
				threadDetectLevels.options.detectR, false, true);
		threadDetectLevels.addPropertyChangeListener(this);
		threadDetectLevels.execute();
		detectButton.setText("STOP");
	}

	private void stopDetection() {
		if (threadDetectLevels != null && !threadDetectLevels.stopFlag)
			threadDetectLevels.stopFlag = true;
	}

	@Override
	public void propertyChange(PropertyChangeEvent evt) {
		if (!StringUtil.equals("thread_ended", evt.getPropertyName()))
			return;
		detectButton.setText(detectString);
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		if (exp != null && threadDetectLevels != null && threadDetectLevels.options != null) {
			exp.applyLevelDetectionDefaultsFrom(threadDetectLevels.options);
			exp.saveExperimentDescriptors();
		}
		parent0.paneKymos.tabIntervals.selectKymographImage(currentKymographImage, false);
		refreshQcList(exp);
		if (exp != null && exp.getSeqKymos() != null)
			CapillariesSequenceMapper.transferMeasuresToKymos(exp.getCapillaries(), exp.getSeqKymos());
	}

	private Capillary getCurrentCapillary(Experiment exp) {
		if (exp == null || exp.getSeqKymos() == null || exp.getSeqKymos().getSequence() == null)
			return null;
		int t = 0;
		if (exp.getSeqKymos().getSequence().getFirstViewer() != null)
			t = exp.getSeqKymos().getSequence().getFirstViewer().getPositionT();
		return exp.getCapillaries().getCapillaryAtT(t);
	}

	private void computeBaseline(boolean all) {
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		if (exp == null)
			return;
		if (all) {
			for (Capillary cap : exp.getCapillaries().getList())
				BottomBaselineEstimator.estimateAndApply(cap);
		} else {
			Capillary cap = getCurrentCapillary(exp);
			if (cap == null)
				return;
			BottomBaselineEstimator.estimateAndApply(cap);
			updateBaselineSpinner(cap);
		}
		exp.save_capillaries_description_and_measures();
		exp.saveMCCapillaries_Only();
		refreshQcList(exp);
		showBaselineOnCurrentKymo(exp);
	}

	private void clearBaseline(boolean all) {
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		if (exp == null)
			return;
		if (all) {
			for (Capillary cap : exp.getCapillaries().getList())
				cap.clearBottomBaseline();
		} else {
			Capillary cap = getCurrentCapillary(exp);
			if (cap != null)
				cap.clearBottomBaseline();
		}
		exp.save_capillaries_description_and_measures();
		exp.saveMCCapillaries_Only();
		removeBaselineRoi(exp);
		refreshQcList(exp);
	}

	private void setManualBaseline() {
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		Capillary cap = getCurrentCapillary(exp);
		if (cap == null)
			return;
		double y = ((Number) baselineYSpinner.getValue()).doubleValue();
		cap.setBottomBaselineY(y);
		cap.setBottomBaselineMad(Double.NaN);
		cap.setBottomBaselineOutlierFrac(Double.NaN);
		exp.save_capillaries_description_and_measures();
		exp.saveMCCapillaries_Only();
		refreshQcList(exp);
		showBaselineOnCurrentKymo(exp);
	}

	private void updateBaselineSpinner(Capillary cap) {
		if (cap != null && Double.isFinite(cap.getBottomBaselineY()))
			baselineYSpinner.setValue(cap.getBottomBaselineY());
	}

	private void refreshQcList(Experiment exp) {
		rankedCaps = BottomBaselineEstimator.rankByBottomNoise(exp != null ? exp.getCapillaries().getList() : null);
		if (rankedIndex >= rankedCaps.size())
			rankedIndex = rankedCaps.size() - 1;
		updateQcLabel();
	}

	private void updateQcLabel() {
		if (rankedCaps == null || rankedCaps.isEmpty()) {
			qcLabel.setText("QC: no bottom measures");
			return;
		}
		if (rankedIndex < 0 || rankedIndex >= rankedCaps.size()) {
			qcLabel.setText("QC: " + rankedCaps.size() + " caps -- press noisy >");
			return;
		}
		Capillary cap = rankedCaps.get(rankedIndex);
		String name = cap.getLast2ofCapillaryName();
		if (name == null)
			name = cap.getRoiName();
		String mad = Double.isFinite(cap.getBottomBaselineMad()) ? String.format("%.1f", cap.getBottomBaselineMad())
				: "--";
		String base = Double.isFinite(cap.getBottomBaselineY()) ? String.format("%.0f", cap.getBottomBaselineY())
				: "NaN";
		qcLabel.setText(String.format("QC: %d/%d -- %s Y=%s MAD=%s", rankedIndex + 1, rankedCaps.size(), name, base,
				mad));
	}

	private void navigateNoise(int delta) {
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		if (exp == null)
			return;
		refreshQcList(exp);
		if (rankedCaps.isEmpty())
			return;
		if (rankedIndex < 0)
			rankedIndex = 0;
		else
			rankedIndex = (rankedIndex + delta + rankedCaps.size()) % rankedCaps.size();
		Capillary cap = rankedCaps.get(rankedIndex);
		int t = cap.getKymographIndex();
		if (t >= 0)
			parent0.paneKymos.tabIntervals.selectKymographImage(t, false);
		parent0.paneLevels.selectCapillaryForDetectionDialogs(cap);
		updateBaselineSpinner(cap);
		updateQcLabel();
		showBaselineOnCurrentKymo(exp);
		if (exp.getSeqKymos() != null)
			CapillariesSequenceMapper.transferMeasuresToKymos(exp.getCapillaries(), exp.getSeqKymos());
		Logger.info("Bottom QC -> " + qcLabel.getText());
	}

	private void showBaselineOnCurrentKymo(Experiment exp) {
		if (exp == null || exp.getSeqKymos() == null || exp.getSeqKymos().getSequence() == null)
			return;
		Capillary cap = getCurrentCapillary(exp);
		removeBaselineRoi(exp);
		if (cap == null || !Double.isFinite(cap.getBottomBaselineY()))
			return;
		Sequence seq = exp.getSeqKymos().getSequence();
		Rectangle bounds = seq.getBounds2D();
		double y = cap.getBottomBaselineY();
		ROI2DLine line = new ROI2DLine(0, y, Math.max(1, bounds.width - 1), y);
		line.setName(BASELINE_ROI_NAME);
		line.setColor(Color.MAGENTA);
		int t = cap.getKymographIndex();
		if (t >= 0)
			line.setT(t);
		seq.addROI(line);
	}

	private void removeBaselineRoi(Experiment exp) {
		if (exp == null || exp.getSeqKymos() == null || exp.getSeqKymos().getSequence() == null)
			return;
		Sequence seq = exp.getSeqKymos().getSequence();
		List<ROI> toRemove = new ArrayList<>();
		for (ROI roi : seq.getROIs()) {
			if (roi != null && BASELINE_ROI_NAME.equals(roi.getName()))
				toRemove.add(roi);
		}
		for (ROI roi : toRemove)
			seq.removeROI(roi);
	}

	protected Canvas2D_3Transforms getKymosCanvas(Experiment exp) {
		if (exp.getSeqKymos() == null || exp.getSeqKymos().getSequence() == null)
			return null;
		if (exp.getSeqKymos().getSequence().getFirstViewer() == null)
			parent0.paneKymos.tabIntervals.displayON();
		Viewer v = exp.getSeqKymos().getSequence().getFirstViewer();
		if (v == null)
			return null;
		if (v.getCanvas() instanceof Canvas2D_3Transforms)
			return (Canvas2D_3Transforms) v.getCanvas();
		return null;
	}

	void addOverlayToSequence(Experiment exp) {
		if (exp.getSeqKymos() == null || exp.getSeqKymos().getSequence() == null)
			return;

		Sequence seq = exp.getSeqKymos().getSequence();
		if (seq.getFirstViewer() == null)
			parent0.paneKymos.tabIntervals.displayON();

		if (overlayThreshold == null)
			overlayThreshold = new OverlayThreshold(seq);
		else {
			seq.removeOverlay(overlayThreshold);
			overlayThreshold.setSequence(seq);
		}
		overlayThreshold.setBoundaryOnlyMode(true);
		overlayThreshold.setBottomBoundaryOnly(true);
		seq.addOverlay(overlayThreshold);

		Viewer v = seq.getFirstViewer();
		if (v != null) {
			IcyCanvas canvas = v.getCanvas();
			if (canvas != null) {
				if (!canvas.hasLayer(overlayThreshold))
					canvas.addLayer(overlayThreshold);
				if (!canvas.isLayersVisible())
					canvas.setLayersVisible(true);
			}
		}

		updateOverlayThreshold();
		seq.overlayChanged(overlayThreshold);
		seq.dataChanged();
	}

	void updateOverlayThreshold() {
		if (overlayThreshold == null)
			return;
		if (!transformViewButton.isSelected())
			return;

		boolean ifGreater = (directionCombo.getSelectedIndex() == 0);
		int threshold = (int) thresholdSpinner.getValue();
		ImageTransformEnums transform = (ImageTransformEnums) transformCombo.getSelectedItem();
		overlayThreshold.setThresholdSingle(threshold, transform, ifGreater);
		overlayThreshold.setBoundaryOnlyMode(true);
		overlayThreshold.setBottomBoundaryOnly(true);
		overlayThreshold.setBottomSearchFromBottomPx((int) searchFromBottomSpinner.getValue());
		overlayThreshold.painterChanged();
		if (overlayThreshold.getSequence() != null) {
			overlayThreshold.getSequence().overlayChanged(overlayThreshold);
			overlayThreshold.getSequence().dataChanged();
		}
	}

	void removeOverlay(Experiment exp) {
		if (exp.getSeqKymos() == null || exp.getSeqKymos().getSequence() == null)
			return;
		Sequence seq = exp.getSeqKymos().getSequence();
		Viewer v = seq.getFirstViewer();
		if (v != null) {
			IcyCanvas canvas = v.getCanvas();
			if (canvas != null && overlayThreshold != null && canvas.hasLayer(overlayThreshold))
				canvas.removeLayer(overlayThreshold);
		}
		if (overlayThreshold != null)
			seq.removeOverlay(overlayThreshold);
	}

	void resetDisplayToRaw(Experiment exp) {
		transformViewButton.setSelected(false);
		if (exp != null) {
			removeOverlay(exp);
			Canvas2D_3Transforms canvas = getKymosCanvas(exp);
			if (canvas != null)
				canvas.setTransformStep1Index(0);
		}
	}

	public void loadDefaultsFromExperiment(Experiment exp) {
		if (exp == null)
			return;
		BuildSeriesOptions options = exp.getLevelDetectionDefaults();
		if (options == null)
			return;
		suppressDisplayUpdate = true;
		try {
			transformCombo.setSelectedItem(options.transformBottom != null ? options.transformBottom : options.transform01);
			directionCombo.setSelectedIndex(options.directionUpBottom ? 0 : 1);
			thresholdSpinner.setValue(options.detectLevelBottomThreshold);
			searchFromBottomSpinner.setValue(Math.max(0, options.bottomSearchFromBottomPx));
		} finally {
			suppressDisplayUpdate = false;
		}
		refreshQcList(exp);
		resetDisplayToRaw(exp);
	}

	void setDialogFromOptions(Capillary cap) {
		if (cap == null)
			return;
		BuildSeriesOptions options = cap.getProperties().getLimitsOptions();
		if (options != null) {
			suppressDisplayUpdate = true;
			try {
				transformCombo
						.setSelectedItem(options.transformBottom != null ? options.transformBottom : options.transform01);
				directionCombo.setSelectedIndex(options.directionUpBottom ? 0 : 1);
				thresholdSpinner.setValue(options.detectLevelBottomThreshold);
				searchFromBottomSpinner.setValue(Math.max(0, options.bottomSearchFromBottomPx));
			} finally {
				suppressDisplayUpdate = false;
			}
		}
		updateBaselineSpinner(cap);
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		resetDisplayToRaw(exp);
	}
}