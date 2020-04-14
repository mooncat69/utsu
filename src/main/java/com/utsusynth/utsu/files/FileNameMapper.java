package com.utsusynth.utsu.files;

import java.util.concurrent.ConcurrentHashMap;

import com.utsusynth.utsu.engine.ExternalProcessRunner;

public class FileNameMapper {

    private static FileNameMapper _this = new FileNameMapper();
    private ConcurrentHashMap<String, String> _fileMap = new ConcurrentHashMap<>();
    private boolean _isWindows;

    private FileNameMapper() {
        String os = System.getProperty("os.name").toLowerCase();
        _isWindows = os.contains("win");
    }

    public static FileNameMapper getInstance() {
        return _this;
    }

    public String getOSName(String path) {

        if (!_isWindows)
            return path;

        if (_fileMap.containsKey(path)) {
            return _fileMap.get(path);
        }

        // Windows filenames are not reliable when calling external executables
        try {
            String output = new ExternalProcessRunner().getProcessOutput("cmd /c for %I in (\"" + path + "\") do @echo %~fsI");
            String dosPath = output.replaceAll("\\r\\n", "");

            if (!_fileMap.containsKey(path)) {
                try {
                    _fileMap.put(path, dosPath);
                } catch (Exception f) {
                    // Does this throw an exception if this is already there??
                }
            }

            return dosPath;
        } catch (Exception e) {
            return path;
        }
    }
}