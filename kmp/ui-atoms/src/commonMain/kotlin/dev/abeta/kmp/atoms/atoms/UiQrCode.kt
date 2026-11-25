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

/**
 * ISO/IEC 18004 required light quiet-zone margin around every QR symbol (4 modules on each edge).
 */
const val QR_QUIET_ZONE_MODULES = 4

private const val MIN_QR_SIZE_DP = 80
private const val MAX_QR_SIZE_DP = 360
private const val MODULE_SEAM_OVERLAP_PX = 0.5f

/**
 * Pure standards-compliant ISO/IEC 18004 QR Code matrix generator (Byte Mode, Error Correction
 * Level M, Versions 1–10 supporting up to 213 UTF-8 bytes with graceful UTF-8 truncation if longer).
 */
fun generateQrMatrix(payload: String): List<List<Boolean>> = QrCodeEncoder.encode(payload)

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
            val moduleCount = matrix.size
            if (moduleCount <= 0) return@Canvas
            val totalModules = moduleCount + QR_QUIET_ZONE_MODULES * 2
            val cellSize = size.minDimension / totalModules
            val quietOffset = QR_QUIET_ZONE_MODULES * cellSize
            val moduleDrawSize = ceil(cellSize + MODULE_SEAM_OVERLAP_PX)
            drawRect(color = DesignPalette.White, size = size)
            for (row in 0 until moduleCount) {
                val rowCells = matrix[row]
                for (col in 0 until rowCells.size) {
                    if (rowCells[col]) {
                        drawRect(
                            color = DesignPalette.Slate950,
                            topLeft =
                                Offset(
                                    x = quietOffset + col * cellSize,
                                    y = quietOffset + row * cellSize,
                                ),
                            size = Size(width = moduleDrawSize, height = moduleDrawSize),
                        )
                    }
                }
            }
        }
    }
}
