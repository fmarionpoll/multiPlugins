package plugins.fmp.multicafe.dlg.levels;

import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.geom.Rectangle2D;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import icy.canvas.IcyCanvas;
import icy.gui.viewer.Viewer;
import icy.sequence.Sequence;
import plugins.fmp.multicafe.MultiCAFE;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.capillaries.CapillariesKymosMapper;
import plugins.fmp.multitools.experiment.sequence.SequenceCamData;
import plugins.fmp.multitools.service.DarkFrameDetector;
import plugins.fmp.multitools.service.DarkFrameDetector.Options;
import plugins.fmp.multitools.service.ExperimentService;
import plugins.fmp.multitools.tools.Logger;
import plugins.fmp.multitools.tools.imageTransform.ImageTransformEnums;
import plugins.fmp.multitools.tools.overlay.OverlayThreshold;

public class CleanGaps extends JPanel {
	private static final long serialVersionUID = 6031521157029550040L;

	private JButton displayRect = new JButton("Area monitored");
	private JSpinner thresholdSpinner = new JSpinner(new SpinnerNumberModel(20., 0., 255., 1.));
	private JCheckBox overlayCheckBox = new JCheckBox("overlay");
	private JCheckBox allSeriesCheckBox = new JCheckBox("ALL (current to last)", false);
	private JCheckBox detectBlackCheckbBox = new JCheckBox("Detect black zones from images", false);
	private JCheckBox cleanMeasuresCheckBox = new JCheckBox("Clean capillary measures", false);
	private JButton runButton = new JButton("Run...");
	private JLabel resultSummary = new JLabel(" ");

	private Options options = new Options();
	private OverlayThreshold overlayThreshold = null;
	private Sequence overlaySequence = null;
	private MultiCAFE parent0 = null;

	// -----------------------------------------------------

	void init(GridLayout capLayout, MultiCAFE parent0) {
		setLayout(capLayout);
		this.parent0 = parent0;

		FlowLayout layoutLeft = new FlowLayout(FlowLayout.LEFT);

		JPanel panel0 = new JPanel(layoutLeft);
		((FlowLayout) panel0.getLayout()).setVgap(0);
		panel0.add(displayRect);
		panel0.add(new JLabel(" threshold:"));
		panel0.add(thresholdSpinner);
		panel0.add(overlayCheckBox);
		add(panel0);

		JPanel panel1 = new JPanel(layoutLeft);
		panel1.add(detectBlackCheckbBox);
		panel1.add(cleanMeasuresCheckBox);
		add(panel1);

		JPanel panel2 = new JPanel(layoutLeft);
		panel2.add(runButton);
		panel2.add(allSeriesCheckBox);
		add(panel2);

		JPanel panel3 = new JPanel(layoutLeft);
		panel3.add(resultSummary);
		add(panel3);

		options.rectMonitor.setName("rectMonitor");
		defineActionListeners();
		defineItemListeners();
		thresholdSpinner.addChangeListener(new ChangeListener() {
			@Override
			public void stateChanged(ChangeEvent e) {
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp != null)
					updateOverlayThreshold(exp);
			}
		});

		parent0.expListComboLazy.addItemListener(new ItemListener() {
			@Override
			public void itemStateChanged(ItemEvent e) {
				if (e.getStateChange() != ItemEvent.SELECTED)
					return;
				Object item = parent0.expListComboLazy.getSelectedItem();
				if (item instanceof Experiment)
					updateFromExperiment((Experiment) item);
			}
		});

