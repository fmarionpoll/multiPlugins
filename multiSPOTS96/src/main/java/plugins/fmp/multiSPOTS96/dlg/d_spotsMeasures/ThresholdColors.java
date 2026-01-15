package plugins.fmp.multiSPOTS96.dlg.d_spotsMeasures;

import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;

import plugins.fmp.multiSPOTS96.MultiSPOTS96;
import plugins.fmp.multitools.tools.JComponents.JComboBoxColorRenderer;

public class ThresholdColors extends JPanel implements PropertyChangeListener {

	/**
	 * 
	 */
	private static final long serialVersionUID = -4359876050505295400L;
	JComboBox<Color> colorPickCombo = new JComboBox<Color>();
	private JComboBoxColorRenderer colorPickComboRenderer = new JComboBoxColorRenderer(colorPickCombo);

	private String textPickAPixel = "Pick a pixel";
	private JButton pickColorButton = new JButton(textPickAPixel);
	private JButton deleteColorButton = new JButton("Delete color");
	JRadioButton rbL1 = new JRadioButton("L1");
	JRadioButton rbL2 = new JRadioButton("L2");
	JSpinner distanceSpinner = new JSpinner(new SpinnerNumberModel(10, 0, 800, 1));
	JRadioButton rbRGB = new JRadioButton("RGB");
	JRadioButton rbHSV = new JRadioButton("HSV");
	JRadioButton rbH1H2H3 = new JRadioButton("H1H2H3");
	private JLabel distanceLabel = new JLabel("Distance  ");
	private JLabel colorspaceLabel = new JLabel("Color space ");

	boolean isUpdatingDataFromComboAllowed = true;
	MultiSPOTS96 multiSpots = null;

	public void init(GridLayout capLayout, MultiSPOTS96 parent0) {

		this.multiSpots = parent0;
		JComponent panel = new JPanel(false);
		panel.setLayout(capLayout);

		colorPickCombo.setRenderer(colorPickComboRenderer);
		FlowLayout layoutLeft = new FlowLayout(FlowLayout.LEFT);
		JPanel panel0 = new JPanel(layoutLeft);
		panel0.add(pickColorButton);
		panel0.add(colorPickCombo);
		panel0.add(deleteColorButton);
		panel.add(panel0);

		JPanel panel1 = new JPanel(layoutLeft);
		ButtonGroup bgd = new ButtonGroup();
		bgd.add(rbL1);
		bgd.add(rbL2);
		panel1.add(distanceLabel);
		panel1.add(rbL1);
		panel1.add(rbL2);
		panel1.add(distanceSpinner);
		panel.add(panel1);

		ButtonGroup bgcs = new ButtonGroup();
		bgcs.add(rbRGB);
		bgcs.add(rbHSV);
		bgcs.add(rbH1H2H3);
		JPanel panel2 = new JPanel(layoutLeft);
		panel2.add(colorspaceLabel);
		panel2.add(rbRGB);
		panel2.add(rbHSV);
		panel2.add(rbH1H2H3);
		panel.add(panel2);

		rbL1.setSelected(true);
		rbRGB.setSelected(true);

		declareActionListeners();
	}

	private void declareActionListeners() {
		rbRGB.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
//				multiSpots.detectionParameters.colortransformop = EnumImageOp.NONE;
				updateThresholdOverlayParameters();
			}
		});

		rbHSV.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
//				multiSpots.detectionParameters.colortransformop = EnumImageOp.RGB_TO_HSV;
				updateThresholdOverlayParameters();
			}
		});

		rbH1H2H3.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
//				multiSpots.detectionParameters.colortransformop = EnumImageOp.RGB_TO_H1H2H3;
				updateThresholdOverlayParameters();
			}
		});

		rbL1.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
