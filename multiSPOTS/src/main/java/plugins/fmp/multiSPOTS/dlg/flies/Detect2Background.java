package plugins.fmp.multiSPOTS.dlg.flies;

import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import icy.gui.dialog.MessageDialog;
import icy.gui.viewer.Viewer;
import icy.image.IcyBufferedImageUtil;
import icy.util.StringUtil;
import plugins.fmp.multiSPOTS.MultiSPOTS;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.sequence.SequenceCamData;
import plugins.fmp.multiSPOTS.series.BuildBackground;
import plugins.fmp.multiSPOTS.series.BuildSeriesOptions;
import plugins.fmp.multitools.tools.imageTransform.ImageTransformEnums;
import plugins.fmp.multitools.tools.overlay.OverlayThreshold;

public class Detect2Background extends JPanel implements ChangeListener, PropertyChangeListener {
	private static final long serialVersionUID = 1L;

	private MultiSPOTS parent0 = null;

	private String detectString = "Build background...";
	private JButton startComputationButton = new JButton(detectString);

	private JSpinner backgroundThresholdSpinner = new JSpinner(new SpinnerNumberModel(60, 0, 255, 1));
	private JSpinner backgroundNFramesSpinner = new JSpinner(new SpinnerNumberModel(20, 0, 255, 1));
	private JSpinner backgroundJitterSpinner = new JSpinner(new SpinnerNumberModel(1, 0, 255, 1));
	private JSpinner backgroundDeltaSpinner = new JSpinner(new SpinnerNumberModel(20, 0, 255, 1));

	// private JCheckBox viewsCheckBox = new JCheckBox("view ref img", true);
	private JButton loadButton = new JButton("Load...");
	private JButton saveButton = new JButton("Save...");
	private JCheckBox allCheckBox = new JCheckBox("ALL (current to last)", false);
	private JCheckBox overlayCheckBox = new JCheckBox("overlay");

	private BuildBackground buildBackground = null;
	private OverlayThreshold ov = null;

	// ----------------------------------------------------

	void init(GridLayout capLayout, MultiSPOTS parent0) {
		setLayout(capLayout);
		this.parent0 = parent0;

		FlowLayout flowLayout = new FlowLayout(FlowLayout.LEFT);
		flowLayout.setVgap(0);

		JPanel panel1 = new JPanel(flowLayout);
		panel1.add(startComputationButton);
		panel1.add(allCheckBox);
		panel1.add(loadButton);
		panel1.add(saveButton);
		add(panel1);

		JPanel panel2 = new JPanel(flowLayout);
		panel2.add(new JLabel("threshold/fly "));
		panel2.add(backgroundThresholdSpinner);
		panel2.add(new JLabel("over n frames "));
		panel2.add(backgroundNFramesSpinner);
		panel2.add(overlayCheckBox);
		panel2.validate();
		add(panel2);

		JPanel panel3 = new JPanel(flowLayout);
		panel3.add(new JLabel("min delta fly/background "));
		panel3.add(backgroundDeltaSpinner);
		panel3.add(new JLabel("jitter around fly "));
		panel3.add(backgroundJitterSpinner);
		add(panel3);

//		JPanel panel4 = new JPanel(flowLayout);
//		panel4.add(loadButton);
//		panel4.add(saveButton);
//		add(panel4);

		defineActionListeners();

		backgroundThresholdSpinner.addChangeListener(this);
	}

