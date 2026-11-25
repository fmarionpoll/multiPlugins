package plugins.fmp.multitools.service;

import java.util.List;

import plugins.fmp.multitools.experiment.Experiment;

/** Resolves physical acquisition times without substituting an export or kymograph grid. */
public final class SpotAcquisitionTimeline {
    private SpotAcquisitionTimeline() { }

    public static double[] camera(Experiment exp, int count) {
        // Use the existing acquisition clock before asking the experiment to rebuild
        // one from filenames (which may be repeated, synthetic, or lack timestamps).
        long[] milliseconds = exp.getCamImages_ms();
        double[] minutes = null;
        if (milliseconds != null && milliseconds.length == count) {
            minutes = new double[count];
            for (int i = 0; i < count; i++) { minutes[i] = milliseconds[i] / 60000.0; }
        } else {
            minutes = exp.getMeasureTimeMinutes();
        }
        if (minutes == null || minutes.length != count) {
            throw new IllegalStateException("Primary spot measurements require the camera acquisition timeline");
        }
        double[] seconds = new double[count];
        for (int i = 0; i < count; i++) {
            seconds[i] = minutes[i] * 60;
            if (!Double.isFinite(seconds[i]) || (i > 0 && seconds[i] <= seconds[i - 1])) {
                throw new IllegalStateException("Camera acquisition times must be finite and increasing");
            }
        }
        return seconds;
    }

    public static double[] camera(Experiment exp, List<Integer> frames) {
        double[] all = camera(exp, exp.getAnalysisFrameCount());
        double[] selected = new double[frames.size()];
        for (int i = 0; i < frames.size(); i++) { selected[i] = all[frames.get(i)]; }
        return selected;
    }
}
