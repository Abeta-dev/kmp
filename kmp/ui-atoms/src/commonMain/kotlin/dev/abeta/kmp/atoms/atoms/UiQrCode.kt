package dev.abeta.kmp.atoms.atoms

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.abeta.kmp.atoms.modifiers.LocalUiHapticFeedback
import dev.abeta.kmp.atoms.modifiers.UiHapticKind
import dev.abeta.kmp.atoms.modifiers.applyLayoutConfig
import dev.abeta.kmp.atoms.modifiers.pressBounce
import dev.abeta.kmp.contracts.ActionRef
import dev.abeta.kmp.contracts.QrCodeAtom
import dev.abeta.kmp.tokens.DesignPalette
import dev.abeta.kmp.tokens.ThemeTokens
import kotlin.math.ceil

/**
 * Standard Version-1 QR matrix edge length (21 × 21 modules).
 */
const val QR_MATRIX_SIZE = 21

private const val FINDER_SIZE = 7
private const val FINDER_INNER_START = 2
private const val FINDER_INNER_END = 4
private const val FINDER_FAR_ORIGIN = QR_MATRIX_SIZE - FINDER_SIZE
private const val SEPARATOR_INDEX = FINDER_SIZE
private const val FAR_SEPARATOR_INDEX = FINDER_FAR_ORIGIN - 1
private const val TIMING_LINE_INDEX = 6
private const val MIN_QR_SIZE_DP = 80
private const val MAX_QR_SIZE_DP = 360
private const val FNV_OFFSET_BASIS = -3750763034362895579L // 0xCBF29CE484222325u
private const val FNV_PRIME = 1099511628211L
private const val XORSHIFT_A = 13
private const val XORSHIFT_B = 7
private const val XORSHIFT_C = 17
private const val MODULE_SEAM_OVERLAP_PX = 0.5f

/**
 * Pure deterministic generator building a 21×21 QR-style matrix with 3 corner finder patterns,
 * separator quiet margins, timing tracks, and hash-seeded data modules from [payload].
 */
fun generateQrMatrix(payload: String): List<List<Boolean>> {
    val grid = Array(QR_MATRIX_SIZE) { BooleanArray(QR_MATRIX_SIZE) }
    val reserved = Array(QR_MATRIX_SIZE) { BooleanArray(QR_MATRIX_SIZE) }

    stampFinderWithSeparator(grid, reserved, topRow = 0, leftCol = 0)
    stampFinderWithSeparator(grid, reserved, topRow = 0, leftCol = FINDER_FAR_ORIGIN)
    stampFinderWithSeparator(grid, reserved, topRow = FINDER_FAR_ORIGIN, leftCol = 0)
    stampTimingTracks(grid, reserved)
    fillHashedDataModules(grid, reserved, payload)

    return List(QR_MATRIX_SIZE) { row -> List(QR_MATRIX_SIZE) { col -> grid[row][col] } }
}

private fun stampFinderWithSeparator(
    grid: Array<BooleanArray>,
    reserved: Array<BooleanArray>,
    topRow: Int,
    leftCol: Int,
) {
    for (dr in -1..FINDER_SIZE) {
        for (dc in -1..FINDER_SIZE) {
            val r = topRow + dr
            val c = leftCol + dc
            if (r !in 0 until QR_MATRIX_SIZE || c !in 0 until QR_MATRIX_SIZE) continue
            reserved[r][c] = true
            val insideFinder = dr in 0 until FINDER_SIZE && dc in 0 until FINDER_SIZE
            grid[r][c] = insideFinder && isFinderModuleDark(dr, dc)
        }
    }
}

private fun isFinderModuleDark(
    dr: Int,
    dc: Int,
): Boolean {
    val onOuterRing = dr == 0 || dr == FINDER_SIZE - 1 || dc == 0 || dc == FINDER_SIZE - 1
    val inInnerCore = dr in FINDER_INNER_START..FINDER_INNER_END && dc in FINDER_INNER_START..FINDER_INNER_END
    return onOuterRing || inInnerCore
}

