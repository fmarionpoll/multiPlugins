package plugins.fmp.multiSPOTS.dlg.measure_imageColors;

import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.GridLayout;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.lang.ref.WeakReference;
import java.util.ArrayList;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JToggleButton;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import icy.canvas.IcyCanvas;
import icy.gui.viewer.Viewer;
import icy.sequence.Sequence;
import icy.util.StringUtil;
import plugins.fmp.multiSPOTS.MultiSPOTS;
import plugins.fmp.multiSPOTS.dlg.measure_imageFilters.SpotsMeasuresUi;
import plugins.fmp.multitools.canvas2D.Canvas2D_3Transforms;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.series.BuildSpotsMeasuresColor;
import plugins.fmp.multitools.series.options.BuildSeriesOptions;
import plugins.fmp.multitools.tools.imageTransform.ImageTransformEnums;
import plugins.fmp.multitools.tools.overlay.OverlayThreshold;

public class DetectColorPanel extends JPanel implements PropertyChangeListener {
	private static final long serialVersionUID = 1L;

	private String detectString = "Detect";
	private JButton detectButton = new JButton(detectString);
	private JCheckBox allSeriesCheckBox = new JCheckBox("ALL (current to last)", false);

	private final ThresholdColors thresholdColors = new ThresholdColors();

	private JToggleButton fliesViewButton = new JToggleButton("View");

	private JLabel fliesFilterLabel = new JLabel("  Flies filter");
	private ImageTransformEnums[] transforms = new ImageTransformEnums[] { ImageTransformEnums.R_RGB,
			ImageTransformEnums.G_RGB, ImageTransformEnums.B_RGB, ImageTransformEnums.B_MINUS_MINRG,
			ImageTransformEnums.B_MINUS_MEANGREY_CTR, ImageTransformEnums.R2MINUS_GB, ImageTransformEnums.G2MINUS_RB,
			ImageTransformEnums.B2MINUS_RG, ImageTransformEnums.RGB, ImageTransformEnums.GBMINUS_2R,
			ImageTransformEnums.RBMINUS_2G, ImageTransformEnums.RGMINUS_2B, ImageTransformEnums.RGB_DIFFS,
			ImageTransformEnums.RGB_DIFFS_LOCAL_MEAN, ImageTransformEnums.H_HSB, ImageTransformEnums.S_HSB,
			ImageTransformEnums.B_HSB };
	private JComboBox<ImageTransformEnums> fliesTransformsComboBox = new JComboBox<ImageTransformEnums>(transforms);
	private final String[] flyDirections = new String[] { " threshold <", " threshold >" };
	private JComboBox<String> fliesDirectionComboBox = new JComboBox<>(flyDirections);
	private JSpinner fliesThresholdSpinner = new JSpinner(new SpinnerNumberModel(50, 0, 255, 1));
	private JCheckBox fliesOverlayCheckBox = new JCheckBox("overlay");

	private final JPanel maskAndFliesPanel = new JPanel();
	private JButton editColorsButton = new JButton("Edit colors…");
	private JButton editMaskFliesButton = new JButton("Edit fly filter…");
	private JButton loadSavePresetButton = new JButton("Load/save detection…");

	private SpotMeasureColorParamsPanel spotMeasureColorParamsPanel;

	private OverlayThreshold overlayThreshold = null;
	private WeakReference<BuildSpotsMeasuresColor> processorRef = null;
	private MultiSPOTS parent0 = null;

	private JDialog thresholdColorsEditorDialog;
	private JDialog maskFliesEditorDialog;
	private JDialog presetEditorDialog;

	public void init(GridLayout gridLayout, MultiSPOTS parent0) {
		this.parent0 = parent0;
		FlowLayout layoutLeft = new FlowLayout(FlowLayout.LEFT);
		layoutLeft.setVgap(0);

		JPanel panel0 = new JPanel(layoutLeft);
		panel0.add(detectButton);
		panel0.add(allSeriesCheckBox);

		thresholdColors.init(parent0);
		thresholdColors.setOnSettingsChanged(() -> {
			Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
			if (exp != null && thresholdColors.getSpotsViewButton().isSelected()
					&& thresholdColors.getSpotsOverlayCheckBox().isSelected()) {
				updateOverlaysThreshold();
			}
		});

		JPanel panelFlies = new JPanel(layoutLeft);
		panelFlies.add(fliesFilterLabel);
		panelFlies.add(fliesTransformsComboBox);
		panelFlies.add(fliesDirectionComboBox);
		panelFlies.add(fliesThresholdSpinner);
		panelFlies.add(fliesViewButton);
		panelFlies.add(fliesOverlayCheckBox);

		SpotsMeasuresUi.layoutStackedRows(maskAndFliesPanel, panelFlies);

		JPanel panelEditors = new JPanel(layoutLeft);
		panelEditors.add(editColorsButton);
		panelEditors.add(editMaskFliesButton);

		JPanel panelPreset = new JPanel(layoutLeft);
		panelPreset.add(loadSavePresetButton);

		SpotsMeasuresUi.layoutStackedRows(this, panel0, panelEditors, panelPreset);

		spotMeasureColorParamsPanel = new SpotMeasureColorParamsPanel(parent0, this);

		fliesTransformsComboBox.setSelectedItem(ImageTransformEnums.B_RGB);
		fliesDirectionComboBox.setSelectedIndex(0);
		fliesOverlayCheckBox.setEnabled(false);
		declareListeners();
	}

