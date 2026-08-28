package plugins.fmp.multicafe.dlg.capillaries;

import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

import icy.gui.frame.progress.ProgressFrame;
import icy.system.thread.ThreadUtil;
import plugins.fmp.multicafe.MultiCAFE;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.capillaries.Capillaries;
import plugins.fmp.multitools.experiment.capillary.Capillary;
import plugins.fmp.multitools.service.CapillaryLengthDetector;
import plugins.fmp.multitools.service.CapillaryLengthDetectorOptions;
import plugins.fmp.multitools.service.CapillaryLengthResult;
import plugins.fmp.multitools.tools.JComponents.CapillaryLengthMeasureDialog;
import plugins.fmp.multitools.tools.Logger;

public class Infos extends JPanel {
	/**
	 * 
	 */
	private static final long serialVersionUID = 4950182090521600937L;

	private JSpinner capillaryVolumeSpinner = new JSpinner(new SpinnerNumberModel(5., 0., 100., 1.));
	private JSpinner capillaryPixelsSpinner = new JSpinner(new SpinnerNumberModel(5, 0, 1000, 1));
	private JButton getCapillaryLengthButton = new JButton("pixels 1rst capillary");
	private JButton editCapillariesButton = new JButton("Edit capillaries infos...");
	private JButton autoMeasureButton = new JButton("Auto-measure all lengths");
	private JButton resetPixelsButton = new JButton("Reset all to single value");
	private JCheckBox allExperimentsCheckBox = new JCheckBox("all experiments");
	private JLabel calibrationStatusLabel = new JLabel("-");
	private MultiCAFE parent0 = null;
	private InfosCapillaryTable infosCapillaryTable = null;
	private List<Capillary> capillariesArrayCopy = new ArrayList<Capillary>();

	private static final String AUTO_MEASURE_TITLE = "Auto-measure capillary lengths";
	private static final String RESET_TITLE = "Reset capillary lengths";

