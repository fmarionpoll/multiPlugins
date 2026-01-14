package plugins.fmp.multitools.fmp_experiment;

import java.io.File;
import java.util.Iterator;
import java.util.List;

import plugins.fmp.multitools.fmp_experiment.sequence.ImageFileData;

public class ImageFileDescriptor {
	public String fileName = null;
	public boolean exists = false;
	public int imageHeight = 0;
	public int imageWidth = 0;

	public static int getExistingFileNames(List<ImageFileData> fileNameList) {
		Iterator<ImageFileData> it = fileNameList.iterator();
		int ntotal = 0;
		while (it.hasNext()) {
			ImageFileData fP = it.next();
			File fileName = new File(fP.fileName);
			fP.exists = fileName.exists();
			if (fileName.exists())
				ntotal++;
		}
		return ntotal;
	}

}