	private void defineActionListeners() {
		startComputationButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				if (startComputationButton.getText().equals(detectString))
					startComputation();
				else
					stopComputation();
			}
		});

		saveButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) parent0.expListCombo.getSelectedItem();
				if (exp != null)
					exp.saveReferenceImage(exp.seqCamData.refImage);
			}
		});

		loadButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				loadBackground();
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

		overlayCheckBox.addItemListener(new ItemListener() {
			public void itemStateChanged(ItemEvent e) {
				Experiment exp = (Experiment) parent0.expListCombo.getSelectedItem();
				if (exp != null) {
					if (overlayCheckBox.isSelected()) {
						if (ov == null)
							ov = new OverlayThreshold(exp.seqCamData.seq);
						exp.seqCamData.seq.addOverlay(ov);
						updateOverlay(exp);
					} else
						removeOverlay(exp);
				}
			}
		});
	}

	@Override
	public void stateChanged(ChangeEvent e) {
		if (e.getSource() == backgroundThresholdSpinner) {
			Experiment exp = (Experiment) parent0.expListCombo.getSelectedItem();
			if (!overlayCheckBox.isSelected())
				overlayCheckBox.setSelected(true);
			if (exp != null)
				updateOverlay(exp);
		}
	}

	void loadBackground() {
		Experiment exp = (Experiment) parent0.expListCombo.getSelectedItem();
		if (exp != null) {
			boolean flag = exp.loadReferenceImage();
			if (flag) {
				Viewer v = new Viewer(exp.seqReference, true);
				Rectangle rectv = exp.seqCamData.seq.getFirstViewer().getBoundsInternal();
				v.setBounds(rectv);
			} else {
				MessageDialog.showDialog("Reference file not found on disk", MessageDialog.ERROR_MESSAGE);
			}
		}
	}

	private void updateOverlay(Experiment exp) {
		SequenceCamData seqCamData = exp.seqCamData;
		if (seqCamData == null)
			return;
		if (ov == null) {
			ov = new OverlayThreshold(seqCamData.seq);
			int t = exp.seqCamData.currentFrame;
			exp.seqCamData.refImage = IcyBufferedImageUtil.getCopy(exp.seqCamData.getSeqImage(t, 0));
		} else {
			seqCamData.seq.removeOverlay(ov);
			ov.setSequence(seqCamData.seq);
		}
		ov.setReferenceImage(exp.seqCamData.refImage);
		seqCamData.seq.addOverlay(ov);

		boolean ifGreater = true;
		ImageTransformEnums transformOp = ImageTransformEnums.NONE; // SUBTRACT; //SUBTRACT_REF;
		int threshold = (int) backgroundThresholdSpinner.getValue();
		ov.setThresholdSingle(threshold, transformOp, ifGreater);
		ov.painterChanged();
	}

	private void removeOverlay(Experiment exp) {
		if (exp.seqCamData != null && exp.seqCamData.seq != null)
			exp.seqCamData.seq.removeOverlay(ov);
	}

	private BuildSeriesOptions initTrackParameters() {
		BuildSeriesOptions options = buildBackground.options;
		options.expList = parent0.expListCombo;
		options.expList.index0 = parent0.expListCombo.getSelectedIndex();
		if (allCheckBox.isSelected())
			options.expList.index1 = options.expList.getItemCount() - 1;
		else
			options.expList.index1 = parent0.expListCombo.getSelectedIndex();
//		parent0.paneKymos.tabDisplay.indexImagesCombo = parent0.paneKymos.tabDisplay.kymographsCombo.getSelectedIndex();

		options.btrackWhite = true;
		options.backgroundThreshold = (int) backgroundThresholdSpinner.getValue();
		options.backgroundNFrames = (int) backgroundNFramesSpinner.getValue();
		Experiment exp = (Experiment) parent0.expListCombo.getSelectedItem();
		options.backgroundFirst = (int) exp.seqCamData.currentFrame;

		options.forceBuildBackground = true;
		options.detectFlies = false;

		options.parent0Rect = parent0.mainFrame.getBoundsInternal();
		options.isFrameFixed = parent0.dlgExcel.tabCommonOptions.getIsFixedFrame();
		options.t_Ms_First = parent0.dlgExcel.tabCommonOptions.getStartMs();
		options.t_Ms_Last = parent0.dlgExcel.tabCommonOptions.getEndMs();
		options.t_Ms_BinDuration = parent0.dlgExcel.tabCommonOptions.getBinMs();

		options.background_jitter = (int) backgroundJitterSpinner.getValue();
		options.background_delta = (int) backgroundDeltaSpinner.getValue();

		return options;
	}

	void startComputation() {
		Experiment exp = (Experiment) parent0.expListCombo.getSelectedItem();
		if (exp == null)
			return;
		parent0.dlgBrowse.panelLoadSave.closeViewsForCurrentExperiment(exp);

		buildBackground = new BuildBackground();
		buildBackground.options = initTrackParameters();
		buildBackground.stopFlag = false;

		buildBackground.addPropertyChangeListener(this);
		buildBackground.execute();
		startComputationButton.setText("STOP");
	}

	private void stopComputation() {
		if (buildBackground != null && !buildBackground.stopFlag)
			buildBackground.stopFlag = true;
	}

	@Override
	public void propertyChange(PropertyChangeEvent evt) {
		if (StringUtil.equals("thread_ended", evt.getPropertyName())) {
			startComputationButton.setText(detectString);
//			parent0.paneKymos.tabDisplay.selectKymographImage(parent0.paneKymos.tabDisplay.indexImagesCombo);
//			parent0.paneKymos.tabDisplay.indexImagesCombo = -1;
			loadBackground();
		}
	}

}
