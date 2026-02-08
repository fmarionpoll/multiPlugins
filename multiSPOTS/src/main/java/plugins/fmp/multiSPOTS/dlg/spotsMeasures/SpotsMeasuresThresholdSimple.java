package plugins.fmp.multiSPOTS.dlg.spotsMeasures;

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

import icy.util.StringUtil;
import plugins.fmp.multiSPOTS.MultiSPOTS;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.series.options.BuildSeriesOptions;
import plugins.fmp.multitools.tools.imageTransform.ImageTransformEnums;
import plugins.fmp.multitools.tools.overlay.OverlayThreshold;

public class SpotsMeasuresThresholdSimple extends JPanel implements PropertyChangeListener {

	/**
	 * 
	 */
	private static final long serialVersionUID = 8921207247623517524L;

	private String detectString = "Detect";
	private JButton detectButton = new JButton(detectString);
	private JCheckBox allSeriesCheckBox = new JCheckBox("ALL (current to last)", false);

	private JCheckBox topSpotCheckBox = new JCheckBox("red", true);
	private JCheckBox bottomSpotCheckBox = new JCheckBox("blue spots", true);
	private JCheckBox compensateBackgroundCheckBox = new JCheckBox("compensate bkg", false);
//	private JCheckBox concurrentDisplayCheckBox = new JCheckBox("concurrent display", false);

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
	private JCheckBox spotsOverlayCheckBox = new JCheckBox("overlay");
	private JToggleButton viewButton1 = new JToggleButton("View");
	private JToggleButton viewButton2 = new JToggleButton("View");

	private JLabel fliesFilterLabel = new JLabel("  Flies filter");
	private JComboBox<ImageTransformEnums> fliesTransformsComboBox = new JComboBox<ImageTransformEnums>(transforms);
	private JComboBox<String> fliesDirectionComboBox = new JComboBox<String>(directions);
	private JSpinner fliesThresholdSpinner = new JSpinner(new SpinnerNumberModel(50, 0, 255, 1));

	private OverlayThreshold overlayThreshold = null;
	private BuildSpotsMeasures threadDetectLevels = null;
	private MultiSPOTS parent0 = null;

