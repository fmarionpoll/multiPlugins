package plugins.fmp.multitools.fmp_tools.chart.strategies;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
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
import javax.swing.JPanel;

import plugins.fmp.multitools.fmp_experiment.Experiment;
import plugins.fmp.multitools.fmp_experiment.cages.Cage;
import plugins.fmp.multitools.fmp_experiment.capillaries.Capillaries;
import plugins.fmp.multitools.fmp_experiment.capillaries.Capillary;
import plugins.fmp.multitools.fmp_tools.chart.ChartCageBuild;
import plugins.fmp.multitools.fmp_tools.results.EnumResults;
import plugins.fmp.multitools.fmp_tools.results.ResultsOptions;

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
					changeListener.actionPerformed(new ActionEvent(updateButton, ActionEvent.ACTION_PERFORMED, "update"));
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
		updateBottomPanel(currentOptions, experiment);
		return bottomPanel;
	}

	@Override
	public void updateControls(EnumResults newResultType, ResultsOptions currentOptions) {
		if (resultTypeComboBox != null && newResultType != null) {
			resultTypeComboBox.setSelectedItem(newResultType);
		}
		updateBottomPanel(currentOptions, currentExperiment);
	}

	private void updateBottomPanel(ResultsOptions currentOptions, Experiment experiment) {
		if (bottomPanel == null || currentOptions == null) {
			return;
		}

		bottomPanel.removeAll();

		if (ChartCageBuild.isLRType(currentOptions.resultType)) {
			// For LR types, show Sum and PI
			bottomPanel.add(new LegendItem("Sum", Color.BLUE));
			bottomPanel.add(new LegendItem("PI", Color.RED));
		} else {
			// For non-LR types, show dynamic legend based on capillaries
			createDynamicCapillaryLegend(experiment);
		}

		bottomPanel.revalidate();
		bottomPanel.repaint();
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
		return new EnumResults[] { EnumResults.TOPRAW, EnumResults.TOPLEVEL, EnumResults.BOTTOMLEVEL,
				EnumResults.TOPLEVEL_LR, EnumResults.DERIVEDVALUES, EnumResults.SUMGULPS, EnumResults.SUMGULPS_LR };
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
