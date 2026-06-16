package plugins.fmp.multiSPOTS.dlg.imageFilters;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

import javax.swing.JPanel;

/**
 * Tab panels used to share a {@link java.awt.GridLayout}(4,1) root, which forces
 * four equal-height rows and stretches short tabs. Use stacked rows with a
 * vertical filler so each row stays at its preferred height.
 */
public final class SpotsMeasuresUi {

	private SpotsMeasuresUi() {
	}

	public static void layoutStackedRows(JPanel root, JPanel... rows) {
		root.setLayout(new GridBagLayout());
		GridBagConstraints c = new GridBagConstraints();
		c.gridx = 0;
		c.fill = GridBagConstraints.HORIZONTAL;
		c.anchor = GridBagConstraints.WEST;
		c.weightx = 1;
		c.weighty = 0;
		c.insets = new Insets(0, 0, 0, 0);
		for (int i = 0; i < rows.length; i++) {
			c.gridy = i;
			root.add(rows[i], c);
		}
		c.gridy = rows.length;
		c.weighty = 1;
		c.fill = GridBagConstraints.BOTH;
		root.add(new JPanel(), c);
	}
}
