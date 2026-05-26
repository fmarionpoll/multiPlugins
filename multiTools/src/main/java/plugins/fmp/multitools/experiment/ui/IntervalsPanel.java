package plugins.fmp.multitools.experiment.ui;

import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import icy.gui.viewer.Viewer;
import icy.sequence.Sequence;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.ExperimentDirectories;
import plugins.fmp.multitools.experiment.GenerationMode;
import plugins.fmp.multitools.experiment.NominalIntervalConfirmer;
import plugins.fmp.multitools.experiment.sequence.ImageLoader;
import plugins.fmp.multitools.experiment.ui.host.IntervalsHost;
import plugins.fmp.multitools.tools.JComponents.JComboBoxMs;

/**
 * Shared Intervals tab UI (multiCAFE / multiSPOTS96). Behaviour differences are
 * delegated to {@link IntervalsHost}.
 */
public class IntervalsPanel extends JPanel implements ItemListener {

	private static final long serialVersionUID = -5739112045358747277L;

	private static final String ANALYSIS_STEP_TOOLTIP = "<html>Duration added per frame in the analysis timeline.<br>"
			+ "<b>Refresh</b> sets this from image timestamps (filename or file date).<br>"
			+ "<b>Apply</b> saves it to the experiment; it drives camera/kymograph bins and time axes.<br>"
			+ "Override only if you subsample frames or need a deliberate time correction.</html>";

	Long val = 1L;
	Long min = 0L;
	Long max = 10000L;
	Long step = 1L;
	Long maxLast = 99999999L;
	public JSpinner indexFirstImageJSpinner = new JSpinner(new SpinnerNumberModel(val, min, max, step));
	public JComboBox<String> clipNumberImagesCombo = new JComboBox<String>(
			new String[] { "up to last frame", "clip N frames to" });
	public JSpinner fixedNumberOfImagesJSpinner = new JSpinner(new SpinnerNumberModel(maxLast, step, maxLast, step));
	public JButton updateToCurrentFrameButton = new JButton("Current frame");
	public JSpinner binSizeJSpinner = new JSpinner(new SpinnerNumberModel(1., 0., 1000., 1.));
	public JComboBoxMs binUnit = new JComboBoxMs();
	public JSpinner nominalIntervalJSpinner = new JSpinner(new SpinnerNumberModel(60, 1, 999, 1));
	public JButton applyButton = new JButton("Apply");
	public JButton refreshButton = new JButton("Refresh");
	private JLabel analysisIntervalLabel = new JLabel("Acquisition (from image times): \u2014");
	private JLabel classSummaryLabel = new JLabel(" ");
	private JButton advancedToggleButton = new JButton("Advanced...");
	private JPanel advancedPanel = new JPanel();
	private JPanel advancedPanel1 = new JPanel();

	private IntervalsHost host;
	private boolean updatingFromExperiment = false;

