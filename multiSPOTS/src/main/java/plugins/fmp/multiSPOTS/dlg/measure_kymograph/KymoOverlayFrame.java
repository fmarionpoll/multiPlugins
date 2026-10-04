package plugins.fmp.multiSPOTS.dlg.measure_kymograph;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.prefs.Preferences;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.plot.XYPlot;
import org.jfree.data.Range;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.xy.XYSeriesCollection;

import icy.gui.frame.IcyFrame;
import icy.gui.util.GuiUtil;
import icy.roi.ROI2D;
import icy.sequence.Sequence;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.cage.Cage;
import plugins.fmp.multitools.experiment.cage.CageSpotStimulusAggregation;
import plugins.fmp.multitools.experiment.spot.Spot;
import plugins.fmp.multitools.tools.chart.builders.CageKymoSeriesBuilder;
import plugins.fmp.multitools.tools.chart.builders.KymoSpotChartSupport;
import plugins.fmp.multitools.tools.chart.interaction.SpotOverlayChartInteractionHandler;
import plugins.fmp.multitools.tools.chart.strategies.ComboBoxUIControlsFactory;
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
	private JComboBox<EnumResults> measureComboBox;
	private JComboBox<EnumResults> parentMeasureComboBox;
	private JComboBox<CageChoice> cageComboBox;
	private JComboBox<SpotChoice> spotComboBox;
	private boolean updatingChoices;
	private EnumResults[] measurementTypes;

	private String baseTitle;
	private Experiment lastExperiment;
	private ResultsOptions lastOptions;

	public interface SelectedSpotsProvider {
		List<Spot> getSelectedSpots();
	}

	private SelectedSpotsProvider selectedSpotsProvider;
	private Consumer<Spot> onSpotChartClicked;

	public void setSelectedSpotsProvider(SelectedSpotsProvider provider) {
		this.selectedSpotsProvider = provider;
	}

	public void setMeasurementTypes(EnumResults[] types) {
		this.measurementTypes = types;
	}

	public void setParentComboBox(JComboBox<EnumResults> parent) {
		this.parentMeasureComboBox = parent;
	}

	public void setOnSpotChartClicked(Consumer<Spot> onSpotChartClicked) {
		this.onSpotChartClicked = onSpotChartClicked;
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
		JPanel top = buildTopControlsPanel(options);
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
		fillCageCombo(preferredCage());
		fillSpotCombo(selectedCage(), preferredSpot());
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

	private JPanel buildTopControlsPanel(ResultsOptions options) {
		JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
		EnumResults[] types = measurementTypes != null && measurementTypes.length > 0 ? measurementTypes
				: new EnumResults[] { options != null && options.resultType != null ? options.resultType
						: EnumResults.KYMO_GREEN_HEIGHT_RATIO };
		measureComboBox = new JComboBox<>(types);
		if (options != null && options.resultType != null) {
			measureComboBox.setSelectedItem(options.resultType);
		}
		measureComboBox.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				EnumResults selected = (EnumResults) measureComboBox.getSelectedItem();
				if (selected != null && lastOptions != null) {
					lastOptions.resultType = selected;
					syncParentMeasureCombo(selected);
					refreshChart();
				}
			}
		});
		top.add(measureComboBox);
		cageComboBox = new JComboBox<>();
		cageComboBox.setMaximumRowCount(16);
		spotComboBox = new JComboBox<>();
		spotComboBox.setMaximumRowCount(16);
		cageComboBox.addActionListener(e -> onCageChosen());
		spotComboBox.addActionListener(e -> onSpotChosen());
		top.add(new JLabel("Cage"));
		top.add(cageComboBox);
		top.add(new JLabel("Spot"));
		top.add(spotComboBox);
		top.add(updateButton);
		return top;
	}

	private void onCageChosen() {
		if (updatingChoices) {
			return;
		}
		fillSpotCombo(selectedCage(), null);
		refreshChart();
		ComboBoxUIControlsFactory.selectCageOnCamera(lastExperiment, selectedCage());
		notifySpotChosen();
	}

	private void onSpotChosen() {
		if (updatingChoices) {
			return;
		}
		refreshChart();
		selectSpotOnCamera(selectedSpot());
		notifySpotChosen();
	}

	private void notifySpotChosen() {
		if (onSpotChartClicked != null && selectedSpot() != null) {
			onSpotChartClicked.accept(selectedSpot());
		}
	}

	private void fillCageCombo(Cage prefer) {
		if (cageComboBox == null) {
			return;
		}
		updatingChoices = true;
		try {
			cageComboBox.removeAllItems();
			if (lastExperiment == null || lastExperiment.getCages() == null) {
				return;
			}
			List<Cage> cages = new ArrayList<>();
			for (Cage cage : lastExperiment.getCages().getCageList()) {
				if (cage != null && cage.getProperties() != null) {
					cages.add(cage);
				}
			}
			cages.sort(Comparator.comparingInt(Cage::getCageID));
			CageChoice select = null;
			for (Cage cage : cages) {
				CageChoice item = new CageChoice(cage);
				cageComboBox.addItem(item);
				if (prefer != null && cage.getCageID() == prefer.getCageID()) {
					select = item;
				}
			}
			if (select != null) {
				cageComboBox.setSelectedItem(select);
			}
		} finally {
			updatingChoices = false;
		}
	}

	private void fillSpotCombo(Cage cage, Spot prefer) {
		if (spotComboBox == null) {
			return;
		}
		updatingChoices = true;
		try {
			spotComboBox.removeAllItems();
			if (cage == null || lastExperiment == null || lastExperiment.getSpots() == null) {
				return;
			}
			List<Spot> spots = cage.getSpotList(lastExperiment.getSpots());
			SpotChoice select = null;
			if (spots != null) {
				for (Spot spot : spots) {
					if (spot == null) {
						continue;
					}
					SpotChoice item = new SpotChoice(spot);
					spotComboBox.addItem(item);
					if (prefer != null && prefer.getName() != null && prefer.getName().equals(spot.getName())) {
						select = item;
					}
				}
			}
			if (select != null) {
				spotComboBox.setSelectedItem(select);
			}
		} finally {
			updatingChoices = false;
		}
	}

	private Cage preferredCage() {
		List<Spot> hinted = selectedSpotsProvider != null ? selectedSpotsProvider.getSelectedSpots() : null;
		if (hinted != null) {
			for (Spot spot : hinted) {
				Cage cage = cageOf(spot);
				if (cage != null) {
					return cage;
				}
			}
		}
		return null;
	}

	private Spot preferredSpot() {
		List<Spot> hinted = selectedSpotsProvider != null ? selectedSpotsProvider.getSelectedSpots() : null;
		if (hinted == null) {
			return null;
		}
		for (Spot spot : hinted) {
			if (spot != null) {
				return spot;
			}
		}
		return null;
	}

	private Cage cageOf(Spot spot) {
		if (spot == null || spot.getProperties() == null || lastExperiment == null || lastExperiment.getCages() == null) {
			return null;
		}
		int id = spot.getProperties().getCageID();
		for (Cage cage : lastExperiment.getCages().getCageList()) {
			if (cage != null && cage.getCageID() == id) {
				return cage;
			}
		}
		return null;
	}

	private Cage selectedCage() {
		Object item = cageComboBox != null ? cageComboBox.getSelectedItem() : null;
		return item instanceof CageChoice ? ((CageChoice) item).cage : null;
	}

	private Spot selectedSpot() {
		Object item = spotComboBox != null ? spotComboBox.getSelectedItem() : null;
		return item instanceof SpotChoice ? ((SpotChoice) item).spot : null;
	}

	private void selectSpotOnCamera(Spot spot) {
		if (lastExperiment == null || lastExperiment.getSeqCamData() == null || spot == null || spot.getName() == null) {
			return;
		}
		Sequence seq = lastExperiment.getSeqCamData().getSequence();
		if (seq == null) {
			return;
		}
		List<ROI2D> roiList = seq.getROI2Ds();
		if (roiList == null) {
			return;
		}
		ROI2D target = null;
		for (ROI2D roi : roiList) {
			if (roi == null || roi.getName() == null || !roi.getName().startsWith("spot")) {
				continue;
			}
			roi.setSelected(false);
			if (roi.getName().equals(spot.getName())) {
				target = roi;
			}
		}
		if (target != null) {
			target.setSelected(true);
			seq.setSelectedROI(target);
			lastExperiment.getSeqCamData().centerDisplayOnRoi(target);
		}
	}

	private void syncParentMeasureCombo(EnumResults selected) {
		if (parentMeasureComboBox == null || selected == null) {
			return;
		}
		if (parentMeasureComboBox.getSelectedItem() == selected) {
			return;
		}
		ActionListener[] listeners = parentMeasureComboBox.getActionListeners();
		for (ActionListener listener : listeners) {
			parentMeasureComboBox.removeActionListener(listener);
		}
		parentMeasureComboBox.setSelectedItem(selected);
		for (ActionListener listener : listeners) {
			parentMeasureComboBox.addActionListener(listener);
		}
	}

	public void refreshChart() {
		if (mainChartPanel == null || lastExperiment == null || lastOptions == null) {
			return;
		}
		if (measureComboBox != null) {
			Object sel = measureComboBox.getSelectedItem();
			if (sel instanceof EnumResults) {
				lastOptions.resultType = (EnumResults) sel;
				applyKymoAggregateChartOptions(lastExperiment, lastOptions);
			}
		}
		Spot chosen = selectedSpot();
		List<Spot> spots = chosen != null ? Collections.singletonList(chosen) : null;
		Range keepX = fixedRange(chartPanel, true);
		Range keepY = fixedRange(chartPanel, false);
		mainChartPanel.removeAll();
		if (spots == null || spots.isEmpty()) {
			mainChartPanel.revalidate();
			mainChartPanel.repaint();
			updateTitle(null);
			return;
		}
		XYSeriesCollection ds = KymoSpotChartSupport.buildOverlayForSpots(lastExperiment, lastOptions, spots);
		NumberAxis xAxis = new NumberAxis("");
		xAxis.setAutoRangeIncludesZero(false);
		EnumResults rt = lastOptions.resultType;
		String yUnit = CageKymoSeriesBuilder.kymoChartRangeAxisLabel(lastOptions);
		if (yUnit == null) {
			yUnit = rt != null ? rt.toUnit() : "";
		}
		NumberAxis yAxis = new NumberAxis(yUnit);
		yAxis.setAutoRangeIncludesZero(true);
		applyFixedRange(xAxis, keepX);
		applyFixedRange(yAxis, keepY);
		XYPlot plot = new XYPlot(ds, xAxis, yAxis, new XYLineAndShapeRenderer());
		JFreeChart chart = new JFreeChart(plot);
		chartPanel = new ChartPanel(chart, 900, 500, 300, 200, 2000, 2000, true, true, true, true, false, true);
		if (lastExperiment != null && lastOptions != null) {
			chartPanel.addChartMouseListener(new SpotOverlayChartInteractionHandler(lastExperiment, lastOptions,
					onSpotChartClicked).createMouseListener());
		}
		mainChartPanel.add(chartPanel, BorderLayout.CENTER);
		mainChartPanel.revalidate();
		mainChartPanel.repaint();
		updateTitle(chosen);
	}

	private static Range fixedRange(ChartPanel panel, boolean domain) {
		if (panel == null || panel.getChart() == null || !(panel.getChart().getPlot() instanceof XYPlot)) {
			return null;
		}
		ValueAxis axis = domain ? ((XYPlot) panel.getChart().getPlot()).getDomainAxis()
				: ((XYPlot) panel.getChart().getPlot()).getRangeAxis();
		if (axis == null || axis.isAutoRange()) {
			return null;
		}
		return axis.getRange();
	}

	private static void applyFixedRange(NumberAxis axis, Range range) {
		if (axis == null || range == null) {
			return;
		}
		axis.setAutoRange(false);
		axis.setRange(range);
	}

	private static void applyKymoAggregateChartOptions(Experiment exp, ResultsOptions options) {
		if (options == null || exp == null || exp.getSpots() == null) {
			return;
		}
		if (options.resultType == EnumResults.AGG_GREENHEIGHT_CONSO
				|| options.resultType == EnumResults.AGG_LINE_CONSO || options.resultType == EnumResults.AGG_RIM) {
			options.spotAggregateGlobalKeyOrder = CageSpotStimulusAggregation.globalStimulusConcKeysFirstSeenOrder(exp,
					exp.getSpots());
		} else {
			options.spotAggregateGlobalKeyOrder = null;
		}
	}

	private void updateTitle(Spot spot) {
		if (mainChartFrame == null) {
			return;
		}
		String t = baseTitle != null ? baseTitle : "Kymograph";
		if (spot != null && spot.getName() != null && !spot.getName().isEmpty()) {
			t = t + ": " + spot.getName();
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

	public ChartPanel getChartPanel() {
		return chartPanel;
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

	private static final class CageChoice {
		final Cage cage;

		private CageChoice(Cage cage) {
			this.cage = cage;
		}

		@Override
		public String toString() {
			return cage == null ? "" : "Cage " + cage.getCageID();
		}
	}

	private static final class SpotChoice {
		final Spot spot;

		private SpotChoice(Spot spot) {
			this.spot = spot;
		}

		@Override
		public String toString() {
			if (spot == null) {
				return "";
			}
			String name = spot.getName() != null ? spot.getName() : "";
			if (spot.getProperties() == null) {
				return name;
			}
			String stim = spot.getProperties().getStimulus();
			String conc = spot.getProperties().getConcentration();
			String extra = stim != null ? stim.trim() : "";
			if (conc != null && !conc.trim().isEmpty()) {
				extra = extra.isEmpty() ? conc.trim() : extra + " " + conc.trim();
			}
			return extra.isEmpty() ? name : name + "  " + extra;
		}
	}
}
