package jbuild.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * SHA1 hash helper.
 */
public final class SHA1 {

    /**
     * Compute the SHA1 of the given bytes.
     *
     * @param bytes input bytes
     * @return SHA1 of bytes
     */
    public static byte[] computeSha1(byte[] bytes) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            // the JVM must provide the SHA-1 algorithm, so just re-throw if something is wrong!
            throw new RuntimeException(e);
        }
        return digest.digest(bytes);
    }

    /**
     * Convert contents of a SHA1 file into the actual SHA1 bytes for verification.
     *
     * @param bytes bytes of SHA1 file
     * @return SHA1 bytes
     */
    public static byte[] fromSha1StringBytes(byte[] bytes) {
        if (bytes.length != 40) {
            throw new IllegalArgumentException("Not a SHA1 string");
        }
        var result = new byte[20];
        for (int i = 39, j = 19; i > 0; i -= 2) {
            var b = fromHexDigit((char) bytes[i]);
            var c = fromHexDigit((char) bytes[i - 1]);
            result[j--] = (byte) (b + (c << 4));
        }
        return result;
    }

    private static byte fromHexDigit(char c) {
        if ('0' <= c && c <= '9') return (byte) (c - 48);
        if ('a' <= c && c <= 'f') return (byte) (c - 87);
        throw new IllegalArgumentException("Not a SHA1 string");
    }

}
