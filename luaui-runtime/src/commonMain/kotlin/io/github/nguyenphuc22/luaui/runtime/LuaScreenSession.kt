package io.github.nguyenphuc22.luaui.runtime

import io.github.nguyenphuc22.luaui.core.LuaAction
import io.github.nguyenphuc22.luaui.core.LuaActionRequest
import io.github.nguyenphuc22.luaui.core.LuaActionResponse
import io.github.nguyenphuc22.luaui.core.LuaButtonNode
import io.github.nguyenphuc22.luaui.core.LuaCapability
import io.github.nguyenphuc22.luaui.core.LuaCapabilities
import io.github.nguyenphuc22.luaui.core.LuaNodeId
import io.github.nguyenphuc22.luaui.core.LuaProtocolLimits
import io.github.nguyenphuc22.luaui.core.LuaProtocolValidator
import io.github.nguyenphuc22.luaui.core.LuaScreenId
import io.github.nguyenphuc22.luaui.core.LuaScreenResponse
import io.github.nguyenphuc22.luaui.core.LuaSubmissionId
import io.github.nguyenphuc22.luaui.core.LuaSubmittedTextField
import io.github.nguyenphuc22.luaui.core.LuaTextFieldAcknowledgement
import io.github.nguyenphuc22.luaui.core.LuaTextFieldNode
import io.github.nguyenphuc22.luaui.core.LuaTextFieldSubmission
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi

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
    private val clientCapabilities: Set<LuaCapability>,
    private val limits: LuaProtocolLimits,
    private val lifetime: LuaScreenSessionLifetime,
) {
    val screenId: LuaScreenId
        get() = screenStore.screenId

    /** Keeps the local snapshot while this same screen session waits for a replacement. */
    fun beginLoading(): LuaScreenSession = LuaScreenSession(
        screenStore = screenStore.beginLoading(),
        localStateStore = localStateStore,
        clientCapabilities = clientCapabilities,
        limits = limits,
        lifetime = lifetime,
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
            LuaScreenSession(
                screenStore = screenStore,
                localStateStore = successorLocalState,
                clientCapabilities = clientCapabilities,
                limits = limits,
                lifetime = lifetime,
            )
        }
    }

    /**
     * Snapshots a declared action from the current validated screen for transport dispatch.
     *
     * Legacy [LuaAction.Submit] remains identifiers-only. [LuaAction.SubmitTextFields] requires
     * a caller-supplied, unique [submissionId] and can snapshot only TextFields explicitly
     * allowlisted by that action. A `null` result is a safe no-op for stale nodes, incompatible
     * capabilities, or an invalid call site.
     */
    fun prepareAction(
        sourceNodeId: LuaNodeId,
        action: LuaAction,
        submissionId: LuaSubmissionId? = null,
    ): LuaPreparedAction? {
        val ready = screenStore.state as? LuaScreenStoreState.Ready ?: return null
        val source = ready.nodeStore.find(sourceNodeId)?.node as? LuaButtonNode ?: return null
        if (source.onClick != action) return null
        if (!clientCapabilities.containsAll(LuaCapabilities.requiredBy(action))) return null

        return when (action) {
            is LuaAction.Submit -> {
                if (submissionId != null) return null
                val request = LuaActionRequest(
                    screenId = screenId,
                    sourceNodeId = sourceNodeId,
                    actionId = action.actionId,
                    clientCapabilities = clientCapabilities,
                )
                if (LuaProtocolValidator.validateActionRequest(request, limits).isNotEmpty()) {
                    return null
                }
                LuaPreparedAction(
                    request = request,
                    lifetime = lifetime,
                    submission = null,
                    localSnapshots = emptyList(),
                )
            }

            is LuaAction.SubmitTextFields -> {
                val resolvedSubmissionId = submissionId ?: return null
                val localSnapshots = mutableListOf<LuaSubmittedTextFieldSnapshot>()
                action.textFieldNodeIds.forEach { fieldId ->
                    val field = ready.nodeStore.find(fieldId)?.node as? LuaTextFieldNode
                        ?: return null
                    val draft = localStateStore.draftFor(field)
                    localSnapshots += LuaSubmittedTextFieldSnapshot(
                        nodeId = fieldId,
                        value = draft.value,
                        draftRevision = draft.draftRevision,
                        slotGeneration = draft.slotGeneration,
                    )
                }
                val submission = LuaTextFieldSubmission(
                    submissionId = resolvedSubmissionId,
                    fields = ImmutableList(
                        localSnapshots.map { snapshot ->
                            LuaSubmittedTextField(
                                nodeId = snapshot.nodeId,
                                value = snapshot.value,
                                draftRevision = snapshot.draftRevision,
                            )
                        },
                    ),
                )
                val request = LuaActionRequest(
                    screenId = screenId,
                    sourceNodeId = sourceNodeId,
                    actionId = action.actionId,
                    clientCapabilities = clientCapabilities,
                    textFieldSubmission = submission,
                )
                if (LuaProtocolValidator.validateActionRequest(request, limits).isNotEmpty()) {
                    return null
                }
                LuaPreparedAction(
                    request = request,
                    lifetime = lifetime,
                    submission = submission,
                    localSnapshots = ImmutableList(localSnapshots),
                )
            }
        }
    }

    /**
     * Completes a request made by [prepareAction], applying an acknowledgement only when it is
     * tied to this exact session lifetime and immutable submission snapshot.
     *
     * This does not establish global response ordering. It only prevents a stale, replayed, or
     * cross-session acknowledgement from settling a draft that is no longer the one submitted.
     */
    fun complete(
        preparedAction: LuaPreparedAction,
        response: LuaActionResponse,
    ): LuaScreenSession {
        if (!preparedAction.belongsTo(lifetime)) return this
        if (response !is LuaActionResponse.AcknowledgedScreen) return accept(response)
        if (!preparedAction.matchesAcknowledgement(response.acknowledgement)) return this

        val successorScreenStore = screenStore.accept(response)
        val ready = successorScreenStore.state as? LuaScreenStoreState.Ready
            ?: return replaceScreenStore(successorScreenStore)
        // A prepared invocation is single-use after a valid matching acknowledgement. This keeps
        // a replay from applying a second replacement screen through the same correlation token.
        // The compare-and-set is deliberately after response validation: an invalid response must
        // not consume a prepared invocation that may still receive a valid response later.
        if (!preparedAction.tryConsumeAcknowledgement(response.acknowledgement)) return this
        val replacement = replaceScreenStore(
            successorScreenStore = successorScreenStore,
            preserveNewerSubmissionDrafts = preparedAction.localSnapshots(),
        )
        val settledLocalState = replacement.localStateStore.acknowledgeTextFieldSubmission(
            nodeStore = ready.nodeStore,
            snapshots = preparedAction.localSnapshots(),
        )
        return if (settledLocalState === replacement.localStateStore) {
            replacement
        } else {
            LuaScreenSession(
                screenStore = replacement.screenStore,
                localStateStore = settledLocalState,
                clientCapabilities = clientCapabilities,
                limits = limits,
                lifetime = lifetime,
            )
        }
    }

    /**
     * Drops all client-owned local state without changing the server definition or lifecycle.
     * Creating a new [LuaScreenSession] has the same local-state effect at a navigation/session
     * boundary.
     */
    fun resetLocalState(): LuaScreenSession {
        val emptyLocalState = LuaLocalStateStore.empty(screenId)
        val resetLocalState = (screenStore.state as? LuaScreenStoreState.Ready)
            ?.let { ready -> emptyLocalState.reconcile(ready.nodeStore) }
            ?: emptyLocalState
        return LuaScreenSession(
            screenStore = screenStore,
            // Materialize slots for a ready screen so a pristine post-reset submit receives a
            // fresh slot generation rather than the render-only fallback generation.
            localStateStore = resetLocalState,
            clientCapabilities = clientCapabilities,
            limits = limits,
            // Explicit reset is a local-state lifetime boundary, so old prepared actions cannot ack.
            lifetime = LuaScreenSessionLifetime(),
        )
    }

    private fun replaceScreenStore(
        successorScreenStore: LuaScreenStore,
        preserveNewerSubmissionDrafts: List<LuaSubmittedTextFieldSnapshot> = emptyList(),
    ): LuaScreenSession {
        val successorLocalState = when (val state = successorScreenStore.state) {
            is LuaScreenStoreState.Ready -> localStateStore.reconcile(
                nodeStore = state.nodeStore,
                preserveNewerSubmissionDrafts = preserveNewerSubmissionDrafts,
            )
            LuaScreenStoreState.Loading,
            is LuaScreenStoreState.Incompatible,
            is LuaScreenStoreState.Failure,
            -> localStateStore
        }
        return LuaScreenSession(
            screenStore = successorScreenStore,
            localStateStore = successorLocalState,
            clientCapabilities = clientCapabilities,
            limits = limits,
            lifetime = lifetime,
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
            clientCapabilities = ImmutableSet(clientCapabilities),
            limits = limits,
            lifetime = LuaScreenSessionLifetime(),
        )
    }
}

