package plugins.fmp.multitools.experiment.ui;

import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeListener;

import icy.gui.viewer.Viewer;
import icy.gui.viewer.ViewerEvent;
import icy.gui.viewer.ViewerListener;
import icy.image.IcyBufferedImage;
import icy.image.ImageUtil;
import icy.sequence.DimensionId;
import icy.sequence.Sequence;
import icy.type.collection.array.Array1DUtil;
import plugins.fmp.multitools.canvas2D.Canvas2D_3Transforms;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.ui.host.CorrectDriftHost;
import plugins.fmp.multitools.tools.Logger;
import plugins.fmp.multitools.tools.JComponents.JComboBoxExperimentLazy;
import plugins.fmp.multitools.tools.imageTransform.CanvasImageTransformOptions;
import plugins.fmp.multitools.tools.imageTransform.ImageTransformEnums;

/**
 * Manual drift correction panel (v1).
 *
 * <p>
 * Workflow:
 * <ul>
 * <li>Adjust X/Y translation</li>
 * <li>Toggle View (difference) and browse frames to judge quality</li>
 * <li>Apply to a frame range (backup originals to {@code original_images/} and overwrite JPEGs)</li>
 * </ul>
 */
public class CorrectDriftPanel extends JPanel implements ViewerListener {

	private static final long serialVersionUID = 1L;

	private static final int MIN_FRAME = 0;
	private static final int MAX_FRAME = 100000;

	private final JButton setReferenceButton = new JButton("Set reference = current frame");
	private final JLabel referenceLabel = new JLabel("Reference: (not set)");

	private final JSpinner xSpinner = new JSpinner(new SpinnerNumberModel(0, -2000, 2000, 1));
	private final JSpinner ySpinner = new JSpinner(new SpinnerNumberModel(0, -2000, 2000, 1));
	private final JButton nudgeLeft = new JButton("←1");
	private final JButton nudgeRight = new JButton("→1");
	private final JButton nudgeUp = new JButton("↑1");
	private final JButton nudgeDown = new JButton("↓1");

	private final JCheckBox viewDifferenceCheck = new JCheckBox("View difference (t - ref)", false);

	private final JSpinner rangeStartSpinner = new JSpinner(new SpinnerNumberModel(0, MIN_FRAME, MAX_FRAME, 1));
	private final JSpinner rangeEndSpinner = new JSpinner(new SpinnerNumberModel(0, MIN_FRAME, MAX_FRAME, 1));
	private final JButton applyButton = new JButton("Apply to range (overwrite JPEGs)");
	private final JButton restoreButton = new JButton("Restore range from original_images");

	private JComboBoxExperimentLazy experimentList = new JComboBoxExperimentLazy();

	private IcyBufferedImage referenceImage = null;

	public void init(GridLayout capLayout, CorrectDriftHost host) {
		this.experimentList = host.getExperimentsCombo();
		initializeUI(capLayout);
		defineActionListeners();
	}

	public void resetFrameIndex() {
		// noop in v1
	}

	private void initializeUI(GridLayout capLayout) {
		setLayout(capLayout);

		FlowLayout flowlayout = new FlowLayout(FlowLayout.LEFT);
		flowlayout.setVgap(1);

		JPanel refPanel = new JPanel(flowlayout);
		refPanel.add(setReferenceButton);
		refPanel.add(referenceLabel);
		add(refPanel);

		JPanel adjustPanel = new JPanel(flowlayout);
		adjustPanel.add(new JLabel("Offset X"));
		adjustPanel.add(xSpinner);
		xSpinner.setPreferredSize(new Dimension(60, 20));
		adjustPanel.add(new JLabel("Y"));
		adjustPanel.add(ySpinner);
		ySpinner.setPreferredSize(new Dimension(60, 20));
		adjustPanel.add(nudgeLeft);
		adjustPanel.add(nudgeRight);
		adjustPanel.add(nudgeUp);
		adjustPanel.add(nudgeDown);
		add(adjustPanel);

		JPanel viewPanel = new JPanel(flowlayout);
		viewPanel.add(viewDifferenceCheck);
		add(viewPanel);

		JPanel applyPanel = new JPanel(flowlayout);
		applyPanel.add(new JLabel("Apply range start"));
		applyPanel.add(rangeStartSpinner);
		rangeStartSpinner.setPreferredSize(new Dimension(60, 20));
		applyPanel.add(new JLabel("end"));
		applyPanel.add(rangeEndSpinner);
		rangeEndSpinner.setPreferredSize(new Dimension(60, 20));
		applyPanel.add(applyButton);
		applyPanel.add(restoreButton);
		add(applyPanel);
	}

