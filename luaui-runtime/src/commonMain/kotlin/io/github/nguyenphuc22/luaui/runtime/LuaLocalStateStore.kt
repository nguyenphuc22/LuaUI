package io.github.nguyenphuc22.luaui.runtime

import io.github.nguyenphuc22.luaui.core.LuaNodeId
import io.github.nguyenphuc22.luaui.core.LuaScreenId
import io.github.nguyenphuc22.luaui.core.LuaTextFieldNode

/**
 * The client-owned, memory-only local state for one [LuaScreenSession].
 *
 * This first vertical slice owns exactly one typed slot: a `TextField` draft. It deliberately
 * has no generic state map, Compose object, persistence behavior, or server-driven mutation API.
 */
class LuaLocalStateStore private constructor(
    val screenId: LuaScreenId,
    private val textFieldDrafts: Map<LuaNodeId, LuaTextFieldDraft>,
) {
    /**
     * Returns the current draft for a compatible [node].
     *
     * A freshly rendered field is pristine at its server-provided initial value. Reconciliation
     * materializes every field in the current immutable snapshot, but this fallback also keeps a
     * newly created empty session safe to render.
     */
    fun draftFor(node: LuaTextFieldNode): LuaTextFieldDraft =
        textFieldDrafts[node.id] ?: LuaTextFieldDraft.pristine(node.initialValue)

    /**
     * Applies a user keystroke only when [nodeId] resolves to a `TextField` in this session's
     * current immutable [nodeStore]. Unknown, wrong-screen, or non-TextField IDs are safe no-ops.
     */
    internal fun updateTextField(
        nodeStore: LuaNodeStore,
        nodeId: LuaNodeId,
        value: String,
    ): LuaLocalStateStore {
        if (nodeStore.screenId != screenId) return this
        val node = nodeStore.find(nodeId)?.node as? LuaTextFieldNode ?: return this
        val previous = draftFor(node)
        val next = LuaTextFieldDraft(
            value = value,
            serverBaseline = previous.serverBaseline,
            isDirty = value != previous.serverBaseline,
        )
        if (next == previous && nodeId in textFieldDrafts) return this

        return LuaLocalStateStore(
            screenId = screenId,
            textFieldDrafts = textFieldDrafts + (nodeId to next),
        )
    }

    /**
     * Creates a detached successor for a validated screen replacement in the same session.
     *
     * A TextField's fixed `TextFieldDraft/v1` slot is compatible only with the same node type.
     * Removed nodes are omitted from the new map, so a later reappearance starts fresh instead
     * of reviving a stale draft.
     */
    internal fun reconcile(nodeStore: LuaNodeStore): LuaLocalStateStore {
        if (nodeStore.screenId != screenId) return this

        val successorDrafts = linkedMapOf<LuaNodeId, LuaTextFieldDraft>()
        nodeStore.nodeIdsInPreorder.forEach { nodeId ->
            val node = nodeStore.find(nodeId)?.node as? LuaTextFieldNode ?: return@forEach
            val previous = textFieldDrafts[nodeId]
            successorDrafts[nodeId] = if (previous?.isDirty == true) {
                // A server replacement can refresh the baseline but never silently overwrite or
                // acknowledge a dirty local draft.
                LuaTextFieldDraft(
                    value = previous.value,
                    serverBaseline = node.initialValue,
                    isDirty = true,
                )
            } else {
                LuaTextFieldDraft.pristine(node.initialValue)
            }
        }

        return if (successorDrafts == textFieldDrafts) {
            this
        } else {
            LuaLocalStateStore(screenId = screenId, textFieldDrafts = successorDrafts)
        }
    }

    companion object {
        /** Starts a new screen session without carrying drafts from a prior session. */
        fun empty(screenId: LuaScreenId): LuaLocalStateStore = LuaLocalStateStore(
            screenId = screenId,
            textFieldDrafts = emptyMap(),
        )
    }
}

/** A typed local draft and its latest server-provided baseline. */
class LuaTextFieldDraft internal constructor(
    val value: String,
    val serverBaseline: String,
    val isDirty: Boolean,
) {
    override fun equals(other: Any?): Boolean = other is LuaTextFieldDraft &&
        value == other.value &&
        serverBaseline == other.serverBaseline &&
        isDirty == other.isDirty

    override fun hashCode(): Int {
        var result = value.hashCode()
        result = 31 * result + serverBaseline.hashCode()
        result = 31 * result + isDirty.hashCode()
        return result
    }

    internal companion object {
        fun pristine(initialValue: String): LuaTextFieldDraft = LuaTextFieldDraft(
            value = initialValue,
            serverBaseline = initialValue,
            isDirty = false,
        )
    }
}
