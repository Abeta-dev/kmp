package dev.abeta.kmp.core.time

import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
internal actual fun currentEpochMillis(): Long =
    kotlin.time.Clock.System
        .now()
        .toEpochMilliseconds()
