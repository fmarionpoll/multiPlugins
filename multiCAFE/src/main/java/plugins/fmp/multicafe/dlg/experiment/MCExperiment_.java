package plugins.fmp.multicafe.dlg.experiment;

import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.List;

import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import icy.canvas.IcyCanvas;
import icy.gui.component.PopupPanel;
import icy.gui.frame.IcyFrame;
import icy.gui.viewer.Viewer;
import icy.gui.viewer.ViewerEvent;
import icy.gui.viewer.ViewerEvent.ViewerEventType;
import icy.gui.viewer.ViewerListener;
import icy.main.Icy;
import icy.sequence.DimensionId;
import icy.sequence.Sequence;
import plugins.fmp.multicafe.MultiCAFE;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.tools.ViewerFMP;

public class MCExperiment_ extends JPanel implements ViewerListener, ChangeListener {
	/**
	 * 
	 */
	private static final long serialVersionUID = -6826269677524125173L;

	PopupPanel capPopupPanel = null;
	public JTabbedPane tabsPane = new JTabbedPane();
	public Options tabOptions = new Options();
	public Infos tabInfos = new Infos();
	public Filter tabFilter = new Filter();
	public EditCapillariesConditional tabEditCond = new EditCapillariesConditional();
	public Intervals tabIntervals = new Intervals();

	private MultiCAFE parent0 = null;

	// Global window position for camera data viewer - shared across all experiments
	private static Rectangle globalCamDataViewerBounds = null;

	public void init(JPanel mainPanel, String string, MultiCAFE parent0) {
		this.parent0 = parent0;

		capPopupPanel = new PopupPanel(string);
		capPopupPanel.collapse();
		mainPanel.add(capPopupPanel);
		GridLayout tabsLayout = new GridLayout(4, 1);

		tabInfos.init(tabsLayout, parent0);
		tabsPane.addTab("Infos", null, tabInfos, "Define infos for this experiment/box");

		tabFilter.init(tabsLayout, parent0);
		tabsPane.addTab("Filter", null, tabFilter, "Filter experiments based on descriptors");

		tabEditCond.init(tabsLayout, parent0);
		tabsPane.addTab("Edit", null, tabEditCond, "Edit descriptors with 1 or 2 conditions");

		tabIntervals.init(tabsLayout, parent0);
		tabsPane.addTab("Intervals", null, tabIntervals, "View/edit time-lapse intervals");

		tabOptions.init(tabsLayout, parent0);
		tabsPane.addTab("Options", null, tabOptions, "Options to display data");

		tabsPane.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);

		JPanel capPanel = capPopupPanel.getMainPanel();
		capPanel.setLayout(new BorderLayout());
		capPanel.add(tabsPane, BorderLayout.PAGE_END);

