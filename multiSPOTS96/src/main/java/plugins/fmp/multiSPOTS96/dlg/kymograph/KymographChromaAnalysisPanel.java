package plugins.fmp.multiSPOTS96.dlg.kymograph;

import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.util.concurrent.ExecutionException;

import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;

import icy.gui.viewer.Viewer;
import plugins.fmp.multiSPOTS96.MultiSPOTS96;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.cage.Cage;
import plugins.fmp.multitools.experiment.cage.CageString;
import plugins.fmp.multitools.service.CageKymographChromaAnalyzer;
import plugins.fmp.multitools.service.CageKymographChromaAnalyzer.Params;
import plugins.fmp.multitools.service.KymographChromaAnalysisResult;
import plugins.fmp.multitools.tools.chart.ChartCagesFrame;
import plugins.fmp.multitools.tools.chart.builders.CageKymographChromaSeriesBuilder;
import plugins.fmp.multitools.tools.results.EnumResults;
import plugins.fmp.multitools.tools.results.ResultsOptions;
import plugins.fmp.multitools.tools.results.ResultsOptionsBuilder;
import plugins.fmp.multitools.tools.chart.strategies.GridLayoutStrategy;
import plugins.fmp.multitools.tools.chart.strategies.NoOpChartUIControlsFactory;

/**
 * Chroma fraction analysis on loaded stacked cage kymographs ({@code kymocage_*.tif*}).
 */
public class KymographChromaAnalysisPanel extends JPanel {

	private static final long serialVersionUID = 1L;

