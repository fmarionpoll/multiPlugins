package plugins.fmp.multiSPOTS.dlg.browse;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JToggleButton;

import plugins.fmp.multiSPOTS.MultiSPOTS;
import plugins.fmp.multitools.tools.JComponents.JComboBoxExperimentLazy;
import plugins.fmp.multitools.tools.JComponents.SequenceNameListRenderer;

final class BrowseUi {

	private BrowseUi() {
	}

	static JPanel createMainGrid(JPanel navPanel, JPanel buttonPanel) {
		JPanel group2Panel = new JPanel();
		group2Panel.setLayout(new BoxLayout(group2Panel, BoxLayout.Y_AXIS));
		navPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		buttonPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		group2Panel.add(navPanel);
		group2Panel.add(buttonPanel);
		return group2Panel;
	}

	static JPanel createNavigationPanel(MultiSPOTS parent0, JButton previousButton, JButton nextButton) {
		JPanel navPanel = new JPanel(new BorderLayout());
		JComboBoxExperimentLazy combo = parent0.expListComboLazy;
		combo.setRenderer(new SequenceNameListRenderer());
		SequenceNameListRenderer.installWidePopup(combo);
		int bWidth = 30;
		int height = 20;
		previousButton.setPreferredSize(new Dimension(bWidth, height));
		nextButton.setPreferredSize(new Dimension(bWidth, height));
		combo.setPreferredSize(new Dimension(0, height));
		combo.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));

		navPanel.add(previousButton, BorderLayout.LINE_START);
		navPanel.add(combo, BorderLayout.CENTER);
		navPanel.add(nextButton, BorderLayout.LINE_END);
		return navPanel;
	}

	static JPanel createButtonPanel(JButton openButton, JButton searchButton, JButton closeButton,
			JToggleButton showFilterButton, JToggleButton showEditButton) {
		JPanel buttonPanel = new JPanel();
		buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.Y_AXIS));

		FlowLayout layout = new FlowLayout(FlowLayout.LEFT);
		layout.setVgap(1);
		layout.setHgap(4);

		JPanel row1 = new JPanel(layout);
		row1.setAlignmentX(0f);
		row1.add(openButton);
		row1.add(searchButton);
		row1.add(closeButton);

		JPanel row2 = new JPanel(layout);
		row2.setAlignmentX(0f);
		row2.add(showFilterButton);
		row2.add(showEditButton);

		buttonPanel.add(row1);
		buttonPanel.add(row2);
		return buttonPanel;
	}
}
