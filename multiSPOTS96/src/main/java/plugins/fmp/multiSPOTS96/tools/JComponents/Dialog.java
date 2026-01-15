package plugins.fmp.multiSPOTS96.tools.JComponents;

import java.io.File;
import java.util.logging.Logger;

import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;

import icy.file.FileUtil;
import icy.gui.dialog.ConfirmDialog;
import plugins.fmp.multitools.tools.JComponents.exceptions.FileDialogException;

/**
 * Utility class for file dialog operations.
 * Provides standardized file selection dialogs with proper validation and error handling.
 */
public final class Dialog {
	
	private static final Logger logger = Logger.getLogger(Dialog.class.getName());
	
	// Private constructor to prevent instantiation
	private Dialog() {
		throw new UnsupportedOperationException("Utility class cannot be instantiated");
	}
	
	/**
	 * Opens a save file dialog with the specified parameters.
	 * 
	 * @param defaultName The default filename to suggest
	 * @param directory The initial directory to open
	 * @param extension The file extension (without dot)
	 * @return The selected file path, or null if cancelled or error occurred
	 * @throws FileDialogException If dialog operation fails
	 */
	public static String saveFileAs(String defaultName, String directory, String extension) throws FileDialogException {
		validateExtension(extension);
		
		try {
			final JFileChooser fileChooser = createFileChooser(directory, extension, false);
			
			if (defaultName != null && !defaultName.trim().isEmpty()) {
				fileChooser.setSelectedFile(new File(defaultName));
			}

			final int returnValue = fileChooser.showSaveDialog(null);
			if (returnValue == JFileChooser.APPROVE_OPTION) {
				return processSaveSelection(fileChooser.getSelectedFile(), extension);
			}
			return null;
			
		} catch (Exception e) {
			logger.severe("Error in saveFileAs: " + e.getMessage());
			throw new FileDialogException("Failed to open save dialog", "save_file_as", extension, e);
		}
	}

	/**
	 * Opens a file selection dialog for multiple files.
	 * 
	 * @param directory The initial directory to open
	 * @param extension The file extension (without dot)
	 * @return Array of selected file paths, or null if cancelled or error occurred
	 * @throws FileDialogException If dialog operation fails
	 */
	public static String[] selectFiles(String directory, String extension) throws FileDialogException {
		validateExtension(extension);
		
		try {
			final JFileChooser fileChooser = createFileChooser(directory, extension, true);

			final int returnValue = fileChooser.showDialog(null, JComponentConstants.FILE_LOAD_BUTTON_TEXT);
			if (returnValue == JFileChooser.APPROVE_OPTION) {
				return processMultipleSelection(fileChooser.getSelectedFiles());
			}
			return null;
			
		} catch (Exception e) {
			logger.severe("Error in selectFiles: " + e.getMessage());
			throw new FileDialogException("Failed to open file selection dialog", "select_files", extension, e);
		}
	}
	
	/**
	 * Creates and configures a JFileChooser with the specified parameters.
	 * 
	 * @param directory The initial directory
	 * @param extension The file extension
	 * @param multiSelection Whether to allow multiple file selection
	 * @return Configured JFileChooser
	 */
	private static JFileChooser createFileChooser(String directory, String extension, boolean multiSelection) {
		final JFileChooser fileChooser = new JFileChooser();
		
		// Set directory if provided and valid
		if (directory != null && !directory.trim().isEmpty()) {
			File dir = new File(directory);
			if (dir.exists() && dir.isDirectory()) {
				fileChooser.setCurrentDirectory(dir);
			}
		}
		
		fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
		fileChooser.setMultiSelectionEnabled(multiSelection);
		
		// Create and set file filter
		String filterDescription = extension + JComponentConstants.FILES_SUFFIX;
		FileNameExtensionFilter filter = new FileNameExtensionFilter(filterDescription, extension);
		fileChooser.addChoosableFileFilter(filter);
		fileChooser.setFileFilter(filter);
		
		return fileChooser;
	}
	
	/**
	 * Processes the save file selection, ensuring proper extension and handling overwrites.
	 * 
	 * @param selectedFile The file selected by the user
	 * @param extension The required extension
	 * @return The final file path, or null if cancelled
	 * @throws FileDialogException If file processing fails
	 */
	private static String processSaveSelection(File selectedFile, String extension) throws FileDialogException {
		try {
			String filePath = selectedFile.getAbsolutePath();
			String normalizedExtension = normalizeExtension(extension);
			
			// Add extension if not present
			if (!filePath.toLowerCase().endsWith(normalizedExtension.toLowerCase())) {
				filePath += normalizedExtension;
				selectedFile = new File(filePath);
			}

			// Handle file overwrite
			if (selectedFile.exists()) {
				if (ConfirmDialog.confirm(JComponentConstants.FILE_OVERWRITE_CONFIRMATION)) {
					if (!FileUtil.delete(selectedFile, true)) {
						throw new FileDialogException("Failed to delete existing file", 
													 "delete_existing", filePath);
					}
				} else {
					return null; // User cancelled overwrite
				}
			}
			
			return filePath;
			
		} catch (Exception e) {
			throw new FileDialogException("Failed to process save selection", 
										 "process_save", selectedFile.getName(), e);
		}
	}
	
	/**
	 * Processes multiple file selection and returns their paths.
	 * 
	 * @param selectedFiles The files selected by the user
	 * @return Array of file paths
	 */
	private static String[] processMultipleSelection(File[] selectedFiles) {
		if (selectedFiles == null || selectedFiles.length == 0) {
			return new String[0];
		}
		
		String[] filePaths = new String[selectedFiles.length];
		for (int i = 0; i < selectedFiles.length; i++) {
			filePaths[i] = selectedFiles[i].getAbsolutePath();
		}
		return filePaths;
	}
	
	/**
	 * Validates the file extension parameter.
	 * 
	 * @param extension The extension to validate
	 * @throws FileDialogException If extension is invalid
	 */
	private static void validateExtension(String extension) throws FileDialogException {
		if (extension == null || extension.trim().isEmpty()) {
			throw new FileDialogException("File extension cannot be null or empty", 
										 "validate_extension", "null_extension");
		}
	}
	
	/**
	 * Normalizes the extension by ensuring it starts with a dot.
	 * 
	 * @param extension The extension to normalize
	 * @return The normalized extension
	 */
	private static String normalizeExtension(String extension) {
		if (extension.startsWith(JComponentConstants.DOT_PREFIX)) {
			return extension;
		}
		return JComponentConstants.DOT_PREFIX + extension;
	}
}
