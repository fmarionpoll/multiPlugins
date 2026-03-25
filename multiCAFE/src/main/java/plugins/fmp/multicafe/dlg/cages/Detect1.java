package plugins.fmp.multicafe.dlg.cages;

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
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;

import icy.gui.viewer.Viewer;
import icy.util.StringUtil;
import plugins.fmp.multicafe.MultiCAFE;
import plugins.fmp.multicafe.canvas2D.Canvas2DWithTransforms;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.cage.Cage;
import plugins.fmp.multitools.experiment.sequence.SequenceCamData;
import plugins.fmp.multitools.series.FlyDetect1;
import plugins.fmp.multitools.series.options.BuildSeriesOptions;
import plugins.fmp.multitools.tools.imageTransform.ImageTransformEnums;
import plugins.fmp.multitools.tools.overlay.OverlayFlyDetect1Preview;

public class Detect1 extends JPanel implements ChangeListener, ItemListener, PropertyChangeListener, PopupMenuListener {
	/**
	 * 
	 */
	private static final long serialVersionUID = 6066671006689527651L;

	private MultiCAFE parent0 = null;
	private String detectString = "Detect...";
	private JButton detectButton = new JButton(detectString);
	private JToggleButton viewButton = new JToggleButton("View", false);
	private JSpinner thresholdSpinner = new JSpinner(new SpinnerNumberModel(60, 0, 255, 1));
	private JComboBox<String> directionComboBox = new JComboBox<String>(
			new String[] { " threshold >", " threshold <" });

	public JCheckBox overlayCheckBox = new JCheckBox("overlay", false);
	public JComboBox<ImageTransformEnums> transformComboBox = new JComboBox<>(new ImageTransformEnums[] { //
			ImageTransformEnums.R_RGB, ImageTransformEnums.G_RGB, ImageTransformEnums.B_RGB,
			ImageTransformEnums.R2MINUS_GB, ImageTransformEnums.G2MINUS_RB, ImageTransformEnums.B2MINUS_RG,
			ImageTransformEnums.NORM_BRMINUSG, ImageTransformEnums.RGB, ImageTransformEnums.H_HSB,
			ImageTransformEnums.S_HSB, ImageTransformEnums.B_HSB });

	private JComboBox<ImageTransformEnums> backgroundComboBox = new JComboBox<>(new ImageTransformEnums[] {
			ImageTransformEnums.NONE, ImageTransformEnums.SUBTRACT_TM1, ImageTransformEnums.SUBTRACT_T0 });

	private JComboBox<String> cagesComboBox = new JComboBox<String>(new String[] { "all cages" });

