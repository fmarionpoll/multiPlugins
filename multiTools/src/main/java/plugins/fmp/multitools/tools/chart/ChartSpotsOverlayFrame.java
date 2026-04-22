package plugins.fmp.multitools.tools.chart;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import icy.gui.frame.IcyFrame;
import icy.gui.util.GuiUtil;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.cage.Cage;
import plugins.fmp.multitools.experiment.spot.Spot;
import plugins.fmp.multitools.experiment.spot.SpotMeasure;
import plugins.fmp.multitools.tools.chart.interaction.SpotOverlayChartInteractionHandler;
import plugins.fmp.multitools.tools.chart.style.SeriesStyleCodec;
import plugins.fmp.multitools.tools.results.EnumResults;
import plugins.fmp.multitools.tools.results.ResultsOptions;

public class ChartSpotsOverlayFrame {
	public interface SelectedSpotsProvider {
		List<Spot> getSelectedSpots();
	}

	private IcyFrame mainChartFrame = null;
	private JPanel mainChartPanel = null;
	private ChartPanel chartPanel = null;

	private JPanel topControlsPanel = null;
	private JCheckBox cbSum = new JCheckBox("sum");
	private JCheckBox cbNoFly = new JCheckBox("sumNoFly");
	private JCheckBox cbClean = new JCheckBox("clean");
	private JCheckBox cbFlyPresent = new JCheckBox("flyPresent");
	private JButton updateButton = new JButton("Update");

	private String baseTitle = null;
	private Experiment lastExperiment = null;
	private ResultsOptions lastOptions = null;
	private List<Spot> lastSelectedSpots = null;
	private SelectedSpotsProvider selectedSpotsProvider = null;

	public void setSelectedSpotsProvider(SelectedSpotsProvider provider) {
		this.selectedSpotsProvider = provider;
	}

	public void createMainChartPanel(String title, ResultsOptions options) {
		if (title == null || title.trim().isEmpty())
			throw new IllegalArgumentException("Title cannot be null or empty");
		if (options == null)
			throw new IllegalArgumentException("ResultsOptions cannot be null");

		this.baseTitle = title;

		mainChartPanel = new JPanel(new BorderLayout());
		String finalTitle = title + ": " + (options.resultType != null ? options.resultType.toString() : "");
		if (mainChartFrame != null && (mainChartFrame.getParent() != null || mainChartFrame.isVisible())) {
			mainChartFrame.setTitle(finalTitle);
			mainChartFrame.removeAll();
		} else {
			mainChartFrame = GuiUtil.generateTitleFrame(finalTitle, new JPanel(), new Dimension(500, 200), true, true,
					true, true);
		}
		mainChartFrame.setLayout(new BorderLayout());
		topControlsPanel = createTopControlsPanel(options);
		mainChartFrame.add(topControlsPanel, BorderLayout.NORTH);
		mainChartFrame.add(new JScrollPane(mainChartPanel), BorderLayout.CENTER);
	}

	public void displayData(Experiment exp, ResultsOptions options, List<Spot> selectedSpots) {
		if (mainChartPanel == null || mainChartFrame == null)
			throw new IllegalStateException("createMainChartPanel must be called first");
		if (exp == null || options == null || selectedSpots == null || selectedSpots.isEmpty())
			return;

		this.lastExperiment = exp;
		this.lastOptions = options;
		this.lastSelectedSpots = selectedSpots;

		refreshChart();

		mainChartFrame.pack();
		if (mainChartFrame.getParent() == null) {
			mainChartFrame.addToDesktopPane();
		}
		mainChartFrame.setVisible(true);
		mainChartFrame.toFront();
		mainChartFrame.requestFocus();
	}

	public void setChartUpperLeftLocation(Rectangle rect) {
		if (rect == null)
			return;
		if (mainChartFrame != null) {
			mainChartFrame.setLocation(rect.getLocation());
		}
	}

	public IcyFrame getMainChartFrame() {
		return mainChartFrame;
	}

	public void dispose() {
		if (mainChartFrame != null) {
			mainChartFrame.dispose();
		}
		mainChartFrame = null;
		mainChartPanel = null;
		chartPanel = null;
	}

