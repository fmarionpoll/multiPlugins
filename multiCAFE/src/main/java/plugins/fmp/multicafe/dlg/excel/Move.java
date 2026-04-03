package plugins.fmp.multicafe.dlg.excel;

import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.plaf.basic.BasicComboBoxRenderer;

import plugins.fmp.multitools.experiment.cage.FlyPositionAxisReference;

public class Move extends JPanel {

	private static final long serialVersionUID = 1290058998782225526L;

	JCheckBox xyCenterCheckBox = new JCheckBox("XY vs image", true);
	JCheckBox yCageTopCheckBox = new JCheckBox("Y vs cage top", true);
	JCheckBox yCageBottomCheckBox = new JCheckBox("Y vs cage bottom", true);
	JComboBox<FlyPositionAxisReference> flyAxisReferenceCombo = new JComboBox<>(
			new DefaultComboBoxModel<>(FlyPositionAxisReference.values()));
	JCheckBox flyClampCageCheckBox = new JCheckBox("Clamp to cage", false);
	JCheckBox xyTipCapsCheckBox = new JCheckBox("XY vs capillary", false);
	JCheckBox distanceCheckBox = new JCheckBox("distance", false);
	JCheckBox aliveCheckBox = new JCheckBox("alive", false);
	JCheckBox sleepCheckBox = new JCheckBox("sleep", false);

	JButton exportToXLSButton = new JButton("save XLS");
	JCheckBox deadEmptyCheckBox = new JCheckBox("dead=empty");

	void init(GridLayout capLayout) {
		setLayout(capLayout);

		flyAxisReferenceCombo.setSelectedItem(FlyPositionAxisReference.LEGACY_IMAGE_TOP);
		flyAxisReferenceCombo.setRenderer(new BasicComboBoxRenderer() {
			private static final long serialVersionUID = 1L;

			@Override
			public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected,
					boolean cellHasFocus) {
				if (value instanceof FlyPositionAxisReference) {
					value = ((FlyPositionAxisReference) value).getUiLabel();
				}
				return super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
			}
		});

		FlowLayout flowLayout1 = new FlowLayout(FlowLayout.LEFT);
		flowLayout1.setVgap(0);
		JPanel panel0 = new JPanel(flowLayout1);
		panel0.add(xyCenterCheckBox);
		panel0.add(yCageTopCheckBox);
		panel0.add(yCageBottomCheckBox);
		panel0.add(new JLabel("Distance 0 at:"));
		panel0.add(flyAxisReferenceCombo);
		panel0.add(flyClampCageCheckBox);
		panel0.add(xyTipCapsCheckBox);
		add(panel0);

		JPanel panel1 = new JPanel(flowLayout1);
		panel1.add(distanceCheckBox);
		panel1.add(sleepCheckBox);
		panel1.add(aliveCheckBox);
		panel1.add(deadEmptyCheckBox);
		add(panel1);

		FlowLayout flowLayout2 = new FlowLayout(FlowLayout.RIGHT);
		flowLayout2.setVgap(0);
		JPanel panel2 = new JPanel(flowLayout2);
		panel2.add(exportToXLSButton);
		add(panel2);

		defineActionListeners();
	}

	private void defineActionListeners() {
		exportToXLSButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				firePropertyChange("EXPORT_MOVEDATA", false, true);
			}
		});
	}

}
