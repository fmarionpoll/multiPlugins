package plugins.fmp.multiSPOTS96.tools.toExcel;

import java.io.FileOutputStream;
import java.io.IOException;

import org.apache.poi.hssf.util.HSSFColor;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;

import plugins.fmp.multiSPOTS96.tools.toExcel.exceptions.ExcelResourceException;

/**
 * Manages Excel resources with proper lifecycle management and cleanup.
 * Implements AutoCloseable for use with try-with-resources blocks.
 */
public class ExcelResourceManager implements AutoCloseable {
    
    private final SXSSFWorkbook workbook;
    private final FileOutputStream fileOutputStream;
    private final String filename;
    
    // Pre-configured styles for common formatting
    private final CellStyle redCellStyle;
    private final CellStyle blueCellStyle;
    private final Font redFont;
    private final Font blueFont;
    
    private boolean closed = false;
    
    /**
     * Creates a new Excel resource manager for the specified file.
     * 
     * @param filename The path to the Excel file to create
     * @throws ExcelResourceException If the file cannot be created or workbook initialization fails
     */
    public ExcelResourceManager(String filename) throws ExcelResourceException {
        this.filename = filename;
        
        try {
            this.workbook = new SXSSFWorkbook();
            this.fileOutputStream = new FileOutputStream(filename);
            
            // Configure workbook settings
            workbook.setMissingCellPolicy(Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
            
            // Initialize common styles
            this.redCellStyle = workbook.createCellStyle();
            this.redFont = workbook.createFont();
            redFont.setColor(HSSFColor.HSSFColorPredefined.RED.getIndex());
            redCellStyle.setFont(redFont);
            
            this.blueCellStyle = workbook.createCellStyle();
            this.blueFont = workbook.createFont();
            blueFont.setColor(HSSFColor.HSSFColorPredefined.BLUE.getIndex());
            blueCellStyle.setFont(blueFont);
            
        } catch (IOException e) {
            throw new ExcelResourceException("Failed to initialize Excel resources", 
                                           "resource_initialization", filename, e);
        }
    }
    
    /**
     * Gets the workbook instance.
     * 
     * @return The Excel workbook
     * @throws ExcelResourceException If the resource manager is closed
     */
    public SXSSFWorkbook getWorkbook() throws ExcelResourceException {
        checkNotClosed();
        return workbook;
    }
    
    /**
     * Gets the red cell style for formatting.
     * 
     * @return The red cell style
     * @throws ExcelResourceException If the resource manager is closed
     */
    public CellStyle getRedCellStyle() throws ExcelResourceException {
        checkNotClosed();
        return redCellStyle;
    }
    
    /**
     * Gets the blue cell style for formatting.
     * 
     * @return The blue cell style
     * @throws ExcelResourceException If the resource manager is closed
     */
    public CellStyle getBlueCellStyle() throws ExcelResourceException {
        checkNotClosed();
        return blueCellStyle;
    }
    
    /**
     * Saves the workbook to the file and closes all resources.
     * This method is idempotent - calling it multiple times has no effect.
     * 
     * @throws ExcelResourceException If saving fails
     */
    public void saveAndClose() throws ExcelResourceException {
        if (closed) {
            return;
        }
        
        try {
            workbook.write(fileOutputStream);
            close();
        } catch (IOException e) {
            throw new ExcelResourceException("Failed to save Excel file", 
                                           "save_operation", filename, e);
        }
    }
    
    /**
     * Closes all resources without saving.
     * This method is idempotent - calling it multiple times has no effect.
     * 
     * @throws ExcelResourceException If closing fails
     */
    @Override
    public void close() throws ExcelResourceException {
        if (closed) {
            return;
        }
        
        ExcelResourceException lastException = null;
        
        // Close workbook
        if (workbook != null) {
            try {
                workbook.close();
            } catch (IOException e) {
                lastException = new ExcelResourceException("Failed to close workbook", 
                                                         "close_workbook", filename, e);
            }
        }
        
        // Close file output stream
        if (fileOutputStream != null) {
            try {
                fileOutputStream.close();
            } catch (IOException e) {
                if (lastException != null) {
                    lastException.addSuppressed(e);
                } else {
                    lastException = new ExcelResourceException("Failed to close file output stream", 
                                                             "close_stream", filename, e);
                }
            }
        }
        
        closed = true;
        
        if (lastException != null) {
            throw lastException;
        }
    }
    
    /**
     * Checks if the resource manager is closed.
     * 
     * @return true if closed, false otherwise
     */
    public boolean isClosed() {
        return closed;
    }
    
    /**
     * Gets the filename associated with this resource manager.
     * 
     * @return The filename
     */
    public String getFilename() {
        return filename;
    }
    
    /**
     * Checks that the resource manager is not closed and throws an exception if it is.
     * 
     * @throws ExcelResourceException If the resource manager is closed
     */
    private void checkNotClosed() throws ExcelResourceException {
        if (closed) {
            throw new ExcelResourceException("Excel resource manager is closed", 
                                           "resource_access", filename);
        }
    }
} 