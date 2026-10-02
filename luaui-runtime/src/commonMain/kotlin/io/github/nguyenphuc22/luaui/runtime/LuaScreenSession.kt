package io.github.nguyenphuc22.luaui.runtime

import io.github.nguyenphuc22.luaui.core.LuaActionResponse
import io.github.nguyenphuc22.luaui.core.LuaCapability
import io.github.nguyenphuc22.luaui.core.LuaNodeId
import io.github.nguyenphuc22.luaui.core.LuaProtocolLimits
import io.github.nguyenphuc22.luaui.core.LuaScreenId
import io.github.nguyenphuc22.luaui.core.LuaScreenResponse

/**
 * One immutable, screen-session-scoped publication unit.
 *
 * It coordinates the existing server-owned [LuaScreenStore] with the client-owned
 * [LuaLocalStateStore]. A valid full response first becomes a validated [LuaNodeStore] through
 * `LuaScreenStore`, then local drafts reconcile against that candidate before this successor is
 * returned. A host can therefore publish both layers with one observable-state assignment.
 */
class LuaScreenSession private constructor(
    val screenStore: LuaScreenStore,
    val localStateStore: LuaLocalStateStore,
) {
    val screenId: LuaScreenId
        get() = screenStore.screenId

    /** Keeps the local snapshot while this same screen session waits for a replacement. */
    fun beginLoading(): LuaScreenSession = LuaScreenSession(
        screenStore = screenStore.beginLoading(),
        localStateStore = localStateStore,
    )

    /** Accepts a screen response and reconciles local state only for a valid `Ready` successor. */
    fun accept(response: LuaScreenResponse): LuaScreenSession = replaceScreenStore(
        screenStore.accept(response),
    )

    /** Accepts an action response and applies the same atomic replacement policy. */
    fun accept(response: LuaActionResponse): LuaScreenSession = replaceScreenStore(
        screenStore.accept(response),
    )

    /** Returns an immutable successor after one local TextField edit. */
    fun updateTextField(nodeId: LuaNodeId, value: String): LuaScreenSession {
        val ready = screenStore.state as? LuaScreenStoreState.Ready ?: return this
        val successorLocalState = localStateStore.updateTextField(
            nodeStore = ready.nodeStore,
            nodeId = nodeId,
            value = value,
        )
        return if (successorLocalState === localStateStore) {
            this
        } else {
            LuaScreenSession(screenStore = screenStore, localStateStore = successorLocalState)
        }
    }

    /**
     * Drops all client-owned local state without changing the server definition or lifecycle.
     * Creating a new [LuaScreenSession] has the same local-state effect at a navigation/session
     * boundary.
     */
    fun resetLocalState(): LuaScreenSession = LuaScreenSession(
        screenStore = screenStore,
        localStateStore = LuaLocalStateStore.empty(screenId),
    )

    private fun replaceScreenStore(successorScreenStore: LuaScreenStore): LuaScreenSession {
        val successorLocalState = when (val state = successorScreenStore.state) {
            is LuaScreenStoreState.Ready -> localStateStore.reconcile(state.nodeStore)
            LuaScreenStoreState.Loading,
            is LuaScreenStoreState.Incompatible,
            is LuaScreenStoreState.Failure,
            -> localStateStore
        }
        return LuaScreenSession(
            screenStore = successorScreenStore,
            localStateStore = successorLocalState,
        )
    }

    companion object {
        /** Creates a fresh local-state lifetime for one expected screen session. */
        fun loading(
            screenId: LuaScreenId,
            clientCapabilities: Set<LuaCapability>,
            limits: LuaProtocolLimits = LuaProtocolLimits(),
        ): LuaScreenSession = LuaScreenSession(
            screenStore = LuaScreenStore.loading(
                screenId = screenId,
                clientCapabilities = clientCapabilities,
                limits = limits,
            ),
            localStateStore = LuaLocalStateStore.empty(screenId),
        )
    }
}
