package com.utsusynth.utsu.files;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.utsusynth.utsu.common.exception.ErrorLogger;
import com.utsusynth.utsu.engine.ExternalProcessRunner;
import com.utsusynth.utsu.model.voicebank.CharacterData;
import com.utsusynth.utsu.model.voicebank.LyricConfig;
import com.utsusynth.utsu.model.voicebank.Voicebank;

import org.apache.commons.io.FileUtils;

public class VoicebankReader {
    private static final ErrorLogger errorLogger = ErrorLogger.getLogger();

    private static final Pattern LYRIC_PATTERN = Pattern.compile("(.+\\.wav)=([^,]*),");
    private static final Pattern PITCH_PATTERN =
            Pattern.compile("([a-gA-G]#?[1-7])\\t\\S*\\t(\\S.*)");

    private final File defaultVoicePath;
    private final File lyricConversionPath;
    private final Provider<Voicebank> voicebankProvider;

    @Inject
    public VoicebankReader(
            File defaultVoicePath,
            File lyricConversionPath,
            Provider<Voicebank> voicebankProvider) {
        this.defaultVoicePath = defaultVoicePath;
        this.lyricConversionPath = lyricConversionPath;
        this.voicebankProvider = voicebankProvider;
    }

    public File getDefaultPath() {
        return defaultVoicePath;
    }

    public Voicebank loadVoicebankFromDirectory(File sourceDir) {

        Voicebank bank = voicebankProvider.get();
        Voicebank.Builder builder = bank.toBuilder();

        File pathToVoicebank;
        if (!sourceDir.exists()) {
            pathToVoicebank = defaultVoicePath;
        } else {
            if (!sourceDir.isDirectory()) {
                pathToVoicebank = sourceDir.getParentFile();
            } else {
                pathToVoicebank = sourceDir;
            }
        }
        builder.setPathToVoicebank(pathToVoicebank);
        System.out.println("Parsed voicebank as " + pathToVoicebank);

        CharacterData characterData = readCharacterData(pathToVoicebank);
        CharacterData parentData = readCharacterData(pathToVoicebank.getParentFile());

        if (characterData == null) {
            // There may be no character data in the path
            characterData = new CharacterData(pathToVoicebank);
        }

        builder.addCharacterData(characterData, parentData);

        // Parse all oto_ini.txt and oto.ini files in arbitrary order.
        try {
            Files.walkFileTree(
                    pathToVoicebank.toPath(),
                    EnumSet.of(FileVisitOption.FOLLOW_LINKS),
                    10,
                    new SimpleFileVisitor<Path>() {
                        @Override
                        public FileVisitResult visitFile(Path path, BasicFileAttributes attr) {
                            for (String otoName : ImmutableSet.of("oto.ini", "oto_ini.txt")) {
                                if (path.endsWith(otoName)) {
                                    Path pathToFile = path.toFile().getParentFile().toPath();
                                    parseOtoIni(pathToVoicebank, pathToFile, otoName, builder, bank);
                                    break;
                                }
                            }
                            return FileVisitResult.CONTINUE;
                        }
                    });
        } catch (IOException e) {
            // TODO: Handle this.
            errorLogger.logError(e);
        }

        // Parse pitch map in arbitrary order, if present.
        for (String pitchMapName : ImmutableSet.of("prefixmap", "prefix.map")) {
            parsePitchMap(pathToVoicebank.toPath().resolve(pitchMapName).toFile(), builder);
        }

        // Parse conversion set for romaji-hiragana-katakana conversion.
        readLyricConversionsFromFile(builder);

        return builder.build();
    }

    private CharacterData readCharacterData(File pathToVoicebank) {

        File characterFile = pathToVoicebank.toPath().resolve("character.txt").toFile();
        File readmeFile = pathToVoicebank.toPath().resolve("readme.txt").toFile();

        if (!characterFile.exists() && !readmeFile.exists()) {
            return null;
        }        

        CharacterData data = new CharacterData(pathToVoicebank);

        if (characterFile.exists()) {

            // Parse character data in English or Japanese.
            String characterData = readConfigFile(characterFile);

            for (String rawLine : characterData.split("\n")) {

                String line = rawLine.trim();

                if (line.startsWith("name=")) {
                    data.setName(line.substring("name=".length()));
                } else if (line.startsWith("名前：")) {
                    data.setName(line.substring("名前：".length()));
                } else if (line.startsWith("author=")) {
                    data.setAuthor(line.substring("author=".length()));
                } else if (line.startsWith("CV：")) {
                    data.setAuthor(line.substring("CV：".length()));
                } else if (line.startsWith("image=")) {
                    data.setImageName(line.substring("image=".length()));
                } else if (line.startsWith("画像：")) {
                    data.setImageName(line.substring("画像：".length()));
                } else if (line.startsWith("sample=")) {
                    data.setSampleName(line.substring("sample=".length()));
                }
            }
        }

        if (readmeFile.exists()) {
            String readme = readConfigFile(readmeFile);
            if (readme != null && readme.length() > data.getDescription().length()) {
                data.setDescription(readme);
            }
        }

        return data;
    }

