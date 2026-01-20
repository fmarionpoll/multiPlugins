package plugins.fmp.multicafe.dlg.browse;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.swing.ButtonGroup;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;

import icy.gui.frame.IcyFrame;
import icy.gui.util.GuiUtil;
import icy.preferences.XMLPreferences;
import java.beans.PropertyChangeListener;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.ExperimentDirectories;

public class SelectFilesPanel extends JPanel {
	/**
	 * 
	 */
	private static final long serialVersionUID = 4172927636287523049L;
	IcyFrame dialogFrame = null;
	private JComboBox<String> filterCombo = new JComboBox<String>(
			new String[] { "capillarytrack", "multicafe", "roisline", "cam", "grabs", "MCcapillaries", "Experiment" });
	private JButton findButton = new JButton("Select root directory and search...");
	private JButton clearSelectedButton = new JButton("Clear selected");
	private JButton clearAllButton = new JButton("Clear all");
	private JButton addSelectedButton = new JButton("Add selected");
	private JButton addAllButton = new JButton("Add all");
	private JRadioButton rbFile = new JRadioButton("file", true);
	private JRadioButton rbDirectory = new JRadioButton("directory");
	private JList<String> directoriesJList = new JList<String>(new DefaultListModel<String>());
	private XMLPreferences guiPrefs = null;
	List<String> selectedNames = null;

	public void initialize(XMLPreferences guiPrefs, PropertyChangeListener listener, List<String> stringList) {
		this.guiPrefs = guiPrefs;
		addPropertyChangeListener(listener);
		selectedNames = stringList;

		JPanel mainPanel = GuiUtil.generatePanelWithoutBorder();
		dialogFrame = new IcyFrame("Select files", true, true);
		dialogFrame.setLayout(new BorderLayout());
		dialogFrame.add(mainPanel, BorderLayout.CENTER);

		FlowLayout layout1 = new FlowLayout(FlowLayout.LEFT);
		layout1.setVgap(1);
		JPanel topPanel = new JPanel(layout1);
		ButtonGroup bg = new ButtonGroup();
		bg.add(rbFile);
		bg.add(rbDirectory);
		topPanel.add(findButton);
		topPanel.add(filterCombo);
		topPanel.add(rbFile);
		topPanel.add(rbDirectory);
		mainPanel.add(GuiUtil.besidesPanel(topPanel));

		directoriesJList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
		directoriesJList.setLayoutOrientation(JList.VERTICAL);
		directoriesJList.setVisibleRowCount(20);
		JScrollPane scrollPane = new JScrollPane(directoriesJList);
		scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
		mainPanel.add(GuiUtil.besidesPanel(scrollPane));

		mainPanel.add(GuiUtil.besidesPanel(clearSelectedButton, clearAllButton));
		mainPanel.add(GuiUtil.besidesPanel(addSelectedButton, addAllButton));

		filterCombo.setEditable(true);
		filterCombo.setSelectedIndex(6);

		addActionListeners();

		dialogFrame.pack();
		dialogFrame.addToDesktopPane();
		dialogFrame.requestFocus();
		dialogFrame.center();
		dialogFrame.setVisible(true);
	}

	void close() {
		dialogFrame.close();
	}

