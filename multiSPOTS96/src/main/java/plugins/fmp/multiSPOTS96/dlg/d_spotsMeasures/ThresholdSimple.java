package plugins.fmp.multiSPOTS96.dlg.d_spotsMeasures;

import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.lang.ref.WeakReference;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JToggleButton;
import javax.swing.SpinnerNumberModel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import icy.gui.viewer.Viewer;
import icy.sequence.Sequence;
import icy.util.StringUtil;
import plugins.fmp.multiSPOTS96.MultiSPOTS96;
import plugins.fmp.multiSPOTS96.experiment.Experiment;
import plugins.fmp.multiSPOTS96.experiment.sequence.SequenceCamData;
import plugins.fmp.multiSPOTS96.series.AdvancedMemoryOptions;
import plugins.fmp.multiSPOTS96.series.BuildSeriesOptions;
import plugins.fmp.multiSPOTS96.series.BuildSpotsMeasuresAdvanced;
import plugins.fmp.multiSPOTS96.tools.canvas2D.Canvas2D_3Transforms;
import plugins.fmp.multiSPOTS96.tools.imageTransform.ImageTransformEnums;

public class ThresholdSimple extends JPanel implements PropertyChangeListener {
	/**
	 * 
	 */
	private static final long serialVersionUID = 8921207247623517524L;

	private String detectString = "Detect";
	private JButton detectButton = new JButton(detectString);
	private JComboBox<String> memUseComboBox = new JComboBox<String>(
			new String[] { "balanced", "conservative", "aggressive" });
	private JCheckBox allSeriesCheckBox = new JCheckBox("ALL (current to last)", false);

	private JLabel spotsFilterLabel = new JLabel("Spots filter");
	private String[] directions = new String[] { " threshold >", " threshold <" };
	private ImageTransformEnums[] transforms = new ImageTransformEnums[] { ImageTransformEnums.R_RGB,
			ImageTransformEnums.G_RGB, ImageTransformEnums.B_RGB, ImageTransformEnums.R2MINUS_GB,
			ImageTransformEnums.G2MINUS_RB, ImageTransformEnums.B2MINUS_RG, ImageTransformEnums.RGB,
			ImageTransformEnums.GBMINUS_2R, ImageTransformEnums.RBMINUS_2G, ImageTransformEnums.RGMINUS_2B,
			ImageTransformEnums.RGB_DIFFS, ImageTransformEnums.H_HSB, ImageTransformEnums.S_HSB,
			ImageTransformEnums.B_HSB };
	private JComboBox<ImageTransformEnums> spotsTransformsComboBox = new JComboBox<ImageTransformEnums>(transforms);
	private JComboBox<String> spotsDirectionComboBox = new JComboBox<String>(directions);
	private JSpinner spotsThresholdSpinner = new JSpinner(new SpinnerNumberModel(35, 0, 255, 1));
	private JToggleButton viewButton1 = new JToggleButton("View");
	private JToggleButton viewButton2 = new JToggleButton("View");

	private JLabel fliesFilterLabel = new JLabel("  Flies filter");
	private JComboBox<ImageTransformEnums> fliesTransformsComboBox = new JComboBox<ImageTransformEnums>(transforms);
	private JComboBox<String> fliesDirectionComboBox = new JComboBox<String>(directions);
	private JSpinner fliesThresholdSpinner = new JSpinner(new SpinnerNumberModel(50, 0, 255, 1));
	private boolean vocalAboutMemory = false;

	// === MEMORY LEAK PREVENTION ===
	// Use weak reference to avoid holding strong references to the processor
	private WeakReference<BuildSpotsMeasuresAdvanced> processorRef = null;
	private MultiSPOTS96 parent0 = null;

	public void init(GridLayout gridLayout, MultiSPOTS96 parent0) {
		setLayout(gridLayout);
		this.parent0 = parent0;
		FlowLayout layoutLeft = new FlowLayout(FlowLayout.LEFT);
		layoutLeft.setVgap(0);

		JPanel panel0 = new JPanel(layoutLeft);
		panel0.add(detectButton);
		panel0.add(new JLabel("memory use"));
		panel0.add(memUseComboBox);
		panel0.add(allSeriesCheckBox);
		add(panel0);

		JPanel panel1 = new JPanel(layoutLeft);
		panel1.add(spotsFilterLabel);
		panel1.add(spotsTransformsComboBox);
		panel1.add(spotsDirectionComboBox);
		panel1.add(spotsThresholdSpinner);
		panel1.add(viewButton1);
		add(panel1);

		JPanel panel2 = new JPanel(layoutLeft);
		panel2.add(fliesFilterLabel);
		panel2.add(fliesDirectionComboBox);
		panel2.add(fliesThresholdSpinner);
		panel2.add(viewButton2);
		add(panel2);

		spotsTransformsComboBox.setSelectedItem(ImageTransformEnums.RGB_DIFFS);
		spotsDirectionComboBox.setSelectedIndex(1);

		fliesTransformsComboBox.setSelectedItem(ImageTransformEnums.B_RGB);
		fliesDirectionComboBox.setSelectedIndex(0);
		declareListeners();
	}

