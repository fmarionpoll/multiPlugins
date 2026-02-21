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

import icy.canvas.IcyCanvas;
import icy.gui.viewer.Viewer;
import icy.sequence.Sequence;
import icy.util.StringUtil;
import plugins.fmp.multicafe.MultiCAFE;
import plugins.fmp.multicafe.canvas2D.Canvas2DWithTransforms;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.capillary.Capillary;
import plugins.fmp.multitools.series.DetectLevels;
import plugins.fmp.multitools.series.options.BuildSeriesOptions;
import plugins.fmp.multitools.tools.imageTransform.ImageTransformEnums;
import plugins.fmp.multitools.tools.overlay.OverlayThreshold;
import plugins.kernel.roi.roi2d.ROI2DRectangle;

public class DetectLevelsDlgDirect extends JPanel implements PropertyChangeListener {
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

	private JCheckBox selectedCapillaryCheckBox = new JCheckBox("selected capillary", false);
	private JSpinner spanTopSpinner = new JSpinner(new SpinnerNumberModel(3, 1, 100, 1));
	private String detectString = "        Detect     ";
	private JButton detectButton = new JButton(detectString);
	private JCheckBox fromRectangleCheckBox = new JCheckBox(" detection from ROI rectangle", false);
	private JCheckBox profilePerpendicularCheckBox = new JCheckBox("profile perpendicular to capillary", true);

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
		panel0.add(selectedCapillaryCheckBox);
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

		JPanel panel03 = new JPanel(layoutLeft);

		panel03.add(fromRectangleCheckBox);
		panel03.add(profilePerpendicularCheckBox);
		panel03.add(runBackwardsCheckBox);
		add(panel03);

		defineActionListeners();
		defineItemListeners();