	private final MultiSPOTS96 parent0;
	private final JSpinner chromaThresholdSpinner = new JSpinner(new SpinnerNumberModel(35, 0, 765, 1));
	private final JSpinner minSumRgbSpinner = new JSpinner(new SpinnerNumberModel(30, 0, 765, 1));
	private final JSpinner minValidRowsSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 500, 1));
	private final JCheckBox plotAbsDeltaCheckBox = new JCheckBox("|Δf| (bin-to-bin)", false);
	private final JRadioButton displayAllButton = new JRadioButton("all cages", true);
	private final JRadioButton displaySelectedCageButton = new JRadioButton("cage selected", false);
	private final JButton analyzeButton = new JButton("Analyze");
	private final JButton displayChartsButton = new JButton("Display charts");
	private final JLabel statusLabel = new JLabel(" ", SwingConstants.LEFT);

	private KymographChromaAnalysisResult lastResult;
	private ChartCagesFrame chartFrame;
	private SwingWorker<KymographChromaAnalysisResult, Void> analyzeWorker;

	public KymographChromaAnalysisPanel(MultiSPOTS96 parent0) {
		super(new GridLayout(5, 1));
		this.parent0 = parent0;
		FlowLayout left = new FlowLayout(FlowLayout.LEFT);
		left.setVgap(0);

		JPanel p0 = new JPanel(left);
		p0.add(analyzeButton);
		p0.add(displayChartsButton);
		add(p0);

		JPanel p1 = new JPanel(left);
		p1.add(new JLabel("S(diffRGB) > "));
		p1.add(chromaThresholdSpinner);
		p1.add(new JLabel("  min R+G+B (valid px) "));
		p1.add(minSumRgbSpinner);
		p1.add(new JLabel("  min valid rows/col "));
		p1.add(minValidRowsSpinner);
		add(p1);

		JPanel p2 = new JPanel(left);
		p2.add(plotAbsDeltaCheckBox);
		p2.add(new JLabel("  display"));
		p2.add(displayAllButton);
		p2.add(displaySelectedCageButton);
		ButtonGroup g = new ButtonGroup();
		g.add(displayAllButton);
		g.add(displaySelectedCageButton);
		add(p2);

		JPanel p3 = new JPanel(left);
		p3.add(new JLabel("Status: "));
		p3.add(statusLabel);
		add(p3);

		analyzeButton.addActionListener(e -> onAnalyze());
		displayChartsButton.addActionListener(e -> onDisplayCharts());
	}

	private Params readParams() {
		return new Params(((Number) chromaThresholdSpinner.getValue()).intValue(),
				((Number) minSumRgbSpinner.getValue()).intValue(),
				((Number) minValidRowsSpinner.getValue()).intValue());
	}

	private void onAnalyze() {
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		if (exp == null) {
			statusLabel.setText("No experiment selected.");
			return;
		}
		if (exp.getSeqKymos() == null || exp.getSeqKymos().getSequence() == null) {
			statusLabel.setText("Load cage kymographs first (Load/Save or experiment open).");
			return;
		}
		if (analyzeWorker != null && !analyzeWorker.isDone()) {
			statusLabel.setText("Analysis already running.");
			return;
		}
		analyzeButton.setEnabled(false);
		statusLabel.setText("Analyzing…");
		final Params params = readParams();
		analyzeWorker = new SwingWorker<KymographChromaAnalysisResult, Void>() {
			@Override
			protected KymographChromaAnalysisResult doInBackground() {
				return CageKymographChromaAnalyzer.analyze(exp, params);
			}

			@Override
			protected void done() {
				analyzeButton.setEnabled(true);
				try {
					lastResult = get();
					int n = lastResult.byCageId.size();
					if (n == 0) {
						statusLabel.setText("No data: check kymograph files and camera sequence for ROI bounds.");
					} else {
						statusLabel.setText("Done: " + n + " cage(s), " + lastResult.widthBins + " bin(s).");
					}
				} catch (InterruptedException ex) {
					Thread.currentThread().interrupt();
					statusLabel.setText("Interrupted.");
				} catch (ExecutionException ex) {
					Throwable c = ex.getCause();
					statusLabel.setText("Error: " + (c != null ? c.getMessage() : ex.getMessage()));
				}
			}
		};
		analyzeWorker.execute();
	}

	private void onDisplayCharts() {
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		if (exp == null || lastResult == null) {
			statusLabel.setText("Run Analyze first.");
			return;
		}
		if (lastResult.byCageId.isEmpty()) {
			statusLabel.setText("Nothing to display.");
			return;
		}
		boolean plotDelta = plotAbsDeltaCheckBox.isSelected();
		CageKymographChromaSeriesBuilder dataBuilder = new CageKymographChromaSeriesBuilder(lastResult, plotDelta);

		int first = 0;
		int last = Math.max(0, exp.getCages().cagesList.size() - 1);
		if (displaySelectedCageButton.isSelected()) {
			Cage cageFound = exp.getCages().findFirstSelectedCage();
			if (cageFound == null) {
				statusLabel.setText("Select a cage ROI on the camera sequence.");
				return;
			}
			int cageNumber;
			try {
				cageNumber = Integer.parseInt(CageString.getCageNumberFromCageRoiName(cageFound.getRoi().getName()));
			} catch (NumberFormatException ex) {
				statusLabel.setText("Could not parse cage id from ROI name.");
				return;
			}
			first = cageNumber;
			last = cageNumber;
		}

		int stepMs = (int) Math.min(Integer.MAX_VALUE, Math.max(1L, exp.getKymoBin_ms()));
		ResultsOptions options = ResultsOptionsBuilder.forChart().withResultType(EnumResults.KYMO_CHROMA_FRACT)
				.withBuildExcelStepMs(stepMs).withCageRange(first, last).build();
		options.relativeToMaximum = false;

		if (chartFrame != null && chartFrame.getMainChartFrame() != null) {
			chartFrame.getMainChartFrame().dispose();
		}
		chartFrame = new ChartCagesFrame(dataBuilder, null, new GridLayoutStrategy(), new NoOpChartUIControlsFactory());
		chartFrame.createMainChartPanel("Kymograph chroma", exp, options);
		chartFrame.setChartUpperLeftLocation(getInitialUpperLeftPosition(exp));
		chartFrame.displayData(exp, options);
		if (chartFrame.getMainChartFrame() != null) {
			chartFrame.getMainChartFrame().toFront();
			chartFrame.getMainChartFrame().requestFocus();
		}
	}

	private Rectangle getInitialUpperLeftPosition(Experiment exp) {
		Rectangle rectv = new Rectangle(50, 500, 10, 10);
		if (exp != null && exp.getSeqCamData() != null && exp.getSeqCamData().getSequence() != null) {
			Viewer v = exp.getSeqCamData().getSequence().getFirstViewer();
			if (v != null) {
				rectv = v.getBounds();
				rectv.translate(0, rectv.height);
			}
		}
		if (rectv.width <= 10 && parent0 != null && parent0.mainFrame != null) {
			rectv = parent0.mainFrame.getBounds();
			rectv.translate(rectv.width, rectv.height + 100);
		}
		rectv.translate(5, 10);
		return rectv;
	}
}
