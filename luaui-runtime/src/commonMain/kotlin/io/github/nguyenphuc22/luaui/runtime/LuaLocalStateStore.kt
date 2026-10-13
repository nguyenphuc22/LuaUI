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
    private val nextSlotGeneration: Long,
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
        if (value == previous.value) return this
        val next = LuaTextFieldDraft(
            value = value,
            serverBaseline = previous.serverBaseline,
            isDirty = value != previous.serverBaseline,
            draftRevision = previous.draftRevision + 1,
            slotGeneration = previous.slotGeneration,
        )
        if (next == previous && nodeId in textFieldDrafts) return this

        return LuaLocalStateStore(
            screenId = screenId,
            textFieldDrafts = textFieldDrafts + (nodeId to next),
            nextSlotGeneration = nextSlotGeneration,
        )
    }

    /**
     * Creates a detached successor for a validated screen replacement in the same session.
     *
     * A TextField's fixed `TextFieldDraft/v1` slot is compatible only with the same node type.
     * Removed nodes are omitted from the new map, so a later reappearance starts fresh instead
     * of reviving a stale draft.
     */
    internal fun reconcile(
        nodeStore: LuaNodeStore,
        preserveNewerSubmissionDrafts: List<LuaSubmittedTextFieldSnapshot> = emptyList(),
    ): LuaLocalStateStore {
        if (nodeStore.screenId != screenId) return this

        val snapshotsByNodeId = preserveNewerSubmissionDrafts.associateBy(
            LuaSubmittedTextFieldSnapshot::nodeId,
        )
        val successorDrafts = linkedMapOf<LuaNodeId, LuaTextFieldDraft>()
        var successorNextSlotGeneration = nextSlotGeneration
        nodeStore.nodeIdsInPreorder.forEach { nodeId ->
            val node = nodeStore.find(nodeId)?.node as? LuaTextFieldNode ?: return@forEach
            val previous = textFieldDrafts[nodeId]
            successorDrafts[nodeId] = when {
                previous != null && previous.differsFrom(snapshotsByNodeId[nodeId]) -> {
                    // An acknowledgement replacement is allowed to refresh the server baseline,
                    // but it must not erase a post-submit edit merely because that edit happens
                    // to equal the old baseline and is therefore locally pristine.
                    LuaTextFieldDraft(
                        value = previous.value,
                        serverBaseline = node.initialValue,
                        isDirty = previous.value != node.initialValue,
                        draftRevision = previous.draftRevision,
                        slotGeneration = previous.slotGeneration,
                    )
                }

                previous?.isDirty == true -> {
                    // A server replacement can refresh the baseline but never silently overwrite
                    // or acknowledge a dirty local draft.
                    LuaTextFieldDraft(
                        value = previous.value,
                        serverBaseline = node.initialValue,
                        isDirty = true,
                        draftRevision = previous.draftRevision,
                        slotGeneration = previous.slotGeneration,
                    )
                }

                previous != null -> LuaTextFieldDraft.pristine(
                    initialValue = node.initialValue,
                    draftRevision = previous.draftRevision,
                    slotGeneration = previous.slotGeneration,
                )

                else -> LuaTextFieldDraft.pristine(
                    initialValue = node.initialValue,
                    slotGeneration = successorNextSlotGeneration++,
                )
            }
        }

        return if (
            successorDrafts == textFieldDrafts && successorNextSlotGeneration == nextSlotGeneration
        ) {
            this
        } else {
            LuaLocalStateStore(
                screenId = screenId,
                textFieldDrafts = successorDrafts,
                nextSlotGeneration = successorNextSlotGeneration,
            )
        }
    }

    /**
     * Settles a matching local submission after its full replacement screen has been validated.
     *
     * A stale response cannot overwrite a newer keystroke: the snapshot must retain the same
     * screen-local slot generation, draft revision, and value. Nodes removed or type-changed by
     * the replacement have no compatible local entry and are deliberately ignored.
     */
    internal fun acknowledgeTextFieldSubmission(
        nodeStore: LuaNodeStore,
        snapshots: List<LuaSubmittedTextFieldSnapshot>,
    ): LuaLocalStateStore {
        if (nodeStore.screenId != screenId || snapshots.isEmpty()) return this

        var successorDrafts = textFieldDrafts
        var changed = false
        snapshots.forEach { snapshot ->
            val node = nodeStore.find(snapshot.nodeId)?.node as? LuaTextFieldNode
                ?: return@forEach
            val current = successorDrafts[snapshot.nodeId] ?: return@forEach
            if (
                current.slotGeneration != snapshot.slotGeneration ||
                current.draftRevision != snapshot.draftRevision ||
                current.value != snapshot.value
            ) {
                return@forEach
            }

            val settled = LuaTextFieldDraft.pristine(
                initialValue = node.initialValue,
                draftRevision = current.draftRevision,
                slotGeneration = current.slotGeneration,
            )
            if (settled != current) {
                successorDrafts = successorDrafts + (snapshot.nodeId to settled)
                changed = true
            }
        }

        return if (changed) {
            LuaLocalStateStore(
                screenId = screenId,
                textFieldDrafts = successorDrafts,
                nextSlotGeneration = nextSlotGeneration,
            )
        } else {
            this
        }
    }

    companion object {
        /** Starts a new screen session without carrying drafts from a prior session. */
        fun empty(screenId: LuaScreenId): LuaLocalStateStore = LuaLocalStateStore(
            screenId = screenId,
            textFieldDrafts = emptyMap(),
            nextSlotGeneration = 1L,
        )
    }
}

/** A typed local draft and its latest server-provided baseline. */
class LuaTextFieldDraft internal constructor(
    val value: String,
    val serverBaseline: String,
    val isDirty: Boolean,
    /** Monotonic within the current compatible local slot; it changes on every value edit. */
    val draftRevision: Long,
    /** Internal incarnation token prevents a pruned/reintroduced ID from reviving an old ack. */
    internal val slotGeneration: Long,
) {
    override fun equals(other: Any?): Boolean = other is LuaTextFieldDraft &&
        value == other.value &&
        serverBaseline == other.serverBaseline &&
        isDirty == other.isDirty &&
        draftRevision == other.draftRevision &&
        slotGeneration == other.slotGeneration

    override fun hashCode(): Int {
        var result = value.hashCode()
        result = 31 * result + serverBaseline.hashCode()
        result = 31 * result + isDirty.hashCode()
        result = 31 * result + draftRevision.hashCode()
        result = 31 * result + slotGeneration.hashCode()
        return result
    }

    internal fun differsFrom(snapshot: LuaSubmittedTextFieldSnapshot?): Boolean = snapshot != null && (
        slotGeneration != snapshot.slotGeneration ||
            draftRevision != snapshot.draftRevision ||
            value != snapshot.value
        )

    internal companion object {
        fun pristine(
            initialValue: String,
            draftRevision: Long = 0L,
            slotGeneration: Long = 0L,
        ): LuaTextFieldDraft = LuaTextFieldDraft(
            value = initialValue,
            serverBaseline = initialValue,
            isDirty = false,
            draftRevision = draftRevision,
            slotGeneration = slotGeneration,
        )
    }
}

/** Internal, non-serializable local snapshot tied to one prepared TextField submission. */
internal class LuaSubmittedTextFieldSnapshot(
    val nodeId: LuaNodeId,
    val value: String,
    val draftRevision: Long,
    val slotGeneration: Long,
)
