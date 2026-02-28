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
		panel1.add(new JLabel("Time between frames ", SwingConstants.RIGHT));
		panel1.add(binSizeJSpinner);
		panel1.add(binUnit);
		panel1.add(new JLabel("Nominal interval (s) ", SwingConstants.RIGHT));
		panel1.add(nominalIntervalJSpinner);
		panel1.add(refreshButton);
		add(panel1);

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
				long requested = (long) fixedNumberOfImagesJSpinner.getValue();
				ImageLoader imgLoader = exp.getSeqCamData()
						.getImageLoader();
				List<String> imagesOnDisk = (ArrayList<String>) ExperimentDirectories
						.getImagesListFromPathV2(imgLoader.getImagesDirectory(), "jpg");
				long available = Math.max(0L, imagesOnDisk.size() - imgLoader.getAbsoluteIndexFirstImage());
				if (available <= 0) {
					fixedNumberOfImagesJSpinner.setValue(0L);
					imgLoader.setFixedNumberOfImages(0L);
					imgLoader.setNTotalFrames(0);
					return;
				}
				long clamped = Math.min(Math.max(1L, requested), available);
				if (clamped != requested) {
					fixedNumberOfImagesJSpinner.setValue(clamped);
				}
				if (imgLoader.getFixedNumberOfImages() == clamped) {
					return;
				}
				imgLoader.setFixedNumberOfImages(clamped);
				imgLoader.setNTotalFrames((int) clamped);
				exp.getSeqCamData().loadImageList(imagesOnDisk);
				long bin_ms = exp.getSeqCamData().getTimeManager().getBinImage_ms();
				exp.getSeqCamData().getTimeManager()
						.setBinLast_ms((clamped - imgLoader.getAbsoluteIndexFirstImage()) * bin_ms);
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
				ImageLoader imgLoader = exp.getSeqCamData()
						.getImageLoader();
				List<String> imagesOnDisk = (ArrayList<String>) ExperimentDirectories
						.getImagesListFromPathV2(imgLoader.getImagesDirectory(), "jpg");
				long available = Math.max(0L, imagesOnDisk.size() - imgLoader.getAbsoluteIndexFirstImage());
				if (available <= 0) {
					fixedNumberOfImagesJSpinner.setValue(0L);
					imgLoader.setFixedNumberOfImages(0L);
					imgLoader.setNTotalFrames(0);
					return;
				}
				imgLoader.setFixedNumberOfImages(available);
				imgLoader.setNTotalFrames((int) available);
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

		long medianMs = exp.getCamImageBin_ms();
		if (medianMs > 0 && !NominalIntervalConfirmer.confirmNominalIfFarFromMedian(this, nominalSec, medianMs, exp.getNominalIntervalSec() >= 0))
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
			if (NominalIntervalConfirmer.confirmUseMedianAsNominal(this, suggestedSec)) {
				exp.setNominalIntervalSec(suggestedSec);
				nominalIntervalJSpinner.setValue(suggestedSec);
			} else
				nominalIntervalJSpinner.setValue(parent0.viewOptions.getDefaultNominalIntervalSec());
		}
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
