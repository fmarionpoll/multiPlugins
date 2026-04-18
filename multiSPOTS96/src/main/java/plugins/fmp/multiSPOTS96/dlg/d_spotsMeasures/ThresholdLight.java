package plugins.fmp.multiSPOTS96.dlg.d_spotsMeasures;

import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
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

import icy.canvas.IcyCanvas;
import icy.gui.viewer.Viewer;
import icy.sequence.Sequence;
import icy.util.StringUtil;
import plugins.fmp.multiSPOTS96.MultiSPOTS96;
import plugins.fmp.multitools.canvas2D.Canvas2D_3Transforms;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.sequence.SequenceCamData;
import plugins.fmp.multitools.series.BuildSpotsMeasuresLight;
import plugins.fmp.multitools.series.options.BuildSeriesOptions;
import plugins.fmp.multitools.series.options.SpotDetectionMode;
import plugins.fmp.multitools.tools.imageTransform.ImageTransformEnums;
import plugins.fmp.multitools.tools.overlay.OverlayThreshold;

public class ThresholdLight extends JPanel implements PropertyChangeListener {
	private static final long serialVersionUID = 1L;

	private String detectString = "Detect";
	private JButton detectButton = new JButton(detectString);
	private JCheckBox allSeriesCheckBox = new JCheckBox("ALL (current to last)", false);

	private JLabel spotsFilterLabel = new JLabel("Spots filter");
	private String[] directions = new String[] { " threshold <", " threshold >" };
	private ImageTransformEnums[] transforms = new ImageTransformEnums[] { ImageTransformEnums.R_RGB,
			ImageTransformEnums.G_RGB, ImageTransformEnums.B_RGB, ImageTransformEnums.R2MINUS_GB,
			ImageTransformEnums.G2MINUS_RB, ImageTransformEnums.B2MINUS_RG, ImageTransformEnums.RGB,
			ImageTransformEnums.GBMINUS_2R, ImageTransformEnums.RBMINUS_2G, ImageTransformEnums.RGMINUS_2B,
			ImageTransformEnums.RGB_DIFFS, ImageTransformEnums.H_HSB, ImageTransformEnums.S_HSB,
			ImageTransformEnums.B_HSB };
	private JComboBox<ImageTransformEnums> spotsTransformsComboBox = new JComboBox<ImageTransformEnums>(transforms);
	private JComboBox<String> spotsDirectionComboBox = new JComboBox<String>(directions);
	private JSpinner spotsThresholdSpinner = new JSpinner(new SpinnerNumberModel(35, 0, 255, 1));
	private JToggleButton spotsViewButton = new JToggleButton("View");
	private JCheckBox spotsOverlayCheckBox = new JCheckBox("overlay");
	private JToggleButton fliesViewButton = new JToggleButton("View");

	private JLabel fliesFilterLabel = new JLabel("  Flies filter");
	private JComboBox<ImageTransformEnums> fliesTransformsComboBox = new JComboBox<ImageTransformEnums>(transforms);
	private JComboBox<String> fliesDirectionComboBox = new JComboBox<String>(directions);
	private JSpinner fliesThresholdSpinner = new JSpinner(new SpinnerNumberModel(50, 0, 255, 1));
	private JCheckBox fliesOverlayCheckBox = new JCheckBox("overlay");

	private OverlayThreshold overlayThreshold = null;
	private WeakReference<BuildSpotsMeasuresLight> processorRef = null;
	private MultiSPOTS96 parent0 = null;
	private JCheckBox usePipelinedDetectionCheckBox = new JCheckBox("Use multi-core detection", false);
	private JCheckBox useGPUCheckBox = new JCheckBox("Use GPU", false);

