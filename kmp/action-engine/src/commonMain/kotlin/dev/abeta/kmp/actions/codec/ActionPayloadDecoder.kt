package dev.abeta.kmp.actions.codec

import dev.abeta.kmp.actions.model.ActionPayload
import dev.abeta.kmp.actions.model.ActionTypes
import dev.abeta.kmp.actions.model.BackPressPayload
import dev.abeta.kmp.actions.model.BatchPayload
import dev.abeta.kmp.actions.model.BiometricAuthPayload
import dev.abeta.kmp.actions.model.CallApiPayload
import dev.abeta.kmp.actions.model.CopyToClipboardPayload
import dev.abeta.kmp.actions.model.DeleteStatePayload
import dev.abeta.kmp.actions.model.DismissOverlayPayload
import dev.abeta.kmp.actions.model.NavigatePayload
import dev.abeta.kmp.actions.model.OpenDialogPayload
import dev.abeta.kmp.actions.model.OpenUrlPayload
import dev.abeta.kmp.actions.model.RefreshPagePayload
import dev.abeta.kmp.actions.model.RequestPermissionPayload
import dev.abeta.kmp.actions.model.SaveStatePayload
import dev.abeta.kmp.actions.model.SharePayload
import dev.abeta.kmp.actions.model.ShowToastPayload
import dev.abeta.kmp.actions.model.TrackEventPayload
import dev.abeta.kmp.actions.model.TriggerFeedbackPayload
import dev.abeta.kmp.actions.model.UpdateSnippetsPayload
import dev.abeta.kmp.contracts.SduiJson
import dev.abeta.kmp.core.error.NotFoundError
import dev.abeta.kmp.core.error.SerializationError
import dev.abeta.kmp.core.result.AppResult
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.serializer

/**
 * Strategy decoding a raw [JsonObject] wire payload into a typed [ActionPayload].
 */
fun interface ActionPayloadDecoder<out P : ActionPayload> {
    /** Decodes [payload] into [P] or returns a typed [AppResult.Failure]. */
    fun decode(payload: JsonObject): AppResult<P>
}

/**
 * Creates a Kotlinx-Serialization-backed [ActionPayloadDecoder] for [P].
 */
inline fun <reified P : ActionPayload> kotlinxActionDecoder(json: Json = SduiJson): ActionPayloadDecoder<P> =
    ActionPayloadDecoder { payload ->
        try {
            AppResult.Success(json.decodeFromJsonElement(serializer<P>(), payload))
        } catch (exception: SerializationException) {
            AppResult.Failure(
                SerializationError(
                    message = exception.message ?: "Failed to decode ${P::class.simpleName}",
                    cause = exception,
                ),
            )
        } catch (exception: IllegalArgumentException) {
            AppResult.Failure(
                SerializationError(
                    message = exception.message ?: "Invalid payload for ${P::class.simpleName}",
                    cause = exception,
                ),
            )
        }
    }

/**
 * Immutable, OCP-compliant registry mapping wire action `type` strings to [ActionPayloadDecoder]s.
 */
class ActionDecoderRegistry private constructor(
    private val decoders: Map<String, ActionPayloadDecoder<ActionPayload>>,
) {
    /** Returns the decoder registered for [type], or `null` if absent. */
    fun decoderFor(type: String): ActionPayloadDecoder<ActionPayload>? = decoders[type]

    /** Returns all action `type` keys registered in this snapshot. */
    fun registeredTypes(): Set<String> = decoders.keys

    /** Decodes [payload] for the given action [type], failing with [NotFoundError] when unregistered. */
    fun decode(
        type: String,
        payload: JsonObject,
    ): AppResult<ActionPayload> {
        val decoder =
            decoders[type]
                ?: return AppResult.Failure(
                    NotFoundError(
                        resource = type,
                        message = "No ActionPayloadDecoder registered for action type '$type'.",
                    ),
                )
        return decoder.decode(payload)
    }

    /** Creates a mutable [Builder] pre-populated with this registry's entries. */
    fun toBuilder(): Builder = Builder(decoders)

    /** Mutable builder for constructing an immutable [ActionDecoderRegistry] snapshot. */
    class Builder internal constructor(
        initial: Map<String, ActionPayloadDecoder<ActionPayload>> = emptyMap(),
    ) {
        private val entries = initial.toMutableMap()

        /** Registers or overrides the [decoder] for action [type]. */
        fun <P : ActionPayload> register(
            type: String,
            decoder: ActionPayloadDecoder<P>,
        ): Builder =
            apply {
                require(type.isNotBlank()) { "Action type must not be blank." }
                entries[type] = decoder
            }

        /** Builds an immutable [ActionDecoderRegistry] snapshot. */
        fun build(): ActionDecoderRegistry = ActionDecoderRegistry(entries.toMap())
    }

    companion object {
        /** Returns a new empty [Builder]. */
        fun builder(): Builder = Builder()
    }
}

/**
 * Registers decoders for all built-in Go BFF action payload types.
 */
object BuiltInActionDecoders {
    /** Populates [builder] with decoders for all standard [ActionTypes]. */
    fun registerDefaults(
        builder: ActionDecoderRegistry.Builder,
        json: Json = SduiJson,
    ): ActionDecoderRegistry.Builder =
        builder.apply {
            register(ActionTypes.NAVIGATE, kotlinxActionDecoder<NavigatePayload>(json))
            register(ActionTypes.BACK_PRESS, kotlinxActionDecoder<BackPressPayload>(json))
            register(ActionTypes.OPEN_DIALOG, kotlinxActionDecoder<OpenDialogPayload>(json))
            register(ActionTypes.DISMISS_OVERLAY, kotlinxActionDecoder<DismissOverlayPayload>(json))
            register(ActionTypes.SHOW_TOAST, kotlinxActionDecoder<ShowToastPayload>(json))
            register(ActionTypes.CALL_API, kotlinxActionDecoder<CallApiPayload>(json))
            register(ActionTypes.SAVE_STATE, kotlinxActionDecoder<SaveStatePayload>(json))
            register(ActionTypes.DELETE_STATE, kotlinxActionDecoder<DeleteStatePayload>(json))
            register(ActionTypes.TRACK_EVENT, kotlinxActionDecoder<TrackEventPayload>(json))
            register(ActionTypes.REFRESH_PAGE, kotlinxActionDecoder<RefreshPagePayload>(json))
            register(ActionTypes.COPY_TO_CLIPBOARD, kotlinxActionDecoder<CopyToClipboardPayload>(json))
            register(ActionTypes.UPDATE_SNIPPETS, kotlinxActionDecoder<UpdateSnippetsPayload>(json))
            register(ActionTypes.BATCH, kotlinxActionDecoder<BatchPayload>(json))
            register(ActionTypes.TRIGGER_FEEDBACK, kotlinxActionDecoder<TriggerFeedbackPayload>(json))
            register(ActionTypes.OPEN_URL, kotlinxActionDecoder<OpenUrlPayload>(json))
            register(ActionTypes.SHARE, kotlinxActionDecoder<SharePayload>(json))
            register(ActionTypes.REQUEST_PERMISSION, kotlinxActionDecoder<RequestPermissionPayload>(json))
            register(ActionTypes.BIOMETRIC_AUTH, kotlinxActionDecoder<BiometricAuthPayload>(json))
        }

    /** Convenience factory creating an [ActionDecoderRegistry] with all built-in decoders. */
    fun createDefaultRegistry(json: Json = SduiJson): ActionDecoderRegistry =
        registerDefaults(ActionDecoderRegistry.builder(), json).build()
}
