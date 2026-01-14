package plugins.fmp.multicafe.dlg.levels;

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
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JToggleButton;
import javax.swing.SpinnerNumberModel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import icy.util.StringUtil;
import plugins.fmp.multicafe.MultiCAFE;
import plugins.fmp.multitools.fmp_experiment.Experiment;
import plugins.fmp.multitools.fmp_experiment.capillaries.Capillary;
import plugins.fmp.multitools.fmp_series.DetectLevels;
import plugins.fmp.multitools.fmp_series.options.BuildSeriesOptions;
import plugins.fmp.multicafe.canvas2D.Canvas2DWithTransforms;
import plugins.fmp.multitools.fmp_tools.imageTransform.ImageTransformEnums;
import plugins.fmp.multitools.fmp_tools.overlay.OverlayThreshold;
import plugins.kernel.roi.roi2d.ROI2DRectangle;

public class DetectLevelsDlg extends JPanel implements PropertyChangeListener {
	private static final long serialVersionUID = -6329863521455897561L;

	private JCheckBox pass1CheckBox = new JCheckBox("pass1", true);
	private JComboBox<String> direction1ComboBox = new JComboBox<String>(
			new String[] { " threshold >", " threshold <" });
	private JSpinner threshold1Spinner = new JSpinner(new SpinnerNumberModel(35, 1, 255, 1));
	private ImageTransformEnums[] transformPass1 = new ImageTransformEnums[] { ImageTransformEnums.R_RGB,
			ImageTransformEnums.G_RGB, ImageTransformEnums.B_RGB, ImageTransformEnums.R2MINUS_GB,
			ImageTransformEnums.G2MINUS_RB, ImageTransformEnums.B2MINUS_RG, ImageTransformEnums.RGB,
			ImageTransformEnums.GBMINUS_2R, ImageTransformEnums.RBMINUS_2G, ImageTransformEnums.RGMINUS_2B,
			ImageTransformEnums.RGB_DIFFS, ImageTransformEnums.H_HSB, ImageTransformEnums.S_HSB,
			ImageTransformEnums.B_HSB };
	public JComboBox<ImageTransformEnums> transformPass1ComboBox = new JComboBox<ImageTransformEnums>(transformPass1);
	private JToggleButton transformPass1DisplayButton = new JToggleButton("View");
	private JCheckBox overlayPass1CheckBox = new JCheckBox("overlay");

	private JCheckBox pass2CheckBox = new JCheckBox("pass2", false);
	private JComboBox<String> direction2ComboBox = new JComboBox<String>(
			new String[] { " threshold >", " threshold <" });
	private JSpinner threshold2Spinner = new JSpinner(new SpinnerNumberModel(40, 1, 255, 1));
	private ImageTransformEnums[] transformPass2 = new ImageTransformEnums[] { ImageTransformEnums.DERICHE_COLOR,
			ImageTransformEnums.DERICHE, ImageTransformEnums.YDIFFN, ImageTransformEnums.YDIFFN2,
			ImageTransformEnums.MINUSHORIZAVG, ImageTransformEnums.COLORDISTANCE_L1_Y,
			ImageTransformEnums.COLORDISTANCE_L2_Y, ImageTransformEnums.SUBTRACT_1RSTCOL,
			ImageTransformEnums.L1DIST_TO_1RSTCOL };
	private JComboBox<ImageTransformEnums> transformPass2ComboBox = new JComboBox<ImageTransformEnums>(transformPass2);
	private JToggleButton transformPass2DisplayButton = new JToggleButton("View");
	private JCheckBox overlayPass2CheckBox = new JCheckBox("overlay");
	private JSpinner jitter2Spinner = new JSpinner(new SpinnerNumberModel(5, 0, 255, 1));

	private JCheckBox selectedKymoCheckBox = new JCheckBox("selected kymograph", false);
	private JSpinner spanTopSpinner = new JSpinner(new SpinnerNumberModel(3, 1, 100, 1));
	private String detectString = "        Detect     ";
	private JButton detectButton = new JButton(detectString);
	private JCheckBox fromCheckBox = new JCheckBox(" detection from ROI rectangle", false);

	private JCheckBox allSeriesCheckBox = new JCheckBox("ALL (current to last)", false);
	private JCheckBox leftCheckBox = new JCheckBox("L", true);
	private JCheckBox rightCheckBox = new JCheckBox("R", true);
	private JCheckBox runBackwardsCheckBox = new JCheckBox("run backwards", false);

