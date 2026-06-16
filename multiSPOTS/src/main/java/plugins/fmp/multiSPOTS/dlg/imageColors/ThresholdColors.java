package plugins.fmp.multiSPOTS.dlg.imageColors;

import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.ArrayList;

import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSpinner;
import javax.swing.JToggleButton;
import javax.swing.SpinnerNumberModel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import icy.image.IcyBufferedImage;
import icy.sequence.Sequence;
import plugins.fmp.multiSPOTS.MultiSPOTS;
import plugins.fmp.multiSPOTS.dlg.imageFilters.SpotsMeasuresUi;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.spots.Spots;
import plugins.fmp.multitools.series.options.BuildSeriesOptions;
import plugins.fmp.multitools.service.SpotColorBackgroundSampler;
import plugins.fmp.multitools.tools.JComponents.JComboBoxColorRenderer;
import plugins.fmp.multitools.tools.imageTransform.SpotThresholdColorSpace;
import plugins.fmp.multitools.tools.overlay.OverlayTrapMouse;

public class ThresholdColors extends JPanel {

	private static final long serialVersionUID = -4359876050505295400L;

	private final JComboBox<Color> colorIncludeCombo = new JComboBox<>();
	private final JComboBoxColorRenderer colorPickComboRenderer = new JComboBoxColorRenderer(colorIncludeCombo);

	private final String textPickAPixel = "Pick (include)";
	private final JButton pickIncludeButton = new JButton(textPickAPixel);
	private final JButton deleteIncludeButton = new JButton("Delete color");
	private final JRadioButton rbL1 = new JRadioButton("L1");
	private final JRadioButton rbL2 = new JRadioButton("L2");
	private final JSpinner includeDistanceSpinner = new JSpinner(new SpinnerNumberModel(10, 0, 800, 1));
	private final JRadioButton rbRGB = new JRadioButton("RGB");
	private final JRadioButton rbHSV = new JRadioButton("HSV");
	private final JRadioButton rbH1H2H3 = new JRadioButton("H1H2H3");
	private final JLabel distanceLabel = new JLabel("Distance  ");
	private final JLabel colorspaceLabel = new JLabel("Color space ");

	private final JComboBox<Color> colorExcludeCombo = new JComboBox<>();
	private final JComboBoxColorRenderer excludeColorComboRenderer = new JComboBoxColorRenderer(colorExcludeCombo);
	private final String textPickExclude = "Pick (exclude)";
	private final JButton pickExcludeButton = new JButton(textPickExclude);
	private final JButton deleteExcludeButton = new JButton("Del excl.");
	private final JLabel excludeDistLabel = new JLabel("Excl. dist ");
	private final JSpinner excludeDistanceSpinner = new JSpinner(new SpinnerNumberModel(12, 0, 800, 1));
	private final JButton sampleBackgroundButton = new JButton("Sample bg (outside ROIs)");

	private final String[] spotRoiCountModes = new String[] { "far from refs", "matching refs" };
	private final JComboBox<String> spotsDirectionComboBox = new JComboBox<>(spotRoiCountModes);
	private final JSpinner spotsThresholdSpinner = new JSpinner(new SpinnerNumberModel(35, 0, 255, 1));
	private final JToggleButton spotsViewButton = new JToggleButton("View");
	private final JCheckBox spotsOverlayCheckBox = new JCheckBox("overlay");

	private boolean isUpdatingDataFromComboAllowed = true;
	private MultiSPOTS multiSpots = null;
	private Runnable onSettingsChanged;

	private OverlayTrapMouse pickOverlay;
	private boolean pickOverlayActive;
	private int lastComboCount;

	private OverlayTrapMouse pickExcludeOverlay;
	private boolean pickExcludeOverlayActive;
	private int lastExcludeComboCount;

	public void setOnSettingsChanged(Runnable onSettingsChanged) {
		this.onSettingsChanged = onSettingsChanged;
	}

