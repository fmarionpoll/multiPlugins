package plugins.fmp.multitools.tools.chart;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.prefs.Preferences;

import javax.swing.JPanel;
import javax.swing.JScrollPane;

import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.plot.CombinedDomainXYPlot;
import org.jfree.chart.plot.CombinedRangeXYPlot;
import org.jfree.chart.plot.XYPlot;
import org.jfree.data.xy.XYSeriesCollection;

import icy.gui.frame.IcyFrame;
import icy.gui.util.GuiUtil;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.cage.Cage;
import plugins.fmp.multitools.experiment.spot.Spot;
import plugins.fmp.multitools.tools.chart.builders.CageCapillarySeriesBuilder;
import plugins.fmp.multitools.tools.chart.builders.CageSeriesBuilder;
import plugins.fmp.multitools.tools.chart.builders.CageSpotSeriesBuilder;
import plugins.fmp.multitools.tools.chart.interaction.CapillaryCombinedChartInteractionHandler;
import plugins.fmp.multitools.tools.chart.interaction.SpotCombinedChartInteractionHandler;
import plugins.fmp.multitools.tools.chart.plot.CageChartPlotFactory;
import plugins.fmp.multitools.tools.chart.strategies.ChartUIControlsFactory;
import plugins.fmp.multitools.tools.chart.strategies.ComboBoxUIControlsFactory;
import plugins.fmp.multitools.tools.chart.style.SeriesStyleCodec;
import plugins.fmp.multitools.tools.results.EnumResults;
import plugins.fmp.multitools.tools.results.ResultsOptions;

/**
 * Displays cages in a single combined chart.
 *
 * <p>
 * {@link CombinedLayout#HORIZONTAL} (default): {@link CombinedRangeXYPlot} —
 * cages side by side, shared Y. Used by MultiCAFE.
 * {@link CombinedLayout#VERTICAL}: {@link CombinedDomainXYPlot} — cages stacked,
 * shared time axis.
 * </p>
 */
public class ChartCagesCombinedFrame {

	public enum CombinedLayout {
		HORIZONTAL, VERTICAL
	}

	private static final int DEFAULT_FRAME_WIDTH = 480;
	private static final int DEFAULT_FRAME_HEIGHT = 200;
	private static final int SUBPLOT_WIDTH = 80;
	private static final int SUBPLOT_HEIGHT = 120;
	private static final Font AXIS_FONT = new Font("SansSerif", Font.PLAIN, 9);

	private IcyFrame mainChartFrame = null;
	private JPanel mainChartPanel = null;
	private ChartPanel chartPanel = null;
	/** Fallback upper-left when no saved preference exists (e.g. relative to cam viewer). */
	private Point graphLocation = new Point(0, 0);
	private Consumer<Spot> onSpotSelectedFromChart;
	private CombinedLayout layout = CombinedLayout.HORIZONTAL;
	private ChartUIControlsFactory uiControlsFactory;
	private String baseTitle = "Capillary level measures";
	private Experiment experiment;
	private ResultsOptions currentOptions;

	public void setOnSpotSelectedFromChart(Consumer<Spot> callback) {
		this.onSpotSelectedFromChart = callback;
	}

	public void setCombinedLayout(CombinedLayout layout) {
		this.layout = layout != null ? layout : CombinedLayout.HORIZONTAL;
	}

	public void setUIControlsFactory(ChartUIControlsFactory uiControlsFactory) {
		this.uiControlsFactory = uiControlsFactory;
	}

