package plugins.fmp.multiSPOTS.dlg.measure_kymograph;

import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.ArrayList;
import java.util.List;

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

import icy.gui.viewer.Viewer;
import icy.roi.ROI;
import icy.roi.ROIEvent;
import icy.roi.ROIEvent.ROIEventType;
import icy.roi.ROIListener;
import icy.sequence.Sequence;
import icy.type.geom.Polyline2D;
import icy.util.StringUtil;
import plugins.fmp.multiSPOTS.MultiSPOTS;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.cage.Cage;
import plugins.fmp.multitools.experiment.spot.Spot;
import plugins.fmp.multitools.experiment.spot.SpotRimGeometry;
import plugins.fmp.multitools.series.AnalyzeSpotRims;
import plugins.fmp.multitools.series.options.BuildSeriesOptions;
import plugins.fmp.multitools.service.KymoImageTransforms;
import plugins.fmp.multitools.service.SpotLineDeficitAnalyzer;
import plugins.fmp.multitools.service.SpotRimAnalyzer;
import plugins.fmp.multitools.tools.imageTransform.ImageTransformEnums;
import plugins.kernel.roi.roi2d.ROI2DPolyLine;

/**
 * Rim analysis: a closed outline inside each spot, floor on the outward band.
 */
public class AnalysisPanelRim extends JPanel implements PropertyChangeListener {

	private static final long serialVersionUID = 1L;

	private static final String ANALYZE_LABEL = "Analyze";
	private static final String STOP_LABEL = "STOP";
	private static final String OUTLINE_PREFIX = "rimOutline_";
	private static final String FLOOR_PREFIX = "rimFloor_";

	public static final String PROPERTY_KYMO_RESULT_UPDATED = "kymoRimResultUpdated";