//				multiSpots.detectionParameters.colordistanceType = EnumColorDistanceType.L1;
				updateThresholdOverlayParameters();
			}
		});

		rbL2.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				updateThresholdOverlayParameters();
			}
		});

		deleteColorButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				if (colorPickCombo.getItemCount() > 0 && colorPickCombo.getSelectedIndex() >= 0)
					colorPickCombo.removeItemAt(colorPickCombo.getSelectedIndex());
				updateThresholdOverlayParameters();
			}
		});

		pickColorButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				pickColor();
			}
		});

		class ItemChangeListener implements ItemListener {
			@Override
			public void itemStateChanged(ItemEvent event) {
				if (event.getStateChange() == ItemEvent.SELECTED && isUpdatingDataFromComboAllowed) {
					updateThresholdOverlayParameters();
				}
			}
		}
		colorPickCombo.addItemListener(new ItemChangeListener());
	}

	void updateThresholdOverlayParameters() {

//		areatrack.detectionParameters.areaDetectionMode = EnumAreaDetection.COLORARRAY;
//		transferDialogToParameters(multiSpots.detectionParameters);
//		multiSpots.setOverlay(multiSpots.detectionParameters.displayOverlay);
	}

	private void pickColor() {

//		boolean bActiveTrapOverlay = false;

		if (pickColorButton.getText().contains("*") || pickColorButton.getText().contains(":")) {
			pickColorButton.setBackground(Color.LIGHT_GRAY);
			pickColorButton.setText(textPickAPixel);
//			bActiveTrapOverlay = false;
		} else {
			pickColorButton.setText("*" + textPickAPixel + "*");
			pickColorButton.setBackground(Color.DARK_GRAY);
//			bActiveTrapOverlay = true;
		}
//		multiSpots.vSequence.setMouseTrapOverlay(bActiveTrapOverlay, pickColorButton, colorPickCombo);
	}

	@Override
	public void propertyChange(PropertyChangeEvent evt) {
		// TODO Auto-generated method stub

	}

//	public void transferParametersToDialog(DetectionParameters detectionParameters) {
//		
//		isUpdatingDataFromComboAllowed = false;
//		colorPickCombo.removeAllItems();
//		int nitems = detectionParameters.colorarray.size();
//		for (int i = 0; i < nitems; i++) {
//			Color colorItem = detectionParameters.colorarray.get(i);
//			colorPickCombo.addItem(colorItem);
//		}
//		isUpdatingDataFromComboAllowed = true;
//		
//		if (detectionParameters.colordistanceType == EnumColorDistanceType.L1)
//			rbL1.setSelected(true);
//		else
//			rbL2.setSelected(true);
//		
//		switch (detectionParameters.colortransformop) {
//			case RGB_TO_HSV:
//				rbHSV.setSelected(true);
//				break;
//			case RGB_TO_H1H2H3:
//				rbH1H2H3.setSelected(true);
//				break;
//			case NONE:
//			default:
//				rbRGB.setSelected(true);
//				break;
//		}
//		
//		distanceSpinner.setValue(detectionParameters.colorthreshold);
//	}
//	
//	public void transferDialogToParameters(DetectionParameters detectionParameters) {
//		
//		detectionParameters.colorthreshold = (int) distanceSpinner.getValue();
//		
//		if (rbHSV.isSelected()) 
//			detectionParameters.colortransformop = EnumImageOp.RGB_TO_HSV;
//		else if (rbH1H2H3.isSelected())
//			detectionParameters.colortransformop = EnumImageOp.RGB_TO_H1H2H3;
//		else 
//			detectionParameters.colortransformop = EnumImageOp.COLORARRAY1;
//		
//		detectionParameters.colorarray.clear();
//		for (int i = 0; i < colorPickCombo.getItemCount(); i++) {
//			Color colorItem = colorPickCombo.getItemAt(i);
//			detectionParameters.colorarray.add(colorItem);
//		}
//		
//		if (rbL1.isSelected())
//			detectionParameters.colordistanceType = EnumColorDistanceType.L1;
//		else
//			detectionParameters.colordistanceType = EnumColorDistanceType.L2;
//	}

}
