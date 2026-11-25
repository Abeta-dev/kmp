@file:Suppress("MagicNumber", "TooManyFunctions")

package dev.abeta.kmp.atoms.atoms

import kotlin.math.abs

private const val MODE_BYTE = 0b0100
private const val EC_LEVEL_M_FORMAT_BITS = 0b00
private const val FORMAT_BCH_POLY = 0x537
private const val FORMAT_XOR_MASK = 0x5412
private const val VERSION_BCH_POLY = 0x1F25
private const val GF_REDUCING_POLY = 0x1D
private const val PAD_BYTE_EVEN = 0xEC
private const val PAD_BYTE_ODD = 0x11
private const val FINDER_SIZE = 7
private const val FINDER_INNER_START = 2
private const val FINDER_INNER_END = 4
private const val TIMING_INDEX = 6
private const val VERSION_INFO_MIN_VERSION = 7
private const val LONG_CHAR_COUNT_MIN_VERSION = 10
private const val MAX_V10_M_BYTES = 213
private const val PENALTY_RUN_BASE = 3
private const val PENALTY_RUN_MIN = 5
private const val PENALTY_BLOCK_WEIGHT = 3
private const val PENALTY_FINDER_LIKE_WEIGHT = 40
private const val PENALTY_BALANCE_WEIGHT = 10
private const val MASK_COUNT = 8

private val FINDER_LIKE_A = booleanArrayOf(true, false, true, true, true, false, true, false, false, false, false)
private val FINDER_LIKE_B = booleanArrayOf(false, false, false, false, true, false, true, true, true, false, true)

internal data class QrVersionSpec(
    val version: Int,
    val totalDataCodewords: Int,
    val ecPerBlock: Int,
    val g1Blocks: Int,
    val g1DataLen: Int,
    val g2Blocks: Int,
    val g2DataLen: Int,
    val alignmentCenters: IntArray,
) {
    val size: Int
        get() = 17 + 4 * version

    val charCountBits: Int
        get() = if (version < LONG_CHAR_COUNT_MIN_VERSION) 8 else 16

    val maxByteCapacity: Int
        get() = (totalDataCodewords * 8 - 4 - charCountBits) / 8
}

internal object QrCodeEncoder {
    private val versionSpecs: List<QrVersionSpec> =
        listOf(
            QrVersionSpec(1, 16, 10, 1, 16, 0, 0, intArrayOf()),
            QrVersionSpec(2, 28, 16, 1, 28, 0, 0, intArrayOf(6, 18)),
            QrVersionSpec(3, 44, 26, 1, 44, 0, 0, intArrayOf(6, 22)),
            QrVersionSpec(4, 64, 18, 2, 32, 0, 0, intArrayOf(6, 26)),
            QrVersionSpec(5, 86, 24, 2, 43, 0, 0, intArrayOf(6, 30)),
            QrVersionSpec(6, 108, 16, 4, 27, 0, 0, intArrayOf(6, 34)),
            QrVersionSpec(7, 124, 18, 4, 31, 0, 0, intArrayOf(6, 22, 38)),
            QrVersionSpec(8, 154, 22, 2, 38, 2, 39, intArrayOf(6, 24, 42)),
            QrVersionSpec(9, 182, 22, 3, 36, 2, 37, intArrayOf(6, 26, 46)),
            QrVersionSpec(10, 216, 26, 4, 43, 1, 44, intArrayOf(6, 28, 50)),
        )

    fun encode(payload: String): List<List<Boolean>> {
        val rawBytes = truncateUtf8Bytes(payload.encodeToByteArray(), MAX_V10_M_BYTES)
        val spec = selectVersion(rawBytes.size)
        val dataCodewords = encodeDataCodewords(rawBytes, spec)
        val finalCodewords = interleaveDataAndEcc(dataCodewords, spec)
        val size = spec.size
        val baseGrid = Array(size) { BooleanArray(size) }
        val isFunction = Array(size) { BooleanArray(size) }

        stampFunctionPatterns(baseGrid, isFunction, spec)
        placeCodewords(baseGrid, isFunction, finalCodewords, size)

        var bestMask = 0
        var bestPenalty = Int.MAX_VALUE
        var bestGrid = baseGrid
        for (mask in 0 until MASK_COUNT) {
            val candidate = applyMaskAndFormat(baseGrid, isFunction, spec, mask)
            val penalty = computePenaltyScore(candidate)
            if (penalty < bestPenalty) {
                bestPenalty = penalty
                bestMask = mask
                bestGrid = candidate
            }
        }
        // bestMask already applied on bestGrid; keep reference explicit for clarity.
        check(bestMask in 0 until MASK_COUNT)
        return List(size) { row -> List(size) { col -> bestGrid[row][col] } }
    }