	public void init(GridLayout capLayout, IntervalsHost host) {
		this.host = host;
		setLayout(capLayout);

		int bWidth = 50;
		int bHeight = 21;
		Dimension dimension = new Dimension(bWidth, bHeight);
		indexFirstImageJSpinner.setPreferredSize(dimension);
		binSizeJSpinner.setPreferredSize(dimension);
		nominalIntervalJSpinner.setPreferredSize(dimension);
		fixedNumberOfImagesJSpinner.setPreferredSize(dimension);
//		updateNFramesButton.setPreferredSize(new Dimension(70, bHeight));
		nominalIntervalJSpinner.setValue(host.getDefaultNominalIntervalSec());

		FlowLayout layout1 = new FlowLayout(FlowLayout.LEFT);
		layout1.setVgap(1);

		JPanel panel0 = new JPanel(layout1);
		panel0.add(new JLabel("Frame:", SwingConstants.RIGHT));
		panel0.add(indexFirstImageJSpinner);
		panel0.add(clipNumberImagesCombo);
		panel0.add(fixedNumberOfImagesJSpinner);
		panel0.add(updateToCurrentFrameButton);
		panel0.add(applyButton);
		add(panel0);

		JPanel panel1 = new JPanel(layout1);
		panel1.add(analysisIntervalLabel);
		panel1.add(refreshButton);
		panel1.add(advancedToggleButton);
		add(panel1);

		advancedPanel.setLayout(layout1);
		JLabel analysisStepLabel = new JLabel("Analysis time step ", SwingConstants.RIGHT);
		analysisStepLabel.setToolTipText(ANALYSIS_STEP_TOOLTIP);
		binSizeJSpinner.setToolTipText(ANALYSIS_STEP_TOOLTIP);
		binUnit.setToolTipText(ANALYSIS_STEP_TOOLTIP);
		advancedPanel.add(analysisStepLabel);
		advancedPanel.add(binSizeJSpinner);
		advancedPanel.add(binUnit);
		add(advancedPanel);

		advancedPanel1.setLayout(layout1);
		advancedPanel1.add(new JLabel("  Nominal interval (s) ", SwingConstants.RIGHT));
		advancedPanel1.add(nominalIntervalJSpinner);
		advancedPanel1.add(classSummaryLabel);
		advancedPanel1.setVisible(false);
		add(advancedPanel1);

		fixedNumberOfImagesJSpinner.setVisible(false);
		defineActionListeners();
		clipNumberImagesCombo.addItemListener(this);
	}

