@file:Suppress("MagicNumber", "LongMethod", "TooManyFunctions")

package dev.abeta.kmp.storybook

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.abeta.kmp.atoms.atoms.UiButton
import dev.abeta.kmp.atoms.atoms.UiSurfaceCard
import dev.abeta.kmp.atoms.atoms.UiTag
import dev.abeta.kmp.atoms.modifiers.shakeOnError
import dev.abeta.kmp.contracts.ActionRef
import dev.abeta.kmp.contracts.ButtonAtom
import dev.abeta.kmp.contracts.ButtonSize
import dev.abeta.kmp.contracts.ButtonVariant
import dev.abeta.kmp.contracts.GridSnippetData
import dev.abeta.kmp.contracts.MetricCardSnippetData
import dev.abeta.kmp.contracts.ResponsiveColumnsSpec
import dev.abeta.kmp.contracts.SduiJson
import dev.abeta.kmp.contracts.SnippetEnvelope
import dev.abeta.kmp.contracts.SnippetTypes
import dev.abeta.kmp.contracts.TagAtom
import dev.abeta.kmp.contracts.TagVariant
import dev.abeta.kmp.contracts.TextAtom
import dev.abeta.kmp.core.platform.AudioCaptureConfig
import dev.abeta.kmp.core.platform.AudioLevelSample
import dev.abeta.kmp.core.platform.DeepLinkParser
import dev.abeta.kmp.core.platform.DesktopTrayState
import dev.abeta.kmp.core.platform.FakeAudioCapturePort
import dev.abeta.kmp.core.platform.FakeSpeechToTextPort
import dev.abeta.kmp.core.platform.HapticStyle
import dev.abeta.kmp.core.platform.OsSettingsSection
import dev.abeta.kmp.core.platform.RecordingDesktopSystemPort
import dev.abeta.kmp.core.platform.SpeechTranscriptChunk
import dev.abeta.kmp.core.platform.TrayStatusKind
import dev.abeta.kmp.core.platform.createPlatformClipboardPort
import dev.abeta.kmp.core.platform.createPlatformDesktopSystemPort
import dev.abeta.kmp.core.platform.createPlatformHapticEngine
import dev.abeta.kmp.core.platform.currentPlatformCapabilities
import dev.abeta.kmp.core.platform.formatCurrencyCents
import dev.abeta.kmp.core.result.getOrNull
import dev.abeta.kmp.layout.platform.PlatformBridgeBundle
import dev.abeta.kmp.layout.snippet.BuiltInSnippetModule
import dev.abeta.kmp.tokens.ProvideAdaptiveLayoutMetrics
import dev.abeta.kmp.tokens.ThemeTokens
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private enum class FormFactorPreview(
    val label: String,
    val widthDp: Int,
    val heightDp: Int,
    val isWatch: Boolean = false,
) {
    WATCH("Watch (260dp)", 260, 280, isWatch = true),
    PHONE("Phone (390dp)", 390, 844),
    TABLET("Tablet / iPad (768dp)", 768, 1024),
    DESKTOP("Desktop / Web (1180dp)", 1180, 820),
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
fun ResponsiveAndBridgesStory(onAction: (ActionRef) -> Unit) {
    val spacing = ThemeTokens.spacing
    val typography = ThemeTokens.typography
    val colors = ThemeTokens.colors
    var selectedFactor by remember { mutableStateOf(FormFactorPreview.TABLET) }
    var bridgeLog by remember { mutableStateOf("Tap any OS bridge trigger below to inspect live host results.") }
    var shakeSequence by remember { mutableLongStateOf(0L) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacing.space16),
    ) {
        Text(
            text = "Adaptive Form-Factor Breakpoints",
            style = typography.headlineSmall,
            color = colors.text.primary,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(spacing.space8),
            verticalArrangement = Arrangement.spacedBy(spacing.space8),
        ) {
            FormFactorPreview.entries.forEach { preset ->
                FilterChip(
                    selected = selectedFactor == preset,
                    onClick = { selectedFactor = preset },
                    label = { Text(preset.label) },
                )
            }
        }
        ProvideAdaptiveLayoutMetrics(
            maxWidth = selectedFactor.widthDp.dp,
            maxHeight = selectedFactor.heightDp.dp,
            isWatch = selectedFactor.isWatch,
        ) {
            AdaptiveGridPreviewCard(selectedFactor = selectedFactor, onAction = onAction)
        }

        Text(
            text = "Core OS Bridges & Capabilities",
            style = typography.headlineSmall,
            color = colors.text.primary,
        )
        CapabilitiesSnapshotCard(
            bridgeLog = bridgeLog,
            shakeSequence = shakeSequence,
            onTriggerClipboardAndLink = {
                val parsed = DeepLinkParser.parse("abeta://store-home?promo=FRESH10")
                val clipboard = createPlatformClipboardPort()
                clipboard.copy(parsed?.rawUri ?: "", label = "DeepLink")
                val echoed = clipboard.readText().getOrNull().orEmpty()
                bridgeLog =
                    "DeepLink screen=${parsed?.screenId} promo=${parsed?.params?.get("promo")} · clipboard=$echoed"
                onAction(
                    ActionRef(
                        type = "copy_to_clipboard",
                        payload = buildJsonObject { put("text", parsed?.rawUri ?: "") },
                    ),
                )
            },
            onTriggerHaptic = {
                createPlatformHapticEngine().perform(HapticStyle.SUCCESS)
                bridgeLog = "Triggered OS HapticStyle.SUCCESS on ${currentPlatformCapabilities().platform.name}"
                onAction(
                    ActionRef(
                        type = "trigger_feedback",
                        payload = buildJsonObject { put("effect", "haptic_tick") },
                    ),
                )
            },
            onTriggerShake = {
                shakeSequence += 1L
                createPlatformHapticEngine().perform(HapticStyle.ERROR)
                bridgeLog = "Triggered error shake #$shakeSequence"
                onAction(
                    ActionRef(
                        type = "trigger_feedback",
                        payload = buildJsonObject { put("effect", "shake_error") },
                    ),
                )
            },
        )

        Text(
            text = "Voice & Desktop OS Bridges",
            style = typography.headlineSmall,
            color = colors.text.primary,
        )
        VoiceAndDesktopBridgesCard(onAction = onAction)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
private fun VoiceAndDesktopBridgesCard(onAction: (ActionRef) -> Unit) {
    val spacing = ThemeTokens.spacing
    val typography = ThemeTokens.typography
    val colors = ThemeTokens.colors
    val scope = rememberCoroutineScope()
    val desktopPort = remember { createPlatformDesktopSystemPort() }
    val previewBundle =
        remember {
            PlatformBridgeBundle.createTestDefault(
                audioCapturePort = FakeAudioCapturePort(recordedBytes = ByteArray(1024)),
                speechToTextPort = FakeSpeechToTextPort(),
                desktopSystemPort = RecordingDesktopSystemPort(),
            )
        }
    val audioPreview = previewBundle.audioCapturePort as FakeAudioCapturePort
    val speechPreview = previewBundle.speechToTextPort as FakeSpeechToTextPort
    val desktopPreview = previewBundle.desktopSystemPort as RecordingDesktopSystemPort
    var voiceDesktopLog by remember {
        mutableStateOf(
            "AudioCapturePort, SpeechToTextPort & DesktopSystemPort wired in PlatformBridgeBundle.",
        )
    }

    UiSurfaceCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(spacing.space4),
            verticalArrangement = Arrangement.spacedBy(spacing.space12),
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(spacing.space8),
                verticalArrangement = Arrangement.spacedBy(spacing.space8),
            ) {
                UiTag(TagAtom(label = "AudioCapturePort: ${audioPreview.state.value}", variant = TagVariant.BRAND))
                UiTag(TagAtom(label = "SpeechToTextPort: ${speechPreview.state.value}", variant = TagVariant.INFO))
                UiTag(
                    TagAtom(
                        label =
                            if (desktopPort.isSupported) {
                                "DesktopSystemPort: Native Ready"
                            } else {
                                "DesktopSystemPort: Headless / Non-Desktop"
                            },
                        variant = if (desktopPort.isSupported) TagVariant.SUCCESS else TagVariant.NEUTRAL,
                    ),
                )
            }
            Text(text = voiceDesktopLog, style = typography.bodyMedium, color = colors.text.primary)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(spacing.space8),
                verticalArrangement = Arrangement.spacedBy(spacing.space8),
            ) {
                UiButton(
                    atom =
                        ButtonAtom(
                            label = "Simulate Voice Capture",
                            size = ButtonSize.SMALL,
                            onClick = ActionRef(id = "story_voice_capture", type = "track_event"),
                        ),
                    onAction = {
                        scope.launch {
                            audioPreview.startCapture(AudioCaptureConfig())
                            audioPreview.emitLevel(AudioLevelSample(normalizedAmplitude = 0.72f))
                            val bytes = audioPreview.stopCapture().getOrNull()?.size ?: 0
                            voiceDesktopLog =
                                "AudioCapturePort captured $bytes PCM bytes (16kHz mono, peak 0.72)."
                        }
                        onAction(
                            ActionRef(
                                id = "story_voice_capture",
                                type = "track_event",
                                payload = buildJsonObject { put("port", "AudioCapturePort") },
                            ),
                        )
                    },
                )
                UiButton(
                    atom =
                        ButtonAtom(
                            label = "Simulate Speech Transcript",
                            variant = ButtonVariant.SECONDARY,
                            size = ButtonSize.SMALL,
                            onClick = ActionRef(id = "story_speech_stt", type = "track_event"),
                        ),
                    onAction = {
                        scope.launch {
                            speechPreview.startListening(localeTag = "en-US", partialResults = true)
                            speechPreview.emitTranscript(
                                SpeechTranscriptChunk(
                                    text = "Show fresh avocados under five dollars",
                                    isFinal = true,
                                    confidence = 0.96f,
                                    localeTag = "en-US",
                                ),
                            )
                            val chunk = speechPreview.stopListening().getOrNull()
                            voiceDesktopLog =
                                "SpeechToTextPort final transcript: \"${chunk?.text.orEmpty()}\" (96%)."
                        }
                        onAction(
                            ActionRef(
                                id = "story_speech_stt",
                                type = "track_event",
                                payload = buildJsonObject { put("port", "SpeechToTextPort") },
                            ),
                        )
                    },
                )
                UiButton(
                    atom =
                        ButtonAtom(
                            label = "Update Desktop Tray & Badge",
                            variant = ButtonVariant.OUTLINE,
                            size = ButtonSize.SMALL,
                            onClick = ActionRef(id = "story_desktop_tray", type = "track_event"),
                        ),
                    onAction = {
                        scope.launch {
                            desktopPreview.updateTrayState(
                                DesktopTrayState(
                                    tooltip = "Abeta KMP · Synced",
                                    status = TrayStatusKind.ACTIVE,
                                    badgeCount = 3,
                                ),
                            )
                            desktopPreview.setBadgeCount(3)
                            desktopPreview.openOsSettings(OsSettingsSection.PRIVACY_MICROPHONE)
                            voiceDesktopLog =
                                "DesktopSystemPort recorded tray=${desktopPreview.trayStates.last().status}, " +
                                "badge=${desktopPreview.badgeCounts.last()}, " +
                                "settings=${desktopPreview.openedSettings.last()}."
                        }
                        onAction(
                            ActionRef(
                                id = "story_desktop_tray",
                                type = "track_event",
                                payload = buildJsonObject { put("port", "DesktopSystemPort") },
                            ),
                        )
                    },
                )
            }
        }
    }
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
private fun AdaptiveGridPreviewCard(
    selectedFactor: FormFactorPreview,
    onAction: (ActionRef) -> Unit,
) {
    val spacing = ThemeTokens.spacing
    val typography = ThemeTokens.typography
    val colors = ThemeTokens.colors
    val metrics = ThemeTokens.adaptiveMetrics
    val registry = remember { BuiltInSnippetModule.createDefaultRegistry() }
    val previewGrid = remember { buildResponsiveGridEnvelope() }

    UiSurfaceCard(modifier = Modifier.widthIn(max = metrics.maxContentWidth).fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.space12)) {
            Text(
                text =
                    "${selectedFactor.label} -> widthClass=${metrics.widthClass}, " +
                        "formFactor=${metrics.formFactor}, columns=${metrics.recommendedGridColumns}, " +
                        "twoPane=${metrics.useTwoPane}",
                style = typography.bodyMedium,
                color = colors.text.secondary,
            )
            registry.Render(
                envelope = previewGrid,
                formValues = emptyMap(),
                onFormValueChange = { _, _ -> },
                onAction = onAction,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
private fun CapabilitiesSnapshotCard(
    bridgeLog: String,
    shakeSequence: Long,
    onTriggerClipboardAndLink: () -> Unit,
    onTriggerHaptic: () -> Unit,
    onTriggerShake: () -> Unit,
) {
    val spacing = ThemeTokens.spacing
    val typography = ThemeTokens.typography
    val colors = ThemeTokens.colors
    val caps = remember { currentPlatformCapabilities() }
    val formattedPrice = remember(caps.localeTag) { formatCurrencyCents(1249L, "USD") }

    UiSurfaceCard(modifier = Modifier.fillMaxWidth().shakeOnError(shakeSequence)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(spacing.space4),
            verticalArrangement = Arrangement.spacedBy(spacing.space12),
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(spacing.space8),
                verticalArrangement = Arrangement.spacedBy(spacing.space8),
            ) {
                UiTag(TagAtom(label = caps.platform.name, variant = TagVariant.BRAND))
                UiTag(TagAtom(label = "Locale: ${caps.localeTag}", variant = TagVariant.NEUTRAL))
                UiTag(TagAtom(label = "TZ: ${caps.timezoneId}", variant = TagVariant.INFO))
                UiTag(TagAtom(label = "Currency: $formattedPrice", variant = TagVariant.SUCCESS))
                UiTag(
                    TagAtom(
                        label = if (caps.supportsBiometrics) "Biometrics Ready" else "Biometrics Optional",
                        variant = TagVariant.WARNING,
                    ),
                )
            }
            Text(text = bridgeLog, style = typography.bodyMedium, color = colors.text.primary)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(spacing.space8),
                verticalArrangement = Arrangement.spacedBy(spacing.space8),
            ) {
                UiButton(
                    atom =
                        ButtonAtom(
                            label = "Parse Deep Link & Copy",
                            size = ButtonSize.SMALL,
                            onClick =
                                ActionRef(
                                    id = "story_deep_link_copy",
                                    type = "copy_to_clipboard",
                                    payload = buildJsonObject { put("text", "abeta://store-home?promo=FRESH10") },
                                ),
                        ),
                    onAction = { onTriggerClipboardAndLink() },
                )
                UiButton(
                    atom =
                        ButtonAtom(
                            label = "Haptic Tick",
                            variant = ButtonVariant.SECONDARY,
                            size = ButtonSize.SMALL,
                            onClick =
                                ActionRef(
                                    id = "story_haptic_tick",
                                    type = "trigger_feedback",
                                    payload = buildJsonObject { put("effect", "haptic_tick") },
                                ),
                        ),
                    onAction = { onTriggerHaptic() },
                )
                UiButton(
                    atom =
                        ButtonAtom(
                            label = "Shake Error Test",
                            variant = ButtonVariant.OUTLINE,
                            size = ButtonSize.SMALL,
                            onClick =
                                ActionRef(
                                    id = "story_shake_error",
                                    type = "trigger_feedback",
                                    payload = buildJsonObject { put("effect", "shake_error") },
                                ),
                        ),
                    onAction = { onTriggerShake() },
                )
            }
        }
    }
}

private fun buildResponsiveGridEnvelope(): SnippetEnvelope {
    val cards =
        (1..4).map { idx ->
            val data =
                SduiJson.encodeToJsonElement(
                    MetricCardSnippetData.serializer(),
                    MetricCardSnippetData(
                        label = TextAtom(text = "Tile $idx"),
                        value = TextAtom(text = "${idx * 10}ms"),
                    ),
                ) as JsonObject
            SnippetEnvelope(
                snippetId = "responsive_tile_$idx",
                snippetType = SnippetTypes.METRIC_CARD,
                data = data,
            )
        }
    val grid =
        GridSnippetData(
            title = TextAtom(text = "Responsive SDUI Grid (2 / 3 / 4 Columns)"),
            columns = 2,
            columnsByWidth = ResponsiveColumnsSpec(compact = 2, medium = 3, expanded = 4),
            spacingDp = 8,
            items = cards,
        )
    return SnippetEnvelope(
        snippetId = "responsive_demo_grid",
        snippetType = SnippetTypes.GRID,
        data = SduiJson.encodeToJsonElement(GridSnippetData.serializer(), grid) as JsonObject,
    )
}