    internal fun selectVersion(byteCount: Int): QrVersionSpec =
        versionSpecs.firstOrNull { byteCount <= it.maxByteCapacity } ?: versionSpecs.last()

    internal fun encodeDataCodewords(
        payloadBytes: ByteArray,
        spec: QrVersionSpec,
    ): IntArray {
        val totalBits = spec.totalDataCodewords * 8
        val bits = BitAccumulator(totalBits)
        bits.append(MODE_BYTE, 4)
        bits.append(payloadBytes.size, spec.charCountBits)
        for (b in payloadBytes) {
            bits.append(b.toInt() and 0xFF, 8)
        }
        val terminatorLen = minOf(4, totalBits - bits.length)
        if (terminatorLen > 0) {
            bits.append(0, terminatorLen)
        }
        val padBits = (8 - (bits.length % 8)) % 8
        if (padBits > 0) {
            bits.append(0, padBits)
        }
        var useEvenPad = true
        while (bits.length < totalBits) {
            bits.append(if (useEvenPad) PAD_BYTE_EVEN else PAD_BYTE_ODD, 8)
            useEvenPad = !useEvenPad
        }
        return bits.toByteIntArray()
    }

    internal fun gfMultiply(
        a: Int,
        b: Int,
    ): Int {
        var x = a and 0xFF
        var y = b and 0xFF
        var result = 0
        repeat(8) {
            if ((y and 1) != 0) result = result xor x
            val carry = (x and 0x80) != 0
            x = (x shl 1) and 0xFF
            if (carry) x = x xor GF_REDUCING_POLY
            y = y ushr 1
        }
        return result
    }

    internal fun computeReedSolomonEcc(
        data: IntArray,
        ecCount: Int,
    ): IntArray {
        val generator = buildReedSolomonGenerator(ecCount)
        val remainder = IntArray(ecCount)
        for (value in data) {
            val factor = (value and 0xFF) xor remainder[0]
            for (i in 0 until ecCount - 1) {
                remainder[i] = remainder[i + 1] xor gfMultiply(generator[i + 1], factor)
            }
            remainder[ecCount - 1] = gfMultiply(generator[ecCount], factor)
        }
        return remainder
    }

    internal fun computeFormatInfoBits(maskPattern: Int): Int {
        val data = (EC_LEVEL_M_FORMAT_BITS shl 3) or (maskPattern and 0x07)
        var rem = data shl 10
        for (bit in 14 downTo 10) {
            if (((rem ushr bit) and 1) != 0) {
                rem = rem xor (FORMAT_BCH_POLY shl (bit - 10))
            }
        }
        return ((data shl 10) or rem) xor FORMAT_XOR_MASK
    }

    internal fun computeVersionInfoBits(version: Int): Int {
        var rem = version shl 12
        for (bit in 17 downTo 12) {
            if (((rem ushr bit) and 1) != 0) {
                rem = rem xor (VERSION_BCH_POLY shl (bit - 12))
            }
        }
        return (version shl 12) or rem
    }

    private fun buildReedSolomonGenerator(degree: Int): IntArray {
        var poly = intArrayOf(1)
        var root = 1
        repeat(degree) {
            val next = IntArray(poly.size + 1)
            for (j in poly.indices) {
                next[j] = next[j] xor poly[j]
                next[j + 1] = next[j + 1] xor gfMultiply(poly[j], root)
            }
            poly = next
            root = gfMultiply(root, 2)
        }
        return poly
    }

