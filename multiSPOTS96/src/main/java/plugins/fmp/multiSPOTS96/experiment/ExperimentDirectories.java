package plugins.fmp.multiSPOTS96.experiment;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.swing.JComboBox;
import javax.swing.JOptionPane;

import icy.file.FileUtil;
import icy.gui.dialog.LoaderDialog;
import plugins.fmp.multiSPOTS96.tools.Directories;

public class ExperimentDirectories {
	private String cameraImagesDirectory = null;
	private String resultsDirectory = null;
//	private String binSubDirectory = null;
	public List<String> cameraImagesList = null;
//	public List<String> kymosImagesList = null;

//	public String getBinSubDirectory() {
//		return binSubDirectory;
//	}

	public String getResultsDirectory() {
		return resultsDirectory;
	}

	public String getCameraImagesDirectory() {
		return cameraImagesDirectory;
	}

	public static List<String> keepOnlyAcceptedNames_List(List<String> namesList, String[] strExtension) {
		int count = namesList.size();
		List<String> outList = new ArrayList<String>(count);

		for (String name : namesList) {
			for (int i = 0; i < strExtension.length; i++) {
				String ext = strExtension[i].toLowerCase();
				String nameGeneric = FileUtil.getGenericPath(name);
				if (nameGeneric.toLowerCase().endsWith(ext)) {
					outList.add(nameGeneric);
					break;
				}
			}
		}
		Collections.sort(outList);
		return outList;
	}

