package dev.abeta.kmp.atoms.atoms

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ExtendedAtomHelpersTest {
    @Test
    fun otpHelpersClampLengthAndKeepOnlyDigits() {
        assertEquals(4, resolveOtpLength(2))
        assertEquals(6, resolveOtpLength(6))
        assertEquals(8, resolveOtpLength(12))

        assertEquals("4829", sanitizeOtpDigits("48-29ab", 4))
        assertEquals("123456", sanitizeOtpDigits("123456789", 6))
        assertEquals("", sanitizeOtpDigits("abcdef", 6))
    }

    @Test
    fun badgeCountRatingAndStatHelpersFormatDeterministically() {
        assertEquals("0", formatBadgeCount(0))
        assertEquals("0", formatBadgeCount(-5))
        assertEquals("42", formatBadgeCount(42, maxCount = 99))
        assertEquals("99", formatBadgeCount(99, maxCount = 99))
        assertEquals("99+", formatBadgeCount(125, maxCount = 99))
        assertEquals("9+", formatBadgeCount(12, maxCount = 9))

        assertEquals(1f, resolveStarFillLevel(4.8f, 0))
        assertEquals(1f, resolveStarFillLevel(4.8f, 4))
        assertEquals(0.5f, resolveStarFillLevel(3.5f, 3))
        assertEquals(0f, resolveStarFillLevel(3.1f, 3))
        assertEquals("4.8 (124)", formatRatingSummary(4.8f, 124))
        assertEquals("4.5", formatRatingSummary(4.5f, null))
        assertEquals("0.0", formatRatingSummary(-1f, null))

        assertEquals("▲ +14%", formatStatDelta("+14%", deltaPositive = true))
        assertEquals("▼ -3.2%", formatStatDelta("-3.2%", deltaPositive = false))
        assertEquals("0.0%", formatStatDelta("0.0%", deltaPositive = null))
        assertNotNull(UiBuiltinIcons.resolve("chevron_down"))
    }

    @Test
    fun qrMatrixGeneratorIsDeterministicAndIncludesThreeFinderPatterns() {
        val matrixA = generateQrMatrix("abeta://order/4091")
        val matrixB = generateQrMatrix("abeta://order/4091")
        val matrixDifferent = generateQrMatrix("abeta://order/9999")

        assertEquals(QR_MATRIX_SIZE, matrixA.size)
        matrixA.forEach { row -> assertEquals(QR_MATRIX_SIZE, row.size) }
        assertEquals(matrixA, matrixB)
        assertNotEquals(matrixA, matrixDifferent)

        // Top-left, top-right, and bottom-left 7x7 finder corners and 3x3 centers
        val finderOrigins = listOf(0 to 0, 0 to 14, 14 to 0)
        finderOrigins.forEach { (r0, c0) ->
            assertTrue(matrixA[r0][c0], "Outer finder corner ($r0,$c0) should be dark")
            assertTrue(matrixA[r0 + 6][c0 + 6], "Outer finder far corner should be dark")
            assertFalse(matrixA[r0 + 1][c0 + 1], "Inner finder ring should be light")
            assertTrue(matrixA[r0 + 3][c0 + 3], "Inner finder core should be dark")
        }
    }
}
