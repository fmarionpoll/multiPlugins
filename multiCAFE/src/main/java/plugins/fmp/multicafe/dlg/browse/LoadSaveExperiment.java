package plugins.fmp.multicafe.dlg.browse;

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
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

import icy.gui.frame.progress.ProgressFrame;
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
import plugins.fmp.multitools.experiment.cage.Cage;
import plugins.fmp.multitools.tools.DescriptorsIO;
import plugins.fmp.multitools.tools.Directories;
import plugins.fmp.multitools.tools.Logger;
import plugins.fmp.multitools.tools.JComponents.SequenceNameListRenderer;

public class LoadSaveExperiment extends JPanel implements PropertyChangeListener, ItemListener, SequenceListener {
	/**
	 * 
	 */
	private static final long serialVersionUID = -690874563607080412L;

	// Performance constants for metadata-only processing
	private static final int METADATA_BATCH_SIZE = 20; // Process 20 experiments at a time
	private static final int PROGRESS_UPDATE_INTERVAL = 10; // Update progress every 10 experiments

	// UI Components
	private JButton openButton = new JButton("Open...");
	private JButton createButton = new JButton("Create...");
	private JButton searchButton = new JButton("Search...");
	private JButton closeButton = new JButton("Close");
	public JCheckBox filteredCheck = new JCheckBox("List filtered");

	// Data structures
	public List<String> selectedNames = new ArrayList<String>();
	private SelectFilesPanel dialogSelect = null;

	// Navigation buttons
	private JButton previousButton = new JButton("<");
	private JButton nextButton = new JButton(">");

	// Metadata storage - lightweight experiment information
	private List<ExperimentMetadata> experimentMetadataList = new ArrayList<>();
	private volatile boolean isProcessing = false;
	private final AtomicInteger processingCount = new AtomicInteger(0);

	// Track currently loading experiment to prevent concurrent loads
	private volatile Experiment currentlyLoadingExperiment = null;
	private volatile int currentlyLoadingIndex = -1;

	// Parent reference
	private MultiCAFE parent0 = null;

	// -----------------------------------------
	public LoadSaveExperiment() {
	}

