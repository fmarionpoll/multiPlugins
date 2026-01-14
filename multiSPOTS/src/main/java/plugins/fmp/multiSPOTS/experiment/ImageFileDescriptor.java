package plugins.fmp.multiSPOTS.experiment;

import java.io.File;
import java.util.Iterator;
import java.util.List;

public class ImageFileDescriptor {
	public String fileName = null;
	public boolean exists = false;
	public int imageHeight = 0;
	public int imageWidth = 0;

	public static int getExistingFileNames(List<ImageFileDescriptor> fileNameList) {
		Iterator<ImageFileDescriptor> it = fileNameList.iterator();
		int ntotal = 0;
		while (it.hasNext()) {
			ImageFileDescriptor fP = it.next();
			File fileName = new File(fP.fileName);
			fP.exists = fileName.exists();
			if (fileName.exists())
				ntotal++;
		}
		return ntotal;
	}

}
