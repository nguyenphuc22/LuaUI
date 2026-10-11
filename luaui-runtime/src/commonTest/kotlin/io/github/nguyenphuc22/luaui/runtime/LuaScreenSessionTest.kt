package io.github.nguyenphuc22.luaui.runtime

import io.github.nguyenphuc22.luaui.core.LuaActionResponse
import io.github.nguyenphuc22.luaui.core.LuaAcknowledgedTextField
import io.github.nguyenphuc22.luaui.core.LuaAction
import io.github.nguyenphuc22.luaui.core.LuaActionId
import io.github.nguyenphuc22.luaui.core.LuaButtonNode
import io.github.nguyenphuc22.luaui.core.LuaCapabilities
import io.github.nguyenphuc22.luaui.core.LuaColumnNode
import io.github.nguyenphuc22.luaui.core.LuaError
import io.github.nguyenphuc22.luaui.core.LuaErrorCode
import io.github.nguyenphuc22.luaui.core.LuaNode
import io.github.nguyenphuc22.luaui.core.LuaNodeId
import io.github.nguyenphuc22.luaui.core.LuaProtocolLimits
import io.github.nguyenphuc22.luaui.core.LuaScreen
import io.github.nguyenphuc22.luaui.core.LuaScreenId
import io.github.nguyenphuc22.luaui.core.LuaScreenResponse
import io.github.nguyenphuc22.luaui.core.LuaSubmissionId
import io.github.nguyenphuc22.luaui.core.LuaSubmittedTextField
import io.github.nguyenphuc22.luaui.core.LuaTextFieldAcknowledgement
import io.github.nguyenphuc22.luaui.core.LuaTextFieldNode
import io.github.nguyenphuc22.luaui.core.LuaTextNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class LuaScreenSessionTest {
    @Test
    fun `dirty TextField draft survives a compatible replacement while its baseline updates`() {
        val initial = readySession(screen = dashboard(initialValue = "all"))
        val edited = initial.updateTextField(filter(initial).id, "rice")

        val replacement = edited.accept(
            LuaActionResponse.Screen(dashboard(initialValue = "new server default")),
        )
        val replacementFilter = filter(replacement)
        val draft = replacement.localStateStore.draftFor(replacementFilter)

        assertNotSame(edited, replacement)
        assertEquals("rice", draft.value)
        assertEquals("new server default", draft.serverBaseline)
        assertTrue(draft.isDirty)
    }

    @Test
    fun `pristine TextField draft is reseeded by a compatible replacement`() {
        val initial = readySession(screen = dashboard(initialValue = "all"))

        val replacement = initial.accept(
            LuaScreenResponse.Screen(dashboard(initialValue = "seasonal")),
        )
        val draft = replacement.localStateStore.draftFor(filter(replacement))

        assertEquals("seasonal", draft.value)
        assertEquals("seasonal", draft.serverBaseline)
        assertTrue(!draft.isDirty)
    }

    @Test
    fun `moving a compatible TextField in the tree retains its draft`() {
        val initial = readySession(screen = dashboard(initialValue = "all"))
        val edited = initial.updateTextField(filter(initial).id, "rice")

        val moved = edited.accept(
            LuaScreenResponse.Screen(nestedDashboard(initialValue = "seasonal")),
        )
        val draft = moved.localStateStore.draftFor(filter(moved))

        assertEquals("rice", draft.value)
        assertEquals("seasonal", draft.serverBaseline)
        assertTrue(draft.isDirty)
    }

    @Test
    fun `removed or incompatible nodes prune a draft and never revive it`() {
        val initial = readySession(screen = dashboard(initialValue = "all"))
        val edited = initial.updateTextField(filter(initial).id, "rice")

        val removed = edited.accept(
            LuaScreenResponse.Screen(dashboard(field = null)),
        )
        val typeChanged = edited.accept(
            LuaScreenResponse.Screen(
                dashboard(field = LuaTextNode(LuaNodeId("dashboard.filter"), "Static filter")),
            ),
        )
        assertIs<LuaScreenStoreState.Ready>(removed.screenStore.state)
        assertIs<LuaScreenStoreState.Ready>(typeChanged.screenStore.state)

        val reintroducedAfterRemoval = removed.accept(
            LuaScreenResponse.Screen(dashboard(initialValue = "fresh")),
        )
        val reintroducedAfterTypeChange = typeChanged.accept(
            LuaScreenResponse.Screen(dashboard(initialValue = "fresh again")),
        )
        val removedDraft = reintroducedAfterRemoval.localStateStore
            .draftFor(filter(reintroducedAfterRemoval))
        val typeChangedDraft = reintroducedAfterTypeChange.localStateStore
            .draftFor(filter(reintroducedAfterTypeChange))

        assertEquals("fresh", removedDraft.value)
        assertEquals("fresh", removedDraft.serverBaseline)
        assertTrue(!removedDraft.isDirty)
        assertEquals("fresh again", typeChangedDraft.value)
        assertEquals("fresh again", typeChangedDraft.serverBaseline)
        assertTrue(!typeChangedDraft.isDirty)
    }

    @Test
    fun `invalid and cross screen replacements retain the previous local snapshot without reconciling`() {
        val initial = readySession(screen = dashboard(initialValue = "all"))
        val edited = initial.updateTextField(filter(initial).id, "rice")

        val invalid = edited.accept(LuaScreenResponse.Screen(duplicateNodeScreen()))
        val wrongScreen = edited.accept(
            LuaScreenResponse.Screen(dashboard(screenId = "inventory", initialValue = "other")),
        )

        assertIs<LuaScreenStoreState.Failure>(invalid.screenStore.state)
        assertIs<LuaScreenStoreState.Failure>(wrongScreen.screenStore.state)
        assertSame(edited.localStateStore, invalid.localStateStore)
        assertSame(edited.localStateStore, wrongScreen.localStateStore)
        assertEquals("rice", invalid.localStateStore.draftFor(filter(initial)).value)
        assertEquals("rice", wrongScreen.localStateStore.draftFor(filter(initial)).value)
    }

    @Test
    fun `loading failure and incompatibility retain local state for a same-session retry`() {
        val initial = readySession(screen = dashboard(initialValue = "all"))
        val edited = initial.updateTextField(filter(initial).id, "rice")
        val loading = edited.beginLoading()
        val failure = loading.accept(
            LuaScreenResponse.Failure(LuaError(LuaErrorCode.TRANSPORT, "Offline")),
        )
        val incompatible = failure.accept(
            LuaScreenResponse.Incompatible(setOf(LuaCapabilities.textField)),
        )

        assertIs<LuaScreenStoreState.Loading>(loading.screenStore.state)
        assertIs<LuaScreenStoreState.Failure>(failure.screenStore.state)
        assertIs<LuaScreenStoreState.Incompatible>(incompatible.screenStore.state)
        assertSame(edited.localStateStore, loading.localStateStore)
        assertSame(edited.localStateStore, failure.localStateStore)
        assertSame(edited.localStateStore, incompatible.localStateStore)
    }

    @Test
    fun `explicit reset and a new session do not retain a previous draft`() {
        val initial = readySession(screen = dashboard(initialValue = "all"))
        val edited = initial.updateTextField(filter(initial).id, "rice")

        val resetDraft = edited.resetLocalState().localStateStore.draftFor(filter(edited))
        val freshSession = readySession(screen = dashboard(initialValue = "all"))
        val freshDraft = freshSession.localStateStore.draftFor(filter(freshSession))

        assertEquals("all", resetDraft.value)
        assertTrue(!resetDraft.isDirty)
        assertEquals("all", freshDraft.value)
        assertTrue(!freshDraft.isDirty)
    }

    @Test
    fun `reset materializes fresh TextField slots for a ready screen`() {
        val edited = readyFormSession(initialValue = "all")
            .updateTextField(LuaNodeId("dashboard.filter"), "rice")

        val reset = edited.resetLocalState()
        val draft = reset.localStateStore.draftFor(filter(reset))

        assertEquals("all", draft.value)
        assertTrue(!draft.isDirty)
        assertTrue(draft.slotGeneration > 0)
    }

    @Test
    fun `editing back to the baseline is pristine and unsupported IDs are no-ops`() {
        val initial = readySession(screen = dashboard(initialValue = "all"))
        val edited = initial.updateTextField(filter(initial).id, "rice")
        val restored = edited.updateTextField(filter(edited).id, "all")

        val draft = restored.localStateStore.draftFor(filter(restored))
        val unknown = restored.updateTextField(LuaNodeId("dashboard.missing"), "ignored")
        val nonTextField = restored.updateTextField(LuaNodeId("dashboard.root"), "ignored")

        assertEquals("all", draft.value)
        assertTrue(!draft.isDirty)
        assertSame(restored, unknown)
        assertSame(restored, nonTextField)
    }

    @Test
    fun `matching acknowledged submission settles a normalized TextField baseline`() {
        val initial = readyFormSession(initialValue = "all")
        val edited = initial.updateTextField(filter(initial).id, " rice ")
        val action = applyAction(edited)
        val prepared = assertIs<LuaPreparedAction>(
            edited.prepareAction(
                sourceNodeId = LuaNodeId("dashboard.apply"),
                action = action,
                submissionId = LuaSubmissionId("dashboard.submit.1"),
            ),
        )
        val submitted = requireNotNull(prepared.request.textFieldSubmission)

        assertEquals(" rice ", submitted.fields.single().value)
        assertEquals(1, submitted.fields.single().draftRevision)

        val settled = edited.complete(
            prepared,
            acknowledgedResponse(
                screen = formDashboard(initialValue = "rice"),
                submissionId = submitted.submissionId,
                draftRevision = submitted.fields.single().draftRevision,
            ),
        )
        val draft = settled.localStateStore.draftFor(filter(settled))

        assertEquals("rice", draft.value)
        assertEquals("rice", draft.serverBaseline)
        assertEquals(1, draft.draftRevision)
        assertTrue(!draft.isDirty)
    }

    @Test
    fun `late acknowledged submission never overwrites a newer local edit`() {
        val initial = readyFormSession(initialValue = "all")
        val submittedSession = initial.updateTextField(filter(initial).id, "rice")
        val action = applyAction(submittedSession)
        val prepared = assertIs<LuaPreparedAction>(
            submittedSession.prepareAction(
                sourceNodeId = LuaNodeId("dashboard.apply"),
                action = action,
                submissionId = LuaSubmissionId("dashboard.submit.1"),
            ),
        )
        val submitted = requireNotNull(prepared.request.textFieldSubmission)
        val newerEdit = submittedSession.updateTextField(filter(submittedSession).id, "corn")

        val completed = newerEdit.complete(
            prepared,
            acknowledgedResponse(
                screen = formDashboard(initialValue = "rice"),
                submissionId = submitted.submissionId,
                draftRevision = submitted.fields.single().draftRevision,
            ),
        )
        val draft = completed.localStateStore.draftFor(filter(completed))

        assertEquals("corn", draft.value)
        assertEquals("rice", draft.serverBaseline)
        assertEquals(2, draft.draftRevision)
        assertTrue(draft.isDirty)
    }

    @Test
    fun `matching acknowledgement settles each unchanged draft independently`() {
        val initial = readyTwoFieldFormSession(firstInitialValue = "all", secondInitialValue = "all")
        val submittedSession = initial
            .updateTextField(filter(initial).id, "rice")
            .updateTextField(secondaryFilter(initial).id, "corn")
        val prepared = assertIs<LuaPreparedAction>(
            submittedSession.prepareAction(
                sourceNodeId = LuaNodeId("dashboard.apply"),
                action = applyAction(submittedSession),
                submissionId = LuaSubmissionId("dashboard.submit.1"),
            ),
        )
        val submitted = requireNotNull(prepared.request.textFieldSubmission)
        val editedAfterSubmit = submittedSession.updateTextField(filter(submittedSession).id, "wheat")

        val completed = editedAfterSubmit.complete(
            prepared,
            LuaActionResponse.AcknowledgedScreen(
                screen = twoFieldFormDashboard(
                    firstInitialValue = "rice-normalized",
                    secondInitialValue = "corn-normalized",
                ),
                acknowledgement = LuaTextFieldAcknowledgement(
                    submissionId = submitted.submissionId,
                    fields = submitted.fields.map { field ->
                        LuaAcknowledgedTextField(field.nodeId, field.draftRevision)
                    },
                ),
            ),
        )
        val firstDraft = completed.localStateStore.draftFor(filter(completed))
        val secondDraft = completed.localStateStore.draftFor(secondaryFilter(completed))

        assertEquals("wheat", firstDraft.value)
        assertEquals("rice-normalized", firstDraft.serverBaseline)
        assertTrue(firstDraft.isDirty)
        assertEquals("corn-normalized", secondDraft.value)
        assertEquals("corn-normalized", secondDraft.serverBaseline)
        assertTrue(!secondDraft.isDirty)
    }

    @Test
    fun `unknown acknowledgement and a new local-state lifetime are ignored safely`() {
        val initial = readyFormSession(initialValue = "all")
        val edited = initial.updateTextField(filter(initial).id, "rice")
        val prepared = assertIs<LuaPreparedAction>(
            edited.prepareAction(
                sourceNodeId = LuaNodeId("dashboard.apply"),
                action = applyAction(edited),
                submissionId = LuaSubmissionId("dashboard.submit.1"),
            ),
        )
        val submitted = requireNotNull(prepared.request.textFieldSubmission)
        val wrongAcknowledgement = acknowledgedResponse(
            screen = formDashboard(initialValue = "rice"),
            submissionId = LuaSubmissionId("dashboard.submit.other"),
            draftRevision = submitted.fields.single().draftRevision,
        )

        assertSame(edited, edited.complete(prepared, wrongAcknowledgement))
        assertSame(
            edited,
            edited.complete(
                prepared,
                acknowledgedResponse(
                    screen = formDashboard(initialValue = "rice"),
                    submissionId = submitted.submissionId,
                    draftRevision = submitted.fields.single().draftRevision + 1,
                ),
            ),
        )
        val reset = edited.resetLocalState()
        assertSame(
            reset,
            reset.complete(
                prepared,
                acknowledgedResponse(
                    screen = formDashboard(initialValue = "rice"),
                    submissionId = submitted.submissionId,
                    draftRevision = submitted.fields.single().draftRevision,
                ),
            ),
        )
    }

    @Test
    fun `raw acknowledgement response is not an implicit local reset`() {
        val initial = readyFormSession(initialValue = "all")
        val edited = initial.updateTextField(filter(initial).id, "rice")
        val prepared = assertIs<LuaPreparedAction>(
            edited.prepareAction(
                sourceNodeId = LuaNodeId("dashboard.apply"),
                action = applyAction(edited),
                submissionId = LuaSubmissionId("dashboard.submit.1"),
            ),
        )
        val submitted = requireNotNull(prepared.request.textFieldSubmission)

        val rawAccepted = edited.accept(
            acknowledgedResponse(
                screen = formDashboard(initialValue = "rice"),
                submissionId = submitted.submissionId,
                draftRevision = submitted.fields.single().draftRevision,
            ),
        )

        val draft = rawAccepted.localStateStore.draftFor(filter(rawAccepted))
        assertEquals("rice", draft.value)
        assertTrue(draft.isDirty)
    }

    @Test
    fun `a prepared acknowledgement is consumed after one valid completion`() {
        val initial = readyFormSession(initialValue = "all")
        val edited = initial.updateTextField(filter(initial).id, "rice")
        val prepared = assertIs<LuaPreparedAction>(
            edited.prepareAction(
                sourceNodeId = LuaNodeId("dashboard.apply"),
                action = applyAction(edited),
                submissionId = LuaSubmissionId("dashboard.submit.1"),
            ),
        )
        val submitted = requireNotNull(prepared.request.textFieldSubmission)
        val accepted = acknowledgedResponse(
            screen = formDashboard(initialValue = "rice"),
            submissionId = submitted.submissionId,
            draftRevision = submitted.fields.single().draftRevision,
        )
        val settled = edited.complete(prepared, accepted)

        val replayed = settled.complete(
            prepared,
            acknowledgedResponse(
                screen = formDashboard(initialValue = "unexpected replay value"),
                submissionId = submitted.submissionId,
                draftRevision = submitted.fields.single().draftRevision,
            ),
        )

        assertSame(settled, replayed)
        assertEquals("rice", replayed.localStateStore.draftFor(filter(replayed)).value)
    }

    @Test
    fun `prepared submission fields are immutable after snapshot`() {
        val initial = readyFormSession(initialValue = "all")
        val edited = initial.updateTextField(filter(initial).id, "rice")
        val prepared = assertIs<LuaPreparedAction>(
            edited.prepareAction(
                sourceNodeId = LuaNodeId("dashboard.apply"),
                action = applyAction(edited),
                submissionId = LuaSubmissionId("dashboard.submit.1"),
            ),
        )
        val fields = requireNotNull(prepared.request.textFieldSubmission).fields

        assertFails {
            (fields as MutableList<LuaSubmittedTextField>)[0] = LuaSubmittedTextField(
                nodeId = LuaNodeId("dashboard.filter"),
                value = "tampered",
                draftRevision = 1,
            )
        }
        assertEquals("rice", fields.single().value)
    }

    @Test
    fun `prepareAction rejects an invalid submission ID and configured request boundary`() {
        val initial = readyFormSession(
            initialValue = "all",
            limits = LuaProtocolLimits(maxSubmittedTextFieldValueLength = 3),
        )
        val edited = initial.updateTextField(filter(initial).id, "rice")
        val action = applyAction(edited)

        assertNull(
            edited.prepareAction(
                sourceNodeId = LuaNodeId("dashboard.apply"),
                action = action,
                submissionId = LuaSubmissionId("invalid submission id"),
            ),
        )
        assertNull(
            edited.prepareAction(
                sourceNodeId = LuaNodeId("dashboard.apply"),
                action = action,
                submissionId = LuaSubmissionId("dashboard.submit.1"),
            ),
        )
    }

    @Test
    fun `acknowledged replacement that removes a field never revives its old draft`() {
        val initial = readyFormSession(initialValue = "all")
        val edited = initial.updateTextField(filter(initial).id, "rice")
        val prepared = assertIs<LuaPreparedAction>(
            edited.prepareAction(
                sourceNodeId = LuaNodeId("dashboard.apply"),
                action = applyAction(edited),
                submissionId = LuaSubmissionId("dashboard.submit.1"),
            ),
        )
        val submitted = requireNotNull(prepared.request.textFieldSubmission)

        val removed = edited.complete(
            prepared,
            acknowledgedResponse(
                screen = dashboard(field = null),
                submissionId = submitted.submissionId,
                draftRevision = submitted.fields.single().draftRevision,
            ),
        )
        val reintroduced = removed.accept(LuaScreenResponse.Screen(formDashboard(initialValue = "fresh")))
        val draft = reintroduced.localStateStore.draftFor(filter(reintroduced))

        assertEquals("fresh", draft.value)
        assertEquals("fresh", draft.serverBaseline)
        assertTrue(!draft.isDirty)
    }

    private fun readySession(screen: LuaScreen): LuaScreenSession = LuaScreenSession.loading(
        screenId = LuaScreenId("dashboard"),
        clientCapabilities = LuaCapabilities.foundation + LuaCapabilities.textField,
    ).accept(LuaScreenResponse.Screen(screen))

    private fun readyFormSession(
        initialValue: String,
        limits: LuaProtocolLimits = LuaProtocolLimits(),
    ): LuaScreenSession = LuaScreenSession.loading(
        screenId = LuaScreenId("dashboard"),
        clientCapabilities = LuaCapabilities.foundation + setOf(
            LuaCapabilities.textField,
            LuaCapabilities.submitTextFields,
        ),
        limits = limits,
    ).accept(LuaScreenResponse.Screen(formDashboard(initialValue)))

    private fun readyTwoFieldFormSession(
        firstInitialValue: String,
        secondInitialValue: String,
    ): LuaScreenSession = LuaScreenSession.loading(
        screenId = LuaScreenId("dashboard"),
        clientCapabilities = LuaCapabilities.foundation + setOf(
            LuaCapabilities.textField,
            LuaCapabilities.submitTextFields,
        ),
    ).accept(LuaScreenResponse.Screen(twoFieldFormDashboard(firstInitialValue, secondInitialValue)))

    private fun filter(session: LuaScreenSession): LuaTextFieldNode {
        val ready = assertIs<LuaScreenStoreState.Ready>(session.screenStore.state)
        return assertIs<LuaTextFieldNode>(
            ready.nodeStore.find(LuaNodeId("dashboard.filter"))?.node,
        )
    }

    private fun secondaryFilter(session: LuaScreenSession): LuaTextFieldNode {
        val ready = assertIs<LuaScreenStoreState.Ready>(session.screenStore.state)
        return assertIs<LuaTextFieldNode>(
            ready.nodeStore.find(LuaNodeId("dashboard.category"))?.node,
        )
    }

    private fun duplicateNodeScreen(): LuaScreen = LuaScreen(
        id = LuaScreenId("dashboard"),
        root = LuaColumnNode(
            id = LuaNodeId("dashboard.root"),
            children = listOf(
                LuaTextNode(LuaNodeId("dashboard.value"), "One"),
                LuaTextNode(LuaNodeId("dashboard.value"), "Two"),
            ),
        ),
        requiredCapabilities = setOf(LuaCapabilities.column, LuaCapabilities.text),
    )

    private fun dashboard(
        screenId: String = "dashboard",
        initialValue: String = "all",
        field: LuaNode? = LuaTextFieldNode(
            id = LuaNodeId("dashboard.filter"),
            label = "Filter",
            initialValue = initialValue,
        ),
    ): LuaScreen {
        val root = LuaColumnNode(
            id = LuaNodeId("dashboard.root"),
            children = listOfNotNull(field),
        )
        return LuaScreen(
            id = LuaScreenId(screenId),
            root = root,
            requiredCapabilities = LuaCapabilities.requiredBy(root),
        )
    }

    private fun nestedDashboard(initialValue: String): LuaScreen {
        val root = LuaColumnNode(
            id = LuaNodeId("dashboard.root"),
            children = listOf(
                LuaColumnNode(
                    id = LuaNodeId("dashboard.content"),
                    children = listOf(
                        LuaTextFieldNode(
                            id = LuaNodeId("dashboard.filter"),
                            label = "Filter",
                            initialValue = initialValue,
                        ),
                    ),
                ),
            ),
        )
        return LuaScreen(
            id = LuaScreenId("dashboard"),
            root = root,
            requiredCapabilities = LuaCapabilities.requiredBy(root),
        )
    }

    private fun formDashboard(initialValue: String): LuaScreen {
        val root = LuaColumnNode(
            id = LuaNodeId("dashboard.root"),
            children = listOf(
                LuaTextFieldNode(
                    id = LuaNodeId("dashboard.filter"),
                    label = "Filter",
                    initialValue = initialValue,
                ),
                LuaButtonNode(
                    id = LuaNodeId("dashboard.apply"),
                    label = "Apply",
                    onClick = LuaAction.SubmitTextFields(
                        actionId = LuaActionId("dashboard.apply"),
                        textFieldNodeIds = listOf(LuaNodeId("dashboard.filter")),
                    ),
                ),
            ),
        )
        return LuaScreen(
            id = LuaScreenId("dashboard"),
            root = root,
            requiredCapabilities = LuaCapabilities.requiredBy(root),
        )
    }

    private fun twoFieldFormDashboard(
        firstInitialValue: String,
        secondInitialValue: String,
    ): LuaScreen {
        val root = LuaColumnNode(
            id = LuaNodeId("dashboard.root"),
            children = listOf(
                LuaTextFieldNode(
                    id = LuaNodeId("dashboard.filter"),
                    label = "Filter",
                    initialValue = firstInitialValue,
                ),
                LuaTextFieldNode(
                    id = LuaNodeId("dashboard.category"),
                    label = "Category",
                    initialValue = secondInitialValue,
                ),
                LuaButtonNode(
                    id = LuaNodeId("dashboard.apply"),
                    label = "Apply",
                    onClick = LuaAction.SubmitTextFields(
                        actionId = LuaActionId("dashboard.apply"),
                        textFieldNodeIds = listOf(
                            LuaNodeId("dashboard.filter"),
                            LuaNodeId("dashboard.category"),
                        ),
                    ),
                ),
            ),
        )
        return LuaScreen(
            id = LuaScreenId("dashboard"),
            root = root,
            requiredCapabilities = LuaCapabilities.requiredBy(root),
        )
    }

    private fun applyAction(session: LuaScreenSession): LuaAction.SubmitTextFields {
        val ready = assertIs<LuaScreenStoreState.Ready>(session.screenStore.state)
        return assertIs<LuaAction.SubmitTextFields>(
            assertIs<LuaButtonNode>(ready.nodeStore.find(LuaNodeId("dashboard.apply"))?.node).onClick,
        )
    }

    private fun acknowledgedResponse(
        screen: LuaScreen,
        submissionId: LuaSubmissionId,
        draftRevision: Long,
    ): LuaActionResponse.AcknowledgedScreen = LuaActionResponse.AcknowledgedScreen(
        screen = screen,
        acknowledgement = LuaTextFieldAcknowledgement(
            submissionId = submissionId,
            fields = listOf(
                LuaAcknowledgedTextField(
                    nodeId = LuaNodeId("dashboard.filter"),
                    draftRevision = draftRevision,
                ),
            ),
        ),
    )
}
