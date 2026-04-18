package plugins.fmp.multicafe.dlg.experiment;

import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.ArrayList;
import java.util.List;

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
import plugins.fmp.multicafe.MultiCAFE;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.ExperimentDirectories;
import plugins.fmp.multitools.experiment.GenerationMode;
import plugins.fmp.multitools.experiment.NominalIntervalConfirmer;
import plugins.fmp.multitools.experiment.sequence.ImageLoader;
import plugins.fmp.multitools.tools.JComponents.JComboBoxMs;

public class Intervals extends JPanel implements ItemListener {
	/**
	 * 
	 */
	private static final long serialVersionUID = -5739112045358747277L;
	Long val = 1L;
	Long min = 0L;
	Long max = 10000L;
	Long step = 1L;
	Long maxLast = 99999999L;
	JSpinner indexFirstImageJSpinner = new JSpinner(new SpinnerNumberModel(val, min, max, step));
	JComboBox<String> clipNumberImagesCombo = new JComboBox<String>(
			new String[] { "up to last frame acquired", "clip number of frames to" });
	JSpinner fixedNumberOfImagesJSpinner = new JSpinner(new SpinnerNumberModel(maxLast, step, maxLast, step));
	JButton updateNFramesButton = new JButton("Update");
	JSpinner binSizeJSpinner = new JSpinner(new SpinnerNumberModel(1., 0., 1000., 1.));
	JComboBoxMs binUnit = new JComboBoxMs();
	JSpinner nominalIntervalJSpinner = new JSpinner(new SpinnerNumberModel(60, 1, 999, 1));
	JButton applyButton = new JButton("Apply changes");
	JButton refreshButton = new JButton("Refresh");
	private JLabel analysisIntervalLabel = new JLabel("Analysis interval: \u2014");
	private JLabel classSummaryLabel = new JLabel(" ");
	private JButton advancedToggleButton = new JButton("Advanced...");
	private JPanel advancedPanel = new JPanel();
	private JPanel advancedPanel1 = new JPanel();
	private MultiCAFE parent0 = null;
	private boolean updatingFromExperiment = false;