	private JCheckBox objectLowsizeCheckBox = new JCheckBox("object > ");
	private JSpinner objectLowsizeSpinner = new JSpinner(new SpinnerNumberModel(50, 0, 9999, 1));
	private JCheckBox objectUpsizeCheckBox = new JCheckBox("object < ");
	private JSpinner objectUpsizeSpinner = new JSpinner(new SpinnerNumberModel(500, 0, 9999, 1));
	private JCheckBox limitRatioCheckBox = new JCheckBox("ratio l/w<");
	private JSpinner limitRatioSpinner = new JSpinner(new SpinnerNumberModel(4, 0, 1000, 1));
	private JCheckBox jitterCheckBox = new JCheckBox("jitter<= ");
	private JSpinner jitterTextField = new JSpinner(new SpinnerNumberModel(5, 0, 1000, 1));
	private JCheckBox nFliesCheckBox = new JCheckBox("nflies<");
	private JSpinner nFliesSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 9999, 1));

	private JCheckBox allCheckBox = new JCheckBox("ALL (current to last)", false);

	private OverlayFlyDetect1Preview overlayFlyDetect1Preview = null;
	private FlyDetect1 flyDetect1 = null;

	// -----------------------------------------------------

	void init(GridLayout capLayout, MultiCAFE parent0) {
		setLayout(capLayout);
		this.parent0 = parent0;

		FlowLayout flowLayout = new FlowLayout(FlowLayout.LEFT);
		flowLayout.setVgap(0);

		JPanel panel1 = new JPanel(flowLayout);
		panel1.add(detectButton);
		panel1.add(cagesComboBox);
		panel1.add(allCheckBox);
		panel1.add(viewButton);
		panel1.add(overlayCheckBox);
		add(panel1);

		cagesComboBox.addPopupMenuListener(this);

		JPanel panel2 = new JPanel(flowLayout);
		transformComboBox.setSelectedIndex(1);
		panel2.add(new JLabel("source "));
		panel2.add(transformComboBox);
		panel2.add(new JLabel("bkgnd "));
		panel2.add(backgroundComboBox);
		panel2.add(directionComboBox);
		((JLabel) directionComboBox.getRenderer()).setHorizontalAlignment(JLabel.RIGHT);
		panel2.add(thresholdSpinner);
		add(panel2);

		JPanel panel3 = new JPanel(flowLayout);
		panel3.add(objectLowsizeCheckBox);
		panel3.add(objectLowsizeSpinner);
		panel3.add(objectUpsizeCheckBox);
		panel3.add(objectUpsizeSpinner);
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
		nFliesCheckBox.setSelected(true);
		nFliesSpinner.setEnabled(nFliesCheckBox.isSelected());

		panel4.add(nFliesCheckBox);
		panel4.add(nFliesSpinner);
		add(panel4);

		defineListeners();

		transformComboBox.addItemListener(this);
		backgroundComboBox.addItemListener(this);
	}

	private void defineListeners() {

		overlayCheckBox.addItemListener(new ItemListener() {
			public void itemStateChanged(ItemEvent e) {
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp != null) {
					if (overlayCheckBox.isSelected())
						updateOverlay(exp);
					else
						removeOverlay(exp);
				}
			}
		});

		thresholdSpinner.addChangeListener(new ChangeListener() {
			public void stateChanged(ChangeEvent e) {
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp != null)
					exp.getCages().setDetect_threshold((int) thresholdSpinner.getValue());
				refreshFlyDetectOverlay();
			}
		});

		detectButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				if (detectButton.getText().equals(detectString))
					startComputation();
				else
					stopComputation();
			}
		});

		viewButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp == null)
					return;

				if (viewButton.isSelected()) {
					Canvas2DWithTransforms canvas = getCamDataCanvas(exp);
					if (canvas != null) {
						int index = transformComboBox.getSelectedIndex();
						canvas.transformsCombo1.setSelectedIndex(index + 1);
						refreshFlyDetectOverlay();
					}
				} else {
					removeOverlay(exp);
					overlayCheckBox.setSelected(false);
					Canvas2DWithTransforms canvas = getCamDataCanvas(exp);
					if (canvas != null)
						canvas.transformsCombo1.setSelectedIndex(0);
				}
				overlayCheckBox.setEnabled(viewButton.isSelected());
			}

		});

		transformComboBox.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp != null && exp.getSeqCamData() != null) {
					Canvas2DWithTransforms canvas = getCamDataCanvas(exp);
					if (canvas != null) {
						int index = transformComboBox.getSelectedIndex();
						canvas.transformsCombo1.setSelectedIndex(index + 1);
						refreshFlyDetectOverlay();
					}
				}
			}
		});

		allCheckBox.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Color color = Color.BLACK;
				if (allCheckBox.isSelected())
					color = Color.RED;
				allCheckBox.setForeground(color);
				detectButton.setForeground(color);
			}
		});

		directionComboBox.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				refreshFlyDetectOverlay();
			}
		});

		backgroundComboBox.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				refreshFlyDetectOverlay();
			}
		});

		cagesComboBox.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				refreshFlyDetectOverlay();
			}
		});

		objectLowsizeCheckBox.addItemListener(new ItemListener() {
			@Override
			public void itemStateChanged(ItemEvent e) {
				refreshFlyDetectOverlay();
			}
		});
		objectUpsizeCheckBox.addItemListener(new ItemListener() {
			@Override
			public void itemStateChanged(ItemEvent e) {
				refreshFlyDetectOverlay();
			}
		});

		limitRatioCheckBox.addItemListener(new ItemListener() {
			@Override
			public void itemStateChanged(ItemEvent e) {
				limitRatioSpinner.setEnabled(limitRatioCheckBox.isSelected());
				refreshFlyDetectOverlay();
			}
		});
		jitterCheckBox.addItemListener(new ItemListener() {
			@Override
			public void itemStateChanged(ItemEvent e) {
				jitterTextField.setEnabled(jitterCheckBox.isSelected());
				refreshFlyDetectOverlay();
			}
		});

		nFliesCheckBox.addItemListener(new ItemListener() {
			@Override
			public void itemStateChanged(ItemEvent e) {
				nFliesSpinner.setEnabled(nFliesCheckBox.isSelected());
				refreshFlyDetectOverlay();
			}
		});

		ChangeListener filterSpinnerListener = new ChangeListener() {
			@Override
			public void stateChanged(ChangeEvent e) {
				refreshFlyDetectOverlay();
			}
		};
		objectLowsizeSpinner.addChangeListener(filterSpinnerListener);
		objectUpsizeSpinner.addChangeListener(filterSpinnerListener);
		limitRatioSpinner.addChangeListener(filterSpinnerListener);
		jitterTextField.addChangeListener(filterSpinnerListener);
		nFliesSpinner.addChangeListener(filterSpinnerListener);
	}

	public void updateOverlay(Experiment exp) {
		SequenceCamData seqCamData = exp.getSeqCamData();
		if (seqCamData == null)
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
				updateOverlay(exp);
			}
		}
	}

	private void applyDetect1FlyOptions(BuildSeriesOptions options, Experiment exp) {
		options.btrackWhite = (directionComboBox.getSelectedIndex() == 0);
		options.blimitLow = objectLowsizeCheckBox.isSelected();
		options.blimitUp = objectUpsizeCheckBox.isSelected();
		options.blimitRatio = limitRatioCheckBox.isSelected();
		options.bjitter = jitterCheckBox.isSelected();
		options.limitLow = (int) objectLowsizeSpinner.getValue();
		options.limitUp = (int) objectUpsizeSpinner.getValue();
		options.limitRatio = ((Number)limitRatioSpinner.getValue()).doubleValue();
		options.jitter = (int) jitterTextField.getValue();
		options.videoChannel = 0;
		options.flyDetectSourceTransform = (ImageTransformEnums) transformComboBox.getSelectedItem();
		options.flyDetectBackgroundTransform = (ImageTransformEnums) backgroundComboBox.getSelectedItem();
		options.threshold = (int) thresholdSpinner.getValue();
		options.blimitMaxBlobsPerCage = nFliesCheckBox.isSelected();
		options.nFliesPresent = Math.max(1, (int) nFliesSpinner.getValue());
		options.binSubDirectory = exp.getBinSubDirectory();
		options.detectCage = cagesComboBox.getSelectedIndex() - 1;
	}

	private BuildSeriesOptions buildPreviewOptions(Experiment exp) {
		BuildSeriesOptions o = new BuildSeriesOptions();
		applyDetect1FlyOptions(o, exp);
		return o;
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

		options.isFrameFixed = parent0.paneExcel.tabCommonOptions.getIsFixedFrame();
		options.t_Ms_First = parent0.paneExcel.tabCommonOptions.getStartMs();
		options.t_Ms_Last = parent0.paneExcel.tabCommonOptions.getEndMs();
		options.t_Ms_BinDuration = parent0.paneExcel.tabCommonOptions.getBinMs();

		options.parent0Rect = parent0.mainFrame.getBoundsInternal();

		return options;
	}

	void startComputation() {
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		if (exp == null)
			return;
		parent0.paneBrowse.panelLoadSave.closeViewsForCurrentExperiment(exp);

		flyDetect1 = new FlyDetect1();
		flyDetect1.options = initTrackParameters(exp);
		flyDetect1.stopFlag = false;
		flyDetect1.buildBackground = false;
		flyDetect1.detectFlies = true;
		flyDetect1.addPropertyChangeListener(this);
		flyDetect1.execute();
		detectButton.setText("STOP");
	}

	private void stopComputation() {
		if (flyDetect1 != null && !flyDetect1.stopFlag) {
			flyDetect1.stopFlag = true;
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

	protected Canvas2DWithTransforms getCamDataCanvas(Experiment exp) {
		if (exp.getSeqCamData() == null || exp.getSeqCamData().getSequence() == null)
			return null;

		Viewer v = exp.getSeqCamData().getSequence().getFirstViewer();
		if (v == null)
			return null;
		return (Canvas2DWithTransforms) v.getCanvas();
	}

	@Override
	public void propertyChange(PropertyChangeEvent evt) {
		if (StringUtil.equals("thread_ended", evt.getPropertyName())) {
			detectButton.setText(detectString);
			parent0.paneKymos.tabIntervals.selectKymographImage(parent0.paneKymos.tabIntervals.indexImagesCombo);
			parent0.paneKymos.tabIntervals.indexImagesCombo = -1;
		}
	}

	@Override
	public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
		int nitems = 1;
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		if (exp != null)
			nitems = exp.getCages().getCageList().size() + 1;
		if (cagesComboBox.getItemCount() != nitems) {
			cagesComboBox.removeAllItems();
			cagesComboBox.addItem("all cages");
			if (exp != null) {
				for (Cage cage : exp.getCages().getCageList()) {
					cagesComboBox.addItem(Integer.toString(cage.getCageID()));
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
				updateOverlay(exp);
			}
		}
	}

}
