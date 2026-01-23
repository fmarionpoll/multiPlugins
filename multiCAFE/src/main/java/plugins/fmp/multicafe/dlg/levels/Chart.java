package plugins.fmp.multicafe.dlg.levels;

import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JRadioButton;

import icy.gui.frame.IcyFrame;
import icy.gui.viewer.Viewer;
import icy.gui.viewer.ViewerEvent;
import icy.gui.viewer.ViewerEvent.ViewerEventType;
import icy.gui.viewer.ViewerListener;
import icy.sequence.DimensionId;
import icy.sequence.Sequence;
import icy.sequence.SequenceEvent;
import icy.sequence.SequenceListener;
import plugins.fmp.multicafe.MultiCAFE;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.cage.Cage;
import plugins.fmp.multitools.experiment.capillaries.Capillaries;
import plugins.fmp.multitools.experiment.capillary.Capillary;
import plugins.fmp.multitools.tools.results.EnumResults;
import plugins.fmp.multitools.tools.results.ResultsOptions;
import plugins.fmp.multitools.tools.results.ResultsOptionsBuilder;

public class Chart extends JPanel implements SequenceListener, ViewerListener {
	/**
	 * 
	 */
	private static final long serialVersionUID = -7079184380174992501L;

	private ChartCageArrayFrame chartCageArrayFrame = null;
	private ChartCombinedFrame chartCombinedFrame = null;
	private MultiCAFE parent0 = null;

	// Listener references for dynamic updates
	private Viewer kymographViewer = null;
	private Experiment currentExperiment = null;

	private EnumResults[] measures = new EnumResults[] { //
			EnumResults.TOPRAW, //
			EnumResults.TOPLEVEL, //
			EnumResults.BOTTOMLEVEL, //
			EnumResults.TOPLEVEL_LR, //
			EnumResults.DERIVEDVALUES, //
			EnumResults.SUMGULPS, //
			EnumResults.SUMGULPS_LR, //
			EnumResults.NBGULPS, //
			EnumResults.AMPLITUDEGULPS, //
			EnumResults.TTOGULP, //
			EnumResults.MARKOV_CHAIN, //
			EnumResults.AUTOCORREL, //
			EnumResults.CROSSCORREL };
	private JComboBox<EnumResults> resultTypeComboBox = new JComboBox<EnumResults>(measures);

	// private JCheckBox correctEvaporationCheckbox = new JCheckBox("correct
	// evaporation", false);
	private JButton displayResultsButton = new JButton("Display results");
	private JButton axisOptionsButton = new JButton("Axis options");
	private JRadioButton displayAllButton = new JRadioButton("all cages");
	private JRadioButton displaySelectedButton = new JRadioButton("cage selected");
	private JRadioButton viewGridButton = new JRadioButton("grid");
	private JRadioButton viewCombinedButton = new JRadioButton("combined");

	private AxisOptions graphOptions = null;

	void init(GridLayout capLayout, MultiCAFE parent0) {
		setLayout(capLayout);
		this.parent0 = parent0;
		setLayout(capLayout);
		FlowLayout layout = new FlowLayout(FlowLayout.LEFT);
		layout.setVgap(0);

		JPanel panel = new JPanel(layout);
		panel.add(resultTypeComboBox);
		add(panel);

		JPanel panelView = new JPanel(layout);
		panelView.add(viewGridButton);
		panelView.add(viewCombinedButton);
		add(panelView);

		JPanel panel1 = new JPanel(layout);
		panel1.add(displayAllButton);
		panel1.add(displaySelectedButton);
		add(panel1);

		JPanel panel04 = new JPanel(layout);
		panel04.add(displayResultsButton);
		panel04.add(axisOptionsButton);
		add(panel04);

		ButtonGroup group1 = new ButtonGroup();
		group1.add(displayAllButton);
		group1.add(displaySelectedButton);
		displayAllButton.setSelected(true);

		ButtonGroup groupView = new ButtonGroup();
		groupView.add(viewGridButton);
		groupView.add(viewCombinedButton);
		viewGridButton.setSelected(true);

		resultTypeComboBox.setSelectedIndex(0);
		defineActionListeners();
	}

