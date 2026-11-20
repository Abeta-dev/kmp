package dev.abeta.kmp.core.identity

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlin.uuid.Uuid

/**
 * Platform-agnostic generator for unique opaque identifiers.
 */
interface UuidGenerator {
    /** Generates and returns a new UUID string. */
    fun nextId(): String
}

/**
 * Production [UuidGenerator] backed by the Kotlin standard library's cryptographically backed [Uuid.random].
 */
class RandomUuidGenerator : UuidGenerator {
    override fun nextId(): String = Uuid.random().toString()
}

/**
 * Deterministic [UuidGenerator] useful for reproducible tests, snapshots, and storybook demos.
 */
class SequentialUuidGenerator(
    private val prefix: String = "00000000-0000-4000-8000-",
    startSequence: Long = 1L,
) : UuidGenerator {
    private val sequence = MutableStateFlow(startSequence)

    override fun nextId(): String {
        var nextValue = 0L
        sequence.update { current ->
            nextValue = current
            current + 1L
        }
        val suffix = nextValue.toString().padStart(UUID_SUFFIX_WIDTH, '0').takeLast(UUID_SUFFIX_WIDTH)
        return "$prefix$suffix"
    }

    private companion object {
        const val UUID_SUFFIX_WIDTH = 12
    }
}
