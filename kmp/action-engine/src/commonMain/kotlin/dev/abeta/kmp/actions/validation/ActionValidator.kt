package dev.abeta.kmp.actions.validation

import dev.abeta.kmp.actions.model.ActionContext
import dev.abeta.kmp.contracts.ActionRef
import dev.abeta.kmp.contracts.ValidationRuleContract
import dev.abeta.kmp.core.error.ValidationError
import dev.abeta.kmp.core.result.AppResult
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Validates an [ActionRef] against the current [ActionContext] prior to decoding and execution.
 */
fun interface ActionValidator {
    /** Returns [AppResult.Success] if valid, or [AppResult.Failure] with a [ValidationError]. */
    fun validate(
        action: ActionRef,
        context: ActionContext,
    ): AppResult<Unit>
}

/**
 * Pluggable validation rule evaluated by [DefaultActionValidator] for a [ValidationRuleContract].
 */
fun interface ActionValidationRule {
    /** Validates [rule] against [action] and [context]. */
    fun evaluate(
        rule: ValidationRuleContract,
        action: ActionRef,
        context: ActionContext,
    ): AppResult<Unit>
}

/**
 * Default [ActionValidator] evaluating [ActionRef.validations] (`List<ValidationRuleContract>`)
 * against [ActionContext.sessionState] (falling back to [ActionContext.metadata]), plus any
 * host-supplied [customRules].
 *
 * Built-in rule identifiers matching the Go BFF `ActionService`:
 * - `"required"`: field value must be present and non-blank
 * - `"min_length"`: field value length must be >= `rule.value` (integer)
 * - `"max_length"`: field value length must be <= `rule.value` (integer)
 * - `"regex"`: field value must match the regular expression in `rule.value`
 * - `"equals"`: field value must equal `rule.value`
 */
class DefaultActionValidator(
    customRules: Map<String, ActionValidationRule> = emptyMap(),
) : ActionValidator {
    private val rules: Map<String, ActionValidationRule> = builtInRules + customRules

    override fun validate(
        action: ActionRef,
        context: ActionContext,
    ): AppResult<Unit> {
        for (ruleContract in action.validations) {
            val evaluator = rules[ruleContract.rule] ?: continue
            val result = evaluator.evaluate(ruleContract, action, context)
            if (result is AppResult.Failure) {
                return result
            }
        }
        return AppResult.Success(Unit)
    }

    private companion object {
        private const val MAX_REGEX_PATTERN_LENGTH = 256

        private val builtInRules: Map<String, ActionValidationRule> =
            mapOf(
                "required" to
                    ActionValidationRule { rule, _, context ->
                        val actual = resolveFieldValue(rule.fieldId, context, trim = true)
                        if (actual.isEmpty()) {
                            validationFailure(rule)
                        } else {
                            AppResult.Success(Unit)
                        }
                    },
                "min_length" to
                    ActionValidationRule { rule, _, context ->
                        val minLen = rule.value?.trim()?.toIntOrNull()
                        val actual = resolveFieldValue(rule.fieldId, context, trim = true)
                        if (minLen == null || minLen < 0 || actual.length < minLen) {
                            validationFailure(rule)
                        } else {
                            AppResult.Success(Unit)
                        }
                    },
                "max_length" to
                    ActionValidationRule { rule, _, context ->
                        val maxLen = rule.value?.trim()?.toIntOrNull()
                        val actual = resolveFieldValue(rule.fieldId, context, trim = false)
                        if (maxLen == null || maxLen < 0 || actual.length > maxLen) {
                            validationFailure(rule)
                        } else {
                            AppResult.Success(Unit)
                        }
                    },
                "regex" to
                    ActionValidationRule { rule, _, context ->
                        val pattern = rule.value
                        if (pattern.isNullOrEmpty()) {
                            AppResult.Success(Unit)
                        } else if (pattern.length > MAX_REGEX_PATTERN_LENGTH) {
                            validationFailure(rule)
                        } else {
                            val actual = resolveFieldValue(rule.fieldId, context, trim = true)
                            val matched =
                                runCatching { Regex(pattern).matches(actual) }
                                    .getOrDefault(false)
                            if (matched) {
                                AppResult.Success(Unit)
                            } else {
                                validationFailure(rule)
                            }
                        }
                    },
                "equals" to
                    ActionValidationRule { rule, _, context ->
                        val expected = rule.value
                        val actual = resolveFieldValue(rule.fieldId, context, trim = false)
                        if (expected != null && actual != expected) {
                            validationFailure(rule)
                        } else {
                            AppResult.Success(Unit)
                        }
                    },
            )

        private fun resolveFieldValue(
            fieldId: String,
            context: ActionContext,
            trim: Boolean,
        ): String {
            val stateElement = context.sessionState[fieldId]
            val raw =
                if (stateElement != null && stateElement !is JsonNull) {
                    when (stateElement) {
                        is JsonPrimitive -> stateElement.contentOrNull.orEmpty()
                        is JsonArray -> if (stateElement.isEmpty()) "" else stateElement.toString()
                        is JsonObject -> if (stateElement.isEmpty()) "" else stateElement.toString()
                    }
                } else {
                    context.metadata[fieldId].orEmpty()
                }
            return if (trim) raw.trim() else raw
        }

        private fun validationFailure(rule: ValidationRuleContract): AppResult.Failure =
            AppResult.Failure(
                ValidationError(
                    field = rule.fieldId,
                    message = rule.message,
                ),
            )
    }
}
