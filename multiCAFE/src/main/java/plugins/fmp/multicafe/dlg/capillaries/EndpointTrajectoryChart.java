package plugins.fmp.multicafe.dlg.capillaries;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Point;
import java.awt.geom.Line2D;

import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.CombinedRangeXYPlot;
import org.jfree.chart.plot.ValueMarker;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import icy.gui.frame.IcyFrame;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.capillaries.tracking.TrackingBoundary;
import plugins.fmp.multitools.experiment.capillary.Capillary;

/** Diagnostic plot of physical endpoints and pixel length through time. */
final class EndpointTrajectoryChart {
	private IcyFrame frame;
	private Experiment experiment;
	private JComboBox<Capillary> combo;
	private JComboBox<String> viewCombo;
	private JPanel holder;
	private boolean updatingExperiment;

	void open(Experiment experiment, Point location) {
		this.experiment = experiment;
		close();
		frame = new IcyFrame("Physical capillary endpoint trajectories", true, true);
		combo = new JComboBox<Capillary>();
		for (Capillary cap : experiment.getCapillaries().getList())
			combo.addItem(cap);
		combo.setRenderer((list, value, index, selected, focus) -> {
			JLabel label = new JLabel(value == null ? "" : name(value));
			if (selected) {
				label.setOpaque(true);
				label.setBackground(list.getSelectionBackground());
				label.setForeground(list.getSelectionForeground());
			}
			return label;
		});
		holder = new JPanel(new BorderLayout());
		viewCombo = new JComboBox<String>(new String[] { "Selected capillary", "All 10 cages combined" });
		JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT));
		controls.add(viewCombo);
		controls.add(combo);
		frame.add(controls, BorderLayout.NORTH);
		frame.add(holder, BorderLayout.CENTER);
		combo.addActionListener(e -> {
			if (!updatingExperiment)
				rebuild();
		});
		viewCombo.addActionListener(e -> {
			combo.setEnabled(viewCombo.getSelectedIndex() == 0);
			rebuild();
		});
		if (combo.getItemCount() > 0)
			combo.setSelectedIndex(0);
		viewCombo.setSelectedIndex(1);
		rebuild();
		frame.setSize(viewCombo.getSelectedIndex() == 1 ? 1250 : 900, 520);
		frame.setLocation(location);
		frame.addToDesktopPane();
		frame.setVisible(true);
	}

	void close() {
		if (frame != null) {
			frame.close();
			frame = null;
		}
	}

	void updateExperiment(Experiment experiment) {
		if (frame == null)
			return;
		if (experiment == null || experiment.getSeqCamData() == null) {
			close();
			return;
		}
		this.experiment = experiment;
		updatingExperiment = true;
		try {
			combo.removeAllItems();
			for (Capillary cap : experiment.getCapillaries().getList())
				combo.addItem(cap);
			if (combo.getItemCount() > 0)
				combo.setSelectedIndex(0);
		} finally {
			updatingExperiment = false;
		}
		rebuild();
	}

	private void rebuild() {
		if (viewCombo != null && viewCombo.getSelectedIndex() == 1) {
			rebuildCombined();
			return;
		}
		Capillary cap = (Capillary) combo.getSelectedItem();
		if (cap == null)
			return;
		XYSeries top = new XYSeries("Top Y");
		XYSeries bottom = new XYSeries("Bottom Y");
		XYSeries length = new XYSeries("Length (px)");
		int n = experiment.getSeqCamData().getImageLoader().getNTotalFrames();
		for (int t = 0; t < n; t++) {
			Line2D line = cap.getPhaseGeometry().getBlueAt(t);
			if (line == null)
				continue;
			top.add(t, Math.min(line.getY1(), line.getY2()));
			bottom.add(t, Math.max(line.getY1(), line.getY2()));
			length.add(t, line.getP1().distance(line.getP2()));
		}
		XYSeriesCollection endpoints = new XYSeriesCollection();
		endpoints.addSeries(top);
		endpoints.addSeries(bottom);
		JFreeChart chart = ChartFactory.createXYLineChart(name(cap), "Frame T", "Endpoint Y (px)", endpoints);
		XYPlot plot = chart.getXYPlot();
		XYLineAndShapeRenderer er = new XYLineAndShapeRenderer(true, false);
		er.setSeriesPaint(0, new Color(30, 110, 220));
		er.setSeriesPaint(1, new Color(220, 80, 40));
		plot.setRenderer(0, er);
		plot.setRangeAxis(1, new NumberAxis("Physical length (px)"));
		plot.setDataset(1, new XYSeriesCollection(length));
		plot.mapDatasetToRangeAxis(1, 1);
		XYLineAndShapeRenderer lr = new XYLineAndShapeRenderer(true, false);
		lr.setSeriesPaint(0, new Color(30, 160, 70));
		plot.setRenderer(1, lr);
		for (TrackingBoundary b : experiment.getCapillaries().getTrackingTimeline().getBoundaries())
			plot.addDomainMarker(new ValueMarker(b.getFrame(), new Color(140, 80, 180), new java.awt.BasicStroke(1f)));
		holder.removeAll();
		holder.add(new ChartPanel(chart), BorderLayout.CENTER);
		holder.revalidate();
		holder.repaint();
		if (frame != null && frame.getWidth() > 0)
			frame.setSize(900, frame.getHeight());
	}

	private void rebuildCombined() {
		NumberAxis sharedAxis = new NumberAxis("Endpoint Y (absolute px)");
		sharedAxis.setAutoRangeIncludesZero(false);
		sharedAxis.setAutoRangeMinimumSize(2.0);
		CombinedRangeXYPlot combined = new CombinedRangeXYPlot(sharedAxis);
		combined.setGap(3.0);
		int nFrames = experiment.getSeqCamData().getImageLoader().getNTotalFrames();
		for (int cage = 0; cage < 10; cage++) {
			java.util.List<Capillary> caps = new java.util.ArrayList<Capillary>();
			for (Capillary cap : experiment.getCapillaries().getList())
				if (cap.getCageID() == cage)
					caps.add(cap);
			if (caps.isEmpty())
				continue;
			XYSeriesCollection data = new XYSeriesCollection();
			for (int ci = 0; ci < caps.size(); ci++) {
				Capillary cap = caps.get(ci);
				XYSeries top = new XYSeries((ci == 0 ? "L" : "R") + " top");
				XYSeries bottom = new XYSeries((ci == 0 ? "L" : "R") + " bottom");
				for (int t = 0; t < nFrames; t++) {
					Line2D line = cap.getPhaseGeometry().getBlueAt(t);
					if (line == null)
						continue;
					double at = Math.min(line.getY1(), line.getY2());
					double ab = Math.max(line.getY1(), line.getY2());
					top.add(t, at);
					bottom.add(t, ab);
				}
				data.addSeries(top);
				data.addSeries(bottom);
			}
			NumberAxis timeAxis = new NumberAxis("cage " + cage);
			timeAxis.setAutoRangeIncludesZero(true);
			XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer(true, false);
			Color[] colors = { Color.BLUE, Color.BLUE, Color.RED, Color.RED };
			for (int s = 0; s < data.getSeriesCount(); s++) {
				renderer.setSeriesPaint(s, colors[s % colors.length]);
				if ((s & 1) == 1)
					renderer.setSeriesStroke(s, new java.awt.BasicStroke(1.5f, java.awt.BasicStroke.CAP_BUTT,
							java.awt.BasicStroke.JOIN_BEVEL, 0f, new float[] { 5f, 3f }, 0f));
			}
			XYPlot subplot = new XYPlot(data, timeAxis, null, renderer);
			subplot.setBackgroundPaint(new Color(245, 245, 245));
			for (TrackingBoundary b : experiment.getCapillaries().getTrackingTimeline().getBoundaries())
				subplot.addDomainMarker(new ValueMarker(b.getFrame(), Color.GRAY, new java.awt.BasicStroke(1f)));
			combined.add(subplot, 1);
		}
		JFreeChart chart = new JFreeChart("All cages: physical capillary endpoints", JFreeChart.DEFAULT_TITLE_FONT,
				combined, false);
		ChartPanel panel = new ChartPanel(chart, 1200, 450, 600, 250, 3000, 1200, true, true, true, true, false, true);
		holder.removeAll();
		holder.add(panel, BorderLayout.CENTER);
		holder.revalidate();
		holder.repaint();
		if (frame != null && frame.getWidth() > 0)
			frame.setSize(1250, frame.getHeight());
	}

	private static String name(Capillary cap) {
		String n = cap.getRoiName();
		return n == null || n.isEmpty() ? cap.getKymographName() : n;
	}
}
