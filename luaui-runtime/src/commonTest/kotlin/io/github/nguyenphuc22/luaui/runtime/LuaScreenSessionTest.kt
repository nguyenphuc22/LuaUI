package io.github.nguyenphuc22.luaui.runtime

import io.github.nguyenphuc22.luaui.core.LuaActionResponse
import io.github.nguyenphuc22.luaui.core.LuaCapabilities
import io.github.nguyenphuc22.luaui.core.LuaColumnNode
import io.github.nguyenphuc22.luaui.core.LuaError
import io.github.nguyenphuc22.luaui.core.LuaErrorCode
import io.github.nguyenphuc22.luaui.core.LuaNode
import io.github.nguyenphuc22.luaui.core.LuaNodeId
import io.github.nguyenphuc22.luaui.core.LuaScreen
import io.github.nguyenphuc22.luaui.core.LuaScreenId
import io.github.nguyenphuc22.luaui.core.LuaScreenResponse
import io.github.nguyenphuc22.luaui.core.LuaTextFieldNode
import io.github.nguyenphuc22.luaui.core.LuaTextNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
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

    private fun readySession(screen: LuaScreen): LuaScreenSession = LuaScreenSession.loading(
        screenId = LuaScreenId("dashboard"),
        clientCapabilities = LuaCapabilities.foundation + LuaCapabilities.textField,
    ).accept(LuaScreenResponse.Screen(screen))

    private fun filter(session: LuaScreenSession): LuaTextFieldNode {
        val ready = assertIs<LuaScreenStoreState.Ready>(session.screenStore.state)
        return assertIs<LuaTextFieldNode>(
            ready.nodeStore.find(LuaNodeId("dashboard.filter"))?.node,
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
}
