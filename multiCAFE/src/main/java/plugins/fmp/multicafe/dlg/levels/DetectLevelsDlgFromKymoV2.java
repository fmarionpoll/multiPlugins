package plugins.fmp.multicafe.dlg.levels;

import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

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
import icy.sequence.Sequence;
import icy.util.StringUtil;
import plugins.fmp.multicafe.MultiCAFE;
import plugins.fmp.multitools.canvas2D.Canvas2D_3Transforms;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.series.DetectLevelsV2;
import plugins.fmp.multitools.series.options.BuildSeriesOptions;
import plugins.fmp.multitools.series.options.LevelDetectV2Options;
import plugins.fmp.multitools.tools.Logger;
import plugins.fmp.multitools.tools.imageTransform.ImageTransformEnums;
import plugins.fmp.multitools.tools.overlay.OverlayThreshold;
import plugins.kernel.roi.roi2d.ROI2DRectangle;

public class DetectLevelsDlgFromKymoV2 extends JPanel implements PropertyChangeListener {
	private static final long serialVersionUID = 1L;

	private JComboBox<String> directionComboBox = new JComboBox<>(new String[] { " threshold >", " threshold <" });
	private JSpinner thresholdSpinner = new JSpinner(new SpinnerNumberModel(35, 1, 255, 1));

	private ImageTransformEnums[] transforms = new ImageTransformEnums[] { //
			ImageTransformEnums.RGB_DIFFS, //
			ImageTransformEnums.B_MINUS_MINRG, //
			ImageTransformEnums.B2MINUS_RG, //
			ImageTransformEnums.B_RGB, //
			ImageTransformEnums.B_MINUS_MEANGREY_CTR, //
			ImageTransformEnums.GBMINUS_2R, //
			ImageTransformEnums.R_RGB, //
			ImageTransformEnums.G_RGB, //
			ImageTransformEnums.R2MINUS_GB, //
			ImageTransformEnums.G2MINUS_RB, //
			ImageTransformEnums.RGB, //
			ImageTransformEnums.H_HSB, //
			ImageTransformEnums.S_HSB, //
			ImageTransformEnums.B_HSB //
	};
	private JComboBox<ImageTransformEnums> transformComboBox = new JComboBox<>(transforms);
	private JToggleButton transformViewButton = new JToggleButton("View");

	private JCheckBox removeHzAvgCheckBox = new JCheckBox("remove Hz avg (tape)", false);
	private JCheckBox tapePrepassCheckBox = new JCheckBox("tape prepass", false);
	private JCheckBox runBackwardsCheckBox = new JCheckBox("run backwards", false);
	private JCheckBox edgePeakCheckBox = new JCheckBox("edge peak", true);
	private JSpinner trackUpSpinner = new JSpinner(new SpinnerNumberModel(3, 0, 100, 1));
	private JSpinner trackDownSpinner = new JSpinner(new SpinnerNumberModel(25, 0, 500, 1));
	private JSpinner medianWinSpinner = new JSpinner(new SpinnerNumberModel(5, 1, 51, 2));
	private JSpinner maxSpikeSpinner = new JSpinner(new SpinnerNumberModel(4, 0, 100, 1));

	private JCheckBox selectedKymoCheckBox = new JCheckBox("selected kymograph", false);
	private String detectString = "        Detect     ";
	private JButton detectButton = new JButton(detectString);
	private JCheckBox fromRectangleCheckBox = new JCheckBox(" detection from ROI rectangle", false);
	private JCheckBox allSeriesCheckBox = new JCheckBox("ALL (current to last)", false);
	private JCheckBox leftCheckBox = new JCheckBox("L", true);
	private JCheckBox rightCheckBox = new JCheckBox("R", true);

	private MultiCAFE parent0 = null;
	private DetectLevelsV2 threadDetectLevels = null;
	private String SEARCHRECT = "search_rectangle_v2";
	private ROI2DRectangle searchRectangleROI2D = null;
	private OverlayThreshold overlayThreshold = null;
	private int currentKymographImage = 0;