	public void createMainChartPanel(String title, Experiment exp, ResultsOptions options) {
		if (title == null || title.trim().isEmpty())
			throw new IllegalArgumentException("Title cannot be null or empty");
		if (exp == null)
			throw new IllegalArgumentException("Experiment cannot be null");
		if (options == null)
			throw new IllegalArgumentException("ResultsOptions cannot be null");

		mainChartPanel = new JPanel(new BorderLayout());

		this.baseTitle = title;
		String finalTitle = title + ": " + options.resultType;
		boolean newFrame = !(mainChartFrame != null
				&& (mainChartFrame.getParent() != null || mainChartFrame.isVisible()));
		if (!newFrame) {
			mainChartFrame.setTitle(finalTitle);
			mainChartFrame.removeAll();
		} else {
			mainChartFrame = GuiUtil.generateTitleFrame(finalTitle, new JPanel(),
					new Dimension(DEFAULT_FRAME_WIDTH, DEFAULT_FRAME_HEIGHT), true, true, true, true);
			mainChartFrame.addComponentListener(new ComponentAdapter() {
				@Override
				public void componentResized(ComponentEvent e) {
					savePreferences();
				}

				@Override
				public void componentMoved(ComponentEvent e) {
					savePreferences();
				}
			});
		}
		mainChartFrame.setLayout(new BorderLayout());
		if (uiControlsFactory != null) {
			JPanel topPanel = uiControlsFactory.createTopPanel(options, new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					if (currentOptions != null && experiment != null) {
						updateFrameTitle();
						displayData(experiment, currentOptions);
					}
				}
			});
			if (topPanel != null) {
				mainChartFrame.add(topPanel, BorderLayout.NORTH);
			}
			JPanel bottomPanel = uiControlsFactory.createBottomPanel(options, exp);
			if (bottomPanel != null) {
				mainChartFrame.add(bottomPanel, BorderLayout.SOUTH);
			}
		}
		mainChartFrame.add(new JScrollPane(mainChartPanel), BorderLayout.CENTER);
	}

	public void displayData(Experiment exp, ResultsOptions options) {
		if (mainChartPanel == null || mainChartFrame == null)
			throw new IllegalStateException("createMainChartPanel must be called first");

		this.experiment = exp;
		this.currentOptions = options;
		if (uiControlsFactory instanceof ComboBoxUIControlsFactory) {
			((ComboBoxUIControlsFactory) uiControlsFactory).setExperiment(exp);
		}
		if (uiControlsFactory != null) {
			uiControlsFactory.updateControls(options.resultType, options);
		}

		mainChartPanel.removeAll();

		exp.getCages().prepareComputations(exp, options);

		boolean horizontal = layout != CombinedLayout.VERTICAL;
		CageSeriesBuilder builder = selectDataBuilder(options.resultType);
		List<Cage> subplotCages = new ArrayList<>();
		List<XYPlot> subplots = new ArrayList<>();
		XYSeriesCollection firstDataset = null;
		for (Cage cage : filterCages(exp, options)) {
			XYSeriesCollection dataset = builder.build(exp, cage, options);
			if (dataset == null || dataset.getSeriesCount() == 0)
				continue;
			if (firstDataset == null) {
				firstDataset = dataset;
			}

			int cageId = cage.getProperties() != null ? cage.getProperties().getCageID() : -1;
			XYPlot subplot = horizontal ? buildHorizontalSubplot(dataset, cageId)
					: buildVerticalSubplot(dataset, cageId);

			int nFlies = SeriesStyleCodec.getNFliesOrDefault(dataset, -1);
			CageChartPlotFactory.setXYPlotBackGroundAccordingToNFlies(subplot, nFlies);

			subplots.add(subplot);
			subplotCages.add(cage);
		}

		XYPlot combined = horizontal ? buildHorizontalCombined(options, subplots)
				: buildVerticalCombined(subplots);
		JFreeChart chart = new JFreeChart(null, JFreeChart.DEFAULT_TITLE_FONT, combined, false);
		int n = Math.max(1, subplotCages.size());
		int w = horizontal ? Math.max(200, n * SUBPLOT_WIDTH + 50) : 480;
		int h = horizontal ? SUBPLOT_HEIGHT : Math.max(160, n * 80);
		chartPanel = new ChartPanel(chart, w, h, n * SUBPLOT_WIDTH, SUBPLOT_HEIGHT, Math.max(2400, w),
				Math.max(1200, h), true, true, true, true, false, true);
		chartPanel.setMinimumDrawWidth(n * SUBPLOT_WIDTH);
		chartPanel.setMinimumDrawHeight(SUBPLOT_HEIGHT);
		if (isSpotResultType(options.resultType)) {
			chartPanel.addChartMouseListener(
					new SpotCombinedChartInteractionHandler(exp, subplotCages, onSpotSelectedFromChart)
							.createMouseListener());
		} else {
			chartPanel.addChartMouseListener(
					new CapillaryCombinedChartInteractionHandler(exp, subplotCages).createMouseListener());
		}
		mainChartPanel.add(chartPanel, BorderLayout.CENTER);
		if (uiControlsFactory != null) {
			uiControlsFactory.refreshLegendFromDataset(exp, options, firstDataset);
		}
		updateFrameTitle();

		mainChartFrame.pack();
		loadPreferences();
		if (mainChartFrame.getParent() == null) {
			mainChartFrame.addToDesktopPane();
		}
		mainChartFrame.setVisible(true);
		mainChartFrame.toFront();
		mainChartFrame.requestFocus();
	}

	/**
	 * Stores a fallback upper-left location (typically the cam viewer). Applied only
	 * when no previous combined-chart window position is saved in preferences.
	 */
	public void setChartUpperLeftLocation(Rectangle rect) {
		if (rect == null)
			return;
		graphLocation = new Point(rect.x, rect.y);
	}

	public IcyFrame getMainChartFrame() {
		return mainChartFrame;
	}

	private static XYPlot buildVerticalSubplot(XYSeriesCollection dataset, int cageId) {
		NumberAxis yAxis = new NumberAxis("cage " + cageId);
		yAxis.setAutoRangeIncludesZero(false);
		NumberAxis dummyX = new NumberAxis();
		dummyX.setAutoRangeIncludesZero(false);
		XYPlot subplot = CageChartPlotFactory.buildXYPlot(dataset, dummyX, yAxis);
		subplot.setDomainAxis(null);
		applyCompactAxis(yAxis);
		return subplot;
	}

	private static XYPlot buildHorizontalSubplot(XYSeriesCollection dataset, int cageId) {
		NumberAxis xAxis = new NumberAxis("cage " + cageId);
		xAxis.setAutoRangeIncludesZero(false);
		NumberAxis dummyY = new NumberAxis();
		dummyY.setAutoRangeIncludesZero(false);
		XYPlot subplot = CageChartPlotFactory.buildXYPlot(dataset, xAxis, dummyY);
		subplot.setRangeAxis(null);
		applyCompactAxis(xAxis);
		return subplot;
	}

	private static CombinedDomainXYPlot buildVerticalCombined(List<XYPlot> subplots) {
		NumberAxis sharedX = new NumberAxis("time (min)");
		sharedX.setAutoRangeIncludesZero(false);
		applyCompactAxis(sharedX);
		CombinedDomainXYPlot combined = new CombinedDomainXYPlot(sharedX);
		combined.setGap(4.0);
		for (XYPlot subplot : subplots) {
			combined.add(subplot, 1);
		}
		return combined;
	}

	private static CombinedRangeXYPlot buildHorizontalCombined(ResultsOptions options, List<XYPlot> subplots) {
		String yLabel = "volume (ul)";
		if (options != null && options.resultType != null) {
			String unit = options.resultType.toUnit();
			if (unit != null && !unit.isEmpty()) {
				yLabel = unit;
			}
		}
		NumberAxis sharedY = new NumberAxis(yLabel);
		sharedY.setAutoRangeIncludesZero(false);
		if (options != null && (options.resultType == EnumResults.TOPLEVEL_PI
				|| options.resultType == EnumResults.TOPLEVEL_PI00
				|| options.resultType == EnumResults.TOPLEVEL_PI_AND_00
				|| options.resultType == EnumResults.SUMGULPS_PI)) {
			sharedY.setAutoRange(false);
			sharedY.setRange(-1.0, 1.0);
		}
		applyCompactAxis(sharedY);
		CombinedRangeXYPlot combined = new CombinedRangeXYPlot(sharedY);
		combined.setGap(4.0);
		for (XYPlot subplot : subplots) {
			combined.add(subplot, 1);
		}
		return combined;
	}

	private static void applyCompactAxis(ValueAxis axis) {
		if (axis == null) {
			return;
		}
		axis.setLabelFont(AXIS_FONT);
		axis.setTickLabelFont(AXIS_FONT);
	}

	private void updateFrameTitle() {
		if (mainChartFrame != null && baseTitle != null && currentOptions != null) {
			String rt = currentOptions.resultType != null ? currentOptions.resultType.toString() : "";
			mainChartFrame.setTitle(baseTitle + ": " + rt);
		}
	}

	private void loadPreferences() {
		if (mainChartFrame == null)
			return;
		Preferences prefs = Preferences.userNodeForPackage(ChartCagesCombinedFrame.class);
		String suffix = layout == CombinedLayout.VERTICAL ? "_v" : "_h";
		int x = prefs.getInt("window_x" + suffix, graphLocation.x);
		int y = prefs.getInt("window_y" + suffix, graphLocation.y);
		int w = prefs.getInt("window_w" + suffix, DEFAULT_FRAME_WIDTH);
		int h = prefs.getInt("window_h" + suffix, DEFAULT_FRAME_HEIGHT);
		mainChartFrame.setBounds(new Rectangle(x, y, w, h));
	}

	private void savePreferences() {
		if (mainChartFrame == null)
			return;
		Preferences prefs = Preferences.userNodeForPackage(ChartCagesCombinedFrame.class);
		Rectangle r = mainChartFrame.getBounds();
		String suffix = layout == CombinedLayout.VERTICAL ? "_v" : "_h";
		prefs.putInt("window_x" + suffix, r.x);
		prefs.putInt("window_y" + suffix, r.y);
		prefs.putInt("window_w" + suffix, r.width);
		prefs.putInt("window_h" + suffix, r.height);
	}

	private CageSeriesBuilder selectDataBuilder(EnumResults resultType) {
		if (isSpotResultType(resultType)) {
			return new CageSpotSeriesBuilder();
		}
		return new CageCapillarySeriesBuilder();
	}

	private boolean isSpotResultType(EnumResults resultType) {
		if (resultType == null)
			return false;
		switch (resultType) {
		case AREA_SUM:
		case AREA_SUMNOFLY:
		case AREA_SUMCLEAN:
		case AREA_SUMCLEAN_V3:
		case AREA_OUT:
		case AREA_DIFF:
		case AREA_FLYPRESENT:
		case AREA_COUNT_V5:
		case GREY_SUM_V5:
		case GREY_SUM_V5_PREFLY:
		case GREY_SUM_CLEAN_V5:
		case AGG_SUMCLEAN:
		case AGG_SUMCLEAN_V5:
		case AGG_AREA_COUNT_V5:
		case AREA_COUNT_COLOR:
		case GREY_SUM_COLOR:
		case GREY_SUM_COLOR_PREFLY:
		case GREY_SUM_CLEAN_COLOR:
		case AGG_SUMCLEAN_COLOR:
		case AGG_AREA_COUNT_COLOR:
		case AGG_MEDIANREF:
		case KYMO_FRACT:
		case KYMO_ABS_DELTA:
		case KYMO_GREEN_HEIGHT:
		case KYMO_GREEN_HEIGHT_RATIO:
		case KYMO_CAGE_MEAN_FRACT:
		case KYMO_CAGE_MEAN_ABS_DELTA:
		case KYMO_CAGE_MEAN_GREEN_HEIGHT_RATIO:
		case AGG_GREENHEIGHT_CONSO:
			return true;
		default:
			return false;
		}
	}

	private List<Cage> filterCages(Experiment exp, ResultsOptions options) {
		List<Cage> cages = exp.getCages() != null ? exp.getCages().getCageList() : null;
		if (cages == null)
			return Collections.emptyList();

		boolean singleCageMode = options.cageIndexFirst == options.cageIndexLast && options.cageIndexFirst >= 0;
		List<Cage> out = new ArrayList<>();
		if (!singleCageMode) {
			out.addAll(cages);
		} else {
			for (Cage cage : cages) {
				if (cage == null || cage.getProperties() == null)
					continue;
				if (cage.getProperties().getCageID() == options.cageIndexFirst) {
					out.add(cage);
					break;
				}
			}
		}
		Collections.sort(out, java.util.Comparator.comparingInt(Cage::getCageID));
		return out;
	}
}