package plugins.fmp.multicafe.dlg.kymos;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import javax.swing.ButtonGroup;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;

import icy.util.StringUtil;
import plugins.fmp.multicafe.MultiCAFE;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.GenerationMode;
import plugins.fmp.multitools.series.BuildKymosFromCapillaries;
import plugins.fmp.multitools.series.options.BuildSeriesOptions;
import plugins.fmp.multitools.tools.JComponents.JComboBoxMs;

public class CreateKymos extends JPanel implements PropertyChangeListener {
	private static final long serialVersionUID = 1771360416354320887L;
	private static final int MAX_DOWNSAMPLE = 10;
	private String detectString = "Start";

	JButton startComputationButton = new JButton("Start");
	JSpinner diskRadiusSpinner = new JSpinner(new SpinnerNumberModel(3, 1, 100, 1));
	JCheckBox allSeriesCheckBox = new JCheckBox("ALL series (current to last)", false);
	JComboBox<String> downsampleCombo = new JComboBox<>();
	JLabel samplingHintLabel = new JLabel(" ");

	JRadioButton isFloatingFrameButton = new JRadioButton("all", true);
	JRadioButton isFixedFrameButton = new JRadioButton("from ", false);
	JSpinner startJSpinner = new JSpinner(new SpinnerNumberModel(0., 0., 10000., 1.));
	JSpinner endJSpinner = new JSpinner(new SpinnerNumberModel(240., 1., 99999999., 1.));
	JComboBoxMs intervalsUnit = new JComboBoxMs();

	EnumStatusComputation sComputation = EnumStatusComputation.START_COMPUTATION;
	private MultiCAFE parent0 = null;
	private BuildKymosFromCapillaries threadBuildKymo = null;

	void init(GridLayout capLayout, MultiCAFE parent0) {
		setLayout(capLayout);
		this.parent0 = parent0;

		FlowLayout layoutLeft = new FlowLayout(FlowLayout.LEFT);
		layoutLeft.setVgap(0);

		JPanel panel0 = new JPanel(layoutLeft);
		((FlowLayout) panel0.getLayout()).setVgap(1);
		allSeriesCheckBox.setToolTipText("Build kymographs for the current experiment through the last in the browse list.");
		panel0.add(startComputationButton);
		panel0.add(allSeriesCheckBox);
		add(panel0);

		DefaultComboBoxModel<String> dsModel = new DefaultComboBoxModel<>();
		for (int i = 1; i <= MAX_DOWNSAMPLE; i++) {
			dsModel.addElement("x" + i);
		}
		downsampleCombo.setModel(dsModel);
		downsampleCombo.setSelectedIndex(0);
		downsampleCombo.setToolTipText("Keep every Nth camera frame; sets kymograph bin step and results subfolder.");

		JPanel panel1 = new JPanel(layoutLeft);
		panel1.add(new JLabel("area around ROIs", SwingConstants.RIGHT));
		panel1.add(diskRadiusSpinner);
		panel1.add(new JLabel("downsample"));
		panel1.add(downsampleCombo);
		panel1.add(samplingHintLabel);
		add(panel1);

		JPanel panel2 = new JPanel(layoutLeft);
		panel2.add(new JLabel("Analyze "));
		isFixedFrameButton.setToolTipText("Limit analysis to the time range below instead of all frames.");
		panel2.add(isFloatingFrameButton);
		panel2.add(isFixedFrameButton);
		panel2.add(startJSpinner);
		startJSpinner.setPreferredSize(new Dimension(80, 20));
		panel2.add(new JLabel(" to "));
		panel2.add(endJSpinner);
		endJSpinner.setPreferredSize(new Dimension(80, 20));
		panel2.add(intervalsUnit);
		intervalsUnit.setSelectedIndex(2);
		add(panel2);

		enableIntervalButtons(false);
		ButtonGroup group = new ButtonGroup();
		group.add(isFloatingFrameButton);
		group.add(isFixedFrameButton);

		defineActionListeners();
	}

	public void syncFromExperiment(Experiment exp) {
		if (exp == null) {
			samplingHintLabel.setText(" ");
			return;
		}
		int stored = Math.max(1, exp.getKymoSubsampleFactor());
		if (stored >= 1 && stored <= MAX_DOWNSAMPLE) {
			downsampleCombo.setSelectedIndex(stored - 1);
		}
		updateSamplingHint(exp);
	}

