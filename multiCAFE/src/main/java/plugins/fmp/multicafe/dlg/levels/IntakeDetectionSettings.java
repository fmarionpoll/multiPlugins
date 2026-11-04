package plugins.fmp.multicafe.dlg.levels;
import plugins.fmp.multitools.service.IntakeEpisodes;
/** Detector settings independent of Swing; legacy text is confined to persistence. */
final class IntakeDetectionSettings {
    final boolean episodes, local;
    IntakeDetectionSettings(boolean episodes, boolean local) {
        this.episodes = episodes; this.local = local;
    }
    static IntakeDetectionSettings fromOptions(String options) {
        return new IntakeDetectionSettings(options.contains("persistent=true"), options.contains("local=true"));
    }
    String options() {
        return IntakeEpisodes.METHOD_VERSION + ",source=accepted_topraw,persistent=" + episodes + ",local=" + local;
    }
}
