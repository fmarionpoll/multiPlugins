package plugins.fmp.multiSPOTS96.dlg.e_flies;

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
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;

import icy.util.StringUtil;
import plugins.fmp.multiSPOTS96.MultiSPOTS96;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.cages.Cage;
import plugins.fmp.multitools.experiment.sequence.SequenceCamData;
import plugins.fmp.multitools.series.BuildSeriesOptions;
import plugins.fmp.multitools.series.DetectFlyUsingSimpleThreshold;
import plugins.fmp.multitools.tools.imageTransform.ImageTransformEnums;
import plugins.fmp.multitools.tools.overlay.OverlayThreshold;

public class Detect1 extends JPanel implements ChangeListener, ItemListener, PropertyChangeListener, PopupMenuListener {
	/**
	 * 
	 */
	private static final long serialVersionUID = 6066671006689527651L;

	private MultiSPOTS96 parent0 = null;
	private String detectString = "Detect...";
	private JButton startComputationButton = new JButton(detectString);
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
	private JSpinner limitRatioSpinner = new JSpinner(new SpinnerNumberModel(4, 0, 1000, 1));

	private JCheckBox whiteObjectCheckBox = new JCheckBox("white object");
	JCheckBox overlayCheckBox = new JCheckBox("overlay");
	private JCheckBox allCheckBox = new JCheckBox("ALL (current to last)", false);

	private OverlayThreshold overlayThreshold1 = null;
	private DetectFlyUsingSimpleThreshold flyDetect1 = null;

	// -----------------------------------------------------

	void init(GridLayout capLayout, MultiSPOTS96 parent0) {
		setLayout(capLayout);
		this.parent0 = parent0;

		FlowLayout flowLayout = new FlowLayout(FlowLayout.LEFT);
		flowLayout.setVgap(0);

		JPanel panel1 = new JPanel(flowLayout);
		panel1.add(startComputationButton);
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

		objectLowsizeCheckBox.setHorizontalAlignment(SwingConstants.RIGHT);
		objectUpsizeCheckBox.setHorizontalAlignment(SwingConstants.RIGHT);
		JPanel panel3 = new JPanel(flowLayout);
		panel3.add(objectLowsizeCheckBox);
		panel3.add(objectLowsizeSpinner);
		panel3.add(objectUpsizeCheckBox);
		panel3.add(objectUpsizeSpinner);
		panel3.add(whiteObjectCheckBox);
		add(panel3);

		JPanel panel4 = new JPanel(flowLayout);
		panel4.add(new JLabel("length/width<", SwingConstants.RIGHT));
		panel4.add(limitRatioSpinner);
		panel4.add(new JLabel("         jitter <= ", SwingConstants.RIGHT));
		panel4.add(jitterTextField);
		panel4.add(overlayCheckBox);
		add(panel4);

		defineActionListeners();
		thresholdSpinner.addChangeListener(this);
		transformComboBox.addItemListener(this);
	}

	private void defineActionListeners() {
		overlayCheckBox.addItemListener(new ItemListener() {
			public void itemStateChanged(ItemEvent e) {
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp != null) {
					if (overlayCheckBox.isSelected()) {
						if (overlayThreshold1 == null)
							overlayThreshold1 = new OverlayThreshold(exp.getSeqCamData().getSequence());
						exp.getSeqCamData().getSequence().addOverlay(overlayThreshold1);
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
		SequenceCamData seqCamData = exp.getSeqCamData();
		if (seqCamData == null)
			return;
		if (overlayThreshold1 == null)
			overlayThreshold1 = new OverlayThreshold(seqCamData.getSequence());
		else {
			seqCamData.getSequence().removeOverlay(overlayThreshold1);
			overlayThreshold1.setSequence(seqCamData.getSequence());
		}
		seqCamData.getSequence().addOverlay(overlayThreshold1);
		boolean ifGreater = true;
		ImageTransformEnums transformOp = (ImageTransformEnums) transformComboBox.getSelectedItem();
		overlayThreshold1.setThresholdSingle(exp.getCages().detect_threshold, transformOp, ifGreater);
		overlayThreshold1.painterChanged();
	}

	public void removeOverlay(Experiment exp) {
		if (exp.getSeqCamData() != null && exp.getSeqCamData().getSequence() != null)
			exp.getSeqCamData().getSequence().removeOverlay(overlayThreshold1);
	}

	@Override
	public void stateChanged(ChangeEvent e) {
		if (e.getSource() == thresholdSpinner) {
			Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
			if (exp != null) {
				exp.getCages().detect_threshold = (int) thresholdSpinner.getValue();
				updateOverlay(exp);
			}
		}
	}

	private BuildSeriesOptions initTrackParameters() {
		BuildSeriesOptions options = new BuildSeriesOptions();
		options.expList = parent0.expListComboLazy;
		options.expList.index0 = parent0.expListComboLazy.getSelectedIndex();
		if (allCheckBox.isSelected())
			options.expList.index1 = options.expList.getItemCount() - 1;
		else
			options.expList.index1 = parent0.expListComboLazy.getSelectedIndex();
//		parent0.paneKymos.tabDisplay.indexImagesCombo = parent0.paneKymos.tabDisplay.kymographsCombo.getSelectedIndex();

		options.btrackWhite = whiteObjectCheckBox.isSelected();
		options.blimitLow = objectLowsizeCheckBox.isSelected();
		options.blimitUp = objectUpsizeCheckBox.isSelected();
		options.limitLow = (int) objectLowsizeSpinner.getValue();
		options.limitUp = (int) objectUpsizeSpinner.getValue();
		options.limitRatio = (int) limitRatioSpinner.getValue();
		options.jitter = (int) jitterTextField.getValue();
		options.videoChannel = 0; // colorChannelComboBox.getSelectedIndex();
		options.transformop = (ImageTransformEnums) transformComboBox.getSelectedItem();
		options.nFliesPresent = (int) nFliesPresentSpinner.getValue();

		options.transformop = (ImageTransformEnums) backgroundComboBox.getSelectedItem();
		options.threshold = (int) thresholdSpinner.getValue();

		options.isFrameFixed = parent0.dlgExcel.tabCommonOptions.getIsFixedFrame();
		options.t_Ms_First = parent0.dlgExcel.tabCommonOptions.getStartMs();
		options.t_Ms_Last = parent0.dlgExcel.tabCommonOptions.getEndMs();
		options.t_Ms_BinDuration = parent0.dlgExcel.tabCommonOptions.getBinMs();

		options.parent0Rect = parent0.mainFrame.getBoundsInternal();
		options.detectCage = allCagesComboBox.getSelectedIndex() - 1;

		return options;
	}

	void startComputation() {
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		if (exp == null)
			return;
		parent0.dlgBrowse.loadSaveExperiment.closeViewsForCurrentExperiment(exp);

		flyDetect1 = new DetectFlyUsingSimpleThreshold();
		flyDetect1.options = initTrackParameters();
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
			nitems = exp.getCages().cagesList.size() + 1;
		if (allCagesComboBox.getItemCount() != nitems) {
			allCagesComboBox.removeAllItems();
			allCagesComboBox.addItem("all cages");
			for (Cage cage : exp.getCages().cagesList)
				allCagesComboBox.addItem(cage.getCageNumberFromRoiName());
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
