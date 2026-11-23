@file:Suppress("ktlint:standard:filename", "MatchingDeclarationName")

package dev.abeta.kmp.layout.snippet

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.abeta.kmp.atoms.atoms.UiAlertBanner
import dev.abeta.kmp.atoms.atoms.UiBadge
import dev.abeta.kmp.atoms.atoms.UiChip
import dev.abeta.kmp.atoms.atoms.UiKeyValueRow
import dev.abeta.kmp.atoms.atoms.UiOtpInput
import dev.abeta.kmp.atoms.atoms.UiPrice
import dev.abeta.kmp.atoms.atoms.UiQrCode
import dev.abeta.kmp.atoms.atoms.UiRadioGroup
import dev.abeta.kmp.atoms.atoms.UiRating
import dev.abeta.kmp.atoms.atoms.UiSegmentedControl
import dev.abeta.kmp.atoms.atoms.UiSelect
import dev.abeta.kmp.atoms.atoms.UiStatTile
import dev.abeta.kmp.contracts.ActionRef
import dev.abeta.kmp.contracts.ComposedNode
import dev.abeta.kmp.contracts.ComposedNodeKind

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName", "CyclomaticComplexMethod")
internal fun RenderComposedExtendedLeafNode(
    node: ComposedNode,
    formValues: Map<String, String>,
    onFormValueChange: (String, String) -> Unit,
    onAction: (ActionRef) -> Unit,
    modifier: Modifier,
) {
    when (node.kind) {
        ComposedNodeKind.RADIO_GROUP -> {
            RenderComposedRadioGroup(node, formValues, onFormValueChange, onAction, modifier)
        }

        ComposedNodeKind.SELECT -> {
            RenderComposedSelect(node, formValues, onFormValueChange, onAction, modifier)
        }

        ComposedNodeKind.OTP_INPUT -> {
            RenderComposedOtpInput(node, formValues, onFormValueChange, onAction, modifier)
        }

        ComposedNodeKind.CHIP -> {
            node.chip?.let { UiChip(atom = it, modifier = modifier, onAction = onAction) }
        }

        ComposedNodeKind.SEGMENTED_CONTROL -> {
            RenderComposedSegmentedControl(node, formValues, onFormValueChange, onAction, modifier)
        }

        ComposedNodeKind.RATING -> {
            RenderComposedRating(node, formValues, onFormValueChange, onAction, modifier)
        }

        ComposedNodeKind.PRICE -> {
            node.price?.let { UiPrice(atom = it, modifier = modifier) }
        }

        ComposedNodeKind.BADGE -> {
            node.badge?.let { UiBadge(atom = it, modifier = modifier) }
        }

        ComposedNodeKind.ALERT_BANNER -> {
            RenderComposedAlertBanner(node, onAction, modifier)
        }

        ComposedNodeKind.STAT_TILE -> {
            node.statTile?.let { UiStatTile(atom = it, modifier = modifier, onAction = onAction) }
        }

        ComposedNodeKind.KEY_VALUE_ROW -> {
            node.keyValueRow?.let { UiKeyValueRow(atom = it, modifier = modifier, onAction = onAction) }
        }

        ComposedNodeKind.QR_CODE -> {
            node.qrCode?.let { UiQrCode(atom = it, modifier = modifier, onAction = onAction) }
        }

        else -> {
            // Core leaf and container nodes are handled by ComposedBlockHelpers and ComposedBlockSnippetRenderer.
        }
    }
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
private fun RenderComposedRadioGroup(
    node: ComposedNode,
    formValues: Map<String, String>,
    onFormValueChange: (String, String) -> Unit,
    onAction: (ActionRef) -> Unit,
    modifier: Modifier,
) {
    node.radioGroup?.let { radio ->
        UiRadioGroup(
            atom = radio,
            modifier = modifier,
            selectedIdOverride = formValues[radio.fieldId],
            onSelect = { selected -> onFormValueChange(radio.fieldId, selected) },
            onAction = onAction,
        )
    }
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
private fun RenderComposedSelect(
    node: ComposedNode,
    formValues: Map<String, String>,
    onFormValueChange: (String, String) -> Unit,
    onAction: (ActionRef) -> Unit,
    modifier: Modifier,
) {
    node.select?.let { select ->
        UiSelect(
            atom = select,
            modifier = modifier,
            selectedIdOverride = formValues[select.fieldId],
            onSelect = { selected -> onFormValueChange(select.fieldId, selected) },
            onAction = onAction,
        )
    }
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
private fun RenderComposedOtpInput(
    node: ComposedNode,
    formValues: Map<String, String>,
    onFormValueChange: (String, String) -> Unit,
    onAction: (ActionRef) -> Unit,
    modifier: Modifier,
) {
    node.otpInput?.let { otp ->
        UiOtpInput(
            atom = otp,
            modifier = modifier,
            valueOverride = formValues[otp.fieldId],
            onValueChange = { updated -> onFormValueChange(otp.fieldId, updated) },
            onAction = onAction,
        )
    }
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
private fun RenderComposedSegmentedControl(
    node: ComposedNode,
    formValues: Map<String, String>,
    onFormValueChange: (String, String) -> Unit,
    onAction: (ActionRef) -> Unit,
    modifier: Modifier,
) {
    node.segmentedControl?.let { segmented ->
        UiSegmentedControl(
            atom = segmented,
            modifier = modifier,
            selectedIdOverride = formValues[segmented.fieldId],
            onSelect = { selected -> onFormValueChange(segmented.fieldId, selected) },
            onAction = onAction,
        )
    }
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
private fun RenderComposedRating(
    node: ComposedNode,
    formValues: Map<String, String>,
    onFormValueChange: (String, String) -> Unit,
    onAction: (ActionRef) -> Unit,
    modifier: Modifier,
) {
    node.rating?.let { rating ->
        val fieldId = rating.fieldId?.takeIf { it.isNotBlank() }
        UiRating(
            atom = rating,
            modifier = modifier,
            ratingOverride = fieldId?.let { formValues[it]?.toFloatOrNull() },
            onRate = { updated -> fieldId?.let { onFormValueChange(it, updated.toString()) } },
            onAction = onAction,
        )
    }
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
private fun RenderComposedAlertBanner(
    node: ComposedNode,
    onAction: (ActionRef) -> Unit,
    modifier: Modifier,
) {
    node.alertBanner?.let { banner ->
        var dismissed by remember(node.nodeId, banner.id, banner.title) { mutableStateOf(false) }
        if (!dismissed) {
            UiAlertBanner(
                atom = banner,
                modifier = modifier,
                onDismiss = {
                    if (banner.dismissible) {
                        dismissed = true
                    }
                },
                onAction = onAction,
            )
        }
    }
}
