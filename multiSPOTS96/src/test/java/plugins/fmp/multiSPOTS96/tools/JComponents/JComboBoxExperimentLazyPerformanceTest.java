package plugins.fmp.multiSPOTS96.tools.JComponents;

import java.util.ArrayList;
import java.util.List;

import plugins.fmp.multiSPOTS96.experiment.Experiment;
import plugins.fmp.multiSPOTS96.tools.LazyExperiment;
import plugins.fmp.multiSPOTS96.tools.LazyExperiment.ExperimentMetadata;

/**
 * Performance test for JComboBoxExperimentLazy to verify that bulk loading
 * doesn't trigger unnecessary experiment loading.
 * 
 * @author MultiSPOTS96
 */
public class JComboBoxExperimentLazyPerformanceTest {

	public static void main(String[] args) {
		System.out.println("Testing JComboBoxExperimentLazy performance...");

		// Create test data
		List<ExperimentMetadata> metadataList = new ArrayList<>();
		for (int i = 0; i < 227; i++) {
			ExperimentMetadata metadata = new ExperimentMetadata("/path/to/camera" + i, "/path/to/results" + i,
					"/path/to/bin");
			metadataList.add(metadata);
		}

		// Test 1: Using addLazyExperiment (old method)
		System.out.println("\nTest 1: Using addLazyExperiment (with duplicate checking)");
		JComboBoxExperimentLazy combo1 = new JComboBoxExperimentLazy();
		long startTime1 = System.currentTimeMillis();

		for (ExperimentMetadata metadata : metadataList) {
			LazyExperiment lazyExp = new LazyExperiment(metadata);
			combo1.addLazyExperiment(lazyExp);
		}

		long endTime1 = System.currentTimeMillis();
		System.out.println("Time taken: " + (endTime1 - startTime1) + "ms");
		System.out.println("Loaded experiments: " + combo1.getLoadedExperimentCount() + "/" + combo1.getItemCount());

		// Test 2: Using addLazyExperimentDirect (new method)
		System.out.println("\nTest 2: Using addLazyExperimentDirect (no duplicate checking)");
		JComboBoxExperimentLazy combo2 = new JComboBoxExperimentLazy();
		long startTime2 = System.currentTimeMillis();

		for (ExperimentMetadata metadata : metadataList) {
			LazyExperiment lazyExp = new LazyExperiment(metadata);
			combo2.addLazyExperiment(lazyExp);
		}

		long endTime2 = System.currentTimeMillis();
		System.out.println("Time taken: " + (endTime2 - startTime2) + "ms");
		System.out.println("Loaded experiments: " + combo2.getLoadedExperimentCount() + "/" + combo2.getItemCount());

		// Test 3: Using addItem directly (fastest)
		System.out.println("\nTest 3: Using addItem directly (fastest)");
		JComboBoxExperimentLazy combo3 = new JComboBoxExperimentLazy();
		long startTime3 = System.currentTimeMillis();

		for (ExperimentMetadata metadata : metadataList) {
			LazyExperiment lazyExp = new LazyExperiment(metadata);
			combo3.addItem(lazyExp);
		}

		long endTime3 = System.currentTimeMillis();
		System.out.println("Time taken: " + (endTime3 - startTime3) + "ms");
		System.out.println("Loaded experiments: " + combo3.getLoadedExperimentCount() + "/" + combo3.getItemCount());

		// Performance comparison
		System.out.println("\nPerformance Comparison:");
		System.out.println("Method 1 (addLazyExperiment): " + (endTime1 - startTime1) + "ms");
		System.out.println("Method 2 (addLazyExperimentDirect): " + (endTime2 - startTime2) + "ms");
		System.out.println("Method 3 (addItem): " + (endTime3 - startTime3) + "ms");

		// Verify that experiments are not loaded during bulk addition
		System.out.println("\nVerification:");
		System.out.println("All methods should show 0 loaded experiments initially");
		System.out.println("Method 1 loaded: " + combo1.getLoadedExperimentCount());
		System.out.println("Method 2 loaded: " + combo2.getLoadedExperimentCount());
		System.out.println("Method 3 loaded: " + combo3.getLoadedExperimentCount());

		// Test accessing an experiment (should trigger loading)
		System.out.println("\nTesting experiment access (should trigger loading)...");
		if (combo3.getItemCount() > 0) {
			Experiment exp = combo3.getItemAt(0);
			System.out.println("After accessing first experiment, loaded count: " + combo3.getLoadedExperimentCount());
		}
	}
}