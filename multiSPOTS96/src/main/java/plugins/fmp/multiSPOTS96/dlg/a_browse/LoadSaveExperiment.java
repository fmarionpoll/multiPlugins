package plugins.fmp.multiSPOTS96.dlg.a_browse;

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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

import icy.gui.frame.progress.ProgressFrame;
import icy.gui.viewer.Viewer;
import icy.sequence.Sequence;
import icy.sequence.SequenceEvent;
import icy.sequence.SequenceEvent.SequenceEventSourceType;
import icy.sequence.SequenceListener;
import plugins.fmp.multiSPOTS96.MultiSPOTS96;
import plugins.fmp.multiSPOTS96.experiment.Experiment;
import plugins.fmp.multiSPOTS96.experiment.ExperimentDirectories;
import plugins.fmp.multiSPOTS96.tools.LazyExperiment;
import plugins.fmp.multiSPOTS96.tools.LazyExperiment.ExperimentMetadata;
import plugins.fmp.multiSPOTS96.tools.JComponents.SequenceNameListRenderer;

public class LoadSaveExperiment extends JPanel implements PropertyChangeListener, ItemListener, SequenceListener {

	private static final long serialVersionUID = -690874563607080412L;
	private static final Logger LOGGER = Logger.getLogger(LoadSaveExperiment.class.getName());

	// Performance constants for metadata-only processing
	private static final int METADATA_BATCH_SIZE = 20; // Process 20 experiments at a time
	private static final int PROGRESS_UPDATE_INTERVAL = 10; // Update progress every 10 experiments

	// UI Components
	private JButton openButton = new JButton("Open...");
	private JButton searchButton = new JButton("Search...");
	private JButton closeButton = new JButton("Close");
	public JCheckBox filteredCheck = new JCheckBox("List filtered");

	// Data structures
	public List<String> selectedNames = new ArrayList<String>();
	private SelectFilesPanel dialogSelect = null;

	// Navigation buttons
	private JButton previousButton = new JButton("<");
	private JButton nextButton = new JButton(">");

	// Parent reference
	private MultiSPOTS96 parent0 = null;

	// Metadata storage - lightweight experiment information
	private List<ExperimentMetadata> experimentMetadataList = new ArrayList<>();
	private volatile boolean isProcessing = false;
	private final AtomicInteger processingCount = new AtomicInteger(0);

	public LoadSaveExperiment() {
	}

	public JPanel initPanel(MultiSPOTS96 parent0) {
		this.parent0 = parent0;
		setLayout(new BorderLayout());
		setPreferredSize(new Dimension(400, 200));

		JPanel group2Panel = initUI();
		defineActionListeners();
		parent0.expListComboLazy.addItemListener(this);

		return group2Panel;
	}

	private JPanel initUI() {
		JPanel group2Panel = new JPanel(new GridLayout(2, 1));
		JPanel navPanel = initNavigationPanel();
		JPanel buttonPanel = initButtonPanel();
		group2Panel.add(navPanel);
		group2Panel.add(buttonPanel);
		return group2Panel;
	}

	private JPanel initNavigationPanel() {
		JPanel navPanel = new JPanel(new BorderLayout());
		SequenceNameListRenderer renderer = new SequenceNameListRenderer();
		parent0.expListComboLazy.setRenderer(renderer);
		int bWidth = 30;
		int height = 20;
		previousButton.setPreferredSize(new Dimension(bWidth, height));
		nextButton.setPreferredSize(new Dimension(bWidth, height));

		navPanel.add(previousButton, BorderLayout.LINE_START);
		navPanel.add(parent0.expListComboLazy, BorderLayout.CENTER);
		navPanel.add(nextButton, BorderLayout.LINE_END);
		return navPanel;
	}