	private MultiCAFE parent0 = null;
	private DetectLevels threadDetectLevels = null;

	private String SEARCHRECT = new String("search_rectangle");
	private ROI2DRectangle searchRectangleROI2D = null;
	private OverlayThreshold overlayThreshold = null;
	private int currentKymographImage = 0;

	// -----------------------------------------------------

	void init(GridLayout capLayout, MultiCAFE parent0) {
		setLayout(capLayout);
		this.parent0 = parent0;

		FlowLayout layoutLeft = new FlowLayout(FlowLayout.LEFT);
		layoutLeft.setVgap(0);

		JPanel panel0 = new JPanel(layoutLeft);
		panel0.add(detectButton);
		panel0.add(allSeriesCheckBox);
		panel0.add(selectedKymoCheckBox);
		panel0.add(leftCheckBox);
		panel0.add(rightCheckBox);
		add(panel0);

		JPanel panel01 = new JPanel(layoutLeft);
		panel01.add(pass1CheckBox);
		panel01.add(direction1ComboBox);
		((JLabel) direction1ComboBox.getRenderer()).setHorizontalAlignment(JLabel.RIGHT);
		panel01.add(threshold1Spinner);
		panel01.add(transformPass1ComboBox);
		panel01.add(transformPass1DisplayButton);
		panel01.add(overlayPass1CheckBox);
		add(panel01);

		JPanel panel02 = new JPanel(layoutLeft);
		panel02.add(pass2CheckBox);
		panel02.add(direction2ComboBox);
		((JLabel) direction2ComboBox.getRenderer()).setHorizontalAlignment(JLabel.RIGHT);
		panel02.add(threshold2Spinner);
		panel02.add(transformPass2ComboBox);
		panel02.add(transformPass2DisplayButton);
		panel02.add(overlayPass2CheckBox);
		add(panel02);

		JPanel panel03 = new JPanel(layoutLeft);
		panel03.add(new JLabel("pass2 vertical jitter"));
		panel03.add(jitter2Spinner);
		panel03.add(fromCheckBox);
		panel03.add(runBackwardsCheckBox);
		add(panel03);

		defineActionListeners();
		defineItemListeners();
		allowItemsAccordingToSelection();
	}

	private void defineItemListeners() {
		overlayPass1CheckBox.addItemListener(new ItemListener() {
			public void itemStateChanged(ItemEvent e) {
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp != null) {
					if (overlayPass1CheckBox.isSelected())
						updateOverlay(exp);
					else
						removeOverlay(exp);
				}
			}
		});

