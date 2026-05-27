package plugins.fmp.multiSPOTS96.dlg.kymograph;

import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;

import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;

import icy.gui.dialog.MessageDialog;
import icy.util.StringUtil;
import plugins.fmp.multiSPOTS96.MultiSPOTS96;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.NominalIntervalConfirmer;
import plugins.fmp.multitools.series.BuildKymosFromCageSpots;
import plugins.fmp.multitools.series.options.BuildSeriesOptions;
import plugins.fmp.multitools.tools.JComponents.JComboBoxMs;

/**
 * Experimental cage kymographs (vertical midline per spot, stacked per cage).
 */
public class BuildPanel extends JPanel implements PropertyChangeListener {

	private static final long serialVersionUID = 1L;

	private final String detectString = "Start";
	private JButton startComputationButton = new JButton("Start");
	private JSpinner diskRadiusSpinner = new JSpinner(new SpinnerNumberModel(3, 1, 100, 1));
	private JCheckBox allSeriesCheckBox = new JCheckBox("ALL series (current to last)", false);
	private JSpinner binSize = new JSpinner(new SpinnerNumberModel(60., 1., 1000., 1.));
	private JComboBoxMs binUnit = new JComboBoxMs();

	private JRadioButton isFloatingFrameButton = new JRadioButton("all", true);
	private JRadioButton isFixedFrameButton = new JRadioButton("from ", false);
	private JSpinner startJSpinner = new JSpinner(new SpinnerNumberModel(0., 0., 10000., 1.));
	private JSpinner endJSpinner = new JSpinner(new SpinnerNumberModel(240., 1., 99999999., 1.));
	private JComboBoxMs intervalsUnit = new JComboBoxMs();

	private JButton openBinFolderButton = new JButton("Open results bin");

	private MultiSPOTS96 parent0;
	private BuildKymosFromCageSpots threadBuildKymo;

	void init(GridLayout capLayout, MultiSPOTS96 parent0) {
		setLayout(capLayout);
		this.parent0 = parent0;

		FlowLayout layoutLeft = new FlowLayout(FlowLayout.LEFT);
		layoutLeft.setVgap(0);

		JPanel panel0 = new JPanel(layoutLeft);
		((FlowLayout) panel0.getLayout()).setVgap(1);
		panel0.add(startComputationButton);
		panel0.add(allSeriesCheckBox);
		panel0.add(openBinFolderButton);
		add(panel0);

		JPanel panel1 = new JPanel(layoutLeft);
		panel1.add(new JLabel("area around ROIs", SwingConstants.RIGHT));
		panel1.add(diskRadiusSpinner);
		panel1.add(new JLabel("analysis interval "));
		panel1.add(binSize);
		panel1.add(binUnit);
		binUnit.setSelectedIndex(1);
		add(panel1);

		JPanel panel2 = new JPanel(layoutLeft);
		panel2.add(new JLabel("Analyze "));
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

	private void defineActionListeners() {
		startComputationButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				if (startComputationButton.getText().equals(detectString)) {
					startComputation();
				} else {
					stopComputation();
				}
			}
		});

		allSeriesCheckBox.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				java.awt.Color color = java.awt.Color.BLACK;
				if (allSeriesCheckBox.isSelected()) {
					color = java.awt.Color.RED;
				}
				allSeriesCheckBox.setForeground(color);
				startComputationButton.setForeground(color);
			}
		});

		isFixedFrameButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				enableIntervalButtons(true);
			}
		});

		isFloatingFrameButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				enableIntervalButtons(false);
			}
		});

		openBinFolderButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				openResultsBinFolder();
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

		options.isFrameFixed = isFixedFrameButton.isSelected();
		options.t_Ms_First = getStartMs();
		options.t_Ms_Last = getEndMs();
		options.t_Ms_BinDuration = (long) ((double) binSize.getValue() * (double) binUnit.getMsUnitValue());

		options.diskRadius = (int) diskRadiusSpinner.getValue();
		options.concurrentDisplay = false;
		options.doCreateBinDir = true;
		options.parent0Rect = parent0.mainFrame.getBoundsInternal();
		options.binSubDirectory = exp.getBinNameFromKymoFrameStep();

		return options;
	}

	private void startComputation() {
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		if (exp == null) {
			return;
		}
		int comboSel = parent0.expListComboLazy.getSelectedIndex();
		int nExp = parent0.expListComboLazy.getItemCount();
		if (comboSel < 0 || nExp <= 0) {
			MessageDialog.showDialog("Select an experiment in the list before building kymographs.",
					MessageDialog.WARNING_MESSAGE);
			return;
		}

		BuildSeriesOptions options = initBuildParameters(exp);
		long binMs = options.t_Ms_BinDuration;
		int nominalSec = (int) Math.max(1, Math.round(binMs / 1000.0));
		long medianMs = exp.getSeqCamData() != null ? exp.getCamImageBin_ms() : 0;
		if (medianMs > 0 && !NominalIntervalConfirmer.confirmNominalIfFarFromMedian(this, nominalSec, medianMs,
				exp.getNominalIntervalSec() >= 0)) {
			return;
		}

		exp.setNominalIntervalSec(nominalSec);
		exp.setKymoBin_ms(binMs);

		threadBuildKymo = new BuildKymosFromCageSpots();
		threadBuildKymo.options = options;
		threadBuildKymo.addPropertyChangeListener(this);
		parent0.setSuppressExperimentOpenOnComboProgrammaticChange(true);
		threadBuildKymo.execute();
		startComputationButton.setText("STOP");
	}

	private void stopComputation() {
		if (threadBuildKymo != null && !threadBuildKymo.stopFlag) {
			threadBuildKymo.stopFlag = true;
		}
	}

	private long getStartMs() {
		return (long) ((double) startJSpinner.getValue() * intervalsUnit.getMsUnitValue());
	}

	private long getEndMs() {
		return (long) ((double) endJSpinner.getValue() * intervalsUnit.getMsUnitValue());
	}

	private void openResultsBinFolder() {
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		if (exp == null) {
			return;
		}
		String dir = exp.getKymosBinFullDirectory();
		if (dir == null) {
			return;
		}
		File folder = new File(dir);
		if (!folder.isDirectory()) {
			return;
		}
		if (!Desktop.isDesktopSupported()) {
			return;
		}
		try {
			Desktop.getDesktop().open(folder);
		} catch (IOException ex) {
			// best-effort
		}
	}

	@Override
	public void propertyChange(PropertyChangeEvent evt) {
		String n = evt.getPropertyName();
		if (StringUtil.equals("thread_ended", n) || StringUtil.equals("thread_done", n)) {
			parent0.setSuppressExperimentOpenOnComboProgrammaticChange(false);
			startComputationButton.setText(detectString);
			Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
			if (exp != null) {
				parent0.expListComboLazy.expListBinSubDirectory = exp.getBinSubDirectory();
			}
		}
	}
}
