package plugins.fmp.multitools.tools.chart.strategies;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Paint;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.ComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

import org.jfree.chart.ChartColor;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.cage.Cage;
import plugins.fmp.multitools.experiment.cage.CageSpotStimulusAggregation;
import plugins.fmp.multitools.experiment.cage.CageSpotStimulusAggregation.StimulusConcKey;
import plugins.fmp.multitools.experiment.capillaries.Capillaries;
import plugins.fmp.multitools.experiment.capillary.Capillary;
import plugins.fmp.multitools.experiment.spot.Spot;
import plugins.fmp.multitools.tools.chart.ChartCageBuild;
import plugins.fmp.multitools.tools.chart.builders.SpotChartSeriesKeys;
import plugins.fmp.multitools.tools.chart.style.SeriesStyleCodec;
import plugins.fmp.multitools.tools.results.EnumResults;
import plugins.fmp.multitools.tools.results.ResultsOptions;

/**
 * UI controls factory that provides a combobox for selecting result types and a
 * legend panel at the bottom. This is used for the levels dialog.
 */
public class ComboBoxUIControlsFactory implements ChartUIControlsFactory {

	private JComboBox<EnumResults> resultTypeComboBox;
	private JComboBox<EnumResults> parentComboBox;
	private JPanel bottomPanel;
	private EnumResults[] measurementTypes;
	private Experiment currentExperiment;

	/**
	 * Sets the current experiment for legend generation.
	 * 
	 * @param experiment the experiment
	 */
	public void setExperiment(Experiment experiment) {
		this.currentExperiment = experiment;
	}

	/**
	 * Sets the parent combobox for synchronization.
	 * 
	 * @param comboBox the parent combobox
	 */
	public void setParentComboBox(JComboBox<EnumResults> comboBox) {
		this.parentComboBox = comboBox;
		if (comboBox != null) {
			ComboBoxModel<EnumResults> model = comboBox.getModel();
			int size = model.getSize();
			EnumResults[] types = new EnumResults[size];
			for (int i = 0; i < size; i++) {
				types[i] = model.getElementAt(i);
			}
			this.measurementTypes = types;
		}
	}

	/**
	 * Sets the available measurement types.
	 * 
	 * @param types the measurement types
	 */
	public void setMeasurementTypes(EnumResults[] types) {
		this.measurementTypes = types;
	}