	private void defineActionListeners() {
		startComputationButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				if (startComputationButton.getText().equals(detectString))
					startComputation();
				else
					stopComputation();
			}
		});

		allSeriesCheckBox.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Color color = Color.BLACK;
				if (allSeriesCheckBox.isSelected())
					color = Color.RED;
				allSeriesCheckBox.setForeground(color);
				startComputationButton.setForeground(color);
			}
		});

		isFixedFrameButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				enableIntervalButtons(true);
			}
		});

		isFloatingFrameButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				enableIntervalButtons(false);
			}
		});

		downsampleCombo.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				updateSamplingHint((Experiment) parent0.expListComboLazy.getSelectedItem());
			}
		});
	}

	private void updateSamplingHint(Experiment exp) {
		if (exp == null) {
			samplingHintLabel.setText(" ");
			return;
		}
		exp.getFileIntervalsFromSeqCamData();
		long medianMs = exp.getCamImageBin_ms();
		if (medianMs <= 0 && exp.getFrameTimeScale() != null && !exp.getFrameTimeScale().isEmpty()) {
			medianMs = exp.getFrameTimeScale().medianDeltaMs();
		}
		int factor = getDownsampleFactor();
		if (medianMs > 0) {
			double sec = medianMs / 1000.0;
			samplingHintLabel.setText(String.format("Native ~%.1fs · keep every %d frame(s)", sec, Integer.valueOf(factor)));
		} else {
			samplingHintLabel.setText("Native sampling unknown · keep every " + factor + " frame(s)");
		}
	}

	private void enableIntervalButtons(boolean isSelected) {
		startJSpinner.setEnabled(isSelected);
		endJSpinner.setEnabled(isSelected);
		intervalsUnit.setEnabled(isSelected);
	}

	private int getDownsampleFactor() {
		int idx = downsampleCombo.getSelectedIndex();
		return Math.max(1, idx + 1);
	}

	private BuildSeriesOptions initBuildParameters(Experiment exp) {
		BuildSeriesOptions options = new BuildSeriesOptions();
		options.expList = parent0.expListComboLazy;
		options.expList.index0 = parent0.expListComboLazy.getSelectedIndex();
		if (allSeriesCheckBox.isSelected())
			options.expList.index1 = parent0.expListComboLazy.getItemCount() - 1;
		else
			options.expList.index1 = options.expList.index0;

		options.isFrameFixed = getIsFixedFrame();
		options.t_Ms_First = getStartMs();
		options.t_Ms_Last = getEndMs();
		options.kymoDownsampleFactor = getDownsampleFactor();

		options.diskRadius = (int) diskRadiusSpinner.getValue();
		options.doRegistration = false;
		options.referenceFrame = 0;
		options.concurrentDisplay = false;
		options.doCreateBinDir = true;
		options.parent0Rect = parent0.mainFrame.getBoundsInternal();
		options.binSubDirectory = exp.getBinNameFromKymoFrameStep();

		return options;
	}

	private void startComputation() {
		sComputation = EnumStatusComputation.STOP_COMPUTATION;
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		if (exp == null)
			return;

		BuildSeriesOptions options = initBuildParameters(exp);
		int factor = Math.max(1, options.kymoDownsampleFactor);

		exp.getFileIntervalsFromSeqCamData();
		long medianMs = exp.getCamImageBin_ms();
		if (medianMs <= 0 && exp.getFrameTimeScale() != null && !exp.getFrameTimeScale().isEmpty()) {
			medianMs = exp.getFrameTimeScale().medianDeltaMs();
		}
		if (medianMs <= 0) {
			medianMs = 1000L;
		}
		// Folder name = native camera sampling only (avoid bin_5 vs bin_10 proliferation).
		// Effective column step and subsampleFactor carry the ×N downsample.
		long effectiveBinMs = medianMs * (long) factor;
		options.t_Ms_BinDuration = effectiveBinMs;
		int cameraSec = (int) Math.max(1, Math.round(medianMs / 1000.0));

		exp.setNominalIntervalSec(cameraSec);
		exp.setKymoBin_ms(effectiveBinMs);
		exp.setKymoSubsampleFactor(factor);
		if (exp.getActiveBinDescription() != null) {
			exp.getActiveBinDescription().setCameraIntervalMs(medianMs);
			exp.getActiveBinDescription().setSubsampleFactor(factor);
		}
		exp.setGenerationMode(GenerationMode.KYMOGRAPH);
		options.binSubDirectory = exp.getBinNameFromKymoFrameStep();

		exp.releaseKymographSequence();
		parent0.paneCapillaries.tabFile.saveCapillaries_file(exp);

		threadBuildKymo = new BuildKymosFromCapillaries();
		threadBuildKymo.options = options;
		threadBuildKymo.addPropertyChangeListener(this);
		threadBuildKymo.execute();
		startComputationButton.setText("STOP");
	}

	private void stopComputation() {
		if (threadBuildKymo != null && !threadBuildKymo.stopFlag) {
			threadBuildKymo.stopFlag = true;
		}
	}

	boolean getIsFixedFrame() {
		return isFixedFrameButton.isSelected();
	}

	long getStartMs() {
		return (long) ((double) startJSpinner.getValue() * intervalsUnit.getMsUnitValue());
	}

	long getEndMs() {
		return (long) ((double) endJSpinner.getValue() * intervalsUnit.getMsUnitValue());
	}

	@Override
	public void propertyChange(PropertyChangeEvent evt) {
		if (StringUtil.equals("thread_ended", evt.getPropertyName())) {
			startComputationButton.setText(detectString);
			Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
			if (exp != null) {
				parent0.expListComboLazy.expListBinSubDirectory = exp.getBinSubDirectory();
			}
		}
	}

}
