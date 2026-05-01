package plugins.fmp.multicafe.dlg.browse;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import icy.gui.viewer.Viewer;
import icy.sequence.Sequence;
import icy.sequence.SequenceEvent;
import icy.sequence.SequenceEvent.SequenceEventSourceType;
import icy.sequence.SequenceListener;
import plugins.fmp.multicafe.MultiCAFE;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.ExperimentDirectories;
import plugins.fmp.multitools.experiment.LazyExperiment;
import plugins.fmp.multitools.experiment.LazyExperiment.ExperimentMetadata;
import plugins.fmp.multitools.experiment.ui.ExperimentBrowseKeyboard;
import plugins.fmp.multitools.experiment.ui.ExperimentLoadLifecycle;
import plugins.fmp.multitools.experiment.ui.SelectFilesPanel;
import plugins.fmp.multitools.tools.Logger;

public class LoadSaveExperiment extends JPanel implements PropertyChangeListener, ItemListener, SequenceListener {

	private static final long serialVersionUID = -690874563607080412L;

	private JButton openButton = new JButton("Open...");
	private JButton createButton = new JButton("Create...");
	private JButton searchButton = new JButton("Search...");
	private JButton closeButton = new JButton("Close");
	public JCheckBox filteredCheck = new JCheckBox("List filtered");

	public JCheckBox getFilteredCheck() {
		return filteredCheck;
	}

	public List<String> selectedNames = new ArrayList<String>();
	private SelectFilesPanel dialogSelect = null;

	private JButton previousButton = new JButton("<");
	private JButton nextButton = new JButton(">");

	List<ExperimentMetadata> experimentMetadataList = new ArrayList<>();
	volatile boolean isProcessing = false;
	final AtomicInteger processingCount = new AtomicInteger(0);
	volatile boolean lastMetadataScanFailed = false;

	final ExperimentLoadLifecycle loadLifecycle = new ExperimentLoadLifecycle();

	MultiCAFE parent0 = null;

	private CafeMetadataScanCoordinator metadataScan;
	private CafeExperimentOpenPipeline openPipeline;
	private CafeExperimentClosePipeline closePipeline;

	public LoadSaveExperiment() {
	}

	public JPanel initPanel(MultiCAFE parent0) {
		this.parent0 = parent0;
		this.metadataScan = new CafeMetadataScanCoordinator(this);
		this.openPipeline = new CafeExperimentOpenPipeline(this);
		this.closePipeline = new CafeExperimentClosePipeline();

		setLayout(new BorderLayout());
		setPreferredSize(new Dimension(400, 200));

		JPanel group2Panel = initUI();
		defineActionListeners();
		SwingUtilities.invokeLater(() -> ExperimentBrowseKeyboard.install(group2Panel, previousButton, nextButton,
				() -> parent0 != null && parent0.mainFrame != null && parent0.mainFrame.isVisible()));
		parent0.expListComboLazy.addItemListener(this);

		return group2Panel;
	}

	private JPanel initUI() {
		JPanel navPanel = CafeBrowseUi.createNavigationPanel(parent0, previousButton, nextButton);
		JPanel buttonPanel = CafeBrowseUi.createButtonPanel(openButton, createButton, searchButton, closeButton,
				filteredCheck);
		return CafeBrowseUi.createMainGrid(navPanel, buttonPanel);
	}

