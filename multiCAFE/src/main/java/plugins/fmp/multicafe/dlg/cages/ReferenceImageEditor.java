package plugins.fmp.multicafe.dlg.cages;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.util.Arrays;

import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;

import icy.canvas.IcyCanvas;
import icy.canvas.IcyCanvas2D;
import icy.gui.dialog.MessageDialog;
import icy.gui.frame.IcyFrame;
import icy.gui.frame.IcyFrameAdapter;
import icy.gui.frame.IcyFrameEvent;
import icy.gui.viewer.Viewer;
import icy.image.IcyBufferedImage;
import icy.image.IcyBufferedImageCursor;
import icy.image.IcyBufferedImageUtil;
import icy.painter.Overlay;
import icy.sequence.Sequence;
import icy.type.point.Point5D;
import plugins.fmp.multicafe.MultiCAFE;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.sequence.SequenceCamData;
import plugins.fmp.multitools.service.SequenceLoaderService;
import plugins.fmp.multitools.service.SequenceLoaderService.ReferenceImageKind;
import plugins.fmp.multitools.tools.Logger;

/**
 * Simple editor for the reference (background) image.
 * <p>
 * Tools:
 * - pick: click to pick RGB color from the reference image
 * - paint: click or drag (left button) to deposit a circular/square patch of the picked color
 * - surround: click or drag to fill the brush with the per-channel median of pixels in a ring
 *   around the brush on the current reference (useful for halos and leftover fly edges)
 * - time median: click or drag to set each pixel in the brush to the temporal median at that
 *   location over the camera stack (useful when the fly moves across frames)
 * <p>
 * Brush tools call {@link MouseEvent#consume()} on press/drag/release so the ICY 2D canvas does not
 * treat left-drag as image panning (see {@code Canvas2D.CanvasView}).
 * </p>
 */
public class ReferenceImageEditor {

	private ReferenceImageEditor() {
	}

	public static void open(MultiCAFE parent0, Experiment exp) {
		open(parent0, exp, ReferenceImageKind.DEFAULT);
	}

	public static void open(MultiCAFE parent0, Experiment exp, ReferenceImageKind kind) {
		if (exp == null || exp.getSeqCamData() == null || exp.getSeqCamData().getSequence() == null) {
			return;
		}

		if (kind == ReferenceImageKind.LIGHT || kind == ReferenceImageKind.DARK) {
			boolean ok = new SequenceLoaderService().loadReferenceImage(exp, kind);
			if (!ok) {
				MessageDialog.showDialog("Reference file not found: " + SequenceLoaderService.getReferenceImageFilename(kind),
						MessageDialog.ERROR_MESSAGE);
				return;
			}
			IcyBufferedImage src = kind == ReferenceImageKind.LIGHT ? exp.getSeqCamData().getReferenceImageLight()
					: exp.getSeqCamData().getReferenceImageDark();
			if (src == null) {
				return;
			}
			exp.getSeqCamData().setReferenceImage(IcyBufferedImageUtil.getCopy(src));
		} else {
			ensureReferenceImageExists(exp);
		}

		IcyBufferedImage ref = exp.getSeqCamData().getReferenceImage();
		if (ref == null) {
			return;
		}

		Sequence seq = exp.getSeqReference();
		if (seq == null) {
			seq = new Sequence(ref);
			seq.setName("referenceImage");
			exp.setSeqReference(seq);
		} else {
			try {
				seq.setImage(0, 0, ref);
			} catch (Exception e) {
				// best effort; viewer will still show current sequence content
			}
		}

		Viewer viewer = seq.getFirstViewer();
		if (viewer == null) {
			viewer = new Viewer(seq, true);
		}

		IcyFrame frame = new IcyFrame("Edit reference image", true, true, true, true);
		frame.setLayout(new BorderLayout());

		EditorState state = new EditorState(exp, viewer, frame, kind);

		frame.add(buildEditorPanel(state), BorderLayout.CENTER);

		Overlay overlay = new EditorOverlay(state);
		state.overlay = overlay;
		seq.addOverlay(overlay);

		frame.addFrameListener(new IcyFrameAdapter() {
			@Override
			public void icyFrameClosed(IcyFrameEvent e) {
				cleanupEditor(state);
			}
		});

		frame.addToDesktopPane();
		frame.pack();
		positionOverMainFrame(frame, parent0);
		frame.setVisible(true);
	}

