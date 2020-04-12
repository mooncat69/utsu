package com.utsusynth.utsu.engine;

import java.io.File;
import java.io.IOException;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.io.Files;
import com.google.inject.Inject;
import com.utsusynth.utsu.common.RegionBounds;
import com.utsusynth.utsu.common.StatusBar;
import com.utsusynth.utsu.common.exception.ErrorLogger;
import com.utsusynth.utsu.common.quantize.Quantizer;
import com.utsusynth.utsu.common.utils.PitchUtils;
import com.utsusynth.utsu.files.ScriptHelper;
import com.utsusynth.utsu.model.song.Note;
import com.utsusynth.utsu.model.song.NoteIterator;
import com.utsusynth.utsu.model.song.Song;
import com.utsusynth.utsu.model.voicebank.LyricConfig;
import com.utsusynth.utsu.model.voicebank.Voicebank;

import org.apache.commons.io.FileUtils;

import javafx.application.Platform;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaPlayer.Status;
import javafx.util.Duration;

public class Engine {
    private static final ErrorLogger errorLogger = ErrorLogger.getLogger();

    public enum PlaybackStatus {
        PLAYING, PAUSED, STOPPED,
    }

    private final Resampler resampler;
    private final Wavtool wavtool;
    private final File tempDir;
    private final StatusBar statusBar;
    private final ExternalProcessRunner runner;
    private File resamplerPath;
    private File wavtoolPath;
    private File lastRenderedFile = null;

    private MediaPlayer instrumentalPlayer; // Used for background music.
    private MediaPlayer mediaPlayer; // Used for audio playback.