	private void defineActionListeners() {
		openButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				handleOpenButton();
			}
		});

		searchButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				handleSearchButton();
			}
		});

		closeButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				handleCloseButton();
			}
		});

		previousButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				handlePreviousButton();
			}
		});

		nextButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				handleNextButton();
			}
		});

		parent0.expListComboLazy.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				updateBrowseInterface();
			}
		});

		filteredCheck.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				parent0.paneExperiment.tabFilter.filterExperimentList(filteredCheck.isSelected());
			}
		});

		createButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				ExperimentDirectories eDAF = new ExperimentDirectories();
				if (eDAF.getDirectoriesFromDialog(parent0.expListComboLazy, null, true)) {
					int item = addExperimentFrom3NamesAnd2Lists(eDAF);
					parent0.expListComboLazy.setSelectedIndex(item);
					parent0.paneExperiment.tabInfos.initCombos();
				}
			}
		});

	}

	@Override
	public void propertyChange(PropertyChangeEvent evt) {
		if (evt.getPropertyName().equals("SELECT1_CLOSED")) {
			metadataScan.onPropertyChangeSelectClosed();
		}
	}

	private void handleOpenButton() {
		ExperimentDirectories eDAF = new ExperimentDirectories();
		final String binDirectory = parent0.expListComboLazy.expListBinSubDirectory;
		if (eDAF.getDirectoriesFromDialog(binDirectory, null, false)) {
			String camDataImagesDirectory = eDAF.getCameraImagesDirectory();
			String resultsDirectory = eDAF.getResultsDirectory();
			ExperimentMetadata metadata = new ExperimentMetadata(camDataImagesDirectory, resultsDirectory,
					binDirectory);

			LazyExperiment lazyExp = new LazyExperiment(metadata);
			int selectedIndex = parent0.expListComboLazy.addLazyExperiment(lazyExp);
			parent0.expListComboLazy.setSelectedIndex(selectedIndex);
			parent0.paneExperiment.tabInfos.initCombos();
		}
	}

	private void handleSearchButton() {
		selectedNames = new ArrayList<String>();
		dialogSelect = new SelectFilesPanel(SelectFilesPanel.Features.cafeDefaults());
		dialogSelect.initialize(parent0.getPreferences("gui"), this, selectedNames);
	}

	private void handleCloseButton() {
		closeAllExperiments();
		parent0.expListComboLazy.removeAllItems();
		parent0.expListComboLazy.updateUI();
	}

	private void handlePreviousButton() {
		parent0.expListComboLazy.setSelectedIndex(parent0.expListComboLazy.getSelectedIndex() - 1);
		updateBrowseInterface();
	}

	private void handleNextButton() {
		parent0.expListComboLazy.setSelectedIndex(parent0.expListComboLazy.getSelectedIndex() + 1);
		updateBrowseInterface();
	}

	@Override
	public void sequenceChanged(SequenceEvent sequenceEvent) {
		if (sequenceEvent.getSourceType() == SequenceEventSourceType.SEQUENCE_DATA) {
			Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
			if (exp != null) {
				if (exp.getSeqCamData().getSequence() != null
						&& sequenceEvent.getSequence() == exp.getSeqCamData().getSequence()) {
					Viewer v = exp.getSeqCamData().getSequence().getFirstViewer();
					int t = v.getPositionT();
					v.setTitle(exp.getSeqCamData().getDecoratedImageName(t));
				}
			}
		}
	}

	@Override
	public void sequenceClosed(Sequence sequence) {
		sequence.removeListener(this);
		ArrayList<Viewer> listViewers = sequence.getViewers();
		for (Viewer v : listViewers) {
			v.close();
		}
	}

	@Override
	public void itemStateChanged(ItemEvent e) {
		if (e.getStateChange() == ItemEvent.SELECTED) {
			final Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
			if (exp != null) {
				if (loadLifecycle.currentlyLoadingExperiment != null
						&& loadLifecycle.currentlyLoadingExperiment != exp) {
					Logger.info(
							"Cancelling load for experiment [" + loadLifecycle.currentlyLoadingIndex + "] - new experiment selected");
					if (loadLifecycle.currentlyLoadingExperiment != null) {
						loadLifecycle.currentlyLoadingExperiment.setLoading(false);
					}
					loadLifecycle.currentlyLoadingExperiment = null;
					loadLifecycle.currentlyLoadingIndex = -1;
				}
				openPipeline.openSelectedExperiment(exp);
			}
		} else if (e.getStateChange() == ItemEvent.DESELECTED) {
			Experiment exp = (Experiment) e.getItem();
			if (exp != null)
				closeViewsForCurrentExperiment(exp);
			else
				Logger.warn("experiment = null");
		}
	}

	void closeAllExperiments() {
		closeCurrentExperiment();
		parent0.expListComboLazy.removeAllItems();
		parent0.paneExperiment.tabFilter.clearAllCheckBoxes();
		parent0.paneExperiment.tabFilter.filterExpList.removeAllItems();
		parent0.paneExperiment.tabInfos.clearCombos();
		filteredCheck.setSelected(false);
		experimentMetadataList.clear();
		if (parent0.descriptorIndex != null)
			parent0.descriptorIndex.clear();
	}

	public void closeViewsForCurrentExperiment(Experiment exp) {
		closePipeline.closeViewsForCurrentExperiment(exp);
	}

	public void closeCurrentExperiment() {
		if (parent0.expListComboLazy.getSelectedIndex() < 0)
			return;
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		if (exp != null)
			closeViewsForCurrentExperiment(exp);
	}

	void updateBrowseInterface() {
		int isel = parent0.expListComboLazy.getSelectedIndex();
		boolean flag1 = (isel == 0 ? false : true);
		boolean flag2 = (isel == (parent0.expListComboLazy.getItemCount() - 1) ? false : true);
		previousButton.setEnabled(flag1);
		nextButton.setEnabled(flag2);
		if (parent0 != null && parent0.paneCages != null) {
			parent0.paneCages.refreshInfosFromCurrentExperiment();
		}
	}

	public String getMemoryUsageInfo() {
		Runtime runtime = Runtime.getRuntime();
		long totalMemory = runtime.totalMemory();
		long freeMemory = runtime.freeMemory();
		long usedMemory = totalMemory - freeMemory;

		return String.format("Memory: %dMB used, %dMB total, %d experiments loaded", usedMemory / 1024 / 1024,
				totalMemory / 1024 / 1024, experimentMetadataList.size());
	}

	private int addExperimentFrom3NamesAnd2Lists(ExperimentDirectories eDAF) {
		Experiment exp = new Experiment(eDAF);
		int item = parent0.expListComboLazy.addExperiment(exp);
		return item;
	}
}