	void init(GridLayout capLayout, MultiCAFE parent0) {
		this.parent0 = parent0;
		setLayout(capLayout);
		FlowLayout layoutLeft = new FlowLayout(FlowLayout.LEFT);
		layoutLeft.setVgap(0);

		JPanel panel00 = new JPanel(layoutLeft);
		panel00.add(detectButton);
		allSeriesCheckBox
				.setToolTipText("Run level detection for the current experiment through the last in the browse list.");
		panel00.add(allSeriesCheckBox);
		panel00.add(selectedKymoCheckBox);
		panel00.add(leftCheckBox);
		panel00.add(rightCheckBox);

		JPanel panel01 = new JPanel(layoutLeft);
		panel01.add(directionComboBox);
		((JLabel) directionComboBox.getRenderer()).setHorizontalAlignment(JLabel.RIGHT);
		panel01.add(thresholdSpinner);
		panel01.add(transformComboBox);
		panel01.add(transformViewButton);

		JPanel panel02 = new JPanel(layoutLeft);
		panel02.add(removeHzAvgCheckBox);
		panel02.add(tapePrepassCheckBox);
		panel02.add(runBackwardsCheckBox);
		panel02.add(fromRectangleCheckBox);

		JPanel panel03 = new JPanel(layoutLeft);
		panel03.add(edgePeakCheckBox);
		panel03.add(new JLabel("track up"));
		panel03.add(trackUpSpinner);
		panel03.add(new JLabel("down"));
		panel03.add(trackDownSpinner);

		JPanel panel04 = new JPanel(layoutLeft);
		panel04.add(new JLabel("median"));
		panel04.add(medianWinSpinner);
		panel04.add(new JLabel("max spike"));
		panel04.add(maxSpikeSpinner);

		add(panel00);
		add(panel01);
		add(panel02);
		add(panel03);
		add(panel04);

		removeHzAvgCheckBox.setToolTipText(
				"Subtracts each row's mean — helps full-width horizontal tape bars, not a local end-of-kymo shadow.");
		tapePrepassCheckBox.setToolTipText(
				"Locate thin persistent horizontal seams (background tape) and skip those edges when a liquid edge exists below.");
		runBackwardsCheckBox.setToolTipText(
				"Blend forward+backward tracks; hold level when tape/shadow would pull the curve down.");
		transformComboBox.setSelectedItem(ImageTransformEnums.RGB_DIFFS);
		defineActionListeners();
		defineItemListeners();
	}

