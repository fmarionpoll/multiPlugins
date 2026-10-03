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
import icy.sequence.Sequence;
import icy.util.StringUtil;
import plugins.fmp.multiSPOTS.MultiSPOTS;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.cage.Cage;
import plugins.fmp.multitools.experiment.spot.Spot;
import plugins.fmp.multitools.series.AnalyzeSpotLineDeficits;
import plugins.fmp.multitools.series.options.BuildSeriesOptions;
import plugins.fmp.multitools.service.KymoImageTransforms;
import plugins.fmp.multitools.service.SpotLineDeficitAnalyzer;
import plugins.fmp.multitools.service.SpotLineDeficitAnalyzer.SpotCross;
import plugins.fmp.multitools.service.SpotLineDeficitAnalyzer.SpotGeom;
import plugins.fmp.multitools.tools.imageTransform.ImageTransformEnums;
import plugins.kernel.roi.roi2d.ROI2DLine;

/**
 * Line analysis: an X through each spot. Each sample is the mean of a band
 * across the line. Floor is the tips outside the circles.
 */
public class AnalysisPanel2 extends JPanel implements PropertyChangeListener {

	private static final long serialVersionUID = 1L;

	private static final String ANALYZE_LABEL = "Analyze";
	private static final String STOP_LABEL = "STOP";
	private static final String LINE_ROI_PREFIX = "lineFloor_";

	public static final String PROPERTY_KYMO_RESULT_UPDATED = "kymoLineResultUpdated";

