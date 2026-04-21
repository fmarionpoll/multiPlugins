package plugins.fmp.multitools.experiment.ui;

import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.image.BufferedImage;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JToggleButton;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeListener;
import javax.vecmath.Vector2d;

import icy.gui.viewer.Viewer;
import icy.gui.viewer.ViewerEvent;
import icy.gui.viewer.ViewerListener;
import icy.image.IcyBufferedImage;
import icy.image.ImageUtil;
import icy.sequence.DimensionId;
import icy.sequence.Sequence;
import plugins.fmp.multitools.canvas2D.Canvas2D_3Transforms;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.ui.host.CorrectDriftHost;
import plugins.fmp.multitools.tools.Logger;
import plugins.fmp.multitools.tools.JComponents.JComboBoxExperimentLazy;
import plugins.fmp.multitools.tools.imageTransform.CanvasImageTransformOptions;
import plugins.fmp.multitools.tools.imageTransform.ImageTransformEnums;
import plugins.fmp.multitools.tools.registration.GaspardRigidRegistration;

/**
 * Manual drift correction: per-frame align with reference, optional batch range.
 */
public class CorrectDriftPanel extends JPanel implements ViewerListener {

	private static final long serialVersionUID = 1L;

	private static final int MIN_FRAME = 0;
	private static final int MAX_FRAME = 100000;

	private final JButton setReferenceButton = new JButton("Set reference = current frame");
	private final JLabel referenceLabel = new JLabel("Reference: (not set)");

	private final JLabel imageIndexLabel = new JLabel("Image index");
	private final JSpinner imageIndexSpinner = new JSpinner(new SpinnerNumberModel(0, 0, MAX_FRAME, 1));
	private final JToggleButton viewTransformToggle = new JToggleButton("View transform");
	private final JButton applyTransformButton = new JButton("Apply transform");

	private final JSpinner xSpinner = new JSpinner(new SpinnerNumberModel(0.0, -2000.0, 2000.0, 0.5));
	private final JSpinner ySpinner = new JSpinner(new SpinnerNumberModel(0.0, -2000.0, 2000.0, 0.5));
	private final JButton nudgeLeft = new JButton("←0.5");
	private final JButton nudgeRight = new JButton("→0.5");
	private final JButton nudgeUp = new JButton("↑0.5");
	private final JButton nudgeDown = new JButton("↓0.5");

	private final JSpinner rangeStartSpinner = new JSpinner(new SpinnerNumberModel(0, MIN_FRAME, MAX_FRAME, 1));
	private final JSpinner rangeEndSpinner = new JSpinner(new SpinnerNumberModel(0, MIN_FRAME, MAX_FRAME, 1));
	private final JButton applyRangeButton = new JButton("Apply to range (overwrite JPEGs)");
	private final JButton restoreButton = new JButton("Restore range from original_images");

	private JComboBoxExperimentLazy experimentList = new JComboBoxExperimentLazy();

	private Integer referenceFrameIndex = null;

	private boolean syncingFromViewer = false;
	private boolean syncingFromSpinner = false;

	public void init(GridLayout capLayout, CorrectDriftHost host) {
		this.experimentList = host.getExperimentsCombo();
		initializeUI(capLayout);
		defineActionListeners();
	}

	public void resetFrameIndex() {
		Experiment exp = getCurrentExperiment();
		if (exp == null || exp.getSeqCamData() == null || exp.getSeqCamData().getSequence() == null) {
			return;
		}
		Sequence seq = exp.getSeqCamData().getSequence();
		Viewer v = seq.getFirstViewer();
		int t = v != null ? v.getPositionT() : 0;
		syncingFromViewer = true;
		try {
			clampSpinnerToSequence(seq);
			imageIndexSpinner.setValue(clampT(t, seq.getSizeT()));
		} finally {
			syncingFromViewer = false;
		}
	}

