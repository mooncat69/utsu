package com.utsusynth.utsu.common.prefs;

import java.io.File;
import java.io.IOException;
import java.util.prefs.Preferences;

public class EnginePreferences {

    private static final String DEFAULT_VOICE_PATH = "assets/voice";
    private static final String ENGINE_PREFS_NODE = "EnginePreferences";
    private static final String ENGINE_PREFS_KEY_VOICE_DIR = "VoiceDir";

    public static final File getVoiceDirectory() {
        var prefs = Preferences.userRoot().node(ENGINE_PREFS_NODE);
        return new File(prefs.get(ENGINE_PREFS_KEY_VOICE_DIR, DEFAULT_VOICE_PATH));
    }

    public static final void setVoiceDirectory(File file ) throws IOException {
        var prefs = Preferences.userRoot().node(ENGINE_PREFS_NODE);
        prefs.put(ENGINE_PREFS_KEY_VOICE_DIR, file.getCanonicalPath());
    }
}