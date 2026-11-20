package plugins.fmp.multitools.tools.JComponents;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.awt.Dimension;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import org.junit.Test;

public class SelectedTabHeightPaneTest {
	@Test
	public void switchingTabsChangesHeightAndPreservesWidthAndTabHeaders() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			SelectedTabHeightPane tabs = new SelectedTabHeightPane();
			JPanel shortPanel = panel(400, 60, 30);
			JPanel tallPanel = panel(600, 240, 180);
			tabs.addTab("Common options", shortPanel);
			tabs.addTab("Spots", tallPanel);
			Dimension shortSize = tabs.getPreferredSize();
			Dimension shortMinimum = tabs.getMinimumSize();
			tabs.setSelectedIndex(1);
			Dimension tallSize = tabs.getPreferredSize();
			assertEquals(180, tallSize.height - shortSize.height);
			assertEquals(150, tabs.getMinimumSize().height - shortMinimum.height);
			assertEquals(shortSize.width, tallSize.width);
			assertTrue(shortSize.width >= 600);
			assertTrue(shortSize.height > 60);
			assertTrue(tallSize.height > 240);
			tabs.setSize(tallSize);
			tabs.doLayout();
			assertTrue(tallPanel.getHeight() >= 240);
			tabs.setSelectedIndex(0);
			assertEquals(shortSize, tabs.getPreferredSize());
			tabs.setSize(shortSize);
			tabs.doLayout();
			assertTrue(shortPanel.getHeight() >= 60);
		});
	}

	@Test
	public void emptyPaneAndExplicitSizesRemainSupported() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			SelectedTabHeightPane tabs = new SelectedTabHeightPane();
			assertTrue(tabs.getPreferredSize().height >= 0);
			tabs.addTab("Options", panel(400, 60, 30));
			tabs.setPreferredSize(new Dimension(700, 300));
			tabs.setMinimumSize(new Dimension(500, 200));
			assertEquals(new Dimension(700, 300), tabs.getPreferredSize());
			assertEquals(new Dimension(500, 200), tabs.getMinimumSize());
		});
	}

	private static JPanel panel(int width, int preferredHeight, int minimumHeight) {
		JPanel panel = new JPanel();
		panel.setPreferredSize(new Dimension(width, preferredHeight));
		panel.setMinimumSize(new Dimension(width, minimumHeight));
		return panel;
	}
}
