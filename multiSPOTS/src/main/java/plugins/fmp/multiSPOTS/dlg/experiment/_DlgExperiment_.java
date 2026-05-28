package plugins.fmp.multiSPOTS.dlg.experiment;

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
import plugins.fmp.multiSPOTS.MultiSPOTS;
import plugins.fmp.multiSPOTS.dlg.hosts.MultiSpotsCorrectDriftHost;
import plugins.fmp.multiSPOTS.dlg.hosts.MultiSpotsIntervalsHost;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.ui.CorrectDriftPanel;
import plugins.fmp.multitools.experiment.ui.IntervalsPanel;
import plugins.fmp.multitools.tools.ViewerFMP;

public class _DlgExperiment_ extends JPanel implements ViewerListener, ChangeListener {
	/**
	 * 
	 */
	private static final long serialVersionUID = -6826269677524125173L;

	PopupPanel capPopupPanel = null;
	public JTabbedPane tabsPane = new JTabbedPane();
	public InfosPanel infosPanel = new InfosPanel();
	public FilterPanel filterPanel = new FilterPanel();
	EditPanel editPanel = new EditPanel();
	public IntervalsPanel intervalsPanel = new IntervalsPanel();
	CorrectDriftPanel correctDriftPanel = new CorrectDriftPanel();
	public OptionsPanel optionsPanel = new OptionsPanel();
	private MultiSPOTS parent0 = null;

	/** Camera sequence viewer bounds, shared across experiments in this session (same idea as MultiCAFE). */
	private static Rectangle globalCamDataViewerBounds = null;

	public void init(JPanel mainPanel, String string, MultiSPOTS parent0) {
		this.parent0 = parent0;

		capPopupPanel = new PopupPanel(string);
		capPopupPanel.collapse();
		mainPanel.add(capPopupPanel);
		GridLayout tabsLayout = new GridLayout(4, 1);

		infosPanel.init(tabsLayout, parent0);
		tabsPane.addTab("Infos", null, infosPanel, "Define descriptors for experiment");

		filterPanel.init(tabsLayout, parent0);
		tabsPane.addTab("Filter", null, filterPanel, "Filter experiments based on descriptors");

		editPanel.init(tabsLayout, parent0);
		tabsPane.addTab("Edit", null, editPanel, "Edit descriptors");

		intervalsPanel.init(tabsLayout, new MultiSpotsIntervalsHost(parent0));
		tabsPane.addTab("Intervals", null, intervalsPanel, "View/edit time-lapse intervals");

		correctDriftPanel.init(tabsLayout, new MultiSpotsCorrectDriftHost(parent0));
		tabsPane.addTab("Correct drift", null, correctDriftPanel, "Correct image drift with time");

		optionsPanel.init(tabsLayout, parent0);
		tabsPane.addTab("Options", null, optionsPanel, "Options to display data");

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
		intervalsPanel.getExptParms(exp);
		infosPanel.transferPreviousExperimentInfosToDialog(exp, exp);
	}

	public void getExperimentInfosFromDialog(Experiment exp) {
		infosPanel.getExperimentInfosFromDialog(exp);
	}

	public void updateViewerForSequenceCam(Experiment exp) {
		final Sequence seq = exp.getSeqCamData().getSequence();
		final ViewerListener parent = this;
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				if (seq == null)
					return;

				Experiment currentlySelected = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (currentlySelected != exp) {
					return;
				}

				Rectangle initialBounds = calculateCamDataViewerBounds(parent0.mainFrame);

				ViewerFMP v = (ViewerFMP) seq.getFirstViewer();
				if (v == null) {
					try {
						v = new ViewerFMP(seq, false, true);
						List<String> list = IcyCanvas.getCanvasPluginNames();
						String pluginName = list.stream().filter(s -> s.contains("Canvas2D_3Transforms")).findFirst()
								.orElse(null);
						if (pluginName != null) {
							v.setCanvas(pluginName);
						}
						if (initialBounds != null) {
							v.setBounds(initialBounds);
						}
						final ViewerFMP vRef = v;
						SwingUtilities.invokeLater(() -> {
							if (vRef != null && seq.getFirstViewer() == vRef) {
								vRef.setVisible(true);
							}
						});
					} catch (Exception e) {
						e.printStackTrace();
						return;
					}
				} else if (initialBounds != null) {
					boolean wasVisible = v.isVisible();
					if (wasVisible) {
						v.setVisible(false);
					}
					v.setBounds(initialBounds);
					if (wasVisible) {
						v.setVisible(true);
					}
				}

				if (v != null) {
					v.removeListener(parent);
					v.removeListener(correctDriftPanel);
					v.addListener(parent);
					v.addListener(correctDriftPanel);
					v.toFront();
					v.requestFocus();
					v.setTitle(exp.getSeqCamData().getDecoratedImageName(0));
					v.setRepeat(false);

					correctDriftPanel.resetFrameIndex();

					if (seq.isUpdating()) {
						seq.endUpdate();
					}
					Experiment current = (Experiment) parent0.expListComboLazy.getSelectedItem();
					if (current == exp) {
						exp.transferCagesROI_toSequence();
						exp.transferSpotsROI_toSequence();
						SwingUtilities.invokeLater(() -> optionsPanel.applyViewOptionsToCurrentExperiment());
					}
				}
			}
		});
	}

	private Rectangle calculateCamDataViewerBounds(IcyFrame mainFrame) {
		if (globalCamDataViewerBounds != null) {
			return globalCamDataViewerBounds;
		}
		if (mainFrame == null) {
			return null;
		}
		Rectangle rect0 = mainFrame.getBoundsInternal();
		Rectangle rectv = new Rectangle();
		rectv.setSize(800, 600);
		if (rect0.x + rect0.width < Icy.getMainInterface().getMainFrame().getDesktopWidth()) {
			rectv.setLocation(rect0.x + rect0.width, rect0.y);
		} else {
			rectv.setLocation(rect0.x, rect0.y);
		}
		return rectv;
	}

	@Override
	public void viewerChanged(ViewerEvent event) {
		if (event.getType() != ViewerEventType.POSITION_CHANGED || event.getDim() != DimensionId.T)
			return;
		Viewer v = event.getSource();
		if (v.getSequence() == null)
			return;
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		if (exp == null || exp.getSeqCamData() == null || exp.getSeqCamData().getSequence() == null)
			return;
		if (v.getSequence().getId() != exp.getSeqCamData().getSequence().getId())
			return;
		exp.onViewerTPositionChanged(v, v.getPositionT(), false);
	}

	@Override
	public void viewerClosed(Viewer viewer) {
		if (viewer != null) {
			globalCamDataViewerBounds = viewer.getBounds();
			viewer.removeListener(this);
			viewer.removeListener(correctDriftPanel);
		}
	}

	@Override
	public void stateChanged(ChangeEvent e) {
		JTabbedPane tabbedPane = (JTabbedPane) e.getSource();
		if (tabbedPane.getSelectedIndex() == 0)
			infosPanel.initCombos();
		else if (tabbedPane.getSelectedIndex() == 1)
			filterPanel.initCombos();
		else if (tabbedPane.getSelectedIndex() == 2)
			editPanel.initEditCombos();
	}

}