	void addActionListeners() {
		findButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				String pattern = ((String) filterCombo.getSelectedItem()).toLowerCase();

				// ugly patch to cope with one of the previous versions of multicafe that saved
				// files under an other name
				if (pattern.contains("MCcapillaries"))
					pattern = "MCcapi";
				boolean isFileName = rbFile.isSelected();
				if (pattern.contains("grabs") || pattern.contains("cam"))
					isFileName = false;
				getListofFilesMatchingPattern(pattern, isFileName);
			}
		});

		clearSelectedButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				List<String> selectedItems = directoriesJList.getSelectedValuesList();
				removeListofNamesFromList(selectedItems);
			}
		});

		clearAllButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				((DefaultListModel<String>) directoriesJList.getModel()).removeAllElements();
			}
		});

		addSelectedButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				List<String> selectedItems = directoriesJList.getSelectedValuesList();
				addNamesToSelectedList(selectedItems);
				removeListofNamesFromList(selectedItems);
				firePropertyChange("SELECT1_CLOSED", false, true);
				close();
			}
		});

		addAllButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				List<String> allItems = new ArrayList<String>(directoriesJList.getModel().getSize());
				for (int i = 0; i < directoriesJList.getModel().getSize(); i++) {
					String name = directoriesJList.getModel().getElementAt(i);
					allItems.add(name);
				}
				addNamesToSelectedList(allItems);
				((DefaultListModel<String>) directoriesJList.getModel()).removeAllElements();
				firePropertyChange("SELECT1_CLOSED", false, true);
				close();
			}
		});
	}

	private void removeListofNamesFromList(List<String> selectedItems) {
		for (String oo : selectedItems)
			((DefaultListModel<String>) directoriesJList.getModel()).removeElement(oo);
	}

	private void setPreferencesPath(String pathString) {
		if (guiPrefs != null) {
			guiPrefs.put("lastUsedPath", pathString);
		}
	}

	private String getPreferencesPath() {
		if (guiPrefs != null) {
			return guiPrefs.get("lastUsedPath", "");
		}
		return "";
	}

	private boolean isLegacyExperimentFile(Path path) {
		String fileName = path.getFileName().toString().toLowerCase();
		return fileName.equals("mcexperiment.xml");
	}

	private boolean isNewFormatExperimentFile(Path path) {
		String fileName = path.getFileName().toString().toLowerCase();
		return fileName.equals("experiment.xml");
	}

	private List<Path> deduplicateExperimentFiles(List<Path> files) {
		if (files == null || files.isEmpty()) {
			return files;
		}

		Map<Path, List<Path>> filesByDirectory = files.stream().collect(Collectors.groupingBy(Path::getParent));

		List<Path> deduplicated = new ArrayList<>();
		for (Map.Entry<Path, List<Path>> entry : filesByDirectory.entrySet()) {
			List<Path> dirFiles = entry.getValue();
			List<Path> newFormatFiles = dirFiles.stream().filter(this::isNewFormatExperimentFile)
					.collect(Collectors.toList());
			List<Path> legacyFiles = dirFiles.stream().filter(this::isLegacyExperimentFile)
					.collect(Collectors.toList());

			if (!newFormatFiles.isEmpty() && !legacyFiles.isEmpty()) {
				deduplicated.addAll(newFormatFiles);
			} else {
				deduplicated.addAll(dirFiles);
			}
		}
		deduplicated.sort((p1, p2) -> p1.toString().compareToIgnoreCase(p2.toString()));
		return deduplicated;
	}

	private boolean getListofFilesMatchingFileNamePattern(String pattern, File directory) {
		final String lastUsedPathString = directory.getAbsolutePath();
		Path lastPath = Paths.get(lastUsedPathString);

		List<Path> result = null;
		try (Stream<Path> walk = Files.walk(lastPath)) {
			result = walk.filter(Files::isRegularFile) // is a file
					.filter(p -> p.getFileName().toString().toLowerCase().contains(pattern))
					.collect(Collectors.toList());
		} catch (IOException e) {
			e.printStackTrace();
		}
		if (result != null && pattern.toLowerCase().contains("experiment")) {
			result = deduplicateExperimentFiles(result);
		}
		boolean flag = false;
		if (result != null && result.size() > 0) {
			flag = true;
			for (Path path : result)
				addNameToListIfNew(path.toString());
		}
		return flag;
	}

	private boolean getListofFilesMatchingDirectoryNamePattern(String pattern, File directory) {
		final String lastUsedPathString = directory.getAbsolutePath();
		Path lastPath = Paths.get(lastUsedPathString);

		List<Path> result = null;
		try (Stream<Path> walk = Files.walk(lastPath)) {
			result = walk.filter(Files::isDirectory) // is a directory
					.filter(p -> p.getFileName().toString().contains(pattern)).collect(Collectors.toList());
		} catch (IOException e) {
			e.printStackTrace();
		}

		if (result != null) {
			for (Path path : result) {
				File dir = path.toFile();
				if (!getListofFilesMatchingFileNamePattern("MCexpe", dir)) {
					String experimentName = createEmptyExperiment(path);
					if (experimentName != null)
						addNameToListIfNew(experimentName);
				}
			}
		}
		return (result != null);
	}

	private String createEmptyExperiment(Path path) {
		ExperimentDirectories eADF = new ExperimentDirectories();
		eADF.getDirectoriesFromGrabPath(path.toString());

		if (eADF.cameraImagesList.size() == 0)
			return null;

		Experiment exp = new Experiment(eADF);
		return exp.getExperimentDirectory();
	}

	private void getListofFilesMatchingPattern(String pattern, boolean isFileName) {
		File dir = chooseDirectory(getPreferencesPath());
		if (dir == null)
			return;
		final String lastUsedPathString = dir.getAbsolutePath();
		setPreferencesPath(lastUsedPathString);

		if (isFileName)
			getListofFilesMatchingFileNamePattern(pattern, dir);
		else {
			if (!getListofFilesMatchingDirectoryNamePattern(pattern, dir) && (pattern == "cam"))
				getListofFilesMatchingDirectoryNamePattern("grab", dir);
		}
	}

	private void addNameToListIfNew(String fileName) {
		int ilast = ((DefaultListModel<String>) directoriesJList.getModel()).getSize();
		boolean found = false;
		for (int i = 0; i < ilast; i++) {
			String oo = ((DefaultListModel<String>) directoriesJList.getModel()).getElementAt(i);
			if (oo.equalsIgnoreCase(fileName)) {
				found = true;
				break;
			}
		}
		if (!found)
			((DefaultListModel<String>) directoriesJList.getModel()).addElement(fileName);
	}

	private File chooseDirectory(String rootdirectory) {
		File dummy_selected = null;
		JFileChooser fc = new JFileChooser();
		if (rootdirectory != null)
			fc.setCurrentDirectory(new File(rootdirectory));
		fc.setDialogTitle("Select a root directory...");
		fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		fc.setAcceptAllFileFilterUsed(false);
		if (fc.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
			dummy_selected = fc.getSelectedFile();
		} else {
			System.out.println("SelectFiles:chooseDirectory() No directory selected ");
		}
		return dummy_selected;
	}

	private void addNamesToSelectedList(List<String> stringList) {
		for (String name : stringList) {
			String directoryName = Paths.get(name).getParent().toString();
			if (isDirectoryWithJpg(directoryName))
				selectedNames.add(directoryName);
		}
		Collections.sort(selectedNames);
	}

	private boolean isDirectoryWithJpg(String directoryName) {
		String imageDirectory = ExperimentDirectories.getImagesDirectoryAsParentFromFileName(directoryName);
//		HashSet <String> hSet = Directories.getDirectoriesWithFilesType (imageDirectory, ".jpg");
		File dir = new File(imageDirectory);
		File[] files = dir.listFiles((d, name) -> name.endsWith(".jpg"));
		boolean flag = (files.length > 0);
		return flag;
	}

}