	private final MultiSPOTS parent0;
	private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);

	private final JSpinner madMultiplierSpinner = new JSpinner(new SpinnerNumberModel(5.0, 0.5, 30.0, 0.5));
	private final JSpinner initialBinsSpinner = new JSpinner(new SpinnerNumberModel(5, 1, 500, 1));
	private final JSpinner flankPxSpinner = new JSpinner(new SpinnerNumberModel(40, 0, 2000, 1));
	private final JSpinner bandWidthSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 21, 2));
	private final JCheckBox insectGateCheckBox = new JCheckBox("Insect filter (exclude)", true);
	private final JComboBox<ImageTransformEnums> insectTransformCombo = new JComboBox<>(
			KymoImageTransforms.METRIC_CHOICES);
	private final JComboBox<String> insectDirectionCombo = new JComboBox<>(new String[] { "metric ≤", "metric >" });
	private final JSpinner insectThresholdSpinner = new JSpinner(new SpinnerNumberModel(50, 0, 512, 1));
	private final JButton analyzeButton = new JButton(ANALYZE_LABEL);
	private final JToggleButton showLinesButton = new JToggleButton("Show crosses");
	private final JCheckBox allSeriesCheckBox = new JCheckBox("ALL series (current to last)", false);
	private final JLabel statusLabel = new JLabel(" ", SwingConstants.LEFT);

	private AnalyzeSpotLineDeficits analyzeThread;

	public AnalysisPanel2(MultiSPOTS parent0) {
		super(new GridLayout(4, 1));
		this.parent0 = parent0;
		FlowLayout left = new FlowLayout(FlowLayout.LEFT);
		left.setVgap(0);
		narrowSpinner(madMultiplierSpinner, 4);
		narrowSpinner(initialBinsSpinner, 4);
		narrowSpinner(flankPxSpinner, 4);
		narrowSpinner(bandWidthSpinner, 4);
		narrowSpinner(insectThresholdSpinner, 4);
		bandWidthSpinner.setToolTipText(
				"Pixels averaged across each arm of the X. 1 is the single-pixel line. 3 averages the line and one pixel on each side, the same idea as the vertical kymograph strip. Wider bands reduce pixel noise until the band is wider than the dye.");
		insectTransformCombo.setSelectedItem(ImageTransformEnums.B_RGB);
		insectGateCheckBox.setToolTipText(
				"Drop a time bin when a fly covers at least 8% of the cross. Uncheck to keep those pixels.");

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
		params.add(new JLabel("width (px)"));
		params.add(bandWidthSpinner);
		add(params);

		JPanel insect = new JPanel(left);
		insect.add(insectGateCheckBox);
		insect.add(insectTransformCombo);
		insect.add(insectDirectionCombo);
		insect.add(insectThresholdSpinner);
		add(insect);

		JPanel hint = new JPanel(left);
		hint.add(showLinesButton);
		hint.add(new JLabel("Cyan = cross on each spot, green = floor used as zero."));
		add(hint);

		showLinesButton.addActionListener(e -> refreshFloorLines());
		flankPxSpinner.addChangeListener(e -> refreshLinesIfShown());
		bandWidthSpinner.addChangeListener(e -> refreshLinesIfShown());

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
		ImageTransformEnums insectTf = insectTransformCombo.getSelectedItem() instanceof ImageTransformEnums
				? (ImageTransformEnums) insectTransformCombo.getSelectedItem()
				: ImageTransformEnums.B_RGB;
		return new SpotLineDeficitAnalyzer.Params(((Number) madMultiplierSpinner.getValue()).doubleValue(),
				((Number) initialBinsSpinner.getValue()).intValue(),
				((Number) flankPxSpinner.getValue()).intValue(), SpotLineDeficitAnalyzer.DEFAULT_SMOOTH_BINS,
				insectGateCheckBox.isSelected(), insectTf, ((Number) insectThresholdSpinner.getValue()).intValue(),
				insectDirectionCombo.getSelectedIndex() == 1, ((Number) bandWidthSpinner.getValue()).intValue());
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

	private void refreshFloorLines() {
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		Sequence seq = exp != null && exp.getSeqCamData() != null ? exp.getSeqCamData().getSequence() : null;
		if (seq == null) {
			showLinesButton.setSelected(false);
			statusLabel.setText("Open an experiment to show the lines.");
			return;
		}
		removeFloorLines(seq);
		if (!showLinesButton.isSelected()) {
			statusLabel.setText(" ");
			return;
		}
		int width = seq.getSizeX();
		int height = seq.getSizeY();
		int flank = ((Number) flankPxSpinner.getValue()).intValue();
		int band = Math.max(1, ((Number) bandWidthSpinner.getValue()).intValue());
		int lineStroke = Math.max(2, band);
		int tipStroke = Math.max(3, band);
		int nLines = 0;
		if (exp.getCages() != null && exp.getCages().cagesList != null && exp.getSpots() != null) {
			for (Cage cage : exp.getCages().cagesList) {
				if (cage == null) {
					continue;
				}
				List<Spot> spots = cage.getSpotList(exp.getSpots());
				if (spots == null || spots.isEmpty()) {
					continue;
				}
				List<SpotGeom> geoms = new ArrayList<>(spots.size());
				for (Spot spot : spots) {
					geoms.add(AnalyzeSpotLineDeficits.geomOf(spot));
				}
				for (SpotCross cross : SpotLineDeficitAnalyzer.crosses(geoms, width, height, flank)) {
					if (cross == null) {
						continue;
					}
					addLine(seq, cross.x0, cross.y0, cross.x1, cross.y1, Color.CYAN, lineStroke, nLines, "a");
					addLine(seq, cross.u0, cross.v0, cross.u1, cross.v1, Color.CYAN, lineStroke, nLines, "b");
					for (int i = 0; i < cross.tipX0.length; i++) {
						addLine(seq, cross.tipX0[i], cross.tipY0[i], cross.tipX1[i], cross.tipY1[i], Color.GREEN,
								tipStroke, nLines, "zero" + i);
					}
					nLines++;
				}
			}
		}
		String widthNote = band > 1 ? ", width " + band + " px" : "";
		statusLabel.setText(nLines + " cross(es)" + widthNote + ". Green tips are the floor.");
		Viewer viewer = seq.getFirstViewer();
		if (viewer != null) {
			viewer.toFront();
		}
	}

	private void refreshLinesIfShown() {
		if (showLinesButton.isSelected()) {
			refreshFloorLines();
		}
	}

	private static void addLine(Sequence seq, int x0, int y0, int x1, int y1, Color color, int stroke, int row,
			String kind) {
		ROI2DLine roi = new ROI2DLine(x0, y0, x1, y1);
		roi.setName(LINE_ROI_PREFIX + kind + "_" + row);
		roi.setColor(color);
		roi.setStroke(stroke);
		roi.setReadOnly(true);
		seq.addROI(roi);
	}

	private static void removeFloorLines(Sequence seq) {
		List<ROI> copy = new ArrayList<>(seq.getROIs());
		for (ROI roi : copy) {
			if (roi != null && roi.getName() != null && roi.getName().startsWith(LINE_ROI_PREFIX)) {
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