	private void defineActionListeners() {
		applyButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) host.getExperimentsCombo().getSelectedItem();
				if (exp != null)
					setExperimentParameters(exp);
			}
		});

		updateToCurrentFrameButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) host.getExperimentsCombo().getSelectedItem();
				if (exp != null)
					applyStartFromCurrentViewerFrame(exp);
			}
		});

		refreshButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) host.getExperimentsCombo().getSelectedItem();
				if (exp != null)
					refreshBinSize(exp);
			}
		});

		advancedToggleButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				boolean show = !advancedPanel.isVisible();
				advancedPanel.setVisible(show);
				advancedToggleButton.setText(show ? "Hide advanced" : "Advanced...");
				advancedPanel1.setVisible(show);
				revalidate();
				repaint();
			}
		});

		indexFirstImageJSpinner.addChangeListener(new ChangeListener() {
			@Override
			public void stateChanged(ChangeEvent e) {
				if (updatingFromExperiment)
					return;
				long newValue = (long) indexFirstImageJSpinner.getValue();
				Experiment exp = (Experiment) host.getExperimentsCombo().getSelectedItem();
				if (exp != null && exp.getSeqCamData() != null
						&& exp.getSeqCamData().getImageLoader().getAbsoluteIndexFirstImage() != newValue) {
					exp.getSeqCamData().getImageLoader().setAbsoluteIndexFirstImage(newValue);
					List<String> imagesList = ExperimentDirectories
							.getImagesListFromPathV2(exp.getSeqCamData().getImageLoader().getImagesDirectory(), "jpg");
					exp.getSeqCamData().loadImageList(imagesList);
					long bin_ms = exp.getSeqCamData().getTimeManager().getBinImage_ms();
					exp.getSeqCamData().getTimeManager()
							.setBinFirst_ms(exp.getSeqCamData().getImageLoader().getAbsoluteIndexFirstImage() * bin_ms);
					exp.saveExperimentDescriptors();
					host.onFirstImageIndexChanged(exp);
				}
			}
		});

		fixedNumberOfImagesJSpinner.addChangeListener(new ChangeListener() {
			@Override
			public void stateChanged(ChangeEvent e) {
				if (updatingFromExperiment)
					return;
				Experiment exp = (Experiment) host.getExperimentsCombo().getSelectedItem();
				if (exp == null || exp.getSeqCamData() == null) {
					return;
				}
				long requestedCount = (long) fixedNumberOfImagesJSpinner.getValue();
				ImageLoader imgLoader = exp.getSeqCamData().getImageLoader();
				List<String> imagesOnDisk = (ArrayList<String>) ExperimentDirectories
						.getImagesListFromPathV2(imgLoader.getImagesDirectory(), "jpg");
				long absFirst = imgLoader.getAbsoluteIndexFirstImage();
				long available = Math.max(0L, imagesOnDisk.size() - absFirst);
				if (available <= 0) {
					fixedNumberOfImagesJSpinner.setValue(0L);
					imgLoader.setFixedNumberOfImages(0L);
					return;
				}
				long clampedCount = Math.min(Math.max(1L, requestedCount), available);
				if (clampedCount != requestedCount) {
					fixedNumberOfImagesJSpinner.setValue(clampedCount);
				}
				long absEndExclusive = absFirst + clampedCount;
				if (imgLoader.getFixedNumberOfImages() == absEndExclusive) {
					return;
				}
				imgLoader.setFixedNumberOfImages(absEndExclusive);
				exp.getSeqCamData().loadImageList(imagesOnDisk);
				long bin_ms = exp.getSeqCamData().getTimeManager().getBinImage_ms();
				exp.getSeqCamData().getTimeManager().setBinLast_ms(Math.max(0L, clampedCount - 1) * bin_ms);
				exp.saveExperimentDescriptors();
			}
		});

		binSizeJSpinner.addChangeListener(new ChangeListener() {
			@Override
			public void stateChanged(ChangeEvent e) {
				Experiment exp = (Experiment) host.getExperimentsCombo().getSelectedItem();
				if (exp != null && exp.getSeqCamData() != null) {
					long bin_ms = (long) (((double) binSizeJSpinner.getValue()) * binUnit.getMsUnitValue());
					exp.getSeqCamData().getTimeManager().setBinImage_ms(bin_ms);
					exp.setCamImageBin_ms(bin_ms);
					exp.setKymoBin_ms(bin_ms);
					exp.getSeqCamData().getTimeManager()
							.setBinFirst_ms(exp.getSeqCamData().getImageLoader().getAbsoluteIndexFirstImage() * bin_ms);
					exp.getSeqCamData().getTimeManager().setBinLast_ms(
							(exp.getSeqCamData().getImageLoader().getFixedNumberOfImages() - 1) * bin_ms);
				}
			}
		});

	}

	private void applyStartFromCurrentViewerFrame(Experiment exp) {
		if (exp.getSeqCamData() == null)
			return;
		Sequence seq = exp.getSeqCamData().getSequence();
		if (seq == null || seq.getSizeT() <= 0)
			return;
		Viewer v = seq.getFirstViewer();
		int t = v != null ? v.getPositionT() : 0;
		if (t < 0)
			t = 0;
		int sizeT = seq.getSizeT();
		if (t >= sizeT)
			t = sizeT - 1;
		long absFirst = exp.getSeqCamData().validToAbsoluteFrameIndex(t);
		updatingFromExperiment = true;
		try {
			indexFirstImageJSpinner.setValue(absFirst);
		} finally {
			updatingFromExperiment = false;
		}
		setExperimentParameters(exp);
	}

	private void setExperimentParameters(Experiment exp) {
		long bin_ms = (long) (((double) binSizeJSpinner.getValue()) * binUnit.getMsUnitValue());
		int nominalSec = ((Number) nominalIntervalJSpinner.getValue()).intValue();
		if (nominalSec < 1)
			nominalSec = Math.max(1, host.getDefaultNominalIntervalSec());

		long detectedMs = exp.getCamImageBin_ms();
		if (!validateBinAgainstDetected(bin_ms, detectedMs))
			return;

		long medianMs = detectedMs;
		if (medianMs > 0 && !NominalIntervalConfirmer.confirmNominalIfFarFromMedian(this, nominalSec, medianMs,
				exp.getNominalIntervalSec() >= 0))
			return;

		exp.getSeqCamData().getTimeManager().setBinImage_ms(bin_ms);
		exp.setCamImageBin_ms(bin_ms);
		exp.setKymoBin_ms(bin_ms);
		exp.setNominalIntervalSec(nominalSec);
		host.setDefaultNominalIntervalSec(nominalSec);
		host.saveViewOptions();

		long firstImageIndex = (long) indexFirstImageJSpinner.getValue();
		exp.getSeqCamData().getImageLoader().setAbsoluteIndexFirstImage(firstImageIndex);
		exp.getSeqCamData().getTimeManager()
				.setBinFirst_ms(exp.getSeqCamData().getImageLoader().getAbsoluteIndexFirstImage() * bin_ms);
		if (exp.getSeqCamData().getImageLoader().getFixedNumberOfImages() > 0)
			exp.getSeqCamData().getTimeManager()
					.setBinLast_ms((exp.getSeqCamData().getImageLoader().getFixedNumberOfImages() - 1) * bin_ms);
		else
			exp.getSeqCamData().getTimeManager()
					.setBinLast_ms((exp.getSeqCamData().getImageLoader().getNTotalFrames() - 1) * bin_ms);

		Viewer v = exp.getSeqCamData().getSequence().getFirstViewer();
		if (v != null)
			v.close();
		host.onAfterIntervalsApply(exp);
		exp.saveExperimentDescriptors();
	}

	public void getExptParms(Experiment exp) {
		updatingFromExperiment = true;
		try {
			refreshBinSize(exp);
			int nominal = exp.getNominalIntervalSec();
			if (nominal > 0)
				nominalIntervalJSpinner.setValue(nominal);
			else
				nominalIntervalJSpinner.setValue(host.getDefaultNominalIntervalSec());
			long bin_ms = exp.getSeqCamData().getTimeManager().getBinImage_ms();
			long dFirst = exp.getSeqCamData().getImageLoader().getAbsoluteIndexFirstImage();
			indexFirstImageJSpinner.setValue(dFirst);
			if (exp.getSeqCamData().getTimeManager().getBinLast_ms() <= 0)
				exp.getSeqCamData().getTimeManager()
						.setBinLast_ms((long) (exp.getSeqCamData().getImageLoader().getNTotalFrames() - 1) * bin_ms);
			fixedNumberOfImagesJSpinner.setValue(exp.getSeqCamData().getImageLoader().getFixedNumberOfImages());
		} finally {
			updatingFromExperiment = false;
		}
	}

	public void displayCamDataIntervals(Experiment exp) {
		getExptParms(exp);
		exp.getFileIntervalsFromSeqCamData();
	}

	private void refreshBinSize(Experiment exp) {
		exp.loadFileIntervalsFromSeqCamData();
		long bin_ms = exp.getSeqCamData().getTimeManager().getBinImage_ms();
		if (bin_ms > 0) {
			exp.setCamImageBin_ms(bin_ms);
			exp.setKymoBin_ms(bin_ms);
		}
		binUnit.setSelectedIndex(1);
		binSizeJSpinner.setValue(bin_ms / (double) binUnit.getMsUnitValue());
		if (exp.getNominalIntervalSec() < 0 && bin_ms > 0) {
			int suggestedSec = (int) Math.round(bin_ms / 1000.0);
			if (advancedPanel.isVisible()) {
				Integer chosenSec = NominalIntervalConfirmer.confirmUseMedianAsNominal(this, suggestedSec);
				if (chosenSec != null) {
					exp.setNominalIntervalSec(chosenSec);
					nominalIntervalJSpinner.setValue(chosenSec);
				} else
					nominalIntervalJSpinner.setValue(host.getDefaultNominalIntervalSec());
			} else {
				nominalIntervalJSpinner.setValue(suggestedSec);
			}
		}
		updateAnalysisIntervalLabel(exp);
		updateClassSummaryLabel(exp, bin_ms);
	}

	private void updateClassSummaryLabel(Experiment exp, long bin_ms) {
		if (exp == null) {
			classSummaryLabel.setText(" ");
			return;
		}
		long camMs = exp.getCamImageBin_ms();
		if (camMs <= 0)
			camMs = bin_ms;
		long effectiveMs = bin_ms > 0 ? bin_ms : camMs;
		int factor = 1;
		if (camMs > 0 && effectiveMs > 0)
			factor = (int) Math.max(1L, Math.round(effectiveMs / (double) camMs));
		GenerationMode mode = host.coerceGenerationMode(exp.getGenerationMode());
		String modeText;
		switch (mode) {
		case KYMOGRAPH:
			modeText = "kymograph";
			break;
		case DIRECT_FROM_STACK:
			modeText = "direct";
			break;
		default:
			modeText = "unknown";
			break;
		}
		int effSec = (int) Math.round(effectiveMs / 1000.0);
		classSummaryLabel.setText(String.format("  Class: factor %dx, %s, ~%d s/sample", factor, modeText, effSec));
	}

	private void updateAnalysisIntervalLabel(Experiment exp) {
		if (exp == null) {
			analysisIntervalLabel.setText("Acquisition (from image times): \u2014");
			return;
		}
		long med = exp.getLastAcquisitionMedianMs();
		long mean = exp.getLastAcquisitionSpanMeanMs();
		long chosen = exp.getCamImageBin_ms();
		if (chosen <= 0 && med <= 0 && mean <= 0) {
			analysisIntervalLabel.setText("Acquisition (from image times): \u2014");
			return;
		}
		String medS = med > 0 ? formatIntervalForLabel(med) : null;
		String meanS = mean > 0 ? formatIntervalForLabel(mean) : null;
		StringBuilder sb = new StringBuilder("Acquisition (from image times): ");
		if (medS != null) {
			sb.append("median ").append(medS);
			if (meanS != null && acquisitionMedianMeanDiffer(med, mean)) {
				sb.append(", span mean ").append(meanS);
			}
		} else if (meanS != null) {
			sb.append("span mean ").append(meanS);
		} else if (chosen > 0) {
			sb.append(formatIntervalForLabel(chosen));
		}
		analysisIntervalLabel.setText(sb.toString());
	}

	private static String formatIntervalForLabel(long ms) {
		double s = ms / 1000.0;
		if (s >= 10)
			return String.format(Locale.US, "%.0f s", s);
		return String.format(Locale.US, "%.2f s", s);
	}

	private static boolean acquisitionMedianMeanDiffer(long medianMs, long meanMs) {
		if (medianMs <= 0 || meanMs <= 0)
			return false;
		long d = Math.abs(medianMs - meanMs);
		if (d <= 500)
			return false;
		return d / (double) Math.max(medianMs, meanMs) > 0.05;
	}

	private boolean validateBinAgainstDetected(long requestedMs, long detectedMs) {
		if (requestedMs <= 0) {
			JOptionPane.showMessageDialog(this, "Analysis interval must be greater than zero.", "Invalid interval",
					JOptionPane.WARNING_MESSAGE);
			return false;
		}
		if (detectedMs <= 0)
			return true;
		double ratio = requestedMs / (double) detectedMs;
		if (ratio >= 0.5 && ratio <= 10.0)
			return true;
		String msg = String.format(
				"The requested analysis interval (%.1f s) is very different from the detected\n"
						+ "frame interval (%.1f s). This usually indicates a mistake.\n\nKeep this value anyway?",
				requestedMs / 1000.0, detectedMs / 1000.0);
		int choice = JOptionPane.showConfirmDialog(this, msg, "Unusual analysis interval", JOptionPane.YES_NO_OPTION,
				JOptionPane.WARNING_MESSAGE);
		return choice == JOptionPane.YES_OPTION;
	}

	@Override
	public void itemStateChanged(ItemEvent e) {
		if (e.getStateChange() == ItemEvent.SELECTED) {
			Object source = e.getSource();
			if (source instanceof JComboBox) {
				Experiment exp = (Experiment) host.getExperimentsCombo().getSelectedItem();
				if (exp != null) {
					boolean clipped = clipNumberImagesCombo.getSelectedIndex() == 1 ? true : false;
					fixedNumberOfImagesJSpinner.setVisible(clipped);
					if (!clipped) {
						fixedNumberOfImagesJSpinner.setValue((long) -1);
					} else {
						fixedNumberOfImagesJSpinner
								.setValue((long) exp.getSeqCamData().getImageLoader().getNTotalFrames());
					}
				}
			}
		}
	}
}
