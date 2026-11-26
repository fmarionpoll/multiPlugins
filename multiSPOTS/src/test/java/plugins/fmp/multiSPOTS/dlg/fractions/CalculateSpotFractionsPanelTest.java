package plugins.fmp.multiSPOTS.dlg.fractions;

import static org.junit.Assert.*;

import java.awt.Component;
import java.awt.Container;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

import org.jfree.data.xy.XYSeriesCollection;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import plugins.fmp.multiSPOTS.dlg.fractions._DlgCalculateFractions_.Step;
import plugins.fmp.multiSPOTS.dlg.fractions._DlgCalculateFractions_.Target;
import plugins.fmp.multiSPOTS.dlg.fractions._DlgCalculateFractions_.UnsavedChoice;
import plugins.fmp.multitools.experiment.spot.Spot;
import plugins.fmp.multitools.experiment.spot.fraction.SpotFractionCalculator;
import plugins.fmp.multitools.experiment.spot.fraction.SpotFractionCalculator.Input;
import plugins.fmp.multitools.experiment.spot.fraction.SpotFractionCalculator.Row;
import plugins.fmp.multitools.experiment.spot.fraction.SpotFractionPlan;
import plugins.fmp.multitools.experiment.spot.fraction.SpotFractionPlan.Illumination;
import plugins.fmp.multitools.experiment.spot.fraction.SpotFractionPlan.Key;
import plugins.fmp.multitools.experiment.spot.fraction.SpotFractionPlan.Reference;
import plugins.fmp.multitools.experiment.spot.fraction.SpotFractionPlan.ReferenceEstimation;
import plugins.fmp.multitools.experiment.spot.fraction.SpotFractionPlan.ReferenceKind;
import plugins.fmp.multitools.experiment.spot.fraction.SpotFractionPlan.Review;
import plugins.fmp.multitools.experiment.spot.fraction.SpotFractionPlan.Settings;
import plugins.fmp.multitools.experiment.spot.fraction.SpotFractionPlan.Tolerance;
import plugins.fmp.multitools.experiment.spot.fraction.SpotFractionStore;
import plugins.fmp.multitools.experiment.spot.measurement.SpotMeasuredQuantity;
import plugins.fmp.multitools.experiment.spot.measurement.SpotMeasurementCsv;
import plugins.fmp.multitools.experiment.spot.measurement.SpotMeasurementMethod;
import plugins.fmp.multitools.experiment.spot.measurement.SpotMethodResult;
import plugins.fmp.multitools.experiment.spot.measurement.SpotProcessingSettings.GapPolicy;
import plugins.fmp.multitools.experiment.spot.measurement.SpotProcessingSettings.TailPolicy;
import plugins.fmp.multitools.experiment.spot.measurement.SpotSampleFlags;

public class CalculateSpotFractionsPanelTest {
	private static final Key KEY = new Key(SpotMeasurementMethod.FILTER, "signal", "");
	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	private static Spot spot(String name, int cage, boolean consumed, double[] values, int... flags) {
		Spot spot = new Spot();
		spot.getProperties().setName(name);
		spot.getProperties().setCageID(cage);
		spot.setConsumedBeforeRecording(consumed);
		double[] times = new double[values.length];
		double[] fly = new double[values.length];
		for (int i = 0; i < times.length; i++) {
			times[i] = i;
			fly[i] = flags.length > 0 && flags[i] != 0 ? 1 : 0;
		}
		int[] sampleFlags = flags.length == 0 ? new int[values.length] : flags;
		spot.getMethodMeasurements().put(new SpotMethodResult(KEY.method, times, fly, "fraction", Collections.emptyMap(),
				Collections.singletonList(new SpotMeasuredQuantity("signal", "intensity", values, sampleFlags))));
		return spot;
	}

	private static List<Input> inputs(Spot... spots) {
		List<Input> inputs = new java.util.ArrayList<>();
		for (Spot spot : spots)
			inputs.add(new Input(spot, KEY.method));
		return inputs;
	}

