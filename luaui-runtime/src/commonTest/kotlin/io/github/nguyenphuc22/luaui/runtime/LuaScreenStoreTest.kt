package io.github.nguyenphuc22.luaui.runtime

import io.github.nguyenphuc22.luaui.core.LuaAction
import io.github.nguyenphuc22.luaui.core.LuaActionId
import io.github.nguyenphuc22.luaui.core.LuaActionResponse
import io.github.nguyenphuc22.luaui.core.LuaCapabilities
import io.github.nguyenphuc22.luaui.core.LuaCapability
import io.github.nguyenphuc22.luaui.core.LuaColumnNode
import io.github.nguyenphuc22.luaui.core.LuaError
import io.github.nguyenphuc22.luaui.core.LuaErrorCode
import io.github.nguyenphuc22.luaui.core.LuaNode
import io.github.nguyenphuc22.luaui.core.LuaNodeId
import io.github.nguyenphuc22.luaui.core.LuaProtocolLimits
import io.github.nguyenphuc22.luaui.core.LuaScreen
import io.github.nguyenphuc22.luaui.core.LuaScreenId
import io.github.nguyenphuc22.luaui.core.LuaScreenResponse
import io.github.nguyenphuc22.luaui.core.LuaTextNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

class LuaScreenStoreTest {
    @Test
    fun `starts loading and beginLoading returns a detached successor`() {
        val loading = store()

        assertIs<LuaScreenStoreState.Loading>(loading.state)

        val ready = loading.accept(LuaScreenResponse.Screen(dashboard()))
        val restarted = ready.beginLoading()

        assertIs<LuaScreenStoreState.Ready>(ready.state)
        assertIs<LuaScreenStoreState.Loading>(restarted.state)
        assertNotSame(ready, restarted)
    }

    @Test
    fun `valid screen response becomes a renderable immutable node store`() {
        val successor = store().accept(LuaScreenResponse.Screen(dashboard()))

        val ready = assertIs<LuaScreenStoreState.Ready>(successor.state)
        assertEquals(LuaScreenId("dashboard"), successor.screenId)
        assertEquals(
            "1,500,000 ₫",
            assertIs<LuaTextNode>(ready.nodeStore.find(LuaNodeId("dashboard.price"))?.node).text,
        )
    }

    @Test
    fun `action screen response replaces the complete snapshot without mutating its predecessor`() {
        val initial = store().accept(LuaScreenResponse.Screen(dashboard(price = "1,500,000 ₫")))
        val replacement = initial.accept(
            LuaActionResponse.Screen(dashboard(price = "1,501,000 ₫")),
        )

        val initialReady = assertIs<LuaScreenStoreState.Ready>(initial.state)
        val replacementReady = assertIs<LuaScreenStoreState.Ready>(replacement.state)

        assertNotSame(initial, replacement)
        assertNotSame(initialReady.nodeStore, replacementReady.nodeStore)
        assertEquals(
            "1,500,000 ₫",
            assertIs<LuaTextNode>(initialReady.nodeStore.find(LuaNodeId("dashboard.price"))?.node).text,
        )
        assertEquals(
            "1,501,000 ₫",
            assertIs<LuaTextNode>(replacementReady.nodeStore.find(LuaNodeId("dashboard.price"))?.node).text,
        )
        assertEquals(
            LuaNodeId("dashboard.refresh"),
            replacementReady.nodeStore.find(LuaNodeId("dashboard.refresh"))?.node?.id,
        )
    }

    @Test
    fun `invalid and cross-screen payloads become failures without publishing a partial tree`() {
        val ready = store().accept(LuaScreenResponse.Screen(dashboard()))
        val invalid = ready.accept(LuaScreenResponse.Screen(duplicateNodeScreen()))
        val wrongScreen = ready.accept(LuaScreenResponse.Screen(dashboard(screenId = "inventory")))

        val invalidFailure = assertIs<LuaScreenStoreState.Failure>(invalid.state)
        val wrongScreenFailure = assertIs<LuaScreenStoreState.Failure>(wrongScreen.state)

        assertEquals(LuaErrorCode.INVALID_SCREEN, invalidFailure.error.code)
        assertTrue(invalidFailure.validationIssues.any { issue -> issue.code == "duplicate_node_id" })
        assertTrue(invalidFailure.validationIssues !is MutableList<*>)
        assertTrue(invalidFailure.validationIssues.subList(0, 1) !is MutableList<*>)
        assertEquals(LuaErrorCode.INVALID_SCREEN, wrongScreenFailure.error.code)
        assertTrue(wrongScreenFailure.validationIssues.isEmpty())
        assertEquals(
            "1,500,000 ₫",
            assertIs<LuaTextNode>(
                assertIs<LuaScreenStoreState.Ready>(ready.state)
                    .nodeStore
                    .find(LuaNodeId("dashboard.price"))
                    ?.node,
            ).text,
        )
    }