	private static void cleanupEditor(EditorState state) {
		if (state == null || state.cleanedUp) {
			return;
		}
		state.cleanedUp = true;
		try {
			Sequence refSeq = state.exp != null ? state.exp.getSeqReference() : null;
			if (refSeq != null && state.overlay != null) {
				refSeq.removeOverlay(state.overlay);
			}
		} catch (Exception ex) {
			// ignore
		}
		try {
			if (state.viewer != null) {
				state.viewer.close();
			}
		} catch (Exception ex) {
			// ignore
		}
	}

	private static void positionOverMainFrame(IcyFrame frame, MultiCAFE parent0) {
		if (parent0 == null || parent0.mainFrame == null) {
			frame.center();
			return;
		}
		Rectangle parentBounds = parent0.mainFrame.getBoundsInternal();
		if (parentBounds == null || parentBounds.width < 1 || parentBounds.height < 1) {
			frame.center();
			return;
		}
		Dimension size = frame.getSize();
		int x = parentBounds.x + (parentBounds.width - size.width) / 2;
		int y = parentBounds.y + (parentBounds.height - size.height) / 2;
		frame.setLocation(x, y);
	}

	private static void ensureReferenceImageExists(Experiment exp) {
		if (exp.getSeqCamData().getReferenceImage() != null) {
			return;
		}
		int t = exp.getSeqCamData().getCurrentFrame();
		IcyBufferedImage current = exp.getSeqCamData().getSeqImage(t, 0);
		if (current != null) {
			exp.getSeqCamData().setReferenceImage(IcyBufferedImageUtil.getCopy(current));
		}
	}

	private static JPanel flowRowLeft() {
		FlowLayout flow = new FlowLayout(FlowLayout.LEFT);
		flow.setVgap(2);
		JPanel row = new JPanel(flow);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		return row;
	}