	public void init(MultiSPOTS parent0) {
		this.multiSpots = parent0;
		colorIncludeCombo.setRenderer(colorPickComboRenderer);
		colorExcludeCombo.setRenderer(excludeColorComboRenderer);
		FlowLayout layoutLeft = new FlowLayout(FlowLayout.LEFT);
		layoutLeft.setVgap(0);

		JPanel panel1 = new JPanel(layoutLeft);
		ButtonGroup bgd = new ButtonGroup();
		bgd.add(rbL1);
		bgd.add(rbL2);
		panel1.add(distanceLabel);
		panel1.add(rbL1);
		panel1.add(rbL2);

		ButtonGroup bgcs = new ButtonGroup();
		bgcs.add(rbRGB);
		bgcs.add(rbHSV);
		bgcs.add(rbH1H2H3);

		panel1.add(colorspaceLabel);
		panel1.add(rbRGB);
		panel1.add(rbHSV);
		panel1.add(rbH1H2H3);

		JPanel panelInclude = new JPanel(layoutLeft);
		panelInclude.add(pickIncludeButton);
		panelInclude.add(colorIncludeCombo);
		panelInclude.add(deleteIncludeButton);
		panelInclude.add(new JLabel("Incl. dist"));
		panelInclude.add(includeDistanceSpinner);

		JPanel panelExclude = new JPanel(layoutLeft);
		panelExclude.add(pickExcludeButton);
		panelExclude.add(colorExcludeCombo);
		panelExclude.add(deleteExcludeButton);
		panelExclude.add(excludeDistLabel);
		panelExclude.add(excludeDistanceSpinner);

		JPanel panelSample = new JPanel(layoutLeft);
		panelSample.add(sampleBackgroundButton);

		JPanel panelSpotRoi = new JPanel(layoutLeft);
		panelSpotRoi.add(new JLabel("ROI count"));
		spotsDirectionComboBox.setToolTipText("<html>Detection only. The color <b>Distance</b> control builds the mask.<br>"
				+ "This control chooses whether spot ROI statistics count pixels that <b>match</b> your reference colors or pixels <b>outside</b> that match.<br>"
				+ "Default &quot;matching refs&quot; matches the red overlay (mask drawn on matched pixels).</html>");
		spotsThresholdSpinner.setToolTipText(
				"<html>Used only after the color step produces a 0/255 mask. For typical thresholds (0&ndash;254) it does <b>not</b> change which pixels match;<br>"
						+ "tune the mask with the <b>Distance</b> control. Very high values (e.g. &ge; 255) can exclude all pixels.</html>");
		panelSpotRoi.add(spotsDirectionComboBox);
		panelSpotRoi.add(new JLabel("post step "));
		panelSpotRoi.add(spotsThresholdSpinner);
		panelSpotRoi.add(spotsViewButton);
		panelSpotRoi.add(spotsOverlayCheckBox);

		SpotsMeasuresUi.layoutStackedRows(this, panel1, panelInclude, panelExclude, panelSample, panelSpotRoi);

		rbL1.setSelected(true);
		rbRGB.setSelected(true);
		spotsDirectionComboBox.setSelectedIndex(1);
		spotsOverlayCheckBox.setEnabled(false);
		lastComboCount = colorIncludeCombo.getItemCount();
		lastExcludeComboCount = colorExcludeCombo.getItemCount();

		declareActionListeners();
	}

	public JToggleButton getSpotsViewButton() {
		return spotsViewButton;
	}

	public JCheckBox getSpotsOverlayCheckBox() {
		return spotsOverlayCheckBox;
	}

	/**
	 * When fly preview is turned on, spot color preview must be turned off (mutual exclusion).
	 */
	public void clearSpotsPreviewForFlyExclusive() {
		spotsViewButton.setSelected(false);
		spotsOverlayCheckBox.setEnabled(false);
	}

	private void fireSettingsChanged() {
		if (onSettingsChanged != null) {
			onSettingsChanged.run();
		}
	}

	private void updateThresholdOverlayParameters() {
		fireSettingsChanged();
	}

	public ArrayList<Color> getReferenceColors() {
		ArrayList<Color> list = new ArrayList<>();
		for (int i = 0; i < colorIncludeCombo.getItemCount(); i++) {
			list.add(colorIncludeCombo.getItemAt(i));
		}
		return list;
	}

