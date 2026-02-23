package plugins.fmp.multicafe.dlg.cages;

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
import icy.image.IcyBufferedImage;
import icy.image.IcyBufferedImageUtil;
import icy.util.StringUtil;
import plugins.fmp.multicafe.MultiCAFE;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.sequence.SequenceCamData;
import plugins.fmp.multitools.series.BuildBackground;
import plugins.fmp.multitools.series.options.BuildSeriesOptions;
import plugins.fmp.multitools.service.SequenceLoaderService;
import plugins.fmp.multitools.tools.Logger;
import plugins.fmp.multitools.tools.imageTransform.ImageTransformEnums;
import plugins.fmp.multitools.tools.overlay.OverlayThreshold;

public class Detect2Background extends JPanel implements ChangeListener, PropertyChangeListener {
	private static final long serialVersionUID = 1L;

	private MultiCAFE parent0 = null;

	private String detectString = "Build background...";
	private JButton startComputationButton = new JButton(detectString);

	private JSpinner backgroundThresholdSpinner = new JSpinner(new SpinnerNumberModel(60, 0, 255, 1));
	private JSpinner backgroundNFramesSpinner = new JSpinner(new SpinnerNumberModel(20, 0, 255, 1));
	private JSpinner backgroundJitterSpinner = new JSpinner(new SpinnerNumberModel(1, 0, 255, 1));
	private JSpinner backgroundDeltaSpinner = new JSpinner(new SpinnerNumberModel(20, 0, 255, 1));

	private JButton loadButton = new JButton("Load...");
	private JButton saveButton = new JButton("Save...");
	private JCheckBox allCheckBox = new JCheckBox("ALL (current to last)", false);
	private JCheckBox overlayCheckBox = new JCheckBox("overlay");

	private BuildBackground buildBackground = null;
	private OverlayThreshold ov = null;

	// ----------------------------------------------------

