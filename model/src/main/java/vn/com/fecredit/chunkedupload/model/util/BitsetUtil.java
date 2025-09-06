package vn.com.fecredit.chunkedupload.model.util;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for managing bitset operations in chunked file uploads.
 */
public class BitsetUtil {

    /**
     * Sets unused bits in the given bitset array to 1.
     * Bits within the range of totalChunks are not modified.
     */
    public static void setUnusedBits(byte[] bitset, int totalChunks) {
        if (bitset == null) return;
        for (int i = totalChunks; i < bitset.length * 8; i++) {
            setUsedBit(bitset, i);
        }
    }

    /**
     * Sets a specific bit to 1 in the bitset to mark it as used.
     */
    public static void setUsedBit(byte[] bitset, int bitIndex) {
        if (bitset != null && bitIndex >= 0) {
            int byteIndex = bitIndex / 8;
            int bitPosition = bitIndex % 8;
            if (byteIndex < bitset.length) {
                bitset[byteIndex] |= (byte) (1 << bitPosition);
            }
        }
    }

    /**
     * Checks if all bits up to totalChunks are set to 1 (used).
     */
    public static boolean isUsedBitSetFull(byte[] bitset) {
        for (int i = 0; i < bitset.length; i++) {
            if (bitset[i] != (byte) 0xFF) { // cast to byte is safer
                return false; // found a byte not fully set
            }
        }
        return true; // all bytes are 0xFF
    }

    /**
     * Converts a bitset mask to a list of integers representing set bit indices.
     */
    public static List<Integer> bitsetToList(byte[] bitset) {
        List<Integer> indices = new ArrayList<>();
        if (bitset != null) {
            for (int byteIdx = 0; byteIdx < bitset.length; byteIdx++) {
                for (int bit = 0; bit < 8; bit++) {
                    if ((bitset[byteIdx] & (1 << bit)) != 0) {
                        indices.add(byteIdx * 8 + bit);
                    }
                }
            }
        }
        return indices;
    }

    /**
     * Inverts all bits in the given bitset: 0 becomes 1, 1 becomes 0.
     */
    public static byte[] invertBits(byte[] bitset) {
        if (bitset != null) {
            for (int i = 0; i < bitset.length; i++) {
                bitset[i] = (byte) ~bitset[i];
            }
        }
        return bitset;
    }

    /**
     * Converts the bitset to a string representation of bits (e.g., "01001101").
     */
    public static String bitsetToString(byte[] bitset) {
        StringBuilder sb = new StringBuilder();
        if (null != bitset && bitset.length > 0)
            for (int i = bitset.length - 1; i > -1; i--) {
                byte b = bitset[i];
                sb.append(String.format("%8s", Integer.toBinaryString(b & 0xFF)).replace(' ', '0')).append(" ");
            }
        return sb.toString().trim();
    }
}
