package dev.abeta.kmp.sample

import dev.abeta.kmp.actions.handler.ActionOutcome
import dev.abeta.kmp.actions.handler.ApiCallExecutor
import dev.abeta.kmp.actions.model.ActionContext
import dev.abeta.kmp.actions.model.ActionTypes
import dev.abeta.kmp.actions.model.CallApiPayload
import dev.abeta.kmp.contracts.ActionRef
import dev.abeta.kmp.contracts.PageResponse
import dev.abeta.kmp.core.error.NotFoundError
import dev.abeta.kmp.core.result.AppResult
import dev.abeta.kmp.layout.repository.ScreenRepository
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Offline [ScreenRepository] serving rich built-in sample screens matching the Go BFF catalog.
 */
class OfflineScreenRepository : ScreenRepository {
    private val screens: Map<String, PageResponse> = SampleScreens.allPages()

    override suspend fun fetchScreen(screenId: String): AppResult<PageResponse> {
        val cleanId = screenId.trim().removePrefix("/")
        val page = screens[cleanId]
        return if (page != null) {
            AppResult.Success(page)
        } else {
            AppResult.Failure(
                NotFoundError(
                    resource = cleanId,
                    message = "Screen '$cleanId' not found in offline sample repository.",
                ),
            )
        }
    }
}

/**
 * Offline [ApiCallExecutor] simulating BFF API execution for sample-app actions.
 */
class OfflineApiCallExecutor : ApiCallExecutor {
    override suspend fun execute(
        payload: CallApiPayload,
        context: ActionContext,
    ): AppResult<ActionOutcome.ApiCallResult> {
        val followUps =
            if (payload.endpoint == "/api/v1/orders/express") expressOrderFollowUps() else emptyList()
        return AppResult.Success(
            ActionOutcome.ApiCallResult(
                endpoint = payload.endpoint,
                method = payload.method,
                isSuccess = true,
                statusCode = HTTP_OK,
                responseBody =
                    buildJsonObject {
                        put("status", "executed")
                        put("endpoint", payload.endpoint)
                        put("offline", true)
                    },
                followUpActions = followUps,
            ),
        )
    }

    private fun expressOrderFollowUps(): List<ActionRef> =
        listOf(
            ActionRef(
                id = "express_order_celebration",
                type = ActionTypes.TRIGGER_FEEDBACK,
                payload =
                    buildJsonObject {
                        put("effect", "celebration")
                        put("title", "Order Confirmed!")
                        put("subtitle", "Abeta Fresh rider arriving in 8 minutes")
                        put("duration_ms", EXPRESS_CELEBRATION_DURATION_MS)
                    },
            ),
            ActionRef(
                id = "express_order_toast",
                type = ActionTypes.SHOW_TOAST,
                payload =
                    buildJsonObject {
                        put("message", "Express order confirmed!")
                        put("duration_ms", EXPRESS_TOAST_DURATION_MS)
                        put("variant", "success")
                    },
            ),
        )

    private companion object {
        const val HTTP_OK = 200
        const val EXPRESS_CELEBRATION_DURATION_MS = 2400L
        const val EXPRESS_TOAST_DURATION_MS = 2500L
    }
}