	private void initializeUI(GridLayout capLayout) {
		setLayout(capLayout);

		FlowLayout flowlayout = new FlowLayout(FlowLayout.LEFT);
		flowlayout.setVgap(1);

		JPanel refPanel = new JPanel(flowlayout);
		refPanel.add(setReferenceButton);
		refPanel.add(referenceLabel);
		add(refPanel);

		JPanel framePanel = new JPanel(flowlayout);
		framePanel.add(imageIndexLabel);
		framePanel.add(imageIndexSpinner);
		imageIndexSpinner.setPreferredSize(new Dimension(70, 22));
		framePanel.add(viewTransformToggle);
		framePanel.add(applyTransformButton);
		add(framePanel);

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

		JPanel batchPanel = new JPanel(flowlayout);
		batchPanel.add(new JLabel("Batch range"));
		batchPanel.add(new JLabel("start"));
		batchPanel.add(rangeStartSpinner);
		rangeStartSpinner.setPreferredSize(new Dimension(60, 20));
		batchPanel.add(new JLabel("end"));
		batchPanel.add(rangeEndSpinner);
		rangeEndSpinner.setPreferredSize(new Dimension(60, 20));
		batchPanel.add(applyRangeButton);
		batchPanel.add(restoreButton);
		add(batchPanel);
	}

	private void defineActionListeners() {
		setReferenceButton.addActionListener(e -> setReferenceFromCurrentFrame());

		viewTransformToggle.addActionListener(e -> refreshDifferenceView());

		imageIndexSpinner.addChangeListener(e -> onImageIndexSpinnerChanged());

		ChangeListener offsetListener = e -> {
			if (viewTransformToggle.isSelected()) {
				refreshDifferenceView();
			}
		};
		xSpinner.addChangeListener(offsetListener);
		ySpinner.addChangeListener(offsetListener);

		nudgeLeft.addActionListener(e -> xSpinner.setValue(((Number) xSpinner.getValue()).doubleValue() - 0.5));
		nudgeRight.addActionListener(e -> xSpinner.setValue(((Number) xSpinner.getValue()).doubleValue() + 0.5));
		nudgeUp.addActionListener(e -> ySpinner.setValue(((Number) ySpinner.getValue()).doubleValue() - 0.5));
		nudgeDown.addActionListener(e -> ySpinner.setValue(((Number) ySpinner.getValue()).doubleValue() + 0.5));

		applyTransformButton.addActionListener(e -> applyTransformCurrentFrame());
		applyRangeButton.addActionListener(e -> applyToRange());
		restoreButton.addActionListener(e -> restoreRange());

		rangeStartSpinner.addChangeListener(e -> ensureRangeOrder());
		rangeEndSpinner.addChangeListener(e -> ensureRangeOrder());
	}

