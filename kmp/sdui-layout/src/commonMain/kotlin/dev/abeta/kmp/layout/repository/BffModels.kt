package dev.abeta.kmp.layout.repository

import kotlinx.serialization.Serializable

/**
 * Error payload returned inside a Go BFF `ginmw.Envelope` failure response.
 */
@Serializable
data class BffApiError(
    val code: String,
    val message: String,
    val detail: String? = null,
)

/**
 * Standard Go BFF `ginmw.Envelope` wrapper around SDUI responses.
 */
@Serializable
data class BffEnvelope<T>(
    val success: Boolean,
    val data: T? = null,
    val error: BffApiError? = null,
)
