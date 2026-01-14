package plugins.fmp.multicafe.dlg.excel;

import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JPanel;

public class Move extends JPanel {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1290058998782225526L;

	JCheckBox xyCenterCheckBox = new JCheckBox("XY vs image", false);
	JCheckBox xyCageCheckBox = new JCheckBox("XY vs cage", true);
	JCheckBox xyTipCapsCheckBox = new JCheckBox("XY vs capillary", false);
	JCheckBox distanceCheckBox = new JCheckBox("distance", true);
	JCheckBox aliveCheckBox = new JCheckBox("alive", true);
	JCheckBox sleepCheckBox = new JCheckBox("sleep", true);
	JCheckBox rectSizeCheckBox = new JCheckBox("ellipse axes", false);

	JButton exportToXLSButton = new JButton("save XLS");
	JCheckBox deadEmptyCheckBox = new JCheckBox("dead=empty");

	void init(GridLayout capLayout) {
		setLayout(capLayout);

		FlowLayout flowLayout1 = new FlowLayout(FlowLayout.LEFT);
		flowLayout1.setVgap(0);
		JPanel panel0 = new JPanel(flowLayout1);
		panel0.add(xyCenterCheckBox);
		panel0.add(xyCageCheckBox);
		panel0.add(xyTipCapsCheckBox);
		panel0.add(rectSizeCheckBox);
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
