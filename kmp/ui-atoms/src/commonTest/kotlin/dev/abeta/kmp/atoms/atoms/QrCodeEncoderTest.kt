package dev.abeta.kmp.atoms.atoms

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class QrCodeEncoderTest {
    @Test
    fun selectsIsoVersionAndMatrixSizeAcrossVersions1To10() {
        val v1 = QrCodeEncoder.encode("abeta://1")
        assertEquals(21, v1.size)
        assertTrue(v1.all { it.size == 21 })

        val v2 = QrCodeEncoder.encode("abeta://order/4091")
        assertEquals(25, v2.size)

        val v7 = QrCodeEncoder.encode("A".repeat(120))
        assertEquals(45, v7.size)

        val v10 = QrCodeEncoder.encode("B".repeat(213))
        assertEquals(57, v10.size)

        // Payloads longer than Version 10 EC-M byte capacity (213 bytes) truncate gracefully without crashing.
        val truncated = QrCodeEncoder.encode("C".repeat(300))
        assertEquals(57, truncated.size)
        assertEquals(QrCodeEncoder.encode("C".repeat(213)), truncated)
    }

    @Test
    fun placesFinderSeparatorTimingAlignmentAndDarkModulePerIso18004() {
        val v1 = QrCodeEncoder.encode("Hello")
        assertFinderAndSeparators(v1)
        assertTimingPatterns(v1)
        // Dark module at (4 * version + 9, 8) -> (13, 8) for Version 1.
        assertTrue(v1[13][8], "Version 1 dark module at (13, 8) must be dark")

        val v2 = QrCodeEncoder.encode("abeta://order/4091")
        assertFinderAndSeparators(v2)
        assertTimingPatterns(v2)
        // Version 2 dark module at (17, 8) and 5x5 alignment pattern centered at (18, 18).
        assertTrue(v2[17][8], "Version 2 dark module at (17, 8) must be dark")
        assertAlignmentPattern(v2, centerRow = 18, centerCol = 18)

        val v7 = QrCodeEncoder.encode("X".repeat(115))
        assertFinderAndSeparators(v7)
        assertTimingPatterns(v7)
        assertTrue(v7[37][8], "Version 7 dark module at (37, 8) must be dark")
        assertAlignmentPattern(v7, centerRow = 22, centerCol = 22)
        assertAlignmentPattern(v7, centerRow = 38, centerCol = 38)
    }

    @Test
    fun computesStandardBchFormatInfoAndVersionInfoBits() {
        // ISO/IEC 18004 Table C.1 for Error Correction Level M (format bits 00) across masks 0..7.
        val expectedFormatM =
            intArrayOf(
                0x5412,
                0x5125,
                0x5E7C,
                0x5B4B,
                0x45F9,
                0x40CE,
                0x4F97,
                0x4AA0,
            )
        for (mask in expectedFormatM.indices) {
            assertEquals(expectedFormatM[mask], QrCodeEncoder.computeFormatInfoBits(mask), "Mismatch for mask $mask")
        }

        // ISO/IEC 18004 Table D.1 version info strings for Versions 7..10.
        assertEquals(0x07C94, QrCodeEncoder.computeVersionInfoBits(7))
        assertEquals(0x085BC, QrCodeEncoder.computeVersionInfoBits(8))
        assertEquals(0x09A99, QrCodeEncoder.computeVersionInfoBits(9))
        assertEquals(0x0A4D3, QrCodeEncoder.computeVersionInfoBits(10))

        // Verify both format copies in the rendered matrix match identically and are valid EC-M codes.
        val matrix = QrCodeEncoder.encode("abeta://pay/42")
        val copy1 = readFormatCopy1(matrix)
        val copy2 = readFormatCopy2(matrix)
        assertEquals(copy1, copy2)
        assertTrue(copy1 in expectedFormatM, "Format info 0x${copy1.toString(16)} must be a valid EC-M BCH word")
    }

    @Test
    fun computesValidGf256ReedSolomonCheckBytes() {
        // Known ISO/IEC 18004 V1-M Byte-Mode "Hello" data codewords (16 data + 10 EC).
        val spec = QrCodeEncoder.selectVersion(5)
        assertEquals(1, spec.version)
        val data = QrCodeEncoder.encodeDataCodewords("Hello".encodeToByteArray(), spec)
        assertEquals(16, data.size)
        // First two bytes: mode (0100) + len high nibble (0000) = 0x40; len low nibble (0101) + 'H'(0x48) high = 0x54.
        assertEquals(0x40, data[0])
        assertEquals(0x54, data[1])

        val ecc = QrCodeEncoder.computeReedSolomonEcc(data, ecCount = spec.ecPerBlock)
        assertEquals(10, ecc.size)

        // Every valid RS codeword polynomial C(x) vanishes at alpha^0 .. alpha^(ecCount - 1) in GF(256).
        val codeword = data + ecc
        var root = 1
        repeat(spec.ecPerBlock) { power ->
            var syndrome = 0
            for (coeff in codeword) {
                syndrome = QrCodeEncoder.gfMultiply(syndrome, root) xor coeff
            }
            assertEquals(0, syndrome, "Reed-Solomon syndrome at alpha^$power must be zero")
            root = QrCodeEncoder.gfMultiply(root, 2)
        }

        // Known ISO/IEC 18004 Annex I reference vector: 16 data bytes -> 10 EC bytes.
        val isoData =
            intArrayOf(
                0x10,
                0x20,
                0x0C,
                0x56,
                0x61,
                0x80,
                0xEC,
                0x11,
                0xEC,
                0x11,
                0xEC,
                0x11,
                0xEC,
                0x11,
                0xEC,
                0x11,
            )
        val isoExpectedEcc = intArrayOf(0xA5, 0x24, 0xD4, 0xC1, 0xED, 0x36, 0xC7, 0x87, 0x2C, 0x55)
        assertContentEquals(isoExpectedEcc, QrCodeEncoder.computeReedSolomonEcc(isoData, ecCount = 10))
    }

    private fun assertFinderAndSeparators(matrix: List<List<Boolean>>) {
        val size = matrix.size
        val origins = listOf(0 to 0, 0 to (size - 7), (size - 7) to 0)
        for ((r0, c0) in origins) {
            for (dr in 0 until 7) {
                for (dc in 0 until 7) {
                    val onOuter = dr == 0 || dr == 6 || dc == 0 || dc == 6
                    val inCore = dr in 2..4 && dc in 2..4
                    assertEquals(onOuter || inCore, matrix[r0 + dr][c0 + dc], "Finder mismatch at ($r0+$dr,$c0+$dc)")
                }
            }
        }
        // White 1-module separators around all three finders.
        for (i in 0..7) {
            assertFalse(matrix[7][i])
            assertFalse(matrix[i][7])
            assertFalse(matrix[7][size - 1 - i])
            assertFalse(matrix[i][size - 8])
            assertFalse(matrix[size - 8][i])
            assertFalse(matrix[size - 1 - i][7])
        }
    }

    private fun assertTimingPatterns(matrix: List<List<Boolean>>) {
        val size = matrix.size
        for (i in 8 until size - 8) {
            val expectedDark = i % 2 == 0
            assertEquals(expectedDark, matrix[6][i], "Horizontal timing mismatch at col $i")
            assertEquals(expectedDark, matrix[i][6], "Vertical timing mismatch at row $i")
        }
    }

    private fun assertAlignmentPattern(
        matrix: List<List<Boolean>>,
        centerRow: Int,
        centerCol: Int,
    ) {
        for (dr in -2..2) {
            for (dc in -2..2) {
                val expectedDark = maxOf(kotlin.math.abs(dr), kotlin.math.abs(dc)) != 1
                assertEquals(
                    expectedDark,
                    matrix[centerRow + dr][centerCol + dc],
                    "Alignment mismatch at ($centerRow+$dr,$centerCol+$dc)",
                )
            }
        }
    }

    private fun readFormatCopy1(matrix: List<List<Boolean>>): Int {
        var bits = 0
        for (col in 0..5) {
            if (matrix[8][col]) bits = bits or (1 shl (14 - col))
        }
        if (matrix[8][7]) bits = bits or (1 shl 8)
        if (matrix[8][8]) bits = bits or (1 shl 7)
        if (matrix[7][8]) bits = bits or (1 shl 6)
        for (row in 0..5) {
            if (matrix[row][8]) bits = bits or (1 shl row)
        }
        return bits
    }

    private fun readFormatCopy2(matrix: List<List<Boolean>>): Int {
        val size = matrix.size
        var bits = 0
        for (i in 0..6) {
            if (matrix[size - 1 - i][8]) bits = bits or (1 shl (14 - i))
        }
        for (i in 0..7) {
            if (matrix[8][size - 8 + i]) bits = bits or (1 shl (7 - i))
        }
        return bits
    }
}
