package dev.abeta.kmp.sample

import dev.abeta.kmp.actions.model.ActionTypes
import dev.abeta.kmp.contracts.ActionRef
import dev.abeta.kmp.contracts.ActionRowSnippetData
import dev.abeta.kmp.contracts.ColorTokenRef
import dev.abeta.kmp.contracts.HeaderSnippetData
import dev.abeta.kmp.contracts.IconAtom
import dev.abeta.kmp.contracts.PageResponse
import dev.abeta.kmp.contracts.SduiJson
import dev.abeta.kmp.contracts.SnippetEnvelope
import dev.abeta.kmp.contracts.SnippetTypes
import dev.abeta.kmp.contracts.TagAtom
import dev.abeta.kmp.contracts.ToolbarSpec
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Suppress("MagicNumber")
internal fun SampleScreens.buildProfileSamplePage(): PageResponse =
    PageResponse(
        pageId = "profile",
        title = "Profile",
        toolbar =
            ToolbarSpec(
                title = text("Profile", "title.large"),
                navigationIcon =
                    IconAtom(
                        name = "arrow_back",
                        sizeDp = 22,
                        color = ColorTokenRef(token = "text.primary"),
                        contentDescription = "Navigate back",
                        onClick = backAction("back_from_profile"),
                    ),
                bgColor = ColorTokenRef(token = "surface.primary"),
            ),
        snippets =
            listOf(
                buildProfileHeaderSnippet(),
                buildProfileWorkspaceRowSnippet(buildProfileCopyWorkspaceAction()),
                buildProfileSyncRowSnippet(buildProfileSyncBatchAction()),
                buildProfileSignOutRowSnippet(buildProfileSignOutDialogAction()),
            ),
    )

private fun buildProfileCopyWorkspaceAction(): ActionRef =
    ActionRef(
        id = "copy_workspace_id",
        type = ActionTypes.COPY_TO_CLIPBOARD,
        payload =
            buildJsonObject {
                put("text", "ws_abeta_01")
                put("label", "Workspace ID")
            },
        secondaryActions =
            listOf(
                ActionRef(
                    id = "toast_ws_copied",
                    type = ActionTypes.SHOW_TOAST,
                    payload =
                        buildJsonObject {
                            put("message", "Workspace ID copied")
                            put("variant", "success")
                        },
                ),
            ),
    )

@Suppress("MagicNumber")
private fun buildProfileSyncBatchAction(): ActionRef =
    ActionRef(
        id = "batch_sync_profile",
        type = ActionTypes.BATCH,
        payload =
            buildJsonObject {
                put("stop_on_error", true)
                put(
                    "actions",
                    buildJsonArray {
                        add(
                            SduiJson.encodeToJsonElement(
                                ActionRef.serializer(),
                                ActionRef(
                                    id = "save_last_sync",
                                    type = ActionTypes.SAVE_STATE,
                                    payload =
                                        buildJsonObject {
                                            put("key", "profile_synced")
                                            put("value", true)
                                            put("scope", "session")
                                        },
                                ),
                            ),
                        )
                        add(
                            SduiJson.encodeToJsonElement(
                                ActionRef.serializer(),
                                ActionRef(
                                    id = "toast_profile_synced",
                                    type = ActionTypes.SHOW_TOAST,
                                    payload =
                                        buildJsonObject {
                                            put("message", "Profile preferences synced")
                                            put("duration_ms", 2000)
                                            put("variant", "success")
                                        },
                                ),
                            ),
                        )
                    },
                )
            },
    )

