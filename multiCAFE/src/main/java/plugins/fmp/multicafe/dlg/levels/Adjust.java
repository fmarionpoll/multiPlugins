package plugins.fmp.multicafe.dlg.levels;

import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.geom.Line2D;
import java.awt.event.ItemEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;

import icy.gui.viewer.Viewer;
import icy.roi.ROI;
import icy.roi.ROI2D;
import icy.sequence.Sequence;
import icy.type.geom.Polyline2D;
import icy.util.StringUtil;
import plugins.fmp.multicafe.MultiCAFE;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.capillary.Capillary;
import plugins.fmp.multitools.experiment.capillary.CapillaryIntakes;
import plugins.fmp.multitools.experiment.capillary.CapillaryMeasure;
import plugins.fmp.multitools.experiment.sequence.SequenceKymos;
import plugins.fmp.multitools.service.MeniscusRefinementPreview;
import plugins.fmp.multitools.series.AdjustMeasuresToDimensions;
import plugins.fmp.multitools.series.ClipCageMeasuresToSmallest;
import plugins.fmp.multitools.series.CropMeasuresToDimensions;
import plugins.fmp.multitools.series.CurvesRestoreLength;
import plugins.fmp.multitools.series.options.BuildSeriesOptions;
import plugins.fmp.multitools.tools.polyline.Level2D;
import plugins.kernel.roi.roi2d.ROI2DLine;
import plugins.kernel.roi.roi2d.ROI2DPolyLine;

public class Adjust extends JPanel implements PropertyChangeListener {
	/**
	 * 
	 */
	private static final long serialVersionUID = 2580935598417087197L;
	private MultiCAFE parent0;
	private JCheckBox allSeriesCheckBox = new JCheckBox("ALL series", false);

	private final String adjustString = "Resize levels to Kymographs";
	private final String cropString = "Crop levels to Kymograph";
	private final String clipString = "Clip levels npts to the shortest curve";
	private final String restoreString = "Restore levels";
	private final String stopString = "STOP ";

	private JButton adjustButton = new JButton(adjustString);
	private JButton restoreButton = new JButton(restoreString);
	private JButton clipButton = new JButton(clipString);
	private JButton cropButton = new JButton(cropString);
	private JSpinner offsetSpinner = new JSpinner(new SpinnerNumberModel(0, -5000, 5000, 1));
	private JButton validateOffsetButton = new JButton("Validate");
	private JLabel offsetStatus = new JLabel(" ");
	private boolean offsetSpinnerAdjusting = false;
	/** Offset shown on the kymograph and not yet written into the measures. */
	private int pendingOffset = 0;
	/** How much of pendingOffset is currently added to the displayed ROIs. */
	private int appliedToRois = 0;
	private Experiment previewExperiment = null;

	private AdjustMeasuresToDimensions threadAdjust = null;
	private CurvesRestoreLength threadRestore = null;
	private ClipCageMeasuresToSmallest threadClip = null;
	private CropMeasuresToDimensions threadCrop = null;

	void init(GridLayout capLayout, MultiCAFE parent0) {
		setLayout(capLayout);
		this.parent0 = parent0;

		FlowLayout layout = new FlowLayout(FlowLayout.LEFT);
		layout.setVgap(0);

		JPanel panel0 = new JPanel(layout);
		panel0.add(adjustButton);
		panel0.add(cropButton);
		add(panel0);

		JPanel panel1 = new JPanel(layout);
		panel1.add(clipButton);
		panel1.add(restoreButton);
		add(panel1);

		JPanel panelShift = new JPanel(layout);
		JLabel offsetLabel = new JLabel("Offset (px)");
		offsetLabel.setLabelFor(offsetSpinner);
		offsetSpinner.setToolTipText(
				"Preview a vertical shift on the open kymograph. Positive moves the curves down. Validate writes it into every capillary.");
		validateOffsetButton.setToolTipText(
				"Write the previewed offset into the top raw and bottom raw curves of every capillary. Save measures afterwards.");
		if (offsetSpinner.getEditor() instanceof JSpinner.DefaultEditor)
			((JSpinner.DefaultEditor) offsetSpinner.getEditor()).getTextField().setColumns(4);
		panelShift.add(offsetLabel);
		panelShift.add(offsetSpinner);
		panelShift.add(validateOffsetButton);
		panelShift.add(offsetStatus);
		add(panelShift);

		JPanel panel2 = new JPanel(layout);
		panel2.add(allSeriesCheckBox);
		add(panel2);

		defineListeners();
	}