	public ArrayList<Color> getExcludeReferenceColors() {
		ArrayList<Color> list = new ArrayList<>();
		for (int i = 0; i < colorExcludeCombo.getItemCount(); i++) {
			list.add(colorExcludeCombo.getItemAt(i));
		}
		return list;
	}

	public int getExcludeDistanceMax() {
		return (int) excludeDistanceSpinner.getValue();
	}

	public int getColorDistanceTypeInt() {
		return rbL1.isSelected() ? 1 : 2;
	}

	public int getColorDistanceMax() {
		return (int) includeDistanceSpinner.getValue();
	}

	public SpotThresholdColorSpace getSpotThresholdColorSpace() {
		if (rbHSV.isSelected()) {
			return SpotThresholdColorSpace.HSV;
		}
		if (rbH1H2H3.isSelected()) {
			return SpotThresholdColorSpace.H1H2H3;
		}
		return SpotThresholdColorSpace.RGB;
	}

	public void applyToBuildSeriesOptions(BuildSeriesOptions options) {
		if (options == null) {
			return;
		}
		options.spotColorReferenceList.clear();
		options.spotColorReferenceList.addAll(getReferenceColors());
		options.spotColorDistanceType = getColorDistanceTypeInt();
		options.spotColorDistanceMax = getColorDistanceMax();
		options.spotColorSpace = getSpotThresholdColorSpace();

		options.spotColorExcludeList.clear();
		options.spotColorExcludeList.addAll(getExcludeReferenceColors());
		int exd = getExcludeDistanceMax();
		options.spotColorExcludeDistanceMax = options.spotColorExcludeList.isEmpty() ? 0 : exd;

		options.spotThresholdUp = spotsDirectionComboBox.getSelectedIndex() == 1;
		options.spotThreshold = (int) spotsThresholdSpinner.getValue();
	}

	/**
	 * Fills this widget from {@code options} (inverse of
	 * {@link #applyToBuildSeriesOptions}). When {@code notifyAfter} is true, runs
	 * the same notification path as a user edit (overlay refresh if wired).
	 */
	public void applyFromBuildSeriesOptions(BuildSeriesOptions options, boolean notifyAfter) {
		if (options == null) {
			return;
		}
		boolean prev = isUpdatingDataFromComboAllowed;
		isUpdatingDataFromComboAllowed = false;
		try {
			colorIncludeCombo.removeAllItems();
			if (options.spotColorReferenceList != null) {
				for (Color c : options.spotColorReferenceList) {
					colorIncludeCombo.addItem(c);
				}
			}
			colorExcludeCombo.removeAllItems();
			if (options.spotColorExcludeList != null) {
				for (Color c : options.spotColorExcludeList) {
					colorExcludeCombo.addItem(c);
				}
			}
			int dt = options.spotColorDistanceType;
			rbL1.setSelected(dt != 2);
			rbL2.setSelected(dt == 2);
			includeDistanceSpinner.setValue(Math.max(0, Math.min(800, options.spotColorDistanceMax)));
			SpotThresholdColorSpace sp = options.spotColorSpace != null ? options.spotColorSpace
					: SpotThresholdColorSpace.RGB;
			rbRGB.setSelected(sp == SpotThresholdColorSpace.RGB);
			rbHSV.setSelected(sp == SpotThresholdColorSpace.HSV);
			rbH1H2H3.setSelected(sp == SpotThresholdColorSpace.H1H2H3);
			int exd = options.spotColorExcludeDistanceMax;
			if (options.spotColorExcludeList == null || options.spotColorExcludeList.isEmpty()) {
				excludeDistanceSpinner.setValue(0);
			} else {
				excludeDistanceSpinner.setValue(Math.max(0, Math.min(800, exd)));
			}
			lastComboCount = colorIncludeCombo.getItemCount();
			lastExcludeComboCount = colorExcludeCombo.getItemCount();
			spotsDirectionComboBox.setSelectedIndex(options.spotThresholdUp ? 1 : 0);
			spotsThresholdSpinner.setValue(Math.max(0, Math.min(255, options.spotThreshold)));
		} finally {
			isUpdatingDataFromComboAllowed = prev;
		}
		if (notifyAfter) {
			updateThresholdOverlayParameters();
		}
	}

