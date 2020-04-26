package com.utsusynth.utsu.engine;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

import com.google.inject.Provider;
import com.utsusynth.utsu.files.VoicebankReader;
import com.utsusynth.utsu.model.song.NoteList;
import com.utsusynth.utsu.model.song.NoteStandardizer;
import com.utsusynth.utsu.model.song.Song;
import com.utsusynth.utsu.model.song.pitch.PitchCurve;
import com.utsusynth.utsu.model.song.pitch.portamento.PortamentoFactory;
import com.utsusynth.utsu.model.voicebank.DisjointLyricSet;
import com.utsusynth.utsu.model.voicebank.LyricConfigMap;
import com.utsusynth.utsu.model.voicebank.PitchMap;
import com.utsusynth.utsu.model.voicebank.Voicebank;
import com.utsusynth.utsu.model.voicebank.VoicebankContainer;
import com.utsusynth.utsu.model.voicebank.VoicebankManager;

public class TestHelper {

    public static final String DEFAULT_VOICE_PATH = "assets/voice/Iona_Beta";

    public static Provider<Song> createSongProvider(ExternalProcessRunner runner, File voicePath) {

        return () -> new Song(
                            createVoicebankContainer(runner, voicePath),
                            new NoteStandardizer(),
                            new NoteList(),
                            new PitchCurve(new PortamentoFactory())
                            );
    }

    public static Provider<Voicebank> createVoiceBankProvider(ExternalProcessRunner runner) {

        LyricConfigMap lyricConfigs = new LyricConfigMap();
        PitchMap pitchMap = new PitchMap();
        DisjointLyricSet conversionSet = new DisjointLyricSet();
        Set<File> soundFiles = new HashSet<>();
        FrqGenerator frqGenerator = createFrqGenerator(runner);

        return () -> new Voicebank(lyricConfigs, pitchMap, conversionSet, soundFiles, frqGenerator);
    }

    public static VoicebankReader createVoicebankReader(ExternalProcessRunner runner, File voicePath) {
        File lyricConversionPath = new File("assets/config/lyric_conversions.txt");
        return new VoicebankReader(voicePath, lyricConversionPath, createVoiceBankProvider(runner));
   }

    public static FrqGenerator createFrqGenerator(ExternalProcessRunner runner) {

        String os = System.getProperty("os.name").toLowerCase();
        String frqGeneratorPath;

        if (os.contains("win")) {
            frqGeneratorPath = "assets/win64/frq0003gen.exe";
        } else if (os.contains("mac")) {
            frqGeneratorPath = "assets/Mac/frq0003gen";
        } else {
            frqGeneratorPath = "assets/linux64/frq0003gen";
        }

        return new FrqGenerator(runner, new File(frqGeneratorPath), 256);
    }
    
    public static VoicebankContainer createVoicebankContainer(ExternalProcessRunner runner, File voicePath) {

        VoicebankReader voicebankReader = createVoicebankReader(runner, voicePath);
        VoicebankManager voicebankManager = new VoicebankManager(voicebankReader);

        return new VoicebankContainer(voicebankManager, voicebankReader);
    }    
}