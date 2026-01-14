package plugins.fmp.multiSPOTS.tools.JComponents;

import javax.swing.JComboBox;

public class JComboBoxMs extends JComboBox<String> {
	/**
	 * 
	 */
	private static final long serialVersionUID = -618283271585890700L;

	String[] scale = new String[] { "ms", "s", "min", "h", "day" };

	public JComboBoxMs() {
		super();
		for (int i = 0; i < scale.length; i++)
			addItem(scale[i]);
	}

	public int getMsUnitValue() {
		int binsize = 1;
		int iselected = getSelectedIndex();
		switch (iselected) {
		case 1:
			binsize = 1000;
			break;
		case 2:
			binsize = 1000 * 60;
			break;
		case 3:
			binsize = 1000 * 60 * 60;
			break;
		case 4:
			binsize = 1000 * 60 * 60 * 24;
			break;
		case 0:
		default:
			break;
		}
		return binsize;
	}

}
