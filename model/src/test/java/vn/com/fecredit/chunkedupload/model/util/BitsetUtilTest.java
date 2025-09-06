package vn.com.fecredit.chunkedupload.model.util;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BitsetUtilTest {
    /**
     * Tests setting unused bits for 5 chunks in a single byte.
     *
     * <p>
     * Expected result: 5 lower bits available (00011111),
     * 3 upper bits marked as unused (11100000 = 0xE0)
     */
    @Test
    void testSetUnusedBits_5Chunks_1Byte() {
        byte[] bitset = new byte[1];
        BitsetUtil.setUnusedBits(bitset, 5);
        System.out.println(BitsetUtil.bitsetToString(bitset));
        assertEquals((byte) 0xE0, bitset[0], "Bitset: " + BitsetUtil.bitsetToString(bitset));
    }

    @Test
    void testSetUnusedBits_8Chunks_1Byte() {
        byte[] bitset = new byte[1];
        BitsetUtil.setUnusedBits(bitset, 8);
        System.out.println(BitsetUtil.bitsetToString(bitset));
        assertEquals((byte) 0x00, bitset[0], "Bitset: " + BitsetUtil.bitsetToString(bitset));
    }

    @Test
    void testSetUnusedBits_10Chunks_2Bytes() {
        byte[] bitset = new byte[2];
        BitsetUtil.setUnusedBits(bitset, 10);
        System.out.println(BitsetUtil.bitsetToString(bitset));
        assertEquals((byte) 0x00, bitset[0], "Bitset: " + BitsetUtil.bitsetToString(bitset));
        assertEquals((byte) 0xFC, bitset[1], "Bitset: " + BitsetUtil.bitsetToString(bitset));
    }

    @Test
    void testSetUnusedBitsAndSetUsedBit_combined() {
        byte[] bitset = new byte[2];
        // Set unused bits for 10 chunks
        BitsetUtil.setUnusedBits(bitset, 10);
        System.out.println(BitsetUtil.bitsetToString(bitset));
        // Set used bits for chunk indices 0, 1, 8, 9
        BitsetUtil.setUsedBit(bitset, 0);
        BitsetUtil.setUsedBit(bitset, 1);
        BitsetUtil.setUsedBit(bitset, 8);
        BitsetUtil.setUsedBit(bitset, 9);
        // Expect: bits 0,1,8,9 set, unused bits set by setUnusedBits
        // Print for debug
        System.out.println("Combined: " + BitsetUtil.bitsetToString(bitset));
        // Validate specific bits
        assertEquals((byte) 0x03, bitset[0], "First byte should have bits 0 and 1 set");
        assertEquals(0xFF03, ((bitset[1] & 0xFF) << 8) | (bitset[0] & 0xFF), "Combined bits check");
    }

    @Test
    void testSetUnusedBitsAndSetUsedBit_fullByte() {
        byte[] bitset = new byte[1];
        BitsetUtil.setUnusedBits(bitset, 5);
        System.out.println(BitsetUtil.bitsetToString(bitset));
        BitsetUtil.setUsedBit(bitset, 0);
        BitsetUtil.setUsedBit(bitset, 4);
        System.out.println("Combined full byte: " + BitsetUtil.bitsetToString(bitset));
        assertEquals((byte) 0xF1, bitset[0], "Bits 0 and 4 set, unused bits set");
    }

    /**
     * Tests setting unused bits for 16 chunks across 2 bytes.
     *
     * <p>
     * Expected result: All 16 bits available (all zeros),
     * No unused bits since chunk count matches byte boundary
     */
    @Test
    void testSetUnusedBits_16Chunks_2Bytes() {
        byte[] bitset = new byte[2];
        BitsetUtil.setUnusedBits(bitset, 16);
        System.out.println(BitsetUtil.bitsetToString(bitset));
        assertEquals((byte) 0x00, bitset[0], "Bitset: " + BitsetUtil.bitsetToString(bitset));
        assertEquals((byte) 0x00, bitset[1], "Bitset: " + BitsetUtil.bitsetToString(bitset));
        // Removed duplicate testSetUsedBit methods
    }

    @Test
    void testSetUsedBit_singleBit() {
        byte[] bitset = new byte[1];
        BitsetUtil.setUsedBit(bitset, 0);
        System.out.println(BitsetUtil.bitsetToString(bitset));
        assertEquals((byte) 0x01, bitset[0]);
    }

    @Test
    void testSetUsedBit_multipleBitsSameByte() {
        byte[] bitset = new byte[1];
        BitsetUtil.setUsedBit(bitset, 0);
        BitsetUtil.setUsedBit(bitset, 3);
        BitsetUtil.setUsedBit(bitset, 7);
        System.out.println(BitsetUtil.bitsetToString(bitset));
        assertEquals((byte) 0x89, bitset[0]);
    }

    @Test
    void testSetUsedBit_acrossBytes() {
        byte[] bitset = new byte[2];
        BitsetUtil.setUsedBit(bitset, 0);
        BitsetUtil.setUsedBit(bitset, 8);
        BitsetUtil.setUsedBit(bitset, 15);
        System.out.println(BitsetUtil.bitsetToString(bitset));
        assertEquals((byte) 0x01, bitset[0]);
        assertEquals((byte) 0x81, bitset[1]);
    }

    @Test
    void testSetUsedBit_outOfBounds() {
        byte[] bitset = new byte[1];
        BitsetUtil.setUsedBit(bitset, 8);
        System.out.println(BitsetUtil.bitsetToString(bitset));
        assertEquals((byte) 0x00, bitset[0]);
    }

    @Test
    void testSetUsedBit_negativeIndex() {
        byte[] bitset = new byte[1];
        BitsetUtil.setUsedBit(bitset, -1);
        // Removed all misplaced/duplicate setUsedBit test methods
        System.out.println(BitsetUtil.bitsetToString(bitset));
        assertEquals((byte) 0x00, bitset[0]);
    }

    @Test
    void testSetUnusedBits_17Chunks_3Bytes() {
        byte[] bitset = new byte[3];
        BitsetUtil.setUnusedBits(bitset, 17);
        System.out.println(BitsetUtil.bitsetToString(bitset));
        assertEquals((byte) 0xFE, bitset[2], "Bitset: " + BitsetUtil.bitsetToString(bitset));
        assertEquals((byte) 0x00, bitset[1], "Bitset: " + BitsetUtil.bitsetToString(bitset));
        assertEquals((byte) 0x00, bitset[0], "Bitset: " + BitsetUtil.bitsetToString(bitset));
    }

    @Test
    void testSetUnusedBits_24Chunks_3Bytes() {
        byte[] bitset = new byte[3];
        BitsetUtil.setUnusedBits(bitset, 24);
        System.out.println(BitsetUtil.bitsetToString(bitset));
        assertEquals((byte) 0x00, bitset[0], "Bitset: " + BitsetUtil.bitsetToString(bitset));
        assertEquals((byte) 0x00, bitset[1], "Bitset: " + BitsetUtil.bitsetToString(bitset));
        assertEquals((byte) 0x00, bitset[2], "Bitset: " + BitsetUtil.bitsetToString(bitset));
    }

    @Test
    void testSetUnusedBits_73Chunks_10Bytes() {
        byte[] bitset = new byte[10];
        BitsetUtil.setUnusedBits(bitset, 73);
        System.out.println(BitsetUtil.bitsetToString(bitset));
        for (int i = 0; i < 9; i++) {
            assertEquals((byte) 0x00, bitset[i], "Bitset: " + BitsetUtil.bitsetToString(bitset));
        }
        assertEquals((byte) 0xFE, bitset[9], "Bitset: " + BitsetUtil.bitsetToString(bitset));
    }

    /**
     * Tests basic conversion from bitset to list.
     *
     * <p>
     * Input: 10101010 00001111
     * Expected indices: 1,3,5,7 (from first byte) and 8,9,10,11 (from second byte)
     */
    @Test
    void testBitsetToList_basic() {
        byte[] bitset = new byte[]{(byte) 0b10101010, (byte) 0b00001111};
        List<Integer> result = BitsetUtil.bitsetToList(bitset);
        Assertions.assertEquals(
                java.util.Arrays.asList(1, 3, 5, 7, 8, 9, 10, 11),
                result
        );
    }

    /**
     * Tests bitset to list conversion with empty bitset.
     *
     * <p>
     * Tests that a bitset with no bits set returns an empty list.
     */
    @Test
    void testBitsetToList_empty() {
        byte[] bitset = new byte[]{0, 0};
        List<Integer> result = BitsetUtil.bitsetToList(bitset);
        Assertions.assertTrue(result.isEmpty());
    }

    /**
     * Tests bitset to list conversion with all bits set.
     *
     * <p>
     * Tests that a bitset with all bits set returns a complete
     * sequential list from 0 to 15 (for 2 bytes).
     */
    @Test
    void testBitsetToList_allSet() {
        byte[] bitset = new byte[]{(byte) 0xFF, (byte) 0xFF};
        List<Integer> result = BitsetUtil.bitsetToList(bitset);
        Assertions.assertEquals(
                java.util.Arrays.asList(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15),
                result
        );
    }

    @Test
    void testBitsetToString_NullInput() {
        assertEquals("", BitsetUtil.bitsetToString(null));
    }

    @Test
    void testBitsetToString_EmptyArray() {
        assertEquals("", BitsetUtil.bitsetToString(new byte[0]));
    }

    @Test
    void testBitsetToString_SingleByte() {
        // 0b01001101 = 77
        byte[] input = {(byte) 0b01001101};
        // Since the method iterates from last to first, and LSB to MSB, expected: "10110010 "
        assertEquals("01001101", BitsetUtil.bitsetToString(input));
    }

    @Test
    void testBitsetToString_MultiByte() {
        // Two bytes: 0b00000001 (1), 0b00000010 (2)
        byte[] input = {(byte) 0b00000001, (byte) 0b00000010};
        assertEquals("00000010 00000001", BitsetUtil.bitsetToString(input));
    }

}