	@Test
	public void workspaceUsesThreeStepsChartsAndExplicitSaveLabels() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			_DlgCalculateFractions_ panel = new _DlgCalculateFractions_();
			Set<String> buttons = new HashSet<>();
			Set<String> tabs = new HashSet<>();
			StringBuilder copy = new StringBuilder();
			collect(panel, buttons, tabs, copy);
			assertTrue(buttons.containsAll(Arrays.asList("Preview calculation", "Restore saved fraction settings and references",
					"Save fraction settings and references", "1. Find hmax - initial reference", "2. Handle occlusions",
					"3. Edit/view fractions", "Back to the three steps", "Apply & save range", "Saved review list",
					"Stop after current experiment", "Approve reference", "Borrow initial reference", "Exclude failed deposit")));
			assertFalse(buttons.contains("Load saved"));
			assertFalse(buttons.contains("Preview current"));
			assertFalse(buttons.contains("Calculate & save current"));
			assertTrue(tabs.containsAll(Arrays.asList("Calculate fractions", "Charts", "Load/save", "Individual fractions",
					"Aggregated fractions", "Correction diagnostics")));
			assertTrue(copy.toString().contains("SpotsFractionMeasurements.csv"));
			assertTrue(copy.toString().contains("not in this workspace yet"));
			assertEquals(Illumination.NONE, panel.controls().illumination);
			assertEquals(TailPolicy.KEEP_MISSING, panel.controls().tail);
			assertFalse(panel.spotOverride.isSelected());
			assertEquals(Tolerance.PERCENT_REFERENCE, panel.controls().toleranceMode);
			panel.method.setSelectedItem(SpotMeasurementMethod.VERTICAL_LINE);
			assertEquals(2, ((javax.swing.DefaultComboBoxModel<String>) panel.quantity.getModel()).getIndexOf("color_integral"));
			assertEquals(ReferenceEstimation.INITIAL_OBSERVATION, panel.controls().referenceEstimation);
			assertFalse(panel.baselineStart.isEnabled());
			panel.toleranceMode.setSelectedItem(Tolerance.ROBUST_NOISE);
			assertFalse(panel.tolerance.isEnabled());
			assertTrue(panel.tolerance.getToolTipText().toLowerCase(java.util.Locale.ROOT).contains("disabled"));
			panel.referenceEstimation.setSelectedItem(ReferenceEstimation.BASELINE_MEDIAN);
			assertTrue(panel.baselineStart.isEnabled());
			panel.baselineStart.setValue(5.0);
			panel.baselineEnd.setValue(15.0);
			assertEquals(300, panel.controls().baselineStartSeconds, 0);
			assertEquals(900, panel.controls().baselineEndSeconds, 0);
			assertEquals(2, panel.view.getItemCount());
			assertEquals(SpotFractionChart.View.FRACTION_REMAINING, panel.view.getItemAt(0));
			assertEquals(SpotFractionChart.View.FRACTION_CONSUMED, panel.view.getItemAt(1));
			assertEquals(2, panel.diagnosticView.getItemCount());
			assertEquals(4, panel.aggregateMeasure.getItemCount());
			panel.showStep(Step.OCCLUSION);
			panel.mainTabs.setSelectedIndex(1);
			panel.mainTabs.setSelectedIndex(0);
			panel.showStep(Step.REFERENCE);
			assertEquals(Step.REFERENCE, panel.testingStep());
			panel.showStep(Step.OVERVIEW);
			assertEquals(Step.OVERVIEW, panel.testingStep());
		});
	}

	@Test
	public void navigationKeepsTheSpotDraftAndDoesNotReloadDisk() throws Exception {
		Path root = folder.newFolder("nav").toPath();
		byte[] primary = primary(root, SpotMeasurementMethod.FILTER, 100, 80);
		SwingUtilities.invokeAndWait(() -> {
			_DlgCalculateFractions_ panel = workspace(root, "bin_09", inputs(spot("A", 1, false, new double[] { 100, 80 }),
					spot("B", 1, false, new double[] { 100, 70 })));
			click(panel, "Preview calculation");
			assertEquals("A", panel.spotPicker.getSelectedItem());
			panel.referenceValue.setText("123");
			panel.showStep(Step.OCCLUSION);
			panel.mainTabs.setSelectedIndex(1);
			panel.mainTabs.setSelectedIndex(2);
			panel.mainTabs.setSelectedIndex(0);
			panel.showStep(Step.REFERENCE);
			assertEquals("123", panel.referenceValue.getText());
			assertEquals(100, panel.testingPlan().references.get(KEY.forSpot("A")).value, 0);
			assertEquals("A", panel.spotPicker.getSelectedItem());
			panel.spotPicker.setSelectedItem("B");
			panel.spotPicker.setSelectedItem("A");
			assertEquals("123", panel.referenceValue.getText());
			assertEquals(SpotMeasurementMethod.FILTER, panel.method.getSelectedItem());
			assertTrue(panel.testingUnsaved());
		});
		assertArrayEquals(primary, Files.readAllBytes(root.resolve(SpotMeasurementMethod.FILTER.fileName())));
	}

	@Test
	public void approvalPreviewSaveAndReloadPreserveTheSameReference() throws Exception {
		Path root = folder.newFolder("save").toPath();
		byte[] primary = primary(root, SpotMeasurementMethod.FILTER, 900, 80, 40);
		SwingUtilities.invokeAndWait(() -> {
			_DlgCalculateFractions_ panel = workspace(root, "bin_20",
					inputs(spot("A", 1, false, new double[] { 900, 80, 40 }, SpotSampleFlags.OCCLUDED, 0, 0)));
			click(panel, "Preview calculation");
			assertEquals(ReferenceKind.DELAYED, panel.testingRow("A").reference.kind);
			assertFalse(panel.testingRow("A").reference.approved);
			click(panel, "Approve reference");
			Reference approved = panel.testingPlan().references.get(KEY.forSpot("A"));
			assertEquals(ReferenceKind.DELAYED, approved.kind);
			assertTrue(approved.approved);
			assertEquals(80, approved.value, 0);
			assertEquals(1, approved.timeSeconds, 0);
			click(panel, "Save fraction settings and references");
			assertFalse(panel.testingUnsaved());
			panel.referenceSource.setText("typed but not approved");
			assertTrue(panel.testingUnsaved());
			Properties broken = new Properties();
			broken.setProperty("version", "999");
			try (java.io.OutputStream out = Files.newOutputStream(root.resolve(SpotFractionStore.FILENAME))) {
				broken.storeToXML(out, "Future file");
			} catch (java.io.IOException ex) {
				throw new RuntimeException(ex);
			}
			click(panel, "Save fraction settings and references");
			assertTrue(panel.testingUnsaved());
			assertEquals(80, panel.testingPlan().references.get(KEY.forSpot("A")).value, 0);
			assertEquals(ReferenceKind.DELAYED, panel.testingPlan().references.get(KEY.forSpot("A")).kind);
			panel.unsavedChoiceForTest = UnsavedChoice.SAVE;
			assertFalse(panel.attemptClose());
			assertEquals(80, panel.testingPlan().references.get(KEY.forSpot("A")).value, 0);
		});
		assertArrayEquals(primary, Files.readAllBytes(root.resolve(SpotMeasurementMethod.FILTER.fileName())));
		assertFalse(Files.exists(root.resolve("SpotsFractionMeasurements.csv")));
		Properties onDisk = new Properties();
		try (java.io.InputStream in = Files.newInputStream(root.resolve(SpotFractionStore.FILENAME))) {
			onDisk.loadFromXML(in);
		}
		assertEquals("999", onDisk.getProperty("version"));
	}

	@Test
	public void successfulSaveReloadsTheApprovedReference() throws Exception {
		Path root = folder.newFolder("reload").toPath();
		SwingUtilities.invokeAndWait(() -> {
			_DlgCalculateFractions_ panel = workspace(root, "bin_20",
					inputs(spot("A", 1, false, new double[] { 900, 80, 40 }, SpotSampleFlags.OCCLUDED, 0, 0)));
			click(panel, "Preview calculation");
			click(panel, "Approve reference");
			click(panel, "Save fraction settings and references");
			panel.unsavedChoiceForTest = UnsavedChoice.DISCARD;
			click(panel, "Restore saved fraction settings and references");
			Reference restored = panel.testingPlan().references.get(KEY.forSpot("A"));
			assertEquals(ReferenceKind.DELAYED, restored.kind);
			assertTrue(restored.approved);
			assertEquals(80, restored.value, 0);
			assertEquals(1, restored.timeSeconds, 0);
			assertFalse(panel.testingUnsaved());
		});
		Reference stored = SpotFractionStore.read(root).references.get(KEY.forSpot("A"));
		assertEquals(80, stored.value, 0);
		assertEquals(ReferenceKind.DELAYED, stored.kind);
		assertTrue(stored.approved);
	}

	@Test
	public void discardAndCancelKeepTheStateTheChoiceNames() throws Exception {
		Path root = folder.newFolder("choice").toPath();
		SwingUtilities.invokeAndWait(() -> {
			_DlgCalculateFractions_ panel = workspace(root, "",
					inputs(spot("A", 1, false, new double[] { 900, 80 }, SpotSampleFlags.OCCLUDED, 0)));
			click(panel, "Preview calculation");
			click(panel, "Approve reference");
			panel.unsavedChoiceForTest = UnsavedChoice.CANCEL;
			panel.method.setSelectedItem(SpotMeasurementMethod.COLOR);
			assertEquals(SpotMeasurementMethod.FILTER, panel.method.getSelectedItem());
			assertEquals(80, panel.testingPlan().references.get(KEY.forSpot("A")).value, 0);
			assertTrue(panel.testingPlan().references.get(KEY.forSpot("A")).approved);
			panel.unsavedChoiceForTest = UnsavedChoice.CANCEL;
			assertFalse(panel.attemptClose());
			assertNotNull(panel.testingPlan());
			panel.unsavedChoiceForTest = UnsavedChoice.DISCARD;
			panel.method.setSelectedItem(SpotMeasurementMethod.COLOR);
			assertEquals(SpotMeasurementMethod.COLOR, panel.method.getSelectedItem());
			assertNull(panel.testingPlan());
			assertFalse(panel.testingUnsaved());
		});
	}

	@Test
	public void occlusionControlsUpdateExperimentOrSpotSettings() throws Exception {
		Path root = folder.newFolder("gaps").toPath();
		SwingUtilities.invokeAndWait(() -> {
			_DlgCalculateFractions_ panel = workspace(root, "bin_20", inputs(
					spot("A", 1, false, new double[] { 100, 900, 100 }, 0, SpotSampleFlags.OCCLUDED, 0),
					spot("B", 1, false, new double[] { 100, 900, 100 }, 0, SpotSampleFlags.OCCLUDED, 0)));
			click(panel, "Preview calculation");
			assertTrue(Double.isFinite(panel.testingRow("A").remaining()[1]));
			assertTrue(Double.isFinite(panel.testingRow("B").remaining()[1]));
			panel.spotOverride.setSelected(true);
			panel.similar.setSelectedItem(GapPolicy.NONE);
			panel.changing.setSelectedItem(GapPolicy.NONE);
			click(panel, "Preview calculation");
			assertEquals(GapPolicy.NONE, panel.testingPlan().settings.get(KEY.forSpot("A")).similarGap);
			assertEquals(GapPolicy.LINEAR, panel.testingPlan().settings.get(KEY).similarGap);
			assertTrue(Double.isNaN(panel.testingRow("A").remaining()[1]));
			assertTrue(Double.isFinite(panel.testingRow("B").remaining()[1]));
		});
	}

	@Test
	public void chartSelectionUsesTheRawObservation() throws Exception {
		Path root = folder.newFolder("chart").toPath();
		SwingUtilities.invokeAndWait(() -> {
			_DlgCalculateFractions_ panel = workspace(root, "bin_20",
					inputs(spot("A", 1, false, new double[] { 100, 900, 50 }, 0, SpotSampleFlags.OCCLUDED, 0)));
			click(panel, "Preview calculation");
			Row row = panel.testingRow("A");
			assertEquals(1, row.remaining()[0], 0);
			assertFalse(panel.fillReferenceFromSample(row, 1));
			assertEquals(Double.toString(row.reference.value), panel.referenceValue.getText());
			assertTrue(panel.fillReferenceFromSample(row, 2));
			assertEquals("50.0", panel.referenceValue.getText());
			assertEquals(Double.toString(row.input.result.getTimesSeconds()[2]), panel.referenceTime.getText());
			assertFalse(panel.referenceValue.getText().equals(Double.toString(row.remaining()[2])));
			click(panel, "Open Find hmax - initial reference");
			assertEquals(Step.REFERENCE, panel.testingStep());
			assertEquals(0, panel.mainTabs.getSelectedIndex());
		});
	}

	@Test
	public void imageMethodsIgnoreTheKymographBin() throws Exception {
		Path root = folder.newFolder("dirs").toPath();
		Path bin = folder.newFolder("dirs", "bin_20").toPath();
		SwingUtilities.invokeAndWait(() -> {
			_DlgCalculateFractions_ panel = new _DlgCalculateFractions_();
			panel.useTestingTarget(root, "bin_20", null, SpotMeasurementMethod.FILTER, "signal");
			assertEquals(root, panel.target(0, SpotMeasurementMethod.FILTER).measures);
			assertEquals(root, panel.target(0, SpotMeasurementMethod.COLOR).measures);
			assertEquals(root, panel.target(0, SpotMeasurementMethod.X_LINES).measures);
			assertEquals(root, panel.target(0, SpotMeasurementMethod.RIM_LINES).measures);
			assertEquals(bin, panel.target(0, SpotMeasurementMethod.VERTICAL_LINE).measures);
		});
		assertEquals(root, FractionSession.measurementsDirectory(root, SpotMeasurementMethod.X_LINES, "bin_20"));
		assertEquals(root, FractionSession.measurementsDirectory(root, SpotMeasurementMethod.RIM_LINES, "bin_60"));
		assertEquals(bin, FractionSession.measurementsDirectory(root, SpotMeasurementMethod.VERTICAL_LINE, "bin_20"));
	}

	@Test
	public void batchKeepsSpotDecisionsAndStopLeavesTheRest() throws Exception {
		Path first = folder.newFolder("batch-a").toPath();
		Path second = folder.newFolder("batch-b").toPath();
		writeSignal(first, "A", new double[] { 100, 80 });
		writeSignal(second, "A", new double[] { 100, 80 });
		byte[] primaryBefore = Files.readAllBytes(first.resolve(SpotMeasurementMethod.FILTER.fileName()));
		SpotFractionPlan original = new SpotFractionPlan();
		original.settings.put(KEY, Settings.DEFAULT);
		original.settings.put(KEY.forSpot("A"), new Settings(GapPolicy.HOLD_PREVIOUS, GapPolicy.HOLD_PREVIOUS,
				TailPolicy.KEEP_MISSING, 600, Tolerance.PERCENT_REFERENCE, 8, 0, Illumination.NONE));
		original.references.put(KEY.forSpot("A"), new Reference(ReferenceKind.EXPLICIT, 100, 0, "External reference", true,
				"fingerprint", Collections.emptyList()));
		SpotFractionStore.write(first, original);
		SpotFractionPlan untouched = new SpotFractionPlan();
		untouched.settings.put(KEY, new Settings(GapPolicy.LINEAR, GapPolicy.LINEAR, TailPolicy.KEEP_MISSING, 600,
				Tolerance.ABSOLUTE, 42, 0, Illumination.NONE));
		SpotFractionStore.write(second, untouched);
		_DlgCalculateFractions_ panel = new _DlgCalculateFractions_();
		Settings chosen = new Settings(GapPolicy.NONE, GapPolicy.NONE, TailPolicy.REVIEW, 120, Tolerance.ABSOLUTE, 3, 0,
				Illumination.NONE);
		int[] checks = { 0 };
		String summary = panel.executeBatch(
				Arrays.asList(new Target(0, "1: a", first, first, null), new Target(1, "2: b", second, second, null)),
				KEY, chosen, () -> checks[0]++ > 0);
		assertTrue(summary.startsWith("Stopped"));
		SpotFractionPlan saved = SpotFractionStore.read(first);
		assertEquals(GapPolicy.NONE, saved.settings.get(KEY).similarGap);
		assertEquals(TailPolicy.REVIEW, saved.settings.get(KEY).tail);
		assertEquals(GapPolicy.HOLD_PREVIOUS, saved.settings.get(KEY.forSpot("A")).similarGap);
		assertEquals(100, saved.references.get(KEY.forSpot("A")).value, 0);
		assertEquals(ReferenceKind.EXPLICIT, saved.references.get(KEY.forSpot("A")).kind);
		assertEquals(42, SpotFractionStore.read(second).settings.get(KEY).tolerance, 0);
		assertFalse(Files.exists(first.resolve("SpotsFractionMeasurements.csv")));
		assertArrayEquals(primaryBefore, Files.readAllBytes(first.resolve(SpotMeasurementMethod.FILTER.fileName())));
	}

	@Test
	public void reviewListSkipsImageBinsAndOpensTheSavedSpot() throws Exception {
		Path root = folder.newFolder("reviews").toPath();
		Path bin = folder.newFolder("reviews", "bin_20").toPath();
		SpotFractionPlan saved = new SpotFractionPlan();
		saved.review.add(new Review(KEY.forSpot("A"), "Initial reference is delayed or provisional; review required"));
		SpotFractionStore.write(root, saved);
		SpotFractionPlan decoy = new SpotFractionPlan();
		decoy.review.add(new Review(KEY.forSpot("Z"), "bin-only review"));
		SpotFractionStore.write(bin, decoy);
		SwingUtilities.invokeAndWait(() -> {
			_DlgCalculateFractions_ panel = workspace(root, "bin_20",
					inputs(spot("A", 1, false, new double[] { 900, 80 }, SpotSampleFlags.OCCLUDED, 0)));
			List<_DlgCalculateFractions_.SavedReview> imageOnly = panel.collectReviews(
					Collections.singletonList(panel.target(0, SpotMeasurementMethod.FILTER)), new StringBuilder());
			assertEquals(1, imageOnly.size());
			assertEquals("A", imageOnly.get(0).review.key.spot);
			List<_DlgCalculateFractions_.SavedReview> vertical = panel.collectReviews(
					Collections.singletonList(panel.target(0, SpotMeasurementMethod.VERTICAL_LINE)), new StringBuilder());
			assertEquals(2, vertical.size());
			click(panel, "Saved review list");
			panel.reviews.setRowSelectionInterval(0, 0);
			click(panel, "Open selected review item");
			assertEquals(Step.REVIEW, panel.testingStep());
			assertEquals(0, panel.mainTabs.getSelectedIndex());
			assertEquals("A", panel.spotPicker.getSelectedItem());
			assertEquals(SpotMeasurementMethod.FILTER, panel.method.getSelectedItem());
			assertEquals("signal", panel.quantity.getSelectedItem());
		});
	}

	@Test
	public void chartNeverClampsNegativeConsumptionAndMarksFilledSegmentsSeparately() {
		Row row = row(new double[] { 100, 900, 50, 120 }, new int[] { 0, 1, 0, 0 }, Settings.DEFAULT);
		XYSeriesCollection data = SpotFractionChart.data(row, SpotFractionChart.View.FRACTION_CONSUMED, false);
		assertNull(data.getY(0, 1));
		assertEquals(.25, data.getYValue(1, 1), 1e-12);
		assertEquals(-.2, data.getYValue(0, 3), 1e-12);
		assertEquals(1, data.getXValue(1, 1), 0);
		assertEquals(SpotSampleFlags.OCCLUDED, row.fractions.getObservationFlags()[1]);
	}

	@Test
	public void optionalVisualConnectionDoesNotChangeUnderlyingMissingValues() {
		Settings settings = new Settings(GapPolicy.NONE, GapPolicy.NONE, TailPolicy.KEEP_MISSING, 600,
				Tolerance.PERCENT_REFERENCE, 8, 0, Illumination.NONE);
		Row row = row(new double[] { 100, 900, 100 }, new int[] { 0, 1, 0 }, settings);
		XYSeriesCollection connected = SpotFractionChart.data(row, SpotFractionChart.View.FRACTION_CONSUMED, true);
		assertEquals(0, connected.getYValue(1, 1), 0);
		assertNull(connected.getY(0, 1));
		assertTrue(Double.isNaN(row.remaining()[1]));
		assertFalse(row.fractions.getEstimatedSamples()[1]);
		assertNull(SpotFractionChart.data(row, SpotFractionChart.View.FRACTION_CONSUMED, false).getY(1, 1));
	}

	@Test
	public void savingOneSelectionRetainsOtherMethodDecisionsAndSupportsRemovingAnOverride() {
		SpotFractionPlan saved = new SpotFractionPlan();
		SpotFractionPlan preview = new SpotFractionPlan();
		Key other = new Key(SpotMeasurementMethod.COLOR, "grey_signal", "");
		saved.settings.put(other, Settings.DEFAULT);
		saved.settings.put(keyForSpot(), Settings.DEFAULT);
		saved.review.add(new Review(other.forSpot("B"), "Other method still needs review"));
		preview.settings.put(KEY, Settings.DEFAULT);
		preview.review.add(new Review(KEY.forSpot("A"), "Initial occlusion"));
		SpotFractionPlan merged = _DlgCalculateFractions_.mergeSelection(saved, preview, KEY);
		assertTrue(merged.settings.containsKey(other));
		assertFalse(merged.settings.containsKey(keyForSpot()));
		assertEquals(2, merged.review.size());
	}

	@Test
	public void aggregatePlotsDoNotTreatVisualConnectionsAsCalculatedContributions() {
		Settings settings = new Settings(GapPolicy.NONE, GapPolicy.NONE, TailPolicy.KEEP_MISSING, 600,
				Tolerance.PERCENT_REFERENCE, 8, 0, Illumination.NONE);
		Row row = row(new double[] { 100, 900, 100 }, new int[] { 0, 1, 0 }, settings);
		plugins.fmp.multitools.experiment.spot.fraction.SpotFractionAggregation.Group group = plugins.fmp.multitools.experiment.spot.fraction.SpotFractionAggregation
				.build(Collections.singletonList(row.input), Collections.singletonList(row), KEY, new SpotFractionPlan()).get(0);
		XYSeriesCollection data = SpotFractionAggregateChart.data(group,
				plugins.fmp.multitools.experiment.spot.fraction.SpotFractionAggregation.Measure.SUM_CONSUMED);
		assertNull(data.getY(0, 1));
		assertNull(data.getY(1, 1));
		assertEquals(0, group.availableSpots()[1]);
	}

	private _DlgCalculateFractions_ workspace(Path root, String bin, List<Input> inputs) {
		_DlgCalculateFractions_ panel = new _DlgCalculateFractions_();
		panel.synchronousForTest = true;
		panel.quantity.addItem("signal");
		panel.quantity.setSelectedItem("signal");
		panel.useTestingTarget(root, bin, inputs, SpotMeasurementMethod.FILTER, "signal");
		return panel;
	}

	private static void click(_DlgCalculateFractions_ panel, String label) {
		JButton button = find(panel, label);
		assertNotNull(label, button);
		assertTrue(label + " disabled: " + button.getToolTipText(), button.isEnabled());
		button.doClick();
	}

	private static JButton find(Container container, String label) {
		for (Component child : container.getComponents()) {
			if (child instanceof JButton && label.equals(((JButton) child).getText()))
				return (JButton) child;
			if (child instanceof Container) {
				JButton nested = find((Container) child, label);
				if (nested != null)
					return nested;
			}
		}
		return null;
	}

	private static Key keyForSpot() {
		return KEY.forSpot("A");
	}

	private byte[] primary(Path directory, SpotMeasurementMethod method, double... values) throws Exception {
		writeSignal(directory, "A", values);
		return Files.readAllBytes(directory.resolve(method.fileName()));
	}

	private static void writeSignal(Path directory, String spot, double[] values) throws Exception {
		SpotMethodResult result = new SpotMethodResult(SpotMeasurementMethod.FILTER, times(values.length),
				new double[values.length], "fraction", Collections.emptyMap(), Collections.singletonList(
						new SpotMeasuredQuantity("signal", "intensity", values, new int[values.length])));
		SpotMeasurementCsv.write(directory.resolve(SpotMeasurementMethod.FILTER.fileName()),
				Collections.singletonMap(spot, Collections.singletonMap(SpotMeasurementMethod.FILTER, result)));
	}

	private static double[] times(int count) {
		double[] times = new double[count];
		for (int i = 0; i < count; i++)
			times[i] = i;
		return times;
	}

	private Row row(double[] raw, int[] flags, Settings settings) {
		Spot spot = new Spot();
		spot.getProperties().setName("A");
		spot.getProperties().setCageID(1);
		double[] times = new double[raw.length];
		double[] fly = new double[raw.length];
		for (int i = 0; i < times.length; i++) {
			times[i] = i * 60.0;
			fly[i] = flags[i] == 0 ? 0 : 1;
		}
		spot.getMethodMeasurements().put(new SpotMethodResult(KEY.method, times, fly, "fraction", Collections.emptyMap(),
				Collections.singletonList(new SpotMeasuredQuantity("signal", "intensity", raw, flags))));
		SpotFractionPlan plan = new SpotFractionPlan();
		plan.settings.put(KEY, settings);
		return SpotFractionCalculator.calculate(inputs(spot), KEY, plan).get(0);
	}

	private void collect(Container container, Set<String> buttons, Set<String> tabs, StringBuilder copy) {
		if (container instanceof JTabbedPane) {
			JTabbedPane pane = (JTabbedPane) container;
			for (int i = 0; i < pane.getTabCount(); i++)
				tabs.add(pane.getTitleAt(i));
		}
		if (container instanceof JComboBox)
			return;
		for (Component child : container.getComponents()) {
			if (child instanceof JButton)
				buttons.add(((JButton) child).getText());
			if (child instanceof JTextArea)
				copy.append(((JTextArea) child).getText()).append('\n');
			if (child instanceof Container)
				collect((Container) child, buttons, tabs, copy);
		}
	}
}
