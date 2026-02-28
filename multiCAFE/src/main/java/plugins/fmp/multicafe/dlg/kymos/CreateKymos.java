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
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;

import icy.util.StringUtil;
import plugins.fmp.multicafe.MultiCAFE;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.NominalIntervalConfirmer;
import plugins.fmp.multitools.series.BuildKymosFromCapillaries;
import plugins.fmp.multitools.series.options.BuildSeriesOptions;
import plugins.fmp.multitools.tools.JComponents.JComboBoxMs;

public class CreateKymos extends JPanel implements PropertyChangeListener {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1771360416354320887L;
	private String detectString = "Start";

	JButton startComputationButton = new JButton("Start");
	JSpinner diskRadiusSpinner = new JSpinner(new SpinnerNumberModel(3, 1, 100, 1));
	JCheckBox allSeriesCheckBox = new JCheckBox("ALL series (current to last)", false);
	JCheckBox selectedCapCheckBox = new JCheckBox("selected capillary", false);
	JSpinner binSize = new JSpinner(new SpinnerNumberModel(1., 1., 1000., 1.));
	JComboBoxMs binUnit = new JComboBoxMs();

	JRadioButton isFloatingFrameButton = new JRadioButton("all", true);
	JRadioButton isFixedFrameButton = new JRadioButton("from ", false);
	JSpinner startJSpinner = new JSpinner(new SpinnerNumberModel(0., 0., 10000., 1.));
	JSpinner endJSpinner = new JSpinner(new SpinnerNumberModel(240., 1., 99999999., 1.));
	JComboBoxMs intervalsUnit = new JComboBoxMs();

	EnumStatusComputation sComputation = EnumStatusComputation.START_COMPUTATION;
	private MultiCAFE parent0 = null;
	private BuildKymosFromCapillaries threadBuildKymo = null;

	// -----------------------------------------------------

	void init(GridLayout capLayout, MultiCAFE parent0) {
		setLayout(capLayout);
		this.parent0 = parent0;

		FlowLayout layoutLeft = new FlowLayout(FlowLayout.LEFT);
		layoutLeft.setVgap(0);

		JPanel panel0 = new JPanel(layoutLeft);
		((FlowLayout) panel0.getLayout()).setVgap(1);
		panel0.add(startComputationButton);
		panel0.add(allSeriesCheckBox);
		panel0.add(selectedCapCheckBox);
		add(panel0);

		JPanel panel1 = new JPanel(layoutLeft);
		panel1.add(new JLabel("area around ROIs", SwingConstants.RIGHT));
		panel1.add(diskRadiusSpinner);
		panel1.add(new JLabel("bin size "));
		panel1.add(binSize);
		panel1.add(binUnit);
		binUnit.setSelectedIndex(2);
		add(panel1);

		JPanel panel2 = new JPanel(layoutLeft);
		panel2.add(new JLabel("Analyze "));
		panel2.add(isFloatingFrameButton);
		panel2.add(isFixedFrameButton);
		panel2.add(startJSpinner);
		startJSpinner.setPreferredSize(new Dimension(80, 20));
		panel2.add(new JLabel(" to "));
		panel2.add(endJSpinner);
		startJSpinner.setPreferredSize(new Dimension(80, 20));
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
		if (exp == null)
			return;
		int nominal = exp.getNominalIntervalSec();
		int value = nominal > 0 ? nominal : parent0.viewOptions.getDefaultNominalIntervalSec();
		binSize.setValue(Double.valueOf(Math.max(1, value)));
		binUnit.setSelectedIndex(2);
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
	}

	private void enableIntervalButtons(boolean isSelected) {
		startJSpinner.setEnabled(isSelected);
		endJSpinner.setEnabled(isSelected);
		intervalsUnit.setEnabled(isSelected);
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
		options.t_Ms_BinDuration = (long) ((double) binSize.getValue() * (double) binUnit.getMsUnitValue());

		options.diskRadius = (int) diskRadiusSpinner.getValue();
		options.doRegistration = false; // doRegistrationCheckBox.isSelected();
		options.referenceFrame = 0; // (int) startFrameSpinner.getValue();
		options.concurrentDisplay = false;
		options.doCreateBinDir = true;
		options.parent0Rect = parent0.mainFrame.getBoundsInternal();
		options.binSubDirectory = exp.getBinNameFromKymoFrameStep();

		options.kymoFirst = 0;
		options.kymoLast = exp.getCapillaries().getList().size() - 1;
		if (selectedCapCheckBox.isSelected()) {
			int t = exp.getCapillaries().getSelectedCapillary();
			if (t >= 0) {
				options.kymoFirst = t;
				options.kymoLast = t;
			}
		}
		return options;
	}

	private void startComputation() {
		sComputation = EnumStatusComputation.STOP_COMPUTATION;
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		if (exp == null)
			return;

		int nominalSec = ((Number) binSize.getValue()).intValue();
		if (nominalSec < 1)
			nominalSec = 60;
		long medianMs = exp.getSeqCamData() != null ? exp.getCamImageBin_ms() : 0;
		if (medianMs > 0 && !NominalIntervalConfirmer.confirmNominalIfFarFromMedian(this, nominalSec, medianMs, exp.getNominalIntervalSec() >= 0))
			return;

		exp.setNominalIntervalSec(nominalSec);
		exp.setKymoBin_ms(nominalSec * 1000L);

		if (exp.getSeqKymos() != null && exp.getSeqKymos().getSequence() != null)
			exp.getSeqKymos().getSequence().close();
		exp.setSeqKymos(null);
		parent0.paneCapillaries.tabFile.saveCapillaries_file(exp);

		threadBuildKymo = new BuildKymosFromCapillaries();
		threadBuildKymo.options = initBuildParameters(exp);
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
		return (long) ((double) startJSpinner.getValue() * binUnit.getMsUnitValue());
	}

	long getEndMs() {
		return (long) ((double) endJSpinner.getValue() * binUnit.getMsUnitValue());
	}

	@Override
	public void propertyChange(PropertyChangeEvent evt) {
		if (StringUtil.equals("thread_ended", evt.getPropertyName())) {
			startComputationButton.setText(detectString);
		}
	}

}
