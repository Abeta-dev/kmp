@file:Suppress("ktlint:standard:filename", "MatchingDeclarationName")

package dev.abeta.kmp.contracts

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Declarative motion specification attached to SDUI atoms, snippets, and bars.
 */
@Serializable
data class MotionSpec(
    val enter: MotionTransition = MotionTransition.NONE,
    val exit: MotionTransition = MotionTransition.NONE,
    @SerialName("duration_token")
    val durationToken: MotionDurationToken? = null,
    @SerialName("duration_ms")
    val durationMs: Int? = null,
    @SerialName("stagger_ms")
    val staggerMs: Int = 0,
    @SerialName("delay_ms")
    val delayMs: Int = 0,
    @SerialName("loop_effect")
    val loopEffect: MotionLoopEffect = MotionLoopEffect.NONE,
)
