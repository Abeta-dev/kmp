package dev.abeta.kmp.layout.snippet

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import dev.abeta.kmp.atoms.modifiers.applyMotionSpec
import dev.abeta.kmp.contracts.ActionRef
import dev.abeta.kmp.contracts.MotionLoopEffect
import dev.abeta.kmp.contracts.SduiJson
import dev.abeta.kmp.contracts.SnippetEnvelope
import dev.abeta.kmp.contracts.SnippetTypes
import dev.abeta.kmp.contracts.TrackingContract
import dev.abeta.kmp.core.error.AppError
import dev.abeta.kmp.core.error.SerializationError
import dev.abeta.kmp.core.result.AppResult
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.serializer

/**
 * [ProvidableCompositionLocal] supplying active field-level validation errors keyed by `field_id`
 * to form snippet renderers without altering the [SnippetRenderer.Render] contract.
 */
val LocalFormValidationErrors: ProvidableCompositionLocal<Map<String, String>> =
    staticCompositionLocalOf { emptyMap() }

/**
 * [ProvidableCompositionLocal] supplying a callback invoked when an individual
 * [dev.abeta.kmp.contracts.ComposedNode] with a non-null [TrackingContract] enters composition,
 * without altering the [SnippetRenderer.Render] interface contract.
 */
val LocalNodeImpressionHandler: ProvidableCompositionLocal<(String, TrackingContract) -> Unit> =
    staticCompositionLocalOf { { _, _ -> } }

internal val LocalSnippetRegistry: ProvidableCompositionLocal<SnippetRegistry?> =
    staticCompositionLocalOf { null }

internal val LocalMotionStaggerIndex: ProvidableCompositionLocal<Int> =
    staticCompositionLocalOf { 0 }

internal const val MAX_SNIPPET_NESTING_DEPTH = 6

internal val LocalSnippetDepth: ProvidableCompositionLocal<Int> =
    staticCompositionLocalOf { 0 }

internal fun buildUniqueSnippetKeys(snippets: List<SnippetEnvelope>): List<String> {
    val used = HashSet<String>(snippets.size)
    return snippets.mapIndexed { index, envelope ->
        val base = envelope.snippetId
        if (used.add(base)) {
            base
        } else {
            var suffix = index
            var candidate = "$base#$suffix"
            while (!used.add(candidate)) {
                suffix += 1
                candidate = "$base#$suffix"
            }
            candidate
        }
    }
}

/**
 * Open/Closed Strategy interface for decoding and rendering a single SDUI snippet type [T].
 *
 * Host applications can register custom [SnippetRenderer] implementations in [SnippetRegistry]
 * without modifying `sdui-layout` or `SduiScreen`.
 */
interface SnippetRenderer<T : Any> {
    /** Wire `snippet_type` identifier handled by this renderer. */
    val snippetType: String

    /** Decodes raw [data] into the typed snippet model [T]. */
    fun decode(data: JsonObject): AppResult<T>

    /** Renders the decoded [model] using stateless UI atoms. */
    @Composable
    @Suppress("ktlint:standard:function-naming", "FunctionName", "LongParameterList")
    fun Render(
        model: T,
        envelope: SnippetEnvelope,
        formValues: Map<String, String>,
        onFormValueChange: (String, String) -> Unit,
        onAction: (ActionRef) -> Unit,
    )
}

/**
 * Type-safe wrapper binding a [SnippetRenderer] to its concrete model type [T]
 * so [SnippetRegistry] can decode and render entries polymorphically.
 */
class SnippetEntry<T : Any>(
    val renderer: SnippetRenderer<T>,
) {
    /** Wire `snippet_type` identifier handled by [renderer]. */
    val snippetType: String
        get() = renderer.snippetType

    /** Decodes [data] using [renderer]. */
    fun decode(data: JsonObject): AppResult<T> = renderer.decode(data)

    /** Decodes [envelope.data] and renders [renderer], or invokes [onDecodeError] on failure. */
    @Composable
    @Suppress("ktlint:standard:function-naming", "FunctionName", "LongParameterList")
    fun Render(
        envelope: SnippetEnvelope,
        formValues: Map<String, String>,
        onFormValueChange: (String, String) -> Unit,
        onAction: (ActionRef) -> Unit,
        onDecodeError: @Composable (SnippetEnvelope, AppError) -> Unit = { _, _ -> },
    ) {
        val decodeResult =
            remember(envelope.snippetId, envelope.snippetType, envelope.data, renderer) {
                renderer.decode(envelope.data)
            }
        when (decodeResult) {
            is AppResult.Success -> {
                val rawMotion = envelope.motion
                val motion =
                    if (
                        rawMotion != null &&
                        envelope.snippetType == SnippetTypes.MARQUEE_STRIP &&
                        rawMotion.loopEffect == MotionLoopEffect.MARQUEE
                    ) {
                        rawMotion.copy(loopEffect = MotionLoopEffect.NONE)
                    } else {
                        rawMotion
                    }
                if (motion != null) {
                    val staggerIndex = LocalMotionStaggerIndex.current
                    Box(
                        modifier =
                            Modifier.applyMotionSpec(
                                motion = motion,
                                itemIndex = staggerIndex,
                                motionKey = envelope.snippetId,
                            ),
                    ) {
                        renderer.Render(
                            model = decodeResult.value,
                            envelope = envelope,
                            formValues = formValues,
                            onFormValueChange = onFormValueChange,
                            onAction = onAction,
                        )
                    }
                } else {
                    renderer.Render(
                        model = decodeResult.value,
                        envelope = envelope,
                        formValues = formValues,
                        onFormValueChange = onFormValueChange,
                        onAction = onAction,
                    )
                }
            }

            is AppResult.Failure -> {
                onDecodeError(envelope, decodeResult.error)
            }
        }
    }
}

