package io.github.nguyenphuc22.luaui.sample

import io.github.nguyenphuc22.luaui.core.LuaActionId
import io.github.nguyenphuc22.luaui.core.LuaActionRequest
import io.github.nguyenphuc22.luaui.core.LuaActionResponse
import io.github.nguyenphuc22.luaui.core.LuaAcknowledgedTextField
import io.github.nguyenphuc22.luaui.core.LuaCapabilities
import io.github.nguyenphuc22.luaui.core.LuaCapability
import io.github.nguyenphuc22.luaui.core.LuaError
import io.github.nguyenphuc22.luaui.core.LuaErrorCode
import io.github.nguyenphuc22.luaui.core.LuaNodeId
import io.github.nguyenphuc22.luaui.core.LuaProtocolValidator
import io.github.nguyenphuc22.luaui.core.LuaScreen
import io.github.nguyenphuc22.luaui.core.LuaScreenId
import io.github.nguyenphuc22.luaui.core.LuaScreenRequest
import io.github.nguyenphuc22.luaui.core.LuaScreenResponse
import io.github.nguyenphuc22.luaui.core.LuaScreenValidationResult
import io.github.nguyenphuc22.luaui.core.LuaTextFieldAcknowledgement
import io.github.nguyenphuc22.luaui.server.LuaScreenController
import io.github.nguyenphuc22.luaui.server.luaScreen
import io.github.nguyenphuc22.luaui.server.submit
import io.github.nguyenphuc22.luaui.server.submitTextFields
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class DashboardController : LuaScreenController {
    private val refreshCount = AtomicInteger(0)
    private val appliedFilter = AtomicReference("")

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
        if (!request.clientCapabilities.containsAll(dashboardCapabilities)) {
            return LuaActionResponse.Failure(
                LuaError(
                    code = LuaErrorCode.INCOMPATIBLE_CLIENT,
                    message = "The client does not support the current dashboard.",
                ),
            )
        }
        return when {
            request.sourceNodeId == refreshNodeId && request.actionId == refreshActionId -> {
                if (request.textFieldSubmission != null) {
                    invalidRequest("Legacy Submit must not carry a TextField submission.")
                } else {
                    actionResponseFor(
                        screen = dashboard(refresh = refreshCount.incrementAndGet()),
                        clientCapabilities = request.clientCapabilities,
                    )
                }
            }

            request.sourceNodeId == applyFilterNodeId && request.actionId == applyFilterActionId -> {
                dispatchFilterSubmission(request)
            }

            else -> LuaActionResponse.Failure(
                LuaError(
                    code = LuaErrorCode.UNAUTHORIZED_ACTION,
                    message = "The requested action is not authorized for this screen.",
                ),
            )
        }
    }

    private fun dispatchFilterSubmission(request: LuaActionRequest): LuaActionResponse {
        val submission = request.textFieldSubmission
            ?: return invalidRequest("SubmitTextFields requires a typed TextField submission.")
        val submittedFilter = submission.fields.singleOrNull()
            ?: return invalidRequest("The filter action requires exactly one submitted TextField.")
        if (submittedFilter.nodeId != filterNodeId) {
            return invalidRequest("The filter action received an unexpected TextField node.")
        }

        // Server business logic owns normalization. The client will receive this canonical value
        // as the replacement TextField baseline only after the acknowledgement matches.
        val normalizedFilter = submittedFilter.value.trim()
        appliedFilter.set(normalizedFilter)
        return actionResponseFor(
            screen = dashboard(filter = normalizedFilter),
            clientCapabilities = request.clientCapabilities,
            acknowledgement = LuaTextFieldAcknowledgement(
                submissionId = submission.submissionId,
                fields = listOf(
                    LuaAcknowledgedTextField(
                        nodeId = filterNodeId,
                        draftRevision = submittedFilter.draftRevision,
                    ),
                ),
            ),
        )
    }

    private fun dashboard(
        refresh: Int = refreshCount.get(),
        filter: String = appliedFilter.get(),
    ): LuaScreen = luaScreen(dashboardScreenId.value) {
        column("dashboard.root") {
            text("dashboard.title", "LuaUI Dashboard")
            text("dashboard.price", "${1_500_000L + refresh * 1_000L} ₫")
            text("dashboard.applied_filter", "Applied filter: ${filter.ifBlank { "all products" }}")
            textField(
                id = filterNodeId.value,
                label = "Product filter",
                initialValue = filter,
            )
            button(
                id = applyFilterNodeId.value,
                label = "Apply filter",
                action = submitTextFields(
                    applyFilterActionId.value,
                    filterNodeId.value,
                ),
            )
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
        clientCapabilities: Set<LuaCapability>,
        acknowledgement: LuaTextFieldAcknowledgement? = null,
    ): LuaActionResponse = when (val response = responseFor(screen, clientCapabilities)) {
        is LuaScreenResponse.Screen -> acknowledgement?.let { accepted ->
            LuaActionResponse.AcknowledgedScreen(
                screen = response.screen,
                acknowledgement = accepted,
            )
        } ?: LuaActionResponse.Screen(response.screen)
        is LuaScreenResponse.Incompatible -> LuaActionResponse.Failure(
            LuaError(
                code = LuaErrorCode.INCOMPATIBLE_CLIENT,
                message = "The client does not support the refreshed screen.",
            ),
        )

        is LuaScreenResponse.Failure -> LuaActionResponse.Failure(response.error)
    }

    private fun invalidRequest(message: String): LuaActionResponse.Failure = LuaActionResponse.Failure(
        LuaError(
            code = LuaErrorCode.INVALID_REQUEST,
            message = message,
        ),
    )

    private companion object {
        val dashboardScreenId = LuaScreenId("dashboard")
        val filterNodeId = LuaNodeId("dashboard.filter")
        val applyFilterNodeId = LuaNodeId("dashboard.apply_filter")
        val applyFilterActionId = LuaActionId("dashboard.apply_filter")
        val refreshNodeId = LuaNodeId("dashboard.refresh")
        val refreshActionId = LuaActionId("dashboard.refresh")
        val formCapabilities = setOf(LuaCapabilities.textField, LuaCapabilities.submitTextFields)
        val dashboardCapabilities = LuaCapabilities.foundation + formCapabilities
    }
}