	private static JPanel buildEditorPanel(EditorState state) {
		JPanel root = new JPanel();
		root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
		root.setAlignmentX(Component.LEFT_ALIGNMENT);

		JPanel row1 = flowRowLeft();
		row1.add(new JLabel("Tool:"));
		row1.add(state.pickRadio);
		row1.add(state.paintRadio);
		row1.add(state.surroundRadio);
		row1.add(state.timeMedianRadio);

		JPanel row2 = flowRowLeft();
		row2.add(new JLabel("radius:"));
		row2.add(state.radiusSpinner);
		row2.add(state.ringWidthLabel);
		row2.add(state.ringWidthSpinner);
		row2.add(state.squareCheckBox);

		String saveLabel;
		if (state.editorKind == ReferenceImageKind.LIGHT) {
			saveLabel = "Save " + SequenceLoaderService.getReferenceImageFilename(ReferenceImageKind.LIGHT);
		} else if (state.editorKind == ReferenceImageKind.DARK) {
			saveLabel = "Save " + SequenceLoaderService.getReferenceImageFilename(ReferenceImageKind.DARK);
		} else {
			saveLabel = "Save " + SequenceLoaderService.getReferenceImageFilename(ReferenceImageKind.DEFAULT);
		}

		JButton saveButton = new JButton(saveLabel);
		JButton closeButton = new JButton("Close");

		saveButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				if (state.editorKind == ReferenceImageKind.LIGHT) {
					state.exp.getSeqCamData().setReferenceImageLight(state.exp.getSeqCamData().getReferenceImage());
				} else if (state.editorKind == ReferenceImageKind.DARK) {
					state.exp.getSeqCamData().setReferenceImageDark(state.exp.getSeqCamData().getReferenceImage());
				}
				new SequenceLoaderService().saveReferenceImage(state.exp, state.editorKind);
			}
		});

		closeButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				try {
					if (state.frame != null) {
						state.frame.close();
					}
				} catch (Exception ex) {
					// ignore
				}
			}
		});

		JPanel row3 = flowRowLeft();
		row3.add(new JLabel("picked:"));
		row3.add(state.colorSwatchButton);
		row3.add(saveButton);
		row3.add(closeButton);

		state.pickRadio.setSelected(true);
		state.updateSwatch();
		updateToolDependentControls(state);

		ButtonGroup group = new ButtonGroup();
		group.add(state.pickRadio);
		group.add(state.paintRadio);
		group.add(state.surroundRadio);
		group.add(state.timeMedianRadio);

		ActionListener toolListener = new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				updateToolDependentControls(state);
				if (state.frame != null) {
					state.frame.pack();
				}
			}
		};
		state.pickRadio.addActionListener(toolListener);
		state.paintRadio.addActionListener(toolListener);
		state.surroundRadio.addActionListener(toolListener);
		state.timeMedianRadio.addActionListener(toolListener);

		root.add(row1);
		root.add(row2);
		root.add(row3);
		return root;
	}

	private static void updateToolDependentControls(EditorState state) {
		boolean ring = state.surroundRadio.isSelected();
		state.ringWidthLabel.setVisible(ring);
		state.ringWidthSpinner.setVisible(ring);
	}

	private static final class EditorState {
		final Experiment exp;
		final Viewer viewer;
		final IcyFrame frame;
		final ReferenceImageKind editorKind;
		volatile boolean cleanedUp = false;
		volatile Overlay overlay = null;
		volatile Color pickedColor = new Color(255, 0, 0);

		final JRadioButton pickRadio = new JRadioButton("pick");
		final JRadioButton paintRadio = new JRadioButton("paint");
		final JRadioButton surroundRadio = new JRadioButton("surround");
		final JRadioButton timeMedianRadio = new JRadioButton("time median");
		final JSpinner radiusSpinner = new JSpinner(new SpinnerNumberModel(8, 1, 200, 1));
		final JLabel ringWidthLabel = new JLabel("ring:");
		final JSpinner ringWidthSpinner = new JSpinner(new SpinnerNumberModel(16, 1, 200, 1));
		final JCheckBox squareCheckBox = new JCheckBox("square", false);
		final JButton colorSwatchButton = new JButton("   ");

		EditorState(Experiment exp, Viewer viewer, IcyFrame frame, ReferenceImageKind editorKind) {
			this.exp = exp;
			this.viewer = viewer;
			this.frame = frame;
			this.editorKind = editorKind != null ? editorKind : ReferenceImageKind.DEFAULT;
			colorSwatchButton.setEnabled(false);
		}

		void updateSwatch() {
			colorSwatchButton.setBackground(pickedColor);
		}
	}

	private static final class EditorOverlay extends Overlay {
		private static final int NO_LAST = Integer.MIN_VALUE;

		private final EditorState state;
		private int lastPaintX = NO_LAST;
		private int lastPaintY = NO_LAST;

		EditorOverlay(EditorState state) {
			super("ReferenceImageEditorOverlay");
			this.state = state;
		}

		@Override
		public void mouseClick(MouseEvent event, Point5D.Double imagePoint, IcyCanvas canvas) {
			if (event == null || imagePoint == null || canvas == null) {
				return;
			}
			if (!(canvas instanceof IcyCanvas2D)) {
				return;
			}
			if (canvas.getSequence() == null) {
				return;
			}

			int x = (int) Math.round(imagePoint.getX());
			int y = (int) Math.round(imagePoint.getY());

			IcyBufferedImage ref = state.exp.getSeqCamData() != null ? state.exp.getSeqCamData().getReferenceImage()
					: null;
			if (ref == null) {
				return;
			}

			if (state.pickRadio.isSelected()) {
				Color c = pickColor(ref, x, y);
				if (c != null) {
					state.pickedColor = c;
					state.updateSwatch();
				}
			}
		}

		@Override
		public void mousePressed(MouseEvent event, Point5D.Double imagePoint, IcyCanvas canvas) {
			if (event == null || imagePoint == null || canvas == null || event.getButton() != MouseEvent.BUTTON1) {
				return;
			}
			if (!(canvas instanceof IcyCanvas2D) || canvas.getSequence() == null) {
				return;
			}
			if (!isBrushTool(state)) {
				return;
			}
			IcyBufferedImage ref = state.exp.getSeqCamData() != null ? state.exp.getSeqCamData().getReferenceImage()
					: null;
			if (ref == null) {
				return;
			}
			int x = (int) Math.round(imagePoint.getX());
			int y = (int) Math.round(imagePoint.getY());
			int r = ((Number) state.radiusSpinner.getValue()).intValue();
			boolean square = state.squareCheckBox.isSelected();
			applyBrushAt(state, ref, x, y, r, square);
			lastPaintX = x;
			lastPaintY = y;
			refreshReferenceSequence(ref);
			event.consume();
		}

		@Override
		public void mouseDrag(MouseEvent event, Point5D.Double imagePoint, IcyCanvas canvas) {
			if (event == null || imagePoint == null || canvas == null) {
				return;
			}
			if ((event.getModifiersEx() & MouseEvent.BUTTON1_DOWN_MASK) == 0) {
				return;
			}
			if (!(canvas instanceof IcyCanvas2D) || canvas.getSequence() == null) {
				return;
			}
			if (!isBrushTool(state) || lastPaintX == NO_LAST) {
				return;
			}
			IcyBufferedImage ref = state.exp.getSeqCamData() != null ? state.exp.getSeqCamData().getReferenceImage()
					: null;
			if (ref == null) {
				return;
			}
			int x = (int) Math.round(imagePoint.getX());
			int y = (int) Math.round(imagePoint.getY());
			int r = ((Number) state.radiusSpinner.getValue()).intValue();
			boolean square = state.squareCheckBox.isSelected();
			brushStroke(state, ref, lastPaintX, lastPaintY, x, y, r, square);
			lastPaintX = x;
			lastPaintY = y;
			refreshReferenceSequence(ref);
			event.consume();
		}

		@Override
		public void mouseReleased(MouseEvent event, Point5D.Double imagePoint, IcyCanvas canvas) {
			boolean hadActiveBrushStroke = lastPaintX != NO_LAST;
			lastPaintX = NO_LAST;
			lastPaintY = NO_LAST;
			if (event != null && event.getButton() == MouseEvent.BUTTON1 && isBrushTool(state) && hadActiveBrushStroke) {
				event.consume();
			}
		}

		private void refreshReferenceSequence(IcyBufferedImage ref) {
			try {
				Sequence refSeq = state.exp.getSeqReference();
				if (refSeq != null) {
					refSeq.setImage(0, 0, ref);
				}
			} catch (Exception e) {
				Logger.warn("ReferenceImageEditor: failed to refresh reference sequence", e);
			}
		}

		private static boolean isBrushTool(EditorState s) {
			return s.paintRadio.isSelected() || s.surroundRadio.isSelected() || s.timeMedianRadio.isSelected();
		}

		private void applyBrushAt(EditorState st, IcyBufferedImage ref, int x, int y, int r, boolean square) {
			if (st.paintRadio.isSelected()) {
				paintPatch(ref, x, y, r, square, st.pickedColor);
				return;
			}
			if (st.surroundRadio.isSelected()) {
				int ringW = ((Number) st.ringWidthSpinner.getValue()).intValue();
				Color c = sampleSurroundMedian(ref, x, y, r, ringW, square);
				if (c != null) {
					paintPatch(ref, x, y, r, square, c);
				}
				return;
			}
			if (st.timeMedianRadio.isSelected()) {
				SequenceCamData cam = st.exp.getSeqCamData();
				fillPatchTimeMedian(ref, cam, x, y, r, square);
			}
		}

		private void brushStroke(EditorState st, IcyBufferedImage ref, int x0, int y0, int x1, int y1, int r,
				boolean square) {
			int dx = Math.abs(x1 - x0);
			int dy = Math.abs(y1 - y0);
			int sx = x0 < x1 ? 1 : -1;
			int sy = y0 < y1 ? 1 : -1;
			int err = dx - dy;
			int x = x0;
			int y = y0;
			for (;;) {
				applyBrushAt(st, ref, x, y, r, square);
				if (x == x1 && y == y1) {
					break;
				}
				int e2 = 2 * err;
				if (e2 > -dy) {
					err -= dy;
					x += sx;
				}
				if (e2 < dx) {
					err += dx;
					y += sy;
				}
			}
		}

		private static Color pickColor(IcyBufferedImage img, int x, int y) {
			if (img == null) {
				return null;
			}
			int w = img.getSizeX();
			int h = img.getSizeY();
			if (x < 0 || y < 0 || x >= w || y >= h) {
				return null;
			}
			try {
				IcyBufferedImageCursor c = new IcyBufferedImageCursor(img);
				int r = (int) Math.round(c.get(x, y, 0));
				int g = img.getSizeC() > 1 ? (int) Math.round(c.get(x, y, 1)) : r;
				int b = img.getSizeC() > 2 ? (int) Math.round(c.get(x, y, 2)) : r;
				r = clamp255(r);
				g = clamp255(g);
				b = clamp255(b);
				return new Color(r, g, b);
			} catch (Exception e) {
				return null;
			}
		}

		private static void paintPatch(IcyBufferedImage img, int cx, int cy, int radius, boolean square, Color color) {
			if (img == null || color == null) {
				return;
			}
			int w = img.getSizeX();
			int h = img.getSizeY();
			int planes = img.getSizeC();

			int r = color.getRed();
			int g = color.getGreen();
			int b = color.getBlue();

			int x0 = Math.max(0, cx - radius);
			int x1 = Math.min(w - 1, cx + radius);
			int y0 = Math.max(0, cy - radius);
			int y1 = Math.min(h - 1, cy + radius);

			int rr = radius * radius;
			IcyBufferedImageCursor cur = new IcyBufferedImageCursor(img);
			try {
				for (int y = y0; y <= y1; y++) {
					int dy = y - cy;
					for (int x = x0; x <= x1; x++) {
						int dx = x - cx;
						if (!square) {
							if (dx * dx + dy * dy > rr) {
								continue;
							}
						}
						if (planes > 0)
							cur.set(x, y, 0, r);
						if (planes > 1)
							cur.set(x, y, 1, g);
						if (planes > 2)
							cur.set(x, y, 2, b);
					}
				}
			} finally {
				cur.commitChanges();
			}
		}

		private static boolean sameDimensions(IcyBufferedImage a, IcyBufferedImage b) {
			if (a == null || b == null) {
				return false;
			}
			return a.getSizeX() == b.getSizeX() && a.getSizeY() == b.getSizeY() && a.getSizeC() == b.getSizeC();
		}

		private static boolean inSurroundRing(int dx, int dy, int brushR, int ringW, boolean square) {
			if (square) {
				int mx = Math.max(Math.abs(dx), Math.abs(dy));
				return mx > brushR && mx <= brushR + ringW;
			}
			long d2 = (long) dx * dx + (long) dy * dy;
			long inner = (long) brushR * brushR;
			long outer = (long) (brushR + ringW) * (brushR + ringW);
			return d2 > inner && d2 <= outer;
		}

		private static Color sampleSurroundMedian(IcyBufferedImage ref, int cx, int cy, int brushR, int ringW,
				boolean square) {
			if (ref == null || ringW < 1) {
				return null;
			}
			int w = ref.getSizeX();
			int h = ref.getSizeY();
			int planes = ref.getSizeC();
			int outer = brushR + ringW;
			int cap = Math.max(16, (2 * outer + 1) * (2 * outer + 1));
			int[] rs = new int[cap];
			int[] gs = planes > 1 ? new int[cap] : null;
			int[] bs = planes > 2 ? new int[cap] : null;
			int n = 0;
			IcyBufferedImageCursor cur = new IcyBufferedImageCursor(ref);
			for (int y = Math.max(0, cy - outer); y <= Math.min(h - 1, cy + outer); y++) {
				int dy = y - cy;
				for (int x = Math.max(0, cx - outer); x <= Math.min(w - 1, cx + outer); x++) {
					int dx = x - cx;
					if (!inSurroundRing(dx, dy, brushR, ringW, square)) {
						continue;
					}
					if (n >= cap) {
						return null;
					}
					rs[n] = (int) Math.round(cur.get(x, y, 0));
					if (gs != null) {
						gs[n] = (int) Math.round(cur.get(x, y, 1));
					}
					if (bs != null) {
						bs[n] = (int) Math.round(cur.get(x, y, 2));
					}
					n++;
				}
			}
			if (n == 0) {
				return null;
			}
			Arrays.sort(rs, 0, n);
			int mr = rs[(n - 1) / 2];
			int mg = mr;
			int mb = mr;
			if (gs != null) {
				Arrays.sort(gs, 0, n);
				mg = gs[(n - 1) / 2];
			}
			if (bs != null) {
				Arrays.sort(bs, 0, n);
				mb = bs[(n - 1) / 2];
			}
			return new Color(clamp255(mr), clamp255(mg), clamp255(mb));
		}

		private static void fillPatchTimeMedian(IcyBufferedImage ref, SequenceCamData cam, int cx, int cy, int radius,
				boolean square) {
			if (ref == null || cam == null) {
				return;
			}
			Sequence seq = cam.getSequence();
			if (seq == null) {
				return;
			}
			int tSize = seq.getSizeT();
			if (tSize < 1) {
				return;
			}
			IcyBufferedImageCursor[] tc = new IcyBufferedImageCursor[tSize];
			for (int t = 0; t < tSize; t++) {
				try {
					IcyBufferedImage im = cam.getSeqImage(t, 0);
					if (im != null && sameDimensions(ref, im)) {
						tc[t] = new IcyBufferedImageCursor(im);
					}
				} catch (Exception e) {
					// skip frame
				}
			}
			int usable = 0;
			for (IcyBufferedImageCursor c : tc) {
				if (c != null) {
					usable++;
				}
			}
			if (usable == 0) {
				return;
			}

			int w = ref.getSizeX();
			int h = ref.getSizeY();
			int planes = ref.getSizeC();
			int x0 = Math.max(0, cx - radius);
			int x1 = Math.min(w - 1, cx + radius);
			int y0 = Math.max(0, cy - radius);
			int y1 = Math.min(h - 1, cy + radius);
			int rr = radius * radius;
			int[] work = new int[tSize];
			IcyBufferedImageCursor out = new IcyBufferedImageCursor(ref);
			try {
				for (int y = y0; y <= y1; y++) {
					int dy = y - cy;
					for (int x = x0; x <= x1; x++) {
						int dx = x - cx;
						if (!square) {
							if (dx * dx + dy * dy > rr) {
								continue;
							}
						} else if (Math.abs(dx) > radius || Math.abs(dy) > radius) {
							continue;
						}
						for (int c = 0; c < planes; c++) {
							int n = 0;
							for (int t = 0; t < tSize; t++) {
								if (tc[t] == null) {
									continue;
								}
								work[n++] = (int) Math.round(tc[t].get(x, y, c));
							}
							if (n > 0) {
								Arrays.sort(work, 0, n);
								int med = clamp255(work[(n - 1) / 2]);
								out.set(x, y, c, med);
							}
						}
					}
				}
			} finally {
				out.commitChanges();
			}
		}

		private static int clamp255(int v) {
			if (v < 0)
				return 0;
			if (v > 255)
				return 255;
			return v;
		}
	}
}