	private JPanel createTopControlsPanel(ResultsOptions options) {
		JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));

		setCheckboxDefaults(options);

		panel.add(cbSum);
		panel.add(cbNoFly);
		panel.add(cbClean);
		panel.add(cbFlyPresent);
		panel.add(updateButton);

		updateButton.addActionListener(e -> refreshChart());

		cbSum.addActionListener(e -> maybeEnforceSingleMeasureSelection());
		cbNoFly.addActionListener(e -> maybeEnforceSingleMeasureSelection());
		cbClean.addActionListener(e -> maybeEnforceSingleMeasureSelection());
		cbFlyPresent.addActionListener(e -> maybeEnforceSingleMeasureSelection());

		return panel;
	}

	private void setCheckboxDefaults(ResultsOptions options) {
		EnumResults rt = options != null ? options.resultType : null;
		cbSum.setSelected(rt == EnumResults.AREA_SUM);
		cbNoFly.setSelected(rt == EnumResults.AREA_SUMNOFLY);
		cbClean.setSelected(rt == EnumResults.AREA_SUMCLEAN);
		cbFlyPresent.setSelected(rt == EnumResults.AREA_FLYPRESENT);
		if (!cbSum.isSelected() && !cbNoFly.isSelected() && !cbClean.isSelected() && !cbFlyPresent.isSelected()) {
			cbClean.setSelected(true);
		}
	}

	private void maybeEnforceSingleMeasureSelection() {
		if (lastSelectedSpots == null || lastSelectedSpots.size() <= 1) {
			return;
		}
		// Several spots selected => only one measure can be selected (overlay spots).
		int n = countSelectedMeasures();
		if (n <= 1)
			return;

		// Keep the first checked in a stable priority order.
		JCheckBox keep = cbClean.isSelected() ? cbClean
				: cbNoFly.isSelected() ? cbNoFly : cbSum.isSelected() ? cbSum : cbFlyPresent;
		cbSum.setSelected(keep == cbSum);
		cbNoFly.setSelected(keep == cbNoFly);
		cbClean.setSelected(keep == cbClean);
		cbFlyPresent.setSelected(keep == cbFlyPresent);
	}

	private int countSelectedMeasures() {
		int n = 0;
		if (cbSum.isSelected())
			n++;
		if (cbNoFly.isSelected())
			n++;
		if (cbClean.isSelected())
			n++;
		if (cbFlyPresent.isSelected())
			n++;
		return n;
	}

	private void refreshChart() {
		if (mainChartPanel == null || mainChartFrame == null || lastExperiment == null || lastOptions == null
				|| lastSelectedSpots == null || lastSelectedSpots.isEmpty()) {
			return;
		}

		// Refresh selected spots on Update (or any refresh), because the user may have changed ROI selection.
		if (selectedSpotsProvider != null) {
			try {
				List<Spot> refreshed = selectedSpotsProvider.getSelectedSpots();
				if (refreshed != null && !refreshed.isEmpty()) {
					lastSelectedSpots = refreshed;
				}
			} catch (Exception e) {
				// keep previous list if provider fails
			}
		}

		maybeEnforceSingleMeasureSelection();

		mainChartPanel.removeAll();

		List<EnumResults> enabledMeasures = getEnabledMeasures(lastOptions);
		if (enabledMeasures.isEmpty()) {
			mainChartPanel.revalidate();
			mainChartPanel.repaint();
			return;
		}

		boolean overlaySpots = lastSelectedSpots.size() > 1;
		boolean overlayMeasures = !overlaySpots && enabledMeasures.size() > 1;

		// Build datasets. When FLYPRESENT is shown together with continuous measures,
		// put it on its own Y axis.
		boolean flyEnabled = enabledMeasures.contains(EnumResults.AREA_FLYPRESENT);
		boolean hasContinuous = enabledMeasures.stream().anyMatch(t -> t != EnumResults.AREA_FLYPRESENT);
		boolean splitFlyAxis = flyEnabled && hasContinuous;

		NumberAxis xAxis = new NumberAxis("time (min)");
		xAxis.setAutoRangeIncludesZero(false);

		NumberAxis yAxis0 = new NumberAxis("");
		yAxis0.setAutoRangeIncludesZero(false);

		XYPlot plot = new XYPlot(null, xAxis, yAxis0, null);

		if (splitFlyAxis) {
			XYSeriesCollection ds0 = buildDatasetForMeasures(lastExperiment, lastOptions, lastSelectedSpots,
					enabledMeasures, false, overlaySpots, overlayMeasures);
			XYLineAndShapeRenderer r0 = createRenderer(ds0);
			plot.setDataset(0, ds0);
			plot.setRenderer(0, r0);

			XYSeriesCollection ds1 = buildDatasetForMeasures(lastExperiment, lastOptions, lastSelectedSpots,
					enabledMeasures, true, overlaySpots, overlayMeasures);
			XYLineAndShapeRenderer r1 = createRenderer(ds1);
			NumberAxis yAxis1 = new NumberAxis(EnumResults.AREA_FLYPRESENT.toUnit());
			yAxis1.setAutoRange(false);
			yAxis1.setRange(-0.2, 1.2);
			plot.setRangeAxis(1, yAxis1);
			plot.setDataset(1, ds1);
			plot.setRenderer(1, r1);
			plot.mapDatasetToRangeAxis(1, 1);

			yAxis0.setLabel("area" + lastOptions.resultType.toUnit());
		} else {
			XYSeriesCollection ds = buildDatasetForMeasures(lastExperiment, lastOptions, lastSelectedSpots,
					enabledMeasures, flyEnabled && !hasContinuous, overlaySpots, overlayMeasures);
			plot.setDataset(0, ds);
			plot.setRenderer(0, createRenderer(ds));

			EnumResults labelType = flyEnabled && !hasContinuous ? EnumResults.AREA_FLYPRESENT : lastOptions.resultType;
			yAxis0.setLabel(labelType != null ? labelType.toUnit() : "");
			if (flyEnabled && !hasContinuous) {
				yAxis0.setAutoRange(false);
				yAxis0.setRange(-0.2, 1.2);
			}
		}

		JFreeChart chart = new JFreeChart(plot);
		if (baseTitle != null) {
			mainChartFrame.setTitle(baseTitle);
		}

		chartPanel = new ChartPanel(chart, 900, 500, 300, 200, 2000, 2000, true, true, true, true, false, true);
		chartPanel.addChartMouseListener(new SpotOverlayChartInteractionHandler(lastExperiment, lastOptions).createMouseListener());
		mainChartPanel.add(chartPanel, BorderLayout.CENTER);

		mainChartPanel.revalidate();
		mainChartPanel.repaint();
	}

	private List<EnumResults> getEnabledMeasures(ResultsOptions options) {
		List<EnumResults> out = new ArrayList<>();
		if (cbSum.isSelected())
			out.add(EnumResults.AREA_SUM);
		if (cbNoFly.isSelected())
			out.add(EnumResults.AREA_SUMNOFLY);
		if (cbClean.isSelected())
			out.add(EnumResults.AREA_SUMCLEAN);
		if (cbFlyPresent.isSelected())
			out.add(EnumResults.AREA_FLYPRESENT);
		return out;
	}

	private XYSeriesCollection buildDatasetForMeasures(Experiment exp, ResultsOptions options, List<Spot> selectedSpots,
			List<EnumResults> enabledMeasures, boolean flyOnly, boolean overlaySpots, boolean overlayMeasures) {
		XYSeriesCollection dataset = new XYSeriesCollection();
		if (overlaySpots) {
			EnumResults chosen = enabledMeasures.isEmpty() ? options.resultType : enabledMeasures.get(0);
			if (chosen == null)
				return dataset;
			if (flyOnly && chosen != EnumResults.AREA_FLYPRESENT)
				return dataset;
			if (!flyOnly && chosen == EnumResults.AREA_FLYPRESENT)
				return dataset;
			for (int i = 0; i < selectedSpots.size(); i++) {
				Spot spot = selectedSpots.get(i);
				XYSeries series = createXYSeriesFromSpotMeasure(exp, spot, options, chosen, spot.getName());
				if (series == null)
					continue;
				applySpotStyle(exp, spot, series, pickSpotColor(i));
				dataset.addSeries(series);
			}
			return dataset;
		}

		// Single spot
		Spot spot = selectedSpots.get(0);
		if (spot == null)
			return dataset;

		// If only one measure is enabled, show a single curve for that measure.
		// If several measures are enabled, overlay measures (different colors).
		for (EnumResults resultType : enabledMeasures) {
			if (resultType == null)
				continue;
			if (flyOnly && resultType != EnumResults.AREA_FLYPRESENT)
				continue;
			if (!flyOnly && resultType == EnumResults.AREA_FLYPRESENT)
				continue;

			Color color = pickMeasureColor(resultType);
			XYSeries series = createXYSeriesFromSpotMeasure(exp, spot, options, resultType,
					spot.getName() + "::" + resultType.name());
			if (series == null)
				continue;
			applySpotStyle(exp, spot, series, color);
			dataset.addSeries(series);
		}
		return dataset;
	}

	private Color pickSpotColor(int index) {
		Color[] palette = new Color[] { new Color(0, 0, 0), new Color(0, 102, 204), new Color(0, 153, 0),
				new Color(204, 102, 0), new Color(153, 0, 153), new Color(0, 153, 153), new Color(153, 102, 0),
				new Color(102, 102, 102) };
		if (index < 0)
			index = 0;
		return palette[index % palette.length];
	}

	private Color pickMeasureColor(EnumResults resultType) {
		if (resultType == null)
			return Color.BLACK;
		switch (resultType) {
		case AREA_SUM:
			return new Color(0, 0, 0);
		case AREA_SUMNOFLY:
			return new Color(0, 102, 204);
		case AREA_SUMCLEAN:
			return new Color(0, 153, 0);
		case AREA_FLYPRESENT:
			return new Color(153, 0, 153);
		default:
			return Color.BLACK;
		}
	}

	private XYLineAndShapeRenderer createRenderer(XYSeriesCollection dataset) {
		XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer(true, false);
		SeriesStyleCodec.applySeriesPaintsFromDescription(dataset, renderer);
		return renderer;
	}

	private static void applySpotStyle(Experiment exp, Spot spot, XYSeries series, Color color) {
		if (exp == null || exp.getCages() == null || exp.getSpots() == null || spot == null || series == null)
			return;
		Cage cage = exp.getCages().getCageFromSpotROIName(spot.getName(), exp.getSpots());
		if (cage == null || cage.getProperties() == null || spot.getProperties() == null)
			return;
		series.setDescription(SeriesStyleCodec.buildDescription(cage.getProperties().getCageID(),
				cage.getProperties().getCageID(), cage.getProperties().getCageNFlies(), color));
	}

	private static XYSeries createXYSeriesFromSpotMeasure(Experiment exp, Spot spot, ResultsOptions baseOptions,
			EnumResults resultType, String seriesKey) {
		if (exp == null || spot == null || baseOptions == null || resultType == null || seriesKey == null)
			return null;

		XYSeries seriesXY = new XYSeries(seriesKey, false);

		if (exp.getSeqCamData().getTimeManager().getCamImagesTime_Ms() == null)
			exp.getSeqCamData().build_MsTimesArray_From_FileNamesList();
		double[] camImages_time_min = exp.getSeqCamData().getTimeManager().getCamImagesTime_Minutes();

		SpotMeasure spotMeasure = spot.getMeasurements(resultType);
		if (spotMeasure == null)
			return null;

		double divider = 1.0;
		if (baseOptions.relativeToMaximum && resultType != EnumResults.AREA_FLYPRESENT) {
			divider = spotMeasure.getMaximumValue();
			if (divider == 0)
				divider = 1.0;
		}

		int npoints = spotMeasure.getCount();
		if (camImages_time_min != null && npoints > camImages_time_min.length)
			npoints = camImages_time_min.length;

		for (int j = 0; j < npoints; j++) {
			double x = camImages_time_min != null ? camImages_time_min[j] : j;
			double y = spotMeasure.getValueAt(j) / divider;
			seriesXY.add(x, y);
		}
		return seriesXY;
	}

	public static List<Spot> dedupeSpots(List<Spot> spots) {
		if (spots == null || spots.isEmpty())
			return Collections.emptyList();
		List<Spot> out = new ArrayList<>();
		for (Spot s : spots) {
			if (s == null)
				continue;
			boolean seen = false;
			for (Spot o : out) {
				if (o != null && o.getName() != null && o.getName().equals(s.getName())) {
					seen = true;
					break;
				}
			}
			if (!seen)
				out.add(s);
		}
		return out;
	}
}