		overlayPass1CheckBox.setEnabled(false);

	}

	private void defineItemListeners() {
		overlayPass1CheckBox.addItemListener(new ItemListener() {
			public void itemStateChanged(ItemEvent e) {
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp != null) {
					if (transformPass1DisplayButton.isSelected() && overlayPass1CheckBox.isSelected())
						addOverlayToSequence(exp);
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

	}

	private void defineActionListeners() {
		transformPass1ComboBox.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp != null && exp.getSeqCamData() != null) {
					Canvas2DWithTransforms canvas = getCamDataCanvas(exp);
					if (canvas != null) {
						int index = transformPass1ComboBox.getSelectedIndex();
						canvas.transformsCombo1.setSelectedIndex(index + 1);
						updateOverlayThreshold();
					}
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
				if (exp == null)
					return;

				boolean displayCheckOverlay = false;
				if (transformPass1DisplayButton.isSelected()) {
					Canvas2DWithTransforms canvas = getCamDataCanvas(exp);
					if (canvas != null) {
						canvas.updateTransformsComboStep1(transformPass1);
						int index = transformPass1ComboBox.getSelectedIndex();
						canvas.selectIndexStep1(index + 1, null);
						displayCheckOverlay = true;
					}
				} else {
					removeOverlay(exp);
					overlayPass1CheckBox.setSelected(false);
					Canvas2DWithTransforms canvas = getCamDataCanvas(exp);
					if (canvas != null)
						canvas.transformsCombo1.setSelectedIndex(0);
				}
				overlayPass1CheckBox.setEnabled(displayCheckOverlay);
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

		fromRectangleCheckBox.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp == null)
					return;
				if (fromRectangleCheckBox.isSelected())
					displaySearchArea(exp);
				else if (searchRectangleROI2D != null)
					exp.getSeqCamData().getSequence().removeROI(searchRectangleROI2D);
			}
		});

		direction1ComboBox.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				updateOverlayThreshold();
			}
		});
	}

	void setDialogFromOptions(Capillary cap) {
		BuildSeriesOptions options = cap.getProperties().getLimitsOptions();

		pass1CheckBox.setSelected(options.pass1);
		transformPass1ComboBox.setSelectedItem(options.transform01);
		int index = options.directionUp1 ? 0 : 1;
		direction1ComboBox.setSelectedIndex(index);
		threshold1Spinner.setValue(options.detectLevel1Threshold);
		selectedCapillaryCheckBox.setSelected(!options.detectSelectedKymo);
		leftCheckBox.setSelected(options.detectL);
		rightCheckBox.setSelected(options.detectR);
		profilePerpendicularCheckBox.setSelected(options.profilePerpendicular);

		fromRectangleCheckBox.setSelected(false);
	}

	void setOptionsFromDialog(Capillary cap) {
		BuildSeriesOptions options = cap.getProperties().getLimitsOptions();
		options.pass1 = pass1CheckBox.isSelected();
		options.pass2 = false; // pass2CheckBox.isSelected();
		options.transform01 = (ImageTransformEnums) transformPass1ComboBox.getSelectedItem();
		options.directionUp1 = (direction1ComboBox.getSelectedIndex() == 0);
		options.detectLevel1Threshold = (int) threshold1Spinner.getValue();
		options.detectSelectedKymo = selectedCapillaryCheckBox.isSelected();

		options.detectL = leftCheckBox.isSelected();
		options.detectR = rightCheckBox.isSelected();
		options.profilePerpendicular = profilePerpendicularCheckBox.isSelected();
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
		options.detectSelectedKymo = selectedCapillaryCheckBox.isSelected();

		options.kymoFirst = 0;
		long step = exp.getKymoBin_ms();
		if (step <= 0)
			step = 1;
		int nTimeBins = (int) ((exp.getKymoLast_ms() - exp.getKymoFirst_ms()) / step + 1);
		options.kymoLast = Math.max(0, nTimeBins - 1);
		currentKymographImage = 0;

		// other parameters
		options.pass1 = pass1CheckBox.isSelected();
		options.transform01 = (ImageTransformEnums) transformPass1ComboBox.getSelectedItem();
		options.directionUp1 = (direction1ComboBox.getSelectedIndex() == 0);
		options.detectLevel1Threshold = (int) threshold1Spinner.getValue();

		options.analyzePartOnly = fromRectangleCheckBox.isSelected();
		options.searchArea = getSearchAreaFromSearchRectangle(exp,
				fromRectangleCheckBox.isSelected() && searchRectangleROI2D != null);

		options.spanDiffTop = (int) spanTopSpinner.getValue();
		options.detectL = leftCheckBox.isSelected();
		options.detectR = rightCheckBox.isSelected();
		options.parent0Rect = parent0.mainFrame.getBoundsInternal();
		options.binSubDirectory = parent0.expListComboLazy.expListBinSubDirectory;
		options.runBackwards = runBackwardsCheckBox.isSelected();
		options.profilePerpendicular = profilePerpendicularCheckBox.isSelected();
		options.sourceCamDirect = true;
		return options;
	}

	void startLevelsDetection() {
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		if (exp != null) {

			threadDetectLevels = new DetectLevels();
			threadDetectLevels.options = initBuildParameters(exp);
			if (!threadDetectLevels.options.sourceCamDirect && !fromRectangleCheckBox.isSelected()) {
				exp.getCapillaries().clearAllMeasures(threadDetectLevels.options.kymoFirst,
						threadDetectLevels.options.kymoLast, threadDetectLevels.options.detectL,
						threadDetectLevels.options.detectR);
			}
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
			fromRectangleCheckBox.setSelected(false);
		}
	}

	private void displaySearchArea(Experiment exp) {
		if (exp.getSeqCamData() == null || exp.getSeqCamData().getSequence() == null)
			return;

		Sequence seq = exp.getSeqCamData().getSequence();
		if (searchRectangleROI2D == null) {
			Rectangle searchRectangle = seq.getBounds2D();
			searchRectangle.width -= 1;
			searchRectangle.height -= 1;
			searchRectangleROI2D = new ROI2DRectangle(searchRectangle);
			searchRectangleROI2D.setName(SEARCHRECT);
			searchRectangleROI2D.setColor(Color.ORANGE);
		}
		searchRectangleROI2D.setT(-1);
		seq.addROI(searchRectangleROI2D);
		seq.setSelectedROI(searchRectangleROI2D);
		seq.roiChanged(searchRectangleROI2D);
	}

	private Rectangle getSearchAreaFromSearchRectangle(Experiment exp, boolean fitSmallerRectangle) {
		Rectangle seqBounds = exp.getSeqCamData().getSequence().getBounds2D();
		int seqW = Math.max(0, seqBounds.width - 1);
		int seqH = Math.max(0, seqBounds.height - 1);
		if (fitSmallerRectangle && searchRectangleROI2D != null) {
			Rectangle r = searchRectangleROI2D.getBounds();
			int x = Math.max(0, r.x);
			int y = Math.max(0, r.y);
			int w = r.width;
			int h = r.height;
			if (x + w > seqW)
				w = seqW - x;
			if (y + h > seqH)
				h = seqH - y;
			return new Rectangle(x, y, Math.max(0, w), Math.max(0, h));
		}
		return new Rectangle(0, 0, seqW, seqH);
	}

	protected Canvas2DWithTransforms getCamDataCanvas(Experiment exp) {
		if (exp.getSeqCamData() == null || exp.getSeqCamData().getSequence() == null)
			return null;

		Viewer v = exp.getSeqCamData().getSequence().getFirstViewer();
		if (v == null)
			return null;
		return (Canvas2DWithTransforms) v.getCanvas();
	}

	void addOverlayToSequence(Experiment exp) {
		if (exp.getSeqCamData() == null || exp.getSeqCamData().getSequence() == null)
			return;

		Sequence seq = exp.getSeqCamData().getSequence();
		if (seq.getFirstViewer() == null)
			parent0.paneKymos.tabDisplay.displayON();

		if (overlayThreshold == null)
			overlayThreshold = new OverlayThreshold(seq);
		else {
			seq.removeOverlay(overlayThreshold);
			overlayThreshold.setSequence(seq);
		}
		seq.addOverlay(overlayThreshold);

		Viewer v = seq.getFirstViewer();
		if (v != null) {
			IcyCanvas canvas = v.getCanvas();
			if (canvas != null) {
				if (!canvas.hasLayer(overlayThreshold))
					canvas.addLayer(overlayThreshold);
				if (!canvas.isLayersVisible())
					canvas.setLayersVisible(true);
			}
		}

		updateOverlayThreshold();
		seq.overlayChanged(overlayThreshold);
		seq.dataChanged();
	}

	void updateOverlayThreshold() {
		if (overlayThreshold == null)
			return;

		if (transformPass1DisplayButton.isSelected() && overlayPass1CheckBox.isSelected()) {
			boolean ifGreater = (direction1ComboBox.getSelectedIndex() == 0);
			int threshold = (int) threshold1Spinner.getValue();
			ImageTransformEnums transform = (ImageTransformEnums) transformPass1ComboBox.getSelectedItem();
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

	void removeOverlay(Experiment exp) {
		if (exp.getSeqCamData() == null || exp.getSeqCamData().getSequence() == null)
			return;
		Sequence seq = exp.getSeqCamData().getSequence();
		Viewer v = seq.getFirstViewer();
		if (v != null) {
			IcyCanvas canvas = v.getCanvas();
			if (canvas != null && canvas.hasLayer(overlayThreshold))
				canvas.removeLayer(overlayThreshold);
		}
		seq.removeOverlay(overlayThreshold);
	}

}