    private fun interleaveDataAndEcc(
        dataCodewords: IntArray,
        spec: QrVersionSpec,
    ): IntArray {
        val dataBlocks = ArrayList<IntArray>(spec.g1Blocks + spec.g2Blocks)
        val eccBlocks = ArrayList<IntArray>(spec.g1Blocks + spec.g2Blocks)
        var offset = 0
        repeat(spec.g1Blocks) {
            val block = dataCodewords.copyOfRange(offset, offset + spec.g1DataLen)
            dataBlocks.add(block)
            eccBlocks.add(computeReedSolomonEcc(block, spec.ecPerBlock))
            offset += spec.g1DataLen
        }
        repeat(spec.g2Blocks) {
            val block = dataCodewords.copyOfRange(offset, offset + spec.g2DataLen)
            dataBlocks.add(block)
            eccBlocks.add(computeReedSolomonEcc(block, spec.ecPerBlock))
            offset += spec.g2DataLen
        }

        val maxDataLen = maxOf(spec.g1DataLen, spec.g2DataLen)
        val totalOut = spec.totalDataCodewords + (spec.g1Blocks + spec.g2Blocks) * spec.ecPerBlock
        val interleaved = IntArray(totalOut)
        var outIndex = 0
        for (i in 0 until maxDataLen) {
            for (block in dataBlocks) {
                if (i < block.size) {
                    interleaved[outIndex++] = block[i]
                }
            }
        }
        for (i in 0 until spec.ecPerBlock) {
            for (ecc in eccBlocks) {
                interleaved[outIndex++] = ecc[i]
            }
        }
        return interleaved
    }

    private fun stampFunctionPatterns(
        grid: Array<BooleanArray>,
        isFunction: Array<BooleanArray>,
        spec: QrVersionSpec,
    ) {
        val size = spec.size
        stampFinderWithSeparator(grid, isFunction, topRow = 0, leftCol = 0, size = size)
        stampFinderWithSeparator(grid, isFunction, topRow = 0, leftCol = size - FINDER_SIZE, size = size)
        stampFinderWithSeparator(grid, isFunction, topRow = size - FINDER_SIZE, leftCol = 0, size = size)
        stampTimingPatterns(grid, isFunction, size)
        stampAlignmentPatterns(grid, isFunction, spec)
        reserveFormatAreasAndDarkModule(grid, isFunction, spec.version, size)
        if (spec.version >= VERSION_INFO_MIN_VERSION) {
            stampVersionInfo(grid, isFunction, spec.version, size)
        }
    }

    private fun stampFinderWithSeparator(
        grid: Array<BooleanArray>,
        isFunction: Array<BooleanArray>,
        topRow: Int,
        leftCol: Int,
        size: Int,
    ) {
        for (dr in -1..FINDER_SIZE) {
            for (dc in -1..FINDER_SIZE) {
                val r = topRow + dr
                val c = leftCol + dc
                if (r !in 0 until size || c !in 0 until size) continue
                isFunction[r][c] = true
                val inside = dr in 0 until FINDER_SIZE && dc in 0 until FINDER_SIZE
                val onOuter = dr == 0 || dr == FINDER_SIZE - 1 || dc == 0 || dc == FINDER_SIZE - 1
                val inCore = dr in FINDER_INNER_START..FINDER_INNER_END && dc in FINDER_INNER_START..FINDER_INNER_END
                grid[r][c] = inside && (onOuter || inCore)
            }
        }
    }

    private fun stampTimingPatterns(
        grid: Array<BooleanArray>,
        isFunction: Array<BooleanArray>,
        size: Int,
    ) {
        for (i in FINDER_SIZE + 1 until size - FINDER_SIZE - 1) {
            val dark = i % 2 == 0
            grid[TIMING_INDEX][i] = dark
            isFunction[TIMING_INDEX][i] = true
            grid[i][TIMING_INDEX] = dark
            isFunction[i][TIMING_INDEX] = true
        }
    }

    private fun stampAlignmentPatterns(
        grid: Array<BooleanArray>,
        isFunction: Array<BooleanArray>,
        spec: QrVersionSpec,
    ) {
        val centers = spec.alignmentCenters
        val size = spec.size
        for (cr in centers) {
            for (cc in centers) {
                val overlapsFinder =
                    (cr < 9 && cc < 9) ||
                        (cr < 9 && cc > size - 9) ||
                        (cr > size - 9 && cc < 9)
                if (overlapsFinder) continue
                for (dr in -2..2) {
                    for (dc in -2..2) {
                        val r = cr + dr
                        val c = cc + dc
                        isFunction[r][c] = true
                        grid[r][c] = maxOf(abs(dr), abs(dc)) != 1
                    }
                }
            }
        }
    }

    private fun reserveFormatAreasAndDarkModule(
        grid: Array<BooleanArray>,
        isFunction: Array<BooleanArray>,
        version: Int,
        size: Int,
    ) {
        for (i in 0..8) {
            if (i != TIMING_INDEX) {
                isFunction[8][i] = true
                isFunction[i][8] = true
            }
        }
        for (i in 0..7) {
            isFunction[8][size - 1 - i] = true
        }
        for (i in 0..6) {
            isFunction[size - 1 - i][8] = true
        }
        val darkModuleRow = 4 * version + 9
        grid[darkModuleRow][8] = true
        isFunction[darkModuleRow][8] = true
    }