	private final MultiSPOTS parent0;
	private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);

	private final JSpinner madMultiplierSpinner = new JSpinner(new SpinnerNumberModel(5.0, 0.5, 30.0, 0.5));
	private final JSpinner initialBinsSpinner = new JSpinner(new SpinnerNumberModel(5, 1, 500, 1));
	private final JSpinner rimWidthSpinner = new JSpinner(
			new SpinnerNumberModel(SpotRimGeometry.DEFAULT_RIM_WIDTH_PX, 1, 20, 1));
	private final JSpinner outerPxSpinner = new JSpinner(
			new SpinnerNumberModel(SpotRimGeometry.DEFAULT_OUTER_PX, 0, 40, 1));
	private final JCheckBox insectGateCheckBox = new JCheckBox("Insect filter (exclude)", true);
	private final JComboBox<ImageTransformEnums> insectTransformCombo = new JComboBox<>(
			KymoImageTransforms.METRIC_CHOICES);
	private final JComboBox<String> insectDirectionCombo = new JComboBox<>(new String[] { "metric ≤", "metric >" });
	private final JSpinner insectThresholdSpinner = new JSpinner(new SpinnerNumberModel(50, 0, 512, 1));
	private final JButton detectButton = new JButton("Detect");
	private final JButton analyzeButton = new JButton(ANALYZE_LABEL);
	private final JToggleButton showRimsButton = new JToggleButton("Show rims");
	private final JCheckBox allSeriesCheckBox = new JCheckBox("ALL series (current to last)", false);
	private final JLabel statusLabel = new JLabel(" ", SwingConstants.LEFT);

	private AnalyzeSpotRims analyzeThread;
	private boolean updatingRois;

	public AnalysisPanelRim(MultiSPOTS parent0) {
		super(new GridLayout(4, 1));
		this.parent0 = parent0;
		FlowLayout left = new FlowLayout(FlowLayout.LEFT);
		left.setVgap(0);
		narrowSpinner(madMultiplierSpinner, 4);
		narrowSpinner(initialBinsSpinner, 4);
		narrowSpinner(rimWidthSpinner, 3);
		narrowSpinner(outerPxSpinner, 3);
		narrowSpinner(insectThresholdSpinner, 4);
		insectTransformCombo.setSelectedItem(ImageTransformEnums.B_RGB);
		insectGateCheckBox.setToolTipText(
				"Drop a time bin when a fly covers at least 8% of the rim. Uncheck to keep those pixels.");

		JPanel actions = new JPanel(left);
		actions.add(detectButton);
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
		params.add(new JLabel("path (px)"));
		params.add(rimWidthSpinner);
		params.add(new JLabel("outer (px)"));
		params.add(outerPxSpinner);
		add(params);

		JPanel insect = new JPanel(left);
		insect.add(insectGateCheckBox);
		insect.add(insectTransformCombo);
		insect.add(insectDirectionCombo);
		insect.add(insectThresholdSpinner);
		add(insect);

		JPanel hint = new JPanel(left);
		hint.add(showRimsButton);
		hint.add(new JLabel("Blue = outer edge of the dye (editable). Green = floor."));
		add(hint);

		showRimsButton.addActionListener(e -> refreshRims());
		rimWidthSpinner.addChangeListener(e -> onBandChanged());
		outerPxSpinner.addChangeListener(e -> onBandChanged());
		detectButton.addActionListener(e -> startDetect());
		analyzeButton.addActionListener(e -> {
			if (ANALYZE_LABEL.equals(analyzeButton.getText())) {
				startAnalyze(false);
			} else {
				stopAnalyze();
			}
		});
	}

	public void addKymoResultListener(java.beans.PropertyChangeListener listener) {
		pcs.addPropertyChangeListener(PROPERTY_KYMO_RESULT_UPDATED, listener);
	}

	public SpotRimAnalyzer.Params readParams() {
		ImageTransformEnums insectTf = insectTransformCombo.getSelectedItem() instanceof ImageTransformEnums
				? (ImageTransformEnums) insectTransformCombo.getSelectedItem()
				: ImageTransformEnums.B_RGB;
		return new SpotRimAnalyzer.Params(((Number) madMultiplierSpinner.getValue()).doubleValue(),
				((Number) initialBinsSpinner.getValue()).intValue(),
				((Number) rimWidthSpinner.getValue()).intValue(), ((Number) outerPxSpinner.getValue()).intValue(),
				SpotLineDeficitAnalyzer.DEFAULT_SMOOTH_BINS, insectGateCheckBox.isSelected(), insectTf,
				((Number) insectThresholdSpinner.getValue()).intValue(), insectDirectionCombo.getSelectedIndex() == 1);
	}

	private void startDetect() {
		startAnalyze(true);
	}

	private void startAnalyze(boolean detectOnly) {
		if (analyzeThread != null && analyzeThread.threadRunning) {
			statusLabel.setText("Analysis already running.");
			return;
		}
		int index0 = parent0.expListComboLazy.getSelectedIndex();
		if (index0 < 0) {
			statusLabel.setText("No experiment selected.");
			return;
		}
		analyzeThread = new AnalyzeSpotRims();
		analyzeThread.analyzerParams = readParams();
		analyzeThread.detectOnly = detectOnly;
		analyzeThread.options = initAnalyzeOptions(detectOnly);
		analyzeThread.lastCageCount = 0;
		analyzeThread.lastBinCount = 0;
		analyzeThread.lastOutlineCount = 0;
		analyzeThread.addPropertyChangeListener(this);
		analyzeThread.execute();
		analyzeButton.setText(STOP_LABEL);
		detectButton.setEnabled(false);
		statusLabel.setText(detectOnly ? "Detecting…" : "Analyzing…");
	}

	private void stopAnalyze() {
		if (analyzeThread != null && !analyzeThread.stopFlag) {
			analyzeThread.stopFlag = true;
			statusLabel.setText("Stopping…");
		}
	}

	private BuildSeriesOptions initAnalyzeOptions(boolean detectOnly) {
		BuildSeriesOptions options = new BuildSeriesOptions();
		options.expList = parent0.expListComboLazy;
		int last = Math.max(0, parent0.expListComboLazy.getItemCount() - 1);
		int sel = Math.max(0, parent0.expListComboLazy.getSelectedIndex());
		options.expList.index0 = sel;
		if (!detectOnly && allSeriesCheckBox.isSelected()) {
			options.expList.index1 = last;
		} else {
			options.expList.index1 = sel;
		}
		if (options.expList.index0 > options.expList.index1) {
			options.expList.index1 = options.expList.index0;
		}
		options.detectAllSeries = !detectOnly && allSeriesCheckBox.isSelected();
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
		detectButton.setEnabled(true);
		final AnalyzeSpotRims finished = analyzeThread;
		analyzeThread = null;
		final int recallIndex = finished != null ? finished.getSelectedExperimentIndex() : -1;
		final int cages = finished != null ? finished.lastCageCount : 0;
		final int bins = finished != null ? finished.lastBinCount : 0;
		final int outlines = finished != null ? finished.lastOutlineCount : 0;
		final boolean saved = finished != null && finished.lastSaved;
		final boolean detectOnly = finished != null && finished.detectOnly;
		SwingUtilities.invokeLater(() -> onFinished(recallIndex, cages, bins, outlines, saved, detectOnly));
	}

	private void onFinished(int recallIndex, int cages, int bins, int outlines, boolean saved, boolean detectOnly) {
		if (recallIndex >= 0) {
			parent0.dlgBrowse.browsePanel.openExperimentAtIndex(recallIndex);
		}
		if (detectOnly) {
			if (outlines <= 0) {
				statusLabel.setText("No outline: dye was not found inside the spot circles.");
			} else if (!saved) {
				statusLabel.setText(outlines + " outline(s). Description was not saved.");
			} else {
				statusLabel.setText(outlines + " outline(s). Blue is editable; green is the floor.");
			}
			showRimsButton.setSelected(true);
			refreshRims();
			return;
		}
		if (cages <= 0 || bins <= 0 || outlines <= 0) {
			statusLabel.setText("No data: open an experiment with spot circles and camera frames.");
		} else if (!saved) {
			statusLabel.setText("Done: " + cages + " cage(s), " + bins + " bin(s), " + outlines
					+ " outline(s). SpotsMeasures.csv was not saved.");
		} else {
			statusLabel.setText(
					"Done: " + cages + " cage(s), " + bins + " bin(s), " + outlines + " outline(s). Chart KYMO_RIM_RATIO.");
		}
		showRimsButton.setSelected(true);
		refreshRims();
		pcs.firePropertyChange(PROPERTY_KYMO_RESULT_UPDATED, false, true);
	}

	private void onBandChanged() {
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		if (exp == null || exp.getCages() == null || exp.getCages().cagesList == null || exp.getSpots() == null) {
			return;
		}
		int width = ((Number) rimWidthSpinner.getValue()).intValue();
		int outer = ((Number) outerPxSpinner.getValue()).intValue();
		for (Cage cage : exp.getCages().cagesList) {
			if (cage == null) {
				continue;
			}
			List<Spot> spots = cage.getSpotList(exp.getSpots());
			if (spots == null) {
				continue;
			}
			for (Spot spot : spots) {
				if (spot != null && spot.getRimGeometry().hasOutline()) {
					spot.getRimGeometry().setRimWidthPx(width);
					spot.getRimGeometry().setOuterPx(outer);
				}
			}
		}
		if (showRimsButton.isSelected()) {
			refreshRims();
		}
	}

	private void refreshRims() {
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		Sequence seq = exp != null && exp.getSeqCamData() != null ? exp.getSeqCamData().getSequence() : null;
		if (seq == null) {
			showRimsButton.setSelected(false);
			statusLabel.setText("Open an experiment to show the rims.");
			return;
		}
		removeRimRois(seq);
		if (!showRimsButton.isSelected()) {
			statusLabel.setText(" ");
			return;
		}
		int n = 0;
		updatingRois = true;
		try {
			if (exp.getCages() != null && exp.getCages().cagesList != null && exp.getSpots() != null) {
				for (Cage cage : exp.getCages().cagesList) {
					if (cage == null) {
						continue;
					}
					List<Spot> spots = cage.getSpotList(exp.getSpots());
					if (spots == null) {
						continue;
					}
					for (Spot spot : spots) {
						if (spot == null || !spot.getRimGeometry().hasOutline()) {
							continue;
						}
						addOutline(seq, spot);
						addFloor(seq, spot);
						n++;
					}
				}
			}
		} finally {
			updatingRois = false;
		}
		statusLabel.setText(n + " rim(s). Blue is editable; green is the floor.");
		Viewer viewer = seq.getFirstViewer();
		if (viewer != null) {
			viewer.toFront();
		}
	}

	private void addOutline(Sequence seq, Spot spot) {
		SpotRimGeometry rim = spot.getRimGeometry();
		ROI2DPolyLine roi = closedLine(rim.outlineX(), rim.outlineY());
		roi.setName(OUTLINE_PREFIX + spot.getName());
		roi.setColor(new Color(80, 140, 255));
		roi.setStroke(2);
		roi.setReadOnly(false);
		roi.addListener(new OutlineEdit(spot, seq));
		seq.addROI(roi);
	}

	private void addFloor(Sequence seq, Spot spot) {
		SpotRimGeometry rim = spot.getRimGeometry();
		double[][] outer = rim.outerCenterline();
		ROI2DPolyLine roi = closedLine(outer[0], outer[1]);
		roi.setName(FLOOR_PREFIX + spot.getName());
		roi.setColor(Color.GREEN);
		roi.setStroke(2);
		roi.setReadOnly(true);
		seq.addROI(roi);
	}

	private void replaceFloor(Sequence seq, Spot spot) {
		String name = FLOOR_PREFIX + spot.getName();
		List<ROI> copy = new ArrayList<>(seq.getROIs());
		for (ROI roi : copy) {
			if (roi != null && name.equals(roi.getName())) {
				seq.removeROI(roi);
			}
		}
		updatingRois = true;
		try {
			addFloor(seq, spot);
		} finally {
			updatingRois = false;
		}
	}

	private static ROI2DPolyLine closedLine(double[] xs, double[] ys) {
		int n = Math.min(xs.length, ys.length);
		double[] cx = new double[n + 1];
		double[] cy = new double[n + 1];
		System.arraycopy(xs, 0, cx, 0, n);
		System.arraycopy(ys, 0, cy, 0, n);
		cx[n] = xs[0];
		cy[n] = ys[0];
		return new ROI2DPolyLine(new Polyline2D(cx, cy, n + 1));
	}

	private final class OutlineEdit implements ROIListener {
		private final Spot spot;
		private final Sequence seq;

		OutlineEdit(Spot spot, Sequence seq) {
			this.spot = spot;
			this.seq = seq;
		}

		@Override
		public void roiChanged(ROIEvent event) {
			if (updatingRois || event.getType() != ROIEventType.ROI_CHANGED) {
				return;
			}
			if (!(event.getSource() instanceof ROI2DPolyLine)) {
				return;
			}
			Polyline2D line = ((ROI2DPolyLine) event.getSource()).getPolyline2D();
			if (line == null || line.npoints < 3) {
				return;
			}
			double[] xs = new double[line.npoints];
			double[] ys = new double[line.npoints];
			System.arraycopy(line.xpoints, 0, xs, 0, line.npoints);
			System.arraycopy(line.ypoints, 0, ys, 0, line.npoints);
			spot.getRimGeometry().setOutline(xs, ys);
			replaceFloor(seq, spot);
			commitDescriptions();
		}
	}

	private void commitDescriptions() {
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		if (exp == null || exp.getSpots() == null || exp.getResultsDirectory() == null) {
			return;
		}
		exp.getSpots().getPersistence().saveDescriptions(exp.getSpots(), exp.getResultsDirectory());
	}

	private static void removeRimRois(Sequence seq) {
		List<ROI> copy = new ArrayList<>(seq.getROIs());
		for (ROI roi : copy) {
			if (roi == null || roi.getName() == null) {
				continue;
			}
			if (roi.getName().startsWith(OUTLINE_PREFIX) || roi.getName().startsWith(FLOOR_PREFIX)) {
				seq.removeROI(roi);
			}
		}
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
