package plugins.fmp.multicafe.dlg.kymos;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

import icy.image.IcyBufferedImage;
import icy.image.IcyBufferedImageUtil;
import icy.sequence.Sequence;
import icy.undo.AbstractIcyUndoableEdit;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.capillary.Capillary;
import plugins.fmp.multitools.experiment.sequence.SequenceKymos;
import plugins.fmp.multitools.series.options.BuildSeriesOptions;
import plugins.fmp.multitools.service.KymographAlignment;
import plugins.fmp.multitools.tools.Logger;

final class KymographAdjustmentDialog extends JDialog {
	private static final long serialVersionUID = 1L;
	private static final int FILTERED_Z = BuildSeriesOptions.Z_INDEX_FILTERED_FOR_GULPS;

	private final Sequence sequence;
	private final JComboBox<String> kymographs = new JComboBox<>();
	private final JButton previous = new JButton("<");
	private final JButton next = new JButton(">");
	private final JCheckBox track = new JCheckBox("Track reference", true);
	private final JButton clearReference = new JButton("Clear points");
	private final JCheckBox nearbyEdges = new JCheckBox("Use nearby edges", true);
	private final JComboBox<KymographAlignment.Target> target = new JComboBox<>(KymographAlignment.Target.values());
	private enum Move { Range, Curve }
	private final JComboBox<Move> move = new JComboBox<>(Move.values());
	private final JButton up = new JButton("-1 px");
	private final JButton down = new JButton("+1 px");
	private final JButton clearMoves = new JButton("Clear moves");
	private final JButton range = new JButton("Reference range: all");
	private final JButton preview = new JButton("Preview");
	private final JCheckBox showAligned = new JCheckBox("Show aligned");
	private final JButton apply = new JButton("Apply");
	private final JLabel status = new JLabel(" ");
	private final ReferenceView view = new ReferenceView();
	private final TreeMap<Integer, Double> anchors = new TreeMap<>();
	private final TreeMap<Integer, Double> curveSource = new TreeMap<>();
	private final TreeMap<Integer, Double> curveShifts = new TreeMap<>();

	private IcyBufferedImage source;
	private BufferedImage original;
	private double[] rangeShifts;
	private boolean hasRangeShifts;
	private int selectionStart = -1;
	private int selectionEnd = -1;
	private int first;
	private int last;
	private Preview proposal;
	private SwingWorker<Preview, Void> worker;

	static void open(Component owner, Experiment experiment, int selectedFrame) {
		SequenceKymos kymos = experiment == null ? null : experiment.getSeqKymos();
		Sequence sequence = kymos == null ? null : kymos.getSequence();
		if (sequence == null || sequence.getSizeT() == 0 || sequence.isUpdating() || experiment.isLoading()) {
			JOptionPane.showMessageDialog(owner, "Build or load kymographs before adjusting them.");
			return;
		}
		if (sequence.getFirstViewer() != null)
			selectedFrame = sequence.getFirstViewer().getPositionT();
		new KymographAdjustmentDialog(owner, experiment, kymos, selectedFrame).setVisible(true);
	}