/** Internal identity token shared only by immutable successors of one screen-session lifetime. */
internal class LuaScreenSessionLifetime

/**
 * A transport-ready request paired with an opaque local snapshot for acknowledgement matching.
 *
 * Its default object representation deliberately does not print submitted values. Applications
 * should also treat [request] as sensitive input and avoid logging it verbatim.
 */
@OptIn(ExperimentalAtomicApi::class)
class LuaPreparedAction internal constructor(
    val request: LuaActionRequest,
    private val lifetime: LuaScreenSessionLifetime,
    private val submission: LuaTextFieldSubmission?,
    private val localSnapshots: List<LuaSubmittedTextFieldSnapshot>,
) {
    private val acknowledgementConsumed = AtomicBoolean(false)

    internal fun belongsTo(candidate: LuaScreenSessionLifetime): Boolean = lifetime === candidate

    internal fun matchesAcknowledgement(acknowledgement: LuaTextFieldAcknowledgement): Boolean {
        val expected = submission ?: return false
        return acknowledgement.submissionId == expected.submissionId &&
            acknowledgement.fields.size == expected.fields.size &&
            acknowledgement.fields.zip(expected.fields).all { (acknowledged, submitted) ->
                acknowledged.nodeId == submitted.nodeId &&
                    acknowledged.draftRevision == submitted.draftRevision
            }
    }

    /** Atomically claims one valid matching acknowledgement for this prepared invocation. */
    internal fun tryConsumeAcknowledgement(acknowledgement: LuaTextFieldAcknowledgement): Boolean {
        if (!matchesAcknowledgement(acknowledgement)) return false
        return acknowledgementConsumed.compareAndSet(expectedValue = false, newValue = true)
    }

    internal fun localSnapshots(): List<LuaSubmittedTextFieldSnapshot> = localSnapshots
}
