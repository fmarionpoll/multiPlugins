package plugins.fmp.multicafe.dlg.levels;

import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
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

import icy.util.StringUtil;
import plugins.fmp.multicafe.MultiCAFE;
import plugins.fmp.multicafe.canvas2D.Canvas2DWithTransforms;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.capillary.Capillary;
import plugins.fmp.multitools.series.DetectGulps;
import plugins.fmp.multitools.series.options.BuildSeriesOptions;
import plugins.fmp.multitools.tools.imageTransform.ImageTransformEnums;

public class DetectGulpsDlg extends JPanel implements PropertyChangeListener {
	/**
	 * 
	 */
	private static final long serialVersionUID = -5590697762090397890L;

	JCheckBox selectedKymoCheckBox = new JCheckBox("selected kymograph", false);
	ImageTransformEnums[] gulpTransforms = new ImageTransformEnums[] { ImageTransformEnums.XDIFFN,
			ImageTransformEnums.YDIFFN, ImageTransformEnums.YDIFFN2, ImageTransformEnums.XYDIFFN };

	JComboBox<ImageTransformEnums> gulpTransforms_comboBox = new JComboBox<ImageTransformEnums>(gulpTransforms);
	JSpinner start_spinner = new JSpinner(new SpinnerNumberModel(0, 0, 100000, 1));
	JSpinner end_spinner = new JSpinner(new SpinnerNumberModel(3, 1, 100000, 1));
	JCheckBox derivative_checkbox = new JCheckBox("derivative", true);
	JCheckBox gulps_checkbox = new JCheckBox("gulps", true);

	private JCheckBox from_pixel_checkbox = new JCheckBox("from (pixel)", false);
	private JToggleButton display_button = new JToggleButton("Display");
	private JSpinner spanTransf2Spinner = new JSpinner(new SpinnerNumberModel(3, 0, 500, 1));
	private JSpinner detectGulpsThresholdSpinner = new JSpinner(new SpinnerNumberModel(.5, 0., 500., .1));
	private String detectString = "        Detect     ";
	private JButton detectButton = new JButton(detectString);
	private JCheckBox all_checkbox = new JCheckBox("ALL (current to last)", false);
	private DetectGulps threadDetectGulps = null;
	private MultiCAFE parent0 = null;

	void init(GridLayout capLayout, MultiCAFE parent0) {
		setLayout(capLayout);
		this.parent0 = parent0;

		FlowLayout layoutLeft = new FlowLayout(FlowLayout.LEFT);
		layoutLeft.setVgap(0);

		JPanel panel0 = new JPanel(layoutLeft);
		panel0.add(detectButton);
		panel0.add(all_checkbox);
		panel0.add(selectedKymoCheckBox);
		panel0.add(derivative_checkbox);
		panel0.add(gulps_checkbox);
		add(panel0);

		JPanel panel01 = new JPanel(layoutLeft);
		panel01.add(new JLabel("threshold", SwingConstants.RIGHT));
		panel01.add(detectGulpsThresholdSpinner);
		panel01.add(gulpTransforms_comboBox);
		panel01.add(display_button);
		add(panel01);

		JPanel panel1 = new JPanel(layoutLeft);
		panel1.add(from_pixel_checkbox);
		panel1.add(start_spinner);
		panel1.add(new JLabel("to"));
		panel1.add(end_spinner);
		add(panel1);

		gulpTransforms_comboBox.setSelectedItem(ImageTransformEnums.XDIFFN);
		defineActionListeners();
	}