/**
 * Creates a Kotlinx-Serialization decoder function for snippet payload type [T].
 */
inline fun <reified T : Any> kotlinxSnippetDecoder(json: Json = SduiJson): (JsonObject) -> AppResult<T> =
    { data ->
        try {
            AppResult.Success(json.decodeFromJsonElement(serializer<T>(), data))
        } catch (exception: SerializationException) {
            AppResult.Failure(
                SerializationError(
                    message = exception.message ?: "Failed to decode snippet ${T::class.simpleName}",
                    cause = exception,
                ),
            )
        } catch (exception: IllegalArgumentException) {
            AppResult.Failure(
                SerializationError(
                    message = exception.message ?: "Invalid payload for snippet ${T::class.simpleName}",
                    cause = exception,
                ),
            )
        }
    }

/**
 * Immutable, OCP-compliant registry mapping `snippet_type` keys to [SnippetEntry] instances.
 */
class SnippetRegistry private constructor(
    private val entries: Map<String, SnippetEntry<*>>,
) {
    /** Resolves the [SnippetEntry] registered for [snippetType], or `null` if unregistered. */
    fun resolve(snippetType: String): SnippetEntry<*>? = entries[snippetType]

    /** Returns all registered `snippet_type` identifiers in this snapshot. */
    fun registeredTypes(): Set<String> = entries.keys

    /** Creates a mutable [Builder] initialized with this registry's entries. */
    fun toBuilder(): Builder = Builder(entries)

    /**
     * Resolves and renders [envelope], invoking [onUnknownSnippet] when unregistered
     * or [onDecodeError] when decoding fails.
     */
    @Composable
    @Suppress("ktlint:standard:function-naming", "FunctionName", "LongParameterList")
    fun Render(
        envelope: SnippetEnvelope,
        formValues: Map<String, String>,
        onFormValueChange: (String, String) -> Unit,
        onAction: (ActionRef) -> Unit,
        onUnknownSnippet: @Composable (SnippetEnvelope) -> Unit = {},
        onDecodeError: @Composable (SnippetEnvelope, AppError) -> Unit = { _, _ -> },
    ) {
        val entry = resolve(envelope.snippetType)
        if (entry == null) {
            onUnknownSnippet(envelope)
            return
        }
        CompositionLocalProvider(LocalSnippetRegistry provides this) {
            entry.Render(
                envelope = envelope,
                formValues = formValues,
                onFormValueChange = onFormValueChange,
                onAction = onAction,
                onDecodeError = onDecodeError,
            )
        }
    }

    /** Mutable builder for constructing an immutable [SnippetRegistry] snapshot. */
    class Builder internal constructor(
        initial: Map<String, SnippetEntry<*>> = emptyMap(),
    ) {
        private val entries = initial.toMutableMap()
        private var builtRegistry: SnippetRegistry? = null

        /** Registers or overrides the [renderer] for its [SnippetRenderer.snippetType]. */
        fun <T : Any> register(renderer: SnippetRenderer<T>): Builder =
            apply {
                require(renderer.snippetType.isNotBlank()) { "Snippet type must not be blank." }
                entries[renderer.snippetType] = SnippetEntry(renderer)
            }

        /** Registers or overrides a pre-wrapped [entry]. */
        fun <T : Any> register(entry: SnippetEntry<T>): Builder =
            apply {
                require(entry.snippetType.isNotBlank()) { "Snippet type must not be blank." }
                entries[entry.snippetType] = entry
            }

        /** Builds an immutable [SnippetRegistry] snapshot. */
        fun build(): SnippetRegistry =
            SnippetRegistry(entries.toMap()).also { built ->
                builtRegistry = built
            }

        internal fun resolveBuiltOrSnapshot(): SnippetRegistry = builtRegistry ?: SnippetRegistry(entries.toMap())
    }

    companion object {
        /** Returns a new empty [Builder]. */
        fun builder(): Builder = Builder()
    }
}
