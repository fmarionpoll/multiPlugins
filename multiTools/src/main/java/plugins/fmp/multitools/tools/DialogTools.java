package plugins.fmp.multiSPOTS96.tools;

import java.awt.Component;
import java.awt.GridBagConstraints;

import javax.swing.JPanel;

public class DialogTools {
	public static void addFiveComponentOnARow(JPanel panel, Component comp1, Component comp2, Component comp3,
			Component comp4, Component comp5, GridBagConstraints c, int delta1, int delta2) {
		if (comp1 != null)
			panel.add(comp1, c);
		c.gridx += delta1;
		if (comp2 != null)
			panel.add(comp2, c);
		c.gridx += delta2;
		if (comp3 != null)
			panel.add(comp3, c);
		c.gridx += delta1;
		if (comp4 != null)
			panel.add(comp4, c);
		c.gridx += delta2;
		if (comp5 != null)
			panel.add(comp5, c);
	}

}