	@Override
	public JPanel createTopPanel(ResultsOptions currentOptions, ActionListener changeListener) {
		JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));

		EnumResults[] typesToUse = getMeasurementTypes();
		resultTypeComboBox = new JComboBox<EnumResults>(typesToUse);
		if (currentOptions != null && currentOptions.resultType != null) {
			resultTypeComboBox.setSelectedItem(currentOptions.resultType);
		}

		resultTypeComboBox.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				EnumResults selectedType = (EnumResults) resultTypeComboBox.getSelectedItem();
				if (selectedType != null && currentOptions != null) {
					currentOptions.resultType = selectedType;

					// Synchronize with parent combobox if it exists
					if (parentComboBox != null && parentComboBox.getSelectedItem() != selectedType) {
						ActionListener[] listeners = parentComboBox.getActionListeners();
						for (ActionListener listener : listeners) {
							parentComboBox.removeActionListener(listener);
						}
						parentComboBox.setSelectedItem(selectedType);
						for (ActionListener listener : listeners) {
							parentComboBox.addActionListener(listener);
						}
					}

					// Notify the change listener
					if (changeListener != null) {
						changeListener.actionPerformed(e);
					}
				}
			}
		});

		topPanel.add(resultTypeComboBox);

		JButton updateButton = new JButton("Update");
		updateButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				if (changeListener != null) {
					changeListener
							.actionPerformed(new ActionEvent(updateButton, ActionEvent.ACTION_PERFORMED, "update"));
				}
			}
		});
		topPanel.add(updateButton);

		return topPanel;
	}

	@Override
	public JPanel createBottomPanel(ResultsOptions currentOptions, Experiment experiment) {
		this.currentExperiment = experiment;
		bottomPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
		fillBottomPanel(currentOptions, experiment, null);
		return bottomPanel;
	}

	@Override
	public void updateControls(EnumResults newResultType, ResultsOptions currentOptions) {
		if (resultTypeComboBox != null && newResultType != null) {
			resultTypeComboBox.setSelectedItem(newResultType);
		}
		fillBottomPanel(currentOptions, currentExperiment, null);
	}

	@Override
	public void refreshLegendFromDataset(Experiment experiment, ResultsOptions options, XYSeriesCollection dataset) {
		this.currentExperiment = experiment;
		fillBottomPanel(options, experiment, dataset);
	}

	private void fillBottomPanel(ResultsOptions currentOptions, Experiment experiment, XYSeriesCollection dataset) {
		if (bottomPanel == null || currentOptions == null) {
			return;
		}

		bottomPanel.removeAll();

		if (currentOptions.resultType == EnumResults.TOPLEVEL_SUM) {
			bottomPanel.add(new LegendItem("Sum", Color.BLUE));
		} else if (currentOptions.resultType == EnumResults.TOPLEVEL_PI) {
			bottomPanel.add(new LegendItem("PI", Color.RED));
		} else if (ChartCageBuild.isLRType(currentOptions.resultType)) {
			bottomPanel.add(new LegendItem("Sum", Color.BLUE));
			bottomPanel.add(new LegendItem("PI", Color.RED));
		} else if (isSpotChartLegendResultType(currentOptions.resultType)) {
			fillSpotLegendPanel(currentOptions, experiment, dataset);
		} else if (ChartCageBuild.isFlyPositionChartResultType(currentOptions.resultType)) {
			String axisLegend = ChartCageBuild.flyPositionChartAxisLegend(currentOptions.resultType);
			if (axisLegend != null) {
				bottomPanel.add(new JLabel(axisLegend));
			}
		} else {
			createDynamicCapillaryLegend(experiment);
		}

		bottomPanel.revalidate();
		bottomPanel.repaint();
	}

	private static boolean isSpotChartLegendResultType(EnumResults r) {
		if (r == null) {
			return false;
		}
		switch (r) {
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
		case KYMO_CAGE_MEAN_FRACT:
		case KYMO_CAGE_MEAN_ABS_DELTA:
		case KYMO_GREEN_HEIGHT:
		case KYMO_GREEN_HEIGHT_RATIO:
		case KYMO_CAGE_MEAN_GREEN_HEIGHT_RATIO:
		case AGG_GREENHEIGHT_CONSO:
			return true;
		default:
			return false;
		}
	}

	/**
	 * Bottom legend for spot/kymo charts: one entry per unique stimulus+concentration
	 * category (using each category's spot color), not one entry per curve.
	 */
	private void fillSpotLegendPanel(ResultsOptions opts, Experiment exp, XYSeriesCollection dataset) {
		if (fillStimulusConcCategoryLegend(exp, dataset)) {
			return;
		}
		EnumResults rt = opts != null ? opts.resultType : null;
		if (rt == EnumResults.AGG_SUMCLEAN || rt == EnumResults.AGG_SUMCLEAN_V5 || rt == EnumResults.AGG_AREA_COUNT_V5
				|| rt == EnumResults.AGG_SUMCLEAN_COLOR || rt == EnumResults.AGG_AREA_COUNT_COLOR
				|| rt == EnumResults.AGG_MEDIANREF || rt == EnumResults.AGG_GREENHEIGHT_CONSO) {
			if (dataset != null && dataset.getSeriesCount() > 0) {
				List<String> seen = new ArrayList<>();
				int palette = 0;
				for (int i = 0; i < dataset.getSeriesCount(); i++) {
					XYSeries s = dataset.getSeries(i);
					String key = s.getKey().toString();
					if (SpotChartSeriesKeys.isMedianRefSeriesKey(key) || isCageMeanSeriesKey(key)) {
						continue;
					}
					String rawLabel = SpotChartSeriesKeys.isAggregateSeriesKey(key) ? labelFromAggregateSeriesKey(key)
							: spotLegendLabelFromSeriesKey(key);
					String label = clipLegendText(rawLabel, 28);
					if (seen.contains(label)) {
						continue;
					}
					seen.add(label);
					Color c = SeriesStyleCodec.tryParseColor(s.getDescription()).orElse(aggregatePaletteColor(palette++));
					bottomPanel.add(new LegendItem(label, c));
				}
				if (datasetHasCageMean(dataset)) {
					bottomPanel.add(new LegendItem("cage mean", Color.DARK_GRAY));
				}
				if (datasetHasMedianRef(dataset)) {
					bottomPanel.add(new LegendItem("median ref", Color.GRAY));
				}
				if (!seen.isEmpty()) {
					return;
				}
			}
		}
		if (dataset != null && dataset.getSeriesCount() > 0 && datasetHasCageMean(dataset)
				&& dataset.getSeriesCount() <= 2) {
			bottomPanel.add(new LegendItem("cage mean", Color.DARK_GRAY));
			return;
		}
		bottomPanel.add(new LegendItem("Spots", Color.DARK_GRAY));
	}

	/**
	 * @return true if at least one stim+conc category was added to the legend
	 */
	private boolean fillStimulusConcCategoryLegend(Experiment exp, XYSeriesCollection dataset) {
		if (exp == null || exp.getSpots() == null) {
			return false;
		}
		List<StimulusConcKey> keys = CageSpotStimulusAggregation.globalStimulusConcKeysFirstSeenOrder(exp,
				exp.getSpots());
		if (keys == null || keys.isEmpty()) {
			return false;
		}
		boolean anyMeaningful = false;
		int palette = 0;
		for (StimulusConcKey k : keys) {
			String label = formatStimConcLabel(k);
			if (label.isEmpty() || "_".equals(label)) {
				continue;
			}
			anyMeaningful = true;
			Color c = colorOfFirstSpotMatching(exp, k);
			if (c == null) {
				c = aggregatePaletteColor(palette);
			}
			palette++;
			bottomPanel.add(new LegendItem(clipLegendText(label, 28), c));
		}
		if (!anyMeaningful) {
			return false;
		}
		if (datasetHasCageMean(dataset)) {
			bottomPanel.add(new LegendItem("cage mean", Color.DARK_GRAY));
		}
		if (datasetHasMedianRef(dataset)) {
			bottomPanel.add(new LegendItem("median ref", Color.GRAY));
		}
		return true;
	}

	private static String formatStimConcLabel(StimulusConcKey k) {
		if (k == null) {
			return "";
		}
		String stim = k.stimulus != null ? k.stimulus.trim() : "";
		String conc = k.concentration != null ? k.concentration.trim() : "";
		if (stim.isEmpty() && conc.isEmpty()) {
			return "";
		}
		if (stim.isEmpty()) {
			return conc;
		}
		if (conc.isEmpty()) {
			return stim;
		}
		return stim + "_" + conc;
	}

	private static Color colorOfFirstSpotMatching(Experiment exp, StimulusConcKey key) {
		if (exp == null || exp.getSpots() == null || key == null) {
			return null;
		}
		for (Spot spot : exp.getSpots().getSpotList()) {
			if (spot == null || spot.getProperties() == null) {
				continue;
			}
			StimulusConcKey spotKey = new StimulusConcKey(spot.getProperties().getStimulus(),
					spot.getProperties().getConcentration());
			if (!key.equals(spotKey)) {
				continue;
			}
			Color c = spot.getProperties().getColor();
			if (c != null) {
				return c;
			}
		}
		return null;
	}

	private static boolean datasetHasCageMean(XYSeriesCollection dataset) {
		if (dataset == null) {
			return false;
		}
		for (int i = 0; i < dataset.getSeriesCount(); i++) {
			if (isCageMeanSeriesKey(dataset.getSeries(i).getKey().toString())) {
				return true;
			}
		}
		return false;
	}

	private static boolean datasetHasMedianRef(XYSeriesCollection dataset) {
		if (dataset == null) {
			return false;
		}
		for (int i = 0; i < dataset.getSeriesCount(); i++) {
			if (SpotChartSeriesKeys.isMedianRefSeriesKey(dataset.getSeries(i).getKey().toString())) {
				return true;
			}
		}
		return false;
	}

	private static boolean isCageMeanSeriesKey(String key) {
		return key != null && key.startsWith("cage mean");
	}

	private static String clipLegendText(String s, int max) {
		if (s == null || s.isEmpty()) {
			return "?";
		}
		return s.length() > max ? s.substring(0, max) : s;
	}

	private static String spotLegendLabelFromSeriesKey(String key) {
		if (key == null) {
			return "";
		}
		int sep = key.lastIndexOf(SpotChartSeriesKeys.SEP);
		return sep > 0 ? key.substring(0, sep) : key;
	}

	private static String labelFromAggregateSeriesKey(String seriesKey) {
		if (seriesKey == null || !SpotChartSeriesKeys.isAggregateSeriesKey(seriesKey)) {
			return seriesKey;
		}
		int l = seriesKey.indexOf('(');
		int r = seriesKey.indexOf(')', l + 1);
		if (l < 0 || r <= l) {
			return seriesKey;
		}
		return seriesKey.substring(l + 1, r).replace(',', '_');
	}

	private static Color aggregatePaletteColor(int index) {
		Paint[] paints = ChartColor.createDefaultPaintArray();
		if (paints == null || paints.length == 0) {
			return Color.BLACK;
		}
		Paint p = paints[Math.max(0, index) % paints.length];
		return p instanceof Color ? (Color) p : Color.BLACK;
	}

	/**
	 * Creates a dynamic legend based on the maximum number of capillaries per cage
	 * and their properties (position, stimulus, concentration).
	 */
	private void createDynamicCapillaryLegend(Experiment experiment) {
		if (experiment == null || experiment.getCapillaries() == null) {
			// Fallback to default L/R if no experiment data
			bottomPanel.add(new LegendItem("L", Color.BLUE));
			bottomPanel.add(new LegendItem("R", Color.RED));
			return;
		}

		// Calculate maximum number of capillaries per cage
		Map<Integer, Integer> capillariesPerCage = new HashMap<>();
		for (Capillary cap : experiment.getCapillaries().getList()) {
			int cageID = cap.getCageID();
			capillariesPerCage.put(cageID, capillariesPerCage.getOrDefault(cageID, 0) + 1);
		}

		int maxCapillariesPerCage = 0;
		for (int count : capillariesPerCage.values()) {
			if (count > maxCapillariesPerCage) {
				maxCapillariesPerCage = count;
			}
		}

		// If no capillaries found, use default
		if (maxCapillariesPerCage == 0) {
			bottomPanel.add(new LegendItem("L", Color.BLUE));
			bottomPanel.add(new LegendItem("R", Color.RED));
			return;
		}

		// Get capillaries from the first cage that has the maximum number
		// This ensures we show all possible capillary types
		Capillaries allCapillaries = experiment.getCapillaries();
		if (allCapillaries == null) {
			bottomPanel.add(new LegendItem("L", Color.BLUE));
			bottomPanel.add(new LegendItem("R", Color.RED));
			return;
		}

		List<Capillary> referenceCapillaries = new ArrayList<>();
		for (Cage cage : experiment.getCages().getCageList()) {
			List<Capillary> cageCaps = cage.getCapillaries(allCapillaries);
			if (cageCaps != null && cageCaps.size() == maxCapillariesPerCage) {
				referenceCapillaries = cageCaps;
				break;
			}
		}

		// If we didn't find a cage with max capillaries, get from any cage
		if (referenceCapillaries.isEmpty()) {
			for (Cage cage : experiment.getCages().getCageList()) {
				List<Capillary> cageCaps = cage.getCapillaries(allCapillaries);
				if (cageCaps != null && !cageCaps.isEmpty()) {
					referenceCapillaries = cageCaps;
					break;
				}
			}
		}

		// Create legend items for up to maxCapillariesPerCage
		// Use colors that cycle through a palette
		Color[] colors = { Color.BLUE, Color.RED, Color.GREEN, Color.ORANGE, Color.MAGENTA, Color.CYAN, Color.PINK,
				Color.YELLOW, Color.GRAY, Color.DARK_GRAY };

		for (int i = 0; i < maxCapillariesPerCage && i < referenceCapillaries.size(); i++) {
			Capillary cap = referenceCapillaries.get(i);
			String position = cap.getSide();
			if (position == null || position.isEmpty() || position.equals(".")) {
				position = String.valueOf(i + 1); // Fallback to index if no side
			}

			String stimulus = cap.getStimulus();
			if (stimulus == null || stimulus.isEmpty()) {
				stimulus = "?";
			} else {
				// Clip to first 3 characters
				stimulus = stimulus.length() > 3 ? stimulus.substring(0, 3) : stimulus;
			}

			String concentration = cap.getConcentration();
			if (concentration == null || concentration.isEmpty()) {
				concentration = "?";
			} else {
				// Clip to first 3 characters
				concentration = concentration.length() > 5 ? concentration.substring(0, 5) : concentration;
			}

			// Combine stimulus and concentration: stimulus_concentration
			String stimulusWithConcentration = stimulus + "_" + concentration;
			String label = position + "_" + stimulusWithConcentration;
			Color color = colors[i % colors.length];
			bottomPanel.add(new LegendItem(label, color));
		}
	}

	private EnumResults[] getMeasurementTypes() {
		if (measurementTypes != null && measurementTypes.length > 0) {
			return measurementTypes;
		}
		// Fallback default list
		return new EnumResults[] { //
				EnumResults.TOPRAW, //
				EnumResults.TOPLEVEL, //
				EnumResults.BOTTOMLEVEL, //
				EnumResults.TOPLEVEL_SUM, //
				EnumResults.TOPLEVEL_PI, //
				EnumResults.DERIVEDVALUES, //
				EnumResults.SUMGULPS, //
				EnumResults.SUMGULPS_LR, //
				EnumResults.NBGULPS, //
				EnumResults.AMPLITUDEGULPS, //
				EnumResults.TTOGULP };
	}

	/**
	 * Gets the result type combobox for external access.
	 * 
	 * @return the combobox
	 */
	public JComboBox<EnumResults> getResultTypeComboBox() {
		return resultTypeComboBox;
	}

	/**
	 * Simple legend item component.
	 */
	private static class LegendItem extends JComponent {
		private static final long serialVersionUID = 1L;
		private String text;
		private Color color;

		public LegendItem(String text, Color color) {
			this.text = text;
			this.color = color;
			setPreferredSize(new Dimension(100, 20));
		}

		@Override
		protected void paintComponent(Graphics g) {
			super.paintComponent(g);
			g.setColor(color);
			g.drawLine(0, 10, 20, 10);
			g.setColor(Color.BLACK);
			g.drawString(text, 25, 15);
		}
	}
}
