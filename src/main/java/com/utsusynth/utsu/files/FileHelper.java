package com.utsusynth.utsu.files;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;

import com.utsusynth.utsu.common.exception.ErrorLogger;

public class FileHelper {

    private static final ErrorLogger errorLogger = ErrorLogger.getLogger();

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
}