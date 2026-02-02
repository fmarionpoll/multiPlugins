package plugins.fmp.multiSPOTS.dlg.browse;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import icy.gui.frame.progress.ProgressFrame;
import icy.gui.viewer.Viewer;
import icy.sequence.Sequence;
import icy.sequence.SequenceEvent;
import icy.sequence.SequenceEvent.SequenceEventSourceType;
import icy.sequence.SequenceListener;
import plugins.fmp.multiSPOTS.MultiSPOTS;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.ExperimentDirectories;
import plugins.fmp.multitools.tools.JComponents.SequenceNameListRenderer;

public class LoadSaveExperiment extends JPanel implements PropertyChangeListener, ItemListener, SequenceListener {
	/**
	 * 
	 */
	private static final long serialVersionUID = -690874563607080412L;

	private JButton createButton = new JButton("Create...");
	private JButton openButton = new JButton("Open...");
	private JButton searchButton = new JButton("Search...");
	private JButton closeButton = new JButton("Close");
	public JCheckBox filteredCheck = new JCheckBox("List filtered");

	public List<String> selectedNames = new ArrayList<String>();
	private SelectFilesPanel dialogSelect = null;

	private JButton previousButton = new JButton("<");
	private JButton nextButton = new JButton(">");

	private MultiSPOTS parent0 = null;

	public JPanel initPanel(MultiSPOTS parent0) {
		this.parent0 = parent0;

		SequenceNameListRenderer renderer = new SequenceNameListRenderer();
		parent0.expListCombo.setRenderer(renderer);
		int bWidth = 30;
		int height = 20;
		previousButton.setPreferredSize(new Dimension(bWidth, height));
		nextButton.setPreferredSize(new Dimension(bWidth, height));

		JPanel sequencePanel0 = new JPanel(new BorderLayout());
		sequencePanel0.add(previousButton, BorderLayout.LINE_START);
		sequencePanel0.add(parent0.expListCombo, BorderLayout.CENTER);
		sequencePanel0.add(nextButton, BorderLayout.LINE_END);

		JPanel sequencePanel = new JPanel(new BorderLayout());
		FlowLayout layout = new FlowLayout(FlowLayout.LEFT);
		layout.setVgap(1);
		JPanel subPanel = new JPanel(layout);
		subPanel.add(openButton);
		subPanel.add(createButton);
		subPanel.add(searchButton);
		subPanel.add(closeButton);
		subPanel.add(filteredCheck);
		sequencePanel.add(subPanel, BorderLayout.LINE_START);

		defineActionListeners();
		parent0.expListCombo.addItemListener(this);

		JPanel twoLinesPanel = new JPanel(new GridLayout(2, 1));
		twoLinesPanel.add(sequencePanel0);
		twoLinesPanel.add(sequencePanel);

		return twoLinesPanel;
	}

	void closeAllExperiments() {
		closeCurrentExperiment();
		parent0.expListCombo.removeAllItems();
		parent0.dlgExperiment.tabFilter.clearAllCheckBoxes();
		parent0.dlgExperiment.tabFilter.filterExpList.removeAllItems();
		parent0.dlgExperiment.tabInfos.clearCombos();
		filteredCheck.setSelected(false);
	}

	public void closeViewsForCurrentExperiment(Experiment exp) {
		if (exp != null) {
			if (exp.getSeqCamData() != null) {
				exp.saveXML_MCExperiment();
				exp.save_SpotsMeasures();
			}
			exp.closeSequences();
		}
	}

	public void closeCurrentExperiment() {
		if (parent0.expListCombo.getSelectedIndex() < 0)
			return;
		Experiment exp = (Experiment) parent0.expListCombo.getSelectedItem();
		if (exp != null)
			closeViewsForCurrentExperiment(exp);
	}

	void updateBrowseInterface() {
		int isel = parent0.expListCombo.getSelectedIndex();
		boolean flag1 = (isel == 0 ? false : true);
		boolean flag2 = (isel == (parent0.expListCombo.getItemCount() - 1) ? false : true);
		previousButton.setEnabled(flag1);
		nextButton.setEnabled(flag2);
	}

	// ------------------------

