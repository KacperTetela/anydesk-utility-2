package raven.anydesk;

import java.util.prefs.Preferences;

public final class AnyDeskSettings {

    private static final Preferences PREFS = Preferences.userNodeForPackage(AnyDeskSettings.class);

    private static final String KEY_CLEANUP_ENABLED = "cleanup.enabled";

    private AnyDeskSettings() {
    }

    public static boolean isCleanupEnabled() {
        return PREFS.getBoolean(KEY_CLEANUP_ENABLED, false);
    }

    public static void setCleanupEnabled(boolean enabled) {
        PREFS.putBoolean(KEY_CLEANUP_ENABLED, enabled);
    }
}