	private List<String> getImagesListFromPath(String strDirectory) {
		List<String> list = new ArrayList<String>();
		Path pathDir = Paths.get(strDirectory);
		if (Files.exists(pathDir)) {
			try (DirectoryStream<Path> stream = Files.newDirectoryStream(pathDir)) {
				for (Path entry : stream) {
					String toAdd = FileUtil.getGenericPath(entry.toString());
					list.add(toAdd);
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return list;
	}

	public static List<String> getImagesListFromPathV2(String dir, String extension) {
		if (!new File(dir).exists())
			return null;
		Path pDir = Paths.get(dir).toAbsolutePath();
		try (Stream<Path> stream = Files.list(pDir)) {
			return stream.filter(file -> !Files.isDirectory(file)).filter(s -> s.toString().endsWith(extension))
					.map(Path::toString).collect(Collectors.toList());
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}

	public List<String> getImagesListFromDialog(String strPath) {
		List<String> list = new ArrayList<String>();
		LoaderDialog dialog = new LoaderDialog(false);
		if (strPath != null)
			dialog.setCurrentDirectory(new File(strPath));
		File[] selectedFiles = dialog.getSelectedFiles();
		if (selectedFiles.length == 0)
			return null;

		// TODO check strPath and provide a way to skip the dialog part
		String strDirectory = Directories.getDirectoryFromName(selectedFiles[0].toString());
		if (strDirectory != null) {
			if (selectedFiles.length == 1)
				list = getImagesListFromPath(strDirectory);
		}
		return list;
	}

	public boolean checkCameraImagesList() {
		boolean isOK = false;
		if (!(cameraImagesList == null)) {
			boolean imageFound = false;
			String jpg = "jpg";
			String grabs = "grabs";
			String grabsDirectory = null;
			for (String name : cameraImagesList) {
				if (name.toLowerCase().endsWith(jpg)) {
					imageFound = true;
					break;
				}
				if (name.toLowerCase().endsWith(grabs))
					grabsDirectory = name;
			}
			if (imageFound) {
				String[] ext = { "jpg" };
				cameraImagesList = keepOnlyAcceptedNames_List(cameraImagesList, ext);
				isOK = true;
			} else if (grabsDirectory != null) {
				cameraImagesList = getImagesListFromPath(grabsDirectory);
				isOK = checkCameraImagesList();
			}
		}
		return isOK;
	}

	public boolean getDirectoriesFromDialog(String expListBinSubDirectory, String rootDirectory,
			boolean createResults) {
		cameraImagesList = getImagesListFromDialog(rootDirectory);
		if (!checkCameraImagesList())
			return false;
		cameraImagesDirectory = Directories.getDirectoryFromName(cameraImagesList.get(0));
		resultsDirectory = getResultsDirectoryDialog(cameraImagesDirectory, Experiment.RESULTS, createResults);

//		binSubDirectory = getBinSubDirectoryFromTIFFLocation(expListBinSubDirectory, resultsDirectory);
//		String kymosDir = resultsDirectory + File.separator + this.binSubDirectory;
//		kymosImagesList = ExperimentDirectories.getImagesListFromPathV2(kymosDir, "tiff");
		return true;
	}

	public boolean getDirectoriesFromExptPath(String expListBinSubDirectory, String exptDirectory) {
		cameraImagesDirectory = getImagesDirectoryAsParentFromFileName(exptDirectory);
		cameraImagesList = ExperimentDirectories.getImagesListFromPathV2(cameraImagesDirectory, "jpg");
		resultsDirectory = getResultsDirectory(cameraImagesDirectory, exptDirectory);

//		binSubDirectory = getBinSubDirectoryFromTIFFLocation(expListBinSubDirectory, resultsDirectory);
//		String kymosDir = resultsDirectory + File.separator + this.binSubDirectory;
//		kymosImagesList = ExperimentDirectories.getImagesListFromPathV2(kymosDir, "tiff");
		return true;
	}

	public boolean getDirectoriesFromGrabPath(String grabsDirectory) {
		this.cameraImagesList = ExperimentDirectories.getImagesListFromPathV2(grabsDirectory, "jpg");
		this.cameraImagesDirectory = grabsDirectory;

		this.resultsDirectory = getResultsDirectory(cameraImagesDirectory, Experiment.RESULTS);
//		this.binSubDirectory = getBinSubDirectoryFromTIFFLocation(null, resultsDirectory);

//		String kymosDir = resultsDirectory + File.separator + this.binSubDirectory;
//		this.kymosImagesList = ExperimentDirectories.getImagesListFromPathV2(kymosDir, "tiff");
		// TODO wrong if any bin
		return true;
	}

//	private String getBinSubDirectoryFromTIFFLocation(String expListBinSubDirectory, String resultsDirectory) {
//		List<String> expList = Directories.getSortedListOfSubDirectoriesWithTIFF(resultsDirectory);
//		String binDirectory = expListBinSubDirectory;
//		if (binDirectory == null) {
//			if (expList.size() > 1) {
//				if (expListBinSubDirectory == null)
//					binDirectory = selectSubDirDialog(expList, "Select item", Experiment.BIN, false);
//			} else if (expList.size() == 1) {
//				binDirectory = expList.get(0).toLowerCase();
//				if (!binDirectory.contains(Experiment.BIN))
//					binDirectory = Experiment.BIN + "60";
//			} else
//				binDirectory = Experiment.BIN + "60";
//		}
//		move_XML_From_Bin_to_Results(binDirectory, resultsDirectory);
//		return binDirectory;
//	}

	static public String getParentIf(String filename, String filter) {
		if (filename != null && filename.contains(filter))
			filename = Paths.get(filename).getParent().toString();
		return filename;
	}

	static public String getImagesDirectoryAsParentFromFileName(String filename) {
		filename = getParentIf(filename, Experiment.BIN);
		filename = getParentIf(filename, Experiment.RESULTS);
		return filename;
	}

	private String getResultsDirectory(String parentDirectory, String resultsSubDirectory) {
		resultsSubDirectory = getParentIf(resultsSubDirectory, Experiment.BIN);

		if (!resultsSubDirectory.contains(Experiment.RESULTS) || !resultsSubDirectory.contains(parentDirectory))
			resultsSubDirectory = parentDirectory + File.separator + Experiment.RESULTS;
		return resultsSubDirectory;
	}

	private String getResultsDirectoryDialog(String parentDirectory, String filter, boolean createResults) {
		List<String> expList = Directories.fetchSubDirectoriesMatchingFilter(parentDirectory, filter);
		expList = Directories.reduceFullNameToLastDirectory(expList);
		String name = null;
		if (createResults || expList.size() > 1) {
			name = selectSubDirDialog(expList, "Select item or type " + Experiment.RESULTS + "xxx", Experiment.RESULTS,
					true);
		} else if (expList.size() == 1)
			name = expList.get(0);
		else
			name = filter;
		return parentDirectory + File.separator + name;
	}

//	private void move_XML_From_Bin_to_Results(String binSubDirectory, String resultsDirectory) {
//		String binDirectory = resultsDirectory + File.separator + binSubDirectory;
//		moveAndRename("MCcapi.xml", binDirectory, "MCcapillaries.xml", resultsDirectory);
//		moveAndRename("MCexpe.xml", binDirectory, "MCexperiment.xml", resultsDirectory);
//		moveAndRename("MCdros.xml", binDirectory, "MCdrosotrack.xml", resultsDirectory);
//	}

	static void moveAndRename(String oldFileName, String oldDirectory, String newFileName, String newDirectory) {
		String oldFilePathString = oldDirectory + File.separator + oldFileName;
		File oldFile = new File(oldFilePathString);
		if (!oldFile.exists() || oldFile.isDirectory())
			return;
		Path oldFilePath = Paths.get(oldFilePathString);

		String newFilePathString = newDirectory + File.separator + newFileName;
		Path newFilePath = Paths.get(newFilePathString);
		if (Files.exists(newFilePath))
			newFilePath = Paths.get(newDirectory + File.separator + oldFileName);

		try {
			Files.move(oldFilePath, newFilePath);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private String selectSubDirDialog(List<String> expList, String title, String type, boolean editable) {
		Object[] array = expList.toArray();
		JComboBox<Object> jcb = new JComboBox<Object>(array);
		jcb.setEditable(editable);
		JOptionPane.showMessageDialog(null, jcb, title, JOptionPane.QUESTION_MESSAGE);
		return (String) jcb.getSelectedItem();
	}

}
