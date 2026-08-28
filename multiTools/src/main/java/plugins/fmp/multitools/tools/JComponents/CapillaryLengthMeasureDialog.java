package plugins.fmp.multitools.tools.JComponents;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.AbstractTableModel;

import plugins.fmp.multitools.service.CapillaryLengthResult;

/**
 * Review table shown before auto-measured capillary lengths replace the current
 * calibration. Rows detected as outliers or failures are unchecked by default,
 * so confirming never applies a value the detector is unsure about.
 */
public class CapillaryLengthMeasureDialog {

	private CapillaryLengthMeasureDialog() {
	}

	private static final String[] COLUMNS = { "apply", "capillary", "current px", "measured px", "trend px", "diff %",
			"mm/px", "comment" };

	public static boolean showAndConfirm(Component parent, CapillaryLengthResult result, String title) {
		if (result == null)
			return false;
		if (result.hasError()) {
			JOptionPane.showMessageDialog(parent, result.getErrorMessage(), title, JOptionPane.WARNING_MESSAGE);
			return false;
		}

		MeasureTableModel model = new MeasureTableModel(result);
		JTable table = new JTable(model);
		table.setPreferredScrollableViewportSize(new Dimension(720, 320));
		table.setFillsViewportHeight(true);
		table.getColumnModel().getColumn(0).setMaxWidth(50);

		JPanel panel = new JPanel(new BorderLayout(4, 4));
		panel.add(new JLabel(buildSummary(result)), BorderLayout.NORTH);
		panel.add(new JScrollPane(table), BorderLayout.CENTER);
		panel.add(buildSelectionPanel(model), BorderLayout.SOUTH);

		int answer = JOptionPane.showConfirmDialog(parent, panel, title, JOptionPane.OK_CANCEL_OPTION,
				JOptionPane.PLAIN_MESSAGE);
		if (answer != JOptionPane.OK_OPTION)
			return false;
		if (result.countSelected() == 0) {
			JOptionPane.showMessageDialog(parent, "No capillary was selected: nothing was changed.", title,
					JOptionPane.INFORMATION_MESSAGE);
			return false;
		}
		return true;
	}

	private static JPanel buildSelectionPanel(final MeasureTableModel model) {
		JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 1));
		JButton selectReliable = new JButton("Select reliable only");
		JButton selectAll = new JButton("Select all measured");
		JButton selectNone = new JButton("Select none");
		panel.add(selectReliable);
		panel.add(selectAll);
		panel.add(selectNone);

		selectReliable.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				model.selectAll(SelectionMode.RELIABLE);
			}
		});
		selectAll.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				model.selectAll(SelectionMode.ANY_MEASURED);
			}
		});
		selectNone.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				model.selectAll(SelectionMode.NONE);
			}
		});
		return panel;
	}

	public static String buildSummary(CapillaryLengthResult result) {
		if (result == null || result.countUsable() == 0)
			return "No capillary length could be measured.";
		return String.format(
				"<html>%d capillary(ies) measured &mdash; median %.1f px, from %.1f to %.1f px "
						+ "(%.1f%% variation across the image).<br>"
						+ "This variation is the geometric distortion the per-capillary scale removes.</html>",
				result.countUsable(), result.getMedianPixels(), result.getMinPixels(), result.getMaxPixels(),
				result.getSpreadPercent());
	}

	private enum SelectionMode {
		RELIABLE, ANY_MEASURED, NONE
	}

	private static class MeasureTableModel extends AbstractTableModel {
		private static final long serialVersionUID = 1L;

		private final List<CapillaryLengthResult.Measure> measures;
		private final double physicalLengthMm;

		MeasureTableModel(CapillaryLengthResult result) {
			this.measures = result.getMeasures();
			this.physicalLengthMm = result.getPhysicalLengthMm();
		}

		void selectAll(SelectionMode mode) {
			for (CapillaryLengthResult.Measure m : measures) {
				boolean measured = Double.isFinite(m.getDetectedPixels()) && m.getDetectedPixels() > 0;
				switch (mode) {
				case RELIABLE:
					m.setSelected(measured && m.getStatus() == CapillaryLengthResult.Status.OK);
					break;
				case ANY_MEASURED:
					m.setSelected(measured);
					break;
				default:
					m.setSelected(false);
					break;
				}
			}
			fireTableDataChanged();
		}

		@Override
		public int getRowCount() {
			return measures.size();
		}

		@Override
		public int getColumnCount() {
			return COLUMNS.length;
		}

		@Override
		public String getColumnName(int column) {
			return COLUMNS[column];
		}

		@Override
		public Class<?> getColumnClass(int columnIndex) {
			return columnIndex == 0 ? Boolean.class : String.class;
		}

		@Override
		public boolean isCellEditable(int rowIndex, int columnIndex) {
			if (columnIndex != 0)
				return false;
			double detected = measures.get(rowIndex).getDetectedPixels();
			return Double.isFinite(detected) && detected > 0;
		}

		@Override
		public void setValueAt(Object value, int rowIndex, int columnIndex) {
			if (columnIndex == 0 && value instanceof Boolean)
				measures.get(rowIndex).setSelected((Boolean) value);
		}

		@Override
		public Object getValueAt(int rowIndex, int columnIndex) {
			CapillaryLengthResult.Measure m = measures.get(rowIndex);
			switch (columnIndex) {
			case 0:
				return Boolean.valueOf(m.isSelected());
			case 1:
				return m.getName();
			case 2:
				return Integer.toString(m.getPreviousPixels());
			case 3:
				return format(m.getDetectedPixels(), "%.1f");
			case 4:
				return format(m.getFittedPixels(), "%.1f");
			case 5:
				return formatDifference(m);
			case 6:
				return formatScale(m);
			case 7:
				return m.getMessage() == null || m.getMessage().isEmpty() ? m.getStatus().getLabel()
						: m.getStatus().getLabel() + ": " + m.getMessage();
			default:
				return "";
			}
		}

		private String formatDifference(CapillaryLengthResult.Measure m) {
			double previous = m.getPreviousPixels();
			if (!(previous > 0) || !Double.isFinite(m.getDetectedPixels()))
				return "-";
			return String.format("%+.1f", 100. * (m.getDetectedPixels() - previous) / previous);
		}

		private String formatScale(CapillaryLengthResult.Measure m) {
			if (!(physicalLengthMm > 0) || !(m.getDetectedPixels() > 0))
				return "-";
			return String.format("%.4f", physicalLengthMm / m.getDetectedPixels());
		}

		private static String format(double value, String pattern) {
			return Double.isFinite(value) ? String.format(pattern, value) : "-";
		}
	}
}