	private void defineItemListeners() {
		ChangeListener refreshPreview = new ChangeListener() {
			public void stateChanged(ChangeEvent e) {
				if (transformViewButton.isSelected()) {
					applyCanvasTransformForView();
					updateOverlayThreshold();
				}
			}
		};
		thresholdSpinner.addChangeListener(refreshPreview);
		trackUpSpinner.addChangeListener(refreshPreview);
		trackDownSpinner.addChangeListener(refreshPreview);
		medianWinSpinner.addChangeListener(refreshPreview);
		maxSpikeSpinner.addChangeListener(refreshPreview);

		ActionListener refreshPreviewAction = new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				if (transformViewButton.isSelected()) {
					applyCanvasTransformForView();
					updateOverlayThreshold();
				}
			}
		};
		removeHzAvgCheckBox.addActionListener(refreshPreviewAction);
		tapePrepassCheckBox.addActionListener(refreshPreviewAction);
		runBackwardsCheckBox.addActionListener(refreshPreviewAction);
		edgePeakCheckBox.addActionListener(refreshPreviewAction);
	}

	/**
	 * Color transform on step1, or Hz-avg then color on step1/step2 when tape
	 * prepass is on.
	 */
	private void applyCanvasTransformForView() {
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		if (exp == null)
			return;
		Canvas2D_3Transforms canvas = getKymosCanvas(exp);
		if (canvas == null)
			return;
		if (removeHzAvgCheckBox.isSelected()) {
			canvas.updateTransformsStep1(new ImageTransformEnums[] { ImageTransformEnums.MINUSHORIZAVG });
			canvas.setTransformStep1(ImageTransformEnums.MINUSHORIZAVG, null);
			canvas.setTransformStep2((ImageTransformEnums) transformComboBox.getSelectedItem(), null);
		} else {
			canvas.setTransformStep2Index(0);
			canvas.updateTransformsStep1(transforms);
			canvas.setTransformStep1(transformComboBox.getSelectedIndex() + 1, null);
		}
	}

	private void defineActionListeners() {
		transformComboBox.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp != null && exp.getSeqKymos() != null && transformViewButton.isSelected()) {
					applyCanvasTransformForView();
					updateOverlayThreshold();
				}
			}
		});

		detectButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				if (detectButton.getText().equals(detectString))
					startLevelsDetection();
				else
					stopLevelsDetection();
			}
		});

		transformViewButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp == null)
					return;
				if (transformViewButton.isSelected()) {
					if (parent0.paneLevels != null)
						parent0.paneLevels.clearLevelsV1View();
					applyCanvasTransformForView();
					addOverlayToSequence(exp);
				} else {
					removeOverlay(exp);
					Canvas2D_3Transforms canvas = getKymosCanvas(exp);
					if (canvas != null) {
						canvas.setTransformStep2Index(0);
						canvas.setTransformStep1Index(0);
					}
				}
			}
		});

		allSeriesCheckBox.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Color color = allSeriesCheckBox.isSelected() ? Color.RED : Color.BLACK;
				allSeriesCheckBox.setForeground(color);
				detectButton.setForeground(color);
			}
		});

		fromRectangleCheckBox.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp == null)
					return;
				if (fromRectangleCheckBox.isSelected())
					displaySearchArea(exp);
				else if (searchRectangleROI2D != null)
					exp.getSeqKymos().getSequence().removeROI(searchRectangleROI2D);
				if (transformViewButton.isSelected())
					updateOverlayThreshold();
			}
		});

		directionComboBox.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				updateOverlayThreshold();
			}
		});
	}

	private LevelDetectV2Options readV2OptionsFromDialog() {
		LevelDetectV2Options v2 = new LevelDetectV2Options();
		v2.transform = (ImageTransformEnums) transformComboBox.getSelectedItem();
		v2.directionUp = (directionComboBox.getSelectedIndex() == 0);
		v2.threshold = (int) thresholdSpinner.getValue();
		v2.removeHorizontalAverage = removeHzAvgCheckBox.isSelected();
		v2.tapePrepass = tapePrepassCheckBox.isSelected();
		v2.runBackwards = runBackwardsCheckBox.isSelected();
		v2.edgePeak = edgePeakCheckBox.isSelected();
		v2.trackUp = (int) trackUpSpinner.getValue();
		v2.trackDown = (int) trackDownSpinner.getValue();
		v2.medianWindow = (int) medianWinSpinner.getValue();
		v2.maxSpikePx = (int) maxSpikeSpinner.getValue();
		return v2;
	}

	private BuildSeriesOptions initBuildParameters(Experiment exp) {
		BuildSeriesOptions options = new BuildSeriesOptions();
		options.expList = parent0.expListComboLazy;
		options.expList.index0 = parent0.expListComboLazy.getSelectedIndex();
		if (allSeriesCheckBox.isSelected())
			options.expList.index1 = options.expList.getItemCount() - 1;
		else
			options.expList.index1 = parent0.expListComboLazy.getSelectedIndex();
		options.detectSelectedKymo = selectedKymoCheckBox.isSelected();

		if (selectedKymoCheckBox.isSelected()) {
			options.kymoFirst = exp.getSeqKymos().getSequence().getFirstViewer().getPositionT();
			options.kymoLast = options.kymoFirst;
			currentKymographImage = options.kymoFirst;
		} else {
			options.kymoFirst = 0;
			options.kymoLast = exp.getSeqKymos().getSequence().getSizeT() - 1;
			currentKymographImage = 0;
		}

		options.analyzePartOnly = fromRectangleCheckBox.isSelected();
		options.searchArea = getSearchAreaFromSearchRectangle(exp,
				fromRectangleCheckBox.isSelected() && searchRectangleROI2D != null);
		options.detectL = leftCheckBox.isSelected();
		options.detectR = rightCheckBox.isSelected();
		options.parent0Rect = parent0.mainFrame.getBoundsInternal();
		options.binSubDirectory = parent0.expListComboLazy.expListBinSubDirectory;
		options.sourceCamDirect = false;
		options.detectTop = true;
		options.detectBottom = false;
		return options;
	}

	void startLevelsDetection() {
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		if (exp == null)
			return;

		threadDetectLevels = new DetectLevelsV2();
		threadDetectLevels.options = initBuildParameters(exp);
		threadDetectLevels.v2Options = readV2OptionsFromDialog();
		if (!fromRectangleCheckBox.isSelected()) {
			exp.getCapillaries().clearKymoMeasuresOnly(threadDetectLevels.options.kymoFirst,
					threadDetectLevels.options.kymoLast, threadDetectLevels.options.detectL,
					threadDetectLevels.options.detectR, true, false);
		}
		threadDetectLevels.addPropertyChangeListener(this);
		threadDetectLevels.execute();
		detectButton.setText("STOP");
	}

	private void stopLevelsDetection() {
		if (threadDetectLevels != null && !threadDetectLevels.stopFlag)
			threadDetectLevels.stopFlag = true;
	}

	@Override
	public void propertyChange(PropertyChangeEvent evt) {
		if (StringUtil.equals("thread_ended", evt.getPropertyName())) {
			detectButton.setText(detectString);
			Logger.debug("DetectLevelsDlgFromKymoV2: thread_ended");
			parent0.paneKymos.tabIntervals.selectKymographImage(currentKymographImage, false);
			parent0.paneKymos.tabIntervals.indexImagesCombo = -1;
			fromRectangleCheckBox.setSelected(false);
			Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
			resetDisplayToRaw(exp);
		}
	}

	/**
	 * Re-apply transform View + overlay after kymograph T changes (other dialogs
	 * must not clear the canvas while this View stays selected).
	 */
	public void reapplyViewIfSelected() {
		if (!transformViewButton.isSelected())
			return;
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		if (exp == null || exp.getSeqKymos() == null)
			return;
		applyCanvasTransformForView();
		addOverlayToSequence(exp);
	}

	void resetDisplayToRaw(Experiment exp) {
		boolean wasViewing = transformViewButton.isSelected();
		transformViewButton.setSelected(false);
		if (exp != null) {
			removeOverlay(exp);
			if (wasViewing) {
				Canvas2D_3Transforms canvas = getKymosCanvas(exp);
				if (canvas != null) {
					canvas.setTransformStep2Index(0);
					canvas.setTransformStep1Index(0);
				}
			}
		}
	}

	private void displaySearchArea(Experiment exp) {
		if (exp.getSeqKymos() == null || exp.getSeqKymos().getSequence() == null)
			return;
		Sequence seq = exp.getSeqKymos().getSequence();
		if (searchRectangleROI2D == null) {
			Rectangle searchRectangle = seq.getBounds2D();
			searchRectangleROI2D = new ROI2DRectangle(searchRectangle);
			searchRectangleROI2D.setName(SEARCHRECT);
			searchRectangleROI2D.setColor(Color.ORANGE);
		}
		searchRectangleROI2D.setT(-1);
		seq.addROI(searchRectangleROI2D);
		seq.setSelectedROI(searchRectangleROI2D);
		seq.roiChanged(searchRectangleROI2D);
	}

	private Rectangle getSearchAreaFromSearchRectangle(Experiment exp, boolean fitSmallerRectangle) {
		Rectangle seqBounds = exp.getSeqKymos().getSequence().getBounds2D();
		int seqW = Math.max(0, seqBounds.width);
		int seqH = Math.max(0, seqBounds.height);
		if (fitSmallerRectangle && searchRectangleROI2D != null) {
			Rectangle r = searchRectangleROI2D.getBounds();
			int x = Math.max(0, r.x);
			int y = Math.max(0, r.y);
			int w = r.width;
			int h = r.height;
			if (x + w > seqW)
				w = seqW - x;
			if (y + h > seqH)
				h = seqH - y;
			return new Rectangle(x, y, Math.max(0, w), Math.max(0, h));
		}
		return new Rectangle(0, 0, seqW, seqH);
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
		if (overlayThreshold == null || !transformViewButton.isSelected())
			return;
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		Rectangle search = null;
		if (exp != null && exp.getSeqKymos() != null) {
			search = getSearchAreaFromSearchRectangle(exp,
					fromRectangleCheckBox.isSelected() && searchRectangleROI2D != null);
		}
		overlayThreshold.setThresholdV2Preview(readV2OptionsFromDialog(), search);
		overlayThreshold.setBoundaryOnlyMode(true);
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
}