    private void parseOtoIni(
            File pathToVoicebank,
            Path pathToOtoFile,
            String otoFile,
            Voicebank.Builder builder,
            Voicebank bank) {

        String otoData = readConfigFile(pathToOtoFile.resolve(otoFile).toFile());
        HashMap<String, String> osNames = getOSFileNames(pathToVoicebank.getAbsolutePath());
        ArrayList<String> missingWavFiles = new ArrayList<>();

        for (String rawLine : otoData.split("\n")) {

            String line = rawLine.trim();
            Matcher matcher = LYRIC_PATTERN.matcher(line);

            if (matcher.find()) {
                String fileName = matcher.group(1); // Assuming this is a .wav file
                String lyricName = matcher.group(2);

                File lyricFile;
                if (osNames.containsKey(fileName)) {
                    lyricFile = new File(osNames.get(fileName));
                } else {
                    lyricFile = LyricConfig.getDefaultWavFile(pathToVoicebank, fileName);
                }

                // Ignore missing wav files and don't make them available
                if (!lyricFile.exists()) {
                    missingWavFiles.add(lyricFile.getName());
                    continue;
                }

                if (lyricName.isEmpty()) {
                    // If no alias provided, use the file name as an adhoc alias.
                    lyricName = fileName.substring(0, fileName.length() - 4);
                }
                String[] configValues = line.substring(matcher.end()).split(",");
                if (configValues.length != 5 || fileName == null || lyricName == null) {
                    System.out.println("Received unexpected results while parsing oto.ini");
                    continue;
                }

                // Search for a frq file.
                String frqName = LyricConfig.getDefaultFrqFileName(fileName);
                File frqFile;

                if (osNames.containsKey(frqName)) {
                    // There may be a variation on the actual file name
                    frqFile = new File(osNames.get(frqName));
                } else {
                    // This is the expected file name
                    frqFile = LyricConfig.getDefaultFrqFile(lyricFile);
                    if (!frqFile.exists()) {
                        bank.createFrq(lyricFile, frqFile);
                    }    
                }

                builder.addLyric(
                        new LyricConfig(
                                fileName,
                                lyricFile,
                                frqFile,
                                lyricName,
                                configValues),
                        frqFile.canRead());
            }
        }

        if (missingWavFiles.size() > 0) {
            System.out.println("Cound not find: " + String.join(", ", missingWavFiles));
        }
    }

    private void parsePitchMap(File pitchMapFile, Voicebank.Builder builder) {
        String pitchData = readConfigFile(pitchMapFile);
        for (String rawLine : pitchData.split("\n")) {
            String line = rawLine.trim();
            // TODO: Handle the case of prefixes rather than suffixes.
            Matcher matcher = PITCH_PATTERN.matcher(line);
            if (matcher.find()) {
                String pitch = matcher.group(1);
                String suffix = matcher.group(2);
                builder.addPitchSuffix(pitch, suffix);
            }
        }
    }

    /* Gets disjoint set used for romaji-hiragana-katakana conversions. */
    private void readLyricConversionsFromFile(Voicebank.Builder builder) {
        String conversionData = readConfigFile(lyricConversionPath);
        for (String line : conversionData.split("\n")) {
            builder.addConversionGroup(line.trim().split(","));
        }
    }

    private String readConfigFile(File file) {
        if (!file.canRead() || !file.isFile()) {
            // This is often okay.
            return "";
        }
        try {
            return FileHelper.readByteArray(FileUtils.readFileToByteArray(file));
        } catch (IOException e) {
            // TODO Handle this.
            errorLogger.logError(e);
        }
        return "";
    }

    /**
     * Parses a file path, and replaces the strings "${DEFAULT}" and "${HOME}" with their
     * corresponding directories.
     */
    public File parseFilePath(String line, String property) {
        String pathString = line.substring(property.length());
        pathString = pathString.replaceFirst("\\$\\{DEFAULT\\}", defaultVoicePath.getAbsolutePath())
                .replaceFirst("\\$\\{HOME\\}", System.getProperty("user.home"));
        return new File(pathString);
    }

    private HashMap<String, String> getOSFileNames(String path) {

        // TODO: Fix this for other OSs
        if (!System.getProperty("os.name").toLowerCase().contains("win")) return new HashMap<String, String>();

        File dir = new File(path);
        HashMap<String, String> fileMap = new HashMap<>();
        final String lineDelimiter = "!!!!";
        final String fieldDelimiter = " :: ";

        // Windows filenames are not reliable when calling external executables
        try {
            String cmd = "cmd /c for %I in (\"" + dir.getAbsolutePath() + "\\*.*\") do @echo %~nxI" + fieldDelimiter + "%~fsI" + lineDelimiter;
            String output = new ExternalProcessRunner().getProcessOutput(cmd);

            // All of the lines from the directory scan
            String[] dirLines = output
                            .replaceAll("\\r\\n", "")
                            .split(lineDelimiter);

            // Add the files to the file map
            Arrays.stream(dirLines)
                            .filter(l -> !l.isEmpty() && (l.contains(".wav") || l.contains(".frq")))
                            .map(l -> l.split(fieldDelimiter))
                            .filter(l -> l.length == 2)
                            .forEach(l -> fileMap.put(l[0], l[1]));
            
        } catch (Exception e) {
            errorLogger.logError(e);
        }

        return fileMap;
    }
}