	private void defineActionListeners() {

		resultTypeComboBox.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp != null) {
					displayChartPanels(exp); // displayGraphsPanels(exp);
				}
			}
		});

		viewGridButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				axisOptionsButton.setEnabled(true);
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp != null) {
					displayChartPanels(exp);
				}
			}
		});

		viewCombinedButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				// AxisOptions is currently implemented for grid charts only
				axisOptionsButton.setEnabled(false);
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp != null) {
					displayChartPanels(exp);
				}
			}
		});

		displayResultsButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp != null) {
					displayChartPanels(exp); // displayGraphsPanels(exp);
				}
			}
		});

		axisOptionsButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp != null) {
					if (graphOptions != null) {
						graphOptions.close();
					}
					if (chartCageArrayFrame != null && viewGridButton.isSelected()) {
						graphOptions = new AxisOptions();
						graphOptions.initialize(parent0, chartCageArrayFrame);
					}
				}
			}
		});

		displayAllButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp != null) {
					removeSelectionListeners(exp);
					displayChartPanels(exp);
				}
			}
		});

		displaySelectedButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp != null) {
					displayChartPanels(exp);
					addSelectionListeners(exp);
				}
			}
		});
	}

	// ------------------------------------------------ OPTION 2

	public void displayChartPanels(Experiment exp) {
		exp.getSeqCamData().getSequence().removeListener(this);
		exp.dispatchCapillariesToCages();
		EnumResults exportType = (EnumResults) resultTypeComboBox.getSelectedItem();
		if (isThereAnyDataToDisplay(exp, exportType)) {
			if (viewCombinedButton.isSelected()) {
				chartCombinedFrame = plotCapillaryMeasuresToCombinedChart(exp, exportType, chartCombinedFrame);
			} else {
				chartCageArrayFrame = plotCapillaryMeasuresToChart(exp, exportType, chartCageArrayFrame);
			}
		}
		exp.getSeqCamData().getSequence().addListener(this);
	}

	private ChartCombinedFrame plotCapillaryMeasuresToCombinedChart(Experiment exp, EnumResults resultType,
			ChartCombinedFrame iChart) {
		// Dispose existing frame if it exists
		if (iChart != null && iChart.getMainChartFrame() != null) {
			IcyFrame oldFrame = iChart.getMainChartFrame();
			if (oldFrame.getParent() != null || oldFrame.isVisible()) {
				oldFrame.setVisible(false);
				oldFrame.dispose();
			}
		}

		int first = 0;
		int last = exp.getCages().getCageList().size() - 1;
		if (exp.getCages().getCageList().size() > 0) {
			first = exp.getCages().getCageList().get(first).getCageID();
			last = exp.getCages().getCageList().get(last).getCageID();
		}

		if (!displayAllButton.isSelected()) {
			Cage cageFound = findSelectedCage(exp);
			if (cageFound == null)
				return null;
			exp.getSeqCamData().centerDisplayOnRoi(cageFound.getRoi());
			first = cageFound.getProperties().getCageID();
			last = first;
		}

		ResultsOptions options = ResultsOptionsBuilder.forChart() //
				.withBuildExcelStepMs(60000) //
				.withSubtractT0(true) //
				.withResultType(resultType) //
				.withCageRange(first, last) //
				.build();

		iChart = new ChartCombinedFrame();
		iChart.createMainChartPanel("Capillary level measures", exp, options);
		iChart.setChartUpperLeftLocation(getInitialUpperLeftPosition(exp));
		iChart.displayData(exp, options);
		return iChart;
	}

	private ChartCageArrayFrame plotCapillaryMeasuresToChart(Experiment exp, EnumResults resultType,
			ChartCageArrayFrame iChart) {
		// Properly dispose and clean up existing chart frame if it exists
		if (iChart != null && iChart.getMainChartFrame() != null) {
			IcyFrame oldFrame = iChart.getMainChartFrame();
			if (oldFrame.getParent() != null || oldFrame.isVisible()) {
				oldFrame.setVisible(false);
				oldFrame.dispose();
			}
		}

		int first = 0;
		int last = exp.getCages().getCageList().size() - 1;
		if (exp.getCages().getCageList().size() > 0) {
			first = exp.getCages().getCageList().get(first).getCageID();
			last = exp.getCages().getCageList().get(last).getCageID();
		}

		if (!displayAllButton.isSelected()) {
			Cage cageFound = findSelectedCage(exp);
			if (cageFound == null)
				return null;
			exp.getSeqCamData().centerDisplayOnRoi(cageFound.getRoi());
			first = cageFound.getProperties().getCageID();
			last = first;
		}

		ResultsOptions options = ResultsOptionsBuilder.forChart() //
				.withBuildExcelStepMs(60000) //
				.withSubtractT0(true) //
				.withResultType(resultType) //
				.withCageRange(first, last) //
				.build();

		// Always create a new ChartCageArrayFrame instance after disposing the old one
		iChart = new ChartCageArrayFrame();
		iChart.setParentComboBox(resultTypeComboBox);
		iChart.createMainChartPanel("Capillary level measures", exp, options);
		iChart.setChartUpperLeftLocation(getInitialUpperLeftPosition(exp));
		iChart.displayData(exp, options);

		if (iChart.getMainChartFrame() != null) {
			iChart.getMainChartFrame().toFront();
			iChart.getMainChartFrame().requestFocus();
		}
		return iChart;
	}
	// ------------------------------------------------

	private Rectangle getInitialUpperLeftPosition(Experiment exp) {
		Rectangle rectv = new Rectangle(50, 500, 10, 10);
		if (exp.getSeqCamData() != null && exp.getSeqCamData().getSequence() != null) {
			Viewer v = exp.getSeqCamData().getSequence().getFirstViewer();
			if (v != null) {
				rectv = v.getBounds();
			} else {
				rectv = parent0.mainFrame.getBounds();
				rectv.translate(0, 150);
			}
		} else {
			if (parent0 != null && parent0.mainFrame != null) {
				rectv = parent0.mainFrame.getBounds();
				rectv.translate(0, 150);
			}
		}
		return rectv;
	}

	// ------------------------------------------------

	public void closeAllCharts() {
		if (currentExperiment != null) {
			removeSelectionListeners(currentExperiment);
		}

		if (chartCageArrayFrame != null) {
			if (chartCageArrayFrame.getMainChartFrame() != null) {
				chartCageArrayFrame.getMainChartFrame().dispose();
			}
			chartCageArrayFrame = null;
		}

		if (chartCombinedFrame != null) {
			if (chartCombinedFrame.getMainChartFrame() != null) {
				chartCombinedFrame.getMainChartFrame().dispose();
			}
			chartCombinedFrame = null;
		}
	}

	private boolean isThereAnyDataToDisplay(Experiment exp, EnumResults resultType) {
		boolean flag = false;
		Capillaries capillaries = exp.getCapillaries();
		for (Capillary cap : capillaries.getList()) {
			flag = cap.isThereAnyMeasuresDone(resultType);
			if (flag)
				break;
		}
		return flag;
	}

	private Capillary findCapillaryFromKymographT(Experiment exp, int t) {
		if (exp == null || exp.getCapillaries() == null)
			return null;
		for (Capillary cap : exp.getCapillaries().getList()) {
			if (cap.getKymographIndex() == t) {
				return cap;
			}
		}
		return null;
	}

	private Cage findSelectedCage(Experiment exp) {
		if (exp == null)
			return null;

		Cage cageFound = null;

		if (exp.getSeqKymos() != null && exp.getSeqKymos().getSequence() != null) {
			Viewer v = exp.getSeqKymos().getSequence().getFirstViewer();
			if (v != null) {
				int t = v.getPositionT();
				Capillary cap = findCapillaryFromKymographT(exp, t);
				if (cap != null) {
					int cageID = cap.getCageID();
					cageFound = exp.getCages().getCageFromID(cageID);
					if (cageFound != null)
						return cageFound;
				}
			}
		}

		cageFound = exp.getCages().findFirstCageWithSelectedCapillary(exp.getCapillaries());
		if (cageFound != null)
			return cageFound;

		cageFound = exp.getCages().findFirstSelectedCage();
		return cageFound;
	}

	@Override
	public void sequenceChanged(SequenceEvent sequenceEvent) {
		if (displaySelectedButton.isSelected() && currentExperiment != null) {
			displayChartPanels(currentExperiment);
		}
	}

	@Override
	public void sequenceClosed(Sequence sequence) {
		sequence.removeListener(this);

		// Save window positions before closing (global positions, shared across all
		// experiments)
		saveChartPositions();

		closeAllCharts();
	}

	private void saveChartPositions() {
	}

	private void addSelectionListeners(Experiment exp) {
		if (exp == null)
			return;

		currentExperiment = exp;

		if (exp.getSeqKymos() != null && exp.getSeqKymos().getSequence() != null) {
			kymographViewer = exp.getSeqKymos().getSequence().getFirstViewer();
			if (kymographViewer != null) {
				kymographViewer.addListener(this);
			}
		}
	}

	private void removeSelectionListeners(Experiment exp) {
		if (kymographViewer != null) {
			kymographViewer.removeListener(this);
			kymographViewer = null;
		}
		currentExperiment = null;
	}

	@Override
	public void viewerChanged(ViewerEvent event) {
		if (displaySelectedButton.isSelected() && event.getType() == ViewerEventType.POSITION_CHANGED
				&& event.getDim() == DimensionId.T) {
			Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
			if (exp != null && exp.getSeqKymos() != null && exp.getSeqKymos().getSequence() != null) {
				Viewer v = event.getSource();
				if (v == exp.getSeqKymos().getSequence().getFirstViewer()) {
					displayChartPanels(exp);
				}
			}
		}
	}

	@Override
	public void viewerClosed(Viewer viewer) {
		if (viewer == kymographViewer) {
			kymographViewer = null;
		}
	}
}
