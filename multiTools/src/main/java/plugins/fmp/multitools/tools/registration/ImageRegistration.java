package plugins.fmp.multitools.tools.registration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.sequence.SequenceCamData;

public abstract class ImageRegistration {
	
	public abstract boolean runRegistration(Experiment exp, int referenceFrame, int startFrame, int endFrame, boolean reverse);
	
	public boolean doBackup(Experiment exp) {
		SequenceCamData seqCamData = exp.getSeqCamData();
		List<String> list = seqCamData.getImagesList(true);
		if (list.size() < 1)
			return false;
		
		String firstFileName = list.get(0);
		File firstFile = new File(firstFileName);
		String parentDirectory = firstFile.getParent();
		String backupDirectory = parentDirectory + File.separator + "original";
		
		Path backupPath = Paths.get(backupDirectory);
		if (Files.notExists(backupPath)) {
			try {
				Files.createDirectory(backupPath);
			} catch (IOException e) {
				e.printStackTrace();
				return false;
			}
		}
		
		for (String filename : list) {
			File file = new File(filename);
			String shortName = file.getName();
			Path destPath = backupPath.resolve(shortName);
			if (Files.notExists(destPath)) {
				try {
					Files.copy(file.toPath(), destPath);
				} catch (IOException e) {
					e.printStackTrace();
					return false;
				}
			}
		}
		return true;
	}

}






