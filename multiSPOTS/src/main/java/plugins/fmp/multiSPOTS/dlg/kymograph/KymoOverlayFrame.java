package plugins.fmp.multiSPOTS.dlg.kymograph;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.List;
import java.util.prefs.Preferences;

import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.xy.XYSeriesCollection;

import icy.gui.frame.IcyFrame;
import icy.gui.util.GuiUtil;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.spot.Spot;
import plugins.fmp.multitools.tools.chart.builders.CageKymoSeriesBuilder;
import plugins.fmp.multitools.tools.chart.builders.KymoSpotChartSupport;
import plugins.fmp.multitools.tools.results.EnumResults;
import plugins.fmp.multitools.tools.results.ResultsOptions;

/**
 * Single-chart overlay for kymograph metric curves (selected spot ROIs),
 * similar in spirit to
 * {@link plugins.fmp.multitools.tools.chart.ChartSpotsOverlayFrame} for spot
 * measures.
 */
public class KymoOverlayFrame {

	private static final int DEFAULT_FRAME_WIDTH = 520;
	private static final int DEFAULT_FRAME_HEIGHT = 220;

	private IcyFrame mainChartFrame;
	private JPanel mainChartPanel;
	private ChartPanel chartPanel;
	private Point graphLocation = new Point(0, 0);
	private final JButton updateButton = new JButton("Update");

	private String baseTitle;
	private Experiment lastExperiment;
	private ResultsOptions lastOptions;
	public interface SelectedSpotsProvider {
		List<Spot> getSelectedSpots();
	}

	private SelectedSpotsProvider selectedSpotsProvider;

	public void setSelectedSpotsProvider(SelectedSpotsProvider provider) {
		this.selectedSpotsProvider = provider;
	}

	public void createMainChartPanel(String title, ResultsOptions options) {
		if (title == null || title.trim().isEmpty()) {
			throw new IllegalArgumentException("title");
		}
		if (options == null) {
			throw new IllegalArgumentException("options");
		}
		this.baseTitle = title;
		mainChartPanel = new JPanel(new BorderLayout());
		String finalTitle = title + ": " + (options.resultType != null ? options.resultType.toString() : "");
		if (mainChartFrame != null && (mainChartFrame.getParent() != null || mainChartFrame.isVisible())) {
			mainChartFrame.setTitle(finalTitle);
			mainChartFrame.removeAll();
		} else {
			mainChartFrame = GuiUtil.generateTitleFrame(finalTitle, new JPanel(),
					new Dimension(DEFAULT_FRAME_WIDTH, DEFAULT_FRAME_HEIGHT), true, true, true, true);
		}
		mainChartFrame.setLayout(new BorderLayout());
		JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
		top.add(updateButton);
		mainChartFrame.add(top, BorderLayout.NORTH);
		mainChartFrame.add(new JScrollPane(mainChartPanel), BorderLayout.CENTER);
		updateButton.addActionListener(e -> refreshChart());

		mainChartFrame.addComponentListener(new ComponentAdapter() {
			@Override
			public void componentResized(ComponentEvent e) {
				savePrefs();
			}

			@Override
			public void componentMoved(ComponentEvent e) {
				savePrefs();
			}
		});
	}

	public void displayData(Experiment exp, ResultsOptions options) {
		if (mainChartPanel == null || mainChartFrame == null) {
			throw new IllegalStateException("createMainChartPanel first");
		}
		this.lastExperiment = exp;
		this.lastOptions = options;
		refreshChart();
		mainChartFrame.pack();
		loadPrefs();
		if (mainChartFrame.getParent() == null) {
			mainChartFrame.addToDesktopPane();
		}
		mainChartFrame.setVisible(true);
		mainChartFrame.toFront();
		mainChartFrame.requestFocus();
	}

	public void refreshChart() {
		if (mainChartPanel == null || lastExperiment == null || lastOptions == null) {
			return;
		}
		List<Spot> spots = selectedSpotsProvider != null ? selectedSpotsProvider.getSelectedSpots() : null;
		mainChartPanel.removeAll();
		if (spots == null || spots.isEmpty()) {
			mainChartPanel.revalidate();
			mainChartPanel.repaint();
			updateTitle(0);
			return;
		}
		XYSeriesCollection ds = KymoSpotChartSupport.buildOverlayForSpots(lastExperiment, lastOptions, spots);
		NumberAxis xAxis = new NumberAxis("time (min)");
		xAxis.setAutoRangeIncludesZero(false);
		EnumResults rt = lastOptions.resultType;
		String yUnit = CageKymoSeriesBuilder.kymoChartRangeAxisLabel(lastOptions);
		if (yUnit == null) {
			yUnit = rt != null ? rt.toUnit() : "";
		}
		NumberAxis yAxis = new NumberAxis(yUnit);
		yAxis.setAutoRangeIncludesZero(true);
		XYPlot plot = new XYPlot(ds, xAxis, yAxis, new XYLineAndShapeRenderer());
		JFreeChart chart = new JFreeChart(plot);
		chartPanel = new ChartPanel(chart, 900, 500, 300, 200, 2000, 2000, true, true, true, true, false, true);
		mainChartPanel.add(chartPanel, BorderLayout.CENTER);
		mainChartPanel.revalidate();
		mainChartPanel.repaint();
		updateTitle(spots.size());
	}

	private void updateTitle(int nSpots) {
		if (mainChartFrame == null) {
			return;
		}
		String t = baseTitle != null ? baseTitle : "Kymograph";
		if (nSpots > 0) {
			t = t + " (" + nSpots + " spot" + (nSpots > 1 ? "s" : "") + ")";
		}
		mainChartFrame.setTitle(t);
	}

	public void setChartUpperLeftLocation(Rectangle rect) {
		if (rect == null) {
			return;
		}
		graphLocation = new Point(rect.x, rect.y);
		if (mainChartFrame != null) {
			mainChartFrame.setLocation(graphLocation);
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

	private void loadPrefs() {
		if (mainChartFrame == null) {
			return;
		}
		Preferences prefs = Preferences.userNodeForPackage(KymoOverlayFrame.class);
		int x = prefs.getInt("window_x", graphLocation.x);
		int y = prefs.getInt("window_y", graphLocation.y);
		int w = prefs.getInt("window_w", DEFAULT_FRAME_WIDTH);
		int h = prefs.getInt("window_h", DEFAULT_FRAME_HEIGHT);
		mainChartFrame.setBounds(new Rectangle(x, y, w, h));
	}

	private void savePrefs() {
		if (mainChartFrame == null) {
			return;
		}
		Preferences prefs = Preferences.userNodeForPackage(KymoOverlayFrame.class);
		Rectangle r = mainChartFrame.getBounds();
		prefs.putInt("window_x", r.x);
		prefs.putInt("window_y", r.y);
		prefs.putInt("window_w", r.width);
		prefs.putInt("window_h", r.height);
	}
}