		tabsPane.addChangeListener(this);
		capPopupPanel.addComponentListener(new ComponentAdapter() {
			@Override
			public void componentResized(ComponentEvent e) {
				parent0.mainFrame.revalidate();
				parent0.mainFrame.pack();
				parent0.mainFrame.repaint();
			}
		});
	}

	public void updateDialogs(Experiment exp) {
		tabIntervals.displayCamDataIntervals(exp);
		tabInfos.transferPreviousExperimentInfosToDialog(exp, exp);
		parent0.paneKymos.tabDisplay.updateResultsAvailable(exp);
	}

	public void getExperimentInfosFromDialog(Experiment exp) {
		tabInfos.getExperimentInfosFromDialog(exp);
	}

	/**
	 * Updates or creates the viewer for the camera sequence (synchronous version).
	 * This ensures the viewer exists immediately, allowing ROI visibility to be set correctly.
	 */
	public void updateViewerForSequenceCam(Experiment exp) {
		updateViewerForSequenceCamSync(exp);
	}
	
	/**
	 * Synchronous version of viewer creation.
	 * This ensures proper synchronization with ROI visibility settings.
	 */
	private void updateViewerForSequenceCamSync(Experiment exp) {
		Sequence seq = exp.getSeqCamData().getSequence();
		if (seq == null)
			return;

		ViewerListener parent = this;
		int expIndex = parent0.expListComboLazy.getSelectedIndex();
		
		// Check if this experiment is still the selected one
		Experiment currentlySelected = (Experiment) parent0.expListComboLazy.getSelectedItem();
		if (currentlySelected != exp) {
			System.err.println("MCExperiment_:updateViewerForSequenceCamSync [" + expIndex
					+ "] - Experiment no longer selected, aborting viewer creation");
			return;
		}

		// Calculate position before any viewer operations to avoid flickering
		Rectangle initialBounds = calculateCamDataViewerBounds(parent0.mainFrame);

		ViewerFMP v = (ViewerFMP) seq.getFirstViewer();
		if (v == null) {
			try {
				// Create viewer with visible=false to prevent flickering
				v = new ViewerFMP(seq, false, true);
				List<String> list = IcyCanvas.getCanvasPluginNames();
				String pluginName = list.stream().filter(s -> s.contains("Canvas2DWithTransforms")).findFirst()
						.orElse(null);
				v.setCanvas(pluginName);

				// Set position before making viewer visible
				if (initialBounds != null) {
					v.setBounds(initialBounds);
				}

				// Now make the viewer visible with the correct position already set
				v.setVisible(true);
			} catch (Exception e) {
				System.err.println("MCExperiment_:updateViewerForSequenceCamSync [" + expIndex
						+ "] - Failed to create viewer: " + e.getMessage());
				return;
			}
		} else {
			// Viewer already exists - reposition it immediately
			if (initialBounds != null) {
				// Hide viewer, set bounds, then show to avoid flickering
				boolean wasVisible = v.isVisible();
				if (wasVisible) {
					v.setVisible(false);
				}
				v.setBounds(initialBounds);
				if (wasVisible) {
					v.setVisible(true);
				}
			}
		}

		v.toFront();
		v.requestFocus();
		v.addListener(parent);
		if (exp.getSeqCamData() != null) {
			v.setTitle(exp.getSeqCamData().getDecoratedImageName(0));
		}
		v.setRepeat(false);
	}
	
	/**
	 * Asynchronous version of viewer creation (LEGACY - can be deleted if sync version works well).
	 * Kept as backup in case we need to revert to async behavior.
	 * 
	 * @deprecated Use updateViewerForSequenceCamSync() instead. This method can be deleted
	 *             once synchronous version is confirmed stable.
	 */
	@Deprecated
	private void updateViewerForSequenceCamAsync(Experiment exp) {
		Sequence seq = exp.getSeqCamData().getSequence();
		if (seq == null)
			return;

		final ViewerListener parent = this;
		final int expIndex = parent0.expListComboLazy.getSelectedIndex();
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				// Check if this experiment is still the selected one
				Experiment currentlySelected = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (currentlySelected != exp) {
					System.err.println("MCExperiment_:updateViewerForSequenceCamAsync [" + expIndex
							+ "] - Experiment no longer selected, aborting viewer creation");
					return;
				}

				// Re-check sequence is still valid (may have changed during async execution)
				Sequence currentSeq = exp.getSeqCamData() != null ? exp.getSeqCamData().getSequence() : null;
				if (currentSeq == null) {
					System.err.println("MCExperiment_:updateViewerForSequenceCamAsync [" + expIndex
							+ "] - Sequence became null before viewer creation");
					return;
				}

				// Calculate position before any viewer operations to avoid flickering
				Rectangle initialBounds = calculateCamDataViewerBounds(parent0.mainFrame);

				ViewerFMP v = (ViewerFMP) currentSeq.getFirstViewer();
				if (v == null) {
					try {
						// Create viewer with visible=false to prevent flickering
						// currentSeq is already validated above
						v = new ViewerFMP(currentSeq, false, true);
						List<String> list = IcyCanvas.getCanvasPluginNames();
						String pluginName = list.stream().filter(s -> s.contains("Canvas2DWithTransforms")).findFirst()
								.orElse(null);
						v.setCanvas(pluginName);

						// Set position before making viewer visible
						if (initialBounds != null) {
							v.setBounds(initialBounds);
						}

						// Now make the viewer visible with the correct position already set
						v.setVisible(true);
					} catch (Exception e) {
						System.err.println("MCExperiment_:updateViewerForSequenceCamAsync [" + expIndex
								+ "] - Failed to create viewer: " + e.getMessage());
						return;
					}
				} else {
					// Viewer already exists - reposition it immediately
					if (initialBounds != null) {
						// Hide viewer, set bounds, then show to avoid flickering
						boolean wasVisible = v.isVisible();
						if (wasVisible) {
							v.setVisible(false);
						}
						v.setBounds(initialBounds);
						if (wasVisible) {
							v.setVisible(true);
						}
					}
				}

				v.toFront();
				v.requestFocus();
				v.addListener(parent);
				if (exp.getSeqCamData() != null) {
					v.setTitle(exp.getSeqCamData().getDecoratedImageName(0));
				}
				v.setRepeat(false);
			}
		});
	}

	private Rectangle calculateCamDataViewerBounds(IcyFrame mainFrame) {
		// Use saved global position if available
		if (globalCamDataViewerBounds != null) {
			return globalCamDataViewerBounds;
		}

		// Initial positioning logic (original behavior)
		Rectangle rect0 = mainFrame.getBoundsInternal();
		Rectangle rectv = new Rectangle();
		rectv.setSize(800, 600); // Default size
		if (rect0.x + rect0.width < Icy.getMainInterface().getMainFrame().getDesktopWidth()) {
			rectv.setLocation(rect0.x + rect0.width, rect0.y);
		} else {
			rectv.setLocation(rect0.x, rect0.y);
		}
		return rectv;
	}

	@Override
	public void viewerChanged(ViewerEvent event) {
		if ((event.getType() == ViewerEventType.POSITION_CHANGED)) {
			if (event.getDim() == DimensionId.T) {
				Viewer v = event.getSource();
				int idViewer = v.getSequence().getId();
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp != null) {
					int idCurrentExp = exp.getSeqCamData().getSequence().getId();
					if (idViewer == idCurrentExp) {
						int t = v.getPositionT();
						v.setTitle(exp.getSeqCamData().getDecoratedImageName(t));
						if (parent0.paneCages.bTrapROIsEdit)
							exp.saveDetRoisToPositions();
						exp.updateROIsAt(t);
					}
				}
			}
		}
	}

	@Override
	public void viewerClosed(Viewer viewer) {
		// Save window position before closing (global position, shared across all
		// experiments)
		if (viewer != null) {
			globalCamDataViewerBounds = viewer.getBounds();
			viewer.removeListener(this);
		}
	}

	@Override
	public void stateChanged(ChangeEvent e) {
		JTabbedPane tabbedPane = (JTabbedPane) e.getSource();
		if (tabbedPane.getSelectedIndex() == 0) {
			if (tabInfos != null)
				tabInfos.initCombos();
		} else if (tabbedPane.getSelectedIndex() == 1) {
			if (tabFilter != null)
				tabFilter.initCombos();
		} else if (tabbedPane.getSelectedIndex() == 2) {
			if (tabEditCond != null)
				tabEditCond.initEditCombos();
		}
	}

}
