package plugins.fmp.multicafe.dlg.levels;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.event.ChangeListener;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.ValueMarker;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import icy.roi.ROI;
import icy.sequence.Sequence;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.cage.Cage;
import plugins.fmp.multitools.experiment.capillary.Capillary;
import plugins.fmp.multitools.experiment.capillary.CapillaryCorrelationGulps;
import plugins.fmp.multitools.service.MeniscusRefinement;
import plugins.fmp.multitools.service.MeniscusRefinementPreview;
import plugins.fmp.multitools.tools.JComponents.RichTooltipLayoutGuard;
import plugins.fmp.multitools.tools.chart.ChartCagesFrame;
import plugins.fmp.multitools.tools.chart.builders.CageCapillarySeriesBuilder;
import plugins.fmp.multitools.tools.chart.style.SeriesStyleCodec;
import plugins.fmp.multitools.tools.polyline.Level2D;
import plugins.fmp.multitools.tools.results.EnumResults;
import plugins.fmp.multitools.tools.results.ResultsOptions;
import plugins.kernel.roi.roi2d.ROI2DPolyLine;

/** Independent phase-1 preview. Manual draft and explicit correlation preview. Only Validate/Undo change saved topraw. */
public final class MeniscusRefinementPanel extends JPanel {
	private static final long serialVersionUID = 1L;
	private final Experiment experiment;
	private final Runnable changed;
	private final JComboBox<Capillary> capillaries = new JComboBox<>();
	private JButton previousButton = new JButton("<");
	private JButton nextButton = new JButton(">");

	private final JSpinner radius = new JSpinner(new SpinnerNumberModel(10, 3, 60, 1));
	private final JSpinner stripRadius = new JSpinner(new SpinnerNumberModel(15, 5, 80, 1));
	private final JSpinner correction = new JSpinner(new SpinnerNumberModel(3., 1., 32., 1.));
	private final JCheckBox showOriginal = new JCheckBox("Saved topraw (orange)", true),
			showProposed = new JCheckBox("Edited topraw (magenta)", true);
	private final JCheckBox expand = new JCheckBox("Expand correction search", true);
	private final JButton runCorrelation = new JButton("Run correlation"), applyCorrelation = new JButton("Apply correlation");
	private final Map<Capillary, MeniscusRefinement.Proposal> correlations = new LinkedHashMap<>();
	private final JButton accept = new JButton("Validate"), undo = new JButton("Undo validation");
	private final JButton nudgeUp = new JButton("-1 px");
	private final JButton nudgeDown = new JButton("+1 px");
	private final JButton graphs = new JButton("Cage graphs...");
	private final JLabel status = new JLabel("Preparing refinement preview...");

	private enum ProfileKind {
		SOURCE("Source"), FINAL("Final");

		private final String label;

		ProfileKind(String label) {
			this.label = label;
		}

		@Override
		public String toString() {
			return label;
		}
	}

	private enum MoveMode {
		RANGE("Range"), RAMP("Ramp"), CURVE("Curve");

		private final String label;

		MoveMode(String label) {
			this.label = label;
		}

		@Override
		public String toString() {
			return label;
		}
	}

	private enum ValidationScope {
		CAPILLARY("Current capillary"), CAGE("Current cage"), EXPERIMENT("All cages of this experiment");

		private final String label;

		ValidationScope(String label) {
			this.label = label;
		}

		@Override
		public String toString() {
			return label;
		}
	}

	private final JComboBox<ValidationScope> validationScope = new JComboBox<>(ValidationScope.values());
	private final JComboBox<MoveMode> moveMode = new JComboBox<>(MoveMode.values());
	private final JComboBox<ProfileKind> profileChoice = new JComboBox<>(ProfileKind.values());
	private final JButton previewAlignment = new JButton("Preview alignment");
	private final JButton backAlignment = new JButton("Back");
	private final JLabel hint = new JLabel();
	private MeniscusRefinement.Validation lastValidation;
	private final List<Capillary> computingCaps = new ArrayList<>();
	private final Map<Capillary, MeniscusRefinement.Proposal> proposals = new LinkedHashMap<>();
	private final Map<Capillary, BufferedImage> aligned = new LinkedHashMap<>();
	private final Map<Capillary, BufferedImage> correlatedStrips = new LinkedHashMap<>();
	private final Map<Capillary, BufferedImage> sources = new LinkedHashMap<>();
	private static final int DRAG_LOCK_PX = 4;
	private int selectFrom = -1, selectTo = -1;
	private int pressX, pressY, pressColumn;
	private boolean dragNudging, dragSelecting, editingStrip;
	private double pendingDelta;
	private static final int BLEND_COLUMNS = 8;

	private static final class StrokePoint {
		int column;
		double offset;

		StrokePoint(int column, double offset) {
			this.column = column;
			this.offset = offset;
		}
	}

	/** One drawn ramp or polyline. The applied shift is final minus source. */
	private static final class Profile {
		final List<StrokePoint> points = new ArrayList<>();
		boolean rampDefined;
		int rampStartColumn = -1, rampEndColumn = -1;
		double rampStartOffset, rampEndOffset;

		void clear() {
			points.clear();
			rampDefined = false;
			rampStartColumn = rampEndColumn = -1;
			rampStartOffset = rampEndOffset = 0;
		}

		boolean drawn(MoveMode mode) {
			return mode == MoveMode.CURVE ? !points.isEmpty() : rampDefined;
		}
	}

	private final Profile sourceProfile = new Profile();
	private final Profile finalProfile = new Profile();
	private double[] frozenLevels;
	private double[] strokeOffset;
	private double[] sourceScreen;
	private double[] finalScreen;
	private BufferedImage frozenStrip;
	private Capillary strokeCapillary;
	private boolean alignmentPreview;
	private boolean shapePopup;
	private int dragPoint = -1;
	private final List<ROI> overlays = new ArrayList<>();
	private Sequence overlaySequence;
	private final RichTooltipLayoutGuard tooltipGuard = new RichTooltipLayoutGuard();
	private boolean closed;
	private boolean suspended;
	private JDialog comparisonDialog;
	private final JPanel comparisonView = new JPanel(new BorderLayout());
	private final javax.swing.Timer previewTimer = new javax.swing.Timer(200, e -> compute());
	private final javax.swing.Timer sourceTimer = new javax.swing.Timer(750, e -> checkSources());

	private void checkSources() {
        if (closed || suspended) return;
        buttons();
        if (!hasKymographs()) {
            cancel();
            clearOverlays();
            MeniscusRefinementPreview.clear(this);
            status.setText("Load this experiment's kymographs and saved measurements first.");
            return;
        }
        if (capillaries.getItemCount() == 0) {
            for (Capillary c : experiment.getCapillaries().getList()) capillaries.addItem(c);
        }
        if (proposals.isEmpty() && cap() != null && cap().getTopRaw().polylineLevel != null
                && cap().getTopRaw().polylineLevel.npoints >= 2) {
			requestPreview();
			return;
		}
		for (Map.Entry<Capillary, MeniscusRefinement.Proposal> entry : proposals.entrySet())
			if (!MeniscusRefinement.isCurrent(entry.getKey(), entry.getValue())) {
				requestPreview();
				break;
			}
	}

	private SwingWorker<MeniscusRefinement.Proposal, Void> worker;

