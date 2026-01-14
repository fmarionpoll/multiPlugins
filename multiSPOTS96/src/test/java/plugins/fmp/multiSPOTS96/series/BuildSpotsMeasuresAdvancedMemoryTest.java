package plugins.fmp.multiSPOTS96.series;

import plugins.fmp.multiSPOTS96.experiment.Experiment;

/**
 * Test class demonstrating memory-optimized usage of BuildSpotsMeasuresAdvanced.
 * This class shows how to use the ultra-conservative memory settings to minimize
 * memory consumption during spot measurement processing.
 * 
 * @author MultiSPOTS96
 * @version 2.3.3
 */
public class BuildSpotsMeasuresAdvancedMemoryTest {

    /**
     * Demonstrates how to use BuildSpotsMeasuresAdvanced with ultra-conservative
     * memory settings to minimize memory usage.
     * 
     * @param experiment the experiment to analyze
     * @return true if processing completed successfully
     */
    public static boolean processExperimentWithMinimalMemory(Experiment experiment) {
        System.out.println("=== Starting Memory-Optimized Processing ===");
        
        // Create ultra-conservative memory options
        AdvancedMemoryOptions ultraConservativeOptions = AdvancedMemoryOptions.createUltraConservative();
        
        // Log the configuration
        System.out.println("Using ultra-conservative memory configuration:");
        System.out.println(ultraConservativeOptions.getConfigurationSummary());
        
        // Create processor with ultra-conservative settings
        BuildSpotsMeasuresAdvanced processor = new BuildSpotsMeasuresAdvanced(ultraConservativeOptions);
        
        // Process the experiment
        try {
            processor.analyzeExperiment(experiment);
            System.out.println("Processing completed successfully with minimal memory usage");
            return true;
        } catch (OutOfMemoryError e) {
            System.err.println("OutOfMemoryError occurred despite ultra-conservative settings:");
            System.err.println("  - Available memory: " + getAvailableMemoryMB() + "MB");
            System.err.println("  - Max memory: " + getMaxMemoryMB() + "MB");
            System.err.println("  - Current usage: " + getUsedMemoryMB() + "MB");
            return false;
        } catch (Exception e) {
            System.err.println("Error during processing: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Demonstrates how to use BuildSpotsMeasuresAdvanced with conservative
     * memory settings for balanced performance and memory usage.
     * 
     * @param experiment the experiment to analyze
     * @return true if processing completed successfully
     */
    public static boolean processExperimentWithConservativeMemory(Experiment experiment) {
        System.out.println("=== Starting Conservative Memory Processing ===");
        
        // Create conservative memory options
        AdvancedMemoryOptions conservativeOptions = AdvancedMemoryOptions.createConservative();
        
        // Log the configuration
        System.out.println("Using conservative memory configuration:");
        System.out.println(conservativeOptions.getConfigurationSummary());
        
        // Create processor with conservative settings
        BuildSpotsMeasuresAdvanced processor = new BuildSpotsMeasuresAdvanced(conservativeOptions);
        
        // Process the experiment
        try {
            processor.analyzeExperiment(experiment);
            System.out.println("Processing completed successfully with conservative memory usage");
            return true;
        } catch (OutOfMemoryError e) {
            System.err.println("OutOfMemoryError occurred with conservative settings:");
            System.err.println("  - Available memory: " + getAvailableMemoryMB() + "MB");
            System.err.println("  - Max memory: " + getMaxMemoryMB() + "MB");
            System.err.println("  - Current usage: " + getUsedMemoryMB() + "MB");
            return false;
        } catch (Exception e) {
            System.err.println("Error during processing: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Demonstrates how to use BuildSpotsMeasuresAdvanced with balanced
     * memory settings for optimal performance and memory usage.
     * 
     * @param experiment the experiment to analyze
     * @return true if processing completed successfully
     */
    public static boolean processExperimentWithBalancedMemory(Experiment experiment) {
        System.out.println("=== Starting Balanced Memory Processing ===");
        
        // Create balanced memory options
        AdvancedMemoryOptions balancedOptions = AdvancedMemoryOptions.createBalanced();
        
        // Log the configuration
        System.out.println("Using balanced memory configuration:");
        System.out.println(balancedOptions.getConfigurationSummary());
        
        // Create processor with balanced settings
        BuildSpotsMeasuresAdvanced processor = new BuildSpotsMeasuresAdvanced(balancedOptions);
        
        // Process the experiment
        try {
            processor.analyzeExperiment(experiment);
            System.out.println("Processing completed successfully with balanced memory usage");
            return true;
        } catch (OutOfMemoryError e) {
            System.err.println("OutOfMemoryError occurred with balanced settings:");
            System.err.println("  - Available memory: " + getAvailableMemoryMB() + "MB");
            System.err.println("  - Max memory: " + getMaxMemoryMB() + "MB");
            System.err.println("  - Current usage: " + getUsedMemoryMB() + "MB");
            return false;
        } catch (Exception e) {
            System.err.println("Error during processing: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Demonstrates progressive memory optimization - starts with ultra-conservative
     * and gradually increases settings if memory allows.
     * 
     * @param experiment the experiment to analyze
     * @return true if processing completed successfully
     */
    public static boolean processExperimentWithProgressiveOptimization(Experiment experiment) {
        System.out.println("=== Starting Progressive Memory Optimization ===");
        
        // Try ultra-conservative first
        System.out.println("Attempting with ultra-conservative settings...");
        if (processExperimentWithMinimalMemory(experiment)) {
            System.out.println("Ultra-conservative processing successful!");
            return true;
        }
        
        // If that fails, try conservative
        System.out.println("Ultra-conservative failed, trying conservative settings...");
        if (processExperimentWithConservativeMemory(experiment)) {
            System.out.println("Conservative processing successful!");
            return true;
        }
        
        // If that fails, try balanced
        System.out.println("Conservative failed, trying balanced settings...");
        if (processExperimentWithBalancedMemory(experiment)) {
            System.out.println("Balanced processing successful!");
            return true;
        }
        
        System.err.println("All memory configurations failed. Consider:");
        System.err.println("  1. Increasing JVM heap size");
        System.err.println("  2. Processing smaller batches");
        System.err.println("  3. Using a machine with more RAM");
        return false;
    }
    
    // Memory utility methods
    private static long getAvailableMemoryMB() {
        Runtime runtime = Runtime.getRuntime();
        return (runtime.maxMemory() - runtime.totalMemory() + runtime.freeMemory()) / 1024 / 1024;
    }
    
    private static long getMaxMemoryMB() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.maxMemory() / 1024 / 1024;
    }
    
    private static long getUsedMemoryMB() {
        Runtime runtime = Runtime.getRuntime();
        return (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024;
    }
    
    /**
     * Main method for testing memory optimization.
     * 
     * @param args command line arguments (not used)
     */
    public static void main(String[] args) {
        System.out.println("BuildSpotsMeasuresAdvanced Memory Optimization Test");
        System.out.println("==================================================");
        System.out.println("Available memory: " + getAvailableMemoryMB() + "MB");
        System.out.println("Max memory: " + getMaxMemoryMB() + "MB");
        System.out.println("Current usage: " + getUsedMemoryMB() + "MB");
        System.out.println();
        
        // This would be called with an actual experiment
        // Example usage:
        // Experiment experiment = loadExperiment();
        // boolean success = processExperimentWithProgressiveOptimization(experiment);
        // System.out.println("Processing result: " + success);
    }
} 