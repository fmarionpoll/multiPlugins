package plugins.fmp.multiSPOTS96.dlg.a_experiment;

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
import plugins.fmp.multiSPOTS96.MultiSPOTS96;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.tools.ViewerFMP;

public class _DlgExperiment_ extends JPanel implements ViewerListener, ChangeListener {
	/**
	 * 
	 */
	private static final long serialVersionUID = -6826269677524125173L;

	PopupPanel capPopupPanel = null;
	public JTabbedPane tabsPane = new JTabbedPane();
	public Infos tabInfos = new Infos();
	public Filter tabFilter = new Filter();
	Edit tabEdit = new Edit();
	public Intervals tabIntervals = new Intervals();
	CorrectDrift tabCorrectDrift = new CorrectDrift();
	public Options tabOptions = new Options();
	private MultiSPOTS96 parent0 = null;

	public void init(JPanel mainPanel, String string, MultiSPOTS96 parent0) {
		this.parent0 = parent0;

		capPopupPanel = new PopupPanel(string);
		capPopupPanel.collapse();
		mainPanel.add(capPopupPanel);
		GridLayout tabsLayout = new GridLayout(4, 1);

		tabInfos.init(tabsLayout, parent0);
		tabsPane.addTab("Infos", null, tabInfos, "Define descriptors for experiment");

		tabFilter.init(tabsLayout, parent0);
		tabsPane.addTab("Filter", null, tabFilter, "Filter experiments based on descriptors");

		tabEdit.init(tabsLayout, parent0);
		tabsPane.addTab("Edit", null, tabEdit, "Edit descriptors");

		tabIntervals.init(tabsLayout, parent0);
		tabsPane.addTab("Intervals", null, tabIntervals, "View/edit time-lapse intervals");

		tabOptions.init(tabsLayout, parent0);
		tabsPane.addTab("Options", null, tabOptions, "Options to display data");

		tabCorrectDrift.init(tabsLayout, parent0);
		tabsPane.addTab("Correct drift", null, tabCorrectDrift, "Correct image drift with time");

//		CorrectDriftRefactored tabCorrectDrift2 = new CorrectDriftRefactored(parent0.expListCombo);
//		tabsPane.addTab("Correct drift", null, tabCorrectDrift2, "Correct image drift with time");

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
		tabIntervals.getExptParms(exp);
		tabInfos.transferPreviousExperimentInfosToDialog(exp, exp);
	}

	public void getExperimentInfosFromDialog(Experiment exp) {
		tabInfos.getExperimentInfosFromDialog(exp.getProperties());
	}

	public void updateViewerForSequenceCam(Experiment exp) {
		final Sequence seq = exp.seqCamData.getSequence();
		final ViewerListener parent = this;
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				if (seq == null)
					return;

				ViewerFMP v = (ViewerFMP) seq.getFirstViewer();
				if (v == null) {
					v = new ViewerFMP(seq, true, true);
					List<String> list = IcyCanvas.getCanvasPluginNames();
					String pluginName = list.stream().filter(s -> s.contains("Canvas2D_3Transforms")).findFirst()
							.orElse(null);
					v.setCanvas(pluginName);
				}

				if (v != null) {
					placeViewerNextToDialogBox(v, parent0.mainFrame);
					v.toFront();
					v.requestFocus();
					v.setTitle(exp.seqCamData.getDecoratedImageName(0));
					v.setRepeat(false);

					v.addListener(parent);
					v.addListener(tabCorrectDrift);
					tabCorrectDrift.resetFrameIndex();
				}
			}
		});
	}

	private void placeViewerNextToDialogBox(Viewer v, IcyFrame mainFrame) {
		Rectangle rectv = v.getBoundsInternal();
		Rectangle rect0 = mainFrame.getBoundsInternal();
		if (rect0.x + rect0.width < Icy.getMainInterface().getMainFrame().getDesktopWidth()) {
			rectv.setLocation(rect0.x + rect0.width, rect0.y);
			v.setBounds(rectv);
		}
	}

	@Override
	public void viewerChanged(ViewerEvent event) {
		if ((event.getType() == ViewerEventType.POSITION_CHANGED) && (event.getDim() == DimensionId.T)) {
			Viewer v = event.getSource();
			int idViewer = v.getSequence().getId();

			Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
			if (exp != null) {
				int idCurrentSeqCamData = exp.seqCamData.getSequence().getId();
				if (idViewer == idCurrentSeqCamData) {
					int t = v.getPositionT();
					v.setTitle(exp.seqCamData.getDecoratedImageName(t));
					// TODO _CAGES if (parent0.dlgCages.bTrapROIsEdit)
					// TODO _CAGES exp.saveDetRoisToPositions();
					exp.updateROIsAt(t);
				}
			}
		}
	}

	@Override
	public void viewerClosed(Viewer viewer) {
		viewer.removeListener(this);
	}

	@Override
	public void stateChanged(ChangeEvent e) {
		JTabbedPane tabbedPane = (JTabbedPane) e.getSource();
		if (tabbedPane.getSelectedIndex() == 0)
			tabInfos.initCombos();
		else if (tabbedPane.getSelectedIndex() == 1)
			tabFilter.initCombos();
		else if (tabbedPane.getSelectedIndex() == 2)
			tabEdit.initEditCombos();
	}

}