	public void init(GridLayout gridLayout, MultiSPOTS parent0) {
		setLayout(gridLayout);
		this.parent0 = parent0;
		FlowLayout layoutLeft = new FlowLayout(FlowLayout.LEFT);
		layoutLeft.setVgap(0);

		JPanel panel0 = new JPanel(layoutLeft);
		panel0.add(detectButton);
		panel0.add(allSeriesCheckBox);
		panel0.add(topSpotCheckBox);
		panel0.add(bottomSpotCheckBox);
		panel0.add(compensateBackgroundCheckBox);
		add(panel0);

		JPanel panel1 = new JPanel(layoutLeft);
		panel1.add(spotsFilterLabel);
		panel1.add(spotsTransformsComboBox);
		panel1.add(spotsDirectionComboBox);
		panel1.add(spotsThresholdSpinner);
		panel1.add(viewButton1);
		panel1.add(spotsOverlayCheckBox);
		add(panel1);

		JPanel panel2 = new JPanel(layoutLeft);
		panel2.add(fliesFilterLabel);
		panel2.add(fliesTransformsComboBox);
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

	private void declareListeners() {
		spotsOverlayCheckBox.addItemListener(new ItemListener() {
			public void itemStateChanged(ItemEvent e) {
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp != null) {
					if (spotsOverlayCheckBox.isSelected()) {
						updateOverlay(exp);
						updateOverlayThreshold();
					} else
						removeOverlay(exp);
				}
			}
		});

		spotsTransformsComboBox.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp != null && exp.getSeqKymos() != null && viewButton1.isSelected()) {
					int index = spotsTransformsComboBox.getSelectedIndex();
					Canvas2D_2Transforms canvas = (Canvas2D_2Transforms) exp.getSeqCamData().getSequence().getFirstViewer()
							.getCanvas();
					updateTransformFunctions1OfCanvas(exp);
					if (!viewButton1.isSelected()) {
						viewButton1.setSelected(true);
					}
					canvas.transformsComboStep1.setSelectedIndex(index + 1);
					updateOverlayThreshold();
				}
			}
		});

		fliesTransformsComboBox.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp != null && exp.getSeqKymos() != null && viewButton2.isSelected()) {
					int index = fliesTransformsComboBox.getSelectedIndex();
					Canvas2D_2Transforms canvas = (Canvas2D_2Transforms) exp.getSeqCamData().getSequence().getFirstViewer()
							.getCanvas();
					updateTransformFunctions2OfCanvas(exp);
					if (!viewButton2.isSelected()) {
						viewButton2.setSelected(true);
					}
					canvas.transformsComboStep1.setSelectedIndex(index + 1);
					updateOverlayThreshold();
				}
			}
		});

		spotsDirectionComboBox.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				updateOverlayThreshold();
			}
		});

		fliesDirectionComboBox.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				updateOverlayThreshold();
			}
		});

		spotsThresholdSpinner.addChangeListener(new ChangeListener() {
			public void stateChanged(ChangeEvent e) {
				updateOverlayThreshold();
			}
		});

		fliesThresholdSpinner.addChangeListener(new ChangeListener() {
			public void stateChanged(ChangeEvent e) {
				updateOverlayThreshold();
			}
		});

		viewButton1.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp != null)
					displayTransform1(exp);
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

	void updateOverlay(Experiment exp) {
		if (exp.getSeqKymos() == null)
			return;

		if (overlayThreshold == null)
			overlayThreshold = new OverlayThreshold(exp.getSeqCamData().getSequence());
		else {
			exp.getSeqCamData().getSequence().removeOverlay(overlayThreshold);
			overlayThreshold.setSequence(exp.getSeqCamData().getSequence());
		}
		exp.getSeqCamData().getSequence().addOverlay(overlayThreshold);
	}

	void removeOverlay(Experiment exp) {
		if (exp.getSeqCamData() != null && exp.getSeqCamData().getSequence() != null)
			exp.getSeqCamData().getSequence().removeOverlay(overlayThreshold);
	}

	void updateOverlayThreshold() {
		if (overlayThreshold == null)
			return;

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
		overlayThreshold.setThresholdSingle(threshold, transform, ifGreater);
		overlayThreshold.painterChanged();
	}

	void startDetection() {
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		if (exp != null) {
			threadDetectLevels = new BuildSpotsMeasures();
			threadDetectLevels.options = initDetectOptions(exp);
			threadDetectLevels.addPropertyChangeListener(this);
			threadDetectLevels.execute();
			detectButton.setText("STOP");
		}
	}

	private void stopDetection() {
		if (threadDetectLevels != null && !threadDetectLevels.stopFlag)
			threadDetectLevels.stopFlag = true;
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

		options.detectL = topSpotCheckBox.isSelected();
		options.detectR = bottomSpotCheckBox.isSelected();
		options.compensateBackground = compensateBackgroundCheckBox.isSelected();

		return options;
	}

	private void displayTransform1(Experiment exp) {
		boolean displayCheckOverlay = false;
		if (viewButton1.isSelected()) {
			updateTransformFunctions1OfCanvas(exp);
			displayCheckOverlay = true;
		} else {
			removeOverlay(exp);
			spotsOverlayCheckBox.setSelected(false);
			Canvas2D_2Transforms canvas = (Canvas2D_2Transforms) exp.getSeqCamData().getSequence().getFirstViewer().getCanvas();
			canvas.transformsComboStep1.setSelectedIndex(0);

		}
		spotsOverlayCheckBox.setEnabled(displayCheckOverlay);
	}

	private void displayTransform2(Experiment exp) {
		boolean displayCheckOverlay = false;
		if (viewButton2.isSelected()) {
			updateTransformFunctions2OfCanvas(exp);
			displayCheckOverlay = true;
		} else {
			removeOverlay(exp);
			spotsOverlayCheckBox.setSelected(false);
			Canvas2D_2Transforms canvas = (Canvas2D_2Transforms) exp.getSeqCamData().getSequence().getFirstViewer().getCanvas();
			canvas.transformsComboStep1.setSelectedIndex(0);

		}
		spotsOverlayCheckBox.setEnabled(displayCheckOverlay);
	}

	private void updateTransformFunctions1OfCanvas(Experiment exp) {
		Canvas2D_2Transforms canvas = (Canvas2D_2Transforms) exp.getSeqCamData().getSequence().getFirstViewer().getCanvas();
		if (canvas.transformsComboStep1.getItemCount() < (spotsTransformsComboBox.getItemCount() + 1)) {
			canvas.updateTransformsComboStep1(transforms);
		}
		int index = spotsTransformsComboBox.getSelectedIndex();
		canvas.selectImageTransformFunctionStep1(index + 1);
	}

	private void updateTransformFunctions2OfCanvas(Experiment exp) {
		Canvas2D_2Transforms canvas = (Canvas2D_2Transforms) exp.getSeqCamData().getSequence().getFirstViewer().getCanvas();
		if (canvas.transformsComboStep1.getItemCount() < (fliesDirectionComboBox.getItemCount() + 1)) {
			canvas.updateTransformsComboStep1(transforms);
		}
		int index = fliesDirectionComboBox.getSelectedIndex();
		canvas.selectImageTransformFunctionStep1(index + 1);
	}

	@Override
	public void propertyChange(PropertyChangeEvent evt) {
		if (StringUtil.equals("thread_ended", evt.getPropertyName())) {
			detectButton.setText(detectString);
			Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
			if (exp != null) {
				exp.load_SpotsMeasures();
				parent0.dlgMeasure.tabGraphs.displayGraphsPanels(exp);
			}
		}
	}

}
