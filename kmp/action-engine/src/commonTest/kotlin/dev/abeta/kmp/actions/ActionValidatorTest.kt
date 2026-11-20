package dev.abeta.kmp.actions

import dev.abeta.kmp.actions.codec.BuiltInActionDecoders
import dev.abeta.kmp.actions.handler.ActionOutcome
import dev.abeta.kmp.actions.handler.BuiltInActionHandlers
import dev.abeta.kmp.actions.handler.DefaultActionDispatcher
import dev.abeta.kmp.actions.model.ActionContext
import dev.abeta.kmp.actions.model.ActionTypes
import dev.abeta.kmp.actions.validation.ActionValidationRule
import dev.abeta.kmp.actions.validation.DefaultActionValidator
import dev.abeta.kmp.contracts.ActionRef
import dev.abeta.kmp.contracts.ValidationRuleContract
import dev.abeta.kmp.core.error.ValidationError
import dev.abeta.kmp.core.result.AppResult
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ActionValidatorTest {
    private val validator =
        DefaultActionValidator(
            customRules =
                mapOf(
                    "min_cart_items" to
                        ActionValidationRule { rule, _, context ->
                            val requiredMin = rule.value?.toIntOrNull() ?: 0
                            val actual =
                                context.sessionState[rule.fieldId]
                                    ?.toString()
                                    ?.toIntOrNull() ?: 0
                            if (actual >= requiredMin) {
                                AppResult.Success(Unit)
                            } else {
                                AppResult.Failure(
                                    ValidationError(
                                        field = rule.fieldId,
                                        message = rule.message,
                                    ),
                                )
                            }
                        },
                ),
        )

    private val dispatcher =
        DefaultActionDispatcher(
            decoderRegistry = BuiltInActionDecoders.createDefaultRegistry(),
            actionRegistry = BuiltInActionHandlers.createDefaultRegistry(),
            validator = validator,
        )

    @Test
    fun requiredMinLengthAndMaxLengthRulesEnforced() =
        runTest {
            val action =
                ActionRef(
                    type = ActionTypes.NAVIGATE,
                    payload = buildJsonObject { put("route", "/checkout") },
                    validations =
                        listOf(
                            ValidationRuleContract("full_name", "required", message = "Full name is required"),
                            ValidationRuleContract("full_name", "min_length", "3", "Min 3 chars"),
                            ValidationRuleContract("full_name", "max_length", "10", "Max 10 chars"),
                        ),
                )

            val missingErr =
                assertIs<ValidationError>(
                    assertIs<AppResult.Failure>(dispatcher.dispatch(action, ActionContext()).single()).error,
                )
            assertEquals("full_name", missingErr.field)
            assertEquals("Full name is required", missingErr.message)

            val shortErr =
                assertIs<ValidationError>(
                    assertIs<AppResult.Failure>(
                        dispatcher
                            .dispatch(action, ActionContext(sessionState = mapOf("full_name" to JsonPrimitive("Al"))))
                            .single(),
                    ).error,
                )
            assertEquals("Min 3 chars", shortErr.message)

            val longErr =
                assertIs<ValidationError>(
                    assertIs<AppResult.Failure>(
                        dispatcher
                            .dispatch(
                                action,
                                ActionContext(sessionState = mapOf("full_name" to JsonPrimitive("VeryLongName"))),
                            ).single(),
                    ).error,
                )
            assertEquals("Max 10 chars", longErr.message)
        }

    @Test
    fun regexEqualsMetadataFallbackAndCustomRulePassWhenSatisfied() =
        runTest {
            val action =
                ActionRef(
                    type = ActionTypes.NAVIGATE,
                    payload = buildJsonObject { put("route", "/checkout") },
                    validations =
                        listOf(
                            ValidationRuleContract(
                                fieldId = "work_email",
                                rule = "regex",
                                value = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$",
                                message = "Enter a valid email address",
                            ),
                            ValidationRuleContract("country_code", "equals", "IN", "Only IN supported"),
                            ValidationRuleContract("cart_count", "min_cart_items", "2", "Need 2 items"),
                        ),
                )

            val customFail =
                dispatcher.dispatch(
                    action,
                    ActionContext(
                        sessionState =
                            mapOf(
                                "work_email" to JsonPrimitive("jane@abeta.dev"),
                                "cart_count" to JsonPrimitive(1),
                            ),
                        metadata = mapOf("country_code" to "IN"),
                    ),
                )
            val customErr = assertIs<ValidationError>(assertIs<AppResult.Failure>(customFail.single()).error)
            assertEquals("cart_count", customErr.field)
            assertEquals("Need 2 items", customErr.message)

            val passingResults =
                dispatcher.dispatch(
                    action,
                    ActionContext(
                        sessionState =
                            mapOf(
                                "work_email" to JsonPrimitive("jane@abeta.dev"),
                                "cart_count" to JsonPrimitive(3),
                            ),
                        metadata = mapOf("country_code" to "IN"),
                    ),
                )
            val outcome = assertIs<AppResult.Success<ActionOutcome>>(passingResults.single()).value
            assertIs<ActionOutcome.NavigationRequested>(outcome)
        }
}
