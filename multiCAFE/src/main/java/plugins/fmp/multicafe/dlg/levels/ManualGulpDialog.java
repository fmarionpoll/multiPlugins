package plugins.fmp.multicafe.dlg.levels;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Locale;
import java.util.function.BooleanSupplier;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;

import plugins.fmp.multitools.experiment.capillary.CapillaryIntakes;
import plugins.fmp.multitools.service.CorrelationGulpPreview;
import plugins.fmp.multitools.service.ManualIntakeEdits;

/** Editor for one manual gulp. OK reports per-bin measures; the caller installs them. */
public final class ManualGulpDialog {
	private ManualGulpDialog() {}

	public static final class Result {
		public final double contextMinutes;
		public final boolean stale;
		public final Accepted accepted;

		private Result(double contextMinutes, boolean stale, Accepted accepted) {
			this.contextMinutes = contextMinutes;
			this.stale = stale;
			this.accepted = accepted;
		}

		public static final class Accepted {
			public final boolean delete;
			public final int selectedFirst, selectedLast, active;
			public final double[] amplitudes, bottoms;

			private Accepted(boolean delete, int selectedFirst, int selectedLast, int active, double[] amplitudes, double[] bottoms) {
				this.delete = delete;
				this.selectedFirst = selectedFirst;
				this.selectedLast = selectedLast;
				this.active = active;
				this.amplitudes = amplitudes;
				this.bottoms = bottoms;
			}
		}
	}

	public static Result show(Component parent, CorrelationGulpPreview preview, int first, int last, double amount, double bottom,
			double[] otherAmplitudes, double[] otherBottoms, double contextMinutes, BooleanSupplier sourcesCurrent) {
		JSpinner amplitude = new JSpinner(new SpinnerNumberModel(amount, 0., Double.MAX_VALUE, .1));
		JSpinner position = new JSpinner(new SpinnerNumberModel(bottom, -Double.MAX_VALUE, Double.MAX_VALUE, .1));
		JSpinner from = new JSpinner(new SpinnerNumberModel(first, 1, preview.raw.length - 1, 1));
		JSpinner to = new JSpinner(new SpinnerNumberModel(last, 1, preview.raw.length - 1, 1));
		JSpinner editBin = new JSpinner(new SpinnerNumberModel(last, first, last, 1));
		JSpinner context = new JSpinner(new SpinnerNumberModel(contextMinutes, 0.,
				Math.max(contextMinutes, preview.minutes[preview.minutes.length - 1] - preview.minutes[0]), .5));
		JPanel fields = editor(preview, from, to, editBin, amplitude, position, context, otherAmplitudes, otherBottoms);
		Zoom measurement = (Zoom) fields.getClientProperty("manualIntakeMeasurement");
		Object[] actions = { "OK", "Delete intake event", "Cancel" };
		while (true) {
			int action = JOptionPane.showOptionDialog(parent, fields, "Manual gulp (evaporation subtracted)",
					JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, actions, actions[0]);
			double remembered = ((Number) context.getValue()).doubleValue();
			if (action != 0 && action != 1) return new Result(remembered, false, null);
			boolean delete = action == 1;
			try {
				from.commitEdit(); to.commitEdit(); editBin.commitEdit();
				if (!delete && !measurement.commitBinEditors())
					throw new IllegalArgumentException("Enter a valid intake size and position.");
				if (!sourcesCurrent.getAsBoolean()) return new Result(remembered, true, null);
				int selectedFirst = ((Number) from.getValue()).intValue(), selectedLast = ((Number) to.getValue()).intValue();
				if (selectedFirst > selectedLast) throw new IllegalArgumentException("First bin must not exceed last bin.");
				if (!delete) for (int i = selectedFirst; i <= selectedLast; i++)
					if (!Double.isFinite(measurement.measuredAmplitudes[i]) || measurement.measuredAmplitudes[i] < 0
							|| !Double.isFinite(measurement.measuredBottoms[i]))
						throw new IllegalArgumentException("Bin " + i + " has missing level data. Enter its intake size and position before saving.");
				return new Result(remembered, false, new Result.Accepted(delete, selectedFirst, selectedLast, measurement.active,
						measurement.measuredAmplitudes, measurement.measuredBottoms));
			} catch (java.text.ParseException | IllegalArgumentException error) {
				JOptionPane.showMessageDialog(parent, error.getMessage(), "Invalid manual gulp", JOptionPane.ERROR_MESSAGE);
			}
		}
	}