		overlayPass2CheckBox.addItemListener(new ItemListener() {
			public void itemStateChanged(ItemEvent e) {
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp != null) {
					if (overlayPass2CheckBox.isSelected())
						updateOverlay(exp);
					else
						removeOverlay(exp);
				}
			}
		});

		threshold1Spinner.addChangeListener(new ChangeListener() {
			public void stateChanged(ChangeEvent e) {
				updateOverlayThreshold();
			}
		});

		threshold2Spinner.addChangeListener(new ChangeListener() {
			public void stateChanged(ChangeEvent e) {
				updateOverlayThreshold();
			}
		});

		jitter2Spinner.addChangeListener(new ChangeListener() {
			public void stateChanged(ChangeEvent e) {
				updateOverlayThreshold();
			}
		});
	}

	private void defineActionListeners() {
		transformPass1ComboBox.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp != null && exp.getSeqKymos() != null) {
					int index = transformPass1ComboBox.getSelectedIndex();
					getKymosCanvas(exp).transformsCombo1.setSelectedIndex(index + 1);
					updateOverlayThreshold();
				}
			}
		});

		transformPass2ComboBox.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				allowItemsAccordingToSelection();
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp != null && exp.getSeqCamData() != null) {
					int index = transformPass2ComboBox.getSelectedIndex();
					getKymosCanvas(exp).transformsCombo1.setSelectedIndex(index + 1);
					updateOverlayThreshold();
				}
			}
		});

		detectButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				if (detectButton.getText().equals(detectString))
					startLevelsDetection();
				else
					stopLevelsDetection();
			}
		});

		transformPass1DisplayButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp != null) {
					boolean displayCheckOverlay = false;
					if (transformPass1DisplayButton.isSelected()) {
						transformPass2DisplayButton.setSelected(false);
						Canvas2DWithTransforms canvas = getKymosCanvas(exp);
						canvas.updateTransformsComboStep1(transformPass1);
						int index = transformPass1ComboBox.getSelectedIndex();
						canvas.selectIndexStep1(index + 1, null);
						displayCheckOverlay = true;
					} else {
						removeOverlay(exp);
						overlayPass1CheckBox.setSelected(false);
						getKymosCanvas(exp).transformsCombo1.setSelectedIndex(0);

					}
					overlayPass1CheckBox.setEnabled(displayCheckOverlay);
				}
			}
		});

		transformPass2DisplayButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp != null) {
					boolean displayCheckOverlay = false;
					if (transformPass2DisplayButton.isSelected()) {
						transformPass1DisplayButton.setSelected(false);
						Canvas2DWithTransforms canvas = getKymosCanvas(exp);
						canvas.updateTransformsComboStep1(transformPass2);
						int index = transformPass2ComboBox.getSelectedIndex();
						canvas.selectIndexStep1(index + 1, null);
						displayCheckOverlay = true;
					} else {
						removeOverlay(exp);
						overlayPass2CheckBox.setSelected(false);
						getKymosCanvas(exp).transformsCombo1.setSelectedIndex(0);
					}
					overlayPass1CheckBox.setEnabled(displayCheckOverlay);
				}
			}
		});

		allSeriesCheckBox.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Color color = Color.BLACK;
				if (allSeriesCheckBox.isSelected())
					color = Color.RED;
				allSeriesCheckBox.setForeground(color);
				detectButton.setForeground(color);
			}
		});

		fromCheckBox.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp == null)
					return;
				if (fromCheckBox.isSelected())
					displaySearchArea(exp);
				else if (searchRectangleROI2D != null)
					exp.getSeqKymos().getSequence().removeROI(searchRectangleROI2D);
			}
		});

		direction1ComboBox.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				updateOverlayThreshold();
			}
		});
	}

	void allowItemsAccordingToSelection() {
		boolean flag = false;
		switch ((ImageTransformEnums) transformPass2ComboBox.getSelectedItem()) {
		case SUBTRACT_1RSTCOL:
		case L1DIST_TO_1RSTCOL:
			flag = true;
			break;

		default:
			flag = true;
			break;
		}
		threshold2Spinner.setEnabled(flag);
		jitter2Spinner.setEnabled(flag);
	}

	void setDialogFromOptions(Capillary cap) {
		BuildSeriesOptions options = cap.getProperties().limitsOptions;

		pass1CheckBox.setSelected(options.pass1);
		pass2CheckBox.setSelected(options.pass2);

		transformPass1ComboBox.setSelectedItem(options.transform01);
		int index = options.directionUp1 ? 0 : 1;
		direction1ComboBox.setSelectedIndex(index);
		threshold1Spinner.setValue(options.detectLevel1Threshold);

		transformPass2ComboBox.setSelectedItem(options.transform02);
		index = options.directionUp2 ? 0 : 1;
		direction2ComboBox.setSelectedIndex(index);
		threshold2Spinner.setValue(options.detectLevel2Threshold);
		jitter2Spinner.setValue(options.jitter2);
		selectedKymoCheckBox.setSelected(!options.detectSelectedKymo);
		leftCheckBox.setSelected(options.detectL);
		rightCheckBox.setSelected(options.detectR);

		fromCheckBox.setSelected(false);
	}

	void setOptionsFromDialog(Capillary cap) {
		BuildSeriesOptions options = cap.getProperties().limitsOptions;
		options.pass1 = pass1CheckBox.isSelected();
		options.pass2 = pass2CheckBox.isSelected();
		options.transform01 = (ImageTransformEnums) transformPass1ComboBox.getSelectedItem();
		options.transform02 = (ImageTransformEnums) transformPass2ComboBox.getSelectedItem();
		options.directionUp1 = (direction1ComboBox.getSelectedIndex() == 0);
		options.detectLevel1Threshold = (int) threshold1Spinner.getValue();
		options.directionUp2 = (direction2ComboBox.getSelectedIndex() == 0);
		options.detectLevel2Threshold = (int) threshold2Spinner.getValue();
		options.detectSelectedKymo = selectedKymoCheckBox.isSelected();

		options.detectL = leftCheckBox.isSelected();
		options.detectR = rightCheckBox.isSelected();
	}

	private BuildSeriesOptions initBuildParameters(Experiment exp) {
		BuildSeriesOptions options = new BuildSeriesOptions();
		// list of stack experiments
		options.expList = parent0.expListComboLazy;
		options.expList.index0 = parent0.expListComboLazy.getSelectedIndex();
		if (allSeriesCheckBox.isSelected())
			options.expList.index1 = options.expList.getItemCount() - 1;
		else
			options.expList.index1 = parent0.expListComboLazy.getSelectedIndex();
		// list of kymographs
		options.detectSelectedKymo = selectedKymoCheckBox.isSelected();

		if (selectedKymoCheckBox.isSelected()) {
			options.kymoFirst = exp.getSeqKymos().getSequence().getFirstViewer().getPositionT();
			;
			options.kymoLast = options.kymoFirst;
			currentKymographImage = exp.getSeqKymos().getSequence().getFirstViewer().getPositionT();
		} else {
			options.kymoFirst = 0;
			options.kymoLast = exp.getSeqKymos().getSequence().getSizeT() - 1;
			currentKymographImage = 0;
		}
		// other parameters
		options.pass1 = pass1CheckBox.isSelected();
		options.transform01 = (ImageTransformEnums) transformPass1ComboBox.getSelectedItem();
		options.directionUp1 = (direction1ComboBox.getSelectedIndex() == 0);
		options.detectLevel1Threshold = (int) threshold1Spinner.getValue();

		options.pass2 = pass2CheckBox.isSelected();
		options.transform02 = (ImageTransformEnums) transformPass2ComboBox.getSelectedItem();
		options.directionUp2 = (direction2ComboBox.getSelectedIndex() == 0);
		options.detectLevel2Threshold = (int) threshold2Spinner.getValue();
		options.jitter2 = (int) jitter2Spinner.getValue();

		options.analyzePartOnly = fromCheckBox.isSelected();
		options.searchArea = getSearchAreaFromSearchRectangle(exp,
				fromCheckBox.isSelected() && searchRectangleROI2D != null);

		options.spanDiffTop = (int) spanTopSpinner.getValue();
		options.detectL = leftCheckBox.isSelected();
		options.detectR = rightCheckBox.isSelected();
		options.parent0Rect = parent0.mainFrame.getBoundsInternal();
		options.binSubDirectory = parent0.expListComboLazy.expListBinSubDirectory;
		options.runBackwards = runBackwardsCheckBox.isSelected();
		return options;
	}

	void startLevelsDetection() {
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		if (exp != null) {

			threadDetectLevels = new DetectLevels();
			threadDetectLevels.options = initBuildParameters(exp);
			exp.getCapillaries().clearAllMeasures(threadDetectLevels.options.kymoFirst,
					threadDetectLevels.options.kymoLast);
			threadDetectLevels.addPropertyChangeListener(this);
			threadDetectLevels.execute();
			detectButton.setText("STOP");
		}
	}

	private void stopLevelsDetection() {
		if (threadDetectLevels != null && !threadDetectLevels.stopFlag)
			threadDetectLevels.stopFlag = true;
	}

	@Override
	public void propertyChange(PropertyChangeEvent evt) {
		if (StringUtil.equals("thread_ended", evt.getPropertyName())) {
			detectButton.setText(detectString);
			System.out.println("thread_ended");
			parent0.paneKymos.tabDisplay.selectKymographImage(currentKymographImage);
			parent0.paneKymos.tabDisplay.indexImagesCombo = -1;
			fromCheckBox.setSelected(false);
		}
	}

	private void displaySearchArea(Experiment exp) {
		if (searchRectangleROI2D == null) {
			Rectangle searchRectangle = exp.getSeqKymos().getSequence().getBounds2D();
			searchRectangle.width -= 1;
			searchRectangle.height -= 1;
			searchRectangleROI2D = new ROI2DRectangle(searchRectangle);
			searchRectangleROI2D.setName(SEARCHRECT);
			searchRectangleROI2D.setColor(Color.ORANGE);
		}
		exp.getSeqKymos().getSequence().addROI(searchRectangleROI2D);
		exp.getSeqKymos().getSequence().setSelectedROI(searchRectangleROI2D);
	}

	private Rectangle getSearchAreaFromSearchRectangle(Experiment exp, boolean fitSmallerRectangle) {
		Rectangle seqRectangle = exp.getSeqKymos().getSequence().getBounds2D();
		seqRectangle.height -= 1;
		seqRectangle.width -= 1;
		if (fitSmallerRectangle) {
			Rectangle rectangle = searchRectangleROI2D.getBounds();
			if (rectangle.x < 0) {
				rectangle.width += rectangle.x;
				rectangle.x = 0;
			}
			if (rectangle.y < 0) {
				rectangle.height += rectangle.y;
				rectangle.y = 0;
			}
			if ((rectangle.width + rectangle.x) > seqRectangle.width)
				rectangle.width = seqRectangle.width - rectangle.x;
			if ((rectangle.height + rectangle.y) > (seqRectangle.height))
				rectangle.height = seqRectangle.height - rectangle.y;
			return rectangle;
		}

		return seqRectangle;
	}

	protected Canvas2DWithTransforms getKymosCanvas(Experiment exp) {
		Canvas2DWithTransforms canvas = (Canvas2DWithTransforms) exp.getSeqKymos().getSequence().getFirstViewer()
				.getCanvas();
		return canvas;
	}

	void updateOverlay(Experiment exp) {
		if (exp.getSeqKymos() == null)
			return;
		if (overlayThreshold == null)
			overlayThreshold = new OverlayThreshold(exp.getSeqKymos().getSequence());
		else {
			exp.getSeqKymos().getSequence().removeOverlay(overlayThreshold);
			overlayThreshold.setSequence(exp.getSeqKymos().getSequence());
		}

		if (transformPass1DisplayButton.isSelected() || transformPass2DisplayButton.isSelected()) {
			exp.getSeqKymos().getSequence().addOverlay(overlayThreshold);
			updateOverlayThreshold();
		}
	}

	void updateOverlayThreshold() {
		if (overlayThreshold == null)
			return;

		boolean ifGreater = true;
		int threshold = 0;
		ImageTransformEnums transform = ImageTransformEnums.NONE;
		if (transformPass1DisplayButton.isSelected()) {
			ifGreater = (direction1ComboBox.getSelectedIndex() == 0);
			threshold = (int) threshold1Spinner.getValue();
			transform = (ImageTransformEnums) transformPass1ComboBox.getSelectedItem();
			overlayThreshold.setThresholdSingle(threshold, transform, ifGreater);
		} else if (transformPass2DisplayButton.isSelected()) {
			ifGreater = (direction2ComboBox.getSelectedIndex() == 0);
			threshold = (int) threshold2Spinner.getValue();
			transform = (ImageTransformEnums) transformPass2ComboBox.getSelectedItem();
			int jitter2 = (int) jitter2Spinner.getValue();

			int[] initialLevels = getInitialLevelPositions();
			if (initialLevels != null && jitter2 > 0) {
				overlayThreshold.setThresholdSingleWithJitter(threshold, transform, ifGreater, jitter2, initialLevels);
			} else {
				overlayThreshold.setThresholdSingle(threshold, transform, ifGreater);
			}
		} else
			return;
		overlayThreshold.painterChanged();
	}

	private int[] getInitialLevelPositions() {
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		if (exp == null || exp.getSeqKymos() == null)
			return null;

		int currentKymoIndex = exp.getSeqKymos().getSequence().getFirstViewer().getPositionT();
		if (currentKymoIndex < 0 || exp.getCapillaries() == null || exp.getCapillaries().getList() == null)
			return null;

		if (currentKymoIndex >= exp.getCapillaries().getList().size())
			return null;

		Capillary cap = exp.getCapillaries().getList().get(currentKymoIndex);
		if (cap == null || cap.getTopLevel() == null)
			return null;

		if (cap.getTopLevel().polylineLevel == null || cap.getTopLevel().polylineLevel.npoints == 0) {
			if (cap.getTopLevel().limit != null && cap.getTopLevel().limit.length > 0) {
				return cap.getTopLevel().limit;
			}
			return null;
		}

		int npoints = cap.getTopLevel().polylineLevel.npoints;
		int[] levels = new int[npoints];
		for (int i = 0; i < npoints; i++) {
			levels[i] = (int) cap.getTopLevel().polylineLevel.ypoints[i];
		}
		return levels;
	}

	void removeOverlay(Experiment exp) {
		if (exp.getSeqKymos() != null && exp.getSeqKymos().getSequence() != null)
			exp.getSeqKymos().getSequence().removeOverlay(overlayThreshold);
	}

}