	private void defineListeners() {
		adjustButton.addActionListener(e -> {
			if (adjustButton.getText().equals(adjustString))
				series_adjustDimensionsStart();
			else
				series_adjustDimensionsStop();
		});

		cropButton.addActionListener(e -> {
			if (cropButton.getText().equals(cropString))
				series_cropDimensionsStart();
			else
				series_cropDimensionsStop();
		});

		restoreButton.addActionListener(e -> {
			if (restoreButton.getText().equals(restoreString))
				series_restoreStart();
			else
				series_restoreStop();
		});

		clipButton.addActionListener(e -> {
			if (restoreButton.getText().equals(restoreString))
				series_clipStart();
			else
				series_clipStop();
		});

		offsetSpinner.addChangeListener(e -> {
			if (!offsetSpinnerAdjusting)
				previewOffset(((Number) offsetSpinner.getValue()).intValue());
		});
		validateOffsetButton.addActionListener(e -> validateOffset());
		parent0.expListComboLazy.addItemListener(e -> {
			if (e.getStateChange() == ItemEvent.DESELECTED && e.getItem() instanceof Experiment)
				dropOffsetPreview((Experiment) e.getItem());
		});

		allSeriesCheckBox.addActionListener(e -> {
			Color color = Color.BLACK;
			if (allSeriesCheckBox.isSelected())
				color = Color.RED;
			allSeriesCheckBox.setForeground(color);
			adjustButton.setForeground(color);
			clipButton.setForeground(color);
			restoreButton.setForeground(color);
			cropButton.setForeground(color);
		});
	}

	void restoreClippedPoints(Experiment exp) {
		SequenceKymos seqKymos = exp.getSeqKymos();
		int t = seqKymos.getCurrentFrame();
		Capillary cap = exp.getCapillaries().getList().get(t);
		cap.restoreClippedMeasures();

		seqKymos.updateROIFromCapillaryMeasure(cap, cap.getTopRaw());
		seqKymos.updateROIFromCapillaryMeasure(cap, cap.getBottomRaw());
		seqKymos.updateROIFromCapillaryMeasure(cap, cap.getDerivative());
	}

	@Override
	public void propertyChange(PropertyChangeEvent evt) {
		if (StringUtil.equals("thread_ended", evt.getPropertyName())) {
			if (adjustButton.getText().contains(stopString))
				adjustButton.setText(adjustString);
			else if (restoreButton.getText().contains(stopString))
				restoreButton.setText(restoreString);
			else if (clipButton.getText().contains(stopString))
				clipButton.setText(clipString);
			else if (cropButton.getText().contains(stopString))
				cropButton.setText(cropString);
		}
	}

	private void series_adjustDimensionsStop() {
		if (threadAdjust != null && !threadAdjust.stopFlag) {
			threadAdjust.stopFlag = true;
		}
	}

	private void series_cropDimensionsStop() {
		if (threadCrop != null && !threadCrop.stopFlag) {
			threadCrop.stopFlag = true;
		}
	}

	private void series_restoreStop() {
		if (threadRestore != null && !threadRestore.stopFlag) {
			threadRestore.stopFlag = true;
		}
	}

	private void series_clipStop() {
		if (threadClip != null && !threadClip.stopFlag) {
			threadClip.stopFlag = true;
		}
	}

	private boolean initBuildParameters(BuildSeriesOptions options) {
		int index = parent0.expListComboLazy.getSelectedIndex();
		Experiment exp = parent0.expListComboLazy.getItemAt(index);
		if (exp == null)
			return false;

		parent0.paneBrowse.browsePanel.closeViewsForCurrentExperiment(exp);
		options.expList = parent0.expListComboLazy;
		options.expList.index0 = parent0.expListComboLazy.getSelectedIndex();
		if (allSeriesCheckBox.isSelected())
			options.expList.index1 = parent0.expListComboLazy.getItemCount() - 1;
		else
			options.expList.index1 = options.expList.index0;

		options.isFrameFixed = parent0.paneExcel.tabCommonOptions.getIsFixedFrame();
		options.t_Ms_First = parent0.paneExcel.tabCommonOptions.getStartMs();
		options.t_Ms_Last = parent0.paneExcel.tabCommonOptions.getEndMs();
		options.t_Ms_BinDuration = parent0.paneExcel.tabCommonOptions.getBinMs();

		options.parent0Rect = parent0.mainFrame.getBoundsInternal();
		options.binSubDirectory = exp.getBinSubDirectory();
		return true;
	}

	private void series_adjustDimensionsStart() {
		threadAdjust = new AdjustMeasuresToDimensions();
		BuildSeriesOptions options = threadAdjust.options;
		if (initBuildParameters(options)) {
			threadAdjust.addPropertyChangeListener(this);
			threadAdjust.execute();
			adjustButton.setText(stopString + adjustString);
		}
	}