	private static JPanel manualGulpFields(JSpinner from, JSpinner to, JSpinner editBin, JSpinner amplitude, JSpinner position) {
		// NumberEditor otherwise reserves enough columns for Double.MAX_VALUE.
		// Limit the visible width, without restricting the values users can enter.
		((JSpinner.NumberEditor) amplitude.getEditor()).getTextField().setColumns(10);
		((JSpinner.NumberEditor) position.getEditor()).getTextField().setColumns(10);
		JPanel fields = new JPanel(new java.awt.GridLayout(0, 2, 6, 6));
		from.setName("manualIntakeFirstBin"); to.setName("manualIntakeLastBin");
		from.setToolTipText("Changing the range measures every selected bin again from saved levels.");
		to.setToolTipText(from.getToolTipText());
		((JSpinner.NumberEditor) from.getEditor()).getTextField().setColumns(4);
		((JSpinner.NumberEditor) to.getEditor()).getTextField().setColumns(4);
		JPanel bins = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 0));
		bins.add(from); bins.add(new JLabel("to")); bins.add(to);
		fields.add(new JLabel("Selected bins (first / last)")); fields.add(bins);
		editBin.setName("manualIntakeEditBin");
		((JSpinner.NumberEditor) editBin.getEditor()).getTextField().setColumns(4);
		JButton saveBin = new JButton("Save");
		saveBin.setName("manualIntakeSaveBin");
		saveBin.setToolTipText("Validate and keep the intake and position of the bin being edited.");
		JButton deleteBin = new JButton("Delete");
		deleteBin.setName("manualIntakeDeleteBin");
		deleteBin.setToolTipText("Set this bin's intake to zero. Other selected bins are left as they are.");
		JPanel editRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		editRow.add(saveBin); editRow.add(deleteBin); editRow.add(editBin);
		fields.add(new JLabel("Bin to edit")); fields.add(editRow);
		fields.add(new JLabel("Net intake for this bin (px)")); fields.add(amplitude);
		fields.add(new JLabel("Bottom position for this bin (px)")); fields.add(position);
		fields.add(new JLabel("Replaces original and selected regions")); fields.add(new JLabel("Each selected bin measured separately"));
		return fields;
	}

	private static JPanel editor(CorrelationGulpPreview preview, JSpinner from, JSpinner to, JSpinner editBin, JSpinner amplitude,
			JSpinner position, JSpinner context, double[] otherAmplitudes, double[] otherBottoms) {
		JPanel editor = new JPanel(new BorderLayout(4, 6));
		editor.add(manualGulpFields(from, to, editBin, amplitude, position), BorderLayout.NORTH);
		Zoom image = new Zoom(preview, from, to, editBin, amplitude, position, otherAmplitudes, otherBottoms);
		editor.putClientProperty("manualIntakeMeasurement", image);
		((JButton) named(editor, "manualIntakeSaveBin")).addActionListener(e -> image.saveActiveBin());
		((JButton) named(editor, "manualIntakeDeleteBin")).addActionListener(e -> image.deleteActiveBin());
		image.setContextMinutes(((Number) context.getValue()).doubleValue());
		JScrollPane scroll = new JScrollPane(image);
		scroll.setPreferredSize(new Dimension(560, 260));
		scroll.getVerticalScrollBar().setUnitIncrement(12);
		scroll.getHorizontalScrollBar().setUnitIncrement(12);
		editor.add(scroll, BorderLayout.CENTER);
		JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		controls.add(new JLabel("Zoom"));
		JComboBox<Integer> zoom = new JComboBox<>(new Integer[] { 4, 8, 12, 16, 24 });
		zoom.setName("manualIntakeZoomScale");
		zoom.setSelectedItem(12);
		zoom.addActionListener(e -> {
			image.scale = (Integer) zoom.getSelectedItem();
			image.revalidate(); image.repaint();
			SwingUtilities.invokeLater(image::revealLine);
		});
		controls.add(zoom);
		controls.add(new JLabel("Context +/- (min)"));
		context.setName("manualIntakeContextMinutes");
		context.setEditor(new JSpinner.NumberEditor(context, "0.##"));
		((JSpinner.NumberEditor) context.getEditor()).getTextField().setColumns(5);
		context.setToolTipText("Time shown before and after the selected interval, in minutes. Changes only the view.");
		controls.add(context);
		JLabel timeRange = new JLabel();
		Runnable updateRange = () -> timeRange.setText(format(preview.minutes[image.left]) + " - "
				+ format(preview.minutes[image.right - 1]) + " min");
		updateRange.run();
		JLabel selectedTime = new JLabel();
		selectedTime.setName("manualIntakeSelectedTime");
		Runnable updateSelectedTime = () -> {
			int missing = 0; double net = 0;
			for (int i = image.first; i <= image.last; i++) {
				if (!Double.isFinite(image.measuredAmplitudes[i]) || !Double.isFinite(image.measuredBottoms[i])) missing++;
				else if (image.measuredAmplitudes[i] > 0)
					net += Math.max(0, image.measuredAmplitudes[i] - preview.evaporation[i] + preview.evaporation[i - 1]);
			}
			selectedTime.setText("Selected time: " + format(preview.minutes[image.first - 1]) + " - "
					+ format(preview.minutes[image.last]) + " min; net " + format(net) + " px"
					+ (missing > 0 ? "; " + missing + " bins need manual values" : ""));
		};
		image.onEdit = updateSelectedTime;
		updateSelectedTime.run();
		boolean[] updatingBins = { false };
		javax.swing.event.ChangeListener boundsChanged = e -> {
			if (updatingBins[0]) return;
			updatingBins[0] = true;
			try {
				int a = ((Number) from.getValue()).intValue(), b = ((Number) to.getValue()).intValue();
				if (a > b) { if (e.getSource() == from) to.setValue(a); else from.setValue(b); }
				image.setRange(((Number) from.getValue()).intValue(), ((Number) to.getValue()).intValue());
				image.setContextMinutes(((Number) context.getValue()).doubleValue());
				updateRange.run(); updateSelectedTime.run();
				image.revalidate(); image.repaint();
				SwingUtilities.invokeLater(image::revealLine);
			} finally { updatingBins[0] = false; }
		};
		from.addChangeListener(boundsChanged); to.addChangeListener(boundsChanged);
		context.addChangeListener(e -> {
			image.setContextMinutes(((Number) context.getValue()).doubleValue());
			updateRange.run();
			image.revalidate(); image.repaint();
			SwingUtilities.invokeLater(image::revealLine);
		});
		controls.add(timeRange);
		JPanel footer = new JPanel(new BorderLayout(0, 4));
		footer.add(controls, BorderLayout.NORTH);
		JPanel legend = new JPanel(new java.awt.GridLayout(0, 1));
		legend.add(selectedTime);
		legend.add(new JLabel("Blue: intake bins; handles: bin to edit. Orange: saved level. Click a bin to edit."));
		footer.add(legend, BorderLayout.SOUTH);
		editor.add(footer, BorderLayout.SOUTH);
		SwingUtilities.invokeLater(image::revealLine);
		return editor;
	}

	private static Component named(Container parent, String name) {
		for (Component child : parent.getComponents()) {
			if (name.equals(child.getName())) return child;
			if (child instanceof Container) {
				Component found = named((Container) child, name);
				if (found != null) return found;
			}
		}
		return null;
	}

	private static String format(double value) {
		return String.format(Locale.ROOT, "%.2f", value);
	}

	/** Live geometry preview only; OK installs edits, Cancel leaves measures untouched. */
	private static final class Zoom extends JPanel {
		private static final long serialVersionUID = 1L;
		private static final int MARGIN = 12;
		private final CorrelationGulpPreview preview;
		private int first, last;
		private final int originalFirst, originalLast;
		private int left, right;
		private final double[] otherAmplitudes, otherBottoms;
		private final double[] measuredAmplitudes, measuredBottoms;
		private final JSpinner editBin, amplitude, position;
		private int active;
		private boolean updatingValues;
		private Runnable onEdit = () -> { };
		private int scale = 12;
		private int dragging;
		private double fixedTop;

		Zoom(CorrelationGulpPreview preview, JSpinner from, JSpinner to, JSpinner editBin, JSpinner amplitude, JSpinner position,
				double[] otherAmplitudes, double[] otherBottoms) {
			this.preview = preview; first = ((Number) from.getValue()).intValue(); last = ((Number) to.getValue()).intValue();
			originalFirst = first; originalLast = last;
			active = last; this.editBin = editBin;
			this.otherAmplitudes = otherAmplitudes.clone(); this.otherBottoms = otherBottoms.clone();
			this.amplitude = amplitude; this.position = position;
			ManualIntakeEdits.BinMeasures measured = ManualIntakeEdits.measureBins(preview.raw, preview.evaporation, first, last);
			measuredAmplitudes = measured.amplitudes; measuredBottoms = measured.bottoms;
			if (first == last) {
				measuredAmplitudes[last] = number(amplitude) > 0 ? number(amplitude) + evaporation() : 0;
				measuredBottoms[last] = number(position);
			}
			loadBin();
			amplitude.addChangeListener(e -> {
				if (updatingValues) return;
				measuredAmplitudes[active] = number(amplitude) > 0 ? number(amplitude) + evaporation() : 0;
				onEdit.run(); repaint();
			});
			position.addChangeListener(e -> {
				if (updatingValues) return;
				measuredBottoms[active] = number(position);
				onEdit.run(); repaint();
			});
			editBin.addChangeListener(e -> {
				if (updatingValues) return;
				int next = ((Number) editBin.getValue()).intValue();
				// Typed spinner text is not in the model until commit. Switching bins
				// reuses these fields, so commit onto this bin before replacing them.
				if (next != active) commitBinEditors();
				active = next; loadBin();
				onEdit.run(); repaint(); SwingUtilities.invokeLater(this::revealLine);
			});
			setContextMinutes(8);
			setName("manualIntakeZoom");
			setBackground(Color.DARK_GRAY);
			setToolTipText("Each selected bin is measured separately. Click a bin or use Bin to edit; the intake and position of the bin you leave are kept. Then adjust its fields or drag its vertical blue endpoints.");
			MouseAdapter mouse = new MouseAdapter() {
				@Override public void mousePressed(MouseEvent e) {
					if (!SwingUtilities.isLeftMouseButton(e)) return;
					int bin = left + (int) Math.floor((e.getX() - MARGIN) / (double) scale);
					if (bin >= first && bin <= last && bin != active) editBin.setValue(bin);
					double bottom = number(position), top = bottom - fullAmplitude();
					int topDistance = Math.abs(e.getY() - y(top)), bottomDistance = Math.abs(e.getY() - y(bottom));
					if (Math.abs(e.getX() - topX()) <= 9 && topDistance <= 9) dragging = 1;
					else if (Math.abs(e.getX() - lineX()) <= 9 && bottomDistance <= 9) { dragging = 2; fixedTop = top; }
				}
				@Override public void mouseDragged(MouseEvent e) {
					if (dragging == 0) return;
					double row = Math.max(0, Math.min(preview.image.getHeight() - 1, (e.getY() - MARGIN) / (double) scale));
					if (dragging == 1)
						amplitude.setValue(Math.max(0, number(position) - row - evaporation()));
					else {
						position.setValue(row);
						amplitude.setValue(Math.max(0, row - fixedTop - evaporation()));
					}
				}
				@Override public void mouseReleased(MouseEvent e) { dragging = 0; }
			};
			addMouseListener(mouse); addMouseMotionListener(mouse);
		}

		private boolean commitBinEditors() {
			boolean amountOk = commitIfTextEdited(amplitude);
			boolean positionOk = commitIfTextEdited(position);
			return amountOk && positionOk;
		}

		/** Commit only text the user changed. Formatting the current model back
		 * through the editor would replace an exact stored measurement with a rounded one. */
		private static boolean commitIfTextEdited(JSpinner spinner) {
			JComponent editor = spinner.getEditor();
			if (!(editor instanceof JSpinner.DefaultEditor)) return true;
			JFormattedTextField field = ((JSpinner.DefaultEditor) editor).getTextField();
			try {
				String shown = field.getFormatter().valueToString(spinner.getValue());
				if (field.getText().equals(shown)) return true;
			} catch (java.text.ParseException | NullPointerException ignored) {
				return false;
			}
			try { spinner.commitEdit(); return true; } catch (java.text.ParseException ignored) { return false; }
		}

		private void saveActiveBin() {
			if (!commitBinEditors() || !Double.isFinite(measuredAmplitudes[active]) || measuredAmplitudes[active] < 0
					|| !Double.isFinite(measuredBottoms[active])) {
				JOptionPane.showMessageDialog(this, "Bin " + active + " has missing or invalid level data. Enter its intake size and position before saving.",
						"Invalid manual gulp", JOptionPane.ERROR_MESSAGE);
				return;
			}
			onEdit.run(); repaint();
		}

		private void deleteActiveBin() {
			amplitude.setValue(0.);
			if (!Double.isFinite(measuredBottoms[active])) measuredBottoms[active] = number(position);
			onEdit.run(); repaint();
		}

		private void loadBin() {
			updatingValues = true;
			try {
				double full = measuredAmplitudes[active];
				amplitude.setValue(Double.isFinite(full) && full > 0 ? Math.max(0, full - evaporation()) : 0.);
				position.setValue(Double.isFinite(measuredBottoms[active]) ? measuredBottoms[active] : preview.image.getHeight() / 2.);
				SpinnerNumberModel bins = (SpinnerNumberModel) editBin.getModel();
				bins.setMinimum(first); bins.setMaximum(last); bins.setValue(active);
			} finally { updatingValues = false; }
		}

		private void setRange(int from, int to) {
			if (from == first && to == last) return;
			first = from; last = to;
			ManualIntakeEdits.BinMeasures measured = ManualIntakeEdits.measureBins(preview.raw, preview.evaporation, first, last);
			for (int i = first; i <= last; i++) {
				measuredAmplitudes[i] = measured.amplitudes[i]; measuredBottoms[i] = measured.bottoms[i];
			}
			active = Math.max(first, Math.min(last, active));
			loadBin();
		}

		private void setContextMinutes(double minutes) {
			left = first - 1; right = last + 1;
			double from = preview.minutes[first - 1] - minutes, to = preview.minutes[last] + minutes;
			while (left > 0 && preview.minutes[left - 1] >= from) left--;
			while (right < preview.minutes.length && preview.minutes[right] <= to) right++;
		}

		private static double number(JSpinner spinner) { return ((Number) spinner.getValue()).doubleValue(); }
		private double evaporation() { return preview.evaporation[active] - preview.evaporation[active - 1]; }
		private double fullAmplitude() { return number(amplitude) > 0 ? number(amplitude) + evaporation() : 0; }
		private int columnX(int frame) { return MARGIN + (int) Math.round((frame - left + .5) * scale); }
		private int lineX() { return columnX(active); }
		private int topX() { return lineX(); }
		private int y(double row) { return MARGIN + (int) Math.round(row * scale); }
		@Override public Dimension getPreferredSize() {
			return new Dimension((right - left) * scale + 2 * MARGIN, preview.image.getHeight() * scale + 2 * MARGIN);
		}
		void revealLine() {
			int center = y(number(position) - fullAmplitude() / 2);
			Rectangle visible = getVisibleRect();
			scrollRectToVisible(new Rectangle(Math.max(0, lineX() - visible.width / 2),
					Math.max(0, center - visible.height / 2), Math.max(1, visible.width), Math.max(1, visible.height)));
		}
		@Override protected void paintComponent(Graphics graphics) {
			super.paintComponent(graphics);
			Graphics2D g = (Graphics2D) graphics.create();
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
			g.drawImage(preview.image, MARGIN, MARGIN, MARGIN + (right - left) * scale,
					MARGIN + preview.image.getHeight() * scale, left, 0, right, preview.image.getHeight(), null);
			g.setColor(new Color(70, 130, 230, 30));
			g.fillRect(columnX(first - 1), MARGIN, Math.max(1, columnX(last) - columnX(first - 1)), preview.image.getHeight() * scale);
			g.setStroke(new BasicStroke(1)); g.setColor(Color.ORANGE);
			for (int i = left + 1; i < right; i++)
				if (Double.isFinite(preview.raw[i - 1]) && Double.isFinite(preview.raw[i]))
					g.drawLine(MARGIN + (int) ((i - left - .5) * scale), y(preview.raw[i - 1]),
							MARGIN + (int) ((i - left + .5) * scale), y(preview.raw[i]));
			g.setColor(CapillaryIntakes.ROI_COLOR); g.setStroke(new BasicStroke(2));
			for (int i = Math.max(1, left); i < right; i++)
				if ((i < first || i > last) && (i < originalFirst || i > originalLast)
						&& otherAmplitudes[i] > 0 && Double.isFinite(otherBottoms[i]))
					g.drawLine(columnX(i), y(otherBottoms[i]), columnX(i), y(otherBottoms[i] - otherAmplitudes[i]));
			for (int i = first; i <= last; i++)
				if (i != active && measuredAmplitudes[i] > 0 && Double.isFinite(measuredAmplitudes[i]) && Double.isFinite(measuredBottoms[i]))
					g.drawLine(columnX(i), y(measuredBottoms[i]), columnX(i), y(measuredBottoms[i] - measuredAmplitudes[i]));
			int x = lineX(), bottom = y(number(position)), top = y(number(position) - fullAmplitude());
			g.setColor(CapillaryIntakes.ROI_COLOR); g.setStroke(new BasicStroke(3));
			g.drawLine(x, top, x, bottom);
			g.fillOval(x - 5, top - 5, 10, 10); g.fillOval(x - 5, bottom - 5, 10, 10);
			g.dispose();
		}
	}
}
