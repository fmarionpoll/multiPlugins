package plugins.fmp.multiSPOTS.dlg.measure;

import static org.junit.Assert.*;

import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;
import javax.swing.JComboBox;
import javax.swing.JRadioButton;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;

import org.junit.Test;

import plugins.fmp.multitools.tools.chart.SpotChartMeasures.Family;
import plugins.fmp.multitools.tools.results.EnumResults;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.cage.Cage;
import plugins.fmp.multitools.experiment.spot.Spot;
import plugins.kernel.roi.roi2d.ROI2DEllipse;
import icy.sequence.Sequence;

public class MeasureChartsPanelTest {
	@org.junit.BeforeClass public static void initializeIcyPreferences() {
		icy.preferences.IcyPreferences.init();
	}

	@Test public void spotDisplayFallsBackWhenCameraIsOpenAndNoSpotRoiIsSelected() {
		Experiment exp = new Experiment();
		Sequence sequence = new Sequence();
		exp.getSeqCamData().attachSequence(sequence);
		try {
			Cage cage = new Cage(); cage.getProperties().setCageID(1);
			exp.getCages().cagesList.add(cage);
			Spot first = spot(exp, sequence, "spot1_a");
			Spot second = spot(exp, sequence, "spot1_b");
			assertEquals(java.util.Collections.singletonList(first), MeasureChartsPanel.initialSpots(exp));
			assertFalse(first.getRoi().isSelected());
			assertFalse(second.getRoi().isSelected());
			second.getRoi().setSelected(true);
			assertEquals(java.util.Collections.singletonList(second), MeasureChartsPanel.initialSpots(exp));
			first.getRoi().setSelected(true);
			assertEquals(java.util.Arrays.asList(first, second), MeasureChartsPanel.initialSpots(exp));
		} finally { sequence.close(); }
	}

	@Test public void spotDisplayWithoutAnyAvailableSpotsReturnsAnEmptySelection() {
		assertTrue(MeasureChartsPanel.initialSpots(new Experiment()).isEmpty());
	}

	private static Spot spot(Experiment exp, Sequence sequence, String name) {
		ROI2DEllipse roi = new ROI2DEllipse(0, 0, 5, 5); roi.setName(name); roi.setSelected(false);
		Spot spot = new Spot(roi); spot.getProperties().setCageID(1); spot.getProperties().setName(name);
		exp.getSpots().getSpotList().add(spot); sequence.addROI(roi); return spot;
	}
	@Test public void allFamiliesSeparateSummaryOptionsAndNeverOfferSpotScopeForSummaries() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			for (Family family : Family.values()) {
				MeasureChartsPanel panel = new MeasureChartsPanel(null, family);
				JTabbedPane tabs = (JTabbedPane) panel.getComponent(0);
				assertEquals("Spot measurements", tabs.getTitleAt(0));
				assertEquals("Cage summaries", tabs.getTitleAt(1));
				Container spots = (Container) tabs.getComponentAt(0);
				Container summaries = (Container) tabs.getComponentAt(1);
				assertEquals(3, descendants(spots, JRadioButton.class).size());
				assertEquals(2, descendants(summaries, JRadioButton.class).size());
				assertOptions(spots, false);
				assertOptions(summaries, true);
				// The two views retain their own measurement choices when switching tabs.
				JComboBox<?> spotChoice = measureCombo(spots);
				Object before = spotChoice.getSelectedItem();
				tabs.setSelectedIndex(1);
				JComboBox<?> summaryChoice = measureCombo(summaries);
				summaryChoice.setSelectedIndex(summaryChoice.getItemCount() - 1);
				tabs.setSelectedIndex(0);
				assertEquals(before, spotChoice.getSelectedItem());
				panel.closeAllCharts();
			}
		});
	}

	@Test public void calculationOptionsFollowTheSummaryAndCanRenderWithoutAnExperiment() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			MeasureChartsPanel panel = new MeasureChartsPanel(null, Family.FILTER);
			JTabbedPane tabs = (JTabbedPane) panel.getComponent(0);
			tabs.setSelectedIndex(1);
			Container summaries = (Container) tabs.getComponentAt(1);
			JComboBox<?> choice = measureCombo(summaries);
			choice.setSelectedItem(EnumResults.CAGE_GREY_MEDIAN);
			assertFalse(rowVisible(summaries, "Baseline (min)"));
			assertTrue(rowVisible(summaries, "Median reference smoothing (samples)"));
			choice.setSelectedItem(EnumResults.AGG_FILTER_DEPLETION);
			assertTrue(rowVisible(summaries, "Baseline (min)"));
			String directory = System.getProperty("chart.preview.dir");
			if (directory != null) {
				render(panel, new File(directory, "filter-cage-summaries.png"));
				MeasureChartsPanel lines = new MeasureChartsPanel(null, Family.LINES);
				render(lines, new File(directory, "lines-spot-measurements.png"));
			}
		});
	}

	private static void assertOptions(Container root, boolean summary) {
		JComboBox<?> combo = measureCombo(root);
		for (int i = 0; i < combo.getItemCount(); i++)
			assertEquals(summary, ((EnumResults) combo.getItemAt(i)).isCageSpotSummary());
	}

	private static JComboBox<?> measureCombo(Container root) {
		for (JComboBox<?> combo : MeasureChartsPanelTest.<JComboBox<?>>descendants(root, JComboBox.class))
			if (combo.getItemCount() > 0 && combo.getItemAt(0) instanceof EnumResults) return combo;
		throw new AssertionError("Missing measurement selector");
	}

	private static boolean rowVisible(Container root, String text) {
		for (javax.swing.JLabel label : MeasureChartsPanelTest.<javax.swing.JLabel>descendants(root, javax.swing.JLabel.class))
			if (text.equals(label.getText())) return label.getParent().isVisible();
		throw new AssertionError("Missing row: " + text);
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private static <T> List<T> descendants(Container root, Class type) {
		List<T> result = new ArrayList<>();
		for (Component component : root.getComponents()) {
			if (type.isInstance(component)) result.add((T) component);
			else if (component instanceof Container) result.addAll(descendants((Container) component, type));
		}
		return result;
	}

	private static void layout(Container container) {
		container.doLayout();
		for (Component child : container.getComponents()) if (child instanceof Container) layout((Container) child);
	}

	private static void render(MeasureChartsPanel panel, File output) {
		panel.setSize(850, 240);
		layout(panel);
		BufferedImage image = new BufferedImage(850, 240, BufferedImage.TYPE_INT_RGB);
		Graphics2D graphics = image.createGraphics();
		panel.printAll(graphics);
		graphics.dispose();
		try {
			output.getParentFile().mkdirs();
			ImageIO.write(image, "png", output);
		} catch (java.io.IOException e) { throw new AssertionError(e); }
	}
}
