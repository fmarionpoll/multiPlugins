package plugins.fmp.multiSPOTS96.dlg.browse;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JPanel;

import plugins.fmp.multiSPOTS96.MultiSPOTS96;
import plugins.fmp.multitools.tools.JComponents.SequenceNameListRenderer;

final class Spots96BrowseUi {

	private Spots96BrowseUi() {
	}

	static JPanel createMainGrid(JPanel navPanel, JPanel buttonPanel) {
		JPanel group2Panel = new JPanel(new GridLayout(2, 1));
		group2Panel.add(navPanel);
		group2Panel.add(buttonPanel);
		return group2Panel;
	}

	static JPanel createNavigationPanel(MultiSPOTS96 parent0, JButton previousButton, JButton nextButton) {
		JPanel navPanel = new JPanel(new BorderLayout());
		SequenceNameListRenderer renderer = new SequenceNameListRenderer();
		parent0.expListComboLazy.setRenderer(renderer);
		int bWidth = 30;
		int height = 20;
		previousButton.setPreferredSize(new Dimension(bWidth, height));
		nextButton.setPreferredSize(new Dimension(bWidth, height));

		navPanel.add(previousButton, BorderLayout.LINE_START);
		navPanel.add(parent0.expListComboLazy, BorderLayout.CENTER);
		navPanel.add(nextButton, BorderLayout.LINE_END);
		return navPanel;
	}

	static JPanel createButtonPanel(JButton openButton, JButton searchButton, JButton closeButton,
			JCheckBox filteredCheck) {
		JPanel buttonPanel = new JPanel(new BorderLayout());
		FlowLayout layout = new FlowLayout(FlowLayout.LEFT);
		layout.setVgap(1);
		JPanel subPanel = new JPanel(layout);
		subPanel.add(openButton);
		subPanel.add(searchButton);
		subPanel.add(closeButton);
		subPanel.add(filteredCheck);
		buttonPanel.add(subPanel, BorderLayout.LINE_START);
		return buttonPanel;
	}
}
