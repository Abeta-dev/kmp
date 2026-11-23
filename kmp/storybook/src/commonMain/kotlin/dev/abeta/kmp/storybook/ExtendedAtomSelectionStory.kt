package dev.abeta.kmp.storybook

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.abeta.kmp.actions.model.ActionTypes
import dev.abeta.kmp.atoms.atoms.UiChip
import dev.abeta.kmp.atoms.atoms.UiOtpInput
import dev.abeta.kmp.atoms.atoms.UiRadioGroup
import dev.abeta.kmp.atoms.atoms.UiSegmentedControl
import dev.abeta.kmp.atoms.atoms.UiSelect
import dev.abeta.kmp.atoms.atoms.UiSpacer
import dev.abeta.kmp.atoms.atoms.UiSurfaceCard
import dev.abeta.kmp.atoms.atoms.UiTag
import dev.abeta.kmp.atoms.atoms.UiText
import dev.abeta.kmp.contracts.ActionRef
import dev.abeta.kmp.contracts.ChipAtom
import dev.abeta.kmp.contracts.IconAtom
import dev.abeta.kmp.contracts.OptionItem
import dev.abeta.kmp.contracts.OtpInputAtom
import dev.abeta.kmp.contracts.RadioGroupAtom
import dev.abeta.kmp.contracts.SduiJson
import dev.abeta.kmp.contracts.SegmentedControlAtom
import dev.abeta.kmp.contracts.SelectAtom
import dev.abeta.kmp.contracts.SpacerAtom
import dev.abeta.kmp.contracts.TagAtom
import dev.abeta.kmp.contracts.TagVariant
import dev.abeta.kmp.contracts.TextAtom
import dev.abeta.kmp.contracts.TypographyTokenRef
import dev.abeta.kmp.tokens.ThemeTokens
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private const val OTP_LENGTH_FOUR = 4
private const val OTP_LENGTH_SIX = 6
private const val SPACER_SM_DP = 8
private const val SPACER_MD_DP = 12
private const val CHIP_ICON_SIZE_DP = 16
private const val SAMPLE_OTP_FOUR = "4829"
private const val SAMPLE_OTP_SIX = "482910"

private data class OtpDemoControls(
    val length: Int,
    val masked: Boolean,
    val showError: Boolean,
    val value: String,
    val onLengthChange: (Int) -> Unit,
    val onMaskedToggle: () -> Unit,
    val onErrorToggle: () -> Unit,
    val onValueChange: (String) -> Unit,
)

private data class DemoChipSpec(
    val id: String,
    val label: String,
    val iconName: String,
)

private val DEMO_CHIPS =
    listOf(
        DemoChipSpec("organic", "Organic", "star"),
        DemoChipSpec("express", "8-Min Express", "check"),
        DemoChipSpec("free_delivery", "Free Delivery", "info"),
        DemoChipSpec("verified", "Verified Hub", "person"),
    )

/**
 * Interactive story showcasing [UiRadioGroup], [UiSelect], [UiOtpInput], [UiChip],
 * and [UiSegmentedControl] with live `SduiJson` wire inspection.
 */
