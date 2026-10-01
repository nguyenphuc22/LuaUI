package io.github.nguyenphuc22.luaui.sample

import io.github.nguyenphuc22.luaui.core.LuaActionId
import io.github.nguyenphuc22.luaui.core.LuaActionRequest
import io.github.nguyenphuc22.luaui.core.LuaActionResponse
import io.github.nguyenphuc22.luaui.core.LuaCapabilities
import io.github.nguyenphuc22.luaui.core.LuaError
import io.github.nguyenphuc22.luaui.core.LuaErrorCode
import io.github.nguyenphuc22.luaui.core.LuaNodeId
import io.github.nguyenphuc22.luaui.core.LuaProtocolValidator
import io.github.nguyenphuc22.luaui.core.LuaScreen
import io.github.nguyenphuc22.luaui.core.LuaScreenId
import io.github.nguyenphuc22.luaui.core.LuaScreenRequest
import io.github.nguyenphuc22.luaui.core.LuaScreenResponse
import io.github.nguyenphuc22.luaui.core.LuaScreenValidationResult
import io.github.nguyenphuc22.luaui.server.LuaScreenController
import io.github.nguyenphuc22.luaui.server.luaScreen
import io.github.nguyenphuc22.luaui.server.submit
import java.util.concurrent.atomic.AtomicInteger

class DashboardController : LuaScreenController {
    private val refreshCount = AtomicInteger(0)

    override suspend fun load(request: LuaScreenRequest): LuaScreenResponse {
        val requestIssues = LuaProtocolValidator.validateScreenRequest(request)
        if (requestIssues.isNotEmpty()) {
            return LuaScreenResponse.Failure(
                LuaError(
                    code = LuaErrorCode.INVALID_REQUEST,
                    message = "The LuaUI screen request is invalid.",
                ),
            )
        }
        if (request.screenId != dashboardScreenId) {
            return LuaScreenResponse.Failure(notFound(request.screenId))
        }

        return responseFor(dashboard(), request.clientCapabilities)
    }

    override suspend fun dispatch(request: LuaActionRequest): LuaActionResponse {
        val requestIssues = LuaProtocolValidator.validateActionRequest(request)
        if (requestIssues.isNotEmpty()) {
            return LuaActionResponse.Failure(
                LuaError(
                    code = LuaErrorCode.INVALID_REQUEST,
                    message = "The LuaUI action request is invalid.",
                ),
            )
        }
        if (request.screenId != dashboardScreenId) {
            return LuaActionResponse.Failure(notFound(request.screenId))
        }
        if (request.sourceNodeId != refreshNodeId || request.actionId != refreshActionId) {
            return LuaActionResponse.Failure(
                LuaError(
                    code = LuaErrorCode.UNAUTHORIZED_ACTION,
                    message = "The requested action is not authorized for this screen.",
                ),
            )
        }

        return actionResponseFor(
            screen = dashboard(refreshCount.incrementAndGet()),
            clientCapabilities = request.clientCapabilities,
        )
    }

    private fun dashboard(refresh: Int = refreshCount.get()): LuaScreen = luaScreen(dashboardScreenId.value) {
        column("dashboard.root") {
            text("dashboard.title", "LuaUI Dashboard")
            text("dashboard.price", "${1_500_000L + refresh * 1_000L} ₫")
            button(
                id = refreshNodeId.value,
                label = "Refresh",
                action = submit(refreshActionId.value),
            )
        }
    }

    private fun notFound(screenId: LuaScreenId): LuaError = LuaError(
        code = LuaErrorCode.NOT_FOUND,
        message = "Screen '${screenId.value}' was not found.",
    )

    private fun responseFor(
        screen: LuaScreen,
        clientCapabilities: Set<io.github.nguyenphuc22.luaui.core.LuaCapability>,
    ): LuaScreenResponse {
        val missing = LuaProtocolValidator.missingCapabilities(screen, clientCapabilities)
        if (missing.isNotEmpty()) return LuaScreenResponse.Incompatible(missing)

        return when (LuaProtocolValidator.validateScreen(screen, clientCapabilities)) {
            is LuaScreenValidationResult.Valid -> LuaScreenResponse.Screen(screen)
            is LuaScreenValidationResult.Invalid -> LuaScreenResponse.Failure(
                LuaError(
                    code = LuaErrorCode.INVALID_SCREEN,
                    message = "The server produced an invalid LuaUI screen.",
                ),
            )
        }
    }

    private fun actionResponseFor(
        screen: LuaScreen,
        clientCapabilities: Set<io.github.nguyenphuc22.luaui.core.LuaCapability>,
    ): LuaActionResponse = when (val response = responseFor(screen, clientCapabilities)) {
        is LuaScreenResponse.Screen -> LuaActionResponse.Screen(response.screen)
        is LuaScreenResponse.Incompatible -> LuaActionResponse.Failure(
            LuaError(
                code = LuaErrorCode.INCOMPATIBLE_CLIENT,
                message = "The client does not support the refreshed screen.",
            ),
        )

        is LuaScreenResponse.Failure -> LuaActionResponse.Failure(response.error)
    }

    private companion object {
        val dashboardScreenId = LuaScreenId("dashboard")
        val refreshNodeId = LuaNodeId("dashboard.refresh")
        val refreshActionId = LuaActionId("dashboard.refresh")
    }
}
