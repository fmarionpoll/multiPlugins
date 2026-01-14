package plugins.fmp.multiSPOTS.tools.JComponents;

import java.io.File;
import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;
import icy.file.FileUtil;
import icy.gui.dialog.ConfirmDialog;

public class Dialog {
	public static String saveFileAs(String defaultName, String directory, String csExt) {
		// load last preferences for loader
		String csFile = null;
		final JFileChooser fileChooser = new JFileChooser();
		if (directory != null)
			fileChooser.setCurrentDirectory(new File(directory));
		fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
		FileNameExtensionFilter xlsFilter = new FileNameExtensionFilter(csExt + " files", csExt, csExt);
		fileChooser.addChoosableFileFilter(xlsFilter);
		fileChooser.setFileFilter(xlsFilter);
		if (defaultName != null)
			fileChooser.setSelectedFile(new File(defaultName));

		final int returnValue = fileChooser.showSaveDialog(null);
		if (returnValue == JFileChooser.APPROVE_OPTION) {
			File f = fileChooser.getSelectedFile();
			csFile = f.getAbsolutePath();
			int dotOK = csExt.indexOf(".");
			if (dotOK < 0)
				csExt = "." + csExt;
			int extensionOK = csFile.indexOf(csExt);
			if (extensionOK < 0) {
				csFile += csExt;
				f = new File(csFile);
			}

			if (f.exists())
				if (ConfirmDialog.confirm("Overwrite existing file ?"))
					FileUtil.delete(f, true);
				else
					csFile = null;
		}
		return csFile;
	}

	public static String[] selectFiles(String directory, String csExt) {
		final JFileChooser fileChooser = new JFileChooser();
		final String path = directory;
		fileChooser.setCurrentDirectory(new File(path));
		fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
		fileChooser.setMultiSelectionEnabled(true);
		FileNameExtensionFilter csFilter = new FileNameExtensionFilter(csExt + " files", csExt, csExt);
		fileChooser.addChoosableFileFilter(csFilter);
		fileChooser.setFileFilter(csFilter);

		final int returnValue = fileChooser.showDialog(null, "Load");
		String[] liststrings = null;
		if (returnValue == JFileChooser.APPROVE_OPTION) {
			File[] files = fileChooser.getSelectedFiles();
			liststrings = new String[files.length];
			for (int i = 0; i < files.length; i++)
				liststrings[i] = files[i].getAbsolutePath();
		}
		return liststrings;
	}

}