	// === MEMORY MONITORING AND CLEANUP ===

	private void forceDialogReturnCleanup() {
//		System.out.println("=== FORCING DIALOG RETURN CLEANUP ===");

		// Force multiple GC passes
		for (int i = 0; i < 3; i++) {
			System.gc();
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}

		// Clear any dialog-specific caches
		clearDialogCaches();
	}

	private void clearDialogCaches() {
		// Clear any caches specific to the dialog
		// This will depend on what the dialog is caching
		System.out.println("Cleared dialog caches");
	}

	// Get processor using weak reference
	private BuildSpotsMeasuresAdvanced getProcessor() {
		if (processorRef != null) {
			return processorRef.get();
		}
		return null;
	}

	// Set processor using weak reference
	private void setProcessor(BuildSpotsMeasuresAdvanced processor) {
		this.processorRef = new WeakReference<>(processor);
	}

	private void declareListeners() {
		spotsTransformsComboBox.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp != null) {
//					if (!viewButton1.isSelected()) {
//						viewButton1.setSelected(true);
//					}

					int index = spotsTransformsComboBox.getSelectedIndex();
					updateCanvasFunctions(exp, index);
					updateOverlaysThreshold();
				}
			}
		});

		fliesTransformsComboBox.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp != null && viewButton2.isSelected()) {
					int index = fliesTransformsComboBox.getSelectedIndex();
					Canvas2D_3Transforms canvas = (Canvas2D_3Transforms) exp.seqCamData.getSequence().getFirstViewer()
							.getCanvas();
					updateTransformFunctions2OfCanvas(canvas);
					if (!viewButton2.isSelected())
						viewButton2.setSelected(true);
					canvas.setTransformStep1Index(index + 1);
					updateOverlaysThreshold();
				}
			}
		});

		spotsDirectionComboBox.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				updateOverlaysThreshold();
			}
		});

		fliesDirectionComboBox.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				updateOverlaysThreshold();
			}
		});

		spotsThresholdSpinner.addChangeListener(new ChangeListener() {
			public void stateChanged(ChangeEvent e) {
				updateOverlaysThreshold();
			}
		});

		fliesThresholdSpinner.addChangeListener(new ChangeListener() {
			public void stateChanged(ChangeEvent e) {
				updateOverlaysThreshold();
			}
		});

		viewButton1.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp != null) {
					displayTransform1(exp);
					displayOverlays(viewButton1.isSelected(), exp);
				}
			}
		});

		viewButton2.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp != null)
					displayTransform2(exp);
			}
		});

		detectButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				if (detectButton.getText().equals(detectString))
					startDetection();
				else
					stopDetection();
			}
		});
	}

	void updateOverlaysThreshold() {
		ImageTransformEnums transform = ImageTransformEnums.NONE;
		boolean ifGreater = true;
		int threshold = 0;

		if (viewButton1.isSelected()) {
			transform = (ImageTransformEnums) spotsTransformsComboBox.getSelectedItem();
			threshold = (int) spotsThresholdSpinner.getValue();
			ifGreater = (spotsDirectionComboBox.getSelectedIndex() == 0);
		} else {
			transform = (ImageTransformEnums) fliesTransformsComboBox.getSelectedItem();
			threshold = (int) fliesThresholdSpinner.getValue();
			ifGreater = (fliesDirectionComboBox.getSelectedIndex() == 0);
		}

		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		if (exp != null) {
			if (exp.seqCamData != null)
				exp.seqCamData.updateOverlayThreshold(threshold, transform, ifGreater);
//			if (exp.seqKymos != null)
//				exp.seqKymos.updateOverlayThreshold(threshold, transform, ifGreater);
		}
	}

	AdvancedMemoryOptions createMemoryOptionsAccordingToUserSelection() {
		String selected = (String) memUseComboBox.getSelectedItem();
		switch (selected) {
		case "conservative":
			return AdvancedMemoryOptions.createConservative();
		case "aggressive":
			return AdvancedMemoryOptions.createAggressive();
		case "balanced":
		default:
			return AdvancedMemoryOptions.createBalanced();
		}
	}

	void startDetection() {
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		if (exp != null) {
//			AdvancedMemoryOptions memOptions = new AdvancedMemoryOptions();
			AdvancedMemoryOptions memOptions = createMemoryOptionsAccordingToUserSelection();
			// Validate configuration
			AdvancedMemoryOptions.ValidationResult result = memOptions.validate();
			if (result.isValid()) {
				System.out.println("Custom configuration is valid");
				System.out.println(memOptions.getConfigurationSummary());
			} else {
				System.err.println("Configuration issues: " + result);
			}

			BuildSpotsMeasuresAdvanced processor = new BuildSpotsMeasuresAdvanced(memOptions);
			processor.options = initDetectOptions(exp);
			processor.addPropertyChangeListener(this);

			// Use weak reference to avoid memory leak
			setProcessor(processor);
			processor.execute();
			detectButton.setText("STOP");
		}
	}

	private void stopDetection() {
		BuildSpotsMeasuresAdvanced processor = getProcessor();
		if (processor != null && !processor.stopFlag)
			processor.stopFlag = true;
	}

	private BuildSeriesOptions initDetectOptions(Experiment exp) {
		BuildSeriesOptions options = new BuildSeriesOptions();

		options.expList = parent0.expListComboLazy;
		options.expList.index0 = parent0.expListComboLazy.getSelectedIndex();
		if (allSeriesCheckBox.isSelected())
			options.expList.index1 = options.expList.getItemCount() - 1;
		else
			options.expList.index1 = parent0.expListComboLazy.getSelectedIndex();
		options.detectAllSeries = allSeriesCheckBox.isSelected();
		if (!allSeriesCheckBox.isSelected()) {
			options.seriesLast = options.seriesFirst;
		} else {
			options.seriesFirst = 0;
		}
		options.concurrentDisplay = false; // concurrentDisplayCheckBox.isSelected();

		// other parameters
		options.transform01 = (ImageTransformEnums) spotsTransformsComboBox.getSelectedItem();
		options.spotThresholdUp = (spotsDirectionComboBox.getSelectedIndex() == 1);
		options.spotThreshold = (int) spotsThresholdSpinner.getValue();

		options.analyzePartOnly = false; // fromCheckBox.isSelected();

		options.overlayTransform = (ImageTransformEnums) spotsTransformsComboBox.getSelectedItem();
		options.overlayIfGreater = (spotsDirectionComboBox.getSelectedIndex() == 1);
		options.overlayThreshold = (int) spotsThresholdSpinner.getValue();

		options.transform02 = (ImageTransformEnums) fliesTransformsComboBox.getSelectedItem();
		options.flyThreshold = (int) fliesThresholdSpinner.getValue();
		options.flyThresholdUp = (fliesDirectionComboBox.getSelectedIndex() == 1);

		return options;
	}

	private void displayTransform2(Experiment exp) {
		if (viewButton2.isSelected()) {
			Canvas2D_3Transforms canvas = (Canvas2D_3Transforms) exp.seqCamData.getSequence().getFirstViewer()
					.getCanvas();
			updateTransformFunctions2OfCanvas(canvas);
		} else {
			removeOverlays(exp);
			Canvas2D_3Transforms canvas = (Canvas2D_3Transforms) exp.seqCamData.getSequence().getFirstViewer()
					.getCanvas();
			canvas.setTransformStep1Index(0);
		}
	}

	private void displayOverlays(boolean displayOn, Experiment exp) {
		if (displayOn) {
			setOverlays(exp);
		} else {
			removeOverlays(exp);
		}
	}

	private void setOverlays(Experiment exp) {
		if (exp.seqCamData != null) {
			exp.seqCamData.updateOverlay();
		}
//		if (exp.seqKymos != null)
//			exp.seqKymos.updateOverlay();
	}

	private void removeOverlays(Experiment exp) {
		if (exp.seqCamData != null)
			exp.seqCamData.removeOverlay();
//		if (exp.seqKymos != null)
//			exp.seqKymos.removeOverlay();
	}

	private void updateCanvasFunctions(Experiment exp, int index) {
		if (exp.seqCamData != null)
			updateCanvasFunction(exp.seqCamData, index);
//		if (exp.seqKymos != null)
//			updateCanvasFunction(exp.seqKymos, index);
	}

	private void updateCanvasFunction(SequenceCamData seqCamData, int index) {
		Sequence sequence = seqCamData.getSequence();
		if (sequence == null)
			return;
		Viewer v = sequence.getFirstViewer();
		if (v == null)
			return;
		Canvas2D_3Transforms canvas = (Canvas2D_3Transforms) v.getCanvas();
		updateTransformFunctions1OfCanvas(canvas);
		canvas.setTransformStep1Index(index + 1);
	}

	private void displayTransform1(Experiment exp) {
		int index = spotsTransformsComboBox.getSelectedIndex();
		if (!viewButton1.isSelected())
			index = -1;
		updateCanvasFunctions(exp, index);
	}

	private void updateTransformFunctions1OfCanvas(Canvas2D_3Transforms canvas) {
		if (canvas.getTransformStep1ItemCount() < (spotsTransformsComboBox.getItemCount() + 1)) {
			canvas.updateTransformsStep1(transforms);
		}
		int index = spotsTransformsComboBox.getSelectedIndex();
		canvas.setTransformStep1(index + 1, null);
	}

	private void updateTransformFunctions2OfCanvas(Canvas2D_3Transforms canvas) {
		if (canvas.getTransformStep1ItemCount() < (fliesDirectionComboBox.getItemCount() + 1)) {
			canvas.updateTransformsStep1(transforms);
		}
		int index = fliesDirectionComboBox.getSelectedIndex();
		canvas.setTransformStep1(index + 1, null);
	}

	@Override
	public void propertyChange(PropertyChangeEvent evt) {
		if (StringUtil.equals("thread_ended", evt.getPropertyName())) {
			detectButton.setText(detectString);
			checkMemoryBeforeLoading();

			Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
			if (exp != null) {
				// Use memory-optimized loading
				loadExperimentDataOptimized(exp);
				parent0.dlgMeasure.tabCharts.displayChartPanels(exp);
			}
			processorRef = null;

		}
	}

	private void checkMemoryBeforeLoading() {
		if (!vocalAboutMemory)
			return;

		Runtime runtime = Runtime.getRuntime();
		long usedMemory = runtime.totalMemory() - runtime.freeMemory();
		double usagePercent = (usedMemory * 100.0) / runtime.maxMemory();

		System.out.println("=== BEFORE EXPERIMENT LOADING ===");
		System.out.println("Memory Usage: " + usagePercent + "%");
		System.out.println("Used Memory: " + (usedMemory / 1024 / 1024) + " MB");

		if (usagePercent > 50) {
			System.err.println("WARNING: High memory before loading experiment data!");
			System.err.println("Forcing cleanup before loading...");

			// Force cleanup before loading
			forcePreLoadingCleanup();

			// Check again
			usedMemory = runtime.totalMemory() - runtime.freeMemory();
			usagePercent = (usedMemory * 100.0) / runtime.maxMemory();
			System.out.println("After cleanup: " + usagePercent + "%");
		}
	}

	private void forcePreLoadingCleanup() {
		if (vocalAboutMemory)
			System.out.println("=== FORCING PRE-LOADING CLEANUP ===");

		// Force multiple GC passes
		for (int i = 0; i < 3; i++) {
			System.gc();
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}
	}

	private void loadExperimentDataOptimized(Experiment exp) {

		try {
			// Load with memory monitoring
			long startMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

			// Use original loading method for now (will be optimized later)
			boolean success = exp.load_MS96_spotsMeasures();

			long endMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
			long memoryIncrease = endMemory - startMemory;

//			System.out.println("Experiment loading completed: " + (success ? "SUCCESS" : "FAILED"));
//			System.out.println("Memory increase: " + (memoryIncrease / 1024 / 1024) + " MB");

			if (memoryIncrease > 1024 * 1024 * 1024) { // > 1GB
				System.err.println(
						"WARNING: Large memory increase during loading: " + (memoryIncrease / 1024 / 1024) + " MB");
				System.err.println("Consider implementing streaming loading for large datasets");
			}

		} catch (Exception e) {
			System.err.println("Error during optimized experiment loading: " + e.getMessage());
			// Fallback to original method
			exp.load_MS96_spotsMeasures();
		}
	}

}