    private fun stampVersionInfo(
        grid: Array<BooleanArray>,
        isFunction: Array<BooleanArray>,
        version: Int,
        size: Int,
    ) {
        val bits = computeVersionInfoBits(version)
        for (i in 0 until 18) {
            val dark = ((bits ushr i) and 1) != 0
            val a = size - 11 + (i % 3)
            val b = i / 3
            grid[b][a] = dark
            isFunction[b][a] = true
            grid[a][b] = dark
            isFunction[a][b] = true
        }
    }

    private fun writeFormatInfo(
        grid: Array<BooleanArray>,
        maskPattern: Int,
        size: Int,
    ) {
        val bits = computeFormatInfoBits(maskPattern)
        // Copy 1 around top-left finder: bit 14 (MSB) at (8,0) down to bit 0 (LSB) at (0,8).
        for (col in 0..5) {
            grid[8][col] = ((bits ushr (14 - col)) and 1) != 0
        }
        grid[8][7] = ((bits ushr 8) and 1) != 0
        grid[8][8] = ((bits ushr 7) and 1) != 0
        grid[7][8] = ((bits ushr 6) and 1) != 0
        for (row in 0..5) {
            grid[row][8] = ((bits ushr row) and 1) != 0
        }

        // Copy 2: bits 14..8 along bottom-left column 8, bits 7..0 along top-right row 8.
        for (i in 0..6) {
            grid[size - 1 - i][8] = ((bits ushr (14 - i)) and 1) != 0
        }
        for (i in 0..7) {
            grid[8][size - 8 + i] = ((bits ushr (7 - i)) and 1) != 0
        }
    }

    private fun placeCodewords(
        grid: Array<BooleanArray>,
        isFunction: Array<BooleanArray>,
        codewords: IntArray,
        size: Int,
    ) {
        val totalBits = codewords.size * 8
        var bitIndex = 0
        var upward = true
        var col = size - 1
        while (col > 0) {
            if (col == TIMING_INDEX) col -= 1
            for (step in 0 until size) {
                val row = if (upward) size - 1 - step else step
                bitIndex = placeRowPair(grid, isFunction, codewords, totalBits, row, col, bitIndex)
            }
            upward = !upward
            col -= 2
        }
    }

    @Suppress("LongParameterList")
    private fun placeRowPair(
        grid: Array<BooleanArray>,
        isFunction: Array<BooleanArray>,
        codewords: IntArray,
        totalBits: Int,
        row: Int,
        col: Int,
        startBitIndex: Int,
    ): Int {
        var bitIndex = startBitIndex
        for (dx in 0..1) {
            val c = col - dx
            if (!isFunction[row][c]) {
                grid[row][c] = readCodewordBit(codewords, totalBits, bitIndex)
                bitIndex += 1
            }
        }
        return bitIndex
    }

    private fun readCodewordBit(
        codewords: IntArray,
        totalBits: Int,
        bitIndex: Int,
    ): Boolean {
        if (bitIndex >= totalBits) return false
        val byteVal = codewords[bitIndex / 8]
        val shift = 7 - (bitIndex % 8)
        return ((byteVal ushr shift) and 1) != 0
    }

    private fun applyMaskAndFormat(
        baseGrid: Array<BooleanArray>,
        isFunction: Array<BooleanArray>,
        spec: QrVersionSpec,
        maskPattern: Int,
    ): Array<BooleanArray> {
        val size = spec.size
        val masked = Array(size) { r -> baseGrid[r].copyOf() }
        for (r in 0 until size) {
            for (c in 0 until size) {
                if (!isFunction[r][c] && shouldInvertModule(maskPattern, r, c)) {
                    masked[r][c] = !masked[r][c]
                }
            }
        }
        writeFormatInfo(masked, maskPattern, size)
        return masked
    }

    private fun shouldInvertModule(
        mask: Int,
        r: Int,
        c: Int,
    ): Boolean =
        when (mask) {
            0 -> (r + c) % 2 == 0
            1 -> r % 2 == 0
            2 -> c % 3 == 0
            3 -> (r + c) % 3 == 0
            4 -> ((r / 2) + (c / 3)) % 2 == 0
            5 -> ((r * c) % 2) + ((r * c) % 3) == 0
            6 -> (((r * c) % 2) + ((r * c) % 3)) % 2 == 0
            else -> (((r + c) % 2) + ((r * c) % 3)) % 2 == 0
        }