	void init(GridLayout capLayout, MultiCAFE parent0) {
		setLayout(capLayout);
		this.parent0 = parent0;

		JPanel panel0 = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 1));
		panel0.add(new JLabel("volume (µl) ", SwingConstants.RIGHT));
		panel0.add(capillaryVolumeSpinner);
		panel0.add(new JLabel("length (pixels) ", SwingConstants.RIGHT));
		panel0.add(capillaryPixelsSpinner);
		panel0.add(getCapillaryLengthButton);
		add(panel0);

		JPanel panel1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 1));
		panel1.add(autoMeasureButton);
		panel1.add(resetPixelsButton);
		panel1.add(allExperimentsCheckBox);
		add(panel1);

		JPanel panel2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 1));
		panel2.add(editCapillariesButton);
		panel2.add(new JLabel("calibration: ", SwingConstants.RIGHT));
		panel2.add(calibrationStatusLabel);
		add(panel2);

		autoMeasureButton.setToolTipText(
				"Measure each capillary length inside its ROI, so peripheral capillaries get their own scale");
		resetPixelsButton.setToolTipText("Give every capillary the length (pixels) value above again");
		allExperimentsCheckBox.setToolTipText("Apply the auto-measure or the reset to the whole experiment list");

		defineActionListeners();
	}

	private void defineActionListeners() {
		getCapillaryLengthButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				double npixels = getLengthFirstCapillaryROI();
				capillaryPixelsSpinner.setValue((int) npixels);
			}
		});

		editCapillariesButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp != null) {
					exp.getCapillaries().transferDescriptionToCapillaries();
					if (infosCapillaryTable != null) {
						infosCapillaryTable.close();
					}
					infosCapillaryTable = new InfosCapillaryTable();
					infosCapillaryTable.initialize(parent0, capillariesArrayCopy);
					infosCapillaryTable.requestFocus();
				}
			}
		});

		autoMeasureButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				if (allExperimentsCheckBox.isSelected())
					autoMeasureAllExperiments();
				else
					autoMeasureCurrentExperiment();
			}
		});

		resetPixelsButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				if (allExperimentsCheckBox.isSelected())
					resetAllExperiments();
				else
					resetCurrentExperiment();
			}
		});
	}

	// set/ get

	public void setDlgInfosCapillaryDescriptors(Capillaries cap) {
		capillaryVolumeSpinner.setValue(cap.getCapillariesDescription().getVolume());
		capillaryPixelsSpinner.setValue(cap.getCapillariesDescription().getPixels());
		updateCalibrationStatus(cap);
	}

	void getCapillaryDescriptorsFromDlgInfos(Capillaries capList) {
		capList.getCapillariesDescription().setVolume((double) capillaryVolumeSpinner.getValue());
		capList.getCapillariesDescription().setPixels((int) capillaryPixelsSpinner.getValue());
	}

	public int getLengthFirstCapillaryROI() {
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		int npixels = 0;
		if (exp != null) {
			exp.getCapillaries().transferROIsFromSequence(exp.getSeqCamData());
			if (exp.getCapillaries().getList().size() > 0) {
				Capillary cap = exp.getCapillaries().getList().get(0);
				npixels = cap.getCapillaryROILength();
			}
		}
		return npixels;
	}

	// auto-measure

	private void autoMeasureCurrentExperiment() {
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		if (exp == null) {
			JOptionPane.showMessageDialog(this, "Select an experiment first.", AUTO_MEASURE_TITLE,
					JOptionPane.WARNING_MESSAGE);
			return;
		}
		exp.getCapillaries().transferROIsFromSequence(exp.getSeqCamData());
		getCapillaryDescriptorsFromDlgInfos(exp.getCapillaries());
		exp.getCapillaries().transferDescriptionToCapillaries();

		CapillaryLengthResult result = new CapillaryLengthDetector().measure(exp, buildDetectorOptions());
		if (!CapillaryLengthMeasureDialog.showAndConfirm(this, result, AUTO_MEASURE_TITLE))
			return;

		int updated = CapillaryLengthDetector.apply(result);
		saveCapillaries(exp);
		updateCalibrationStatus(exp.getCapillaries());
		JOptionPane.showMessageDialog(this,
				updated + " capillary(ies) now use their own pixel length.\n" + summaryLine(result),
				AUTO_MEASURE_TITLE, JOptionPane.INFORMATION_MESSAGE);
	}

	private void autoMeasureAllExperiments() {
		final int nExperiments = parent0.expListComboLazy.getItemCount();
		if (nExperiments < 1)
			return;
		int answer = JOptionPane.showConfirmDialog(this,
				"Measure capillary lengths on " + nExperiments + " experiment(s)?\n"
						+ "Reliable measures are applied automatically; a report is written to the log.",
				AUTO_MEASURE_TITLE, JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
		if (answer != JOptionPane.OK_OPTION)
			return;

		final double volume = ((Number) capillaryVolumeSpinner.getValue()).doubleValue();
		ThreadUtil.bgRun(new Runnable() {
			@Override
			public void run() {
				ProgressFrame progress = new ProgressFrame("Measuring capillary lengths");
				progress.setLength(nExperiments);
				CapillaryLengthDetector detector = new CapillaryLengthDetector();
				CapillaryLengthDetectorOptions options = buildDetectorOptions();
				int nUpdated = 0;
				int nExperimentsUpdated = 0;
				Logger.info("CapillaryLength,experiment,capillaries,median_px,min_px,max_px,spread_percent");

				for (int i = 0; i < nExperiments; i++) {
					Experiment exp = parent0.expListComboLazy.getItemAt(i);
					if (exp == null) {
						progress.incPosition();
						continue;
					}
					progress.setMessage("Experiment " + (i + 1) + " of " + nExperiments);
					try {
						exp.loadExperimentDescriptors();
						exp.load_capillaries_description_and_measures();
						exp.getCapillaries().getCapillariesDescription().setVolume(volume);
						exp.getCapillaries().transferDescriptionToCapillaries();

						CapillaryLengthResult result = detector.measure(exp, options);
						if (result.hasError()) {
							Logger.warn("CapillaryLength: " + exp.getResultsDirectory() + " - "
									+ result.getErrorMessage());
						} else {
							int updated = CapillaryLengthDetector.apply(result);
							if (updated > 0) {
								saveCapillaries(exp);
								nUpdated += updated;
								nExperimentsUpdated++;
							}
							Logger.info(String.format("CapillaryLength,%s,%d,%.1f,%.1f,%.1f,%.2f",
									exp.getResultsDirectory(), updated, result.getMedianPixels(),
									result.getMinPixels(), result.getMaxPixels(), result.getSpreadPercent()));
						}
					} catch (Exception e) {
						Logger.error("CapillaryLength: failed on " + exp.getResultsDirectory(), e);
					}
					progress.incPosition();
				}
				progress.close();

				final String message = nUpdated + " capillary(ies) updated in " + nExperimentsUpdated + " of "
						+ nExperiments + " experiment(s).\nPer-experiment values are listed in the log.";
				SwingUtilities.invokeLater(new Runnable() {
					@Override
					public void run() {
						updateCalibrationStatus(selectedCapillaries());
						JOptionPane.showMessageDialog(Infos.this, message, AUTO_MEASURE_TITLE,
								JOptionPane.INFORMATION_MESSAGE);
					}
				});
			}
		});
	}

	// reset to a single value

	private void resetCurrentExperiment() {
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		if (exp == null) {
			JOptionPane.showMessageDialog(this, "Select an experiment first.", RESET_TITLE,
					JOptionPane.WARNING_MESSAGE);
			return;
		}
		if (!confirmDiscardMeasuredLengths(exp.getCapillaries().countAutoMeasuredPixels()))
			return;

		getCapillaryDescriptorsFromDlgInfos(exp.getCapillaries());
		exp.getCapillaries().transferDescriptionToCapillaries(true);
		saveCapillaries(exp);
		updateCalibrationStatus(exp.getCapillaries());
		JOptionPane.showMessageDialog(this,
				"All capillaries use " + exp.getCapillaries().getCapillariesDescription().getPixels() + " pixels again.",
				RESET_TITLE, JOptionPane.INFORMATION_MESSAGE);
	}

	private void resetAllExperiments() {
		final int nExperiments = parent0.expListComboLazy.getItemCount();
		if (nExperiments < 1)
			return;
		final double volume = ((Number) capillaryVolumeSpinner.getValue()).doubleValue();
		final int pixels = ((Number) capillaryPixelsSpinner.getValue()).intValue();
		int answer = JOptionPane.showConfirmDialog(this,
				"Give every capillary of " + nExperiments + " experiment(s) a length of " + pixels + " pixels?\n"
						+ "Any auto-measured length will be discarded.",
				RESET_TITLE, JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
		if (answer != JOptionPane.OK_OPTION)
			return;

		ThreadUtil.bgRun(new Runnable() {
			@Override
			public void run() {
				ProgressFrame progress = new ProgressFrame("Resetting capillary lengths");
				progress.setLength(nExperiments);
				int nReset = 0;
				for (int i = 0; i < nExperiments; i++) {
					Experiment exp = parent0.expListComboLazy.getItemAt(i);
					if (exp == null) {
						progress.incPosition();
						continue;
					}
					progress.setMessage("Experiment " + (i + 1) + " of " + nExperiments);
					try {
						exp.loadExperimentDescriptors();
						exp.load_capillaries_description_and_measures();
						exp.getCapillaries().getCapillariesDescription().setVolume(volume);
						exp.getCapillaries().getCapillariesDescription().setPixels(pixels);
						exp.getCapillaries().transferDescriptionToCapillaries(true);
						saveCapillaries(exp);
						nReset++;
					} catch (Exception e) {
						Logger.error("CapillaryLength: reset failed on " + exp.getResultsDirectory(), e);
					}
					progress.incPosition();
				}
				progress.close();

				final String message = nReset + " of " + nExperiments + " experiment(s) reset to " + pixels
						+ " pixels.";
				SwingUtilities.invokeLater(new Runnable() {
					@Override
					public void run() {
						updateCalibrationStatus(selectedCapillaries());
						JOptionPane.showMessageDialog(Infos.this, message, RESET_TITLE,
								JOptionPane.INFORMATION_MESSAGE);
					}
				});
			}
		});
	}

	private boolean confirmDiscardMeasuredLengths(int nAutoMeasured) {
		if (nAutoMeasured < 1)
			return true;
		int answer = JOptionPane.showConfirmDialog(this,
				nAutoMeasured + " capillary(ies) currently use a length measured on the image.\n"
						+ "Replacing them by a single value discards that calibration. Continue?",
				RESET_TITLE, JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
		return answer == JOptionPane.OK_OPTION;
	}

	// helpers

	private CapillaryLengthDetectorOptions buildDetectorOptions() {
		return new CapillaryLengthDetectorOptions();
	}

	private Capillaries selectedCapillaries() {
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		return exp != null ? exp.getCapillaries() : null;
	}

	private void saveCapillaries(Experiment exp) {
		exp.saveMCCapillaries_Only();
		exp.save_capillaries_description_and_measures();
	}

	private String summaryLine(CapillaryLengthResult result) {
		if (result.countUsable() == 0)
			return "";
		return String.format("Lengths range from %.1f to %.1f px (%.1f%% distortion across the image).",
				result.getMinPixels(), result.getMaxPixels(), result.getSpreadPercent());
	}

	private void updateCalibrationStatus(Capillaries capillaries) {
		if (capillaries == null || capillaries.getList().isEmpty()) {
			calibrationStatusLabel.setText("-");
			return;
		}
		int total = capillaries.getList().size();
		int auto = capillaries.countAutoMeasuredPixels();
		if (auto == 0)
			calibrationStatusLabel
					.setText("single value (" + capillaries.getCapillariesDescription().getPixels() + " px)");
		else if (auto == total)
			calibrationStatusLabel.setText("per-capillary (auto-measured)");
		else
			calibrationStatusLabel.setText("mixed: " + auto + "/" + total + " auto-measured");
	}

}