	private void defineActionListeners() {
		setReferenceButton.addActionListener(e -> setReferenceFromCurrentFrame());

		viewDifferenceCheck.addActionListener(e -> refreshDifferenceView());

		ChangeListener offsetListener = e -> {
			if (viewDifferenceCheck.isSelected()) {
				refreshDifferenceView();
			}
		};
		xSpinner.addChangeListener(offsetListener);
		ySpinner.addChangeListener(offsetListener);

		nudgeLeft.addActionListener(e -> xSpinner.setValue(((Integer) xSpinner.getValue()) - 1));
		nudgeRight.addActionListener(e -> xSpinner.setValue(((Integer) xSpinner.getValue()) + 1));
		nudgeUp.addActionListener(e -> ySpinner.setValue(((Integer) ySpinner.getValue()) - 1));
		nudgeDown.addActionListener(e -> ySpinner.setValue(((Integer) ySpinner.getValue()) + 1));

		applyButton.addActionListener(e -> applyToRange());
		restoreButton.addActionListener(e -> restoreRange());

		rangeStartSpinner.addChangeListener(e -> ensureRangeOrder());
		rangeEndSpinner.addChangeListener(e -> ensureRangeOrder());
	}

	private void ensureRangeOrder() {
		int s = (int) rangeStartSpinner.getValue();
		int en = (int) rangeEndSpinner.getValue();
		if (en < s) {
			rangeEndSpinner.setValue(s);
		}
	}

	private Experiment getCurrentExperiment() {
		return (Experiment) experimentList.getSelectedItem();
	}

	private void setReferenceFromCurrentFrame() {
		Experiment exp = getCurrentExperiment();
		if (exp == null || exp.getSeqCamData() == null || exp.getSeqCamData().getSequence() == null) {
			return;
		}
		Sequence seq = exp.getSeqCamData().getSequence();
		Viewer v = seq.getFirstViewer();
		int t = v != null ? v.getPositionT() : 0;
		IcyBufferedImage img = seq.getImage(t, 0);
		if (img == null) {
			return;
		}

		referenceImage = img;
		referenceLabel.setText("Reference: t=" + t);

		int n = seq.getSizeT();
		if (n > 0) {
			rangeStartSpinner.setValue(0);
			rangeEndSpinner.setValue(n - 1);
		}

		refreshDifferenceView();
	}

	private void refreshDifferenceView() {
		Experiment exp = getCurrentExperiment();
		if (exp == null || exp.getSeqCamData() == null || exp.getSeqCamData().getSequence() == null) {
			return;
		}

		Sequence seq = exp.getSeqCamData().getSequence();
		Viewer v = seq.getFirstViewer();
		if (v == null) {
			return;
		}

		if (!(v.getCanvas() instanceof Canvas2D_3Transforms)) {
			Logger.warn("CorrectDriftPanel: viewer canvas is not Canvas2D_3Transforms; difference view unavailable");
			return;
		}

		Canvas2D_3Transforms canvas = (Canvas2D_3Transforms) v.getCanvas();
		CanvasImageTransformOptions options = canvas.getOptionsStep1();

		if (!viewDifferenceCheck.isSelected() || referenceImage == null) {
			options.backgroundImage = null;
			canvas.setTransformStep1(ImageTransformEnums.NONE, options);
			return;
		}

		int dx = (int) xSpinner.getValue();
		int dy = (int) ySpinner.getValue();

		IcyBufferedImage shiftedRef = translate(referenceImage, -dx, -dy);
		options.backgroundImage = shiftedRef;
		canvas.setTransformStep1(ImageTransformEnums.SUBTRACT_REF, options);
	}