	private void series_cropDimensionsStart() {
		threadCrop = new CropMeasuresToDimensions();
		BuildSeriesOptions options = threadCrop.options;
		if (initBuildParameters(options)) {
			threadCrop.addPropertyChangeListener(this);
			threadCrop.execute();
			cropButton.setText(stopString + cropString);
		}
	}

	private void series_restoreStart() {
		threadRestore = new CurvesRestoreLength();
		BuildSeriesOptions options = threadRestore.options;
		if (initBuildParameters(options)) {
			threadRestore.addPropertyChangeListener(this);
			threadRestore.execute();
			restoreButton.setText(stopString + restoreString);
		}
	}

	private void previewOffset(int offset) {
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		if (exp == null) {
			pendingOffset = offset;
			return;
		}
		if (previewExperiment != null && previewExperiment != exp)
			dropOffsetPreview(previewExperiment);
		previewExperiment = exp;
		pendingOffset = offset;
		int delta = pendingOffset - appliedToRois;
		if (delta != 0)
			shiftDisplayedLevelRois(exp, delta);
		offsetStatus.setText(offset == 0 ? " " : "Preview");
	}

	private void validateOffset() {
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		if (exp == null || exp.getCapillaries() == null || exp.getCapillaries().getList() == null) {
			JOptionPane.showMessageDialog(this, "Select an experiment first.");
			return;
		}
		int offset = pendingOffset;
		unapplyLevelOffsetPreview(exp);
		pullEditedLevelsOfCurrentFrame(exp);
		pendingOffset = 0;
		appliedToRois = 0;
		previewExperiment = null;
		setOffsetSpinner(0);
		if (offset == 0) {
			offsetStatus.setText(" ");
			return;
		}
		int shifted = 0;
		for (Capillary cap : exp.getCapillaries().getList()) {
			if (cap != null && cap.addConstantLevelOffset(offset))
				shifted++;
		}
		if (shifted == 0) {
			offsetStatus.setText(" ");
			JOptionPane.showMessageDialog(this, "No top or bottom curves to shift.");
			return;
		}
		refreshDisplayedLevels(exp);
		offsetStatus.setText("Validated. Save measures to keep it.");
	}

	/** Puts the displayed curves back on the stored measures. The spinner value is kept. */
	void unapplyLevelOffsetPreview(Experiment exp) {
		if (exp == null || exp != previewExperiment || appliedToRois == 0)
			return;
		shiftDisplayedLevelRois(exp, -appliedToRois);
	}

	/** After ROIs were rebuilt from measures, show the preview again. */
	void onKymographRoisRebuilt(Experiment exp) {
		if (exp == null || exp != previewExperiment)
			return;
		appliedToRois = 0;
		if (pendingOffset != 0)
			shiftDisplayedLevelRois(exp, pendingOffset);
	}

	void discardLevelOffsetPreview() {
		pendingOffset = 0;
		appliedToRois = 0;
		previewExperiment = null;
		setOffsetSpinner(0);
		offsetStatus.setText(" ");
	}

	private void dropOffsetPreview(Experiment exp) {
		unapplyLevelOffsetPreview(exp);
		discardLevelOffsetPreview();
	}

	private void setOffsetSpinner(int value) {
		offsetSpinnerAdjusting = true;
		offsetSpinner.setValue(value);
		offsetSpinnerAdjusting = false;
	}

	private void shiftDisplayedLevelRois(Experiment exp, int dy) {
		if (dy == 0 || exp == null || exp.getSeqKymos() == null || exp.getSeqKymos().getSequence() == null)
			return;
		SequenceKymos seqKymos = exp.getSeqKymos();
		Sequence seq = seqKymos.getSequence();
		int t = currentKymographFrame(seqKymos);
		Capillary shown = t >= 0 ? seqKymos.getCapillaryForFrame(t, exp.getCapillaries()) : null;
		boolean gulpWasDirty = shown != null && shown.isGulpMeasuresDirty();
		int shifted = 0;
		seq.beginUpdate();
		try {
			for (ROI2D roi : seq.getROI2Ds()) {
				if (roi.getT() != t && roi.getT() != -1)
					continue;
				if (!isLevelPreviewRoi(roi))
					continue;
				shiftRoiY(roi, dy);
				seq.roiChanged(roi);
				shifted++;
			}
		} finally {
			seq.endUpdate();
		}
		if (shifted == 0)
			return;
		if (shown != null && !gulpWasDirty)
			shown.setGulpMeasuresDirty(false);
		Viewer viewer = seq.getFirstViewer();
		if (viewer != null && viewer.getCanvas() != null)
			viewer.getCanvas().refresh();
		appliedToRois += dy;
	}