	private Window dialogOwner() {
		return SwingUtilities.getWindowAncestor(this);
	}

	private JDialog newFloatingEditor(String title, JPanel body, int approxWidth, int approxHeight) {
		Window owner = dialogOwner();
		JDialog d;
		if (owner != null) {
			d = new JDialog(owner, title, Dialog.ModalityType.MODELESS);
		} else {
			d = new JDialog((Frame) null, title);
			d.setModal(false);
		}
		JScrollPane scroll = new JScrollPane(body);
		scroll.setPreferredSize(new Dimension(approxWidth, approxHeight));
		d.setContentPane(scroll);
		d.setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
		d.pack();
		d.setLocationRelativeTo(owner);
		return d;
	}

	private void showFloatingColorsEditor() {
		if (thresholdColorsEditorDialog == null || !thresholdColorsEditorDialog.isDisplayable()) {
			thresholdColorsEditorDialog = newFloatingEditor("Edit detection colors", thresholdColors, 760, 560);
		}
		thresholdColorsEditorDialog.setVisible(true);
		thresholdColorsEditorDialog.toFront();
	}

	private void showFloatingMaskFliesEditor() {
		if (maskFliesEditorDialog == null || !maskFliesEditorDialog.isDisplayable()) {
			maskFliesEditorDialog = newFloatingEditor("Edit fly filter", maskAndFliesPanel, 900, 140);
		}
		maskFliesEditorDialog.setVisible(true);
		maskFliesEditorDialog.toFront();
	}

	private void showFloatingPresetEditor() {
		if (presetEditorDialog == null || !presetEditorDialog.isDisplayable()) {
			presetEditorDialog = newFloatingEditor("Load / save color detection preset", spotMeasureColorParamsPanel,
					520, 280);
		}
		presetEditorDialog.setVisible(true);
		presetEditorDialog.toFront();
	}

	private BuildSpotsMeasuresColor getProcessor() {
		if (processorRef != null) {
			return processorRef.get();
		}
		return null;
	}

	private void setProcessor(BuildSpotsMeasuresColor processor) {
		this.processorRef = new WeakReference<>(processor);
	}

