package plugins.fmp.multiSPOTS96.dlg.spotsMeasures2;

import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.ArrayList;

import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import icy.image.IcyBufferedImage;
import icy.sequence.Sequence;
import plugins.fmp.multiSPOTS96.MultiSPOTS96;
import plugins.fmp.multiSPOTS96.dlg.spotsMeasures.SpotsMeasuresUi;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.spots.Spots;
import plugins.fmp.multitools.series.options.BuildSeriesOptions;
import plugins.fmp.multitools.service.SpotColorBackgroundSampler;
import plugins.fmp.multitools.tools.imageTransform.SpotThresholdColorSpace;
import plugins.fmp.multitools.tools.JComponents.JComboBoxColorRenderer;
import plugins.fmp.multitools.tools.overlay.OverlayTrapMouse;

public class ThresholdColors extends JPanel {

	private static final long serialVersionUID = -4359876050505295400L;

	private final JComboBox<Color> colorPickCombo = new JComboBox<>();
	private final JComboBoxColorRenderer colorPickComboRenderer = new JComboBoxColorRenderer(colorPickCombo);

	private final String textPickAPixel = "Pick (spot)";
	private final JButton pickColorButton = new JButton(textPickAPixel);
	private final JButton deleteColorButton = new JButton("Delete color");
	private final JRadioButton rbL1 = new JRadioButton("L1");
	private final JRadioButton rbL2 = new JRadioButton("L2");
	private final JSpinner distanceSpinner = new JSpinner(new SpinnerNumberModel(10, 0, 800, 1));
	private final JRadioButton rbRGB = new JRadioButton("RGB");
	private final JRadioButton rbHSV = new JRadioButton("HSV");
	private final JRadioButton rbH1H2H3 = new JRadioButton("H1H2H3");
	private final JLabel distanceLabel = new JLabel("Distance  ");
	private final JLabel colorspaceLabel = new JLabel("Color space ");

	private final JComboBox<Color> excludeColorCombo = new JComboBox<>();
	private final JComboBoxColorRenderer excludeColorComboRenderer = new JComboBoxColorRenderer(excludeColorCombo);
	private final String textPickExclude = "Pick (exclude)";
	private final JButton pickExcludeButton = new JButton(textPickExclude);
	private final JButton deleteExcludeButton = new JButton("Del excl.");
	private final JLabel excludeDistLabel = new JLabel("Excl. dist ");
	private final JSpinner excludeDistanceSpinner = new JSpinner(new SpinnerNumberModel(12, 0, 800, 1));
	private final JButton sampleBackgroundButton = new JButton("Sample bg (outside ROIs)");

	private boolean isUpdatingDataFromComboAllowed = true;
	private MultiSPOTS96 multiSpots = null;
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

	public void init(MultiSPOTS96 parent0) {
		this.multiSpots = parent0;
		colorPickCombo.setRenderer(colorPickComboRenderer);
		excludeColorCombo.setRenderer(excludeColorComboRenderer);
		FlowLayout layoutLeft = new FlowLayout(FlowLayout.LEFT);
		layoutLeft.setVgap(0);
		JPanel panel0 = new JPanel(layoutLeft);
		panel0.add(pickColorButton);
		panel0.add(colorPickCombo);
		panel0.add(deleteColorButton);

		JPanel panel1 = new JPanel(layoutLeft);
		ButtonGroup bgd = new ButtonGroup();
		bgd.add(rbL1);
		bgd.add(rbL2);
		panel1.add(distanceLabel);
		panel1.add(rbL1);
		panel1.add(rbL2);
		panel1.add(distanceSpinner);

		ButtonGroup bgcs = new ButtonGroup();
		bgcs.add(rbRGB);
		bgcs.add(rbHSV);
		bgcs.add(rbH1H2H3);
		JPanel panel2 = new JPanel(layoutLeft);
		panel2.add(colorspaceLabel);
		panel2.add(rbRGB);
		panel2.add(rbHSV);
		panel2.add(rbH1H2H3);

		JPanel panelEx = new JPanel(layoutLeft);
		panelEx.add(new JLabel("Exclude "));
		panelEx.add(pickExcludeButton);
		panelEx.add(excludeColorCombo);
		panelEx.add(deleteExcludeButton);
		panelEx.add(excludeDistLabel);
		panelEx.add(excludeDistanceSpinner);

		JPanel panelSample = new JPanel(layoutLeft);
		panelSample.add(sampleBackgroundButton);

		SpotsMeasuresUi.layoutStackedRows(this, panel0, panel1, panel2, panelEx, panelSample);

		rbL1.setSelected(true);
		rbRGB.setSelected(true);
		lastComboCount = colorPickCombo.getItemCount();
		lastExcludeComboCount = excludeColorCombo.getItemCount();

		declareActionListeners();
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
		for (int i = 0; i < colorPickCombo.getItemCount(); i++) {
			list.add(colorPickCombo.getItemAt(i));
		}
		return list;
	}