	private void defineActionListeners() {
		parent0.expListCombo.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				updateBrowseInterface();
			}
		});

		nextButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				parent0.expListCombo.setSelectedIndex(parent0.expListCombo.getSelectedIndex() + 1);
				updateBrowseInterface();
			}
		});

		previousButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				parent0.expListCombo.setSelectedIndex(parent0.expListCombo.getSelectedIndex() - 1);
				updateBrowseInterface();
			}
		});

		searchButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				selectedNames = new ArrayList<String>();
				dialogSelect = new SelectFilesPanel();
				dialogSelect.initialize(parent0, selectedNames);
			}
		});

		createButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				ExperimentDirectories eDAF = new ExperimentDirectories();
				final String subDir = parent0.expListCombo.stringExpBinSubDirectory;
				if (eDAF.getDirectoriesFromDialog(subDir, null, true)) {
					int item = parent0.expListCombo.addExperiment(new Experiment(eDAF), false);
					parent0.dlgExperiment.tabInfos.initInfosCombos();
					parent0.expListCombo.setSelectedIndex(item);
				}
			}
		});

		openButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				ExperimentDirectories eDAF = new ExperimentDirectories();
				final String subDir = parent0.expListCombo.stringExpBinSubDirectory;
				if (eDAF.getDirectoriesFromDialog(subDir, null, false)) {
					int item = parent0.expListCombo.addExperiment(new Experiment(eDAF), false);
					parent0.dlgExperiment.tabInfos.initInfosCombos();
					parent0.expListCombo.setSelectedIndex(item);
				}
			}
		});

		closeButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				closeAllExperiments();
				parent0.dlgExperiment.tabsPane.setSelectedIndex(0);
				parent0.expListCombo.removeAllItems();
				parent0.expListCombo.updateUI();
			}
		});

		filteredCheck.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				parent0.dlgExperiment.tabFilter.filterExperimentList(filteredCheck.isSelected());
			}
		});
	}

	@Override
	public void sequenceChanged(SequenceEvent sequenceEvent) {
		if (sequenceEvent.getSourceType() == SequenceEventSourceType.SEQUENCE_DATA) {
			Experiment exp = (Experiment) parent0.expListCombo.getSelectedItem();
			if (exp != null) {
				if (exp.getSeqCamData().getSequence() != null
						&& sequenceEvent.getSequence() == exp.getSeqCamData().getSequence()) {
					Viewer v = exp.getSeqCamData().getSequence().getFirstViewer();
					int t = v.getPositionT();
					v.setTitle(exp.getSeqCamData().getDecoratedImageName(t));
				}
				// TODO: check if the lines below are necessary
				if (exp.getSeqKymos().getSequence() != null && sequenceEvent.getSequence() == exp.getSeqKymos().seq) {
					Viewer v = exp.getSeqKymos().getSequence().getFirstViewer();
					v.setTitle("dummy");
				}
			}
		}
	}

	@Override
	public void sequenceClosed(Sequence sequence) {
		sequence.removeListener(this);
	}

	@Override
	public void propertyChange(PropertyChangeEvent evt) // TODO
	{
		if (evt.getPropertyName().equals("SELECT1_CLOSED")) {
			if (selectedNames.size() < 1)
				return;

			ExperimentDirectories expDirectories = new ExperimentDirectories();
			final String subDir = parent0.expListCombo.stringExpBinSubDirectory;
			if (expDirectories.getDirectoriesFromExptPath(subDir, selectedNames.get(0))) {
				int item = parent0.expListCombo.addExperiment(new Experiment(expDirectories), false);
				parent0.dlgExperiment.tabInfos.initInfosCombos();
				parent0.expListCombo.setSelectedIndex(item);

				SwingUtilities.invokeLater(new Runnable() {
					public void run() {
						for (int i = 1; i < selectedNames.size(); i++) {
							ExperimentDirectories eDAF = new ExperimentDirectories();
							if (eDAF.getDirectoriesFromExptPath(subDir, selectedNames.get(i))) {
								parent0.expListCombo.addExperiment(new Experiment(eDAF), false);
							}
						}
						selectedNames.clear();
						updateBrowseInterface();
					}
				});
			}
		}
	}

	@Override
	public void itemStateChanged(ItemEvent e) {
		if (e.getStateChange() == ItemEvent.SELECTED) {
			final Experiment exp = (Experiment) parent0.expListCombo.getSelectedItem();
			if (exp != null)
				openSelecteExperiment(exp);
		} else if (e.getStateChange() == ItemEvent.DESELECTED) {
			Experiment exp = (Experiment) e.getItem();
			closeViewsForCurrentExperiment(exp);
		}
	}

	boolean openSelecteExperiment(Experiment exp) {
		ProgressFrame progressFrame = new ProgressFrame("Load Data");
		exp.loadXML_MCExperiment();

		boolean flag = true;
		progressFrame.setMessage("Load image");
		List<String> imagesList = (ArrayList<String>) ExperimentDirectories
				.getImagesListFromPathV2(exp.getSeqCamData().imagesDirectory, "jpg");
		exp.getSeqCamData().loadImageList(imagesList);
		parent0.dlgExperiment.updateViewerForSequenceCam(exp);

		exp.getSeqCamData().getSequence().addListener(this);
		if (exp.getSeqCamData() != null) {
			exp.load_Spots();
			exp.load_SpotsMeasures();
			exp.spotsArray.transferSpotsToSequenceAsROIs(exp.getSeqCamData().getSequence());

			exp.load_Cages();
			exp.getCages().pushROIsFromCagesToSequence(exp.getSeqCamData().getSequence());

			parent0.dlgMeasure.tabGraphs.displayGraphsPanels(exp);

			if (exp.getSeqKymos() != null) {
				parent0.dlgKymos.tabLoadSave.loadDefaultKymos(exp);
			}

			progressFrame.setMessage("Load data: update dialogs");
			parent0.dlgExperiment.updateDialogs(exp);
			parent0.dlgSpots.updateDialogs(exp);
		} else {
			flag = false;
			System.out.println(
					"LoadSaveExperiments:openSelectedExperiment() Error: no jpg files found for this experiment\n");
		}
		parent0.dlgExperiment.tabInfos.transferPreviousExperimentInfosToDialog(exp, exp);
		progressFrame.close();

		return flag;
	}

}
