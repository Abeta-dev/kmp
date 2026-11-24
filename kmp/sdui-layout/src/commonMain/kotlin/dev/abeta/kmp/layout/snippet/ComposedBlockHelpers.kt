package dev.abeta.kmp.layout.snippet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import dev.abeta.kmp.actions.model.ActionTypes
import dev.abeta.kmp.atoms.atoms.UiAvatar
import dev.abeta.kmp.atoms.atoms.UiButton
import dev.abeta.kmp.atoms.atoms.UiCountdown
import dev.abeta.kmp.atoms.atoms.UiDivider
import dev.abeta.kmp.atoms.atoms.UiIcon
import dev.abeta.kmp.atoms.atoms.UiImage
import dev.abeta.kmp.atoms.atoms.UiProgress
import dev.abeta.kmp.atoms.atoms.UiRollingCounter
import dev.abeta.kmp.atoms.atoms.UiSpacer
import dev.abeta.kmp.atoms.atoms.UiStepper
import dev.abeta.kmp.atoms.atoms.UiTag
import dev.abeta.kmp.atoms.atoms.UiText
import dev.abeta.kmp.atoms.tokens.resolve
import dev.abeta.kmp.contracts.ActionRef
import dev.abeta.kmp.contracts.AlignmentToken
import dev.abeta.kmp.contracts.ComposedNode
import dev.abeta.kmp.contracts.ComposedNodeKind
import dev.abeta.kmp.contracts.DividerAtom
import dev.abeta.kmp.contracts.SpacerAtom
import dev.abeta.kmp.tokens.ThemeTokens
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName", "CyclomaticComplexMethod")
internal fun RenderComposedLeafNode(
    node: ComposedNode,
    formValues: Map<String, String>,
    onFormValueChange: (String, String) -> Unit,
    onAction: (ActionRef) -> Unit,
    modifier: Modifier,
) {
    when (node.kind) {
        ComposedNodeKind.TEXT -> {
            node.text?.let { UiText(it, modifier, onAction) }
        }

        ComposedNodeKind.BUTTON -> {
            node.button?.let { UiButton(it, modifier, onAction) }
        }

        ComposedNodeKind.TAG -> {
            node.tag?.let { UiTag(it, modifier, onAction) }
        }

        ComposedNodeKind.ICON -> {
            node.icon?.let { UiIcon(it, modifier, onAction) }
        }

        ComposedNodeKind.IMAGE -> {
            node.image?.let { UiImage(it, modifier, onAction) }
        }

        ComposedNodeKind.AVATAR -> {
            node.avatar?.let { UiAvatar(it, modifier, onAction) }
        }

        ComposedNodeKind.PROGRESS -> {
            node.progress?.let { UiProgress(it, modifier) }
        }

        ComposedNodeKind.DIVIDER -> {
            UiDivider(node.divider ?: DividerAtom(), modifier)
        }

        ComposedNodeKind.SPACER -> {
            RenderComposedSpacerNode(node, modifier)
        }

        ComposedNodeKind.STEPPER -> {
            RenderComposedStepperNode(node, formValues, onFormValueChange, onAction, modifier)
        }

        ComposedNodeKind.COUNTDOWN -> {
            node.countdown?.let { UiCountdown(it, modifier, onAction) }
        }

        ComposedNodeKind.ROLLING_COUNTER -> {
            RenderComposedRollingCounterNode(node, modifier)
        }

        else -> {
            RenderComposedExtendedLeafNode(node, formValues, onFormValueChange, onAction, modifier)
        }
    }
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
private fun RenderComposedSpacerNode(
    node: ComposedNode,
    modifier: Modifier,
) {
    val spacer = node.spacer ?: SpacerAtom(heightDp = node.spacingDp.coerceAtLeast(0))
    UiSpacer(atom = spacer, modifier = modifier)
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
private fun RenderComposedStepperNode(
    node: ComposedNode,
    formValues: Map<String, String>,
    onFormValueChange: (String, String) -> Unit,
    onAction: (ActionRef) -> Unit,
    modifier: Modifier,
) {
    node.stepper?.let { stepper ->
        UiStepper(
            atom = stepper,
            modifier = modifier,
            quantityOverride = formValues[stepper.fieldId]?.toIntOrNull(),
            onQuantityChange = { updatedQty -> onFormValueChange(stepper.fieldId, updatedQty.toString()) },
            onAction = onAction,
        )
    }
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
private fun RenderComposedRollingCounterNode(
    node: ComposedNode,
    modifier: Modifier,
) {
    val value = node.counterValue ?: node.text?.text ?: ""
    val style =
        (node.counterTypography ?: node.text?.typography)
            .resolve(fallback = ThemeTokens.typography.titleMedium)
    val color =
        (node.counterColor ?: node.text?.color)
            .resolve(fallback = ThemeTokens.colors.text.primary)
    UiRollingCounter(value = value, style = style, color = color, modifier = modifier)
}

internal fun resolveComposedHorizontalAlignment(
    horizontalAlignment: String?,
    fallbackToken: AlignmentToken?,
): Alignment.Horizontal =
    when (horizontalAlignment?.trim()?.lowercase()) {
        "center" -> {
            Alignment.CenterHorizontally
        }

        "end" -> {
            Alignment.End
        }

        "start" -> {
            Alignment.Start
        }

        else -> {
            when (fallbackToken) {
                AlignmentToken.CENTER -> Alignment.CenterHorizontally
                AlignmentToken.END -> Alignment.End
                AlignmentToken.START, null -> Alignment.Start
            }
        }
    }

internal fun resolveComposedVerticalAlignment(verticalAlignment: String?): Alignment.Vertical =
    when (verticalAlignment?.trim()?.lowercase()) {
        "top" -> Alignment.Top
        "bottom" -> Alignment.Bottom
        else -> Alignment.CenterVertically
    }

internal fun resolveComposedHorizontalArrangement(
    arrangement: String?,
    spacingDp: Dp,
): Arrangement.Horizontal =
    when (arrangement?.trim()?.lowercase()) {
        "space_between" -> Arrangement.SpaceBetween
        "space_around" -> Arrangement.SpaceAround
        "space_evenly" -> Arrangement.SpaceEvenly
        "center" -> Arrangement.spacedBy(spacingDp, Alignment.CenterHorizontally)
        "end" -> Arrangement.spacedBy(spacingDp, Alignment.End)
        else -> Arrangement.spacedBy(spacingDp)
    }

internal fun resolveComposedVerticalArrangement(
    arrangement: String?,
    spacingDp: Dp,
): Arrangement.Vertical =
    when (arrangement?.trim()?.lowercase()) {
        "space_between" -> Arrangement.SpaceBetween
        "space_around" -> Arrangement.SpaceAround
        "space_evenly" -> Arrangement.SpaceEvenly
        "center" -> Arrangement.spacedBy(spacingDp, Alignment.CenterVertically)
        "bottom", "end" -> Arrangement.spacedBy(spacingDp, Alignment.Bottom)
        else -> Arrangement.spacedBy(spacingDp)
    }

internal fun resolveComposedBoxAlignment(
    horizontalAlignment: String?,
    verticalAlignment: String?,
    fallbackToken: AlignmentToken?,
): Alignment {
    val horiz = horizontalAlignment?.trim()?.lowercase() ?: fallbackToken?.name?.lowercase()
    val vert = verticalAlignment?.trim()?.lowercase()
    return when {
        vert == "top" && horiz == "end" -> Alignment.TopEnd
        vert == "top" && horiz == "center" -> Alignment.TopCenter
        vert == "bottom" && horiz == "end" -> Alignment.BottomEnd
        vert == "bottom" && horiz == "center" -> Alignment.BottomCenter
        vert == "bottom" -> Alignment.BottomStart
        horiz == "center" -> Alignment.Center
        horiz == "end" -> Alignment.CenterEnd
        else -> Alignment.TopStart
    }
}

internal fun resolveChildBoxAlignment(
    boxAlignment: String?,
    fallback: Alignment = Alignment.TopStart,
): Alignment =
    when (boxAlignment?.trim()?.lowercase()) {
        "top_start" -> Alignment.TopStart
        "top_center" -> Alignment.TopCenter
        "top_end" -> Alignment.TopEnd
        "center_start" -> Alignment.CenterStart
        "center" -> Alignment.Center
        "center_end" -> Alignment.CenterEnd
        "bottom_start" -> Alignment.BottomStart
        "bottom_center" -> Alignment.BottomCenter
        "bottom_end" -> Alignment.BottomEnd
        else -> fallback
    }

internal fun resolveComposedNodeClickAction(node: ComposedNode): ActionRef? {
    val clickAction = node.onClick
    val clickTracking = node.clickTracking
    return when {
        clickAction != null && clickTracking != null && clickAction.tracking == null -> {
            clickAction.copy(tracking = clickTracking)
        }

        clickAction != null -> {
            clickAction
        }

        clickTracking != null -> {
            ActionRef(
                id = node.nodeId?.takeIf { it.isNotBlank() }?.let { "track_$it" },
                type = ActionTypes.TRACK_EVENT,
                payload =
                    buildJsonObject {
                        put("event_name", clickTracking.eventName)
                        if (clickTracking.properties.isNotEmpty()) {
                            put(
                                "properties",
                                buildJsonObject {
                                    clickTracking.properties.forEach { (key, value) -> put(key, value) }
                                },
                            )
                        }
                    },
                tracking = clickTracking,
            )
        }

        else -> {
            null
        }
    }
}
