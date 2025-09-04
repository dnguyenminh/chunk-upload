package vn.com.fecredit.chunkedupload.model.util;

/**
 * Utility class for managing bitset operations in chunked file uploads.
 *
 * <p>
 * This class provides methods for:
 * <ul>
 * <li>Setting and managing used/unused bits in a byte array bitset</li>
 * <li>Converting between bitset and list representations</li>
 * <li>Bit manipulation operations for chunk tracking</li>
 * </ul>
 * <p>
 * The bitset is used to track uploaded chunks where:
 * <ul>
 * <li>0 indicates a chunk has not been uploaded</li>
 * <li>1 indicates a chunk has been uploaded</li>
 * </ul>
 */
public class BitsetUtil {

    /**
     * Sets unused bits in the given bitset array based on the total number of chunks.
     * <p>
     * For the specified totalChunks, this method sets the corresponding bits in the bitset array to 0 or 1
     * to indicate unused or used bits. Bits beyond totalChunks are set to unused.
     *
     * @param bitset      the byte array representing the bitset
     * @param totalChunks the total number of chunks to consider
     */
    public static void setUnusedBits(byte[] bitset, int totalChunks) {
        if (null != bitset) {
            int zeroLength = (int) Math.floor((double) totalChunks / 8);
            for (int i = 0; i < zeroLength; i++) {
                bitset[i] = (byte) 0;
            }
            if (zeroLength * 8 < totalChunks) {
                int oneLength = bitset.length - zeroLength - 1;
                for (int i = 0; i < oneLength; i++) {
                    bitset[i] = (byte) 0xFF;
                }
                int highOneLength = totalChunks % 8;
                bitset[zeroLength] = (byte) (0xFF << highOneLength);
            }
        }
    }

    /**
     * Sets a specific bit to 1 in the bitset to mark it as used.
     *
     * <p>
     * This method is used to track uploaded chunks by marking their corresponding
     * bits as used (1) in the bitset. The bit position is calculated from the
     * chunk index.
     *
     * @param bitset   The byte array representing the bitset
     * @param bitIndex The index of the bit to set (chunk number)
     */
    public static void setUsedBit(byte[] bitset, int bitIndex) {
        if (null != bitset && bitIndex >= 0) {
            int byteIndex = bitIndex / 8;
            int bitPosition = bitIndex % 8;
            if (byteIndex < bitset.length) {
                bitset[byteIndex] = (byte) (bitset[byteIndex] | (1 << bitPosition));
            }
        }
    }

    /**
     * Converts a bitset mask to a list of integers representing set bit indices.
     *
     * <p>
     * This method scans through the bitset and creates a list containing the indices
     * of all bits that are set to 1. This is useful for:
     * <ul>
     * <li>Identifying which chunks have been uploaded</li>
     * <li>Determining missing chunks</li>
     * <li>Validating upload completion</li>
     * </ul>
     *
     * @param bitset The byte array representing the bitset
     * @return A list of integers where each integer is the index of a set bit (1)
     */
    public static java.util.List<Integer> bitsetToList(byte[] bitset) {
        java.util.List<Integer> indices = new java.util.ArrayList<>();
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
     *
     * <p>
     * This operation is performed in-place, modifying the original bitset.
     * It is useful for:
     * <ul>
     * <li>Converting between uploaded/not uploaded chunk representations</li>
     * <li>Finding missing chunks by inverting the completed chunks bitset</li>
     * <li>Bitwise complement operations in chunk tracking</li>
     * </ul>
     *
     * @param bitset The byte array representing the bitset to invert
     * @return The inverted bitset (same array instance)
     */
    public static byte[] invertBits(byte[] bitset) {
        if (bitset != null) {
            for (int i = 0; i < bitset.length; i++) {
                bitset[i] = (byte) ~bitset[i];
            }
        }
        return bitset;
    }
}
