package plugins.fmp.multiSPOTS96.tools.JComponents;

import java.util.ArrayList;
import java.util.List;

import plugins.fmp.multiSPOTS96.experiment.Experiment;

/**
 * Simple test class for JComboBoxExperimentLazy to verify functionality.
 * 
 * @author MultiSPOTS96
 * @version 1.0.0
 */
public class JComboBoxExperimentLazyTest {

	public static void main(String[] args) {
		JComboBoxExperimentLazyTest test = new JComboBoxExperimentLazyTest();
		test.runTests();
	}

	public void runTests() {
		System.out.println("=== JComboBoxExperimentLazy Test Suite ===");
		
		testBasicFunctionality();
		testMemoryEfficiency();
		testLazyLoading();
		testBackwardCompatibility();
		
		System.out.println("=== All Tests Completed ===");
	}

	/**
	 * Test basic functionality of the lazy combo box.
	 */
	private void testBasicFunctionality() {
		System.out.println("\n--- Test 1: Basic Functionality ---");
		
		JComboBoxExperimentLazy combo = new JComboBoxExperimentLazy();
		combo.stringExpBinSubDirectory = "/test/bin";
		
		// Test adding experiments
		List<Experiment> experiments = createTestExperiments(5);
		for (Experiment exp : experiments) {
			int index = combo.addExperiment(exp);
			System.out.println("Added experiment at index: " + index);
		}
		
		// Test item count
		int itemCount = combo.getItemCount();
		System.out.println("Total experiments: " + itemCount);
		assertTrue(itemCount == 5, "Expected 5 experiments, got " + itemCount);
		
		// Test getting items
		for (int i = 0; i < itemCount; i++) {
			Experiment exp = combo.getItemAt(i);
			System.out.println("Experiment " + i + ": " + exp.toString());
		}
		
		System.out.println("✓ Basic functionality test passed");
	}

	/**
	 * Test memory efficiency compared to regular combo box.
	 */
	private void testMemoryEfficiency() {
		System.out.println("\n--- Test 2: Memory Efficiency ---");
		
		JComboBoxExperimentLazy lazyCombo = new JComboBoxExperimentLazy();
		lazyCombo.stringExpBinSubDirectory = "/test/bin";
		
		// Add experiments
		List<Experiment> experiments = createTestExperiments(10);
		for (Experiment exp : experiments) {
			lazyCombo.addExperiment(exp);
		}
		
		// Check initial memory usage
		String initialMemory = lazyCombo.getMemoryUsageInfo();
		int initialLoaded = lazyCombo.getLoadedExperimentCount();
		System.out.println("Initial memory: " + initialMemory);
		System.out.println("Initial loaded experiments: " + initialLoaded + " / " + lazyCombo.getItemCount());
		
		// Load one experiment
		lazyCombo.getItemAt(0);
		String afterLoadMemory = lazyCombo.getMemoryUsageInfo();
		int afterLoadCount = lazyCombo.getLoadedExperimentCount();
		System.out.println("After loading one: " + afterLoadMemory);
		System.out.println("Loaded experiments: " + afterLoadCount + " / " + lazyCombo.getItemCount());
		
		assertTrue(afterLoadCount == 1, "Expected 1 loaded experiment, got " + afterLoadCount);
		
		System.out.println("✓ Memory efficiency test passed");
	}

	/**
	 * Test lazy loading behavior.
	 */
	private void testLazyLoading() {
		System.out.println("\n--- Test 3: Lazy Loading ---");
		
		JComboBoxExperimentLazy combo = new JComboBoxExperimentLazy();
		combo.stringExpBinSubDirectory = "/test/bin";
		
		// Add experiments
		List<Experiment> experiments = createTestExperiments(3);
		for (Experiment exp : experiments) {
			combo.addExperiment(exp);
		}
		
		// Test that experiments are not loaded initially
		int initialLoaded = combo.getLoadedExperimentCount();
		System.out.println("Initially loaded: " + initialLoaded + " / " + combo.getItemCount());
		assertTrue(initialLoaded == 0, "Expected 0 loaded experiments initially, got " + initialLoaded);
		
		// Test that accessing an experiment loads it
		Experiment firstExp = combo.getItemAt(0);
		int afterAccessLoaded = combo.getLoadedExperimentCount();
		System.out.println("After accessing first experiment: " + afterAccessLoaded + " / " + combo.getItemCount());
		assertTrue(afterAccessLoaded == 1, "Expected 1 loaded experiment after access, got " + afterAccessLoaded);
		
		// Test that accessing the same experiment doesn't load it again
		Experiment sameExp = combo.getItemAt(0);
		int afterSecondAccessLoaded = combo.getLoadedExperimentCount();
		System.out.println("After second access: " + afterSecondAccessLoaded + " / " + combo.getItemCount());
		assertTrue(afterSecondAccessLoaded == 1, "Expected still 1 loaded experiment, got " + afterSecondAccessLoaded);
		
		// Test that accessing a different experiment loads it
		Experiment secondExp = combo.getItemAt(1);
		int afterThirdAccessLoaded = combo.getLoadedExperimentCount();
		System.out.println("After accessing second experiment: " + afterThirdAccessLoaded + " / " + combo.getItemCount());
		assertTrue(afterThirdAccessLoaded == 2, "Expected 2 loaded experiments, got " + afterThirdAccessLoaded);
		
		System.out.println("✓ Lazy loading test passed");
	}

	/**
	 * Test backward compatibility with JComboBoxExperiment interface.
	 */
	private void testBackwardCompatibility() {
		System.out.println("\n--- Test 4: Backward Compatibility ---");
		
		JComboBoxExperimentLazy combo = new JComboBoxExperimentLazy();
		combo.stringExpBinSubDirectory = "/test/bin";
		
		// Test all methods that should work the same as JComboBoxExperiment
		List<Experiment> experiments = createTestExperiments(3);
		for (Experiment exp : experiments) {
			combo.addExperiment(exp);
		}
		
		// Test getExperimentIndexFromExptName
		int index = combo.getExperimentIndexFromExptName("test_experiment_1");
		System.out.println("Index of test_experiment_1: " + index);
		
		// Test getExperimentFromExptName
		Experiment foundExp = combo.getExperimentFromExptName("test_experiment_1");
		System.out.println("Found experiment: " + (foundExp != null ? foundExp.toString() : "null"));
		
		// Test getExperimentsAsList
		List<Experiment> expList = combo.getExperimentsAsList();
		System.out.println("Experiment list size: " + expList.size());
		assertTrue(expList.size() == 3, "Expected 3 experiments in list, got " + expList.size());
		
		// Test setExperimentsFromList
		combo.removeAllItems();
		combo.setExperimentsFromList(expList);
		System.out.println("After setExperimentsFromList: " + combo.getItemCount());
		assertTrue(combo.getItemCount() == 3, "Expected 3 experiments after setExperimentsFromList, got " + combo.getItemCount());
		
		System.out.println("✓ Backward compatibility test passed");
	}

	/**
	 * Creates a list of test experiments.
	 */
	private List<Experiment> createTestExperiments(int count) {
		List<Experiment> experiments = new ArrayList<>();
		for (int i = 1; i <= count; i++) {
			Experiment exp = new Experiment();
			exp.setResultsDirectory("/test/experiments/test_experiment_" + i);
			experiments.add(exp);
		}
		return experiments;
	}

	/**
	 * Simple assertion method for testing.
	 */
	private void assertTrue(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
} 