@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
fun ExtendedAtomSelectionStory(onAction: (ActionRef) -> Unit) {
    val spacing = ThemeTokens.spacing
    var otpLength by remember { mutableIntStateOf(OTP_LENGTH_SIX) }
    var otpMasked by remember { mutableStateOf(false) }
    var otpShowError by remember { mutableStateOf(false) }
    var otpValue by remember { mutableStateOf("482") }

    val otpAtom =
        remember(otpLength, otpMasked, otpShowError) {
            OtpInputAtom(
                fieldId = "story_verification_otp",
                label = if (otpMasked) "$otpLength-Digit Security PIN" else "$otpLength-Digit Verification Code",
                length = otpLength,
                masked = otpMasked,
                helperText = "Enter all $otpLength digits to emit the on_complete ActionRef",
                errorText = if (otpShowError) "Invalid verification code — please retry" else null,
                onComplete =
                    ActionRef(
                        id = "story_otp_complete",
                        type = ActionTypes.SHOW_TOAST,
                        payload =
                            buildJsonObject {
                                put("message", "Verification code confirmed!")
                                put("variant", "success")
                            },
                    ),
            )
        }
    val otpControls =
        OtpDemoControls(
            length = otpLength,
            masked = otpMasked,
            showError = otpShowError,
            value = otpValue,
            onLengthChange = { nextLen ->
                otpLength = nextLen
                otpValue = otpValue.take(nextLen)
            },
            onMaskedToggle = { otpMasked = !otpMasked },
            onErrorToggle = { otpShowError = !otpShowError },
            onValueChange = { otpValue = it },
        )

    Column(verticalArrangement = Arrangement.spacedBy(spacing.space16)) {
        RadioGroupSpecimenCard(onAction = onAction)
        SelectSpecimenCard(onAction = onAction)
        OtpSpecimenCard(atom = otpAtom, controls = otpControls, onAction = onAction)
        ChipsSpecimenCard(onAction = onAction)
        SegmentedControlSpecimenCard(onAction = onAction)
        JsonContractPreviewCard(
            title = "OtpInputAtom Wire JSON",
            jsonText = SduiJson.encodeToString(OtpInputAtom.serializer(), otpAtom),
        )
    }
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
private fun RadioGroupSpecimenCard(onAction: (ActionRef) -> Unit) {
    var selectedSlot by remember { mutableStateOf("express") }
    val options =
        remember {
            listOf(
                OptionItem("express", "8-min Express · FREE", "Cold-chain EV courier · Priority hub", badge = "FAST"),
                OptionItem("scheduled", "Scheduled 6–7 PM", "Eco-batched evening doorstep window"),
                OptionItem("pickup", "Express Hub Pickup", "Ready in 5 mins · Smart Locker #4"),
            )
        }
    val radioAtom =
        remember(selectedSlot) {
            RadioGroupAtom(
                fieldId = "story_delivery_slot",
                label = "Delivery Window",
                options = options,
                selectedId = selectedSlot,
                onChange = ActionRef(id = "story_radio_select", type = ActionTypes.SAVE_STATE),
            )
        }

    UiSurfaceCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UiText(TextAtom(text = "UiRadioGroup · Delivery Slot", typography = TypographyTokenRef("title.large")))
            UiTag(TagAtom(label = selectedSlot.uppercase(), variant = TagVariant.BRAND))
        }
        UiSpacer(SpacerAtom(heightDp = SPACER_MD_DP))
        UiRadioGroup(
            atom = radioAtom,
            selectedIdOverride = selectedSlot,
            onSelect = { selectedSlot = it },
            onAction = onAction,
        )
    }
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
private fun SelectSpecimenCard(onAction: (ActionRef) -> Unit) {
    var selectedSource by remember { mutableStateOf("wallet") }
    val options =
        remember {
            listOf(
                OptionItem("wallet", "Abeta Wallet · \$2,480.50", "Instant · \$0.00 fee", badge = "DEFAULT"),
                OptionItem("metal", "Metal Card •••• 4829", "5% instant cashback on Abeta apps"),
                OptionItem("bank", "Linked Bank •••• 9104", "Zero-fee ACH & instant clearing"),
                OptionItem("pack_500g", "Bulk Unit · 500 g Pack", "Wholesale unit dropdown preset"),
            )
        }
    val selectAtom =
        remember(selectedSource) {
            SelectAtom(
                fieldId = "story_funding_select",
                label = "Bank / Funding Source",
                placeholder = "Choose a funding source",
                options = options,
                selectedId = selectedSource,
                helperText = "Instant clearing supported across all verified rails",
                onChange = ActionRef(id = "story_select_change", type = ActionTypes.SAVE_STATE),
            )
        }

    UiSurfaceCard(modifier = Modifier.fillMaxWidth()) {
        UiText(TextAtom(text = "UiSelect · Bank & Unit Dropdown", typography = TypographyTokenRef("title.large")))
        UiSpacer(SpacerAtom(heightDp = SPACER_MD_DP))
        UiSelect(
            atom = selectAtom,
            selectedIdOverride = selectedSource,
            onSelect = { selectedSource = it },
            onAction = onAction,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
private fun OtpSpecimenCard(
    atom: OtpInputAtom,
    controls: OtpDemoControls,
    onAction: (ActionRef) -> Unit,
) {
    val spacing = ThemeTokens.spacing
    UiSurfaceCard(modifier = Modifier.fillMaxWidth()) {
        UiText(TextAtom(text = "UiOtpInput · OTP & Masked PIN", typography = TypographyTokenRef("title.large")))
        UiSpacer(SpacerAtom(heightDp = SPACER_SM_DP))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(spacing.space8),
            verticalArrangement = Arrangement.spacedBy(spacing.space4),
        ) {
            FilterChip(
                selected = controls.length == OTP_LENGTH_FOUR,
                onClick = { controls.onLengthChange(OTP_LENGTH_FOUR) },
                label = { Text("4-Digit") },
            )
            FilterChip(
                selected = controls.length == OTP_LENGTH_SIX,
                onClick = { controls.onLengthChange(OTP_LENGTH_SIX) },
                label = { Text("6-Digit") },
            )
            FilterChip(
                selected = controls.masked,
                onClick = controls.onMaskedToggle,
                label = { Text("Masked PIN") },
            )
            FilterChip(
                selected = controls.showError,
                onClick = controls.onErrorToggle,
                label = { Text("Error State") },
            )
            OutlinedButton(
                onClick = {
                    val sample = if (controls.length == OTP_LENGTH_FOUR) SAMPLE_OTP_FOUR else SAMPLE_OTP_SIX
                    controls.onValueChange(sample)
                    atom.onComplete?.let(onAction)
                },
            ) {
                Text("Fill Sample")
            }
            OutlinedButton(onClick = { controls.onValueChange("") }) {
                Text("Clear")
            }
        }
        UiSpacer(SpacerAtom(heightDp = SPACER_MD_DP))
        UiOtpInput(
            atom = atom,
            valueOverride = controls.value,
            onValueChange = controls.onValueChange,
            onAction = onAction,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
private fun ChipsSpecimenCard(onAction: (ActionRef) -> Unit) {
    val spacing = ThemeTokens.spacing
    var selectedIds by remember { mutableStateOf(setOf("organic", "express")) }
    var removedIds by remember { mutableStateOf(emptySet<String>()) }
    val visibleChips = DEMO_CHIPS.filterNot { it.id in removedIds }

    UiSurfaceCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UiText(TextAtom(text = "UiChip · Selectable & Removable", typography = TypographyTokenRef("title.large")))
            if (removedIds.isNotEmpty()) {
                OutlinedButton(onClick = { removedIds = emptySet() }) {
                    Text("Restore Chips")
                }
            }
        }
        UiSpacer(SpacerAtom(heightDp = SPACER_MD_DP))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(spacing.space8),
            verticalArrangement = Arrangement.spacedBy(spacing.space8),
        ) {
            visibleChips.forEach { spec ->
                val isSelected = spec.id in selectedIds
                UiChip(
                    atom =
                        ChipAtom(
                            id = "story_chip_${spec.id}",
                            label = spec.label,
                            selected = isSelected,
                            removable = true,
                            leadingIcon = IconAtom(name = spec.iconName, sizeDp = CHIP_ICON_SIZE_DP),
                            onClick = ActionRef(id = "chip_toggle_${spec.id}", type = ActionTypes.SAVE_STATE),
                            onRemove = ActionRef(id = "chip_remove_${spec.id}", type = ActionTypes.TRACK_EVENT),
                        ),
                    selectedOverride = isSelected,
                    onClick = {
                        selectedIds = if (isSelected) selectedIds - spec.id else selectedIds + spec.id
                    },
                    onRemove = { removedIds = removedIds + spec.id },
                    onAction = onAction,
                )
            }
        }
    }
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
private fun SegmentedControlSpecimenCard(onAction: (ActionRef) -> Unit) {
    var activeRail by remember { mutableStateOf("p2p") }
    val segments =
        remember {
            listOf(
                OptionItem("p2p", "Instant P2P"),
                OptionItem("bank", "Bank Rail"),
                OptionItem("scheduled", "Scheduled"),
            )
        }
    val segmentedAtom =
        remember(activeRail) {
            SegmentedControlAtom(
                fieldId = "story_rail_segment",
                options = segments,
                selectedId = activeRail,
                onChange = ActionRef(id = "story_segment_change", type = ActionTypes.SAVE_STATE),
            )
        }

    UiSurfaceCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UiText(TextAtom(text = "UiSegmentedControl · Sliding Pill", typography = TypographyTokenRef("title.large")))
            UiTag(TagAtom(label = activeRail.uppercase(), variant = TagVariant.INFO))
        }
        UiSpacer(SpacerAtom(heightDp = SPACER_MD_DP))
        UiSegmentedControl(
            atom = segmentedAtom,
            selectedIdOverride = activeRail,
            onSelect = { activeRail = it },
            onAction = onAction,
        )
    }
}
