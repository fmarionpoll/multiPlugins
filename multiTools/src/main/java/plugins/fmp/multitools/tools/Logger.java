package plugins.fmp.multitools.tools;

import javax.swing.JOptionPane;

import org.slf4j.LoggerFactory;

/**
 * Centralized logging utility for MultiCAFE.
 * Provides logging functionality with user notification for critical errors.
 */
public class Logger {
	private static final org.slf4j.Logger logger = LoggerFactory.getLogger("MultiCAFE");
	
	/**
	 * Logs an error message with optional exception.
	 * Shows user dialog for critical errors.
	 * 
	 * @param message the error message
	 * @param throwable the exception (can be null)
	 * @param showToUser if true, shows error dialog to user
	 */
	public static void error(String message, Throwable throwable, boolean showToUser) {
		if (throwable != null) {
			logger.error(message, throwable);
		} else {
			logger.error(message);
		}
		
		if (showToUser) {
			showErrorDialog("Error", message, throwable);
		}
	}
	
	/**
	 * Logs an error message with exception (no user dialog).
	 * 
	 * @param message the error message
	 * @param throwable the exception
	 */
	public static void error(String message, Throwable throwable) {
		error(message, throwable, false);
	}
	
	/**
	 * Logs an error message (no user dialog).
	 * 
	 * @param message the error message
	 */
	public static void error(String message) {
		error(message, null, false);
	}
	
	/**
	 * Logs a warning message.
	 * 
	 * @param message the warning message
	 */
	public static void warn(String message) {
		logger.warn(message);
	}
	
	/**
	 * Logs a warning message with exception.
	 * 
	 * @param message the warning message
	 * @param throwable the exception
	 */
	public static void warn(String message, Throwable throwable) {
		if (throwable != null) {
			logger.warn(message, throwable);
		} else {
			logger.warn(message);
		}
	}
	
	/**
	 * Logs an informational message.
	 * 
	 * @param message the info message
	 */
	public static void info(String message) {
		logger.info(message);
	}
	
	/**
	 * Logs a debug message.
	 * 
	 * @param message the debug message
	 */
	public static void debug(String message) {
		logger.debug(message);
	}
	
	/**
	 * Shows an error dialog to the user.
	 * 
	 * @param title the dialog title
	 * @param message the error message
	 * @param throwable the exception (can be null)
	 */
	private static void showErrorDialog(String title, String message, Throwable throwable) {
		String fullMessage = message;
		if (throwable != null && throwable.getMessage() != null) {
			fullMessage += "\n\nDetails: " + throwable.getMessage();
		}
		
		JOptionPane.showMessageDialog(
			null,
			fullMessage,
			title,
			JOptionPane.ERROR_MESSAGE
		);
	}
	
	/**
	 * Shows a warning dialog to the user.
	 * 
	 * @param title the dialog title
	 * @param message the warning message
	 */
	public static void showWarning(String title, String message) {
		logger.warn(message);
		JOptionPane.showMessageDialog(
			null,
			message,
			title,
			JOptionPane.WARNING_MESSAGE
		);
	}
	
	/**
	 * Shows an info dialog to the user.
	 * 
	 * @param title the dialog title
	 * @param message the info message
	 */
	public static void showInfo(String title, String message) {
		logger.info(message);
		JOptionPane.showMessageDialog(
			null,
			message,
			title,
			JOptionPane.INFORMATION_MESSAGE
		);
	}
}

