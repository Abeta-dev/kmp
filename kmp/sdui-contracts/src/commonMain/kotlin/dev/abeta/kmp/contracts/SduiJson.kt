package dev.abeta.kmp.contracts

import kotlinx.serialization.json.Json

/**
 * Shared lenient [Json] serializer configuration for Go BFF SDUI wire contracts.
 */
val SduiJson: Json =
    Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        explicitNulls = false
    }
