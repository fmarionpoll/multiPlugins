package plugins.fmp.multiSPOTS.dlg.imageColors;

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
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import icy.canvas.IcyCanvas;
import icy.gui.viewer.Viewer;
import icy.sequence.Sequence;
import icy.util.StringUtil;
import plugins.fmp.multiSPOTS.MultiSPOTS;
import plugins.fmp.multiSPOTS.dlg.imageFilters.SpotsMeasuresUi;
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

	private final String[] spotRoiCountModes = new String[] { "far from refs", "matching refs" };
	private JComboBox<String> spotsDirectionComboBox = new JComboBox<>(spotRoiCountModes);
	private JSpinner spotsThresholdSpinner = new JSpinner(new SpinnerNumberModel(35, 0, 255, 1));
	private JToggleButton spotsViewButton = new JToggleButton("View");
	private JCheckBox spotsOverlayCheckBox = new JCheckBox("overlay");
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
	private JButton editMaskFliesButton = new JButton("Edit mask & flies…");
	private JButton loadSavePresetButton = new JButton("Load/save detection…");

	private SpotMeasureColorParamsPanel spotMeasureColorParamsPanel;

	private OverlayThreshold overlayThreshold = null;
	private WeakReference<BuildSpotsMeasuresColor> processorRef = null;
	private MultiSPOTS parent0 = null;

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
			if (exp != null && spotsViewButton.isSelected() && spotsOverlayCheckBox.isSelected()) {
				updateOverlaysThreshold();
			}
		});

		JPanel panelSpotsMask = new JPanel(layoutLeft);
		panelSpotsMask.add(new JLabel("ROI count"));
		spotsDirectionComboBox
				.setToolTipText("<html>Detection only. The color <b>Distance</b> (above) builds the mask.<br>"
						+ "This control chooses whether spot ROI statistics count pixels that <b>match</b> your reference colors or pixels <b>outside</b> that match.<br>"
						+ "Default &quot;matching refs&quot; matches the red overlay (mask drawn on matched pixels).</html>");
		spotsThresholdSpinner.setToolTipText(
				"<html>Used only after the color step produces a 0/255 mask. For typical thresholds (0&ndash;254) it does <b>not</b> change which pixels match;<br>"
						+ "tune the mask with <b>Distance</b> in Edit colors. Very high values (e.g. &ge; 255) can exclude all pixels.</html>");
		panelSpotsMask.add(spotsDirectionComboBox);
		panelSpotsMask.add(new JLabel("post step "));
		panelSpotsMask.add(spotsThresholdSpinner);
		panelSpotsMask.add(spotsViewButton);
		panelSpotsMask.add(spotsOverlayCheckBox);

		JPanel panelFlies = new JPanel(layoutLeft);
		panelFlies.add(fliesFilterLabel);
		panelFlies.add(fliesTransformsComboBox);
		panelFlies.add(fliesDirectionComboBox);
		panelFlies.add(fliesThresholdSpinner);
		panelFlies.add(fliesViewButton);
		panelFlies.add(fliesOverlayCheckBox);

		SpotsMeasuresUi.layoutStackedRows(maskAndFliesPanel, panelSpotsMask, panelFlies);

		JPanel panelEditors = new JPanel(layoutLeft);
		panelEditors.add(editColorsButton);
		panelEditors.add(editMaskFliesButton);

		JPanel panelPreset = new JPanel(layoutLeft);
		panelPreset.add(loadSavePresetButton);

		SpotsMeasuresUi.layoutStackedRows(this, panel0, panelEditors, panelPreset);

		spotMeasureColorParamsPanel = new SpotMeasureColorParamsPanel(parent0, this);

		spotsDirectionComboBox.setSelectedIndex(1);
		fliesTransformsComboBox.setSelectedItem(ImageTransformEnums.B_RGB);
		fliesDirectionComboBox.setSelectedIndex(0);
		spotsOverlayCheckBox.setEnabled(false);
		fliesOverlayCheckBox.setEnabled(false);
		declareListeners();
	}

	private Window dialogOwner() {
		return SwingUtilities.getWindowAncestor(this);
	}

	private void showModalEditor(String title, JPanel body, int approxWidth, int approxHeight) {
		Window owner = dialogOwner();
		JScrollPane scroll = new JScrollPane(body);
		scroll.setPreferredSize(new Dimension(approxWidth, approxHeight));
		JDialog d = owner != null ? new JDialog(owner, title, Dialog.ModalityType.APPLICATION_MODAL)
				: new JDialog((Frame) null, title, true);
		d.setContentPane(scroll);
		d.pack();
		d.setLocationRelativeTo(owner);
		d.setVisible(true);
		d.dispose();
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
				showModalEditor("Edit detection colors", thresholdColors, 760, 480);
			}
		});
		editMaskFliesButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				showModalEditor("Edit mask and fly filter", maskAndFliesPanel, 900, 220);
			}
		});
		loadSavePresetButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				showModalEditor("Load / save color detection preset", spotMeasureColorParamsPanel, 520, 280);
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
			@Override
			public void stateChanged(ChangeEvent e) {
				updateOverlaysThreshold();
			}
		});

		fliesThresholdSpinner.addChangeListener(new ChangeListener() {
			@Override
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
					clearSpotCanvasTransform(exp);
					spotsOverlayCheckBox.setEnabled(spotsViewButton.isSelected());
					if (spotsViewButton.isSelected()) {
						if (spotsOverlayCheckBox.isSelected()) {
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
						spotsViewButton.setSelected(false);
						spotsOverlayCheckBox.setEnabled(false);
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

		spotsOverlayCheckBox.addItemListener(new ItemListener() {
			@Override
			public void itemStateChanged(ItemEvent e) {
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp != null) {
					if (spotsOverlayCheckBox.isSelected()) {
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

		boolean showSpotsOverlay = spotsViewButton.isSelected() && spotsOverlayCheckBox.isSelected();
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
		options.spotThresholdUp = (spotsDirectionComboBox.getSelectedIndex() == 1);
		options.spotThreshold = (int) spotsThresholdSpinner.getValue();

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
		o.spotThresholdUp = spotsDirectionComboBox.getSelectedIndex() == 1;
		o.spotThreshold = (int) spotsThresholdSpinner.getValue();
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
		spotsDirectionComboBox.setSelectedIndex(o.spotThresholdUp ? 1 : 0);
		spotsThresholdSpinner.setValue(Math.max(0, Math.min(255, o.spotThreshold)));
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