	public void applyFromBuildSeriesOptions(BuildSeriesOptions options) {
		applyFromBuildSeriesOptions(options, true);
	}

	/**
	 * After programmatic model changes (e.g. XML preset), fix selection and repaint
	 * (custom color renderer).
	 */
	public void refreshComboPresentationAfterBulkLoad() {
		if (colorIncludeCombo.getItemCount() > 0) {
			colorIncludeCombo.setSelectedIndex(0);
		} else {
			colorIncludeCombo.setSelectedIndex(-1);
		}
		if (colorExcludeCombo.getItemCount() > 0) {
			colorExcludeCombo.setSelectedIndex(0);
		} else {
			colorExcludeCombo.setSelectedIndex(-1);
		}
		colorIncludeCombo.repaint();
		colorExcludeCombo.repaint();
		revalidate();
		repaint();
	}

	private void declareActionListeners() {
		ActionListener fire = new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				updateThresholdOverlayParameters();
			}
		};
		rbRGB.addActionListener(fire);
		rbHSV.addActionListener(fire);
		rbH1H2H3.addActionListener(fire);
		rbL1.addActionListener(fire);
		rbL2.addActionListener(fire);

		includeDistanceSpinner.addChangeListener(new ChangeListener() {
			@Override
			public void stateChanged(ChangeEvent e) {
				updateThresholdOverlayParameters();
			}
		});
		excludeDistanceSpinner.addChangeListener(new ChangeListener() {
			@Override
			public void stateChanged(ChangeEvent e) {
				updateThresholdOverlayParameters();
			}
		});

		deleteIncludeButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				if (colorIncludeCombo.getItemCount() > 0 && colorIncludeCombo.getSelectedIndex() >= 0) {
					colorIncludeCombo.removeItemAt(colorIncludeCombo.getSelectedIndex());
				}
				lastComboCount = colorIncludeCombo.getItemCount();
				updateThresholdOverlayParameters();
			}
		});
		deleteExcludeButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				if (colorExcludeCombo.getItemCount() > 0 && colorExcludeCombo.getSelectedIndex() >= 0) {
					colorExcludeCombo.removeItemAt(colorExcludeCombo.getSelectedIndex());
				}
				lastExcludeComboCount = colorExcludeCombo.getItemCount();
				updateThresholdOverlayParameters();
			}
		});

		pickIncludeButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				pickColor();
			}
		});
		pickExcludeButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				pickExcludeColor();
			}
		});

		sampleBackgroundButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				sampleBackgroundFromRois();
			}
		});

		spotsDirectionComboBox.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				updateThresholdOverlayParameters();
			}
		});
		spotsThresholdSpinner.addChangeListener(new ChangeListener() {
			@Override
			public void stateChanged(ChangeEvent e) {
				updateThresholdOverlayParameters();
			}
		});

		colorIncludeCombo.addItemListener(new ItemListener() {
			@Override
			public void itemStateChanged(ItemEvent event) {
				if (event.getStateChange() != ItemEvent.SELECTED || !isUpdatingDataFromComboAllowed) {
					return;
				}
				int n = colorIncludeCombo.getItemCount();
				if (pickOverlayActive && n > lastComboCount) {
					finishPickOverlay();
				}
				lastComboCount = n;
				updateThresholdOverlayParameters();
			}
		});
		colorExcludeCombo.addItemListener(new ItemListener() {
			@Override
			public void itemStateChanged(ItemEvent event) {
				if (event.getStateChange() != ItemEvent.SELECTED || !isUpdatingDataFromComboAllowed) {
					return;
				}
				int n = colorExcludeCombo.getItemCount();
				if (pickExcludeOverlayActive && n > lastExcludeComboCount) {
					finishPickExcludeOverlay();
				}
				lastExcludeComboCount = n;
				updateThresholdOverlayParameters();
			}
		});
	}

	private void finishPickOverlay() {
		pickOverlayActive = false;
		if (multiSpots == null) {
			return;
		}
		Experiment exp = (Experiment) multiSpots.expListComboLazy.getSelectedItem();
		if (exp != null && exp.getSeqCamData() != null && exp.getSeqCamData().getSequence() != null
				&& pickOverlay != null) {
			exp.getSeqCamData().getSequence().removeOverlay(pickOverlay);
		}
		pickIncludeButton.setText(textPickAPixel);
		pickIncludeButton.setBackground(Color.LIGHT_GRAY);
	}

	private void finishPickExcludeOverlay() {
		pickExcludeOverlayActive = false;
		if (multiSpots == null) {
			return;
		}
		Experiment exp = (Experiment) multiSpots.expListComboLazy.getSelectedItem();
		if (exp != null && exp.getSeqCamData() != null && exp.getSeqCamData().getSequence() != null
				&& pickExcludeOverlay != null) {
			exp.getSeqCamData().getSequence().removeOverlay(pickExcludeOverlay);
		}
		pickExcludeButton.setText(textPickExclude);
		pickExcludeButton.setBackground(Color.LIGHT_GRAY);
	}

	private void pickColor() {
		if (multiSpots == null) {
			return;
		}
		Experiment exp = (Experiment) multiSpots.expListComboLazy.getSelectedItem();
		if (exp == null || exp.getSeqCamData() == null) {
			return;
		}
		Sequence seq = exp.getSeqCamData().getSequence();
		if (seq == null) {
			return;
		}
		if (pickOverlayActive) {
			if (pickOverlay != null) {
				seq.removeOverlay(pickOverlay);
			}
			pickOverlayActive = false;
			pickIncludeButton.setText(textPickAPixel);
			pickIncludeButton.setBackground(Color.LIGHT_GRAY);
		} else {
			if (pickOverlay == null) {
				pickOverlay = new OverlayTrapMouse(pickIncludeButton, colorIncludeCombo);
			}
			seq.addOverlay(pickOverlay);
			pickOverlayActive = true;
			pickIncludeButton.setText("*" + textPickAPixel + "*");
			pickIncludeButton.setBackground(Color.DARK_GRAY);
		}
	}

	private void pickExcludeColor() {
		if (multiSpots == null) {
			return;
		}
		Experiment exp = (Experiment) multiSpots.expListComboLazy.getSelectedItem();
		if (exp == null || exp.getSeqCamData() == null) {
			return;
		}
		Sequence seq = exp.getSeqCamData().getSequence();
		if (seq == null) {
			return;
		}
		if (pickExcludeOverlayActive) {
			if (pickExcludeOverlay != null) {
				seq.removeOverlay(pickExcludeOverlay);
			}
			pickExcludeOverlayActive = false;
			pickExcludeButton.setText(textPickExclude);
			pickExcludeButton.setBackground(Color.LIGHT_GRAY);
		} else {
			if (pickExcludeOverlay == null) {
				pickExcludeOverlay = new OverlayTrapMouse(pickExcludeButton, colorExcludeCombo);
			}
			seq.addOverlay(pickExcludeOverlay);
			pickExcludeOverlayActive = true;
			pickExcludeButton.setText("*" + textPickExclude + "*");
			pickExcludeButton.setBackground(Color.DARK_GRAY);
		}
	}

	private void sampleBackgroundFromRois() {
		if (multiSpots == null) {
			return;
		}
		Experiment exp = (Experiment) multiSpots.expListComboLazy.getSelectedItem();
		if (exp == null || exp.getSeqCamData() == null || exp.getSeqCamData().getSequence() == null) {
			return;
		}
		Sequence seq = exp.getSeqCamData().getSequence();
		IcyBufferedImage img = seq.getImage(0, 0);
		if (img == null) {
			return;
		}
		Spots spots = exp.getSpots();
		if (spots == null) {
			return;
		}
		ArrayList<Color> proto = SpotColorBackgroundSampler.sampleOutsideSpotRois(img, spots, 48, 12000);
		isUpdatingDataFromComboAllowed = false;
		colorExcludeCombo.removeAllItems();
		for (Color c : proto) {
			colorExcludeCombo.addItem(c);
		}
		isUpdatingDataFromComboAllowed = true;
		lastExcludeComboCount = colorExcludeCombo.getItemCount();
		if (colorExcludeCombo.getItemCount() > 0 && (int) excludeDistanceSpinner.getValue() <= 0) {
			excludeDistanceSpinner.setValue(12);
		}
		updateThresholdOverlayParameters();
	}
}
