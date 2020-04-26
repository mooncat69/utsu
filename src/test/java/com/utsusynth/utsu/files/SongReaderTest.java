package com.utsusynth.utsu.files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;

import com.utsusynth.utsu.engine.ExternalProcessRunner;
import com.utsusynth.utsu.engine.TestHelper;
import com.utsusynth.utsu.model.song.Song;

import org.junit.Test;

public class SongReaderTest {

    private static final String TEST_SONG_DIR = TestConstants.TEST_ASSETS_PATH + "default/songs/";
    private static final String TEST_SONG_DIR_UST12 = TEST_SONG_DIR + "ust-12/";

    @Test
    public void TestUst12Reader() {
        var runner = new ExternalProcessRunner();
        var voicePath = new File(TestHelper.DEFAULT_VOICE_PATH);
        var voicebankReader = TestHelper.createVoicebankReader(runner, voicePath);
        var songProvider = TestHelper.createSongProvider(runner, voicePath);
        var reader = new Ust12Reader(songProvider, voicebankReader);

        try {
            var dir = new File(TEST_SONG_DIR_UST12);
            if (!dir.exists()) return;

            for (var file: dir.listFiles()) {

                if (file.isDirectory() || !file.getName().endsWith(".ust")) continue;

                var song = reader.loadSong(file);

                if (file.getName() == "test-1.ust") {
                    assertEquals("Incorrect Flags", "F1Y0B0H0", song.getFlags());
                    assertEquals("Incorrect Tempo", 120.0, song.getTempo(), 0.1);
                    assertEquals("Incorrect Project Name", "UTAU 1.2 Test", song.getProjectName());
                    assertEquals("Incorrect Output File", "U12Test.wav", song.getOutputFile().getName());
                    assertEquals("Incorrect Voice Dir", "%VOICE%uta", song.getVoiceDir().getName());
                    assertEquals("Incorrect Mode 2", true, song.getMode2());
                } else {
                    assertTrue("Incorrect Tempo", song.getTempo() >= Song.MIN_TEMPO && song.getTempo() <= Song.MAX_TEMPO);
                    assertTrue("Incorrect Project Name", !song.getProjectName().isBlank());
                    assertTrue("Incorrect Output File", !song.getOutputFile().getName().isBlank());
                    assertTrue("Incorrect Voice Dir", !song.getVoiceDir().getName().isBlank());
                }

                var postition = -1;
                var iterator = song.getNotes().iterator();
                assertTrue("Not enough notes", song.getNumNotes() > 0);

                while (iterator.hasNext()) {
                    var n = iterator.next();
                    assertTrue("Note duration is missing", n.getDuration() > 0);
                    assertTrue("Lyric is missing", !n.getLyric().isBlank());
                    assertTrue("Pitch is missing", !n.getPitch().isBlank());
                    assertTrue("Position is missing", n.getPosition() > postition);
                    postition = n.getPosition();
                }
            }
        } catch (IOException ioe) {
            fail(ioe.getMessage());
        }
    }
}