	private void declareListeners() {
		editColorsButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				showFloatingColorsEditor();
			}
		});

		editMaskFliesButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				showFloatingMaskFliesEditor();
			}
		});

		loadSavePresetButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				showFloatingPresetEditor();
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
					if (!fliesViewButton.isSelected()) {
						fliesViewButton.setSelected(true);
					}
					if (canvas instanceof Canvas2D_3Transforms) {
						((Canvas2D_3Transforms) canvas).setTransformStep1Index(index + 1);
					}
					updateOverlaysThreshold();
				}
			}
		});

		fliesDirectionComboBox.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				updateOverlaysThreshold();
			}
		});

		fliesThresholdSpinner.addChangeListener(new ChangeListener() {
			@Override
			public void stateChanged(ChangeEvent e) {
				updateOverlaysThreshold();
			}
		});

		thresholdColors.getSpotsViewButton().addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp != null) {
					if (thresholdColors.getSpotsViewButton().isSelected()) {
						fliesViewButton.setSelected(false);
						fliesOverlayCheckBox.setEnabled(false);
					}
					clearSpotCanvasTransform(exp);
					thresholdColors.getSpotsOverlayCheckBox()
							.setEnabled(thresholdColors.getSpotsViewButton().isSelected());
					if (thresholdColors.getSpotsViewButton().isSelected()) {
						if (thresholdColors.getSpotsOverlayCheckBox().isSelected()) {
							updateOverlaysThreshold();
						} else {
							removeOurOverlay(exp);
						}
					} else {
						removeOurOverlay(exp);
						if (exp.getSeqCamData() != null) {
							exp.getSeqCamData().removeOverlay();
						}
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
						thresholdColors.clearSpotsPreviewForFlyExclusive();
					}
					displayTransform2(exp);
					fliesOverlayCheckBox.setEnabled(fliesViewButton.isSelected());
					if (fliesViewButton.isSelected()) {
						if (fliesOverlayCheckBox.isSelected()) {
							updateOverlaysThreshold();
						} else {
							removeOurOverlay(exp);
						}
					} else {
						removeOurOverlay(exp);
						if (exp.getSeqCamData() != null) {
							exp.getSeqCamData().removeOverlay();
						}
					}
				}
			}
		});

		thresholdColors.getSpotsOverlayCheckBox().addItemListener(new ItemListener() {
			@Override
			public void itemStateChanged(ItemEvent e) {
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp != null) {
					if (thresholdColors.getSpotsOverlayCheckBox().isSelected()) {
						updateOverlaysThreshold();
					} else {
						removeOurOverlay(exp);
						if (exp.getSeqCamData() != null) {
							exp.getSeqCamData().removeOverlay();
						}
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
						if (exp.getSeqCamData() != null) {
							exp.getSeqCamData().removeOverlay();
						}
					}
				}
			}
		});

		detectButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				if (detectButton.getText().equals(detectString)) {
					startDetection();
				} else {
					stopDetection();
				}
			}
		});
	}

	void updateOverlaysThreshold() {
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		if (exp == null || exp.getSeqCamData() == null) {
			return;
		}

		boolean showSpotsOverlay = thresholdColors.getSpotsViewButton().isSelected()
				&& thresholdColors.getSpotsOverlayCheckBox().isSelected();
		boolean showFliesOverlay = fliesViewButton.isSelected() && fliesOverlayCheckBox.isSelected();

		if (!showSpotsOverlay && !showFliesOverlay) {
			removeOurOverlay(exp);
			exp.getSeqCamData().removeOverlay();
			return;
		}

		if (showSpotsOverlay) {
			ArrayList<java.awt.Color> colors = thresholdColors.getReferenceColors();
			if (colors.isEmpty()) {
				removeOurOverlay(exp);
				return;
			}
			ensureOverlayAdded(exp);
			ArrayList<java.awt.Color> exColors = thresholdColors.getExcludeReferenceColors();
			int exMax = thresholdColors.getExcludeDistanceMax();
			overlayThreshold.setThresholdColor(colors, thresholdColors.getColorDistanceTypeInt(),
					thresholdColors.getColorDistanceMax(), thresholdColors.getSpotThresholdColorSpace(),
					exColors.isEmpty() || exMax <= 0 ? null : exColors, exMax);
			overlayThreshold.painterChanged();
			return;
		}

		ImageTransformEnums transform = (ImageTransformEnums) fliesTransformsComboBox.getSelectedItem();
		int threshold = (int) fliesThresholdSpinner.getValue();
		boolean ifGreater = (fliesDirectionComboBox.getSelectedIndex() == 1);

		ensureOverlayAdded(exp);
		overlayThreshold.setThresholdSingle(threshold, transform, ifGreater);
		overlayThreshold.painterChanged();
	}

	private void ensureOverlayAdded(Experiment exp) {
		if (exp.getSeqCamData() == null) {
			return;
		}
		if (overlayThreshold == null) {
			overlayThreshold = new OverlayThreshold(exp.getSeqCamData().getSequence());
		} else {
			exp.getSeqCamData().getSequence().removeOverlay(overlayThreshold);
			overlayThreshold.setSequence(exp.getSeqCamData().getSequence());
		}
		exp.getSeqCamData().getSequence().addOverlay(overlayThreshold);
	}

	private void removeOurOverlay(Experiment exp) {
		if (exp.getSeqCamData() != null && exp.getSeqCamData().getSequence() != null && overlayThreshold != null) {
			exp.getSeqCamData().getSequence().removeOverlay(overlayThreshold);
		}
		overlayThreshold = null;
	}

	void startDetection() {
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		if (exp != null) {
			BuildSpotsMeasuresColor processor = new BuildSpotsMeasuresColor();
			processor.options = initDetectOptions(exp);
			processor.addPropertyChangeListener(this);
			setProcessor(processor);
			processor.execute();
			detectButton.setText("STOP");
		}
	}

	private void stopDetection() {
		BuildSpotsMeasuresColor processor = getProcessor();
		if (processor != null && !processor.stopFlag) {
			processor.stopFlag = true;
		}
	}

	private BuildSeriesOptions initDetectOptions(Experiment exp) {
		BuildSeriesOptions options = new BuildSeriesOptions();

		options.expList = parent0.expListComboLazy;
		options.expList.index0 = parent0.expListComboLazy.getSelectedIndex();
		if (allSeriesCheckBox.isSelected()) {
			options.expList.index1 = options.expList.getItemCount() - 1;
		} else {
			options.expList.index1 = parent0.expListComboLazy.getSelectedIndex();
		}
		options.detectAllSeries = allSeriesCheckBox.isSelected();
		if (!allSeriesCheckBox.isSelected()) {
			options.seriesLast = options.seriesFirst;
		} else {
			options.seriesFirst = 0;
		}
		options.concurrentDisplay = false;

		thresholdColors.applyToBuildSeriesOptions(options);

		options.transform01 = ImageTransformEnums.NONE;
		options.analyzePartOnly = false;

		options.overlayTransform = ImageTransformEnums.NONE;
		options.overlayIfGreater = options.spotThresholdUp;
		options.overlayThreshold = options.spotThreshold;

		options.transform02 = (ImageTransformEnums) fliesTransformsComboBox.getSelectedItem();
		options.flyThreshold = (int) fliesThresholdSpinner.getValue();
		options.flyThresholdUp = (fliesDirectionComboBox.getSelectedIndex() == 1);

		options.flyOccupancyPercentForSpotSumNoFly = 8.0;

		options.useGpuTransforms = false;

		return options;
	}

	public void copyColorPipelineToPreset(BuildSeriesOptions o) {
		if (o == null) {
			return;
		}
		thresholdColors.applyToBuildSeriesOptions(o);
		Object tf = fliesTransformsComboBox.getSelectedItem();
		if (tf instanceof ImageTransformEnums) {
			o.transform02 = (ImageTransformEnums) tf;
		}
		o.flyThreshold = (int) fliesThresholdSpinner.getValue();
		o.flyThresholdUp = fliesDirectionComboBox.getSelectedIndex() == 1;
	}

	public void applyColorPipelineFromPreset(BuildSeriesOptions o) {
		if (o == null) {
			return;
		}
		thresholdColors.applyFromBuildSeriesOptions(o, false);
		if (o.transform02 != null) {
			fliesTransformsComboBox.setSelectedItem(o.transform02);
		}
		if (fliesTransformsComboBox.getSelectedIndex() < 0) {
			fliesTransformsComboBox.setSelectedItem(ImageTransformEnums.B_RGB);
		}
		fliesThresholdSpinner.setValue(Math.max(0, Math.min(255, o.flyThreshold)));
		fliesDirectionComboBox.setSelectedIndex(o.flyThresholdUp ? 1 : 0);
		if (parent0 != null) {
			Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
			if (exp != null) {
				updateOverlaysThreshold();
			}
		}
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				thresholdColors.refreshComboPresentationAfterBulkLoad();
			}
		});
	}

	private void clearSpotCanvasTransform(Experiment exp) {
		removeOurOverlay(exp);
		removeOverlays(exp);
		if (exp.getSeqCamData() == null) {
			return;
		}
		Sequence sequence = exp.getSeqCamData().getSequence();
		if (sequence == null) {
			return;
		}
		Viewer v = sequence.getFirstViewer();
		if (v == null) {
			return;
		}
		IcyCanvas canvas = v.getCanvas();
		if (canvas instanceof Canvas2D_3Transforms) {
			((Canvas2D_3Transforms) canvas).setTransformStep1Index(0);
		}
	}

	private void removeOverlays(Experiment exp) {
		if (exp.getSeqCamData() != null) {
			exp.getSeqCamData().removeOverlay();
		}
	}

	private void displayTransform2(Experiment exp) {
		if (fliesViewButton.isSelected()) {
			IcyCanvas canvas = exp.getSeqCamData().getSequence().getFirstViewer().getCanvas();
			updateTransformFunctions2OfCanvas(canvas);
		} else {
			removeOurOverlay(exp);
			removeOverlays(exp);
			IcyCanvas canvas = exp.getSeqCamData().getSequence().getFirstViewer().getCanvas();
			if (canvas instanceof Canvas2D_3Transforms) {
				((Canvas2D_3Transforms) canvas).setTransformStep1Index(0);
			}
		}
	}

	private void updateTransformFunctions2OfCanvas(IcyCanvas canvas) {
		if (!(canvas instanceof Canvas2D_3Transforms)) {
			return;
		}
		Canvas2D_3Transforms c3 = (Canvas2D_3Transforms) canvas;
		if (c3.getTransformStep1ItemCount() < (fliesTransformsComboBox.getItemCount() + 1)) {
			c3.updateTransformsStep1(transforms);
		}
		int index = fliesTransformsComboBox.getSelectedIndex();
		c3.setTransformStep1(index + 1, null);
	}

	@Override
	public void propertyChange(PropertyChangeEvent evt) {
		if (StringUtil.equals("thread_ended", evt.getPropertyName())) {
			detectButton.setText(detectString);

			Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
			if (exp != null) {
				exp.load_spots_description_and_measures();
				parent0.dlgMeasureV5.chartsColorPanel.displayChartPanels(exp);
			}
			processorRef = null;
		}
	}
}