    @Inject
    public Engine(
            Resampler resampler,
            Wavtool wavtool,
            StatusBar statusBar,
            File resamplerPath,
            File wavtoolPath,
            ExternalProcessRunner runner) {
        this.resampler = resampler;
        this.wavtool = wavtool;
        this.statusBar = statusBar;
        this.resamplerPath = resamplerPath;
        this.wavtoolPath = wavtoolPath;
        this.runner = runner;

        // Create temporary directory for rendering.
        tempDir = Files.createTempDir();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                FileUtils.deleteDirectory(tempDir);
            } catch (IOException e) {
                errorLogger.logError(e);
            }
        }));
    }

    public File getResamplerPath() {
        return resamplerPath;
    }

    public void setResamplerPath(File resamplerPath) {
        this.resamplerPath = resamplerPath;
    }

    public File getWavtoolPath() {
        return wavtoolPath;
    }

    public void setWavtoolPath(File wavtoolPath) {
        this.wavtoolPath = wavtoolPath;
    }

    /**
     * Exports of region of a song to a WAV file.
     * 
     * @return Whether or not there is any sound to export.
     */
    public boolean renderWav(Song song, File finalDestination) {
        Optional<File> finalSong = render(song, RegionBounds.WHOLE_SONG);
        if (finalSong.isPresent()) {
            finalSong.get().renameTo(finalDestination);
        }
        return finalSong.isPresent();
    }

    /**
     * Starts playback for a region of a song.
     * 
     * @return Whether or not there is any sound to play.
     */
    public boolean startPlayback(
            Song song,
            RegionBounds bounds,
            Function<Duration, Void> startCallback,
            Runnable endCallback) {
        stopPlayback(); // Clear existing playback, if present.
        Optional<File> finalSong = render(song, bounds);
        if (finalSong.isPresent()) {
            // Play instrumental, if present.
            if (song.getInstrumental().isPresent()) {
                Media instrumental = new Media(song.getInstrumental().get().toURI().toString());
                System.out.println(instrumental.getSource());
                instrumentalPlayer = new MediaPlayer(instrumental);
                instrumentalPlayer.play();
            }
            Media media = new Media(finalSong.get().toURI().toString());
            mediaPlayer = new MediaPlayer(media);
            mediaPlayer.setOnReady(() -> startCallback.apply(media.getDuration()));
            mediaPlayer.setOnEndOfMedia(() -> mediaPlayer.stop());
            mediaPlayer.setOnStopped(() -> {
                endCallback.run();
                if (instrumentalPlayer != null) {
                    instrumentalPlayer.stop();
                }
            });
            mediaPlayer.play();
        }
        return finalSong.isPresent();
    }

    public void pausePlayback() {
        if (mediaPlayer != null) {
            mediaPlayer.pause();
        }
        if (instrumentalPlayer != null && instrumentalPlayer.getStatus().equals(Status.PLAYING)) {
            instrumentalPlayer.pause();
        }
    }

    public void resumePlayback() {
        if (mediaPlayer != null) {
            mediaPlayer.play();
        }
        if (instrumentalPlayer != null && instrumentalPlayer.getStatus().equals(Status.PAUSED)) {
            instrumentalPlayer.play();
        }
    }

    public void stopPlayback() {
        if (mediaPlayer != null) {
            mediaPlayer.stop();
        }
    }

    public PlaybackStatus getStatus() {
        if (mediaPlayer != null) {
            switch (mediaPlayer.getStatus()) {
                case PLAYING:
                    return PlaybackStatus.PLAYING;
                case PAUSED:
                    return PlaybackStatus.PAUSED;
                default:
                    return PlaybackStatus.STOPPED;
            }
        }
        return PlaybackStatus.STOPPED;
    }

    private Optional<File> render(Song song, RegionBounds bounds) {
        if (lastRenderedFile != null && lastRenderedFile.exists() && bounds.equals(song.getLastRenderedRegion())) {
            // Return old final song if it has not been invalidated.
            return Optional.of(lastRenderedFile);
        }

        File renderedSilence = new File(tempDir, "rendered_silence.wav");

        NoteIterator notes = song.getNoteIterator(bounds);
        if (!notes.hasNext()) {
            return Optional.absent();
        }

        LocalTime startTime = LocalTime.now();
        
        int totalDelta = notes.getCurDelta(); // Absolute position of current note.
        Voicebank voicebank = song.getVoicebank();
        boolean isFirstNote = true;        
        File finalSong; // Is the final keyword causing file locking??

        try {
            // The final rendering goes to this file
            finalSong = File.createTempFile("utsu-", ".wav");
            finalSong.deleteOnExit(); // Required??
        } catch (IOException ioe) {
            return Optional.absent();
        }

        // Holds all of the script lines for resampler and wavtool
        ArrayList<String> wavtoolScriptLines = new ArrayList<>();
        ArrayList<String> resamplerScriptLines = new ArrayList<>();

        while (notes.hasNext()) {
            Note note = notes.next();
            totalDelta += note.getDelta(); // Unique for every note in a single sequence.

            // Get lyric config.
            Optional<LyricConfig> config = Optional.absent();
            if (!note.getTrueLyric().isEmpty()) {
                config = voicebank.getLyricConfig(note.getTrueLyric());
            }
            if (!config.isPresent()) {
                // Make one last valiant effort to find the true lyric.
                String prevLyric = getNearbyPrevLyric(notes.peekPrev());
                String pitch = PitchUtils.noteNumToPitch(note.getNoteNum());
                config = voicebank.getLyricConfig(prevLyric, note.getLyric(), pitch);
                if (config.isPresent()) {
                    note.setTrueLyric(config.get().getTrueLyric());
                }
            }

            // Find preutterance of current and next notes.
            double preutter = note.getRealPreutter();
            Optional<Double> nextPreutter = Optional.absent();
            if (notes.peekNext().isPresent()) {
                nextPreutter = Optional.of(notes.peekNext().get().getRealPreutter());
            }

            // Possible silence before first note.
            if (isFirstNote) {
                if (notes.getCurDelta() - preutter > bounds.getMinMs()) {
                    double startDelta = notes.getCurDelta() - preutter - bounds.getMinMs();
                    addSilence(startDelta, song, renderedSilence, finalSong, resamplerScriptLines, wavtoolScriptLines);
                }
                isFirstNote = false;
            }

            // Add silence in place of note if lyric not found.
            if (!config.isPresent()) {
                System.out.println("Could not find config for lyric: " + note.getLyric());
                if (notes.peekNext().isPresent()) {
                    addSilence(
                            note.getLength() - notes.peekNext().get().getRealPreutter(),
                            song,
                            renderedSilence,
                            finalSong,
                            resamplerScriptLines,
                            wavtoolScriptLines);
                } else {
                    // Case where the last note in the song is silent.
                    addFinalSilence(
                            note.getLength(),
                            song,
                            renderedSilence,
                            finalSong,
                            resamplerScriptLines,
                            wavtoolScriptLines);
                }
                continue;
            }

            // Adjust note length based on preutterance/overlap.
            double adjustedLength =
                    note.getRealDuration() > -1 ? note.getRealDuration() : note.getDuration();
            System.out.println("Length is " + adjustedLength);

            // Calculate pitchbends.
            int firstStep = getFirstPitchStep(totalDelta, preutter);
            int lastStep = getLastPitchStep(totalDelta, preutter, adjustedLength);
            String pitchString = song.getPitchString(firstStep, lastStep, note.getNoteNum());

            final LyricConfig curConfig = config.get();
            final boolean includeOverlap =
                    areNotesTouching(notes.peekPrev(), voicebank, Optional.of(preutter));
            final boolean isLastNote = !notes.peekNext().isPresent();

            File resampleCacheFile = resampler.getResampleCacheFile(
                    resamplerPath,
                    note,
                    adjustedLength,
                    curConfig,
                    pitchString,
                    song);

            String resampleScriptLine = resampler.getResampleScript(
                    resamplerPath,
                    note,
                    adjustedLength,
                    curConfig,
                    resampleCacheFile,
                    pitchString,
                    song);

            String wavScript = wavtool.getNewNoteScript(
                    wavtoolPath,
                    song,
                    note,
                    adjustedLength,
                    curConfig,
                    resampleCacheFile,
                    finalSong,
                    includeOverlap,
                    isLastNote);

            resamplerScriptLines.add(resampleScriptLine);        
            wavtoolScriptLines.add(wavScript);

            // Possible silence after each note.
            if (notes.peekNext().isPresent()
                    && !areNotesTouching(Optional.of(note), voicebank, nextPreutter)) {
                // Add silence
                double silenceLength;
                if (nextPreutter.isPresent()) {
                    silenceLength = note.getLength() - note.getDuration() - nextPreutter.get();
                } else {
                    silenceLength = note.getLength() - note.getDuration();
                }
                addSilence(silenceLength, song, renderedSilence, finalSong, resamplerScriptLines, wavtoolScriptLines);
            }
        }

        try {
            final ScriptHelper scriptHelper = new ScriptHelper(runner);
            scriptHelper.RunScriptParallel(resamplerScriptLines);
            scriptHelper.RunScriptSerial(wavtoolScriptLines);

            LocalTime finishTime = LocalTime.now();
            System.out.println("Rendered region in " + ChronoUnit.SECONDS.between(startTime, finishTime) + " seconds");

        } catch (IOException e) {
            errorLogger.logError(e);
            return Optional.absent();
        }

        if (statusBar != null) {
            // If this is being used as a pure engine, this is not supported
            Platform.runLater(() -> statusBar.setProgress(1.0)); // Mark task as complete.
        }

        song.setRendered(bounds); // Cache region that was played.
        lastRenderedFile = finalSong; // Save this for next time
        return Optional.of(finalSong);
    }

    private void addSilence(
            double duration,
            Song song,
            File renderedNote,
            File finalSong,
            ArrayList<String> resamplerScriptLines,
            ArrayList<String> wavtoolScriptLines) {

        double trueDuration = duration * (125.0 / song.getTempo());

        addSilenceImpl(trueDuration, false, song, renderedNote, finalSong, resamplerScriptLines, wavtoolScriptLines);
    }

    private void addFinalSilence(
            double duration,
            Song song,
            File renderedNote,
            File finalSong,
            ArrayList<String> resamplerScriptLines,
            ArrayList<String> wavtoolScriptLines) {
 
        // The final note must be passed to the wavtool.
        double trueDuration = Math.max(duration, 0) * (125.0 / song.getTempo());

        addSilenceImpl(trueDuration, true, song, renderedNote, finalSong, resamplerScriptLines, wavtoolScriptLines);
    }

    private void addSilenceImpl(
            double trueDuration,
            boolean isFinal,
            Song song,
            File renderedNote,
            File finalSong,
            ArrayList<String> resamplerScriptLines,
            ArrayList<String> wavtoolScriptLines) {

        if (trueDuration <= 0.0) {
            // Is this right for Final silence??
            return;
        }
        
        File resampleCacheFile = resampler.getResampleSilenceCacheFile(resamplerPath, trueDuration);
        String resampleScriptLine = resampler.getResampleSilenceScript(resamplerPath, resampleCacheFile, trueDuration);
        String wavtoolScriptLine = wavtool.getSilenceScript(wavtoolPath, trueDuration, resampleCacheFile, finalSong, isFinal);

        resamplerScriptLines.add(resampleScriptLine);
        wavtoolScriptLines.add(wavtoolScriptLine);
    }

    // Returns empty string if there is no nearby (within DEFAULT_NOTE_DURATION) previous note.
    private static String getNearbyPrevLyric(Optional<Note> prev) {
        if (prev.isPresent() && prev.get().getLength()
                - prev.get().getDuration() > Quantizer.DEFAULT_NOTE_DURATION) {
            return prev.get().getLyric();
        }
        return "";
    }

    private static int getFirstPitchStep(int totalDelta, double preutter) {
        return (int) Math.ceil((totalDelta - preutter) / 5.0);
    }

    private static int getLastPitchStep(int totalDelta, double preutter, double adjustedLength) {
        return (int) Math.floor((totalDelta - preutter + adjustedLength) / 5.0);
    }

    // Determines whether two notes are "touching" given the second note's preutterance.
    private static boolean areNotesTouching(
            Optional<Note> note,
            Voicebank voicebank,
            Optional<Double> nextPreutter) {
        if (!note.isPresent() || !nextPreutter.isPresent()) {
            return false;
        }

        // Return false if current note cannot be rendered.
        if (!voicebank.getLyricConfig(note.get().getTrueLyric()).isPresent()) {
            return false;
        }

        double preutterance = Math.min(nextPreutter.get(), note.get().getLength());
        if (preutterance + note.get().getDuration() < note.get().getLength()) {
            return false;
        }
        return true;
    }
}