	private final JPanel plot = new JPanel() {
		private static final long serialVersionUID = 1L;
		{
			MouseAdapter mouse = new MouseAdapter() {
				@Override
				public void mousePressed(MouseEvent e) {
					if (suspended || !hasKymographs() || !inEditableStrip(e.getY(), getHeight())) {
						editingStrip = false;
						return;
					}
					editingStrip = true;
					if (moveMode() != MoveMode.RANGE) {
						shapePressed(e);
						return;
					}
					int n = columnCount();
					if (n <= 0)
						return;
					pressX = e.getX();
					pressY = e.getY();
					pressColumn = columnAt(e.getX(), n, getWidth());
					dragNudging = dragSelecting = false;
					pendingDelta = 0;
				}

				@Override
				public void mouseDragged(MouseEvent e) {
					if (!editingStrip)
						return;
					if (moveMode() != MoveMode.RANGE) {
						shapeDragged(e);
						return;
					}
					int n = columnCount();
					if (n <= 0)
						return;
					int dx = e.getX() - pressX, dy = e.getY() - pressY;
					if (!dragNudging && !dragSelecting) {
						if (Math.abs(dx) < DRAG_LOCK_PX && Math.abs(dy) < DRAG_LOCK_PX)
							return;
						boolean inside = selectFrom >= 0 && pressColumn >= selectFrom && pressColumn <= selectTo;
						if (Math.abs(dy) > Math.abs(dx) && (inside || selectFrom < 0)) {
							dragNudging = true;
							if (!inside)
								selectFrom = selectTo = pressColumn;
						} else
							dragSelecting = true;
					}
					if (dragSelecting) {
						int col = columnAt(e.getX(), n, getWidth());
						selectFrom = Math.min(pressColumn, col);
						selectTo = Math.max(pressColumn, col);
						pendingDelta = 0;
					} else if (dragNudging)
						pendingDelta = nudgePixels(dy, getHeight());
					repaint();
				}

				@Override
				public void mouseReleased(MouseEvent e) {
					if (!editingStrip)
						return;
					editingStrip = false;
					if (moveMode() != MoveMode.RANGE) {
						shapeReleased(e);
						return;
					}
					if (dragNudging) {
						double delta = pendingDelta;
						dragNudging = false;
						pendingDelta = 0;
						if (delta != 0)
							commitOffset(delta);
						else
							repaint();
					} else if (dragSelecting) {
						dragSelecting = false;
						repaint();
					} else if (columnCount() > 0) {
						selectFrom = selectTo = columnAt(e.getX(), columnCount(), getWidth());
						repaint();
					}
				}

				@Override
				public void mouseWheelMoved(MouseWheelEvent e) {
					if (suspended || !hasKymographs() || !inEditableStrip(e.getY(), getHeight()) || moveMode() != MoveMode.RANGE)
						return;
					int steps = e.getWheelRotation();
					if (steps == 0)
						return;
					if (selectFrom < 0) {
						status.setText("Select columns on the strip first.");
						return;
					}
					commitOffset(steps);
				}
			};
			addMouseListener(mouse);
			addMouseMotionListener(mouse);
			addMouseWheelListener(mouse);
		}

		@Override
		protected void paintComponent(Graphics graphics) {
			super.paintComponent(graphics);
			Graphics2D g = (Graphics2D) graphics.create();
			Capillary selected = cap();
			if (!hasKymographs()) {
				g.drawString("Load this experiment's kymographs and saved measurements first.", 12, 24);
			} else if (selected == null || !aligned.containsKey(selected)) {
				g.drawString("Preparing stabilized image...", 12, 24);
			} else {
				int width = getWidth();
				int height = getHeight();
				g.setColor(Color.DARK_GRAY);
				String range = selectFrom < 0 ? "" : "  |  columns " + selectFrom + "-" + selectTo;
				g.drawString(selected.getLast2ofCapillaryName() + " (+/- " + stripRadius() + " px)" + range, 10, 16);
				paintReferenceStrip(g, correlatedStrips.get(selected), correlatedTop(height), correlatedBottom(height), width);
				g.setColor(Color.DARK_GRAY);
				g.drawString("Correlated preview" + (correlations.containsKey(selected) ? "" : " (out of date - Run correlation)"), 10, correlatedTop(height) - 4);
				paintEditableStrip(g, selected, width, height);
				g.setColor(Color.DARK_GRAY);
				g.drawString("Topraw - editable", 10, stripTop(height) - 4);
			}
			g.dispose();
		}
	};

