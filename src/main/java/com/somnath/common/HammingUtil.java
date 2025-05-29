// --- src\main\java\com\somnath\common\HammingUtil.java ---
package com.somnath.common;

import java.util.Random;

public class HammingUtil {

    private static final Random random = new Random(); // Used for randomly flipping a bit in simulateError

    /**
     * Converts a byte array into a binary string (padded to 8 bits per byte).
     */
    public static String bytesToBinaryString(byte[] bytes) {
        StringBuilder binary = new StringBuilder();
        for (byte b : bytes) {
            int val = b;
            // Iterate through bits from most significant to least significant
            for (int i = 0; i < 8; i++) {
                binary.append((val & 128) == 0 ? 0 : 1);
                val <<= 1; // Shift left to check the next bit
            }
        }
        return binary.toString();
    }

    /**
     * Converts a binary string back into a byte array.
     * Assumes the binary string length is a multiple of 8.
     */
    public static byte[] binaryStringToBytes(String binaryString) {
        if (binaryString.length() % 8 != 0) {
            // This shouldn't happen with correctly encoded/decoded binary that originated from bytes
            throw new IllegalArgumentException("Binary string length (" + binaryString.length() + ") must be a multiple of 8.");
        }
        byte[] bytes = new byte[binaryString.length() / 8];
        for (int i = 0; i < bytes.length; i++) {
            int byteValue = 0;
            // Read 8 bits to form a byte
            for (int j = 0; j < 8; j++) {
                // Shift the current value left by 1, then add the new bit (0 or 1)
                byteValue = (byteValue << 1) | (binaryString.charAt(i * 8 + j) - '0');
            }
            bytes[i] = (byte) byteValue;
        }
        return bytes;
    }

    /**
     * Encodes a binary string using Hamming(7,4).
     * Pads the input binary string with '0's if its length is not a multiple of 4.
     * Operates on 4-bit data blocks (d3 d2 d1 d0). Output is 7-bit encoded blocks (p0 p1 p2 d3 d2 d1 d0).
     * Bit order in 7-bit block: p0 p1 p2 d3 d2 d1 d0 (indices 0 to 6)
     * Parity bit coverage (by index in 7-bit block):
     * p0 (index 0) covers bits at indices 0, 3, 4, 6 (p0, d3, d2, d0)
     * p1 (index 1) covers bits at indices 1, 3, 5, 6 (p1, d3, d1, d0)
     * p2 (index 2) covers bits at indices 2, 4, 5, 6 (p2, d2, d1, d0)
     */
    public static String encode(String binaryInput) {
        // Pad input to be a multiple of 4 bits
        int paddingNeeded = (4 - (binaryInput.length() % 4)) % 4;
        String paddedInput = binaryInput + "0".repeat(paddingNeeded);

        StringBuilder encodedBinary = new StringBuilder();
        for (int i = 0; i < paddedInput.length(); i += 4) {
            String dataBlock = paddedInput.substring(i, i + 4);
            // dataBlock is d3 d2 d1 d0 (e.g., "1101")
            // The bits in the *input* string correspond to:
            int d3 = dataBlock.charAt(0) - '0'; // This will go to index 3 in the 7-bit encoded block
            int d2 = dataBlock.charAt(1) - '0'; // This will go to index 4
            int d1 = dataBlock.charAt(2) - '0'; // This will go to index 5
            int d0 = dataBlock.charAt(3) - '0'; // This will go to index 6

            // Calculate parity bits (p0, p1, p2) based on the standard coverage for the p0 p1 p2 d3 d2 d1 d0 layout
            // p0 covers bits at positions 1, 3, 5, 7 (1-based index) -> corresponds to d3, d2, d0 (0-based indices 3, 4, 6 in encoded)
            // p1 covers bits at positions 2, 3, 6, 7 (1-based index) -> corresponds to d3, d1, d0 (0-based indices 3, 5, 6 in encoded)
            // p2 covers bits at positions 4, 5, 6, 7 (1-based index) -> corresponds to d2, d1, d0 (0-based indices 4, 5, 6 in encoded)

            int p0 = d3 ^ d2 ^ d0;
            int p1 = d3 ^ d1 ^ d0;
            int p2 = d2 ^ d1 ^ d0;


            // Construct the 7-bit encoded word: p0 p1 p2 d3 d2 d1 d0
            encodedBinary.append(p0).append(p1).append(p2).append(d3).append(d2).append(d1).append(d0);
        }
        return encodedBinary.toString();
    }


    /**
     * Decodes a Hamming(7,4) binary string.
     * Assumes the input binary string is corrected and its length is a multiple of 7.
     * Extracts the original 4-bit data blocks (d3 d2 d1 d0).
     * Input block is p0 p1 p2 d3 d2 d1 d0 (indices 0 to 6).
     */
    public static String decode(String correctedBinaryInput) {
        if (correctedBinaryInput.length() % 7 != 0) {
            System.err.println("Hamming decode input length (" + correctedBinaryInput.length() + ") not a multiple of 7!");
            // This should not happen if correctError returns multiple of 7
            throw new IllegalArgumentException("Corrected binary string length must be a multiple of 7.");
        }

        StringBuilder decodedBinary = new StringBuilder();
        for (int i = 0; i < correctedBinaryInput.length(); i += 7) {
            String encodedBlock = correctedBinaryInput.substring(i, i + 7);
            // encodedBlock is p0 p1 p2 d3 d2 d1 d0
            // Indices:         0  1  2  3  4  5  6

            // Extract data bits from their fixed positions in the 7-bit block
            char d3 = encodedBlock.charAt(3);
            char d2 = encodedBlock.charAt(4);
            char d1 = encodedBlock.charAt(5);
            char d0 = encodedBlock.charAt(6);

            // Append data bits in the original d3 d2 d1 d0 order
            decodedBinary.append(d3).append(d2).append(d1).append(d0);
        }
        // The padding '0's added during encode will remain here.
        return decodedBinary.toString();
    }