	private void defineActionListeners() {
		gulpTransforms_comboBox.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp != null && exp.getSeqCamData() != null) {
					int index = gulpTransforms_comboBox.getSelectedIndex();
					getKymosCanvas(exp).transformsCombo1.setSelectedIndex(index + 1);
				}
			}
		});

		detectButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				if (detectButton.getText().equals(detectString))
					startComputation(true);
				else
					stopComputation();
			}
		});

		display_button.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp != null && exp.getSeqCamData() != null) {
					if (display_button.isSelected()) {
						Canvas2DWithTransforms canvas = getKymosCanvas(exp);
						canvas.updateTransformsComboStep1(gulpTransforms);
						int index = gulpTransforms_comboBox.getSelectedIndex();
						canvas.selectIndexStep1(index + 1, null);
					} else
						getKymosCanvas(exp).transformsCombo1.setSelectedIndex(0);
				}
			}
		});

		all_checkbox.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Color color = Color.BLACK;
				if (all_checkbox.isSelected())
					color = Color.RED;
				all_checkbox.setForeground(color);
				detectButton.setForeground(color);
			}
		});

	}

	private BuildSeriesOptions initBuildParameters(Experiment exp) {
		BuildSeriesOptions options = threadDetectGulps.options;
		options.expList = parent0.expListComboLazy;
		options.expList.index0 = parent0.expListComboLazy.getSelectedIndex();

		if (all_checkbox.isSelected())
			options.expList.index1 = options.expList.getItemCount() - 1;
		else
			options.expList.index1 = parent0.expListComboLazy.getSelectedIndex();

		options.detectSelectedKymo = selectedKymoCheckBox.isSelected();

		if (selectedKymoCheckBox.isSelected()) {
			int t = exp.getSeqKymos().getSequence().getFirstViewer().getPositionT();
			options.kymoFirst = t;
			options.kymoLast = t;
		} else {
			options.kymoFirst = 0;
			options.kymoLast = exp.getSeqKymos().getSequence().getSizeT() - 1;
		}
		options.detectGulpsThreshold_uL = (double) detectGulpsThresholdSpinner.getValue();
		options.transformForGulps = (ImageTransformEnums) gulpTransforms_comboBox.getSelectedItem();
		options.detectSelectedKymo = selectedKymoCheckBox.isSelected();
		options.spanDiff = (int) spanTransf2Spinner.getValue();
		options.buildGulps = gulps_checkbox.isSelected();
		options.buildDerivative = derivative_checkbox.isSelected();
		options.analyzePartOnly = from_pixel_checkbox.isSelected();
		options.searchArea.x = (int) start_spinner.getValue();
		options.searchArea.width = (int) end_spinner.getValue() + (int) start_spinner.getValue();
		options.parent0Rect = parent0.mainFrame.getBoundsInternal();
		options.binSubDirectory = exp.getBinSubDirectory();
		return options;
	}

	void startComputation(boolean bDetectGulps) {
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		if (exp != null) {
			exp.saveCapillariesMeasures(exp.getKymosBinFullDirectory());
			threadDetectGulps = new DetectGulps();
			threadDetectGulps.options = initBuildParameters(exp);
			if (!bDetectGulps)
				threadDetectGulps.options.buildGulps = false;
			threadDetectGulps.addPropertyChangeListener(this);
			threadDetectGulps.execute();
			detectButton.setText("STOP");
		}
	}

	void setInfos(Capillary cap) {
		BuildSeriesOptions options = cap.getGulpsOptions();
		detectGulpsThresholdSpinner.setValue(options.detectGulpsThreshold_uL);
		gulpTransforms_comboBox.setSelectedItem(options.transformForGulps);
		selectedKymoCheckBox.setSelected(options.detectSelectedKymo);
	}

	private void stopComputation() {
		if (threadDetectGulps != null && !threadDetectGulps.stopFlag) {
			threadDetectGulps.stopFlag = true;
		}
	}

	@Override
	public void propertyChange(PropertyChangeEvent evt) {
		if (StringUtil.equals("thread_ended", evt.getPropertyName())) {
			detectButton.setText(detectString);
			parent0.paneKymos.tabDisplay.selectKymographImage(parent0.paneKymos.tabDisplay.indexImagesCombo);
			parent0.paneKymos.tabDisplay.indexImagesCombo = -1;

			start_spinner.setValue(threadDetectGulps.options.searchArea.x);
			end_spinner.setValue(threadDetectGulps.options.searchArea.width + threadDetectGulps.options.searchArea.x);

		}
	}

	protected Canvas2DWithTransforms getKymosCanvas(Experiment exp) {
		Canvas2DWithTransforms canvas = (Canvas2DWithTransforms) exp.getSeqKymos().getSequence().getFirstViewer()
				.getCanvas();
		return canvas;
	}

}
