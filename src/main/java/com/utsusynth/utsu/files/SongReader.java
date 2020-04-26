package com.utsusynth.utsu.files;

import java.io.File;
import java.io.IOException;

import com.utsusynth.utsu.model.song.Song;

public interface SongReader {
    public Song loadSong(File path) throws IOException;
}