	public void init(GridLayout gridLayout, MultiSPOTS96 parent0) {
		setLayout(gridLayout);
		this.parent0 = parent0;
		FlowLayout layoutLeft = new FlowLayout(FlowLayout.LEFT);
		layoutLeft.setVgap(0);

		JPanel panel0 = new JPanel(layoutLeft);
		panel0.add(detectButton);
		panel0.add(allSeriesCheckBox);
		panel0.add(usePipelinedDetectionCheckBox);
		panel0.add(useGPUCheckBox);
		add(panel0);

		JPanel panel1 = new JPanel(layoutLeft);
		panel1.add(spotsFilterLabel);
		panel1.add(spotsTransformsComboBox);
		panel1.add(spotsDirectionComboBox);
		panel1.add(spotsThresholdSpinner);
		panel1.add(spotsViewButton);
		panel1.add(spotsOverlayCheckBox);
		add(panel1);

		JPanel panel2 = new JPanel(layoutLeft);
		panel2.add(fliesFilterLabel);
		panel2.add(fliesTransformsComboBox);
		panel2.add(fliesDirectionComboBox);
		panel2.add(fliesThresholdSpinner);
		panel2.add(fliesViewButton);
		panel2.add(fliesOverlayCheckBox);
		add(panel2);

		spotsTransformsComboBox.setSelectedItem(ImageTransformEnums.RGB_DIFFS);
		spotsDirectionComboBox.setSelectedIndex(1);

		fliesTransformsComboBox.setSelectedItem(ImageTransformEnums.B_RGB);
		fliesDirectionComboBox.setSelectedIndex(0);
		spotsOverlayCheckBox.setEnabled(false);
		fliesOverlayCheckBox.setEnabled(false);
		useGPUCheckBox.setEnabled(false);
		syncDetectionModeFromViewOptions();
		declareListeners();
	}

	private void syncDetectionModeFromViewOptions() {
		if (parent0 == null)
			return;
		SpotDetectionMode mode = parent0.viewOptions.getSpotDetectionMode();
		// Treat anything other than BASIC (incl. AUTO) as "use pipelined"
		boolean usePipelined = mode != SpotDetectionMode.BASIC;
		usePipelinedDetectionCheckBox.setSelected(usePipelined);
	}

	private BuildSpotsMeasuresLight getProcessor() {
		if (processorRef != null) {
			return processorRef.get();
		}
		return null;
	}

	private void setProcessor(BuildSpotsMeasuresLight processor) {
		this.processorRef = new WeakReference<>(processor);
	}

