package plugins.fmp.multitools.test;

import org.junit.rules.ExternalResource;

import icy.preferences.GeneralPreferences;
import icy.preferences.IcyPreferences;

/** Keeps image tests independent of the application's saved Virtual Mode setting. */
public final class InMemoryImageRule extends ExternalResource {
    private boolean previousVirtualMode;

    @Override
    protected void before() {
        IcyPreferences.init();
        previousVirtualMode = GeneralPreferences.getVirtualMode();
        GeneralPreferences.setVirtualMode(false);
    }

    @Override
    protected void after() {
        GeneralPreferences.setVirtualMode(previousVirtualMode);
    }
}
