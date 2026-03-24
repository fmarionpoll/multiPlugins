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
import plugins.fmp.multitools.tools.overlay.OverlayThreshold;

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

	private JSpinner jitterTextField = new JSpinner(new SpinnerNumberModel(5, 0, 1000, 1));
	private JSpinner objectLowsizeSpinner = new JSpinner(new SpinnerNumberModel(50, 0, 9999, 1));
	private JSpinner objectUpsizeSpinner = new JSpinner(new SpinnerNumberModel(500, 0, 9999, 1));
	private JCheckBox objectLowsizeCheckBox = new JCheckBox("object > ");
	private JCheckBox objectUpsizeCheckBox = new JCheckBox("object < ");
	private JSpinner limitRatioSpinner = new JSpinner(new SpinnerNumberModel(4, 0, 1000, 1));

	private JCheckBox allCheckBox = new JCheckBox("ALL (current to last)", false);

	private OverlayThreshold overlayThreshold = null;
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

		JPanel panel4 = new JPanel(flowLayout);
		panel4.add(new JLabel("length/width<"));
		panel4.add(limitRatioSpinner);
		panel4.add(new JLabel("jitter <= "));
		panel4.add(jitterTextField);

		add(panel4);

		defineListeners();

		transformComboBox.addItemListener(this);
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
				updateOverlayThreshold();
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
						updateOverlayThreshold();
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
						updateOverlayThreshold();
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
				updateOverlayThreshold();
			}
		});
	}

	public void updateOverlay(Experiment exp) {
		SequenceCamData seqCamData = exp.getSeqCamData();
		if (seqCamData == null)
			return;
		if (overlayThreshold == null)
			overlayThreshold = new OverlayThreshold(seqCamData.getSequence());
		else {
			seqCamData.getSequence().removeOverlay(overlayThreshold);
			overlayThreshold.setSequence(seqCamData.getSequence());
		}
		seqCamData.getSequence().addOverlay(overlayThreshold);
		boolean ifGreater = false;
		ImageTransformEnums transformOp = (ImageTransformEnums) transformComboBox.getSelectedItem();
		overlayThreshold.setThresholdSingle(exp.getCages().getDetect_threshold(), transformOp, ifGreater);
		overlayThreshold.painterChanged();
	}

	public void removeOverlay(Experiment exp) {
		if (exp.getSeqCamData() != null && exp.getSeqCamData().getSequence() != null)
			exp.getSeqCamData().getSequence().removeOverlay(overlayThreshold);
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

	private BuildSeriesOptions initTrackParameters(Experiment exp) {
		BuildSeriesOptions options = new BuildSeriesOptions();
		options.expList = parent0.expListComboLazy;
		options.expList.index0 = parent0.expListComboLazy.getSelectedIndex();
		if (allCheckBox.isSelected())
			options.expList.index1 = options.expList.getItemCount() - 1;
		else
			options.expList.index1 = parent0.expListComboLazy.getSelectedIndex();

		options.btrackWhite = (directionComboBox.getSelectedIndex() == 1);
		options.blimitLow = objectLowsizeCheckBox.isSelected();
		options.blimitUp = objectUpsizeCheckBox.isSelected();
		options.limitLow = (int) objectLowsizeSpinner.getValue();
		options.limitUp = (int) objectUpsizeSpinner.getValue();
		options.limitRatio = (int) limitRatioSpinner.getValue();
		options.jitter = (int) jitterTextField.getValue();
		options.videoChannel = 0; // colorChannelComboBox.getSelectedIndex();
		options.transformop = (ImageTransformEnums) transformComboBox.getSelectedItem();
		options.nFliesPresent = 1;

		options.transformop = (ImageTransformEnums) backgroundComboBox.getSelectedItem();
		options.isFrameFixed = parent0.paneExcel.tabCommonOptions.getIsFixedFrame();
		options.t_Ms_First = parent0.paneExcel.tabCommonOptions.getStartMs();
		options.t_Ms_Last = parent0.paneExcel.tabCommonOptions.getEndMs();
		options.t_Ms_BinDuration = parent0.paneExcel.tabCommonOptions.getBinMs();

		options.parent0Rect = parent0.mainFrame.getBoundsInternal();
		options.binSubDirectory = exp.getBinSubDirectory();

		options.detectCage = cagesComboBox.getSelectedIndex() - 1;

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

	void updateOverlayThreshold() {
		if (overlayThreshold == null)
			return;

		if (viewButton.isSelected() && overlayCheckBox.isSelected()) {
			boolean ifGreater = (directionComboBox.getSelectedIndex() == 0);
			int threshold = (int) thresholdSpinner.getValue();
			ImageTransformEnums transform = (ImageTransformEnums) transformComboBox.getSelectedItem();
			overlayThreshold.setThresholdSingle(threshold, transform, ifGreater);

		} else {
			return;
		}

		overlayThreshold.painterChanged();
		if (overlayThreshold.getSequence() != null) {
			overlayThreshold.getSequence().overlayChanged(overlayThreshold);
			overlayThreshold.getSequence().dataChanged();
		}
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