private fun buildProfileSignOutDialogAction(): ActionRef {
    val confirmSignOut =
        ActionRef(
            id = "confirm_signout",
            type = ActionTypes.BATCH,
            payload =
                buildJsonObject {
                    put(
                        "actions",
                        buildJsonArray {
                            add(
                                SduiJson.encodeToJsonElement(
                                    ActionRef.serializer(),
                                    ActionRef(
                                        id = "dismiss_signout",
                                        type = ActionTypes.DISMISS_OVERLAY,
                                        payload = buildJsonObject { put("overlay_id", "signout_confirm") },
                                    ),
                                ),
                            )
                            add(
                                SduiJson.encodeToJsonElement(
                                    ActionRef.serializer(),
                                    ActionRef(
                                        id = "nav_home_after_signout",
                                        type = ActionTypes.NAVIGATE,
                                        payload =
                                            buildJsonObject {
                                                put("route", "/screens/home")
                                                put("clear_back_stack", true)
                                            },
                                    ),
                                ),
                            )
                        },
                    )
                },
        )
    val cancelSignOut =
        ActionRef(
            id = "cancel_signout",
            type = ActionTypes.DISMISS_OVERLAY,
            payload = buildJsonObject { put("overlay_id", "signout_confirm") },
        )
    return ActionRef(
        id = "open_signout_dialog",
        type = ActionTypes.OPEN_DIALOG,
        payload =
            buildJsonObject {
                put("dialog_id", "signout_confirm")
                put("title", "Sign out of workspace?")
                put("message", "Your local session state will be cleared.")
                put("dismissible", true)
                put("positive_action", SduiJson.encodeToJsonElement(ActionRef.serializer(), confirmSignOut))
                put("negative_action", SduiJson.encodeToJsonElement(ActionRef.serializer(), cancelSignOut))
            },
    )
}

@Suppress("MagicNumber")
private fun SampleScreens.buildProfileHeaderSnippet(): SnippetEnvelope =
    SnippetEnvelope(
        snippetId = "profile_header",
        snippetType = SnippetTypes.HEADER,
        data =
            encode(
                HeaderSnippetData(
                    title = text("Alex Rivera", "headline.small"),
                    subtitle = text("Principal Mobile Architect", "body.medium", "text.secondary"),
                    leadingIcon =
                        IconAtom(
                            name = "person",
                            sizeDp = 32,
                            color = ColorTokenRef(token = "brand.primary"),
                        ),
                    trailingTag =
                        TagAtom(
                            label = "VERIFIED",
                            bgColor = ColorTokenRef(token = "status.success_container"),
                        ),
                    layout = cardLayout,
                ),
            ),
    )

@Suppress("MagicNumber")
private fun SampleScreens.buildProfileWorkspaceRowSnippet(copyWorkspace: ActionRef): SnippetEnvelope =
    SnippetEnvelope(
        snippetId = "profile_row_workspace",
        snippetType = SnippetTypes.ACTION_ROW,
        data =
            encode(
                ActionRowSnippetData(
                    title = text("Workspace ID", "title.small"),
                    subtitle = text("ws_abeta_01", "body.small", "text.secondary"),
                    leadingIcon =
                        IconAtom(
                            name = "info",
                            sizeDp = 22,
                            color = ColorTokenRef(token = "brand.primary"),
                        ),
                    trailingIcon =
                        IconAtom(
                            name = "chevron_right",
                            sizeDp = 20,
                            color = ColorTokenRef(token = "text.tertiary"),
                        ),
                    onClick = copyWorkspace,
                    layout = cardLayout,
                ),
            ),
    )

@Suppress("MagicNumber")
private fun SampleScreens.buildProfileSyncRowSnippet(syncBatch: ActionRef): SnippetEnvelope =
    SnippetEnvelope(
        snippetId = "profile_row_sync",
        snippetType = SnippetTypes.ACTION_ROW,
        data =
            encode(
                ActionRowSnippetData(
                    title = text("Force Sync State", "title.small"),
                    subtitle =
                        text(
                            "Persist session preferences and show confirmation",
                            "body.small",
                            "text.secondary",
                        ),
                    leadingIcon =
                        IconAtom(
                            name = "refresh",
                            sizeDp = 22,
                            color = ColorTokenRef(token = "status.info"),
                        ),
                    trailingIcon =
                        IconAtom(
                            name = "chevron_right",
                            sizeDp = 20,
                            color = ColorTokenRef(token = "text.tertiary"),
                        ),
                    onClick = syncBatch,
                    layout = cardLayout,
                ),
            ),
    )

@Suppress("MagicNumber")
private fun SampleScreens.buildProfileSignOutRowSnippet(signOutDialog: ActionRef): SnippetEnvelope =
    SnippetEnvelope(
        snippetId = "profile_row_signout",
        snippetType = SnippetTypes.ACTION_ROW,
        data =
            encode(
                ActionRowSnippetData(
                    title = text("Sign Out", "title.small", "text.error"),
                    leadingIcon =
                        IconAtom(
                            name = "close",
                            sizeDp = 22,
                            color = ColorTokenRef(token = "status.error"),
                        ),
                    onClick = signOutDialog,
                    layout = cardLayout,
                ),
            ),
    )