	void init(GridLayout capLayout, MultiCAFE parent0) {
		setLayout(capLayout);
		this.parent0 = parent0;

		int bWidth = 50;
		int bHeight = 21;
		Dimension dimension = new Dimension(bWidth, bHeight);
		indexFirstImageJSpinner.setPreferredSize(dimension);
		binSizeJSpinner.setPreferredSize(dimension);
		nominalIntervalJSpinner.setPreferredSize(dimension);
		fixedNumberOfImagesJSpinner.setPreferredSize(dimension);
		updateNFramesButton.setPreferredSize(new Dimension(70, bHeight));

		FlowLayout layout1 = new FlowLayout(FlowLayout.LEFT);
		layout1.setVgap(1);

		JPanel panel0 = new JPanel(layout1);
		panel0.add(new JLabel("Frame:", SwingConstants.RIGHT));
		panel0.add(indexFirstImageJSpinner);
		panel0.add(clipNumberImagesCombo);
		panel0.add(fixedNumberOfImagesJSpinner);
		panel0.add(updateNFramesButton);
		panel0.add(applyButton);
		add(panel0);

		JPanel panel1 = new JPanel(layout1);
		panel1.add(analysisIntervalLabel);
		panel1.add(refreshButton);
		panel1.add(advancedToggleButton);
		add(panel1);

		advancedPanel.setLayout(layout1);
		advancedPanel.add(new JLabel("Time between frames ", SwingConstants.RIGHT));
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
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp != null)
					setExperimentParameters(exp);
			}
		});

		refreshButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
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
			public void stateChanged(ChangeEvent e) {
				if (updatingFromExperiment)
					return;
				long newValue = (long) indexFirstImageJSpinner.getValue();
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
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
					parent0.paneExperiment.updateViewerForSequenceCam(exp);
					parent0.paneExperiment.tabOptions.applyCentralViewOptionsToCamViewer(exp);
				}
			}
		});

		fixedNumberOfImagesJSpinner.addChangeListener(new ChangeListener() {
			public void stateChanged(ChangeEvent e) {
				if (updatingFromExperiment)
					return;
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
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
				// Persist as absolute end index (exclusive) to match Experiment.xml semantics:
				// fixedNumberOfImages = total number of images from frame 0 on disk.
				imgLoader.setFixedNumberOfImages(absEndExclusive);
				exp.getSeqCamData().loadImageList(imagesOnDisk);
				long bin_ms = exp.getSeqCamData().getTimeManager().getBinImage_ms();
				exp.getSeqCamData().getTimeManager().setBinLast_ms(Math.max(0L, clampedCount - 1) * bin_ms);
				exp.saveExperimentDescriptors();
			}
		});

		binSizeJSpinner.addChangeListener(new ChangeListener() {
			public void stateChanged(ChangeEvent e) {
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
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

		updateNFramesButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp == null || exp.getSeqCamData() == null) {
					return;
				}
				ImageLoader imgLoader = exp.getSeqCamData().getImageLoader();
				List<String> imagesOnDisk = (ArrayList<String>) ExperimentDirectories
						.getImagesListFromPathV2(imgLoader.getImagesDirectory(), "jpg");
				long absFirst = imgLoader.getAbsoluteIndexFirstImage();
				long available = Math.max(0L, imagesOnDisk.size() - absFirst);
				if (available <= 0) {
					fixedNumberOfImagesJSpinner.setValue(0L);
					imgLoader.setFixedNumberOfImages(0L);
					imgLoader.setNTotalFrames(0);
					return;
				}
				// Set end to last image on disk (exclusive).
				imgLoader.setFixedNumberOfImages(imagesOnDisk.size());
				fixedNumberOfImagesJSpinner.setValue(available);
				exp.getSeqCamData().loadImageList(imagesOnDisk);
				exp.saveExperimentDescriptors();
			}
		});

	}

	private void setExperimentParameters(Experiment exp) {
		long bin_ms = (long) (((double) binSizeJSpinner.getValue()) * binUnit.getMsUnitValue());
		int nominalSec = ((Number) nominalIntervalJSpinner.getValue()).intValue();
		if (nominalSec < 1)
			nominalSec = 60;

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
		parent0.viewOptions.setDefaultNominalIntervalSec(nominalSec);
		parent0.viewOptions.save(parent0.getPreferences("viewOptions"));

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
		parent0.paneBrowse.panelLoadSave.closeCurrentExperiment();
		List<String> imagesList = ExperimentDirectories
				.getImagesListFromPathV2(exp.getSeqCamData().getImageLoader().getImagesDirectory(), "jpg");
		exp.getSeqCamData().loadImageList(imagesList);
		parent0.paneExperiment.updateDialogs(exp);
		parent0.paneExperiment.updateViewerForSequenceCam(exp);
		parent0.paneExperiment.tabOptions.applyCentralViewOptionsToCamViewer(exp);
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
				nominalIntervalJSpinner.setValue(parent0.viewOptions.getDefaultNominalIntervalSec());
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
				// Only bother the user with the nominal-confirmation dialog when they
				// have explicitly opened the Advanced panel. For the 99% case the
				// detected median is the right value and the read-only label is enough.
				Integer chosenSec = NominalIntervalConfirmer.confirmUseMedianAsNominal(this, suggestedSec);
				if (chosenSec != null) {
					exp.setNominalIntervalSec(chosenSec);
					nominalIntervalJSpinner.setValue(chosenSec);
				} else
					nominalIntervalJSpinner.setValue(parent0.viewOptions.getDefaultNominalIntervalSec());
			} else {
				// Pre-populate the spinner so the advanced panel is coherent if opened
				// later, without recording a nominal choice on behalf of the user.
				nominalIntervalJSpinner.setValue(suggestedSec);
			}
		}
		updateAnalysisIntervalLabel(exp, bin_ms);
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
		GenerationMode mode = exp.getGenerationMode();
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

	private void updateAnalysisIntervalLabel(Experiment exp, long bin_ms) {
		if (bin_ms <= 0) {
			analysisIntervalLabel.setText("Analysis interval: \u2014");
			return;
		}
		int displaySec = (int) Math.round(bin_ms / 1000.0);
		int nominal = exp != null ? exp.getNominalIntervalSec() : -1;
		if (nominal > 0 && Math.abs(nominal - displaySec) <= 1) {
			analysisIntervalLabel.setText(String.format("Analysis interval: %d s (from frames)", nominal));
		} else {
			analysisIntervalLabel.setText(String.format("Analysis interval: %d s (from frames)", displaySec));
		}
	}

	/**
	 * Rejects bin sizes that are clearly inconsistent with the detected frame
	 * interval (below half or above ten times). Accepts anything when no detection
	 * is available (first load).
	 */
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
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
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