	public ArrayList<Color> getExcludeReferenceColors() {
		ArrayList<Color> list = new ArrayList<>();
		for (int i = 0; i < excludeColorCombo.getItemCount(); i++) {
			list.add(excludeColorCombo.getItemAt(i));
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
		return (int) distanceSpinner.getValue();
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

		distanceSpinner.addChangeListener(new ChangeListener() {
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

		deleteColorButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				if (colorPickCombo.getItemCount() > 0 && colorPickCombo.getSelectedIndex() >= 0) {
					colorPickCombo.removeItemAt(colorPickCombo.getSelectedIndex());
				}
				lastComboCount = colorPickCombo.getItemCount();
				updateThresholdOverlayParameters();
			}
		});
		deleteExcludeButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				if (excludeColorCombo.getItemCount() > 0 && excludeColorCombo.getSelectedIndex() >= 0) {
					excludeColorCombo.removeItemAt(excludeColorCombo.getSelectedIndex());
				}
				lastExcludeComboCount = excludeColorCombo.getItemCount();
				updateThresholdOverlayParameters();
			}
		});

		pickColorButton.addActionListener(new ActionListener() {
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

		colorPickCombo.addItemListener(new ItemListener() {
			@Override
			public void itemStateChanged(ItemEvent event) {
				if (event.getStateChange() != ItemEvent.SELECTED || !isUpdatingDataFromComboAllowed) {
					return;
				}
				int n = colorPickCombo.getItemCount();
				if (pickOverlayActive && n > lastComboCount) {
					finishPickOverlay();
				}
				lastComboCount = n;
				updateThresholdOverlayParameters();
			}
		});
		excludeColorCombo.addItemListener(new ItemListener() {
			@Override
			public void itemStateChanged(ItemEvent event) {
				if (event.getStateChange() != ItemEvent.SELECTED || !isUpdatingDataFromComboAllowed) {
					return;
				}
				int n = excludeColorCombo.getItemCount();
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
		if (exp != null && exp.getSeqCamData() != null && exp.getSeqCamData().getSequence() != null && pickOverlay != null) {
			exp.getSeqCamData().getSequence().removeOverlay(pickOverlay);
		}
		pickColorButton.setText(textPickAPixel);
		pickColorButton.setBackground(Color.LIGHT_GRAY);
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
			pickColorButton.setText(textPickAPixel);
			pickColorButton.setBackground(Color.LIGHT_GRAY);
		} else {
			if (pickOverlay == null) {
				pickOverlay = new OverlayTrapMouse(pickColorButton, colorPickCombo);
			}
			seq.addOverlay(pickOverlay);
			pickOverlayActive = true;
			pickColorButton.setText("*" + textPickAPixel + "*");
			pickColorButton.setBackground(Color.DARK_GRAY);
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
				pickExcludeOverlay = new OverlayTrapMouse(pickExcludeButton, excludeColorCombo);
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
		excludeColorCombo.removeAllItems();
		for (Color c : proto) {
			excludeColorCombo.addItem(c);
		}
		isUpdatingDataFromComboAllowed = true;
		lastExcludeComboCount = excludeColorCombo.getItemCount();
		if (excludeColorCombo.getItemCount() > 0 && (int) excludeDistanceSpinner.getValue() <= 0) {
			excludeDistanceSpinner.setValue(12);
		}
		updateThresholdOverlayParameters();
	}
}