    internal fun computePenaltyScore(grid: Array<BooleanArray>): Int =
        computeRunPenalty(grid) +
            computeBlockPenalty(grid) +
            computeFinderLikePenalty(grid) +
            computeBalancePenalty(grid)

    private fun computeRunPenalty(grid: Array<BooleanArray>): Int {
        val size = grid.size
        var penalty = 0
        for (i in 0 until size) {
            penalty += lineRunPenalty(size) { idx -> grid[i][idx] }
            penalty += lineRunPenalty(size) { idx -> grid[idx][i] }
        }
        return penalty
    }

    private inline fun lineRunPenalty(
        size: Int,
        moduleAt: (Int) -> Boolean,
    ): Int {
        var score = 0
        var runColor = moduleAt(0)
        var runLength = 1
        for (idx in 1 until size) {
            val current = moduleAt(idx)
            if (current == runColor) {
                runLength += 1
            } else {
                if (runLength >= PENALTY_RUN_MIN) {
                    score += PENALTY_RUN_BASE + (runLength - PENALTY_RUN_MIN)
                }
                runColor = current
                runLength = 1
            }
        }
        if (runLength >= PENALTY_RUN_MIN) {
            score += PENALTY_RUN_BASE + (runLength - PENALTY_RUN_MIN)
        }
        return score
    }

    private fun computeBlockPenalty(grid: Array<BooleanArray>): Int {
        val size = grid.size
        var penalty = 0
        for (r in 0 until size - 1) {
            for (c in 0 until size - 1) {
                val color = grid[r][c]
                if (grid[r][c + 1] == color && grid[r + 1][c] == color && grid[r + 1][c + 1] == color) {
                    penalty += PENALTY_BLOCK_WEIGHT
                }
            }
        }
        return penalty
    }

    private fun computeFinderLikePenalty(grid: Array<BooleanArray>): Int {
        val size = grid.size
        val patternLen = FINDER_LIKE_A.size
        if (size < patternLen) return 0
        var penalty = 0
        for (i in 0 until size) {
            for (start in 0..(size - patternLen)) {
                if (matchesLinePattern(start, FINDER_LIKE_A) { idx -> grid[i][idx] } ||
                    matchesLinePattern(start, FINDER_LIKE_B) { idx -> grid[i][idx] }
                ) {
                    penalty += PENALTY_FINDER_LIKE_WEIGHT
                }
                if (matchesLinePattern(start, FINDER_LIKE_A) { idx -> grid[idx][i] } ||
                    matchesLinePattern(start, FINDER_LIKE_B) { idx -> grid[idx][i] }
                ) {
                    penalty += PENALTY_FINDER_LIKE_WEIGHT
                }
            }
        }
        return penalty
    }

    private inline fun matchesLinePattern(
        start: Int,
        pattern: BooleanArray,
        moduleAt: (Int) -> Boolean,
    ): Boolean {
        for (k in pattern.indices) {
            if (moduleAt(start + k) != pattern[k]) return false
        }
        return true
    }

    private fun computeBalancePenalty(grid: Array<BooleanArray>): Int {
        val size = grid.size
        val total = size * size
        var darkCount = 0
        for (r in 0 until size) {
            for (c in 0 until size) {
                if (grid[r][c]) darkCount += 1
            }
        }
        val k = abs(darkCount * 20 - total * 10) / total
        return k * PENALTY_BALANCE_WEIGHT
    }

    private fun truncateUtf8Bytes(
        raw: ByteArray,
        maxBytes: Int,
    ): ByteArray {
        if (raw.size <= maxBytes) return raw
        var end = maxBytes
        while (end > 0 && (raw[end].toInt() and 0xC0) == 0x80) {
            end -= 1
        }
        return raw.copyOf(if (end > 0) end else maxBytes)
    }

    private class BitAccumulator(
        capacityBits: Int,
    ) {
        private val bytes = IntArray((capacityBits + 7) / 8)
        var length: Int = 0
            private set

        fun append(
            value: Int,
            bitCount: Int,
        ) {
            for (shift in bitCount - 1 downTo 0) {
                val bit = (value ushr shift) and 1
                if (bit != 0) {
                    bytes[length / 8] = bytes[length / 8] or (1 shl (7 - (length % 8)))
                }
                length += 1
            }
        }

        fun toByteIntArray(): IntArray = bytes
    }
}