	private void onImageIndexSpinnerChanged() {
		if (syncingFromViewer) {
			return;
		}
		Experiment exp = getCurrentExperiment();
		if (exp == null || exp.getSeqCamData() == null || exp.getSeqCamData().getSequence() == null) {
			return;
		}
		Sequence seq = exp.getSeqCamData().getSequence();
		Viewer v = seq.getFirstViewer();
		if (v == null) {
			return;
		}
		int sizeT = seq.getSizeT();
		int t = clampT((int) imageIndexSpinner.getValue(), sizeT);
		if (t != (int) imageIndexSpinner.getValue()) {
			syncingFromSpinner = true;
			try {
				imageIndexSpinner.setValue(t);
			} finally {
				syncingFromSpinner = false;
			}
		}
		syncingFromSpinner = true;
		try {
			v.setPositionT(t);
		} finally {
			SwingUtilities.invokeLater(() -> syncingFromSpinner = false);
		}
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

	private static int clampT(int t, int sizeT) {
		if (sizeT <= 0) {
			return 0;
		}
		if (t < 0) {
			return 0;
		}
		if (t >= sizeT) {
			return sizeT - 1;
		}
		return t;
	}

	private void clampSpinnerToSequence(Sequence seq) {
		int max = Math.max(0, seq.getSizeT() - 1);
		SpinnerNumberModel m = (SpinnerNumberModel) imageIndexSpinner.getModel();
		m.setMaximum(max);
		int min = ((Number) m.getMinimum()).intValue();
		if (min > max) {
			m.setMinimum(0);
		}
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

		referenceFrameIndex = t;

		IcyBufferedImage copy = new IcyBufferedImage(img.getWidth(), img.getHeight(), img.getSizeC(),
				img.getDataType_());
		for (int c = 0; c < img.getSizeC(); c++) {
			copy.copyData(img, c, c);
			copy.setDataXY(c, copy.getDataXY(c));
		}
		exp.getSeqCamData().setReferenceImage(copy);
		referenceLabel.setText("Reference: t=" + t);

		int n = seq.getSizeT();
		if (n > 0) {
			rangeStartSpinner.setValue(0);
			rangeEndSpinner.setValue(n - 1);
		}

		clampSpinnerToSequence(seq);
		syncingFromViewer = true;
		try {
			imageIndexSpinner.setValue(clampT(t, seq.getSizeT()));
		} finally {
			syncingFromViewer = false;
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

		IcyBufferedImage storedRef = exp.getSeqCamData().getReferenceImage();
		if (!viewTransformToggle.isSelected() || storedRef == null) {
			options.backgroundImage = null;
			options.translateDx = 0;
			options.translateDy = 0;
			canvas.setTransformStep1(ImageTransformEnums.NONE, options);
			return;
		}

		canvas.addTransformStep1(ImageTransformEnums.SHIFT_SUBTRACT_REF);

		double dx = ((Number) xSpinner.getValue()).doubleValue();
		double dy = ((Number) ySpinner.getValue()).doubleValue();

		options.backgroundImage = storedRef;
		options.translateDx = dx;
		options.translateDy = dy;
		canvas.setTransformStep1(ImageTransformEnums.SHIFT_SUBTRACT_REF, options);
	}

	private void applyTransformCurrentFrame() {
		Experiment exp = getCurrentExperiment();
		if (exp == null || exp.getSeqCamData() == null || exp.getSeqCamData().getSequence() == null) {
			return;
		}
		String imagesDir = exp.getSeqCamData().getImagesDirectory();
		if (imagesDir == null) {
			return;
		}
		Sequence seq = exp.getSeqCamData().getSequence();
		final int tApply = clampT((int) imageIndexSpinner.getValue(), seq.getSizeT());
		final double dx = ((Number) xSpinner.getValue()).doubleValue();
		final double dy = ((Number) ySpinner.getValue()).doubleValue();

		new Thread(() -> {
			try {
				Path originalsDir = Path.of(imagesDir, "original_images");
				Files.createDirectories(originalsDir);
				Path manifest = originalsDir.resolve("backup_manifest.txt");
				try (BufferedWriter out = new BufferedWriter(new FileWriter(manifest.toFile(), true))) {
					applyOneFrameToDisk(exp, tApply, dx, dy, originalsDir, out);
				}
				final IcyBufferedImage reloaded = reloadFrameFromDisk(exp, tApply);
				SwingUtilities.invokeLater(() -> {
					if (reloaded != null) {
						try {
							seq.beginUpdate();
							seq.setImage(tApply, 0, reloaded);
						} finally {
							seq.endUpdate();
						}
					}
					int sizeT = seq.getSizeT();
					int nextT = computeNextFrameIndex(tApply, referenceFrameIndex, sizeT);
					clampSpinnerToSequence(seq);
					syncingFromViewer = true;
					try {
						imageIndexSpinner.setValue(nextT);
					} finally {
						syncingFromViewer = false;
					}
					Viewer v = seq.getFirstViewer();
					if (v != null) {
						syncingFromSpinner = true;
						try {
							v.setPositionT(nextT);
						} finally {
							syncingFromSpinner = false;
						}
					}
					refreshDifferenceView();
				});
			} catch (Exception ex) {
				Logger.error("CorrectDriftPanel: apply transform failed: " + ex.getMessage(), ex);
			}
		}, "ManualDriftApplyOne").start();
	}

	private static int computeNextFrameIndex(int t, Integer tRef, int sizeT) {
		if (sizeT <= 0) {
			return 0;
		}
		if (tRef != null) {
			if (tRef > t) {
				return clampT(t - 1, sizeT);
			}
			if (tRef < t) {
				return clampT(t + 1, sizeT);
			}
			return t;
		}
		return clampT(t + 1, sizeT);
	}

	private static void applyOneFrameToDisk(Experiment exp, int t, double dx, double dy, Path originalsDir,
			BufferedWriter manifestOut) throws Exception {
		String fileName = exp.getSeqCamData().getFileNameFromImageList(t);
		if (fileName == null) {
			return;
		}
		Path src = Path.of(fileName);
		if (!Files.exists(src)) {
			return;
		}
		Path backup = originalsDir.resolve(src.getFileName().toString());
		if (!Files.exists(backup)) {
			Files.copy(src, backup, StandardCopyOption.COPY_ATTRIBUTES);
			manifestOut.write(backup.getFileName().toString());
			manifestOut.newLine();
			manifestOut.flush();
		}
		BufferedImage img = ImageUtil.load(src.toFile(), true);
		if (img == null) {
			return;
		}
		IcyBufferedImage icy = IcyBufferedImage.createFrom(img);
		icy = GaspardRigidRegistration.applyTranslation2D(icy, -1, new Vector2d(dx, dy), true);
		ImageUtil.save(ImageUtil.toRGBImage(icy), "jpg", src.toFile());
	}

	private static IcyBufferedImage reloadFrameFromDisk(Experiment exp, int t) {
		String fileName = exp.getSeqCamData().getFileNameFromImageList(t);
		if (fileName == null) {
			return null;
		}
		BufferedImage bi = ImageUtil.load(new File(fileName), true);
		if (bi == null) {
			return null;
		}
		return IcyBufferedImage.createFrom(bi);
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

		double dx = ((Number) xSpinner.getValue()).doubleValue();
		double dy = ((Number) ySpinner.getValue()).doubleValue();
		int start = (int) rangeStartSpinner.getValue();
		int end = (int) rangeEndSpinner.getValue();

		new Thread(() -> {
			try {
				Path originalsDir = Path.of(imagesDir, "original_images");
				Files.createDirectories(originalsDir);
				Path manifest = originalsDir.resolve("backup_manifest.txt");
				try (BufferedWriter out = new BufferedWriter(new FileWriter(manifest.toFile(), true))) {
					for (int t = start; t <= end; t++) {
						applyOneFrameToDisk(exp, t, dx, dy, originalsDir, out);
					}
				}
			} catch (Exception ex) {
				Logger.error("CorrectDriftPanel: apply range failed: " + ex.getMessage(), ex);
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
			if (syncingFromSpinner) {
				return;
			}
			Viewer v = event.getSource();
			Sequence seq = v.getSequence();
			if (seq == null) {
				return;
			}
			Experiment exp = getCurrentExperiment();
			if (exp == null || exp.getSeqCamData() == null || exp.getSeqCamData().getSequence() == null) {
				return;
			}
			if (seq.getId() != exp.getSeqCamData().getSequence().getId()) {
				return;
			}
			int t = clampT(v.getPositionT(), seq.getSizeT());
			syncingFromViewer = true;
			try {
				clampSpinnerToSequence(seq);
				imageIndexSpinner.setValue(t);
			} finally {
				syncingFromViewer = false;
			}
			if (viewTransformToggle.isSelected()) {
				refreshDifferenceView();
			}
		}
	}

	@Override
	public void viewerClosed(Viewer viewer) {
		viewer.removeListener(this);
	}
}
