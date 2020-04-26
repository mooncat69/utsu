package com.utsusynth.utsu.files;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.util.UUID;

import com.utsusynth.utsu.common.exception.ErrorLogger;

import org.apache.commons.io.FileUtils;

public class FileHelper {

    private static final ErrorLogger errorLogger = ErrorLogger.getLogger();
    private static final boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
    private static final String userDir = deriveUserDirectory();
    private static final String utsuUserDir = deriveUtsuUserDir();
    private static final String utsuTempDir = deriveUtsuTempDir();
    private static final String utsuCacheDir = deriveUtsuCacheDir();

    /***
     * Reads a byte array and tries to form a valid string. Will resort to Shift JIS encoding if this fails
     * @param bytes Bytes to decode
     * @return A correctly decoded string, based upon the source encoding
     */
    public static String readByteArray(byte[] bytes) {
        try {
            String charset = "UTF-8";
            CharsetDecoder utf8Decoder =
                    Charset.forName("UTF-8").newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                            .onUnmappableCharacter(CodingErrorAction.REPORT);
            try {
                // Test for UTF-8
                utf8Decoder.decode(ByteBuffer.wrap(bytes));
            } catch (CharacterCodingException e) {
                charset = "SJIS";
            }
            return new String(bytes, charset);
        } catch (UnsupportedEncodingException e) {
            // TODO Handle this.
            errorLogger.logError(e);
            return "";
        }
    }

    public static String getCharSet(File file) throws IOException {
        try {
            var bytes = FileUtils.readFileToByteArray(file);
            String charset = "UTF-8";
            CharsetDecoder utf8Decoder =
                    Charset.forName("UTF-8").newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                            .onUnmappableCharacter(CodingErrorAction.REPORT);
            try {
                // Test for UTF-8
                utf8Decoder.decode(ByteBuffer.wrap(bytes));
            } catch (CharacterCodingException e) {
                charset = "SJIS";
            }
            return charset;
        } catch (UnsupportedEncodingException e) {
            // TODO Handle this.
            errorLogger.logError(e);
            return "";
        }
    }

    public static String readTextFile(File file) throws IOException {
        return readByteArray(FileUtils.readFileToByteArray(file));
    }

    public static String getUtsuDirectory() {
        return utsuUserDir;
    }

    public static String getUserDirectory() {
        return userDir;
    }

    public static String getUtsuTempDirectory() {
        return utsuTempDir;
    }

    public static String getUtsuCacheDirectory() {
        return utsuCacheDir;
    }

    public static File createTempFile(String prefix, String suffix) throws IOException {
        var filename = getUtsuTempDirectory() + prefix + UUID.randomUUID().toString() + suffix;
        var file = new File(filename);
        file.deleteOnExit();
        return file;
    }

    private static String deriveUserDirectory() {

        var homeDir = "";

        if (isWindows) {
            // Windows only environment variables
            homeDir = System.getenv("LOCALAPPDATA");

            if (homeDir.isBlank()) {
                homeDir = System.getenv("APPDATA");
            }

            if (homeDir.isBlank()) {
                homeDir = System.getenv("HOMEPATH");
                if (!homeDir.isBlank()) homeDir += "/Documents";
            }
        }

        if (homeDir.isBlank()) {
            homeDir = FileUtils.getUserDirectory().getAbsolutePath();
        }

        // Make sure the path ends with '/'
        if (!homeDir.endsWith("\\") && !homeDir.endsWith("/")) homeDir += "/";

        // Make sure this path exists
        new File(homeDir).mkdirs();

        return homeDir;
    }

    private static String deriveUtsuUserDir() {
        var dirName = userDir + (isWindows ? "UTSU/" : ".utsu/");
        new File(dirName).mkdirs();
        return dirName;
    }

    private static String deriveUtsuTempDir() {
        var dirName = utsuUserDir + (isWindows ? "Temp/" : "tmp/");
        new File(dirName).mkdirs();
        return dirName;
    }

    private static String deriveUtsuCacheDir() {
        var dirName = utsuUserDir + (isWindows ? "Cache/" : "cache/");
        new File(dirName).mkdirs();
        return dirName;
    }
}