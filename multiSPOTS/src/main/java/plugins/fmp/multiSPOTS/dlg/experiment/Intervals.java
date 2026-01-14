package plugins.fmp.multiSPOTS.dlg.experiment;

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

import plugins.fmp.multiSPOTS.MultiSPOTS;
import plugins.fmp.multiSPOTS.experiment.Experiment;
import plugins.fmp.multiSPOTS.experiment.ExperimentDirectories;
import plugins.fmp.multiSPOTS.tools.JComponents.JComboBoxMs;

public class Intervals extends JPanel implements ItemListener {
	/**
	 * 
	 */
	private static final long serialVersionUID = -5739112045358747277L;
	Long val = 1L; // set your own value, I used to check if it works
	Long min = 0L;
	Long max = 10000L;
	Long step = 1L;
	Long maxLast = 99999999L;
	JSpinner indexFirstImageJSpinner = new JSpinner(new SpinnerNumberModel(val, min, max, step));
	JComboBox<String> clipNumberImagesCombo = new JComboBox<String>(
			new String[] { "up to last frame acquired", "clip number of frames to" });
	JSpinner fixedNumberOfImagesJSpinner = new JSpinner(new SpinnerNumberModel(maxLast, step, maxLast, step));
	JSpinner binSizeJSpinner = new JSpinner(new SpinnerNumberModel(1., 0., 1000., 1.));
	JComboBoxMs binUnit = new JComboBoxMs();
	JButton applyButton = new JButton("Apply changes");
	JButton refreshButton = new JButton("Refresh");
	private MultiSPOTS parent0 = null;

	void init(GridLayout capLayout, MultiSPOTS parent0) {
		setLayout(capLayout);
		this.parent0 = parent0;

		int bWidth = 50;
		int bHeight = 21;
		Dimension dimension = new Dimension(bWidth, bHeight);
		indexFirstImageJSpinner.setPreferredSize(dimension);
		binSizeJSpinner.setPreferredSize(dimension);
		fixedNumberOfImagesJSpinner.setPreferredSize(dimension);

		FlowLayout layout1 = new FlowLayout(FlowLayout.LEFT);
		layout1.setVgap(1);

		JPanel panel0 = new JPanel(layout1);
		panel0.add(new JLabel("Frame:", SwingConstants.RIGHT));
		panel0.add(indexFirstImageJSpinner);
		panel0.add(clipNumberImagesCombo);
		panel0.add(fixedNumberOfImagesJSpinner);
		add(panel0);

		JPanel panel1 = new JPanel(layout1);
		panel1.add(new JLabel("Time between frames ", SwingConstants.RIGHT));
		panel1.add(binSizeJSpinner);
		panel1.add(binUnit);
		add(panel1);

		panel1.add(refreshButton);
		panel1.add(applyButton);

		fixedNumberOfImagesJSpinner.setVisible(false);
		defineActionListeners();
		clipNumberImagesCombo.addItemListener(this);
	}