	private JPanel initButtonPanel() {
		JPanel buttonPanel = new JPanel(new BorderLayout());
		FlowLayout layout = new FlowLayout(FlowLayout.LEFT);
		layout.setVgap(1);
		JPanel subPanel = new JPanel(layout);
		subPanel.add(openButton);
		subPanel.add(searchButton);
		subPanel.add(closeButton);
		subPanel.add(filteredCheck);
		buttonPanel.add(subPanel, BorderLayout.LINE_START);
		return buttonPanel;
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
				parent0.dlgExperiment.tabFilter.filterExperimentList(filteredCheck.isSelected());
			}
		});

	}

	@Override
	public void propertyChange(PropertyChangeEvent evt) {
		if (evt.getPropertyName().equals("SELECT1_CLOSED")) {
			if (selectedNames.size() < 1) {
				return;
			}

			if (isProcessing) {
				LOGGER.warning("File processing already in progress, ignoring new request");
				return;
			}

			processSelectedFilesMetadataOnly();
		}
	}

	private void processSelectedFilesMetadataOnly() {
		isProcessing = true;
		processingCount.set(0);
		experimentMetadataList.clear();

		ProgressFrame progressFrame = new ProgressFrame("Processing Experiment Metadata");
		progressFrame.setMessage("Scanning " + selectedNames.size() + " experiment directories...");

		SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {
			@Override
			protected Void doInBackground() throws Exception {
				processMetadataOnly(progressFrame);
				return null;
			}

			@Override
			protected void done() {
				isProcessing = false;
				progressFrame.close();
				SwingUtilities.invokeLater(() -> {
					updateBrowseInterface();
				});
			}
		};

		worker.execute();
	}

	private void processMetadataOnly(ProgressFrame progressFrame) {
		final String subDir = parent0.expListComboLazy.stringExpBinSubDirectory;
		final int totalFiles = selectedNames.size();

		try {
			// Process files in batches for metadata only
			for (int i = 0; i < totalFiles; i += METADATA_BATCH_SIZE) {
				int endIndex = Math.min(i + METADATA_BATCH_SIZE, totalFiles);

				// Update progress
				final int currentBatch = i;
				final int currentEndIndex = endIndex;
				SwingUtilities.invokeLater(() -> {
					progressFrame.setMessage(String.format("Scanning experiments %d-%d of %d", currentBatch + 1,
							currentEndIndex, totalFiles));
					progressFrame.setPosition((double) currentBatch / totalFiles);
				});

				// Process batch for metadata only
				for (int j = i; j < endIndex; j++) {
					final String fileName = selectedNames.get(j);
					processSingleFileMetadataOnly(fileName, subDir);
					processingCount.incrementAndGet();

					// Update progress periodically
					if (j % PROGRESS_UPDATE_INTERVAL == 0) {
						final int currentProgress = j;
						SwingUtilities.invokeLater(() -> {
							progressFrame.setMessage(String.format("Found %d experiments...", currentProgress + 1));
						});
					}

					// Minimal delay to prevent UI freezing
					try {
						Thread.sleep(1); // Very small delay
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
						break;
					}
				}
			}

			// Add metadata to UI
			SwingUtilities.invokeLater(() -> {
				addMetadataToUI();
			});

			// Clear selected names after processing
			selectedNames.clear();

		} catch (Exception e) {
			LOGGER.severe("Error processing experiment metadata: " + e.getMessage());
			SwingUtilities.invokeLater(() -> {
				progressFrame.setMessage("Error: " + e.getMessage());
			});
		}
	}

	private void processSingleFileMetadataOnly(String fileName, String subDir) {
		try {
			// Create lightweight ExperimentDirectories for metadata scanning only
			ExperimentDirectories expDirectories = new ExperimentDirectories();

			// Only check if the experiment directory exists and is valid
			if (expDirectories.getDirectoriesFromExptPath(subDir, fileName)) {
				String camDataImagesDirectory = expDirectories.getCameraImagesDirectory();
				String resultsDirectory = expDirectories.getResultsDirectory();
				ExperimentMetadata metadata = new ExperimentMetadata(camDataImagesDirectory, resultsDirectory, subDir);
				experimentMetadataList.add(metadata);
			}

		} catch (Exception e) {
			LOGGER.warning("Failed to process metadata for file " + fileName + ": " + e.getMessage());
		}
	}

	private void addMetadataToUI() {
		try {
			List<LazyExperiment> lazyExperiments = new ArrayList<>();
			for (ExperimentMetadata metadata : experimentMetadataList) {
				LazyExperiment lazyExp = new LazyExperiment(metadata);
				lazyExperiments.add(lazyExp);
			}

			parent0.expListComboLazy.addLazyExperimentsBulk(lazyExperiments);
			parent0.dlgExperiment.tabInfos.initCombos();

			// Kick off background descriptor preloading for fast filters/infos
			parent0.descriptorIndex.preloadFromCombo(parent0.expListComboLazy, new Runnable() {
				@Override
				public void run() {
					// Once preloaded, refresh Infos and Filter combos if tabs are visited
					parent0.dlgExperiment.tabInfos.initCombos();
					parent0.dlgExperiment.tabFilter.initCombos();
				}
			});

			// Also generate descriptors files in background for any experiment missing it
			new javax.swing.SwingWorker<Void, Void>() {
				@Override
				protected Void doInBackground() throws Exception {
					for (int i = 0; i < parent0.expListComboLazy.getItemCount(); i++) {
						plugins.fmp.multiSPOTS96.experiment.Experiment exp = parent0.expListComboLazy.getItemAtNoLoad(i);
						String path = plugins.fmp.multiSPOTS96.tools.DescriptorsIO
								.getDescriptorsFullName(exp.getResultsDirectory());
						java.io.File f = new java.io.File(path);
						if (!f.exists()) {
							plugins.fmp.multiSPOTS96.tools.DescriptorsIO.buildFromExperiment(exp);
						}
					}
					return null;
				}
			}.execute();

		} catch (Exception e) {
			LOGGER.warning("Error adding metadata to UI: " + e.getMessage());
		}
	}

	public boolean openSelectedExperiment(Experiment exp) {
		ProgressFrame progressFrame = new ProgressFrame("Load Experiment Data");

		try {
			// If it's a LazyExperiment, load the data first
			if (exp instanceof LazyExperiment) {
				progressFrame.setMessage("Loading experiment data...");
				((LazyExperiment) exp).loadIfNeeded();
			}

			progressFrame.setMessage("Loading experiment data...");
			exp.load_MS96_experiment();

			progressFrame.setMessage("Loading images...");
			List<String> imagesList = ExperimentDirectories.getImagesListFromPathV2(exp.seqCamData.getImagesDirectory(),
					"jpg");
			exp.seqCamData.loadImageList(imagesList);
			exp.seqCamData.getSequence().addListener(this);
			if (exp.seqCamData != null) {
				progressFrame.setMessage("Loading cages and spots...");
				exp.load_MS96_cages();
				exp.transferCagesROI_toSequence();
				exp.transferSpotsROI_toSequence();
				exp.load_MS96_spotsMeasures();
				parent0.dlgMeasure.tabCharts.displayChartPanels(exp);

				progressFrame.setMessage("Updating dialogs...");
				parent0.dlgExperiment.updateDialogs(exp);
				parent0.dlgSpots.updateDialogs(exp);
			} else {
				LOGGER.warning("No jpg files found for experiment: " + exp.toString());
				progressFrame.close();
				return false;
			}

			parent0.dlgExperiment.tabInfos.transferPreviousExperimentInfosToDialog(exp, exp);
			progressFrame.close();
			parent0.dlgExperiment.updateViewerForSequenceCam(exp);
			return true;

		} catch (Exception e) {
			LOGGER.severe("Error opening experiment: " + e.getMessage());
			progressFrame.close();
			return false;
		}
	}

	private void handleOpenButton() {
		ExperimentDirectories eDAF = new ExperimentDirectories();
		final String binDirectory = parent0.expListComboLazy.stringExpBinSubDirectory;
		if (eDAF.getDirectoriesFromDialog(binDirectory, null, false)) {
			String camDataImagesDirectory = eDAF.getCameraImagesDirectory();
			String resultsDirectory = eDAF.getResultsDirectory();
			ExperimentMetadata metadata = new ExperimentMetadata(camDataImagesDirectory, resultsDirectory,
					binDirectory);

			LazyExperiment lazyExp = new LazyExperiment(metadata);
			int selectedIndex = parent0.expListComboLazy.addLazyExperiment(lazyExp);
			parent0.dlgExperiment.tabInfos.initCombos();
			parent0.expListComboLazy.setSelectedIndex(selectedIndex);
		}
	}

	private void handleSearchButton() {
		selectedNames = new ArrayList<String>();
		dialogSelect = new SelectFilesPanel();
		dialogSelect.initialize(parent0, selectedNames);
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

	// Other required methods
	@Override
	public void sequenceChanged(SequenceEvent sequenceEvent) {
		if (sequenceEvent.getSourceType() == SequenceEventSourceType.SEQUENCE_DATA) {
			// Handle sequence changes for currently loaded experiment
			// Note: This will only work for the currently loaded experiment
		}
	}

	@Override
	public void sequenceClosed(Sequence sequence) {
		sequence.removeListener(this);
	}

	@Override
	public void itemStateChanged(ItemEvent e) {
		if (e.getStateChange() == ItemEvent.SELECTED) {
			final Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
			if (exp != null) {
				openSelectedExperiment(exp);
			}
		} else if (e.getStateChange() == ItemEvent.DESELECTED) {
			Experiment exp = (Experiment) e.getItem();
			if (exp != null)
				closeViewsForCurrentExperiment(exp);
			else
				System.out.println("experiment = null");
		}
	}

	public void closeViewsForCurrentExperiment(Experiment exp) {
		if (exp != null) {
			if (exp.seqCamData != null) {
				// TODO??? Avoid auto-saving analysis when closing; saving should be explicit
				exp.save_MS96_experiment();

				if (exp.seqCamData.getSequence() != null) {
					Viewer v = exp.seqCamData.getSequence().getFirstViewer();
					if (v != null)
						v.close();
				}
			}
			exp.closeSequences();
		}
	}

	public void closeCurrentExperiment() {
		if (parent0.expListComboLazy.getSelectedIndex() < 0)
			return;
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		if (exp != null)
			closeViewsForCurrentExperiment(exp);
	}

	public void closeAllExperiments() {
		closeCurrentExperiment();
		parent0.expListComboLazy.removeAllItems();
		parent0.dlgExperiment.tabFilter.clearAllCheckBoxes();
		parent0.dlgExperiment.tabFilter.filterExpList.removeAllItems();
		parent0.dlgExperiment.tabInfos.clearCombos();
		filteredCheck.setSelected(false);
		experimentMetadataList.clear();
		if (parent0.descriptorIndex != null)
			parent0.descriptorIndex.clear();
	}

	void updateBrowseInterface() {
		int isel = parent0.expListComboLazy.getSelectedIndex();
		boolean flag1 = (isel == 0 ? false : true);
		boolean flag2 = (isel == (parent0.expListComboLazy.getItemCount() - 1) ? false : true);
		previousButton.setEnabled(flag1);
		nextButton.setEnabled(flag2);
	}

	/**
	 * Gets memory usage statistics for monitoring.
	 * 
	 * @return Memory usage information
	 */
	public String getMemoryUsageInfo() {
		Runtime runtime = Runtime.getRuntime();
		long totalMemory = runtime.totalMemory();
		long freeMemory = runtime.freeMemory();
		long usedMemory = totalMemory - freeMemory;

		return String.format("Memory: %dMB used, %dMB total, %d experiments loaded", usedMemory / 1024 / 1024,
				totalMemory / 1024 / 1024, experimentMetadataList.size());
	}
}
