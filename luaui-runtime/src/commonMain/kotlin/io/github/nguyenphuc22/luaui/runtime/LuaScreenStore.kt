package io.github.nguyenphuc22.luaui.runtime

import io.github.nguyenphuc22.luaui.core.LuaActionResponse
import io.github.nguyenphuc22.luaui.core.LuaCapability
import io.github.nguyenphuc22.luaui.core.LuaError
import io.github.nguyenphuc22.luaui.core.LuaErrorCode
import io.github.nguyenphuc22.luaui.core.LuaProtocolLimits
import io.github.nguyenphuc22.luaui.core.LuaScreen
import io.github.nguyenphuc22.luaui.core.LuaScreenId
import io.github.nguyenphuc22.luaui.core.LuaScreenResponse
import io.github.nguyenphuc22.luaui.core.LuaValidationIssue

/** The complete, immutable lifecycle state for one screen-scoped [LuaScreenStore]. */
sealed interface LuaScreenStoreState {
    /** A response has not been accepted for this screen yet. */
    data object Loading : LuaScreenStoreState

    /** A validated, indexed, immutable screen snapshot is ready to render. */
    data class Ready(
        val nodeStore: LuaNodeStore,
    ) : LuaScreenStoreState

    /** The server reported that this client cannot render the requested screen. */
    class Incompatible internal constructor(
        missingCapabilities: Set<LuaCapability>,
    ) : LuaScreenStoreState {
        /** A detached read-only snapshot of the server-reported missing capabilities. */
        val missingCapabilities: Set<LuaCapability> = ImmutableSet(missingCapabilities)
    }

    /** A safe error state that must not render an unvalidated or partial screen. */
    class Failure internal constructor(
        val error: LuaError,
        validationIssues: List<LuaValidationIssue> = emptyList(),
    ) : LuaScreenStoreState {
        /**
         * Client-side validation diagnostics for logging or debug UI.
         *
         * Production callers choose their own redaction and fallback rather than displaying
         * inbound payload details directly.
         */
        val validationIssues: List<LuaValidationIssue> = ImmutableList(validationIssues)
    }
}

/**
 * An immutable response-state machine for one [screenId].
 *
 * A call to [accept] creates a complete successor: it first builds a candidate [LuaNodeStore]
 * and only then returns `Ready`. The caller owns observable UI state and performs one state
 * assignment, for example `screenStore = screenStore.accept(response)`. This class deliberately
 * does not perform network I/O, expose Compose state, or define response ordering/retry policy.
 */
class LuaScreenStore private constructor(
    val screenId: LuaScreenId,
    private val clientCapabilities: Set<LuaCapability>,
    private val limits: LuaProtocolLimits,
    val state: LuaScreenStoreState,
) {
    /** Returns a new store in [LuaScreenStoreState.Loading] without changing this snapshot. */
    fun beginLoading(): LuaScreenStore = replaceState(LuaScreenStoreState.Loading)

    /** Accepts a full-screen response and returns a complete successor state. */
    fun accept(response: LuaScreenResponse): LuaScreenStore = replaceState(
        when (response) {
            is LuaScreenResponse.Screen -> stateFor(response.screen)
            is LuaScreenResponse.Incompatible -> LuaScreenStoreState.Incompatible(
                response.missingCapabilities,
            )

            is LuaScreenResponse.Failure -> LuaScreenStoreState.Failure(response.error)
        },
    )

    /** Accepts an action response using the same full-screen validation and replacement policy. */
    fun accept(response: LuaActionResponse): LuaScreenStore = replaceState(
        when (response) {
            is LuaActionResponse.Screen -> stateFor(response.screen)
            is LuaActionResponse.Failure -> LuaScreenStoreState.Failure(response.error)
        },
    )

    private fun stateFor(screen: LuaScreen): LuaScreenStoreState {
        if (screen.id != screenId) {
            return LuaScreenStoreState.Failure(
                LuaError(
                    code = LuaErrorCode.INVALID_SCREEN,
                    message = "Received a LuaUI screen for a different screen ID.",
                ),
            )
        }

        return when (
            val creation = LuaNodeStore.create(
                screen = screen,
                clientCapabilities = clientCapabilities,
                limits = limits,
            )
        ) {
            is LuaNodeStoreCreation.Ready -> LuaScreenStoreState.Ready(creation.store)
            is LuaNodeStoreCreation.Invalid -> LuaScreenStoreState.Failure(
                error = LuaError(
                    code = LuaErrorCode.INVALID_SCREEN,
                    message = "The client rejected an invalid LuaUI screen.",
                ),
                validationIssues = creation.issues,
            )
        }
    }

    private fun replaceState(nextState: LuaScreenStoreState): LuaScreenStore = LuaScreenStore(
        screenId = screenId,
        clientCapabilities = clientCapabilities,
        limits = limits,
        state = nextState,
    )

    companion object {
        /**
         * Creates the initial loading state for one expected screen response.
         *
         * [clientCapabilities] is snapshotted so future mutation of a caller-owned collection
         * cannot change validation of later responses.
         */
        fun loading(
            screenId: LuaScreenId,
            clientCapabilities: Set<LuaCapability>,
            limits: LuaProtocolLimits = LuaProtocolLimits(),
        ): LuaScreenStore = LuaScreenStore(
            screenId = screenId,
            clientCapabilities = ImmutableSet(clientCapabilities),
            limits = limits,
            state = LuaScreenStoreState.Loading,
        )
    }
}