	private void declareListeners() {
		spotsTransformsComboBox.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp != null) {
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
				if (exp != null && fliesViewButton.isSelected()) {
					int index = fliesTransformsComboBox.getSelectedIndex();
					IcyCanvas canvas = exp.getSeqCamData().getSequence().getFirstViewer().getCanvas();
					updateTransformFunctions2OfCanvas(canvas);
					if (!fliesViewButton.isSelected())
						fliesViewButton.setSelected(true);
					if (canvas instanceof Canvas2D_3Transforms)
						((Canvas2D_3Transforms) canvas).setTransformStep1Index(index + 1);
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

		spotsViewButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp != null) {
					if (spotsViewButton.isSelected()) {
						fliesViewButton.setSelected(false);
						fliesOverlayCheckBox.setEnabled(false);
					}
					displayTransform1(exp);
					spotsOverlayCheckBox.setEnabled(spotsViewButton.isSelected());
					if (spotsViewButton.isSelected()) {
						if (spotsOverlayCheckBox.isSelected())
							updateOverlaysThreshold();
						else
							removeOurOverlay(exp);
					} else {
						removeOurOverlay(exp);
						if (exp.getSeqCamData() != null)
							exp.getSeqCamData().removeOverlay();
					}
				}
			}
		});

		fliesViewButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp != null) {
					if (fliesViewButton.isSelected()) {
						spotsViewButton.setSelected(false);
						spotsOverlayCheckBox.setEnabled(false);
					}
					displayTransform2(exp);
					fliesOverlayCheckBox.setEnabled(fliesViewButton.isSelected());
					if (fliesViewButton.isSelected()) {
						if (fliesOverlayCheckBox.isSelected())
							updateOverlaysThreshold();
						else
							removeOurOverlay(exp);
					} else {
						removeOurOverlay(exp);
						if (exp.getSeqCamData() != null)
							exp.getSeqCamData().removeOverlay();
					}
				}
			}
		});

		spotsOverlayCheckBox.addItemListener(new ItemListener() {
			@Override
			public void itemStateChanged(ItemEvent e) {
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp != null) {
					if (spotsOverlayCheckBox.isSelected()) {
						updateOverlaysThreshold();
					} else {
						removeOurOverlay(exp);
						if (exp.getSeqCamData() != null)
							exp.getSeqCamData().removeOverlay();
					}
				}
			}
		});

		fliesOverlayCheckBox.addItemListener(new ItemListener() {
			@Override
			public void itemStateChanged(ItemEvent e) {
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp != null) {
					if (fliesOverlayCheckBox.isSelected()) {
						updateOverlaysThreshold();
					} else {
						removeOurOverlay(exp);
						if (exp.getSeqCamData() != null)
							exp.getSeqCamData().removeOverlay();
					}
				}
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

		usePipelinedDetectionCheckBox.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				if (parent0 == null)
					return;
				SpotDetectionMode mode = usePipelinedDetectionCheckBox.isSelected() ? SpotDetectionMode.PIPELINED
						: SpotDetectionMode.BASIC;
				parent0.viewOptions.setSpotDetectionMode(mode);
				parent0.viewOptions.save(parent0.getPreferences("viewOptions"));
			}
		});
	}

	void updateOverlaysThreshold() {
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		if (exp == null || exp.getSeqCamData() == null)
			return;

		boolean showSpotsOverlay = spotsViewButton.isSelected() && spotsOverlayCheckBox.isSelected();
		boolean showFliesOverlay = fliesViewButton.isSelected() && fliesOverlayCheckBox.isSelected();

		if (!showSpotsOverlay && !showFliesOverlay) {
			removeOurOverlay(exp);
			exp.getSeqCamData().removeOverlay();
			return;
		}

		ImageTransformEnums transform;
		boolean ifGreater;
		int threshold;
		if (showSpotsOverlay) {
			transform = (ImageTransformEnums) spotsTransformsComboBox.getSelectedItem();
			threshold = (int) spotsThresholdSpinner.getValue();
			ifGreater = (spotsDirectionComboBox.getSelectedIndex() == 1);
		} else {
			transform = (ImageTransformEnums) fliesTransformsComboBox.getSelectedItem();
			threshold = (int) fliesThresholdSpinner.getValue();
			ifGreater = (fliesDirectionComboBox.getSelectedIndex() == 1);
		}

		ensureOverlayAdded(exp);
		overlayThreshold.setThresholdSingle(threshold, transform, ifGreater);
		overlayThreshold.painterChanged();
	}

	private void ensureOverlayAdded(Experiment exp) {
		if (exp.getSeqCamData() == null)
			return;
		if (overlayThreshold == null) {
			overlayThreshold = new OverlayThreshold(exp.getSeqCamData().getSequence());
		} else {
			exp.getSeqCamData().getSequence().removeOverlay(overlayThreshold);
			overlayThreshold.setSequence(exp.getSeqCamData().getSequence());
		}
		exp.getSeqCamData().getSequence().addOverlay(overlayThreshold);
	}

	private void removeOurOverlay(Experiment exp) {
		if (exp.getSeqCamData() != null && exp.getSeqCamData().getSequence() != null && overlayThreshold != null)
			exp.getSeqCamData().getSequence().removeOverlay(overlayThreshold);
		overlayThreshold = null;
	}

	void startDetection() {
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		if (exp != null) {
			BuildSpotsMeasuresLight processor = new BuildSpotsMeasuresLight();
			processor.options = initDetectOptions(exp);
			processor.addPropertyChangeListener(this);
			setProcessor(processor);
			processor.execute();
			detectButton.setText("STOP");
		}
	}

	private void stopDetection() {
		BuildSpotsMeasuresLight processor = getProcessor();
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
		options.concurrentDisplay = false;

		options.transform01 = (ImageTransformEnums) spotsTransformsComboBox.getSelectedItem();
		options.spotThresholdUp = (spotsDirectionComboBox.getSelectedIndex() == 1);
		options.spotThreshold = (int) spotsThresholdSpinner.getValue();

		options.analyzePartOnly = false;

		options.overlayTransform = (ImageTransformEnums) spotsTransformsComboBox.getSelectedItem();
		options.overlayIfGreater = (spotsDirectionComboBox.getSelectedIndex() == 1);
		options.overlayThreshold = (int) spotsThresholdSpinner.getValue();

		options.transform02 = (ImageTransformEnums) fliesTransformsComboBox.getSelectedItem();
		options.flyThreshold = (int) fliesThresholdSpinner.getValue();
		options.flyThresholdUp = (fliesDirectionComboBox.getSelectedIndex() == 1);

		options.spotDetectionMode = (parent0 != null) ? parent0.viewOptions.getSpotDetectionMode()
				: SpotDetectionMode.AUTO;
		// GPU option
		options.useGpuTransforms = useGPUCheckBox.isSelected();

		return options;
	}

	private void displayTransform2(Experiment exp) {
		if (fliesViewButton.isSelected()) {
			IcyCanvas canvas = exp.getSeqCamData().getSequence().getFirstViewer().getCanvas();
			updateTransformFunctions2OfCanvas(canvas);
		} else {
			removeOurOverlay(exp);
			removeOverlays(exp);
			IcyCanvas canvas = exp.getSeqCamData().getSequence().getFirstViewer().getCanvas();
			if (canvas instanceof Canvas2D_3Transforms)
				((Canvas2D_3Transforms) canvas).setTransformStep1Index(0);
		}
	}

	private void removeOverlays(Experiment exp) {
		if (exp.getSeqCamData() != null)
			exp.getSeqCamData().removeOverlay();
	}

	private void updateCanvasFunctions(Experiment exp, int index) {
		if (exp.getSeqCamData() != null)
			updateCanvasFunction(exp.getSeqCamData(), index);
	}

	private void updateCanvasFunction(SequenceCamData seqCamData, int index) {
		Sequence sequence = seqCamData.getSequence();
		if (sequence == null)
			return;
		Viewer v = sequence.getFirstViewer();
		if (v == null)
			return;
		IcyCanvas canvas = v.getCanvas();
		updateTransformFunctions1OfCanvas(canvas);
		if (canvas instanceof Canvas2D_3Transforms)
			((Canvas2D_3Transforms) canvas).setTransformStep1Index(index + 1);
	}

	private void displayTransform1(Experiment exp) {
		int index = spotsTransformsComboBox.getSelectedIndex();
		if (!spotsViewButton.isSelected())
			index = -1;
		updateCanvasFunctions(exp, index);
	}

	private void updateTransformFunctions1OfCanvas(IcyCanvas canvas) {
		if (!(canvas instanceof Canvas2D_3Transforms))
			return;
		Canvas2D_3Transforms c3 = (Canvas2D_3Transforms) canvas;
		if (c3.getTransformStep1ItemCount() < (spotsTransformsComboBox.getItemCount() + 1))
			c3.updateTransformsStep1(transforms);
		int index = spotsTransformsComboBox.getSelectedIndex();
		c3.setTransformStep1(index + 1, null);
	}

	private void updateTransformFunctions2OfCanvas(IcyCanvas canvas) {
		if (!(canvas instanceof Canvas2D_3Transforms))
			return;
		Canvas2D_3Transforms c3 = (Canvas2D_3Transforms) canvas;
		if (c3.getTransformStep1ItemCount() < (fliesDirectionComboBox.getItemCount() + 1))
			c3.updateTransformsStep1(transforms);
		int index = fliesDirectionComboBox.getSelectedIndex();
		c3.setTransformStep1(index + 1, null);
	}

	@Override
	public void propertyChange(PropertyChangeEvent evt) {
		if (StringUtil.equals("thread_ended", evt.getPropertyName())) {
			detectButton.setText(detectString);

			Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
			if (exp != null) {
				exp.load_spots_description_and_measures();
				parent0.dlgMeasure.tabCharts.displayChartPanels(exp);
			}
			processorRef = null;
		}
	}
}
