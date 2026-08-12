package plugins.fmp.multiSPOTS.dlg.e_flyPosition;

import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JToggleButton;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;

import icy.canvas.IcyCanvas;
import icy.gui.viewer.Viewer;
import icy.gui.viewer.ViewerEvent;
import icy.gui.viewer.ViewerEvent.ViewerEventType;
import icy.gui.viewer.ViewerListener;
import icy.sequence.DimensionId;
import icy.util.StringUtil;
import plugins.fmp.multiSPOTS.MultiSPOTS;
import plugins.fmp.multitools.canvas2D.Canvas2D_3Transforms;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.cage.Cage;
import plugins.fmp.multitools.experiment.sequence.SequenceCamData;
import plugins.fmp.multitools.series.FlyDetect1;
import plugins.fmp.multitools.series.options.BuildSeriesOptions;
import plugins.fmp.multitools.tools.imageTransform.CanvasImageTransformOptions;
import plugins.fmp.multitools.tools.imageTransform.ImageTransformEnums;
import plugins.fmp.multitools.tools.overlay.OverlayFlyDetect1Preview;

public class Detect1Panel extends JPanel
		implements ChangeListener, ItemListener, PropertyChangeListener, PopupMenuListener, ViewerListener {
	/**
	 * 
	 */
	private static final long serialVersionUID = 6066671006689527651L;

	private static final ImageTransformEnums[] SOURCE_TRANSFORMS = { ImageTransformEnums.R_RGB,
			ImageTransformEnums.G_RGB, ImageTransformEnums.B_RGB, ImageTransformEnums.R2MINUS_GB,
			ImageTransformEnums.G2MINUS_RB, ImageTransformEnums.B2MINUS_RG, ImageTransformEnums.NORM_BRMINUSG,
			ImageTransformEnums.RGB, ImageTransformEnums.H_HSB, ImageTransformEnums.S_HSB, ImageTransformEnums.B_HSB };

	private static final ImageTransformEnums[] BACKGROUND_TRANSFORMS = { ImageTransformEnums.NONE,
			ImageTransformEnums.SUBTRACT_TM1, ImageTransformEnums.SUBTRACT_T0 };

	private MultiSPOTS parent0 = null;
	private String detectString = "Detect...";
	private JButton startComputationButton = new JButton(detectString);
	private JToggleButton viewButton = new JToggleButton("View");
	private JSpinner nFliesPresentSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 255, 1));

	JComboBox<ImageTransformEnums> transformComboBox = new JComboBox<>(
			new ImageTransformEnums[] { ImageTransformEnums.R_RGB, ImageTransformEnums.G_RGB, ImageTransformEnums.B_RGB,
					ImageTransformEnums.R2MINUS_GB, ImageTransformEnums.G2MINUS_RB, ImageTransformEnums.B2MINUS_RG,
					ImageTransformEnums.NORM_BRMINUSG, ImageTransformEnums.RGB, ImageTransformEnums.H_HSB,
					ImageTransformEnums.S_HSB, ImageTransformEnums.B_HSB });

	private JComboBox<ImageTransformEnums> backgroundComboBox = new JComboBox<>(new ImageTransformEnums[] {
			ImageTransformEnums.NONE, ImageTransformEnums.SUBTRACT_TM1, ImageTransformEnums.SUBTRACT_T0 });

	private JComboBox<String> allCagesComboBox = new JComboBox<String>(new String[] { "all cages" });
	private JSpinner thresholdSpinner = new JSpinner(new SpinnerNumberModel(60, 0, 255, 1));
	private JSpinner jitterTextField = new JSpinner(new SpinnerNumberModel(5, 0, 1000, 1));
	private JSpinner objectLowsizeSpinner = new JSpinner(new SpinnerNumberModel(50, 0, 9999, 1));
	private JSpinner objectUpsizeSpinner = new JSpinner(new SpinnerNumberModel(500, 0, 9999, 1));
	private JCheckBox objectLowsizeCheckBox = new JCheckBox("object > ");
	private JCheckBox objectUpsizeCheckBox = new JCheckBox("object < ");
	private JCheckBox limitRatioCheckBox = new JCheckBox("length/width<");
	private JCheckBox jitterCheckBox = new JCheckBox("jitter<= ");
	private JSpinner limitRatioSpinner = new JSpinner(new SpinnerNumberModel(4, 0, 1000, 1));

	private JCheckBox whiteObjectCheckBox = new JCheckBox("white object");
	private JCheckBox excludeSpotBlobsCheckBox = new JCheckBox("ignore blobs on spots", false);
	JCheckBox overlayCheckBox = new JCheckBox("overlay");
	private JCheckBox allCheckBox = new JCheckBox("ALL (current to last)", false);

	private OverlayFlyDetect1Preview overlayFlyDetect1Preview = null;
	private FlyDetect1 flyDetect1 = null;
	private Viewer viewListenerViewer = null;

	// -----------------------------------------------------

	void init(GridLayout capLayout, MultiSPOTS parent0) {
		setLayout(capLayout);
		this.parent0 = parent0;

		FlowLayout flowLayout = new FlowLayout(FlowLayout.LEFT);
		flowLayout.setVgap(0);

		JPanel panel1 = new JPanel(flowLayout);
		panel1.add(startComputationButton);
		panel1.add(viewButton);
		panel1.add(allCagesComboBox);
		panel1.add(allCheckBox);
		panel1.add(new JLabel("n flies "));
		panel1.add(nFliesPresentSpinner);
		add(panel1);

		allCagesComboBox.addPopupMenuListener(this);

		JPanel panel2 = new JPanel(flowLayout);
		transformComboBox.setSelectedIndex(1);
		panel2.add(new JLabel("source ", SwingConstants.RIGHT));
		panel2.add(transformComboBox);
		panel2.add(new JLabel("bkgnd ", SwingConstants.RIGHT));
		panel2.add(backgroundComboBox);
		panel2.add(new JLabel("threshold ", SwingConstants.RIGHT));
		panel2.add(thresholdSpinner);
		add(panel2);

		objectLowsizeCheckBox.setSelected(true);
		objectUpsizeCheckBox.setSelected(true);
		objectLowsizeCheckBox.setHorizontalAlignment(SwingConstants.RIGHT);
		objectUpsizeCheckBox.setHorizontalAlignment(SwingConstants.RIGHT);
		JPanel panel3 = new JPanel(flowLayout);
		panel3.add(objectLowsizeCheckBox);
		panel3.add(objectLowsizeSpinner);
		panel3.add(objectUpsizeCheckBox);
		panel3.add(objectUpsizeSpinner);
		panel3.add(whiteObjectCheckBox);
		add(panel3);

		limitRatioCheckBox.setSelected(true);
		jitterCheckBox.setSelected(false);
		limitRatioSpinner.setEnabled(limitRatioCheckBox.isSelected());
		jitterTextField.setEnabled(jitterCheckBox.isSelected());

		JPanel panel4 = new JPanel(flowLayout);
		panel4.add(limitRatioCheckBox);
		panel4.add(limitRatioSpinner);
		panel4.add(jitterCheckBox);
		panel4.add(jitterTextField);
		panel4.add(excludeSpotBlobsCheckBox);
		panel4.add(overlayCheckBox);
		add(panel4);

		limitRatioCheckBox.addItemListener(e -> {
			limitRatioSpinner.setEnabled(limitRatioCheckBox.isSelected());
		});
		jitterCheckBox.addItemListener(e -> {
			jitterTextField.setEnabled(jitterCheckBox.isSelected());
		});

		defineActionListeners();
		thresholdSpinner.addChangeListener(this);
		transformComboBox.addItemListener(this);
		backgroundComboBox.addItemListener(this);

		ChangeListener refreshListener = e -> refreshFlyDetectOverlay();
		objectLowsizeCheckBox.addItemListener(e -> refreshFlyDetectOverlay());
		objectUpsizeCheckBox.addItemListener(e -> refreshFlyDetectOverlay());
		limitRatioCheckBox.addItemListener(e -> {
			limitRatioSpinner.setEnabled(limitRatioCheckBox.isSelected());
			refreshFlyDetectOverlay();
		});
		jitterCheckBox.addItemListener(e -> {
			jitterTextField.setEnabled(jitterCheckBox.isSelected());
			refreshFlyDetectOverlay();
		});
		excludeSpotBlobsCheckBox.addItemListener(e -> refreshFlyDetectOverlay());
		whiteObjectCheckBox.addItemListener(e -> refreshFlyDetectOverlay());
		objectLowsizeSpinner.addChangeListener(refreshListener);
		objectUpsizeSpinner.addChangeListener(refreshListener);
		limitRatioSpinner.addChangeListener(refreshListener);
		jitterTextField.addChangeListener(refreshListener);
		nFliesPresentSpinner.addChangeListener(refreshListener);

		overlayCheckBox.setEnabled(false);
	}

	private void defineActionListeners() {
		viewButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp == null)
					return;
				if (viewButton.isSelected()) {
					syncViewTransforms(exp);
					if (overlayCheckBox.isSelected())
						updateOverlay(exp);
				} else {
					detachViewListener();
					removeOverlay(exp);
					overlayCheckBox.setSelected(false);
					Canvas2D_3Transforms canvas = getCamDataCanvas(exp);
					if (canvas != null) {
						canvas.setTransformStep1Index(0);
						canvas.setTransformStep2Index(0);
					}
				}
				overlayCheckBox.setEnabled(viewButton.isSelected());
			}
		});

		transformComboBox.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp != null && viewButton.isSelected())
					syncViewTransforms(exp);
				refreshFlyDetectOverlay();
			}
		});

		backgroundComboBox.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp != null && viewButton.isSelected())
					syncViewTransforms(exp);
				refreshFlyDetectOverlay();
			}
		});

		overlayCheckBox.addItemListener(new ItemListener() {
			public void itemStateChanged(ItemEvent e) {
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp != null) {
					if (overlayCheckBox.isSelected()) {
						if (!viewButton.isSelected()) {
							viewButton.setSelected(true);
							syncViewTransforms(exp);
						}
						updateOverlay(exp);
					} else
						removeOverlay(exp);
				}
			}
		});

		startComputationButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				if (startComputationButton.getText().equals(detectString))
					startComputation();
				else
					stopComputation();
			}
		});

		allCheckBox.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Color color = Color.BLACK;
				if (allCheckBox.isSelected())
					color = Color.RED;
				allCheckBox.setForeground(color);
				startComputationButton.setForeground(color);
			}
		});
	}

	public void updateOverlay(Experiment exp) {
		if (exp == null)
			return;
		SequenceCamData seqCamData = exp.getSeqCamData();
		if (seqCamData == null || seqCamData.getSequence() == null)
			return;
		if (overlayFlyDetect1Preview == null)
			overlayFlyDetect1Preview = new OverlayFlyDetect1Preview(seqCamData.getSequence());
		else {
			seqCamData.getSequence().removeOverlay(overlayFlyDetect1Preview);
			overlayFlyDetect1Preview.setSequence(seqCamData.getSequence());
		}
		seqCamData.getSequence().addOverlay(overlayFlyDetect1Preview);
		int threshold = (int) thresholdSpinner.getValue();
		exp.getCages().setDetect_threshold(threshold);
		overlayFlyDetect1Preview.setPreviewState(exp, buildPreviewOptions(exp));
		overlayFlyDetect1Preview.painterChanged();
	}

	public void removeOverlay(Experiment exp) {
		if (exp.getSeqCamData() != null && exp.getSeqCamData().getSequence() != null
				&& overlayFlyDetect1Preview != null)
			exp.getSeqCamData().getSequence().removeOverlay(overlayFlyDetect1Preview);
	}

	@Override
	public void stateChanged(ChangeEvent e) {
		if (e.getSource() == thresholdSpinner) {
			Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
			if (exp != null) {
				exp.getCages().setDetect_threshold((int) thresholdSpinner.getValue());
				refreshFlyDetectOverlay();
			}
		}
	}

	void refreshFlyDetectOverlay() {
		if (overlayFlyDetect1Preview == null)
			return;
		if (!viewButton.isSelected() || !overlayCheckBox.isSelected())
			return;

		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		if (exp == null || exp.getSeqCamData() == null || exp.getSeqCamData().getSequence() == null)
			return;

		int threshold = (int) thresholdSpinner.getValue();
		exp.getCages().setDetect_threshold(threshold);
		overlayFlyDetect1Preview.setPreviewState(exp, buildPreviewOptions(exp));
		overlayFlyDetect1Preview.painterChanged();
		exp.getSeqCamData().getSequence().overlayChanged(overlayFlyDetect1Preview);
		exp.getSeqCamData().getSequence().dataChanged();
	}

	protected Canvas2D_3Transforms getCamDataCanvas(Experiment exp) {
		if (exp == null || exp.getSeqCamData() == null || exp.getSeqCamData().getSequence() == null)
			return null;
		Viewer v = exp.getSeqCamData().getSequence().getFirstViewer();
		if (v == null)
			return null;
		IcyCanvas canvas = v.getCanvas();
		if (canvas instanceof Canvas2D_3Transforms)
			return (Canvas2D_3Transforms) canvas;
		return null;
	}

	private void syncViewTransforms(Experiment exp) {
		Canvas2D_3Transforms canvas = getCamDataCanvas(exp);
		if (canvas == null)
			return;

		canvas.updateTransformsStep1(BACKGROUND_TRANSFORMS);
		canvas.updateTransformsStep2(SOURCE_TRANSFORMS);

		ImageTransformEnums bg = (ImageTransformEnums) backgroundComboBox.getSelectedItem();
		ImageTransformEnums src = (ImageTransformEnums) transformComboBox.getSelectedItem();
		if (bg != null)
			canvas.setTransformStep1(bg, null);
		if (src != null)
			canvas.setTransformStep2(src, null);

		updateCanvasBackgroundForCurrentFrame(exp, canvas);
		attachViewListener(exp);
		canvas.refresh();
	}

	private void updateCanvasBackgroundForCurrentFrame(Experiment exp, Canvas2D_3Transforms canvas) {
		if (exp == null || canvas == null || exp.getSeqCamData() == null)
			return;
		int t = exp.getSeqCamData().getCurrentFrame();
		Viewer v = exp.getSeqCamData().getSequence().getFirstViewer();
		if (v != null)
			t = v.getPositionT();

		CanvasImageTransformOptions opts = canvas.getOptionsStep1();
		ImageTransformEnums bg = (ImageTransformEnums) backgroundComboBox.getSelectedItem();
		if (bg == null)
			bg = ImageTransformEnums.NONE;
		opts.transformOption = bg;
		FlyDetect1.fillFlyDetectBackgroundOptions(exp, t, opts);
	}

	private void attachViewListener(Experiment exp) {
		if (exp == null || exp.getSeqCamData() == null || exp.getSeqCamData().getSequence() == null)
			return;
		Viewer v = exp.getSeqCamData().getSequence().getFirstViewer();
		if (v == null)
			return;
		if (viewListenerViewer != null && viewListenerViewer != v)
			viewListenerViewer.removeListener(this);
		v.removeListener(this);
		v.addListener(this);
		viewListenerViewer = v;
	}

	private void detachViewListener() {
		if (viewListenerViewer != null) {
			viewListenerViewer.removeListener(this);
			viewListenerViewer = null;
		}
	}

	@Override
	public void viewerChanged(ViewerEvent event) {
		if (event.getType() != ViewerEventType.POSITION_CHANGED || event.getDim() != DimensionId.T)
			return;
		if (!viewButton.isSelected())
			return;
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		if (exp == null)
			return;
		Canvas2D_3Transforms canvas = getCamDataCanvas(exp);
		if (canvas != null) {
			updateCanvasBackgroundForCurrentFrame(exp, canvas);
			canvas.refresh();
		}
		refreshFlyDetectOverlay();
	}

	@Override
	public void viewerClosed(Viewer viewer) {
		if (viewer == viewListenerViewer)
			viewListenerViewer = null;
	}

	private void applyDetect1FlyOptions(BuildSeriesOptions options, Experiment exp) {
		options.btrackWhite = whiteObjectCheckBox.isSelected();
		options.blimitLow = objectLowsizeCheckBox.isSelected();
		options.blimitUp = objectUpsizeCheckBox.isSelected();
		options.blimitRatio = limitRatioCheckBox.isSelected();
		options.bjitter = jitterCheckBox.isSelected();
		options.limitLow = (int) objectLowsizeSpinner.getValue();
		options.limitUp = (int) objectUpsizeSpinner.getValue();
		options.limitRatio = (int) limitRatioSpinner.getValue();
		options.jitter = (int) jitterTextField.getValue();
		options.videoChannel = 0;
		options.flyDetectSourceTransform = (ImageTransformEnums) transformComboBox.getSelectedItem();
		options.flyDetectBackgroundTransform = (ImageTransformEnums) backgroundComboBox.getSelectedItem();
		options.threshold = (int) thresholdSpinner.getValue();
		options.nFliesPresent = (int) nFliesPresentSpinner.getValue();
		options.blimitMaxBlobsPerCage = true;
		options.bexcludeSpotBlobs = excludeSpotBlobsCheckBox.isSelected();
		if (exp != null) {
			options.binSubDirectory = exp.getBinSubDirectory();
		}
		options.detectCage = allCagesComboBox.getSelectedIndex() - 1;
	}

	private BuildSeriesOptions buildPreviewOptions(Experiment exp) {
		BuildSeriesOptions options = new BuildSeriesOptions();
		applyDetect1FlyOptions(options, exp);
		return options;
	}

	private BuildSeriesOptions initTrackParameters(Experiment exp) {
		BuildSeriesOptions options = new BuildSeriesOptions();
		options.expList = parent0.expListComboLazy;
		options.expList.index0 = parent0.expListComboLazy.getSelectedIndex();
		if (allCheckBox.isSelected())
			options.expList.index1 = options.expList.getItemCount() - 1;
		else
			options.expList.index1 = parent0.expListComboLazy.getSelectedIndex();

		applyDetect1FlyOptions(options, exp);

		options.isFrameFixed = parent0.dlgExcel.excelOptionsPanel.getIsFixedFrame();
		options.t_Ms_First = parent0.dlgExcel.excelOptionsPanel.getStartMs();
		options.t_Ms_Last = parent0.dlgExcel.excelOptionsPanel.getEndMs();
		options.t_Ms_BinDuration = parent0.dlgExcel.excelOptionsPanel.getBinMs();

		options.parent0Rect = parent0.mainFrame.getBoundsInternal();

		return options;
	}

	void startComputation() {
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		if (exp == null)
			return;
		parent0.dlgBrowse.browsePanel.closeViewsForCurrentExperiment(exp);

		flyDetect1 = new FlyDetect1();
		flyDetect1.options = initTrackParameters(exp);
		flyDetect1.stopFlag = false;
		flyDetect1.buildBackground = false;
		flyDetect1.detectFlies = true;
		flyDetect1.addPropertyChangeListener(this);
		flyDetect1.execute();
		startComputationButton.setText("STOP");
	}

	private void stopComputation() {
		if (flyDetect1 != null && !flyDetect1.stopFlag) {
			flyDetect1.stopFlag = true;
		}
	}

	@Override
	public void propertyChange(PropertyChangeEvent evt) {
		if (StringUtil.equals("thread_ended", evt.getPropertyName())) {
			startComputationButton.setText(detectString);
//			parent0.paneKymos.tabDisplay.selectKymographImage(parent0.paneKymos.tabDisplay.indexImagesCombo);
//			parent0.paneKymos.tabDisplay.indexImagesCombo = -1;
		}
	}

	@Override
	public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
		int nitems = 1;
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		if (exp != null)
			nitems = exp.getCages().getCageList().size() + 1;
		if (allCagesComboBox.getItemCount() != nitems) {
			allCagesComboBox.removeAllItems();
			allCagesComboBox.addItem("all cages");
			if (exp != null) {
				for (Cage cage : exp.getCages().getCageList()) {
					allCagesComboBox.addItem(cage.getCageNumberFromRoiName());
				}
			}
		}
	}

	@Override
	public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
		// TODO Auto-generated method stub
	}

	@Override
	public void popupMenuCanceled(PopupMenuEvent e) {
		// TODO Auto-generated method stub

	}

	@Override
	public void itemStateChanged(ItemEvent e) {
		if (e.getStateChange() == ItemEvent.SELECTED) {
			Object source = e.getSource();
			if (source instanceof JComboBox) {
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp != null && viewButton.isSelected())
					syncViewTransforms(exp);
				refreshFlyDetectOverlay();
			}
		}
	}

}
