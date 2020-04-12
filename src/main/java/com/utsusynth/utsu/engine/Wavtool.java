package com.utsusynth.utsu.engine;

import java.io.File;
import com.google.inject.Inject;
import com.utsusynth.utsu.model.song.Song;
import com.utsusynth.utsu.files.ScriptHelper;
import com.utsusynth.utsu.model.song.Note;
import com.utsusynth.utsu.model.voicebank.LyricConfig;

public class Wavtool {
    private final ExternalProcessRunner runner;

    @Inject
    Wavtool(ExternalProcessRunner runner) {
        this.runner = runner;
    }

    void addNewNote(
            File wavtoolPath,
            Song song,
            Note note,
            double noteLength,
            LyricConfig config,
            File inputFile,
            File outputFile,
            boolean includeOverlap,
            boolean triggerSynthesis) {

        String[] args = getNewNoteArgs(wavtoolPath, song, note, noteLength, config, inputFile, outputFile, includeOverlap, triggerSynthesis);

        // Call wavtool to add new note onto the end of the output file.
        runner.runProcess(args);
    }

    String getNewNoteScript(
            File wavtoolPath,
            Song song,
            Note note,
            double noteLength,
            LyricConfig config,
            File inputFile,
            File outputFile,
            boolean includeOverlap,
            boolean triggerSynthesis) {

        String[] args = getNewNoteArgs(wavtoolPath, song, note, noteLength, config, inputFile, outputFile, includeOverlap, triggerSynthesis);
        return ScriptHelper.getScriptLine(args);
    }

    void addSilence(
            File wavtoolPath,
            double duration,
            File inputFile,
            File outputFile,
            boolean triggerSynthesis) {

        String[] args = getSilenceArgs(wavtoolPath, duration, inputFile, outputFile, triggerSynthesis);

        runner.runProcess(args);
    }

    String getSilenceScript(
            File wavtoolPath,
            double duration,
            File inputFile,
            File outputFile,
            boolean triggerSynthesis) {

        String[] args = getSilenceArgs(wavtoolPath, duration, inputFile, outputFile, triggerSynthesis);
        return ScriptHelper.getScriptLine(args);
    }

    private String[] getNewNoteArgs(
            File wavtoolPath,
            Song song,
            Note note,
            double noteLength,
            LyricConfig config,
            File inputFile,
            File outputFile,
            boolean includeOverlap,
            boolean triggerSynthesis) {

        String outputFilePath = outputFile.getAbsolutePath();
        String inputFilePath = inputFile.getAbsolutePath();
        double startPoint = note.getStartPoint(); // TODO: Add auto start point.
        String[] envelope = note.getFullEnvelope();

        double overlap = Math.min(config.getOverlap(), note.getFadeIn());
        double boundedOverlap = Math.max(0, Math.min(overlap, noteLength));
        // Ignore overlap if current note doesn't touch previous one.
        if (!includeOverlap) {
            boundedOverlap = 0;
        }

        double scaleFactor = 125 / song.getTempo();

        String[] args = {
            wavtoolPath.getAbsolutePath(),
            outputFilePath,
            inputFilePath,
            Double.toString(startPoint),
            Double.toString(noteLength * scaleFactor),
            envelope[0], // p1
            envelope[1], // p2
            envelope[2], // p3
            envelope[3], // v1
            envelope[4], // v2
            envelope[5], // v3
            envelope[6], // v4
            Double.toString(boundedOverlap * scaleFactor), // overlap
            envelope[8], // p4
            envelope[9], // p5
            envelope[10], // v5
            triggerSynthesis ? "LAST_NOTE" : "" // Triggers final song processing.
        };

        return args;
    }

    private String[] getSilenceArgs(
        File wavtoolPath,
        double duration,
        File inputFile,
        File outputFile,
        boolean triggerSynthesis) {

        String startPoint = "0.0";
        String noteLength = Double.toString(duration); // Tempo already applied.
        String[] envelope = new String[] {"0", "0", "0", "0", "0", "0", "0", "0", "0", "0", "0"};

        String[] args = {
                wavtoolPath.getAbsolutePath(),
                outputFile.getAbsolutePath(),
                inputFile.getAbsolutePath(),
                startPoint,
                noteLength,
                envelope[0], // p1
                envelope[1], // p2
                envelope[2], // p3
                envelope[3], // v1
                envelope[4], // v2
                envelope[5], // v3
                envelope[6], // v4
                envelope[7], // overlap
                envelope[8], // p4
                envelope[9], // p5
                envelope[10], // v5
                triggerSynthesis ? "LAST_NOTE" : ""// Triggers final song processing.
        };

        return args;
    }
}