    @Test
    fun `response failures and incompatibility preserve safe typed state and recover on a later screen`() {
        val error = LuaError(LuaErrorCode.UNAUTHORIZED_ACTION, "Action no longer allowed.")
        val screenFailure = store().accept(LuaScreenResponse.Failure(error))
        val ready = store().accept(LuaScreenResponse.Screen(dashboard()))
        val actionFailure = ready.accept(LuaActionResponse.Failure(error))
        val missing = mutableSetOf(LuaCapabilities.button)
        val incompatible = screenFailure.accept(LuaScreenResponse.Incompatible(missing))
        missing.clear()
        val recovered = incompatible.accept(LuaScreenResponse.Screen(dashboard()))

        assertEquals(error, assertIs<LuaScreenStoreState.Failure>(screenFailure.state).error)
        assertEquals(error, assertIs<LuaScreenStoreState.Failure>(actionFailure.state).error)
        assertIs<LuaScreenStoreState.Ready>(ready.state)
        val incompatibleState = assertIs<LuaScreenStoreState.Incompatible>(incompatible.state)
        assertEquals(setOf(LuaCapabilities.button), incompatibleState.missingCapabilities)
        assertTrue(incompatibleState.missingCapabilities !is MutableSet<*>)
        assertTrue(incompatibleState.missingCapabilities.iterator() !is MutableIterator<*>)
        assertIs<LuaScreenStoreState.Ready>(recovered.state)
    }

    @Test
    fun `snapshots capabilities and applies configured validation limits to every response`() {
        val capabilities = LuaCapabilities.foundation.toMutableSet()
        val withSnapshot = LuaScreenStore.loading(
            screenId = LuaScreenId("dashboard"),
            clientCapabilities = capabilities,
        )
        capabilities.clear()

        assertIs<LuaScreenStoreState.Ready>(
            withSnapshot.accept(LuaScreenResponse.Screen(dashboard())).state,
        )

        val constrained = store(limits = LuaProtocolLimits(maxNodes = 1))
            .accept(LuaScreenResponse.Screen(dashboard()))
        val constrainedFailure = assertIs<LuaScreenStoreState.Failure>(constrained.state)

        assertEquals(LuaErrorCode.INVALID_SCREEN, constrainedFailure.error.code)
        assertTrue(constrainedFailure.validationIssues.any { issue -> issue.code == "node_limit_exceeded" })
    }

    @Test
    fun `missing client capabilities reject a candidate instead of weakening later validation`() {
        val successor = LuaScreenStore.loading(
            screenId = LuaScreenId("dashboard"),
            clientCapabilities = setOf(LuaCapabilities.column, LuaCapabilities.text),
        ).accept(LuaScreenResponse.Screen(dashboard()))

        val failure = assertIs<LuaScreenStoreState.Failure>(successor.state)
        assertEquals(LuaErrorCode.INVALID_SCREEN, failure.error.code)
        assertTrue(failure.validationIssues.any { issue -> issue.code == "client_capability_missing" })
    }

    private fun store(
        limits: LuaProtocolLimits = LuaProtocolLimits(),
    ): LuaScreenStore = LuaScreenStore.loading(
        screenId = LuaScreenId("dashboard"),
        clientCapabilities = LuaCapabilities.foundation,
        limits = limits,
    )

    private fun duplicateNodeScreen(): LuaScreen {
        val root = LuaColumnNode(
            id = LuaNodeId("dashboard.root"),
            children = listOf(
                LuaTextNode(LuaNodeId("dashboard.value"), "First"),
                LuaTextNode(LuaNodeId("dashboard.value"), "Second"),
            ),
        )
        return LuaScreen(
            id = LuaScreenId("dashboard"),
            root = root,
            requiredCapabilities = LuaCapabilities.requiredBy(root),
        )
    }

    private fun dashboard(
        screenId: String = "dashboard",
        price: String = "1,500,000 ₫",
    ): LuaScreen {
        val root = LuaColumnNode(
            id = LuaNodeId("dashboard.root"),
            children = listOf<LuaNode>(
                LuaTextNode(LuaNodeId("dashboard.title"), "Dashboard"),
                LuaTextNode(LuaNodeId("dashboard.price"), price),
                io.github.nguyenphuc22.luaui.core.LuaButtonNode(
                    id = LuaNodeId("dashboard.refresh"),
                    label = "Refresh",
                    onClick = LuaAction.Submit(LuaActionId("dashboard.refresh")),
                ),
            ),
        )
        return LuaScreen(
            id = LuaScreenId(screenId),
            root = root,
            requiredCapabilities = LuaCapabilities.requiredBy(root),
        )
    }
}