		Experiment selected = (Experiment) parent0.expListComboLazy.getSelectedItem();
		if (selected != null)
			updateFromExperiment(selected);
	}

	private void defineItemListeners() {
		overlayCheckBox.addItemListener(new ItemListener() {
			@Override
			public void itemStateChanged(ItemEvent e) {
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp == null)
					return;
				if (overlayCheckBox.isSelected())
					addOverlayToSequence(exp);
				else
					removeOverlay(exp);
			}
		});
	}

	private void defineActionListeners() {
		displayRect.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp == null)
					return;
				SequenceCamData seqCam = exp.getSeqCamData();
				if (seqCam != null && seqCam.getSequence() != null) {
					seqCam.getSequence().addROI(options.rectMonitor);
				}
			}
		});

		runButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				runFromSelectedToLastIfNeeded();
			}
		});
	}

	private void runFromSelectedToLastIfNeeded() {
		int index0 = parent0.expListComboLazy.getSelectedIndex();
		if (index0 < 0)
			return;
		int index1 = allSeriesCheckBox.isSelected() ? parent0.expListComboLazy.getItemCount() - 1 : index0;
		if (index1 < index0)
			return;
		boolean doDetect = detectBlackCheckbBox.isSelected();
		boolean doClean = cleanMeasuresCheckBox.isSelected();
		if (!doDetect && !doClean)
			return;

		// Keep selection stable and show progress in log.
		for (int index = index0; index <= index1; index++) {
			parent0.expListComboLazy.setSelectedIndex(index);
			Object item = parent0.expListComboLazy.getSelectedItem();
			if (!(item instanceof Experiment))
				continue;
			Experiment exp = (Experiment) item;
			Logger.info("CleanGaps: run " + (index - index0 + 1) + "/" + (index1 - index0 + 1));
			if (doDetect)
				runDetectionFromSeqCamData(exp);
			if (doClean)
				runCleanCapillaryMeasures(exp);
		}
	}

	private void runDetectionFromSeqCamData(Experiment exp) {
		if (exp == null) {
			return;
		}

		ExperimentService expService = new ExperimentService();
		SequenceCamData seqCam = exp.getSeqCamData();
		if (seqCam == null || seqCam.getSequence() == null) {
			seqCam = expService.openSequenceCamData(exp);
		}
		if (seqCam == null || seqCam.getSequence() == null) {
			Logger.warn("CleanGaps: no camera sequence available for experiment");
			return;
		}

		Rectangle2D rect = options.rectMonitor.getRectangle();
		options.roiX = (int) rect.getMinX();
		options.roiY = (int) rect.getMinY();
		options.roiWidth = (int) rect.getWidth();
		options.roiHeight = (int) rect.getHeight();
		options.thresholdMean = (double) thresholdSpinner.getValue();

		// Threshold is left as 0 for now; user will choose later based on sums.
		options.thresholdSum = 0L;

		DarkFrameDetector detector = new DarkFrameDetector();
		int[] lightStatus = detector.runDetection(exp, options);
		if (lightStatus == null) {
			Logger.warn("CleanGaps: dark frame detection failed");
			return;
		}

		Logger.info("CleanGaps: computed light status for " + lightStatus.length + " frames");

		int dark = 0;
		for (int s : lightStatus)
			if (s == 0)
				dark++;
		resultSummary.setText("Dark: " + dark + ", Light: " + (lightStatus.length - dark));

		Rectangle2D r = options.rectMonitor.getRectangle();
		exp.setDarkFrameThresholdMean(((Number) thresholdSpinner.getValue()).doubleValue());
		exp.setDarkFrameRoiX(r.getMinX());
		exp.setDarkFrameRoiY(r.getMinY());
		exp.setDarkFrameRoiWidth(r.getWidth());
		exp.setDarkFrameRoiHeight(r.getHeight());
	}

	private void runCleanCapillaryMeasures(Experiment exp) {
		SequenceCamData seqCam = exp.getSeqCamData();
		int[] lightStatus = seqCam != null ? seqCam.getLightStatusPerFrame() : null;
		if (lightStatus == null || lightStatus.length == 0) {
			runDetectionFromSeqCamData(exp);
			seqCam = exp.getSeqCamData();
			lightStatus = seqCam != null ? seqCam.getLightStatusPerFrame() : null;
		}
		if (lightStatus == null || lightStatus.length == 0) {
			Logger.warn("CleanGaps: run detection first or no camera sequence");
			return;
		}

		if (!exp.load_capillaries_description_and_measures()) {
			Logger.warn("CleanGaps: could not load capillary measures");
			return;
		}
		if (exp.getCapillaries() == null || exp.getCapillaries().getList().isEmpty()) {
			Logger.warn("CleanGaps: no capillaries for this experiment");
			return;
		}

		exp.getCapillaries().clearMeasuresAtDarkFrames(lightStatus);
		if (exp.getSeqKymos() != null)
			CapillariesKymosMapper.pushCapillaryMeasuresToKymos(exp.getCapillaries(), exp.getSeqKymos());

		boolean saved = exp.save_capillaries_description_and_measures();
		if (!saved)
			Logger.warn("CleanGaps: could not save capillary measures");
		Logger.info("CleanGaps: cleared capillary measures at dark frames (saved=" + saved + ")");
	}

	private void updateFromExperiment(Experiment exp) {
		if (exp == null)
			return;
		thresholdSpinner.setValue(exp.getDarkFrameThresholdMean());
		options.rectMonitor.setBounds2D(new Rectangle2D.Double(exp.getDarkFrameRoiX(), exp.getDarkFrameRoiY(),
				exp.getDarkFrameRoiWidth(), exp.getDarkFrameRoiHeight()));
		if (overlayThreshold != null && overlaySequence != null && exp.getSeqCamData() != null
				&& exp.getSeqCamData().getSequence() == overlaySequence)
			updateOverlayThreshold(exp);
	}

	private void addOverlayToSequence(Experiment exp) {
		if (exp.getSeqCamData() == null || exp.getSeqCamData().getSequence() == null)
			return;
		Sequence seq = exp.getSeqCamData().getSequence();
		if (overlayThreshold != null && overlaySequence != null && overlaySequence != seq) {
			removeOverlayFromSequence(overlaySequence);
			overlaySequence = null;
		}
		if (overlayThreshold == null) {
			overlayThreshold = new OverlayThreshold(seq);
		} else {
			seq.removeOverlay(overlayThreshold);
		}
		overlayThreshold.setSequence(seq);
		seq.addOverlay(overlayThreshold);
		overlaySequence = seq;
		updateOverlayThreshold(exp);
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
		overlayThreshold.painterChanged();
		seq.overlayChanged(overlayThreshold);
		seq.dataChanged();
	}

	private void removeOverlay(Experiment exp) {
		if (exp.getSeqCamData() == null || exp.getSeqCamData().getSequence() == null)
			return;
		removeOverlayFromSequence(exp.getSeqCamData().getSequence());
		overlaySequence = null;
	}

	private void removeOverlayFromSequence(Sequence seq) {
		if (seq == null)
			return;
		Viewer v = seq.getFirstViewer();
		if (v != null) {
			IcyCanvas canvas = v.getCanvas();
			if (canvas != null && overlayThreshold != null && canvas.hasLayer(overlayThreshold))
				canvas.removeLayer(overlayThreshold);
		}
		seq.removeOverlay(overlayThreshold);
	}

	private void updateOverlayThreshold(Experiment exp) {
		if (overlayThreshold == null || overlaySequence == null)
			return;
		if (exp.getSeqCamData() == null || exp.getSeqCamData().getSequence() != overlaySequence)
			return;
		int threshold = (int) Math.round(((Number) thresholdSpinner.getValue()).doubleValue());
		overlayThreshold.setThresholdSingle(threshold, ImageTransformEnums.RGB, false);
		overlayThreshold.setClipBounds(options.rectMonitor.getRectangle());
		overlayThreshold.painterChanged();
		overlaySequence.overlayChanged(overlayThreshold);
		overlaySequence.dataChanged();
	}

}