	void init(GridLayout capLayout, MultiCAFE parent0) {
		setLayout(capLayout);
		this.parent0 = parent0;

		FlowLayout flowLayout = new FlowLayout(FlowLayout.LEFT);
		flowLayout.setVgap(0);

		JPanel panel1 = new JPanel(flowLayout);
		panel1.add(startComputationButton);
		panel1.add(allCheckBox);
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

		JPanel panel4 = new JPanel(flowLayout);
		panel4.add(loadButton);
		panel4.add(saveButton);
		add(panel4);

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
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp != null)
					new SequenceLoaderService().saveReferenceImage(exp);
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
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp != null) {
					if (overlayCheckBox.isSelected()) {
						if (ov == null)
							ov = new OverlayThreshold(exp.getSeqCamData().getSequence());
						exp.getSeqCamData().getSequence().addOverlay(ov);
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
			Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
			if (!overlayCheckBox.isSelected())
				overlayCheckBox.setSelected(true);
			if (exp != null)
				updateOverlay(exp);
		}
	}

	void loadBackground() {
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		if (exp != null) {
			boolean flag = new SequenceLoaderService().loadReferenceImage(exp);
			if (flag) {
				Viewer v = new Viewer(exp.getSeqReference(), true);
				Rectangle rectv = exp.getSeqCamData().getSequence().getFirstViewer().getBoundsInternal();
				v.setBounds(rectv);
			} else {
				MessageDialog.showDialog("Reference file not found on disk", MessageDialog.ERROR_MESSAGE);
			}
		}
	}

	private void updateOverlay(Experiment exp) {
		SequenceCamData seqCamData = exp.getSeqCamData();
		if (seqCamData == null || seqCamData.getSequence() == null)
			return;
		
		IcyBufferedImage referenceImage = seqCamData.getReferenceImage();
		
		if (referenceImage == null) {
			int t = seqCamData.getCurrentFrame();
			IcyBufferedImage currentImage = seqCamData.getSeqImage(t, 0);
			if (currentImage == null) {
				Logger.warn("Detect2Background: Cannot update overlay - no current image available at frame " + t);
				return;
			}
			referenceImage = IcyBufferedImageUtil.getCopy(currentImage);
			seqCamData.setReferenceImage(referenceImage);
		}
		
		if (ov == null) {
			ov = new OverlayThreshold(seqCamData.getSequence());
		} else {
			seqCamData.getSequence().removeOverlay(ov);
			ov.setSequence(seqCamData.getSequence());
		}
		
		ov.setReferenceImage(referenceImage);
		seqCamData.getSequence().addOverlay(ov);

		boolean ifGreater = true;
		ImageTransformEnums transformOp = ImageTransformEnums.NONE;
		int threshold = (int) backgroundThresholdSpinner.getValue();
		ov.setThresholdSingle(threshold, transformOp, ifGreater);
		ov.painterChanged();
	}

	private void removeOverlay(Experiment exp) {
		if (exp.getSeqCamData() != null && exp.getSeqCamData().getSequence() != null)
			exp.getSeqCamData().getSequence().removeOverlay(ov);
	}

	private BuildSeriesOptions initTrackParameters(Experiment exp) {
		BuildSeriesOptions options = buildBackground.options;
		options.expList = parent0.expListComboLazy;
		options.expList.index0 = parent0.expListComboLazy.getSelectedIndex();
		if (allCheckBox.isSelected())
			options.expList.index1 = options.expList.getItemCount() - 1;
		else
			options.expList.index1 = parent0.expListComboLazy.getSelectedIndex();

		options.btrackWhite = true;
		options.backgroundThreshold = (int) backgroundThresholdSpinner.getValue();
		options.backgroundNFrames = (int) backgroundNFramesSpinner.getValue();
		options.backgroundFirst = (int) exp.getSeqCamData().getCurrentFrame();

		options.forceBuildBackground = true;
		options.detectFlies = false;

		options.parent0Rect = parent0.mainFrame.getBoundsInternal();
		options.binSubDirectory = exp.getBinSubDirectory();

		options.isFrameFixed = parent0.paneExcel.tabCommonOptions.getIsFixedFrame();
		options.t_Ms_First = parent0.paneExcel.tabCommonOptions.getStartMs();
		options.t_Ms_Last = parent0.paneExcel.tabCommonOptions.getEndMs();
		options.t_Ms_BinDuration = parent0.paneExcel.tabCommonOptions.getBinMs();

		options.background_jitter = (int) backgroundJitterSpinner.getValue();
		options.background_delta = (int) backgroundDeltaSpinner.getValue();

		return options;
	}

	void startComputation() {
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		if (exp == null)
			return;
		parent0.paneBrowse.panelLoadSave.closeViewsForCurrentExperiment(exp);

		buildBackground = new BuildBackground();
		buildBackground.options = initTrackParameters(exp);
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
			parent0.paneKymos.tabIntervals.selectKymographImage(parent0.paneKymos.tabIntervals.indexImagesCombo);
			parent0.paneKymos.tabIntervals.indexImagesCombo = -1;
			
			if (buildBackground != null && buildBackground.isSaveSuccessful()) {
				loadBackgroundWithRetry();
			} else {
				Logger.warn("Background image was not saved successfully - skipping automatic load");
			}
		}
	}
	
	private void loadBackgroundWithRetry() {
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		if (exp == null)
			return;
		
		String path = exp.getExperimentDirectory() + java.io.File.separator + "referenceImage.jpg";
		java.io.File file = new java.io.File(path);
		
		int maxRetries = 5;
		int retryDelayMs = 200;
		boolean loaded = false;
		
		for (int i = 0; i < maxRetries; i++) {
			if (file.exists()) {
				loaded = new SequenceLoaderService().loadReferenceImage(exp);
				if (loaded) {
					Viewer v = new Viewer(exp.getSeqReference(), true);
					Rectangle rectv = exp.getSeqCamData().getSequence().getFirstViewer().getBoundsInternal();
					v.setBounds(rectv);
					Logger.info("Background image loaded successfully after " + (i + 1) + " attempt(s)");
					return;
				}
			}
			
			if (i < maxRetries - 1) {
				try {
					Thread.sleep(retryDelayMs);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					break;
				}
			}
		}
		
		if (!loaded) {
			Logger.warn("Failed to load background image after " + maxRetries + " attempts: " + path);
			MessageDialog.showDialog("Reference file not found on disk after background creation.\nPath: " + path, MessageDialog.ERROR_MESSAGE);
		}
	}

}