	public JPanel initPanel(MultiCAFE parent0) {
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
		subPanel.add(createButton);
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
			if (selectedNames.size() < 1) {
				return;
			}

			if (isProcessing) {
				Logger.warn("File processing already in progress, ignoring new request");
				return;
			}

			processSelectedFilesMetadataOnly();
		}
	}

	private void processSelectedFilesMetadataOnly() {
		isProcessing = true;
		processingCount.set(0);
		experimentMetadataList.clear();
		long startTime = System.nanoTime();

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
				long endTime = System.nanoTime();
				System.out.println(
						"LoadExperiment: processSelectedFilesMetadataOnly took " + (endTime - startTime) / 1e6 + " ms");
				SwingUtilities.invokeLater(() -> {
					updateBrowseInterface();
				});
			}
		};

		worker.execute();
	}

	private void processMetadataOnly(ProgressFrame progressFrame) {
		final String subDir = parent0.expListComboLazy.expListBinSubDirectory;
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
					final int fileIndex = j;
					processSingleFileMetadataOnly(fileName, subDir, fileIndex);
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
			Logger.error("Error processing experiment metadata: " + e.getMessage(), e);
			SwingUtilities.invokeLater(() -> {
				progressFrame.setMessage("Error: " + e.getMessage());
			});
		}
	}

	private void processSingleFileMetadataOnly(String fileName, String subDir, int fileIndex) {
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
			Logger.warn("Failed to process metadata for file [" + fileIndex + "] " + fileName + ": " + e.getMessage(),
					e);
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
			parent0.paneExperiment.tabInfos.initCombos();

			// Kick off background descriptor preloading for fast filters/infos
			parent0.descriptorIndex.preloadFromCombo(parent0.expListComboLazy, new Runnable() {
				@Override
				public void run() {
					// Once preloaded, refresh Infos and Filter combos if tabs are visited
					parent0.paneExperiment.tabInfos.initCombos();
					parent0.paneExperiment.tabFilter.initCombos();
				}
			});

			// Also generate descriptors files in background for any experiment missing it
			new SwingWorker<Void, Void>() {
				@Override
				protected Void doInBackground() throws Exception {
					for (int i = 0; i < parent0.expListComboLazy.getItemCount(); i++) {
						Experiment exp = parent0.expListComboLazy.getItemAtNoLoad(i);
						String path = DescriptorsIO.getDescriptorsFullName(exp.getResultsDirectory());
						java.io.File f = new java.io.File(path);
						if (!f.exists()) {
							DescriptorsIO.buildFromExperiment(exp);
						}
					}
					return null;
				}
			}.execute();

		} catch (Exception e) {
			Logger.warn("Error adding metadata to UI: " + e.getMessage(), e);
		}
	}

	/**
	 * Counts the number of cages with fly positions.
	 * 
	 * @param exp The experiment to count cages for
	 * @return The count of cages with fly positions
	 */
	private int countCagesWithFlyPositions(Experiment exp) {
		int count = 0;
		for (Cage cage : exp.getCages().getCageList()) {
			if (cage.flyPositions != null && cage.flyPositions.flyPositionList != null
					&& !cage.flyPositions.flyPositionList.isEmpty()) {
				count++;
			}
		}
		return count;
	}

	/**
	 * Logs cage load completion with timing and statistics.
	 * 
	 * @param exp       The experiment that was loaded
	 * @param expIndex  The index of the experiment
	 * @param startTime The start time in nanoseconds
	 * @param endTime   The end time in nanoseconds
	 */
	private void logCageLoadCompletion(Experiment exp, int expIndex, long startTime, long endTime) {
		int cageCount = exp.getCages().getCageList().size();
		int cagesWithFlyPositions = countCagesWithFlyPositions(exp);
		int totalFlyPositions = 0;
		for (Cage cage : exp.getCages().getCageList()) {
			if (cage.flyPositions != null && cage.flyPositions.flyPositionList != null
					&& !cage.flyPositions.flyPositionList.isEmpty()) {
				totalFlyPositions += cage.flyPositions.flyPositionList.size();
			}
		}
		System.out.println("LoadExperiment: openSelectedExperiment [" + expIndex + "] load completed, total time: "
				+ (endTime - startTime) / 1e6 + " ms, cages: " + cageCount + ", with fly positions: "
				+ cagesWithFlyPositions + ", total fly positions: " + totalFlyPositions);
	}

	/**
	 * Validates that the experiment is still selected and not being saved.
	 * 
	 * @param exp           The experiment to validate
	 * @param expIndex      The index of the experiment
	 * @param progressFrame The progress frame to close if validation fails
	 * @return true if validation passes, false otherwise
	 */
	private boolean validateExperimentSelection(Experiment exp, int expIndex, ProgressFrame progressFrame) {
		if (parent0.expListComboLazy.getSelectedItem() != exp) {
			Logger.info("Skipping load for experiment [" + expIndex + "] - no longer selected");
			return false;
		}

		if (exp.isSaving()) {
			Logger.warn("Cannot load experiment [" + expIndex + "] - save operation in progress: " + exp.toString());
			progressFrame.close();
			return false;
		}

		return true;
	}

	/**
	 * Initializes the experiment load by setting flags and tracking state.
	 * 
	 * @param exp      The experiment being loaded
	 * @param expIndex The index of the experiment
	 */
	private void initializeExperimentLoad(Experiment exp, int expIndex) {
		exp.setLoading(true);
		currentlyLoadingExperiment = exp;
		currentlyLoadingIndex = expIndex;
	}

	/**
	 * Aborts the experiment load and cleans up state.
	 * 
	 * @param exp           The experiment being aborted
	 * @param expIndex      The index of the experiment
	 * @param progressFrame The progress frame to close
	 * @param reason        The reason for aborting (for logging)
	 * @return false to indicate load was aborted
	 */
	private boolean abortExperimentLoad(Experiment exp, int expIndex, ProgressFrame progressFrame, String reason) {
		Logger.info("Aborting load for experiment [" + expIndex + "] - " + reason);
		exp.setLoading(false);
		if (currentlyLoadingExperiment == exp) {
			currentlyLoadingExperiment = null;
			currentlyLoadingIndex = -1;
		}
		progressFrame.close();
		return false;
	}

	/**
	 * Loads experiment metadata (lazy or XML).
	 * 
	 * @param exp           The experiment to load metadata for
	 * @param expIndex      The index of the experiment
	 * @param progressFrame The progress frame to update
	 * @return true if successful, false if aborted
	 */
	private boolean loadExperimentMetadata(Experiment exp, int expIndex, ProgressFrame progressFrame) {
		if (exp instanceof LazyExperiment) {
			progressFrame.setMessage("Loading experiment metadata...");
			((LazyExperiment) exp).loadIfNeeded();
		} else {
			exp.xmlLoad_MCExperiment();
		}

		if (parent0.expListComboLazy.getSelectedItem() != exp) {
			return abortExperimentLoad(exp, expIndex, progressFrame, "different experiment selected after lazy load");
		}

		return true;
	}

	/**
	 * Loads experiment images and updates the viewer.
	 * 
	 * @param exp           The experiment to load images for
	 * @param expIndex      The index of the experiment
	 * @param progressFrame The progress frame to update
	 * @return true if successful, false if aborted or failed
	 */
	private boolean loadExperimentImages(Experiment exp, int expIndex, ProgressFrame progressFrame) {
		progressFrame.setMessage("Load image");
		exp.getSeqCamData().loadImages();

		// Fix: Recalculate nTotalFrames from actual image count if there's a mismatch
		// This handles the case where nFrames=1 was incorrectly saved to Experiment.xml
		plugins.fmp.multitools.experiment.sequence.ImageLoader imgLoader = exp.getSeqCamData().getImageLoader();
		int actualImageCount = imgLoader.getImagesCount();
		int loadedNFrames = imgLoader.getNTotalFrames();
		if (actualImageCount > 0 && loadedNFrames > 0 && actualImageCount != loadedNFrames) {
			long frameFirst = imgLoader.getAbsoluteIndexFirstImage();
			long nImages = actualImageCount + frameFirst;
			imgLoader.setFixedNumberOfImages(nImages);
			imgLoader.setNTotalFrames(actualImageCount);
		}

		if (parent0.expListComboLazy.getSelectedItem() != exp) {
			return abortExperimentLoad(exp, expIndex, progressFrame,
					"different experiment selected after loading images");
		}

		parent0.paneExperiment.updateViewerForSequenceCam(exp);

		// Apply checkbox state to capillary ROIs now that viewer is created
		// synchronously
		if (exp.getSeqCamData() != null && exp.getSeqCamData().getSequence() != null
				&& exp.getSeqCamData().getSequence().getFirstViewer() != null && exp.getCapillaries() != null
				&& exp.getCapillaries().getList().size() > 0) {
			boolean shouldBeVisible = parent0.paneExperiment.tabOptions.viewCapillariesCheckBox.isSelected();
			parent0.paneExperiment.tabOptions.displayROIsCategory(shouldBeVisible, "line");
		}

		if (parent0.expListComboLazy.getSelectedItem() != exp) {
			return abortExperimentLoad(exp, expIndex, progressFrame, "different experiment selected during load");
		}

		if (exp.getSeqCamData() == null) {
			Logger.error("LoadSaveExperiments:openSelectedExperiment() [" + expIndex
					+ "] Error: no jpg files found for this experiment\n");
			progressFrame.close();
			exp.setLoading(false);
			if (currentlyLoadingExperiment == exp) {
				currentlyLoadingExperiment = null;
				currentlyLoadingIndex = -1;
			}
			return false;
		}

		if (exp.getSeqCamData().getSequence() != null) {
			exp.getSeqCamData().getSequence().addListener(this);
		}

		return true;
	}

	/**
	 * Loads capillaries data.
	 * 
	 * @param exp           The experiment to load capillaries for
	 * @param progressFrame The progress frame to update
	 */
	private void loadCapillariesData(Experiment exp, ProgressFrame progressFrame) {
		progressFrame.setMessage("Load capillaries");
		exp.loadCamDataCapillaries();
	}

	/**
	 * Loads kymographs and capillary measures.
	 * 
	 * @param exp            The experiment to load kymographs for
	 * @param selectedBinDir The selected bin directory
	 * @param progressFrame  The progress frame to update
	 */
	private void loadKymographsAndMeasures(Experiment exp, String selectedBinDir, ProgressFrame progressFrame) {
		if (exp.getCapillaries() == null || exp.getCapillaries().getList().size() == 0) {
			return;
		}

		progressFrame.setMessage("Load kymographs");
		if (selectedBinDir != null) {
			parent0.paneKymos.tabLoadSave.loadDefaultKymos(exp);
		}

		progressFrame.setMessage("Load capillary measures");
		if (selectedBinDir != null && exp.getBinSubDirectory() != null) {
			String binFullDir = exp.getKymosBinFullDirectory();
			if (binFullDir != null) {
				exp.load_capillaries_description_and_measures();

				// Populate combo box AFTER measures are loaded to ensure capillaries are fully
				// populated
				if (exp.getSeqKymos() != null && exp.getCapillaries() != null
						&& exp.getCapillaries().getList().size() > 0) {
					parent0.paneKymos.tabDisplay.transferCapillaryNamesToComboBox(exp);

					// Now display the kymograph viewer after combo box is populated
					// Use invokeLater to ensure combo box population completes first
					SwingUtilities.invokeLater(new Runnable() {
						public void run() {
							parent0.paneKymos.tabDisplay.displayUpdateOnSwingThread();
						}
					});
				}
			}
		}

		if (exp.getSeqKymos() != null && exp.getSeqKymos().getSequence() != null) {
			exp.getSeqKymos().getSequence().addListener(this);
		}
	}

	/**
	 * Displays graphs if enabled.
	 * 
	 * @param exp The experiment to display graphs for
	 */
	private void displayGraphsIfEnabled(Experiment exp) {
		if (parent0.paneExperiment.tabOptions.graphsCheckBox.isSelected()) {
			parent0.paneLevels.tabGraphs.displayChartPanels(exp);
		}
	}

	/**
	 * Prepares the cage measures file by moving it if needed.
	 * 
	 * @param exp The experiment to prepare the file for
	 */
	private void prepareCageMeasuresFile(Experiment exp) {
		String pathToMeasures = exp.getResultsDirectory() + java.io.File.separator + "CagesMeasures.csv";
		java.io.File f = new java.io.File(pathToMeasures);
		if (!f.exists()) {
			String pathToOldCsv = exp.getKymosBinFullDirectory() + java.io.File.separator + "CagesMeasures.csv";
			java.io.File fileToMove = new java.io.File(pathToOldCsv);
			if (fileToMove.exists()) {
				fileToMove.renameTo(f);
			}
		}
	}

	boolean openSelectedExperiment(Experiment exp) {
		if (exp == null)
			return false;

		final long startTime = System.nanoTime();
		int expIndex = parent0.expListComboLazy.getSelectedIndex();

		Logger.info("LoadSaveExperiment:openSelectedExperiment() START - exp="
				+ (exp != null ? exp.getResultsDirectory() : "null") + ", isLazy=" + (exp instanceof LazyExperiment)
				+ ", capillaries.count="
				+ (exp != null && exp.getCapillaries() != null ? exp.getCapillaries().getList().size() : "N/A"));

		ProgressFrame progressFrame = new ProgressFrame("Load Experiment Data");

		if (!validateExperimentSelection(exp, expIndex, progressFrame)) {
			return false;
		}

		initializeExperimentLoad(exp, expIndex);

		try {
			if (!validateExperimentSelection(exp, expIndex, progressFrame)) {
				return abortExperimentLoad(exp, expIndex, progressFrame, "different experiment selected");
			}

			if (!loadExperimentMetadata(exp, expIndex, progressFrame)) {
				return false;
			}

			if (!loadExperimentImages(exp, expIndex, progressFrame)) {
				return false;
			}

			loadCapillariesData(exp, progressFrame);

			String selectedBinDir = selectBinDirectory(exp);
			if (selectedBinDir != null) {
				exp.setBinSubDirectory(selectedBinDir);
				parent0.expListComboLazy.expListBinSubDirectory = selectedBinDir;
			}

			loadKymographsAndMeasures(exp, selectedBinDir, progressFrame);

			if (!validateExperimentSelection(exp, expIndex, progressFrame)) {
				return abortExperimentLoad(exp, expIndex, progressFrame,
						"different experiment selected before cage load");
			}

			prepareCageMeasuresFile(exp);

			// Load cages synchronously (this method handles ROI transfer and preserves
			// capillaries)
			progressFrame.setMessage("Load cage measures...");
			boolean cagesLoaded = exp.load_cages_description_and_measures();

			if (!cagesLoaded) {
				Logger.warn("Failed to load cages for experiment [" + expIndex + "]");
			}

			exp.updateROIsAt(0);

			// Display graphs AFTER cages are loaded (dispatchCapillariesToCages needs cages
			// to be loaded)
			displayGraphsIfEnabled(exp);

			progressFrame.setMessage("Load data: update dialogs");

			parent0.paneExperiment.updateDialogs(exp);
			parent0.paneKymos.updateDialogs(exp);
			parent0.paneCapillaries.updateDialogs(exp);

			parent0.paneExperiment.tabInfos.transferPreviousExperimentInfosToDialog(exp, exp);

			long endTime = System.nanoTime();
			logCageLoadCompletion(exp, expIndex, startTime, endTime);

			exp.setLoading(false);
			if (currentlyLoadingExperiment == exp) {
				currentlyLoadingExperiment = null;
				currentlyLoadingIndex = -1;
			}

			progressFrame.close();
			return true;
		} catch (Exception e) {
			Logger.error("Error opening experiment [" + expIndex + "]: "
					+ (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()), e);
			Logger.error("Exception details [" + expIndex + "]: " + e.toString(), e);
			progressFrame.close();

			// Clear loading flag - loading failed but we're done trying
			exp.setLoading(false);
			if (currentlyLoadingExperiment == exp) {
				currentlyLoadingExperiment = null;
				currentlyLoadingIndex = -1;
			}

			long endTime = System.nanoTime();
			System.out.println("LoadExperiment: openSelecteExperiment [" + expIndex + "] failed, took "
					+ (endTime - startTime) / 1e6 + " ms");
			return false;
		}
	}

	/**
	 * Detects and selects bin directory (bin_60, bin_xx) according to rules: - If
	 * loading single experiment and multiple bins exist, ask user - If loading
	 * series and not first file, keep previously selected bin - If directory not
	 * found, ask user what to do
	 */
	private String selectBinDirectory(Experiment exp) {
		String resultsDir = exp.getResultsDirectory();
		if (resultsDir == null) {
			return null;
		}

		// Check if results directory exists on disk
		File resultsDirFile = new File(resultsDir);
		if (!resultsDirFile.exists() || !resultsDirFile.isDirectory()) {
			return null;
		}

		List<String> binDirs = Directories.getSortedListOfSubDirectoriesWithTIFF(resultsDir);
		if (binDirs == null || binDirs.isEmpty()) {
			return null;
//			// No bin directories found - ask user what to do
//			int response = JOptionPane.showConfirmDialog(null,
//					"No bin directories found in " + resultsDir + "\nDo you want to continue without kymographs?",
//					"No Bin Directory Found", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
//			if (response == JOptionPane.YES_OPTION) {
//				return null;
//			} else {
//				// User can manually select a directory
//				return selectBinDirectoryDialog(binDirs);
//			}
		}

		// Check if we're loading a single experiment or a series
		boolean isFirstExperiment = (parent0.expListComboLazy.getSelectedIndex() == 0);
		boolean isSingleExperiment = (parent0.expListComboLazy.getItemCount() == 1);

		// If we have a previously selected bin directory and it exists, use it (for
		// series)
		String previousBinDir = parent0.expListComboLazy.expListBinSubDirectory;
		if (!isFirstExperiment && previousBinDir != null && binDirs.contains(previousBinDir)) {
			return previousBinDir;
		}

		// If single experiment or first in series
		if (isSingleExperiment || isFirstExperiment) {
			if (binDirs.size() > 1) {
				// Multiple bin directories - ask user which one
				return selectBinDirectoryDialog(binDirs);
			} else if (binDirs.size() == 1) {
				// Only one bin directory - use it
				return binDirs.get(0);
			}
		}

		// Default: use first available or ask
		if (binDirs.size() > 0) {
			return binDirs.get(0);
		}

		return null;
	}

	/**
	 * Shows a dialog to let user select a bin directory from the list
	 */
	private String selectBinDirectoryDialog(List<String> binDirs) {
		if (binDirs == null || binDirs.isEmpty()) {
			return null;
		}

		Object[] array = binDirs.toArray();
		JComboBox<Object> jcb = new JComboBox<Object>(array);
		jcb.setEditable(false);
		JOptionPane.showMessageDialog(null, jcb, "Select bin directory", JOptionPane.QUESTION_MESSAGE);
		Object selected = jcb.getSelectedItem();
		return (selected != null) ? selected.toString() : null;
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
		dialogSelect = new SelectFilesPanel();
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

	// ----------------------------

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
				// Cancel any ongoing load for a different experiment
				if (currentlyLoadingExperiment != null && currentlyLoadingExperiment != exp) {
					Logger.info(
							"Cancelling load for experiment [" + currentlyLoadingIndex + "] - new experiment selected");
					// Clear loading flag for the previous experiment
					if (currentlyLoadingExperiment != null) {
						currentlyLoadingExperiment.setLoading(false);
					}
					currentlyLoadingExperiment = null;
					currentlyLoadingIndex = -1;
				}
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
		if (exp != null) {
			// Don't save if loading is still in progress (prevents race condition)
			if (exp.isLoading()) {
				Logger.warn("LoadSaveExperiment: Skipping save for experiment - loading still in progress: "
						+ exp.toString());
				return;
			}

			// Don't start a new save if one is already in progress (prevents concurrent
			// saves)
			if (exp.isSaving()) {
				Logger.warn("LoadSaveExperiment: Skipping save for experiment - save operation already in progress: "
						+ exp.toString());
				return;
			}

			// Set saving flag to prevent concurrent saves and loads
			exp.setSaving(true);

			try {
				// Clean up fly detection ROIs before closing
				if (exp.getSeqCamData() != null && exp.getSeqCamData().getSequence() != null) {
					exp.cleanPreviousDetectedFliesROIs();
				}

				if (exp.getSeqCamData() != null) {
					exp.saveExperimentDescriptors();

					// Save capillaries using new dual-file system (descriptions + measures)
					int capCountBeforeSave = exp.getCapillaries() != null ? exp.getCapillaries().getList().size() : 0;
					Logger.info("LoadSaveExperiment:closeViewsForCurrentExperiment() About to save capillaries - count="
							+ capCountBeforeSave + ", exp=" + exp.getResultsDirectory());
					exp.save_capillaries_description_and_measures();

					// Update cages from sequence before saving
					exp.getCages().transferROIsFromSequence(exp.getSeqCamData());

					// Save cages descriptions synchronously
					exp.getCages().getPersistence().saveCages(exp.getCages(), exp.getResultsDirectory(), exp);

					// Save cage measures to bin directory
					String binDir = exp.getKymosBinFullDirectory();
					if (binDir != null) {
						exp.getCages().getPersistence().saveMeasures(exp.getCages(), binDir);
					}

					// Save spots using new dual-file system
					exp.save_spots_description_and_measures();

					// Save MS96_descriptors.xml (synchronous, but quick)
					if (exp.getSeqCamData() != null) {
						DescriptorsIO.buildFromExperiment(exp);
					}
				}
				// Close sequences after all saves complete
				exp.closeSequences();

			} catch (Exception e) {
				Logger.error("Error in closeViewsForCurrentExperiment: " + e.getMessage(), e);
			} finally {
				// Always clear saving flag, even if save fails
				exp.setSaving(false);
			}
		}
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

	// ------------------------

	private int addExperimentFrom3NamesAnd2Lists(ExperimentDirectories eDAF) {
		Experiment exp = new Experiment(eDAF);
		int item = parent0.expListComboLazy.addExperiment(exp);
		return item;
	}

}
