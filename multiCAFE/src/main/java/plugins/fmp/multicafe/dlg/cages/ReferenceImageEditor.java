package plugins.fmp.multicafe.dlg.cages;

import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

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
import icy.gui.frame.IcyFrame;
import icy.gui.viewer.Viewer;
import icy.image.IcyBufferedImage;
import icy.image.IcyBufferedImageCursor;
import icy.image.IcyBufferedImageUtil;
import icy.painter.Overlay;
import icy.sequence.Sequence;
import icy.type.point.Point5D;
import plugins.fmp.multicafe.MultiCAFE;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.service.SequenceLoaderService;
import plugins.fmp.multitools.tools.Logger;

/**
 * Simple editor for the reference (background) image.
 * <p>
 * Two tools:
 * - pick: click to pick RGB color from the reference image
 * - paint: click to deposit a circular/square patch of the picked color
 * </p>
 */
public class ReferenceImageEditor {

	private ReferenceImageEditor() {
	}

	public static void open(MultiCAFE parent0, Experiment exp) {
		if (exp == null || exp.getSeqCamData() == null || exp.getSeqCamData().getSequence() == null) {
			return;
		}

		ensureReferenceImageExists(exp);
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
		frame.setLayout(new GridLayout(2, 1));

		EditorState state = new EditorState(exp, viewer, frame);

		JPanel toolsPanel = buildToolsPanel(state);
		frame.add(toolsPanel);

		JPanel actionsPanel = buildActionsPanel(state);
		frame.add(actionsPanel);

		Overlay overlay = new EditorOverlay(state);
		state.overlay = overlay;
		seq.addOverlay(overlay);

		frame.addToDesktopPane();
		frame.pack();
		frame.center();
		frame.setVisible(true);
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

	private static JPanel buildToolsPanel(EditorState state) {
		FlowLayout flow = new FlowLayout(FlowLayout.LEFT);
		flow.setVgap(0);
		JPanel panel = new JPanel(flow);

		panel.add(new JLabel("Tool:"));
		panel.add(state.pickRadio);
		panel.add(state.paintRadio);
		panel.add(new JLabel("radius:"));
		panel.add(state.radiusSpinner);
		panel.add(state.squareCheckBox);
		panel.add(new JLabel("picked:"));
		panel.add(state.colorSwatchButton);

		state.pickRadio.setSelected(true);
		state.updateSwatch();

		ButtonGroup group = new ButtonGroup();
		group.add(state.pickRadio);
		group.add(state.paintRadio);

		return panel;
	}

	private static JPanel buildActionsPanel(EditorState state) {
		FlowLayout flow = new FlowLayout(FlowLayout.LEFT);
		flow.setVgap(0);
		JPanel panel = new JPanel(flow);

		JButton saveButton = new JButton("Save referenceImage.jpg");
		JButton closeButton = new JButton("Close");

		saveButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				new SequenceLoaderService().saveReferenceImage(state.exp);
			}
		});

		closeButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				try {
					Sequence refSeq = state.exp != null ? state.exp.getSeqReference() : null;
					if (refSeq != null && state.overlay != null) {
						refSeq.removeOverlay(state.overlay);
					}
				} catch (Exception ex) {
					// ignore
				}
				try {
					if (state.frame != null) {
						state.frame.close();
					}
				} catch (Exception ex) {
					// ignore
				}
			}
		});

		panel.add(saveButton);
		panel.add(closeButton);
		return panel;
	}

	private static final class EditorState {
		final Experiment exp;
		final IcyFrame frame;
		volatile Overlay overlay = null;
		volatile Color pickedColor = new Color(255, 0, 0);

		final JRadioButton pickRadio = new JRadioButton("pick");
		final JRadioButton paintRadio = new JRadioButton("paint");
		final JSpinner radiusSpinner = new JSpinner(new SpinnerNumberModel(8, 1, 200, 1));
		final JCheckBox squareCheckBox = new JCheckBox("square", false);
		final JButton colorSwatchButton = new JButton("   ");

		EditorState(Experiment exp, Viewer viewer, IcyFrame frame) {
			this.exp = exp;
			this.frame = frame;
			colorSwatchButton.setEnabled(false);
		}

		void updateSwatch() {
			colorSwatchButton.setBackground(pickedColor);
		}
	}

	private static final class EditorOverlay extends Overlay {
		private final EditorState state;

		EditorOverlay(EditorState state) {
			super("ReferenceImageEditorOverlay");
			this.state = state;
		}

		@Override
		public void mouseClick(java.awt.event.MouseEvent event, Point5D.Double imagePoint, IcyCanvas canvas) {
			if (event == null || imagePoint == null || canvas == null) {
				return;
			}
			if (!(canvas instanceof IcyCanvas2D)) {
				return;
			}
			Sequence seq = canvas.getSequence();
			if (seq == null) {
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
				return;
			}

			if (state.paintRadio.isSelected()) {
				int r = ((Number) state.radiusSpinner.getValue()).intValue();
				boolean square = state.squareCheckBox.isSelected();
				paintPatch(ref, x, y, r, square, state.pickedColor);

				try {
					// Ensure sequence refresh
					Sequence refSeq = state.exp.getSeqReference();
					if (refSeq != null) {
						refSeq.setImage(0, 0, ref);
					}
				} catch (Exception e) {
					Logger.warn("ReferenceImageEditor: failed to refresh reference sequence", e);
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

		private static int clamp255(int v) {
			if (v < 0)
				return 0;
			if (v > 255)
				return 255;
			return v;
		}
	}
}