	private void applyToRange() {
		Experiment exp = getCurrentExperiment();
		if (exp == null || exp.getSeqCamData() == null) {
			return;
		}
		String imagesDir = exp.getSeqCamData().getImagesDirectory();
		if (imagesDir == null) {
			return;
		}

		int dx = (int) xSpinner.getValue();
		int dy = (int) ySpinner.getValue();
		int start = (int) rangeStartSpinner.getValue();
		int end = (int) rangeEndSpinner.getValue();

		new Thread(() -> {
			try {
				Path originalsDir = Path.of(imagesDir, "original_images");
				Files.createDirectories(originalsDir);
				Path manifest = originalsDir.resolve("backup_manifest.txt");
				try (BufferedWriter out = new BufferedWriter(new FileWriter(manifest.toFile(), true))) {
					for (int t = start; t <= end; t++) {
						String fileName = exp.getSeqCamData().getFileNameFromImageList(t);
						if (fileName == null) {
							continue;
						}
						Path src = Path.of(fileName);
						if (!Files.exists(src)) {
							continue;
						}
						Path backup = originalsDir.resolve(src.getFileName().toString());
						if (!Files.exists(backup)) {
							Files.copy(src, backup, StandardCopyOption.COPY_ATTRIBUTES);
							out.write(backup.getFileName().toString());
							out.newLine();
							out.flush();
						}

						BufferedImage img = ImageUtil.load(src.toFile(), true);
						if (img == null) {
							continue;
						}
						BufferedImage corrected = translate(img, dx, dy);
						ImageUtil.save(corrected, "jpg", src.toFile());
					}
				}
			} catch (Exception ex) {
				Logger.error("CorrectDriftPanel: apply failed: " + ex.getMessage(), ex);
			} finally {
				SwingUtilities.invokeLater(this::refreshDifferenceView);
			}
		}, "ManualDriftApply").start();
	}

	private void restoreRange() {
		Experiment exp = getCurrentExperiment();
		if (exp == null || exp.getSeqCamData() == null) {
			return;
		}
		String imagesDir = exp.getSeqCamData().getImagesDirectory();
		if (imagesDir == null) {
			return;
		}
		int start = (int) rangeStartSpinner.getValue();
		int end = (int) rangeEndSpinner.getValue();

		new Thread(() -> {
			try {
				Path originalsDir = Path.of(imagesDir, "original_images");
				if (!Files.exists(originalsDir)) {
					return;
				}
				for (int t = start; t <= end; t++) {
					String fileName = exp.getSeqCamData().getFileNameFromImageList(t);
					if (fileName == null) {
						continue;
					}
					Path dst = Path.of(fileName);
					Path src = originalsDir.resolve(dst.getFileName().toString());
					if (Files.exists(src)) {
						Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
					}
				}
			} catch (Exception ex) {
				Logger.error("CorrectDriftPanel: restore failed: " + ex.getMessage(), ex);
			} finally {
				SwingUtilities.invokeLater(this::refreshDifferenceView);
			}
		}, "ManualDriftRestore").start();
	}

	@Override
	public void viewerChanged(ViewerEvent event) {
		if ((event.getType() == ViewerEvent.ViewerEventType.POSITION_CHANGED) && (event.getDim() == DimensionId.T)) {
			// no-op; user can browse frames freely
		}
	}

	@Override
	public void viewerClosed(Viewer viewer) {
		viewer.removeListener(this);
	}

	private static IcyBufferedImage translate(IcyBufferedImage src, int dx, int dy) {
		if (src == null) {
			return null;
		}
		IcyBufferedImage out = new IcyBufferedImage(src.getWidth(), src.getHeight(), src.getSizeC(), src.getDataType_());
		out.beginUpdate();
		try {
			for (int ch = 0; ch < src.getSizeC(); ch++) {
				double[] srcData = Array1DUtil.arrayToDoubleArray(src.getDataXY(ch), src.isSignedDataType());
				double[] dstData = Array1DUtil.arrayToDoubleArray(out.getDataXY(ch), out.isSignedDataType());
				int w = src.getWidth();
				int h = src.getHeight();
				for (int y = 0; y < h; y++) {
					int ys = y - dy;
					if (ys < 0 || ys >= h) {
						continue;
					}
					int rowDst = y * w;
					int rowSrc = ys * w;
					for (int x = 0; x < w; x++) {
						int xs = x - dx;
						if (xs < 0 || xs >= w) {
							continue;
						}
						dstData[rowDst + x] = srcData[rowSrc + xs];
					}
				}
				Array1DUtil.doubleArrayToArray(dstData, out.getDataXY(ch));
			}
		} finally {
			out.endUpdate();
		}
		return out;
	}

	private static BufferedImage translate(BufferedImage src, int dx, int dy) {
		if (src == null) {
			return null;
		}
		BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
		Graphics2D g = out.createGraphics();
		try {
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			g.drawImage(src, dx, dy, null);
		} finally {
			g.dispose();
		}
		return out;
	}
}