	private static boolean isLevelPreviewRoi(ROI2D roi) {
		if (roi == null || roi.getName() == null)
			return false;
		if ("true".equals(roi.getProperty(MeniscusRefinementPreview.ROI_PROPERTY)))
			return false;
		String name = roi.getName();
		if (name.contains("derivative"))
			return false;
		return name.contains("toplevel") || name.contains("bottomlevel") || name.contains("gulp")
				|| name.contains(CapillaryIntakes.ROI_TOKEN);
	}

	private static void shiftRoiY(ROI2D roi, int dy) {
		if (roi instanceof ROI2DPolyLine) {
			Polyline2D src = ((ROI2DPolyLine) roi).getPolyline2D();
			if (src == null || src.npoints == 0 || src.ypoints == null)
				return;
			int n = Math.min(src.npoints, src.ypoints.length);
			double[] x = new double[n];
			double[] y = new double[n];
			System.arraycopy(src.xpoints, 0, x, 0, n);
			for (int i = 0; i < n; i++)
				y[i] = Double.isFinite(src.ypoints[i]) ? src.ypoints[i] + dy : src.ypoints[i];
			((ROI2DPolyLine) roi).setPolyline2D(new Polyline2D(x, y, n));
		} else if (roi instanceof ROI2DLine) {
			Line2D line = ((ROI2DLine) roi).getLine();
			if (line == null)
				return;
			((ROI2DLine) roi).setLine(
					new Line2D.Double(line.getX1(), line.getY1() + dy, line.getX2(), line.getY2() + dy));
		}
	}

	/** Keeps an unsaved edit of the curve currently shown on the kymograph. */
	private void pullEditedLevelsOfCurrentFrame(Experiment exp) {
		SequenceKymos seqKymos = exp.getSeqKymos();
		if (seqKymos == null || seqKymos.getSequence() == null)
			return;
		int t = currentKymographFrame(seqKymos);
		if (t < 0)
			return;
		Capillary cap = seqKymos.getCapillaryForFrame(t, exp.getCapillaries());
		if (cap == null)
			return;
		for (ROI roi : seqKymos.getSequence().getROIs()) {
			if (!(roi instanceof ROI2D) || ((ROI2D) roi).getT() != t || roi.getName() == null)
				continue;
			String name = roi.getName();
			if (name.contains("toplevel"))
				pullLevelIfEdited(cap, roi, cap.getTopRaw());
			else if (name.contains("bottomlevel"))
				pullLevelIfEdited(cap, roi, cap.getBottomRaw());
		}
	}

	private static void pullLevelIfEdited(Capillary cap, ROI roi, CapillaryMeasure measure) {
		if (!(roi instanceof ROI2DPolyLine) || measure == null || measure.polylineLevel == null)
			return;
		Polyline2D line = ((ROI2DPolyLine) roi).getPolyline2D();
		Level2D stored = measure.polylineLevel;
		if (line == null || line.npoints == 0 || line.ypoints == null)
			return;
		boolean different = line.npoints != stored.npoints;
		int n = Math.min(line.npoints, stored.npoints);
		for (int i = 0; !different && i < n; i++) {
			double storedY = stored.ypoints[i];
			double shownY = line.ypoints[i];
			different = (Double.isNaN(storedY) != Double.isNaN(shownY)) || (storedY != shownY);
		}
		if (different)
			cap.transferROIToLinearMeasure(roi);
	}

	private void refreshDisplayedLevels(Experiment exp) {
		SequenceKymos seqKymos = exp.getSeqKymos();
		if (seqKymos == null || seqKymos.getSequence() == null)
			return;
		int t = currentKymographFrame(seqKymos);
		if (t >= 0)
			seqKymos.replaceCapillaryMeasureRoisAtT(t, exp.getCapillaries());
		Viewer viewer = seqKymos.getSequence().getFirstViewer();
		if (viewer != null && parent0.paneKymos != null)
			parent0.paneKymos.tabIntervals.applyCentralViewOptionsToKymosViewer(viewer);
	}

	private static int currentKymographFrame(SequenceKymos seqKymos) {
		int t = seqKymos.getCurrentFrame();
		if (seqKymos.getSequence() != null && seqKymos.getSequence().getFirstViewer() != null)
			t = seqKymos.getSequence().getFirstViewer().getPositionT();
		return t;
	}

	private void series_clipStart() {
		threadClip = new ClipCageMeasuresToSmallest();
		BuildSeriesOptions options = threadClip.options;
		if (initBuildParameters(options)) {
			threadClip.addPropertyChangeListener(this);
			threadClip.execute();
			clipButton.setText(stopString + clipString);
		}
	}

}
