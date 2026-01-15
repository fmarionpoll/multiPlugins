package plugins.fmp.multicafe.dlg.levels;

import java.awt.Rectangle;

import javax.swing.JComboBox;
import javax.swing.JPanel;

import org.jfree.data.Range;

import icy.gui.frame.IcyFrame;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.tools.chart.ChartCagePair;
import plugins.fmp.multitools.tools.chart.ChartCagesFrame;
import plugins.fmp.multitools.tools.chart.ChartInteractionHandler;
import plugins.fmp.multitools.tools.chart.ChartInteractionHandlerFactory;
import plugins.fmp.multitools.tools.chart.builders.CageCapillarySeriesBuilder;
import plugins.fmp.multitools.tools.chart.builders.CageSeriesBuilder;
import plugins.fmp.multitools.tools.chart.builders.CageSpotSeriesBuilder;
import plugins.fmp.multitools.tools.chart.strategies.ComboBoxUIControlsFactory;
import plugins.fmp.multitools.tools.chart.strategies.GridLayoutStrategy;
import plugins.fmp.multitools.tools.results.EnumResults;
import plugins.fmp.multitools.tools.results.ResultsOptions;

/**
 * Chart display class for capillary and spot data visualization. This class
 * creates and manages a grid of charts displaying measurements for different
 * cages in an experiment.
 * 
 * <p>
 * This class is a wrapper around the generic {@link ChartCagesFrame} that
 * provides a convenient API for the levels dialog. It automatically configures
 * the appropriate data builder, interaction handlers, and UI controls for
 * capillary/spot measurements.
 * </p>
 * 
 * <p>
 * Usage example:
 * 
 * <pre>
 * ChartCageArrayFrame chartFrame = new ChartCageArrayFrame();
 * chartFrame.createMainChartPanel("Capillary level measures", experiment, options);
 * chartFrame.setParentComboBox(parentComboBox);
 * chartFrame.displayData(experiment, options);
 * </pre>
 * 
 * @author MultiCAFE
 */
public class ChartCageArrayFrame extends IcyFrame {

	/** The underlying generic chart frame */
	private ChartCagesFrame genericFrame;

	/** UI controls factory for combobox and legend */
	private ComboBoxUIControlsFactory uiControlsFactory;

	/**
	 * Creates the main chart panel and frame.
	 * 
	 * @param title   the title for the chart window
	 * @param exp     the experiment containing the data
	 * @param options the export options for data processing
	 * @throws IllegalArgumentException if any required parameter is null
	 */
	public void createMainChartPanel(String title, Experiment exp, ResultsOptions options) {
		// Create strategies
		GridLayoutStrategy layoutStrategy = new GridLayoutStrategy();
		uiControlsFactory = new ComboBoxUIControlsFactory();

		// Select appropriate data builder based on result type
		CageSeriesBuilder dataBuilder = selectDataBuilder(options != null ? options.resultType : null);

		// Create interaction handler factory
		ChartInteractionHandlerFactory handlerFactory = new ChartInteractionHandlerFactory() {
			@Override
			public ChartInteractionHandler createHandler(Experiment experiment, ResultsOptions resultsOptions,
					ChartCagePair[][] chartArray) {
				return createInteractionHandler(experiment, resultsOptions, chartArray);
			}
		};

		genericFrame = new ChartCagesFrame(dataBuilder, handlerFactory, layoutStrategy, uiControlsFactory);

		genericFrame.createMainChartPanel(title, exp, options);
	}

	/**
	 * Selects the appropriate data builder based on result type.
	 * 
	 * @param resultType the result type
	 * @return the appropriate builder
	 */
	private CageSeriesBuilder selectDataBuilder(EnumResults resultType) {
		if (isSpotResultType(resultType)) {
			return new CageSpotSeriesBuilder();
		} else {
			// Default to capillary builder
			return new CageCapillarySeriesBuilder();
		}
	}