	private KymographAdjustmentDialog(Component owner, Experiment experiment, SequenceKymos kymos, int selectedFrame) {
		super(SwingUtilities.getWindowAncestor(owner), "Adjust kymograph", Dialog.ModalityType.APPLICATION_MODAL);
		sequence = kymos.getSequence();
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);
		addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				if (worker != null)
					worker.cancel(true);
			}
		});
		for (int t = 0; t < sequence.getSizeT(); t++) {
			Capillary capillary = kymos.getCapillaryForFrame(t, experiment.getCapillaries());
			String path = kymos.getFileNameFromImageList(t);
			String name = capillary == null ? null : capillary.getRoiName();
			kymographs.addItem(name != null ? name : path != null ? new File(path).getName() : "Kymograph " + (t + 1));
		}
		kymographs.setSelectedIndex(Math.max(0, Math.min(selectedFrame, kymographs.getItemCount() - 1)));

		JPanel controls = new JPanel();
		controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS));
		JPanel selection = new JPanel(new FlowLayout(FlowLayout.LEFT));
		selection.add(new JLabel("Kymograph"));
		selection.add(previous);
		selection.add(kymographs);
		selection.add(next);
		selection.add(new JLabel("Move"));
		selection.add(move);
		selection.add(up);
		selection.add(down);
		selection.add(clearMoves);
		controls.add(selection);
		JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT));
		for (Component control : new Component[] { track, clearReference, nearbyEdges, target, range, preview,
				showAligned, apply })
			actions.add(control);
		controls.add(actions);
		add(controls, BorderLayout.NORTH);
		add(view, BorderLayout.CENTER);
		JPanel footer = new JPanel(new FlowLayout(FlowLayout.LEFT));
		footer.add(status);
		add(footer, BorderLayout.SOUTH);

		track.setToolTipText("Click a fixed edge or dark line. Right-click near an anchor to remove it. Manual moves are added to the reference correction.");
		previous.setToolTipText("Previous capillary");
		next.setToolTipText("Next capillary");
		clearReference.setToolTipText("Remove all yellow reference points for this kymograph.");
		nearbyEdges.setToolTipText("Require agreement between nearby edges to distinguish camera motion from changes in a single edge.");
		move.setToolTipText("With Track reference off, select a block in Range mode or drag the feature to its target at several columns in Curve mode.");
		target.setToolTipText("Fit a horizontal reference or preserve the feature's overall slope.");
		range.setToolTipText("Choose the inclusive, zero-based time columns to align.");
		showAligned.setToolTipText("Toggle between the aligned image and the original with editing overlays.");
		apply.setToolTipText("Replace this kymograph in memory. Use Kymographs > Load/Save to write the TIFF.");
		kymographs.addActionListener(e -> selectImage());
		previous.addActionListener(e -> kymographs.setSelectedIndex(kymographs.getSelectedIndex() - 1));
		next.addActionListener(e -> kymographs.setSelectedIndex(kymographs.getSelectedIndex() + 1));
		clearReference.addActionListener(e -> {
			anchors.clear();
			invalidatePreview();
		});
		track.addActionListener(e -> {
			showAligned.setSelected(false);
			showEditHint();
			updateControls();
		});
		nearbyEdges.addActionListener(e -> invalidatePreview());
		target.addActionListener(e -> invalidatePreview());
		move.addActionListener(e -> {
			showEditHint();
			updateControls();
		});
		up.addActionListener(e -> shiftRange(-1));
		down.addActionListener(e -> shiftRange(1));
		clearMoves.addActionListener(e -> {
			Arrays.fill(rangeShifts, 0);
			hasRangeShifts = false;
			curveSource.clear();
			curveShifts.clear();
			invalidatePreview();
		});
		range.addActionListener(e -> editRange());
		preview.addActionListener(e -> previewAlignment());
		showAligned.addActionListener(e -> updateControls());
		apply.addActionListener(e -> applyAlignment());

		selectImage();
		pack();
		setLocationRelativeTo(owner);
	}

	private void selectImage() {
		source = sequence.getImage(kymographs.getSelectedIndex(), 0);
		original = null;
		anchors.clear();
		curveSource.clear();
		curveShifts.clear();
		rangeShifts = new double[source == null ? 0 : source.getSizeX()];
		hasRangeShifts = false;
		selectionStart = selectionEnd = -1;
		first = 0;
		last = source == null ? -1 : source.getSizeX() - 1;
		try {
			if (source != null)
				original = IcyBufferedImageUtil.getARGBImage(source);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		invalidatePreview();
	}

	private void invalidatePreview() {
		proposal = null;
		showAligned.setSelected(false);
		showEditHint();
		updateControls();
	}

	private void showEditHint() {
		if (original == null)
			status.setText("No kymograph image available.");
		else if (track.isSelected())
			status.setText("Click a fixed edge or band. Uncheck Track reference to add manual moves.");
		else if (move.getSelectedItem() == Move.Range)
			status.setText("Drag across columns to select them. Drag inside the selection vertically or use -1 px / +1 px.");
		else
			status.setText("Drag from the feature to its desired row at each control point. Right-click to remove a point.");
	}

	private void shiftRange(double delta) {
		int lo = Math.min(selectionStart, selectionEnd);
		int hi = Math.max(selectionStart, selectionEnd);
		for (int x = lo; x <= hi; x++)
			rangeShifts[x] += delta;
		hasRangeShifts = Arrays.stream(rangeShifts).anyMatch(shift -> shift != 0);
		invalidatePreview();
	}

	private void updateControls() {
		boolean idle = worker == null;
		boolean ready = idle && original != null && original.getWidth() >= 2;
		kymographs.setEnabled(idle);
		int selected = kymographs.getSelectedIndex();
		previous.setEnabled(idle && selected > 0);
		next.setEnabled(idle && selected >= 0 && selected + 1 < kymographs.getItemCount());
		track.setEnabled(ready);
		clearReference.setEnabled(ready && !anchors.isEmpty());
		boolean picking = track.isSelected();
		boolean editing = ready && !picking && !showAligned.isSelected();
		boolean manual = hasRangeShifts || !curveShifts.isEmpty();
		boolean hasReference = ready && !anchors.subMap(first, true, last, true).isEmpty();
		nearbyEdges.setEnabled(ready && picking);
		target.setEnabled(ready && picking);
		range.setEnabled(ready && picking);
		move.setEnabled(editing);
		up.setEnabled(editing && move.getSelectedItem() == Move.Range && selectionStart >= 0);
		down.setEnabled(up.isEnabled());
		clearMoves.setEnabled(ready && manual);
		range.setText(original == null || (first == 0 && last == original.getWidth() - 1)
				? "Reference range: all" : "Reference range: " + first + "-" + last);
		preview.setEnabled(ready && (hasReference || manual));
		showAligned.setEnabled(ready && proposal != null);
		apply.setEnabled(ready && proposal != null && proposal.alignment.maxShift > 1e-9);
		view.repaint();
	}

	private void editRange() {
		JTextField from = new JTextField(Integer.toString(first), 6);
		JTextField to = new JTextField(Integer.toString(last), 6);
		Object[] fields = { "First column (0-" + (original.getWidth() - 1) + ")", from, "Last column (inclusive)", to };
		String[] choices = { "Set range", "All columns", "Cancel" };
		int choice = JOptionPane.showOptionDialog(this, fields, "Reference range", JOptionPane.DEFAULT_OPTION,
				JOptionPane.PLAIN_MESSAGE, null, choices, choices[0]);
		if (choice == 1) {
			first = 0;
			last = original.getWidth() - 1;
		} else if (choice == 0) {
			try {
				int start = Integer.parseInt(from.getText().trim());
				int end = Integer.parseInt(to.getText().trim());
				if (start < 0 || end <= start || end >= original.getWidth())
					throw new NumberFormatException();
				first = start;
				last = end;
			} catch (NumberFormatException e) {
				status.setText("Enter a valid range of at least two image columns.");
				return;
			}
		} else {
			return;
		}
		invalidatePreview();
	}

	private void previewAlignment() {
		BufferedImage image = original;
		IcyBufferedImage input = source;
		int start = first;
		int end = last;
		Map<Integer, Double> seeds = new TreeMap<>(anchors.subMap(start, true, end, true));
		boolean useNearbyEdges = nearbyEdges.isSelected();
		double[] ranges = rangeShifts.clone();
		TreeMap<Integer, Double> curve = new TreeMap<>(curveShifts);
		boolean manual = hasRangeShifts || !curve.isEmpty();
		KymographAlignment.Target mode = (KymographAlignment.Target) target.getSelectedItem();
		status.setText(seeds.isEmpty() ? "Previewing manual moves..." : "Tracking the reference...");
		worker = new SwingWorker<Preview, Void>() {
			@Override
			protected Preview doInBackground() throws InterruptedException {
				KymographAlignment.Result reference = seeds.isEmpty() ? null
						: KymographAlignment.analyze(image, seeds, start, end, mode, useNearbyEdges);
				KymographAlignment.Result alignment = manual ? KymographAlignment.addManualShifts(reference, ranges, curve)
						: reference;
				IcyBufferedImage aligned = KymographAlignment.align(input, alignment);
				return new Preview(alignment, aligned, IcyBufferedImageUtil.getARGBImage(aligned));
			}

			@Override
			protected void done() {
				worker = null;
				if (!isDisplayable())
					return;
				proposal = null;
				try {
					proposal = get();
					KymographAlignment.Result result = proposal.alignment;
					showAligned.setSelected(false);
					String coverage = seeds.isEmpty() ? "Manual moves. "
							: String.format("Matched %d of %d columns. ", result.matchedColumns, end - start + 1);
					status.setText(coverage + String.format("Largest shift %.2f px.", result.maxShift));
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				} catch (CancellationException e) {
					return;
				} catch (ExecutionException e) {
					Throwable cause = e.getCause();
					status.setText(cause instanceof IllegalArgumentException ? cause.getMessage() : "Preview failed.");
					if (!(cause instanceof IllegalArgumentException))
						Logger.error("Kymograph alignment preview failed", cause);
				}
				updateControls();
			}
		};
		updateControls();
		worker.execute();
	}

	private void applyAlignment() {
		int frame = kymographs.getSelectedIndex();
		if (sequence.isUpdating() || sequence.getImage(frame, 0) != source) {
			selectImage();
			status.setText("The source kymograph changed. Preview the current image again.");
			return;
		}
		IcyBufferedImage before = source;
		IcyBufferedImage filtered = sequence.getImage(frame, FILTERED_Z);
		IcyBufferedImage after = proposal.aligned;
		applyWithUndo(sequence, frame, before, after, filtered);
		selectImage();
		status.setText("Applied in memory. Save the TIFF from Load/Save before re-running level or gulp detection.");
	}

	private static void applyWithUndo(Sequence sequence, int frame, IcyBufferedImage before, IcyBufferedImage after,
			IcyBufferedImage filtered) {
		replaceImage(sequence, frame, after, null);
		sequence.addUndoableEdit(new AbstractIcyUndoableEdit(sequence, "Align kymograph") {
			@Override
			public void undo() {
				super.undo();
				replaceImage(sequence, frame, before, filtered);
			}

			@Override
			public void redo() {
				super.redo();
				replaceImage(sequence, frame, after, null);
			}
		});
	}

	private static void replaceImage(Sequence sequence, int frame, IcyBufferedImage image, IcyBufferedImage filtered) {
		sequence.beginUpdate();
		try {
			sequence.setImage(frame, 0, image);
			if (filtered == null)
				sequence.removeImage(frame, FILTERED_Z);
			else
				sequence.setImage(frame, FILTERED_Z, filtered);
		} finally {
			sequence.endUpdate();
		}
	}

	private static final class Preview {
		final KymographAlignment.Result alignment;
		final IcyBufferedImage aligned;
		final BufferedImage display;

		Preview(KymographAlignment.Result alignment, IcyBufferedImage aligned, BufferedImage display) {
			this.alignment = alignment;
			this.aligned = aligned;
			this.display = display;
		}
	}

	private final class ReferenceView extends JPanel {
		private static final long serialVersionUID = 1L;
		private int dragColumn = -1;
		private boolean shiftSelection;
		private boolean rangeDragStarted;
		private int pressX;
		private int pressY;
		private double dragRow;
		private double currentRow;

		ReferenceView() {
			setBackground(Color.DARK_GRAY);
			setPreferredSize(new Dimension(900, 350));
			MouseAdapter mouse = new MouseAdapter() {
				@Override
				public void mousePressed(MouseEvent e) {
					if (worker != null || showAligned.isSelected() || original == null)
						return;
					Rectangle bounds = imageBounds();
					if (!bounds.contains(e.getPoint()))
						return;
					if (track.isSelected()) {
						pickAnchor(e);
					} else if (SwingUtilities.isLeftMouseButton(e)) {
						dragColumn = columnAt(e, bounds);
						dragRow = currentRow = rowAt(e, bounds);
						if (move.getSelectedItem() == Move.Range) {
							pressX = e.getX();
							pressY = e.getY();
							shiftSelection = false;
							rangeDragStarted = false;
						}
					} else if (SwingUtilities.isRightMouseButton(e) && move.getSelectedItem() == Move.Curve) {
						curveSource.keySet().removeIf(x -> Math.abs(drawX(x, bounds) - e.getX()) <= 6);
						curveShifts.keySet().retainAll(curveSource.keySet());
						invalidatePreview();
					}
				}

				@Override
				public void mouseDragged(MouseEvent e) {
					if (dragColumn < 0)
						return;
					Rectangle bounds = imageBounds();
					currentRow = rowAt(e, bounds);
					if (move.getSelectedItem() == Move.Range) {
						if (!rangeDragStarted) {
							int dx = Math.abs(e.getX() - pressX);
							int dy = Math.abs(e.getY() - pressY);
							if (dx == 0 && dy == 0)
								return;
							rangeDragStarted = true;
							shiftSelection = dy > dx && dragColumn >= Math.min(selectionStart, selectionEnd)
									&& dragColumn <= Math.max(selectionStart, selectionEnd);
						}
						if (!shiftSelection) {
							selectionStart = dragColumn;
							selectionEnd = columnAt(e, bounds);
						}
					}
					repaint();
				}

				@Override
				public void mouseReleased(MouseEvent e) {
					if (dragColumn < 0)
						return;
					mouseDragged(e);
					if (move.getSelectedItem() == Move.Curve) {
						curveSource.put(dragColumn, dragRow);
						curveShifts.put(dragColumn, currentRow - dragRow);
						invalidatePreview();
					} else if (shiftSelection && currentRow != dragRow) {
						shiftRange(currentRow - dragRow);
					} else if (!rangeDragStarted) {
						selectionStart = selectionEnd = dragColumn;
					}
					dragColumn = -1;
					updateControls();
				}
			};
			addMouseListener(mouse);
			addMouseMotionListener(mouse);
		}

		private Rectangle imageBounds() {
			int height = Math.min(getHeight(), original.getHeight() * 3);
			return new Rectangle(0, (getHeight() - height) / 2, getWidth(), height);
		}

		private void pickAnchor(MouseEvent e) {
			Rectangle bounds = imageBounds();
			int x = columnAt(e, bounds);
			if (SwingUtilities.isLeftMouseButton(e) && x >= first && x <= last) {
				anchors.put(x, rowAt(e, bounds));
			} else if (SwingUtilities.isRightMouseButton(e)) {
				anchors.keySet().removeIf(column -> Math.abs(drawX(column, bounds) - e.getX()) <= 6);
			} else {
				return;
			}
			invalidatePreview();
		}

		private int columnAt(MouseEvent e, Rectangle bounds) {
			int x = (int) ((e.getX() + .5) * original.getWidth() / bounds.width);
			return Math.max(0, Math.min(original.getWidth() - 1, x));
		}

		private double rowAt(MouseEvent e, Rectangle bounds) {
			double y = (e.getY() - bounds.y + .5) * original.getHeight() / bounds.height - .5;
			return Math.max(0, Math.min(original.getHeight() - 1, y));
		}

		private int drawX(int x, Rectangle bounds) {
			return (int) ((x + .5) * bounds.width / original.getWidth());
		}

		private int drawY(double y, Rectangle bounds) {
			return bounds.y + (int) ((y + .5) * bounds.height / original.getHeight());
		}

		@Override
		protected void paintComponent(Graphics g) {
			super.paintComponent(g);
			if (original == null)
				return;
			Rectangle bounds = imageBounds();
			boolean aligned = showAligned.isSelected() && proposal != null;
			g.drawImage(aligned ? proposal.display : original, bounds.x, bounds.y, bounds.width, bounds.height, null);
			if (!aligned && !track.isSelected() && move.getSelectedItem() == Move.Range)
				paintRangeMoves(g, bounds);
			if (!aligned && proposal != null && proposal.alignment.tracked != null && track.isSelected()) {
				paintTrace(g, bounds, proposal.alignment.tracked, Color.CYAN);
				paintTrace(g, bounds, proposal.alignment.target, Color.GREEN);
			}
			if (!aligned && track.isSelected()) {
				g.setColor(Color.YELLOW);
				anchors.forEach((x, y) -> g.fillOval(drawX(x, bounds) - 3, drawY(y, bounds) - 3, 7, 7));
			} else if (!aligned && move.getSelectedItem() == Move.Range && selectionStart >= 0) {
				int left = columnBoundary(Math.min(selectionStart, selectionEnd), bounds);
				int right = columnBoundary(Math.max(selectionStart, selectionEnd) + 1, bounds);
				g.setColor(new Color(255, 200, 70, 90));
				g.fillRect(left, bounds.y, Math.max(2, right - left), bounds.height);
			} else if (!aligned && move.getSelectedItem() == Move.Curve) {
				paintCurve(g, bounds, false, Color.ORANGE);
				paintCurve(g, bounds, true, Color.CYAN);
				if (dragColumn >= 0)
					g.drawLine(drawX(dragColumn, bounds), drawY(dragRow, bounds),
							drawX(dragColumn, bounds), drawY(currentRow, bounds));
			}
		}

		private int columnBoundary(int x, Rectangle bounds) {
			return bounds.x + (int) Math.round(x * (double) bounds.width / original.getWidth());
		}

		private double rangeMoveOnScreen(int column, Rectangle bounds) {
			double shift = rangeShifts[column];
			if (dragColumn >= 0 && shiftSelection && column >= Math.min(selectionStart, selectionEnd)
					&& column <= Math.max(selectionStart, selectionEnd))
				shift += currentRow - dragRow;
			double pixels = shift * bounds.height / original.getHeight();
			return Math.max(-bounds.height, Math.min(bounds.height, pixels));
		}

		private void paintRangeMoves(Graphics g, Rectangle bounds) {
			if (!hasRangeShifts && (dragColumn < 0 || !shiftSelection))
				return;
			int width = original.getWidth();
			int height = original.getHeight();
			for (int start = 0; start < width;) {
				double shift = rangeMoveOnScreen(start, bounds);
				int end = start + 1;
				while (end < width && rangeMoveOnScreen(end, bounds) == shift)
					end++;
				if (shift != 0) {
					int left = columnBoundary(start, bounds);
					int right = columnBoundary(end, bounds);
					Graphics2D strip = (Graphics2D) g.create();
					try {
						strip.clipRect(left, bounds.y, right - left, bounds.height);
						// Extend the exposed edge just as the final resampling does.
						int edge = shift > 0 ? 0 : height - 1;
						int edgeHeight = (int) Math.ceil(Math.abs(shift));
						int y = shift > 0 ? bounds.y : bounds.y + bounds.height - edgeHeight;
						strip.drawImage(original, bounds.x, y, bounds.x + bounds.width, y + edgeHeight,
								0, edge, width, edge + 1, null);
						strip.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
						AffineTransform position = AffineTransform.getTranslateInstance(bounds.x, bounds.y + shift);
						position.scale((double) bounds.width / width, (double) bounds.height / height);
						strip.drawImage(original, position, null);
					} finally {
						strip.dispose();
					}
				}
				start = end;
			}
		}

		private void paintCurve(Graphics g, Rectangle bounds, boolean destination, Color color) {
			g.setColor(color);
			int previousX = -1;
			int previousY = 0;
			for (Map.Entry<Integer, Double> point : curveSource.entrySet()) {
				int column = point.getKey();
				int x = drawX(column, bounds);
				double row = point.getValue() + (destination ? curveShifts.get(column) : 0);
				int y = drawY(row, bounds);
				if (previousX >= 0)
					g.drawLine(previousX, previousY, x, y);
				g.fillOval(x - 3, y - 3, 7, 7);
				previousX = x;
				previousY = y;
			}
		}

		private void paintTrace(Graphics g, Rectangle bounds, double[] rows, Color color) {
			g.setColor(color);
			for (int x = first; x <= last; x++) {
				if (!Double.isFinite(rows[x]))
					continue;
				int px = drawX(x, bounds);
				int py = drawY(rows[x], bounds);
				if (x > first && Double.isFinite(rows[x - 1]))
					g.drawLine(drawX(x - 1, bounds), drawY(rows[x - 1], bounds), px, py);
				else
					g.drawLine(px, py, px, py);
			}
		}
	}
}