private fun stampTimingTracks(
    grid: Array<BooleanArray>,
    reserved: Array<BooleanArray>,
) {
    for (idx in SEPARATOR_INDEX + 1 until FAR_SEPARATOR_INDEX) {
        val dark = idx % 2 == 0
        grid[TIMING_LINE_INDEX][idx] = dark
        reserved[TIMING_LINE_INDEX][idx] = true
        grid[idx][TIMING_LINE_INDEX] = dark
        reserved[idx][TIMING_LINE_INDEX] = true
    }
}

private fun fillHashedDataModules(
    grid: Array<BooleanArray>,
    reserved: Array<BooleanArray>,
    payload: String,
) {
    var seed = FNV_OFFSET_BASIS
    payload.forEach { ch ->
        seed = (seed xor ch.code.toLong()) * FNV_PRIME
    }
    if (seed == 0L) seed = FNV_PRIME

    for (row in 0 until QR_MATRIX_SIZE) {
        for (col in 0 until QR_MATRIX_SIZE) {
            if (!reserved[row][col]) {
                seed = seed xor (seed shl XORSHIFT_A)
                seed = seed xor (seed ushr XORSHIFT_B)
                seed = seed xor (seed shl XORSHIFT_C)
                grid[row][col] = (seed and 1L) == 1L
            }
        }
    }
}

/**
 * Stateless deterministic pure-Compose Canvas QR code UI atom rendered from a [QrCodeAtom] contract.
 */
@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
fun UiQrCode(
    atom: QrCodeAtom,
    modifier: Modifier = Modifier,
    onAction: (ActionRef) -> Unit = {},
) {
    val colors = ThemeTokens.colors
    val spacing = ThemeTokens.spacing
    val typography = ThemeTokens.typography
    val haptic = LocalUiHapticFeedback.current

    val matrix = remember(atom.payload) { generateQrMatrix(atom.payload) }
    val resolvedSize = atom.sizeDp.coerceIn(MIN_QR_SIZE_DP, MAX_QR_SIZE_DP)
    val clickAction = atom.onClick
    val containerModifier =
        modifier
            .applyLayoutConfig(atom.layout)
            .testTag("qr_code_${atom.id ?: "default"}")
            .let { base ->
                if (clickAction != null) {
                    base
                        .pressBounce()
                        .clickable(role = Role.Button) {
                            haptic(UiHapticKind.CLICK)
                            onAction(clickAction)
                        }
                } else {
                    base
                }
            }

    Column(
        modifier = containerModifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing.space8),
    ) {
        UiQrMatrixFrame(
            matrix = matrix,
            sizeDp = resolvedSize,
            description = atom.label ?: "QR code for ${atom.payload}",
        )
        atom.label?.takeIf { it.isNotBlank() }?.let { labelText ->
            Text(
                text = labelText,
                style = typography.titleSmall,
                color = colors.text.primary,
                textAlign = TextAlign.Center,
            )
        }
        atom.caption?.takeIf { it.isNotBlank() }?.let { captionText ->
            Text(
                text = captionText,
                style = typography.caption,
                color = colors.text.secondary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
private fun UiQrMatrixFrame(
    matrix: List<List<Boolean>>,
    sizeDp: Int,
    description: String,
) {
    val colors = ThemeTokens.colors
    val spacing = ThemeTokens.spacing
    val cornerRadius = ThemeTokens.cornerRadius
    val strokeWidth = ThemeTokens.strokeWidth
    val frameShape = RoundedCornerShape(cornerRadius.md)

    Box(
        modifier =
            Modifier
                .clip(frameShape)
                .background(DesignPalette.White, frameShape)
                .border(strokeWidth.thin, colors.border.default, frameShape)
                .padding(spacing.space12),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            modifier =
                Modifier
                    .size(sizeDp.dp)
                    .semantics { contentDescription = description },
        ) {
            val cellSize = size.minDimension / QR_MATRIX_SIZE
            val moduleDrawSize = ceil(cellSize + MODULE_SEAM_OVERLAP_PX)
            for (row in 0 until QR_MATRIX_SIZE) {
                val rowCells = matrix[row]
                for (col in 0 until QR_MATRIX_SIZE) {
                    if (rowCells[col]) {
                        drawRect(
                            color = DesignPalette.Slate950,
                            topLeft = Offset(x = col * cellSize, y = row * cellSize),
                            size = Size(width = moduleDrawSize, height = moduleDrawSize),
                        )
                    }
                }
            }
        }
    }
}
