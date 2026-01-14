package plugins.fmp.multitools.fmp_tools.registration;

import java.io.File;

import javax.vecmath.Vector2d;

import icy.file.Saver;
import icy.image.IcyBufferedImage;
import icy.sequence.Sequence;
import plugins.fmp.multitools.fmp_experiment.Experiment;
import plugins.fmp.multitools.fmp_experiment.sequence.SequenceCamData;

public class ImageRegistrationGaspard extends ImageRegistration {

	@Override
	public boolean runRegistration(Experiment exp, int referenceFrame, int startFrame, int endFrame, boolean reverse) {
		if (!doBackup(exp))
			return false;

		SequenceCamData seqCamData = exp.getSeqCamData();
		Sequence seq = seqCamData.getSequence();
		IcyBufferedImage refImage = seq.getImage(referenceFrame, 0);

		int step = reverse ? -1 : 1;
		int start = reverse ? endFrame : startFrame;
		int end = reverse ? startFrame : endFrame;

		int t = start;
		while ((reverse && t >= end) || (!reverse && t <= end)) {
			if (t == referenceFrame) {
				t += step;
				continue;
			}

			IcyBufferedImage img = seq.getImage(t, 0);

			Vector2d translation = new Vector2d();
			int n = 0;
			int minC = 0;
			int maxC = img.getSizeC() - 1;

			for (int c = minC; c <= maxC; c++) {
				translation.add(GaspardRigidRegistration.findTranslation2D(img, c, refImage, c));
				n++;
			}

			translation.scale(1.0 / n);

			if (translation.lengthSquared() != 0) {
				IcyBufferedImage newImg = GaspardRigidRegistration.applyTranslation2D(img, -1, translation, true);
				String filename = seqCamData.getFileNameFromImageList(t);
				File file = new File(filename);
				try {
					Saver.saveImage(newImg, file, true);
				} catch (Exception e) {
					e.printStackTrace();
					return false;
				}
			}
			t += step;
		}
		return true;
	}

}





