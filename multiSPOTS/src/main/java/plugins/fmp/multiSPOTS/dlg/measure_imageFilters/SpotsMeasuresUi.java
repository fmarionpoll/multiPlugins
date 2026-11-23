package plugins.fmp.multiSPOTS.dlg.measure_imageFilters;

import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

import javax.swing.DefaultListCellRenderer;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;

import plugins.fmp.multitools.tools.results.EnumResults;

/**
 * Tab panels used to share a {@link java.awt.GridLayout}(4,1) root, which forces
 * four equal-height rows and stretches short tabs. Use stacked rows with a
 * vertical filler so each row stays at its preferred height.
 */
public final class SpotsMeasuresUi {

	private SpotsMeasuresUi() {
	}

	public static void showMeasurementLabels(JComboBox<EnumResults> selector) {
		selector.setRenderer(new DefaultListCellRenderer() {
			private static final long serialVersionUID = 1L;

			@Override
			public Component getListCellRendererComponent(JList<?> list, Object value, int index,
					boolean selected, boolean hasFocus) {
				Object label = value instanceof EnumResults ? ((EnumResults) value).toDisplayLabel() : value;
				return super.getListCellRendererComponent(list, label, index, selected, hasFocus);
			}
		});
		Runnable updateTooltip = () -> {
			Object selected = selector.getSelectedItem();
			selector.setToolTipText(selected instanceof EnumResults ? ((EnumResults) selected).toTitle() : null);
		};
		selector.addActionListener(event -> updateTooltip.run());
		updateTooltip.run();
	}

	/** Short measure token in the list, explanation from {@link EnumResults#toTitle()} on the tooltip. */
	public static void showMeasurementNames(JComboBox<EnumResults> selector) {
		selector.setRenderer(new DefaultListCellRenderer() {
			private static final long serialVersionUID = 1L;

			@Override
			public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected,
					boolean hasFocus) {
				Component row = super.getListCellRendererComponent(list, value, index, selected, hasFocus);
				if (value instanceof EnumResults && row instanceof JLabel) {
					EnumResults measure = (EnumResults) value;
					((JLabel) row).setText(measure.name());
					((JComponent) row).setToolTipText(measure.toTitle());
				}
				return row;
			}
		});
		Runnable updateTooltip = () -> {
			Object selected = selector.getSelectedItem();
			selector.setToolTipText(selected instanceof EnumResults ? ((EnumResults) selected).toTitle() : null);
		};
		selector.addActionListener(event -> updateTooltip.run());
		updateTooltip.run();
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