    /**
     * Simulates a single-bit error in a binary string.
     * Selects a random bit and flips it.
     * @return The binary string with one bit flipped.
     */
    public static String simulateError(String binaryInput) {
        if (binaryInput == null || binaryInput.isEmpty()) {
            return binaryInput;
        }
        StringBuilder errored = new StringBuilder(binaryInput);
        int randomIndex = random.nextInt(errored.length());
        char flippedBit = (errored.charAt(randomIndex) == '0') ? '1' : '0';
        errored.setCharAt(randomIndex, flippedBit);
        return errored.toString();
    }

    /**
     * Corrects a single-bit error in a Hamming(7,4) binary string.
     * Assumes the input length is a multiple of 7.
     * Returns the corrected binary string.
     * Updates error flags in the Message object.
     * @param erroredBinaryInput The binary string with potential error.
     * @param message The Message object to update error flags.
     * @return The corrected binary string.
     */
    public static String correctError(String erroredBinaryInput, Message message) {
        if (erroredBinaryInput.length() % 7 != 0) {
            System.err.println("Hamming correct input length (" + erroredBinaryInput.length() + ") not a multiple of 7!");
            // If input length is wrong, we cannot correct.
            message.setErrorDetected(false); // Cannot even check
            message.setErrorCorrected(false);
            return erroredBinaryInput; // Return as is, decryption will likely fail anyway
        }

        StringBuilder correctedBinary = new StringBuilder(erroredBinaryInput);
        boolean errorDetectedInAnyBlock = false; // Flag to track if *any* block had a detectable error

        for (int i = 0; i < erroredBinaryInput.length(); i += 7) {
            String block = erroredBinaryInput.substring(i, i + 7);
            // Block is p0 p1 p2 d3 d2 d1 d0
            // Indices (0-based): 0  1  2  3  4  5  6

            // Extract received bits from the current block
            int r_p0 = block.charAt(0) - '0';
            int r_p1 = block.charAt(1) - '0';
            int r_p2 = block.charAt(2) - '0';
            int r_d3 = block.charAt(3) - '0';
            int r_d2 = block.charAt(4) - '0';
            int r_d1 = block.charAt(5) - '0';
            int r_d0 = block.charAt(6) - '0';

            // Calculate syndrome bits s0, s1, s2 based on standard parity checks:
            // s0 = Check bit 1 (p0): p0 ^ d3 ^ d2 ^ d0  (Covers indices 0, 3, 4, 6)
            // s1 = Check bit 2 (p1): p1 ^ d3 ^ d1 ^ d0  (Covers indices 1, 3, 5, 6)
            // s2 = Check bit 4 (p2): p2 ^ d2 ^ d1 ^ d0  (Covers indices 2, 4, 5, 6)
            int s0 = r_p0 ^ r_d3 ^ r_d2 ^ r_d0;
            int s1 = r_p1 ^ r_d3 ^ r_d1 ^ r_d0;
            int s2 = r_p2 ^ r_d2 ^ r_d1 ^ r_d0;

            // Syndrome word: s2 s1 s0 (Binary representation of the 1-based error position, from 1 to 7)
            int syndrome = (s2 << 2) | (s1 << 1) | s0;

            if (syndrome != 0) {
                // If syndrome is non-zero, an error was detected in this block.
                // Syndrome value (1-7) corresponds to the position of the error bit (1-based).
                // Convert 1-based index to 0-based index within the block (0-6).
                int errorIndexInBlock = syndrome - 1;

                // Check if the calculated error index is within the valid range (0-6) for a 7-bit block.
                // A syndrome of 0 means no error. A non-zero syndrome outside 1-7 is impossible for a single-bit error in (7,4).
                // If syndrome > 7, it indicates a multi-bit error or severe corruption that Hamming(7,4) can't reliably handle.
                // For simplicity in this demo, we assume only single-bit errors occur and the syndrome is always 1-7 if non-zero.
                if (errorIndexInBlock >= 0 && errorIndexInBlock < 7) {
                    // Flip the bit at the error position within this block in the corrected string builder
                    int globalErrorIndex = i + errorIndexInBlock;
                    char oldBit = correctedBinary.charAt(globalErrorIndex);
                    char newBit = (oldBit == '0') ? '1' : '0';
                    correctedBinary.setCharAt(globalErrorIndex, newBit);

                    errorDetectedInAnyBlock = true; // Set flag for the whole message
                } else {
                    // This case should theoretically not happen with single-bit errors in (7,4)
                    // but handling it prevents potential IndexOutOfBoundsException if syndrome logic were flawed.
                    System.err.println("Hamming correction calculated invalid syndrome position: " + syndrome);
                }

            }
        }

        // Update error flags in the Message object based on what happened during correction
        message.setErrorDetected(errorDetectedInAnyBlock); // True if *any* block had a detectable error
        // Assuming only single errors were detected, they were also corrected by H(7,4)
        message.setErrorCorrected(errorDetectedInAnyBlock); // Error detected implies corrected

        return correctedBinary.toString();
    }
}