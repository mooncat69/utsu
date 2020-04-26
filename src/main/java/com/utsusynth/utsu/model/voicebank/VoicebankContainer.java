package com.utsusynth.utsu.model.voicebank;

import java.io.File;
import java.io.IOException;

import com.google.inject.Inject;
import com.utsusynth.utsu.common.exception.ErrorLogger;
import com.utsusynth.utsu.common.prefs.EnginePreferences;
import com.utsusynth.utsu.files.VoicebankReader;

/** Manages a single voicebank and its save settings. */
public class VoicebankContainer {
    private File location;

    private final VoicebankManager voicebankManager;
    private final VoicebankReader voicebankReader;

    @Inject
    public VoicebankContainer(VoicebankManager voicebankManager, VoicebankReader voicebankReader) {
        this.voicebankManager = voicebankManager;
        this.voicebankReader = voicebankReader;
        setVoicebank(voicebankReader.getDefaultPath()); // Start with default voicebank.
    }

    public Voicebank get() {
        // Reloads voicebank from file if necessary.
        if (voicebankManager.hasVoicebank(location)) {
            return voicebankManager.getVoicebank(location);
        } else {
            Voicebank voicebank = voicebankReader.loadVoicebankFromDirectory(location);
            voicebankManager.setVoicebank(location, voicebank);
            return voicebank;
        }
    }

    public void mutate(Voicebank newVoicebank) {
        voicebankManager.setVoicebank(location, newVoicebank);
    }

    public void setVoicebank(File newLocation) {
        try {
            var path = newLocation.getName();
            final String VoiceToken = "%VOICE%";
            if (!path.startsWith(VoiceToken)) {
                location = newLocation;
            } else {
                var rootVoice = EnginePreferences.getVoiceDirectory().getCanonicalPath();
                var voicePath = rootVoice + "/" + path.substring(VoiceToken.length());
                location = new File(voicePath);
            }
        } catch (IOException ioe) {
            ErrorLogger.getLogger().logError(ioe);
        }
    }

    public void removeVoicebank() {
        voicebankManager.removeVoicebank(location);
    }

    public File getLocation() {
        return location;
    }
}
