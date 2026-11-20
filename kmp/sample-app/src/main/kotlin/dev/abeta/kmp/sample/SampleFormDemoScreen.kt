package dev.abeta.kmp.sample

import dev.abeta.kmp.actions.model.ActionTypes
import dev.abeta.kmp.contracts.ActionRef
import dev.abeta.kmp.contracts.ButtonAtom
import dev.abeta.kmp.contracts.ButtonVariant
import dev.abeta.kmp.contracts.ColorTokenRef
import dev.abeta.kmp.contracts.FormSnippetData
import dev.abeta.kmp.contracts.HeaderSnippetData
import dev.abeta.kmp.contracts.IconAtom
import dev.abeta.kmp.contracts.KeyboardKind
import dev.abeta.kmp.contracts.LayoutConfig
import dev.abeta.kmp.contracts.PageResponse
import dev.abeta.kmp.contracts.SduiJson
import dev.abeta.kmp.contracts.SnippetEnvelope
import dev.abeta.kmp.contracts.SnippetTypes
import dev.abeta.kmp.contracts.SpacingValues
import dev.abeta.kmp.contracts.TagAtom
import dev.abeta.kmp.contracts.TextFieldAtom
import dev.abeta.kmp.contracts.ToolbarSpec
import dev.abeta.kmp.contracts.ValidationRuleContract
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Suppress("MagicNumber")
internal fun SampleScreens.buildFormDemoSamplePage(): PageResponse =
    PageResponse(
        pageId = "form-demo",
        title = "Form Demo",
        toolbar =
            ToolbarSpec(
                title = text("Form Demo", "title.large"),
                navigationIcon =
                    IconAtom(
                        name = "arrow_back",
                        sizeDp = 22,
                        color = ColorTokenRef(token = "text.primary"),
                        contentDescription = "Navigate back",
                        onClick = backAction("back_from_form"),
                    ),
                bgColor = ColorTokenRef(token = "surface.primary"),
            ),
        snippets =
            listOf(
                buildFormDemoHeaderSnippet(),
                buildFormDemoBodySnippet(buildFormDemoSubmitAction()),
            ),
    )

@Suppress("MagicNumber")
private fun buildFormDemoSubmitAction(): ActionRef =
    ActionRef(
        id = "submit_workspace_form",
        type = ActionTypes.CALL_API,
        payload =
            buildJsonObject {
                put("endpoint", "/api/v1/actions/execute")
                put("method", "POST")
                put("body", buildJsonObject { put("form_id", "workspace_onboarding") })
                put(
                    "on_success",
                    SduiJson.encodeToJsonElement(
                        ActionRef.serializer(),
                        ActionRef(
                            id = "toast_form_saved",
                            type = ActionTypes.SHOW_TOAST,
                            payload =
                                buildJsonObject {
                                    put("message", "Workspace setup saved!")
                                    put("duration_ms", 2500)
                                    put("variant", "success")
                                },
                        ),
                    ),
                )
            },
        validations =
            listOf(
                ValidationRuleContract(
                    fieldId = "full_name",
                    rule = "required",
                    message = "Full name is required",
                ),
                ValidationRuleContract(
                    fieldId = "work_email",
                    rule = "required",
                    message = "Work email is required",
                ),
                ValidationRuleContract(
                    fieldId = "work_email",
                    rule = "regex",
                    value = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$",
                    message = "Enter a valid email address",
                ),
            ),
        secondaryActions =
            listOf(
                ActionRef(
                    id = "track_form_submit",
                    type = ActionTypes.TRACK_EVENT,
                    payload =
                        buildJsonObject {
                            put("event_name", "workspace_form_submitted")
                            put(
                                "properties",
                                buildJsonObject { put("form_id", "workspace_onboarding") },
                            )
                        },
                ),
            ),
    )

@Suppress("MagicNumber")
private fun SampleScreens.buildFormDemoHeaderSnippet(): SnippetEnvelope =
    SnippetEnvelope(
        snippetId = "form_header",
        snippetType = SnippetTypes.HEADER,
        data =
            encode(
                HeaderSnippetData(
                    title = text("Workspace Setup", "headline.small"),
                    subtitle =
                        text(
                            "All fields are validated declaratively on submit",
                            "body.medium",
                            "text.secondary",
                        ),
                    trailingTag =
                        TagAtom(
                            label = "VALIDATED",
                            bgColor = ColorTokenRef(token = "status.info_container"),
                        ),
                    layout =
                        LayoutConfig(
                            padding = SpacingValues(horizontal = 16, vertical = 12),
                            fillMaxWidth = true,
                        ),
                ),
            ),
    )

private fun SampleScreens.buildFormDemoBodySnippet(submitAct: ActionRef): SnippetEnvelope =
    SnippetEnvelope(
        snippetId = "form_workspace_setup",
        snippetType = SnippetTypes.FORM,
        data =
            encode(
                FormSnippetData(
                    formId = "workspace_onboarding",
                    title = text("Organization Details", "title.medium"),
                    description =
                        text(
                            "Provide your contact info to initialize your SDUI workspace.",
                            "body.small",
                            "text.secondary",
                        ),
                    fields =
                        listOf(
                            TextFieldAtom(
                                fieldId = "full_name",
                                label = "Full Name",
                                placeholder = "Alex Rivera",
                                required = true,
                                keyboardType = KeyboardKind.TEXT,
                            ),
                            TextFieldAtom(
                                fieldId = "work_email",
                                label = "Work Email",
                                placeholder = "alex@abeta.dev",
                                required = true,
                                regex = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$",
                                keyboardType = KeyboardKind.EMAIL,
                            ),
                            TextFieldAtom(
                                fieldId = "team_size",
                                label = "Team Size",
                                placeholder = "12",
                                helperText = "Optional number between 1 and 9999",
                                regex = "^\\d+$",
                                keyboardType = KeyboardKind.NUMBER,
                            ),
                        ),
                    submitButton =
                        ButtonAtom(
                            id = "btn_submit_form",
                            label = "Submit Setup",
                            variant = ButtonVariant.PRIMARY,
                            onClick = submitAct,
                        ),
                    cancelButton =
                        ButtonAtom(
                            id = "btn_cancel_form",
                            label = "Cancel",
                            variant = ButtonVariant.OUTLINE,
                            onClick = backAction("cancel_workspace_form"),
                        ),
                    layout = cardLayout,
                ),
            ),
    )