	public static void open(Component owner, Experiment experiment, Capillary selected, Runnable changed) {
		if (experiment == null)
			return;
		MeniscusRefinementPanel panel = new MeniscusRefinementPanel(experiment, selected, changed);
		JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(owner), "Phase 1 - refine meniscus from topraw",
				Dialog.ModalityType.MODELESS);
		dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
		dialog.setContentPane(panel);
		dialog.setSize(1100, 700);
		dialog.setLocationRelativeTo(owner);
		dialog.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosed(WindowEvent event) {
				panel.close();
			}
		});
		dialog.setVisible(true);
	}

	public MeniscusRefinementPanel(Experiment experiment, Capillary selected, Runnable changed) {
		super(new BorderLayout(5, 5));
		this.experiment = experiment;
		this.changed = changed;
		validationScope.setSelectedItem(ValidationScope.CAPILLARY);
		for (Capillary cap : experiment.getCapillaries().getList())
			capillaries.addItem(cap);
		capillaries.setRenderer(new DefaultListCellRenderer() {
			private static final long serialVersionUID = 1L;

			@Override
			public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected,
					boolean focus) {
				super.getListCellRendererComponent(list, value, index, selected, focus);
				if (value instanceof Capillary) {
					Capillary cap = (Capillary) value;
					setText(cap.getLast2ofCapillaryName() + (cap.getNFlies() == 0 ? " (no-fly control)" : ""));
				}
				return this;
			}
		});
		if (selected != null)
			capillaries.setSelectedItem(selected);
		JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT));
		int bWidth = 30;
		int height = 20;
		previousButton.setPreferredSize(new Dimension(bWidth, height));
		nextButton.setPreferredSize(new Dimension(bWidth, height));
		previousButton.setMargin(new java.awt.Insets(0, 0, 0, 0));
		nextButton.setMargin(new java.awt.Insets(0, 0, 0, 0));
		controls.add(previousButton);
		controls.add(capillaries);
		controls.add(nextButton);
		controls.add(new JLabel("Window +/- px"));
		controls.add(radius);
		controls.add(new JLabel("Strip +/- px"));
		controls.add(stripRadius);
		controls.add(new JLabel("Correction around topraw shift +/- px"));
		controls.add(correction);
		controls.add(expand);

		JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT));
		actions.add(new JLabel("Apply to"));
		actions.add(validationScope);
		actions.add(new JLabel("Move"));
		actions.add(moveMode);
		actions.add(new JLabel("Profile"));
		profileChoice.setSelectedItem(ProfileKind.FINAL);
		actions.add(profileChoice);
		actions.add(previewAlignment);
		actions.add(backAlignment);
		actions.add(runCorrelation);
		actions.add(applyCorrelation);
		actions.add(accept);
		actions.add(undo);
		actions.add(showOriginal);
		actions.add(showProposed);
		actions.add(nudgeUp);
		actions.add(nudgeDown);
		actions.add(graphs);

		JPanel header = new JPanel(new GridLayout(0, 1));
		header.add(controls);
		header.add(actions);
		header.add(hint);
		updateHint();

		add(header, BorderLayout.NORTH);
		add(plot, BorderLayout.CENTER);
		add(status, BorderLayout.SOUTH);
		defineToolTips();
		defineActionListeners();

		ChangeListener reset = e -> invalidateCorrelation();
		radius.addChangeListener(reset);
		correction.addChangeListener(reset);
		stripRadius.addChangeListener(e -> resampleStripRadius());
		previewTimer.setRepeats(false);
		buttons();
		sourceTimer.start();
		requestPreview();
	}

	private void defineToolTips() {
		stripRadius.setToolTipText("Rows shown above and below each meniscus. Does not rerun correlation.");
		moveMode.setToolTipText(
				"Range shifts a block. Ramp and Curve draw a path on the strip and leave the picture in place until Preview alignment.");
		profileChoice.setToolTipText(
				"Source (orange) is the edge to move. Final (blue) is where it should land. The shift is final minus source.");
		previewAlignment.setToolTipText(
				"Shift the strip by the gap between the orange source and the blue final curve. The draft is not changed.");
		backAlignment.setToolTipText("Restore the strip picture and keep both curves.");
		nudgeUp.setToolTipText("Move the selected columns up in the image.");
		nudgeDown.setToolTipText("Move the selected columns down in the image.");
		undo.setToolTipText(lastValidation == null ? "Undo the last validation"
				: "Restore the " + lastValidation.size() + " capillaries changed by the last validation");
	}

	private void defineActionListeners() {
		nudgeUp.addActionListener(e -> nudgeSelection(-1));
		nudgeDown.addActionListener(e -> nudgeSelection(1));
		graphs.addActionListener(e -> showGraphs());
		showOriginal.addActionListener(e -> updateOverlays());
		showProposed.addActionListener(e -> updateOverlays());
		runCorrelation.addActionListener(e -> runCorrelation());
		applyCorrelation.addActionListener(e -> applyCorrelation());
		accept.addActionListener(e -> apply(false));
		undo.addActionListener(e -> apply(true));
		previewAlignment.addActionListener(e -> showAlignmentPreview());
		backAlignment.addActionListener(e -> restoreFrozenStrip());
		moveMode.addActionListener(e -> {
			commitStroke();
			clearSelection();
			clearStroke();
			updateHint();
			updateOverlays();
			buttons();
			plot.repaint();
		});
		profileChoice.addActionListener(e -> {
			dragPoint = -1;
			updateHint();
			buttons();
			plot.repaint();
		});
		capillaries.addActionListener(e -> {
			commitStroke();
			clearSelection();
			clearStroke();
			updateOverlays();
			List<Capillary> targets = scope();
			boolean ready = !targets.isEmpty()
					&& targets.stream().allMatch(c -> MeniscusRefinement.isCurrent(c, proposals.get(c)));
			boolean coveredByWorker = worker != null && computingCaps.containsAll(targets);
			if (!ready && !coveredByWorker)
				requestPreview();

			buttons();
			if (ready)
				readyStatus();
			plot.repaint();
			refreshComparison();
			Sequence seq = experiment.getSeqKymos() == null ? null : experiment.getSeqKymos().getSequence();
			if (seq != null && seq.getFirstViewer() != null && cap() != null)
				seq.getFirstViewer().setPositionT(cap().getKymographIndex());
		});
		previousButton.addActionListener(e -> capillaries.setSelectedIndex(capillaries.getSelectedIndex() - 1));
		nextButton.addActionListener(e -> capillaries.setSelectedIndex(capillaries.getSelectedIndex() + 1));
		expand.addActionListener(e -> invalidateCorrelation());
		validationScope.addActionListener(e -> requestPreview());
	}

	private Capillary cap() {
		return (Capillary) capillaries.getSelectedItem();
	}

	private void cancel() {
		if (worker != null)
			worker.cancel(true);
		worker = null;
		computingCaps.clear();
	}

	private List<Capillary> scope() {
		if (validationScope.getSelectedItem() == ValidationScope.EXPERIMENT)
			return new ArrayList<>(experiment.getCapillaries().getList());
		return visibleCaps();
	}

	/** Keep inspection readable even while preparing all cages for validation. */
	private List<Capillary> visibleCaps() {
		List<Capillary> caps = new ArrayList<>();
		Capillary selected = cap();
		if (selected != null) {
			if (validationScope.getSelectedItem() != ValidationScope.CAPILLARY && selected.getCageID() >= 0) {
				for (Capillary cap : experiment.getCapillaries().getList())
					if (cap.getCageID() == selected.getCageID())
						caps.add(cap);
			} else
				caps.add(selected);
		}
		return caps;
	}

	private void buttons() {
		List<Capillary> caps = scope();
		boolean busy = suspended || !hasKymographs() || worker != null || previewTimer.isRunning();
		runCorrelation.setEnabled(!closed && !busy && !caps.isEmpty() && caps.stream().allMatch(c -> MeniscusRefinement.isCurrent(c, proposals.get(c))));
		applyCorrelation.setEnabled(!closed && !busy && !caps.isEmpty() && caps.stream().allMatch(c -> correlations.containsKey(c) && MeniscusRefinement.isCurrent(c, proposals.get(c))));
		accept.setEnabled(!closed && !busy && !caps.isEmpty()
				&& caps.stream().allMatch(cap -> MeniscusRefinement.isCurrent(cap, proposals.get(cap))));
		undo.setEnabled(!closed && !busy && MeniscusRefinement.canUndo(lastValidation));
		nudgeUp.setEnabled(!closed && !busy);
		nudgeDown.setEnabled(!closed && !busy);
		defineToolTips();
		previousButton.setEnabled(capillaries.getSelectedIndex() > 0);
		nextButton.setEnabled(capillaries.getSelectedIndex() + 1 < capillaries.getItemCount());
		previewAlignment.setEnabled(!closed && !busy && hasStroke() && !alignmentPreview);
		backAlignment.setEnabled(!closed && !busy && alignmentPreview);
		profileChoice.setEnabled(!closed && moveMode() != MoveMode.RANGE);
	}

	private void readyStatus() {
		StringBuilder summary = new StringBuilder(
				proposals.size() + " capillaries ready (" + validationScope.getSelectedItem() + "). ");
		for (Capillary cap : visibleCaps()) {
			MeniscusRefinement.Proposal p = proposals.get(cap);
			if (p == null)
				continue;
			int good = 0;
			boolean[] flags = p.quality();
			for (int i = 1; i < flags.length; i++)
				if (flags[i])
					good++;
			summary.append(cap.getLast2ofCapillaryName()).append(": ").append(good).append("/").append(flags.length - 1)
					.append(" reliable; ");
		}
		status.setText(summary + "Editable topraw; Validate saves the selected scope. Run correlation explicitly to refresh its preview.");
	}

	private void reset() {
		previewTimer.stop();
		cancel();
		clearStroke();

		proposals.clear();
		{
			aligned.clear();
			correlatedStrips.clear();
			sources.clear();
		}
		clearSelection();
		clearOverlays();
		MeniscusRefinementPreview.clear(this);
		ChartCagesFrame.refreshRefinement(experiment);
		refreshComparison();
		buttons();
		plot.repaint();
		status.setText("Preparing refinement preview...");
	}

	private void requestPreview() {
		restartPreview();
	}

	private void restartPreview() {
		if (closed || suspended)
			return;
		cancel();
		previewTimer.restart();
		buttons();
	}

    private boolean hasKymographs() {
        return experiment.getSeqKymos() != null && experiment.getSeqKymos().getSequence() != null
                && experiment.getSeqKymos().getSequence().getSizeT() > 0;
    }

    /** Keep the draft and validation history while another experiment is displayed. */
    void suspendSession() {
        previewTimer.stop();
        cancel();
        commitStroke();
        clearStroke();
        suspended = true;
        sourceTimer.stop();
        clearOverlays();
        MeniscusRefinementPreview.clear(this);
        ChartCagesFrame.refreshRefinement(experiment);
        if (comparisonDialog != null) comparisonDialog.setVisible(false);
        buttons();
    }

    void resumeSession() {
        if (closed) return;
        suspended = false;
        sourceTimer.start();
        if (hasKymographs()) {
            compute();
        } else {
            buttons();
            status.setText("Load this experiment's kymographs and saved measurements first.");
        }
    }

	public void close() {
		if (closed)
			return;
		closed = true;
		sourceTimer.stop();
		reset();
		tooltipGuard.close();
		if (comparisonDialog != null)
			comparisonDialog.dispose();
	}

    private void compute() {
        if (closed || suspended || cap() == null) return;
        try {
            if (experiment.getSeqKymos() == null || experiment.getSeqKymos().getSequence() == null)
                throw new IllegalStateException("Open the kymographs first.");
            for (Capillary c : scope()) {
                if (MeniscusRefinement.isCurrent(c, proposals.get(c))) continue;
                Level2D raw = c.getTopRaw().polylineLevel;
                if (raw == null || raw.npoints < 2) throw new IllegalStateException("Detect topraw first.");
                BufferedImage source = MeniscusRefinement.image(experiment.getSeqKymos().getSequence(), c, raw.npoints);
                sources.put(c, source);
                proposals.put(c, MeniscusRefinement.editable(raw, source, c.getTopRaw().getRefinementQuality()));
                correlations.remove(c);
                correlatedStrips.remove(c);
            }

            rebuildStrips();
            publishEdits();
            readyStatus();
        } catch (RuntimeException error) { status.setText(message(error)); }
        buttons();
    }

    private void invalidateCorrelation() {
        cancel();
        correlations.clear();
        buttons();
        plot.repaint();
    }

    private void publishEdits() {
        MeniscusRefinementPreview.publish(this, proposals);
        updateOverlays();
        ChartCagesFrame.refreshRefinement(experiment);
        refreshComparison();
    }

    private void runCorrelation() {
        commitStroke();
        if (worker != null) return;
        final Map<Capillary, Level2D> inputs = new LinkedHashMap<>();
        for (Capillary c : scope()) {
            MeniscusRefinement.Proposal p = proposals.get(c);
            if (!MeniscusRefinement.isCurrent(c, p)) return;
            inputs.put(c, new Level2D(p.x(), p.refined(), p.columnCount()));
        }
        final int window = ((Number) radius.getValue()).intValue();
        final double range = ((Number) correction.getValue()).doubleValue();
        final boolean adaptive = expand.isSelected();
        final Map<Capillary, BufferedImage> images = new LinkedHashMap<>(sources);
        computingCaps.addAll(inputs.keySet());
        worker = new SwingWorker<MeniscusRefinement.Proposal, Void>() {
            private final Map<Capillary, MeniscusRefinement.Proposal> results = new LinkedHashMap<>();
            protected MeniscusRefinement.Proposal doInBackground() {
                for (Capillary c : inputs.keySet()) {
                    if (isCancelled()) return null;
                    results.put(c, MeniscusRefinement.propose(inputs.get(c), images.get(c), window, range, adaptive));
                }
                return null;
            }
            protected void done() {
                if (worker != this || closed) return;
                worker = null;
                computingCaps.clear();
                try {
                    get();
                    for (Capillary c : results.keySet()) {
                        MeniscusRefinement.Proposal p = proposals.get(c);
                        if (!MeniscusRefinement.isCurrent(c, p) || !Arrays.equals(p.refined(), inputs.get(c).ypoints))
                            throw new IllegalStateException("Topraw changed; run correlation again.");
                    }
                    correlations.putAll(results);
                    for (Capillary c : results.keySet())
                        correlatedStrips.put(c, MeniscusRefinement.stabilized(images.get(c), results.get(c).refined(), stripRadius()));
                    status.setText("Correlation ready. Apply correlation transfers the preview into editable topraw.");
                } catch (Exception error) { status.setText(message(error)); }
                buttons();
                plot.repaint();
            }
        };
        worker.execute();
        status.setText("Computing correlation...");
        buttons();
    }

    private void applyCorrelation() {
        List<Capillary> targets = scope();
        if (targets.isEmpty() || !targets.stream().allMatch(c -> correlations.containsKey(c)
                && MeniscusRefinement.isCurrent(c, proposals.get(c)))) return;
        clearStroke();
        for (Capillary c : targets) {
            MeniscusRefinement.Proposal p = proposals.get(c);
            p.replaceLevels(correlations.get(c).refined(), correlations.get(c).quality());
        }
        invalidateCorrelation();
        rebuildStrips();
        publishEdits();
        readyStatus();
    }

	private void clearOverlays() {
		if (overlaySequence != null && !overlays.isEmpty())
			overlaySequence.removeROIs(overlays, false);
		overlays.clear();
		overlaySequence = null;
	}

	private void updateOverlays() {
		clearOverlays();
		if (experiment.getSeqKymos() == null)
			return;
		overlaySequence = experiment.getSeqKymos().getSequence();
		if (overlaySequence == null)
			return;
		Capillary selected = cap();
		for (Map.Entry<Capillary, MeniscusRefinement.Proposal> entry : proposals.entrySet()) {
			Capillary cap = entry.getKey();
			if (cap != selected)
				continue;
			MeniscusRefinement.Proposal p = entry.getValue();
			if (!MeniscusRefinement.isCurrent(cap, p))
				continue;
			if (showOriginal.isSelected())
				addOverlay(cap, p.x(), p.original(), Color.ORANGE, "original");
			if (showProposed.isSelected())
				addOverlay(cap, p.x(), p.refined(), Color.MAGENTA, "proposed");
			if (strokeOffset != null && frozenLevels != null && hasStroke())
				addOverlay(cap, p.x(), draftedLevels(p), Color.RED, "curve");
		}
		overlaySequence.addROIs(overlays, false);
	}

	private void addOverlay(Capillary cap, double[] x, double[] y, Color color, String label) {
		ROI2DPolyLine roi = new ROI2DPolyLine(new Level2D(x, y, y.length));
		roi.setName(cap.getKymographPrefix() + "_xcorr_refinement_" + label);
		roi.setT(cap.getKymographIndex());
		roi.setColor(color);
		roi.setStroke(1.5);
		roi.setReadOnly(true);
		CapillaryCorrelationGulps.markOverlay(roi);
		roi.setProperty(MeniscusRefinementPreview.ROI_PROPERTY, "true");
		overlays.add(roi);
	}

	private void showGraphs() {
		if (comparisonDialog == null || !comparisonDialog.isDisplayable()) {
			comparisonDialog = new JDialog(SwingUtilities.getWindowAncestor(this), "Meniscus refinement comparison",
					Dialog.ModalityType.MODELESS);
			comparisonDialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
			comparisonDialog.setContentPane(comparisonView);
			comparisonDialog.setSize(1000, 760);
			comparisonDialog.setLocationRelativeTo(this);
		}
		fillComparison();
		comparisonDialog.setVisible(true);
		comparisonDialog.toFront();
	}

	private void refreshComparison() {
		if (comparisonDialog == null || !comparisonDialog.isVisible())
			return;
		fillComparison();
	}

	private void fillComparison() {
		Capillary selected = cap();
		comparisonDialog
				.setTitle("Meniscus comparison - " + (selected == null ? "" : selected.getLast2ofCapillaryName()));
		comparisonView.removeAll();
		comparisonView.add(comparisonContent(), BorderLayout.CENTER);
		comparisonView.revalidate();
		comparisonView.repaint();
	}

	/**
	 * The lower plot exposes subpixel corrections hidden by the full volume scale.
	 */
	private JPanel comparisonContent() {
		JPanel content = new JPanel(new BorderLayout(5, 5));
		if (proposals.isEmpty()) {
			content.add(
					new JLabel(aligned.isEmpty() ? status.getText()
							: "Refinement accepted into topraw. Comparison previews have been cleared."),
					BorderLayout.NORTH);
			return content;
		}
		Capillary selected = cap();
		if (selected == null) {
			content.add(new JLabel("Select a capillary."), BorderLayout.NORTH);
			return content;
		}
		Cage cage = new Cage();
		cage.getProperties().setCageID(selected.getCageID());
		cage.addCapillaryIfUnique(selected);
		ResultsOptions options = new ResultsOptions();
		options.resultType = EnumResults.TOPRAW;
		options.buildExcelStepMs = (int) experiment.getKymoBin_ms();
		XYSeriesCollection levels = new CageCapillarySeriesBuilder().build(experiment, cage, options);
		for (int i = levels.getSeriesCount() - 1; i >= 0; i--) {
			String key = levels.getSeriesKey(i).toString();
			if (key.endsWith("_evaporation"))
				levels.removeSeries(i);
			else if (key.endsWith("proposed (preview)"))
				levels.getSeries(i).setKey(key.replace("proposed (preview)", "proposed"));
			else if (key.equals(plugins.fmp.multitools.tools.chart.builders.CapillaryChartSeriesKeys.key(selected)))
				levels.getSeries(i).setKey(selected.getLast2ofCapillaryName() + " original");
		}
		JFreeChart levelChart = ChartFactory.createXYLineChart(
				selected.getLast2ofCapillaryName() + " - original and proposed levels", "Elapsed time (minutes)",
				"Volume (ul)", levels);
		XYLineAndShapeRenderer levelRenderer = (XYLineAndShapeRenderer) levelChart.getXYPlot().getRenderer();
		SeriesStyleCodec.applySeriesPaintsFromDescription(levels, levelRenderer);
		for (int i = 0; i < levels.getSeriesCount(); i++) {
			boolean proposed = levels.getSeriesKey(i).toString().endsWith(" proposed");
			levelRenderer.setSeriesStroke(i, proposed
					? new BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[] { 6, 4 }, 0)
					: new BasicStroke(1f));
		}
		XYSeriesCollection corrections = new XYSeriesCollection();
		double maximum = 0;
		StringBuilder summary = new StringBuilder("Maximum absolute correction: ");
		List<Color> colors = new ArrayList<>();
		MeniscusRefinement.Proposal shown = proposals.get(selected);
		if (shown != null && MeniscusRefinement.isCurrent(selected, shown)) {
			double[] original = shown.original(), refined = shown.refined();
			XYSeries series = new XYSeries(selected.getLast2ofCapillaryName() + " correction", false);
			double capMaximum = 0;
			for (int i = 0; i < original.length; i++) {
				double correction = refined[i] - original[i];
				series.add(CageCapillarySeriesBuilder.displayTimeMinutes(experiment, i, experiment.getKymoBin_ms()),
						correction);
				if (Double.isFinite(correction))
					capMaximum = Math.max(capMaximum, Math.abs(correction));
			}
			maximum = Math.max(maximum, capMaximum);
			summary.append(selected.getLast2ofCapillaryName()).append(" ")
					.append(String.format(java.util.Locale.ROOT, "%.2f px; ", capMaximum));
			corrections.addSeries(series);
			String side = selected.getCapillarySide();
			colors.add("L".equals(side) || "1".equals(side) ? Color.MAGENTA : new Color(210, 120, 0));
		}
		JFreeChart deltaChart = ChartFactory.createXYLineChart("Correction = proposed minus original (pixels)",
				"Elapsed time (minutes)", "Correction (pixels)", corrections);
		XYLineAndShapeRenderer deltaRenderer = (XYLineAndShapeRenderer) deltaChart.getXYPlot().getRenderer();
		for (int i = 0; i < colors.size(); i++) {
			deltaRenderer.setSeriesPaint(i, colors.get(i));
			deltaRenderer.setSeriesStroke(i, new BasicStroke(1.5f));
		}
		double limit = Math.max(.5, maximum * 1.15);
		deltaChart.getXYPlot().getRangeAxis().setRange(-limit, limit);
		deltaChart.getXYPlot().addRangeMarker(new ValueMarker(0, Color.GRAY, new BasicStroke(1f)));
		JPanel charts = new JPanel(new GridLayout(2, 1, 5, 5));
		charts.add(new ChartPanel(levelChart));
		charts.add(new ChartPanel(deltaChart));
		content.add(new JLabel(summary.toString()), BorderLayout.NORTH);
		content.add(charts, BorderLayout.CENTER);
		content.add(new JLabel(
				"Positive correction: proposed meniscus is lower in the image. Drag a region to zoom; right-click for zoom options."),
				BorderLayout.SOUTH);
		return content;
	}

	private void apply(boolean undoing) {
		try {
			if (!undoing)
				commitStroke();
			int count;
			if (undoing) {
				count = lastValidation == null ? 0 : lastValidation.size();
				MeniscusRefinement.undo(lastValidation);
				lastValidation = null;
			} else {
				Map<Capillary, MeniscusRefinement.Proposal> batch = new LinkedHashMap<>();
				for (Capillary cap : scope())
					batch.put(cap, proposals.get(cap));
				lastValidation = MeniscusRefinement.validateAll(batch);
				count = lastValidation.size();
			}
			if (cap() != null)
				experiment.getSeqKymos().replaceCapillaryMeasureRoisAtT(cap().getKymographIndex(),
						experiment.getCapillaries());
			if (changed != null)
				changed.run();
            invalidateCorrelation();
            clearStroke();
            compute();
            status.setText((undoing ? "Restored previous topraw for " : "Validated edited topraw for ") + count
                    + " capillaries. Use Levels Save to persist. Correlation runs only on request.");
		} catch (RuntimeException error) {
			status.setText(message(error));
			buttons();
		}
	}

	private static String message(Throwable error) {
		while (error.getCause() != null)
			error = error.getCause();
		return error.getMessage() == null ? error.toString() : error.getMessage();
	}

	private void clearSelection() {
		selectFrom = selectTo = -1;
		dragNudging = dragSelecting = false;
		pendingDelta = 0;
	}

	private int columnCount() {
		MeniscusRefinement.Proposal p = proposals.get(cap());
		return p == null ? 0 : p.columnCount();
	}

	private static final int STRIP_HEADER = 24;
	private static final int STRIP_LABEL = 16;
	private static final int STRIP_GAP = 6;
	private static final int STRIP_MARGIN = 8;

	private static int bandHeight(int panelHeight) {
		int available = panelHeight - STRIP_HEADER - STRIP_MARGIN - 2 * STRIP_LABEL - STRIP_GAP;
		return Math.max(1, available / 2);
	}

	private static int toprawTop(int panelHeight) {
		return STRIP_HEADER + STRIP_LABEL;
	}

	private static int toprawBottom(int panelHeight) {
		return toprawTop(panelHeight) + bandHeight(panelHeight);
	}

	/** Top of the read-only correlated strip. */
	private static int correlatedTop(int panelHeight) {
		return toprawBottom(panelHeight) + STRIP_GAP + STRIP_LABEL;
	}

	private static int correlatedBottom(int panelHeight) {
		return correlatedTop(panelHeight) + bandHeight(panelHeight);
	}

	private static int stripTop(int panelHeight) { return toprawTop(panelHeight); }
	private static int stripBottom(int panelHeight) {
		return toprawBottom(panelHeight);
	}

	private static boolean inEditableStrip(int y, int panelHeight) {
		return y >= stripTop(panelHeight) && y < stripBottom(panelHeight);
	}

	private void paintReferenceStrip(Graphics2D g, BufferedImage strip, int y1, int y2, int width) {
		if (strip == null || y2 <= y1)
			return;
		g.drawImage(strip, 10, y1, width - 20, y2 - y1, null);
		paintMidline(g, y1, y2, width);
	}

	private void paintEditableStrip(Graphics2D g, Capillary selected, int width, int height) {
		int y1 = stripTop(height), y2 = stripBottom(height);
		if (y2 <= y1)
			return;
		BufferedImage strip = aligned.get(selected);
		MeniscusRefinement.Proposal p = proposals.get(selected);
		boolean[] quality = p == null ? selected.getTopRaw().getRefinementQuality() : p.quality();
		int n = quality == null ? 0 : quality.length;
		g.drawImage(strip, 10, y1, width - 20, y2 - y1, null);
		int imageShift = 0;
		if (pendingDelta != 0 && selectFrom >= 0 && n > 0) {
			imageShift = -(int) Math.round(pendingDelta * (y2 - y1) / (2.0 * stripRadius() + 1));
			int left = columnEdge(selectFrom, n, width, true);
			int right = columnEdge(selectTo, n, width, false);
			int bandWidth = Math.max(1, right - left);
			java.awt.Shape previousClip = g.getClip();
			g.setClip(left, y1, bandWidth, y2 - y1);
			g.setColor(plot.getBackground());
			g.fillRect(left, y1, bandWidth, y2 - y1);
			g.drawImage(strip, 10, y1 + imageShift, width - 20, y2 - y1, null);
			g.setClip(previousClip);
		}
		if (moveMode() == MoveMode.RANGE && selectFrom >= 0 && n > 0) {
			int left = columnEdge(selectFrom, n, width, true);
			int right = columnEdge(selectTo, n, width, false);
			g.setColor(new Color(255, 220, 0, 70));
			g.fillRect(left, y1, Math.max(1, right - left), y2 - y1);
		}
		paintMidline(g, y1, y2, width);
		if (quality != null) {
			g.setColor(Color.RED);
			for (int i = 1; i < quality.length; i++)
				if (!quality[i]) {
					int x = 10 + (int) ((i + .5) * (width - 20.) / quality.length);
					int tickShift = i >= selectFrom && i <= selectTo ? imageShift : 0;
					g.drawLine(x, y1 + tickShift, x, y1 + tickShift + 9);
				}
		}
		paintStroke(g, width, height);
	}

	private static void paintMidline(Graphics2D g, int y1, int y2, int width) {
		g.setColor(Color.WHITE);
		g.setStroke(new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[] { 6, 6 }, 0));
		int mid = (y1 + y2) / 2;
		g.drawLine(10, mid, width - 10, mid);
		g.setStroke(new BasicStroke(1));
	}

	private static int columnAt(int mouseX, int n, int width) {
		if (n <= 0)
			return 0;
		double span = Math.max(1, width - 20.);
		int col = (int) Math.floor((mouseX - 10.) / span * n);
		if (col < 0)
			return 0;
		if (col >= n)
			return n - 1;
		return col;
	}

	private static int columnEdge(int index, int n, int width, boolean left) {
		double span = Math.max(1, width - 20.);
		double t = (left ? index : index + 1) / (double) n;
		return 10 + (int) Math.round(t * span);
	}

	private double nudgePixels(int dy, int panelHeight) {
		int span = stripBottom(panelHeight) - stripTop(panelHeight);
		if (span <= 0)
			return 0;
		// Screen y grows downward, so an upward drag has a negative dy.
		// Negate it: the selection then moves up the strip with the pointer.
		return -dy * (2.0 * stripRadius() + 1) / span;
	}

	private int stripRadius() {
		return ((Number) stripRadius.getValue()).intValue();
	}

	/** Resample the current proposals. Does not rerun correlation. */
	private void rebuildStrips() {
		int radius = stripRadius();
		for (Map.Entry<Capillary, BufferedImage> entry : sources.entrySet()) {
			BufferedImage source = entry.getValue();
			int width = source.getWidth();
			double[] levels = levelsForStrip(entry.getKey(), width);
			if (levels != null)
				aligned.put(entry.getKey(), MeniscusRefinement.stabilized(source, levels, radius));
			MeniscusRefinement.Proposal correlated = correlations.get(entry.getKey());
			double[] raw = correlated == null ? null : correlated.refined();
			if (raw != null)
				correlatedStrips.put(entry.getKey(), MeniscusRefinement.stabilized(source, raw, radius));
		}
		plot.repaint();
	}

	private double[] levelsForStrip(Capillary cap, int width) {
		MeniscusRefinement.Proposal p = proposals.get(cap);
		if (p != null && MeniscusRefinement.isCurrent(cap, p)) {
			double[] refined = p.refined();
			if (refined.length == width)
				return refined;
		}
		return null;
	}

	private void nudgeSelection(double delta) {
		if (moveMode() != MoveMode.RANGE) {
			status.setText("Switch Move to Range to shift a block of columns.");
			return;
		}
		if (selectFrom < 0) {
			status.setText("Select columns on the strip first.");
			return;
		}
		commitOffset(delta);
	}

	private void commitOffset(double delta) {
		Capillary selected = cap();
		MeniscusRefinement.Proposal p = selected == null ? null : proposals.get(selected);
		if (p == null || selectFrom < 0 || delta == 0 || !Double.isFinite(delta))
			return;
		if (!MeniscusRefinement.isCurrent(selected, p))
			return;
		invalidateCorrelation();
		p.addOffset(selectFrom, selectTo, delta);
		BufferedImage source = sources.get(selected);
		if (source != null)
			aligned.put(selected, MeniscusRefinement.stabilized(source, p.refined(), stripRadius()));

		MeniscusRefinementPreview.publish(this, proposals);
		updateOverlays();
		ChartCagesFrame.refreshRefinement(experiment);
		refreshComparison();
		readyStatus();
		plot.repaint();
	}

	private MoveMode moveMode() {
		return (MoveMode) moveMode.getSelectedItem();
	}

	private void updateHint() {
		if (moveMode() == MoveMode.RAMP)
			hint.setText(
					"Source (orange) is the edge to move. Final (blue) is where it should land. Drag the selected profile. The shift is final minus source.");
		else if (moveMode() == MoveMode.CURVE)
			hint.setText(
					"Click points on the selected profile. Source is orange, Final is blue. Right-click removes a point. The shift is final minus source.");
		else
			hint.setText(
					"One capillary. Drag across the strip to select columns, then drag vertically, scroll, or use -1 px / +1 px. Positive is lower in the image.");
	}

	private Profile activeProfile() {
		return profileChoice.getSelectedItem() == ProfileKind.SOURCE ? sourceProfile : finalProfile;
	}

	private void shapePressed(MouseEvent e) {
		if (alignmentPreview) {
			status.setText("Back returns to the drawn path.");
			return;
		}
		int n = columnCount();
		if (n <= 0)
			return;
		if (e.isPopupTrigger() || SwingUtilities.isRightMouseButton(e)) {
			shapePopup = true;
			if (moveMode() == MoveMode.CURVE)
				removeCurvePoint(e);
			return;
		}
		shapePopup = false;
		if (!SwingUtilities.isLeftMouseButton(e))
			return;
		if (!ensureFrozen())
			return;
		int column = columnAt(e.getX(), n, e.getComponent().getWidth());
		double offset = clampOffset(screenToOffset(e.getY(), e.getComponent().getHeight()));
		Profile profile = activeProfile();
		if (moveMode() == MoveMode.RAMP) {
			profile.rampDefined = true;
			profile.rampStartColumn = profile.rampEndColumn = column;
			profile.rampStartOffset = profile.rampEndOffset = offset;
			dragPoint = -1;
		} else {
			int hit = curvePointAt(e.getX(), e.getY(), e.getComponent().getWidth(), e.getComponent().getHeight());
			if (hit >= 0)
				dragPoint = hit;
			else {
				profile.points.add(new StrokePoint(column, offset));
				dragPoint = profile.points.size() - 1;
			}
		}
		rebuildStroke();
	}

	private void shapeDragged(MouseEvent e) {
		if (alignmentPreview || shapePopup || frozenLevels == null)
			return;
		int n = columnCount();
		if (n <= 0)
			return;
		int column = columnAt(e.getX(), n, e.getComponent().getWidth());
		double offset = clampOffset(screenToOffset(e.getY(), e.getComponent().getHeight()));
		Profile profile = activeProfile();
		if (moveMode() == MoveMode.RAMP && profile.rampDefined) {
			profile.rampEndColumn = column;
			profile.rampEndOffset = offset;
			rebuildStroke();
		} else if (moveMode() == MoveMode.CURVE && dragPoint >= 0 && dragPoint < profile.points.size()) {
			StrokePoint point = profile.points.get(dragPoint);
			point.column = column;
			point.offset = offset;
			rebuildStroke();
		}
	}

	private void shapeReleased(MouseEvent e) {
		if (e.isPopupTrigger() && !shapePopup && moveMode() == MoveMode.CURVE)
			removeCurvePoint(e);
		else if (!shapePopup && moveMode() == MoveMode.CURVE && dragPoint >= 0)
			dropDuplicateCurvePoint(dragPoint);
		shapePopup = false;
		dragPoint = -1;
		if (frozenLevels != null)
			rebuildStroke();
		else
			plot.repaint();
	}

	private void removeCurvePoint(MouseEvent e) {
		int hit = curvePointAt(e.getX(), e.getY(), e.getComponent().getWidth(), e.getComponent().getHeight());
		if (hit < 0)
			return;
		activeProfile().points.remove(hit);
		dragPoint = -1;
		rebuildStroke();
	}

	private void dropDuplicateCurvePoint(int index) {
		List<StrokePoint> points = activeProfile().points;
		if (index < 0 || index >= points.size())
			return;
		int column = points.get(index).column;
		for (int i = points.size() - 1; i >= 0; i--)
			if (i != index && points.get(i).column == column)
				points.remove(i);
	}

	private boolean ensureFrozen() {
		if (frozenLevels != null)
			return true;
		Capillary selected = cap();
		MeniscusRefinement.Proposal p = selected == null ? null : proposals.get(selected);
		if (p == null || !MeniscusRefinement.isCurrent(selected, p))
			return false;
		frozenLevels = p.refined();
		frozenStrip = aligned.get(selected);
		strokeCapillary = selected;
		alignmentPreview = false;
		return true;
	}

	private void clearStroke() {
		if (alignmentPreview && strokeCapillary != null && frozenStrip != null)
			aligned.put(strokeCapillary, frozenStrip);
		frozenLevels = null;
		strokeOffset = null;
		sourceScreen = null;
		finalScreen = null;
		frozenStrip = null;
		strokeCapillary = null;
		alignmentPreview = false;
		sourceProfile.clear();
		finalProfile.clear();
		shapePopup = false;
		dragPoint = -1;
	}

	private boolean hasStroke() {
		return frozenLevels != null && (finalProfile.rampDefined || !finalProfile.points.isEmpty());
	}

	private void rebuildStroke() {
		invalidateCorrelation();
		int n = frozenLevels == null ? 0 : frozenLevels.length;
		if (n <= 0) {
			strokeOffset = null;
			sourceScreen = null;
			finalScreen = null;
			buttons();
			plot.repaint();
			return;
		}
		sourceScreen = rasterProfile(sourceProfile, n);
		finalScreen = rasterProfile(finalProfile, n);
		double[] delta = new double[n];
		Arrays.fill(delta, Double.NaN);
		for (int i = 0; i < n; i++) {
			if (!Double.isFinite(finalScreen[i]))
				continue;
			double source = Double.isFinite(sourceScreen[i]) ? sourceScreen[i] : 0;
			delta[i] = finalScreen[i] - source;
		}
		strokeOffset = delta;
		updateOverlays();
		buttons();
		plot.repaint();
	}

	private double[] rasterProfile(Profile profile, int n) {
		double[] delta = new double[n];
		Arrays.fill(delta, Double.NaN);
		if (moveMode() == MoveMode.RAMP && profile.rampDefined)
			fillRamp(delta, profile);
		else if (moveMode() == MoveMode.CURVE)
			fillCurve(delta, profile.points);
		return delta;
	}

	private void fillRamp(double[] delta, Profile profile) {
		int c0 = clampColumn(profile.rampStartColumn, delta.length);
		int c1 = clampColumn(profile.rampEndColumn, delta.length);
		int lo = Math.min(c0, c1), hi = Math.max(c0, c1);
		for (int i = lo; i <= hi; i++) {
			double t = c1 == c0 ? 0 : (i - c0) / (double) (c1 - c0);
			delta[i] = profile.rampStartOffset + t * (profile.rampEndOffset - profile.rampStartOffset);
		}
		blendEnds(delta, lo, hi);
	}

	private void fillCurve(double[] delta, List<StrokePoint> curvePoints) {
		if (curvePoints.isEmpty())
			return;
		List<StrokePoint> ordered = new ArrayList<>();
		for (StrokePoint point : curvePoints) {
			int column = clampColumn(point.column, delta.length);
			boolean replaced = false;
			for (StrokePoint kept : ordered)
				if (kept.column == column) {
					kept.offset = point.offset;
					replaced = true;
					break;
				}
			if (!replaced)
				ordered.add(new StrokePoint(column, point.offset));
		}
		ordered.sort((a, b) -> Integer.compare(a.column, b.column));
		if (ordered.size() == 1)
			delta[ordered.get(0).column] = ordered.get(0).offset;
		for (int p = 0; p < ordered.size() - 1; p++) {
			int a = ordered.get(p).column, b = ordered.get(p + 1).column;
			double start = ordered.get(p).offset, end = ordered.get(p + 1).offset;
			for (int i = a; i <= b; i++) {
				double t = (i - a) / (double) (b - a);
				delta[i] = start + t * (end - start);
			}
		}
		blendEnds(delta, ordered.get(0).column, ordered.get(ordered.size() - 1).column);
	}

	private static void blendEnds(double[] delta, int first, int last) {
		double start = delta[first];
		double end = delta[last];
		for (int k = 1; k <= BLEND_COLUMNS; k++) {
			double weight = (BLEND_COLUMNS - k) / (double) BLEND_COLUMNS;
			int left = first - k;
			if (left >= 0)
				delta[left] = start * weight;
			int right = last + k;
			if (right < delta.length)
				delta[right] = end * weight;
		}
	}

	private void showAlignmentPreview() {
		Capillary selected = strokeCapillary == null ? cap() : strokeCapillary;
		BufferedImage source = selected == null ? null : sources.get(selected);
		if (selected == null || source == null || frozenLevels == null || !hasStroke())
			return;
		if (frozenStrip == null)
			frozenStrip = aligned.get(selected);
		aligned.put(selected, MeniscusRefinement.stabilized(source, strokeLevels(source.getHeight()), stripRadius()));
		alignmentPreview = true;
		status.setText(
				"Strip shifted by the gap between the orange source and the blue final curve. Back restores the picture.");
		buttons();
		plot.repaint();
	}

	private void restoreFrozenStrip() {
		Capillary selected = strokeCapillary == null ? cap() : strokeCapillary;
		if (selected != null && frozenStrip != null)
			aligned.put(selected, frozenStrip);
		alignmentPreview = false;
		status.setText("The strip is back on editable topraw. Both curves are still in place.");
		buttons();
		plot.repaint();
	}

	private double[] strokeLevels(int imageHeight) {
		double[] levels = frozenLevels.clone();
		double max = Math.max(0, imageHeight - 1);
		for (int i = 0; i < levels.length && strokeOffset != null && i < strokeOffset.length; i++) {
			if (!Double.isFinite(strokeOffset[i]))
				continue;
			double value = frozenLevels[i] + imageDelta(strokeOffset[i]);
			if (value < 0)
				value = 0;
			else if (value > max)
				value = max;
			levels[i] = value;
		}
		return levels;
	}

	private double[] draftedLevels(MeniscusRefinement.Proposal p) {
		double[] drafted = p.refined();
		if (frozenLevels == null || strokeOffset == null)
			return drafted;
		BufferedImage source = sources.get(cap());
		int height = source == null ? 0 : source.getHeight();
		int n = Math.min(drafted.length, Math.min(frozenLevels.length, strokeOffset.length));
		for (int i = 0; i < n; i++) {
			if (!Double.isFinite(strokeOffset[i]))
				continue;
			double value = frozenLevels[i] + imageDelta(strokeOffset[i]);
			if (height > 0) {
				if (value < 0)
					value = 0;
				else if (value > height - 1)
					value = height - 1;
			}
			drafted[i] = value;
		}
		return drafted;
	}

	private void commitStroke() {
		Capillary selected = strokeCapillary == null ? cap() : strokeCapillary;
		MeniscusRefinement.Proposal p = selected == null ? null : proposals.get(selected);
		if (p == null || strokeOffset == null || !hasStroke() || !MeniscusRefinement.isCurrent(selected, p))
			return;
		invalidateCorrelation();
		p.addOffsets(imageDeltas(strokeOffset));
		clearStroke();
		BufferedImage source = sources.get(selected);
		if (source != null)
			aligned.put(selected, MeniscusRefinement.stabilized(source, p.refined(), stripRadius()));

		MeniscusRefinementPreview.publish(this, proposals);
		updateOverlays();
		ChartCagesFrame.refreshRefinement(experiment);
		refreshComparison();
		plot.repaint();
	}

	private void resampleStripRadius() {
		Capillary selected = strokeCapillary == null ? cap() : strokeCapillary;
		BufferedImage source = selected == null ? null : sources.get(selected);
		if (frozenLevels != null && source != null && frozenLevels.length == source.getWidth()) {
			frozenStrip = MeniscusRefinement.stabilized(source, frozenLevels, stripRadius());
			MeniscusRefinement.Proposal correlated = correlations.get(selected);
			double[] raw = correlated == null ? null : correlated.refined();
			if (raw != null)
				correlatedStrips.put(selected, MeniscusRefinement.stabilized(source, raw, stripRadius()));
			if (alignmentPreview)
				showAlignmentPreview();
			else {
				aligned.put(selected, frozenStrip);
				plot.repaint();
			}
			return;
		}
		rebuildStrips();
	}

	private void paintStroke(Graphics2D g, int width, int height) {
		int n = sourceScreen == null ? 0 : sourceScreen.length;
		if (n <= 0 || stripBottom(height) <= stripTop(height))
			return;
		g.setStroke(new BasicStroke(2f));
		paintProfile(g, sourceScreen, new Color(255, 140, 0), width, height);
		paintProfile(g, finalScreen, new Color(30, 90, 220), width, height);
		if (moveMode() != MoveMode.CURVE)
			return;
		paintHandles(g, sourceProfile.points, new Color(255, 140, 0), n, width, height);
		paintHandles(g, finalProfile.points, new Color(30, 90, 220), n, width, height);
	}

	private void paintProfile(Graphics2D g, double[] screen, Color color, int width, int height) {
		if (screen == null)
			return;
		g.setColor(color);
		int prevX = -1, prevY = -1;
		int n = screen.length;
		for (int i = 0; i < n; i++) {
			if (!Double.isFinite(screen[i])) {
				prevX = -1;
				continue;
			}
			int x = 10 + (int) Math.round((i + .5) * (width - 20.) / n);
			int y = offsetToScreen(screen[i], height);
			if (prevX >= 0)
				g.drawLine(prevX, prevY, x, y);
			prevX = x;
			prevY = y;
		}
	}

	private void paintHandles(Graphics2D g, List<StrokePoint> points, Color color, int n, int width, int height) {
		g.setColor(color);
		for (StrokePoint point : points) {
			if (point.column < 0 || point.column >= n)
				continue;
			int x = 10 + (int) Math.round((point.column + .5) * (width - 20.) / n);
			int y = offsetToScreen(point.offset, height);
			g.fillOval(x - 3, y - 3, 7, 7);
		}
	}

	private int curvePointAt(int mouseX, int mouseY, int width, int height) {
		int n = columnCount();
		if (n <= 0)
			return -1;
		int best = -1;
		double bestDistance = 64;
		List<StrokePoint> points = activeProfile().points;
		for (int i = 0; i < points.size(); i++) {
			StrokePoint point = points.get(i);
			int x = 10 + (int) Math.round((point.column + .5) * (width - 20.) / n);
			int y = offsetToScreen(point.offset, height);
			double dx = x - mouseX, dy = y - mouseY;
			double distance = dx * dx + dy * dy;
			if (distance <= bestDistance) {
				bestDistance = distance;
				best = i;
			}
		}
		return best;
	}

	private int offsetToScreen(double offset, int panelHeight) {
		int y1 = stripTop(panelHeight), y2 = stripBottom(panelHeight);
		int mid = (y1 + y2) / 2;
		return mid + (int) Math.round(offset * (y2 - y1) / (2.0 * stripRadius() + 1));
	}

	/** Screen y grows downward. Negate so the strip moves toward the stroke. */
	private static double imageDelta(double screenOffset) {
		return -screenOffset;
	}

	private static double[] imageDeltas(double[] screenOffset) {
		double[] delta = new double[screenOffset.length];
		for (int i = 0; i < screenOffset.length; i++)
			delta[i] = Double.isFinite(screenOffset[i]) ? imageDelta(screenOffset[i]) : Double.NaN;
		return delta;
	}

	private double screenToOffset(int mouseY, int panelHeight) {
		int y1 = stripTop(panelHeight), y2 = stripBottom(panelHeight);
		int span = y2 - y1;
		if (span <= 0)
			return 0;
		return (mouseY - (y1 + y2) / 2.0) * (2.0 * stripRadius() + 1) / span;
	}

	private double clampOffset(double offset) {
		double limit = stripRadius();
		if (offset > limit)
			return limit;
		if (offset < -limit)
			return -limit;
		return offset;
	}

	private static int clampColumn(int column, int n) {
		if (n <= 0)
			return 0;
		if (column < 0)
			return 0;
		if (column >= n)
			return n - 1;
		return column;
	}
}