	private void defineActionListeners() {
		applyButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) parent0.expListCombo.getSelectedItem();
				if (exp != null)
					setExperimentParameters(exp);
			}
		});

		refreshButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) parent0.expListCombo.getSelectedItem();
				if (exp != null)
					refreshBinSize(exp);
			}
		});

		indexFirstImageJSpinner.addChangeListener(new ChangeListener() {
			public void stateChanged(ChangeEvent e) {
				long newValue = (long) indexFirstImageJSpinner.getValue();
				Experiment exp = (Experiment) parent0.expListCombo.getSelectedItem();
				if (exp != null && exp.seqCamData.absoluteIndexFirstImage != newValue) {
					exp.seqCamData.absoluteIndexFirstImage = newValue;
					List<String> imagesList = ExperimentDirectories
							.getImagesListFromPathV2(exp.seqCamData.imagesDirectory, "jpg");
					exp.seqCamData.loadImageList(imagesList);
					long bin_ms = exp.seqCamData.binImage_ms;
					exp.seqCamData.binFirst_ms = exp.seqCamData.absoluteIndexFirstImage * bin_ms;
					exp.saveXML_MCExperiment();
				}
			}
		});

		fixedNumberOfImagesJSpinner.addChangeListener(new ChangeListener() {
			public void stateChanged(ChangeEvent e) {
				Experiment exp = (Experiment) parent0.expListCombo.getSelectedItem();
				long newValue = (long) fixedNumberOfImagesJSpinner.getValue();
				if (exp != null && exp.seqCamData.fixedNumberOfImages != newValue) {
					exp.seqCamData.fixedNumberOfImages = newValue;
					List<String> imagesList = (ArrayList<String>) ExperimentDirectories
							.getImagesListFromPathV2(exp.seqCamData.imagesDirectory, "jpg");
					exp.seqCamData.loadImageList(imagesList);
					long bin_ms = exp.seqCamData.binImage_ms;
					exp.seqCamData.binLast_ms = ((long) (fixedNumberOfImagesJSpinner.getValue())
							- exp.seqCamData.absoluteIndexFirstImage) * bin_ms;
				}
			}
		});

		binSizeJSpinner.addChangeListener(new ChangeListener() {
			public void stateChanged(ChangeEvent e) {
				Experiment exp = (Experiment) parent0.expListCombo.getSelectedItem();
				if (exp != null) {
					long bin_ms = (long) (((double) binSizeJSpinner.getValue()) * binUnit.getMsUnitValue());
					exp.seqCamData.binImage_ms = bin_ms;
					exp.seqCamData.binFirst_ms = exp.seqCamData.absoluteIndexFirstImage * bin_ms;
					exp.seqCamData.binLast_ms = (exp.seqCamData.fixedNumberOfImages - 1) * bin_ms;
				}
			}
		});

	}

	private void setExperimentParameters(Experiment exp) {
		exp.seqCamData.binImage_ms = (long) (((double) binSizeJSpinner.getValue()) * binUnit.getMsUnitValue());
		long bin_ms = exp.seqCamData.binImage_ms;
		exp.seqCamData.absoluteIndexFirstImage = (long) indexFirstImageJSpinner.getValue();
		exp.seqCamData.binFirst_ms = exp.seqCamData.absoluteIndexFirstImage * bin_ms;
		if (exp.seqCamData.fixedNumberOfImages > 0)
			exp.seqCamData.binLast_ms = (exp.seqCamData.fixedNumberOfImages - 1) * bin_ms;
		else
			exp.seqCamData.binLast_ms = (exp.seqCamData.nTotalFrames - 1) * bin_ms;
	}

	public void getExptParms(Experiment exp) {

		refreshBinSize(exp);

		long bin_ms = exp.seqCamData.binImage_ms;
		long dFirst = exp.seqCamData.absoluteIndexFirstImage;
		indexFirstImageJSpinner.setValue(dFirst);
		if (exp.seqCamData.binLast_ms <= 0)
			exp.seqCamData.binLast_ms = (long) (exp.seqCamData.nTotalFrames - 1) * bin_ms;
		fixedNumberOfImagesJSpinner.setValue(exp.seqCamData.fixedNumberOfImages);
		// exp.getFileIntervalsFromSeqCamData();
	}

	private void refreshBinSize(Experiment exp) {
		exp.loadFileIntervalsFromSeqCamData();
		binUnit.setSelectedIndex(1);
		binSizeJSpinner.setValue(exp.seqCamData.binImage_ms / (double) binUnit.getMsUnitValue());
	}

	@Override
	public void itemStateChanged(ItemEvent e) {
		if (e.getStateChange() == ItemEvent.SELECTED) {
			Object source = e.getSource();
			if (source instanceof JComboBox) {
				Experiment exp = (Experiment) parent0.expListCombo.getSelectedItem();
				if (exp != null) {
					boolean clipped = clipNumberImagesCombo.getSelectedIndex() == 1 ? true : false;
					fixedNumberOfImagesJSpinner.setVisible(clipped);
					if (!clipped) {
						fixedNumberOfImagesJSpinner.setValue((long) -1);
					} else {
						fixedNumberOfImagesJSpinner.setValue((long) exp.seqCamData.nTotalFrames);
					}
				}
			}
		}
	}
}