	/**
	 * Displays data for the experiment.
	 * 
	 * @param exp            the experiment containing the data
	 * @param resultsOptions the export options for data processing
	 * @throws IllegalArgumentException if exp or resultsOptions is null
	 */
	public void displayData(Experiment exp, ResultsOptions resultsOptions) {
		if (genericFrame == null) {
			throw new IllegalStateException("createMainChartPanel must be called first");
		}

		// Check if we need to recreate the frame with a different builder
		// (e.g., if result type changed from capillary to spot or vice versa)
		// For now, we'll recreate it if the builder type would be different
		// This is a limitation - ideally the framework would support changing builders

		genericFrame.displayData(exp, resultsOptions);
	}

	/**
	 * Creates the appropriate interaction handler based on the result type.
	 * 
	 * @param exp            the experiment
	 * @param resultsOptions the export options
	 * @param chartArray     the chart panel array
	 * @return the appropriate interaction handler
	 */
	private ChartInteractionHandler createInteractionHandler(Experiment exp, ResultsOptions resultsOptions,
			ChartCagePair[][] chartArray) {
		if (isSpotResultType(resultsOptions.resultType)) {
			return new SpotChartInteractionHandler(exp, resultsOptions, chartArray);
		} else {
			// Default to capillary handler for capillary types and others
			return new CapillaryChartInteractionHandler(exp, resultsOptions, chartArray);
		}
	}

	/**
	 * Determines if a result type is for spot measurements.
	 * 
	 * @param resultType the result type to check
	 * @return true if it's a spot type, false otherwise
	 */
	private boolean isSpotResultType(EnumResults resultType) {
		if (resultType == null) {
			return false;
		}
		switch (resultType) {
		case AREA_SUM:
		case AREA_SUMCLEAN:
		case AREA_OUT:
		case AREA_DIFF:
		case AREA_FLYPRESENT:
			return true;
		default:
			return false;
		}
	}

	/**
	 * Sets the chart location relative to a rectangle.
	 * 
	 * @param rectv the reference rectangle
	 * @throws IllegalArgumentException if rectv is null
	 */
	public void setChartUpperLeftLocation(Rectangle rectv) {
		if (genericFrame != null) {
			genericFrame.setChartUpperLeftLocation(rectv);
		}
	}

	/**
	 * Sets the parent combobox reference for synchronization.
	 * 
	 * @param comboBox the parent combobox to synchronize with
	 */
	public void setParentComboBox(JComboBox<EnumResults> comboBox) {
		if (uiControlsFactory != null) {
			uiControlsFactory.setParentComboBox(comboBox);
		}
	}

	// Delegation methods for backward compatibility

	/**
	 * Gets the main chart panel.
	 * 
	 * @return the main chart panel
	 */
	public JPanel getMainChartPanel() {
		return genericFrame != null ? genericFrame.getMainChartPanel() : null;
	}

	/**
	 * Gets the main chart frame.
	 * 
	 * @return the main chart frame
	 */
	public IcyFrame getMainChartFrame() {
		return genericFrame != null ? genericFrame.getMainChartFrame() : null;
	}

	/**
	 * Gets the chart panel array.
	 * 
	 * @return the chart panel array
	 */
	public ChartCagePair[][] getChartCagePairArray() {
		return genericFrame != null ? genericFrame.getChartCagePairArray() : null;
	}

	/**
	 * Gets the number of panels along X axis.
	 * 
	 * @return the number of panels along X
	 */
	public int getPanelsAlongX() {
		return genericFrame != null ? genericFrame.getPanelsAlongX() : 0;
	}

	/**
	 * Gets the number of panels along Y axis.
	 * 
	 * @return the number of panels along Y
	 */
	public int getPanelsAlongY() {
		return genericFrame != null ? genericFrame.getPanelsAlongY() : 0;
	}

	public Range getXRange() {
		return genericFrame != null ? genericFrame.getXRange() : null;
	}

	public void setXRange(Range range) {
		if (genericFrame != null) {
			genericFrame.setXRange(range);
		}
	}

	public Range getYRange() {
		return genericFrame != null ? genericFrame.getYRange() : null;
	}

	public void setYRange(Range range) {
		if (genericFrame != null) {
			genericFrame.setYRange(range);
		}
	}
}
