package dev.abeta.kmp.layout.engine

import dev.abeta.kmp.actions.model.ActionTypes
import dev.abeta.kmp.analytics.AnalyticsCategory
import dev.abeta.kmp.analytics.AnalyticsEvent
import dev.abeta.kmp.analytics.AnalyticsTrackingPayload
import dev.abeta.kmp.contracts.ActionRef
import dev.abeta.kmp.contracts.PageResponse
import dev.abeta.kmp.contracts.SnippetEnvelope
import dev.abeta.kmp.contracts.TrackingContract

internal object LayoutAnalyticsHelper {
    const val EVENT_PAGE_OPEN = "page_open"
    const val EVENT_INTERACTION_CLICK = "interaction_click"

    fun buildPageOpenEvent(
        page: PageResponse,
        deepLinkParams: Map<String, String>,
    ): AnalyticsEvent {
        val properties =
            buildMap {
                page.tracking?.properties?.let(::putAll)
                putAll(deepLinkParams)
                put("page_id", page.pageId)
                page.title?.takeIf { it.isNotBlank() }?.let { put("title", it) }
                put("snippet_count", page.snippets.size.toString())
            }
        return AnalyticsEvent(
            name = EVENT_PAGE_OPEN,
            properties = properties,
            category = AnalyticsCategory.SCREEN,
            screenName = page.pageId,
        )
    }

    fun buildActionInteractionEvents(
        action: ActionRef,
        pageId: String?,
    ): List<AnalyticsEvent> {
        if (action.type == ActionTypes.TRACK_EVENT) {
            return emptyList()
        }
        val properties =
            buildMap {
                action.tracking?.properties?.let(::putAll)
                pageId?.let { put("page_id", it) }
                action.id?.takeIf { it.isNotBlank() }?.let { put("action_id", it) }
                put("action_type", action.type)
            }
        val events =
            mutableListOf(
                AnalyticsEvent(
                    name = EVENT_INTERACTION_CLICK,
                    properties = properties,
                    category = AnalyticsCategory.ACTION,
                    screenName = pageId,
                ),
            )
        val customEventName =
            action.tracking
                ?.eventName
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
        if (customEventName != null && customEventName != EVENT_INTERACTION_CLICK) {
            events.add(
                AnalyticsEvent(
                    name = customEventName,
                    properties = properties,
                    category = AnalyticsCategory.ACTION,
                    screenName = pageId,
                ),
            )
        }
        return events
    }

    fun buildSnippetImpressionPayload(
        snippet: SnippetEnvelope,
        tracking: TrackingContract,
        pageId: String?,
    ): AnalyticsTrackingPayload =
        AnalyticsTrackingPayload(
            eventName = tracking.eventName,
            properties =
                buildMap {
                    put("snippet_id", snippet.snippetId)
                    pageId?.let { put("page_id", it) }
                    putAll(tracking.properties)
                },
        )

    fun buildNodeImpressionPayload(
        nodeKey: String,
        tracking: TrackingContract,
        pageId: String?,
    ): AnalyticsTrackingPayload =
        AnalyticsTrackingPayload(
            eventName = tracking.eventName,
            properties =
                buildMap {
                    put("node_id", nodeKey)
                    pageId?.let { put("page_id", it) }
                    putAll(tracking.properties)
                },
        )
}
