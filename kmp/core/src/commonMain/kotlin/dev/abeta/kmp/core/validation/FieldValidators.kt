package dev.abeta.kmp.core.validation

import dev.abeta.kmp.core.error.ValidationError

/**
 * Pure multiplatform string and field validators shared cleanly across actions, state, and UI forms.
 */
object FieldValidators {
    private const val MIN_EMAIL_LENGTH = 5
    private const val MAX_EMAIL_LENGTH = 254

    val EMAIL_REGEX: Regex = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
    val E164_PHONE_REGEX: Regex = Regex("^\\+?[1-9]\\d{6,14}$")
    val HTTP_URL_REGEX: Regex = Regex("^https?://[^\\s/$.?#].[^\\s]*$", RegexOption.IGNORE_CASE)

    /** Returns `true` when [email] conforms to standard email format and length bounds. */
    fun isValidEmail(email: String?): Boolean {
        val trimmed = email?.trim().orEmpty()
        return trimmed.length in MIN_EMAIL_LENGTH..MAX_EMAIL_LENGTH && EMAIL_REGEX.matches(trimmed)
    }

    /** Strips formatting symbols from [phone] and validates it against E.164 international phone rules. */
    fun isValidPhone(phone: String?): Boolean {
        val normalized = phone?.trim()?.replace(Regex("[\\s()-]"), "").orEmpty()
        return E164_PHONE_REGEX.matches(normalized)
    }

    /** Returns `true` when [url] starts with `http://` or `https://` and contains a valid host token. */
    fun isValidUrl(url: String?): Boolean {
        val trimmed = url?.trim().orEmpty()
        return trimmed.isNotEmpty() && HTTP_URL_REGEX.matches(trimmed)
    }

    /** Validates non-blank presence for [field], returning `null` if valid or a [ValidationError]. */
    fun validateRequired(
        field: String,
        value: String?,
        message: String = "$field is required",
    ): ValidationError? =
        if (value.isNullOrBlank()) {
            ValidationError(field = field, message = message, code = "REQUIRED_FIELD")
        } else {
            null
        }

    /** Validates email syntax for [field], returning `null` if valid or a [ValidationError]. */
    fun validateEmail(
        field: String = "email",
        value: String?,
        message: String = "Enter a valid email address",
    ): ValidationError? =
        validateRequired(field, value)
            ?: if (!isValidEmail(value)) {
                ValidationError(field = field, message = message, code = "INVALID_EMAIL")
            } else {
                null
            }

    /** Validates international phone syntax for [field], returning `null` if valid or a [ValidationError]. */
    fun validatePhone(
        field: String = "phone",
        value: String?,
        message: String = "Enter a valid international phone number",
    ): ValidationError? =
        validateRequired(field, value)
            ?: if (!isValidPhone(value)) {
                ValidationError(field = field, message = message, code = "INVALID_PHONE")
            } else {
                null
            }

    /** Validates inclusive character length bounds for [field]. */
    fun validateLength(
        field: String,
        value: String?,
        minLength: Int = 0,
        maxLength: Int = Int.MAX_VALUE,
    ): ValidationError? {
        val len = value?.length ?: 0
        return when {
            len < minLength -> {
                ValidationError(
                    field = field,
                    message = "$field must be at least $minLength characters",
                    code = "MIN_LENGTH",
                )
            }

            len > maxLength -> {
                ValidationError(
                    field = field,
                    message = "$field must be at most $maxLength characters",
                    code = "MAX_LENGTH",
                )
            }

            else -> {
                null
            }
        }
    }
}
