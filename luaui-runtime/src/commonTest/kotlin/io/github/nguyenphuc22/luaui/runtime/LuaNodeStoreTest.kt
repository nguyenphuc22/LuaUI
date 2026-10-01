package io.github.nguyenphuc22.luaui.runtime

import io.github.nguyenphuc22.luaui.core.LuaAction
import io.github.nguyenphuc22.luaui.core.LuaActionId
import io.github.nguyenphuc22.luaui.core.LuaButtonNode
import io.github.nguyenphuc22.luaui.core.LuaCapabilities
import io.github.nguyenphuc22.luaui.core.LuaColumnNode
import io.github.nguyenphuc22.luaui.core.LuaNode
import io.github.nguyenphuc22.luaui.core.LuaNodeId
import io.github.nguyenphuc22.luaui.core.LuaProtocolLimits
import io.github.nguyenphuc22.luaui.core.LuaScreen
import io.github.nguyenphuc22.luaui.core.LuaScreenId
import io.github.nguyenphuc22.luaui.core.LuaTextNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LuaNodeStoreTest {
    @Test
    fun `indexes nested nodes with parents child order and stable preorder`() {
        val store = ready(dashboard())

        assertEquals(LuaNodeId("dashboard.root"), store.rootId)
        assertEquals(5, store.size)
        assertTrue(store.contains(LuaNodeId("dashboard.price")))
        assertNull(store.find(LuaNodeId("dashboard.missing")))
        assertNull(store.parentOf(store.rootId))
        assertNull(store.childrenOf(LuaNodeId("dashboard.missing")))
        assertEquals(
            listOf(
                LuaNodeId("dashboard.root"),
                LuaNodeId("dashboard.title"),
                LuaNodeId("dashboard.content"),
                LuaNodeId("dashboard.price"),
                LuaNodeId("dashboard.refresh"),
            ),
            store.nodeIdsInPreorder,
        )
        assertEquals(
            LuaNodeId("dashboard.content"),
            store.parentOf(LuaNodeId("dashboard.price"))?.node?.id,
        )
        assertEquals(
            listOf(LuaNodeId("dashboard.price")),
            store.childrenOf(LuaNodeId("dashboard.content"))?.map { entry -> entry.node.id },
        )
        assertEquals(emptyList(), store.childrenOf(LuaNodeId("dashboard.price")))
    }

    @Test
    fun `snapshots mutable source collections before indexing`() {
        val children = mutableListOf<LuaNode>(
            LuaTextNode(LuaNodeId("dashboard.title"), "Dashboard"),
            LuaTextNode(LuaNodeId("dashboard.price"), "1,500,000 ₫"),
        )
        val capabilities = mutableSetOf(LuaCapabilities.column, LuaCapabilities.text)
        val source = LuaScreen(
            id = LuaScreenId("dashboard"),
            root = LuaColumnNode(LuaNodeId("dashboard.root"), children),
            requiredCapabilities = capabilities,
        )
        val store = ready(source)

        assertEquals(source.root, store.root)
        children.clear()
        capabilities.clear()

        assertEquals(3, store.size)
        assertEquals(
            listOf(LuaNodeId("dashboard.title"), LuaNodeId("dashboard.price")),
            store.childrenOf(LuaNodeId("dashboard.root"))?.map { entry -> entry.node.id },
        )
        assertEquals(setOf(LuaCapabilities.column, LuaCapabilities.text), store.requiredCapabilities)
        assertTrue((store.root as LuaColumnNode).children !is MutableList<*>)
        assertTrue((store.root as LuaColumnNode).children.subList(0, 1) !is MutableList<*>)
        assertTrue(store.requiredCapabilities !is MutableSet<*>)
        assertTrue(store.requiredCapabilities.iterator() !is MutableIterator<*>)
    }

    @Test
    fun `rejects cyclic in memory trees before snapshotting`() {
        val children = mutableListOf<LuaNode>()
        val root = LuaColumnNode(
            id = LuaNodeId("dashboard.root"),
            children = children,
        )
        children += root
        val cyclic = LuaScreen(
            id = LuaScreenId("dashboard"),
            root = root,
            requiredCapabilities = LuaCapabilities.requiredBy(root),
        )

        val result = invalid(LuaNodeStore.create(cyclic, LuaCapabilities.foundation))

        assertTrue(result.issues.any { issue -> issue.code == "duplicate_node_id" })
    }

    @Test
    fun `rejects invalid screens missing capabilities and configured limits`() {
        val duplicateRoot = LuaColumnNode(
            id = LuaNodeId("dashboard.root"),
            children = listOf(
                LuaTextNode(LuaNodeId("dashboard.value"), "First"),
                LuaTextNode(LuaNodeId("dashboard.value"), "Second"),
            ),
        )
        val duplicateScreen = LuaScreen(
            id = LuaScreenId("dashboard"),
            root = duplicateRoot,
            requiredCapabilities = LuaCapabilities.requiredBy(duplicateRoot),
        )

        val duplicate = invalid(LuaNodeStore.create(duplicateScreen, LuaCapabilities.foundation))
        assertTrue(duplicate.issues.any { issue -> issue.code == "duplicate_node_id" })

        val missingCapability = invalid(
            LuaNodeStore.create(
                screen = dashboard(),
                clientCapabilities = setOf(LuaCapabilities.column, LuaCapabilities.text),
            ),
        )
        assertTrue(missingCapability.issues.any { issue -> issue.code == "client_capability_missing" })

        val limited = invalid(
            LuaNodeStore.create(
                screen = dashboard(),
                clientCapabilities = LuaCapabilities.foundation,
                limits = LuaProtocolLimits(maxNodes = 1),
            ),
        )
        assertTrue(limited.issues.any { issue -> issue.code == "node_limit_exceeded" })
    }

    @Test
    fun `full screen replacements create isolated snapshots with stable IDs`() {
        val initial = ready(dashboard(price = "1,500,000 ₫"))
        val replacement = ready(dashboard(price = "1,501,000 ₫"))

        assertNotSame(initial, replacement)
        assertEquals(
            "1,500,000 ₫",
            assertIs<LuaTextNode>(assertNotNull(initial.find(LuaNodeId("dashboard.price"))).node).text,
        )
        assertEquals(
            "1,501,000 ₫",
            assertIs<LuaTextNode>(assertNotNull(replacement.find(LuaNodeId("dashboard.price"))).node).text,
        )
        assertEquals(
            LuaNodeId("dashboard.refresh"),
            replacement.find(LuaNodeId("dashboard.refresh"))?.node?.id,
        )
    }

    private fun ready(screen: LuaScreen): LuaNodeStore = assertIs<LuaNodeStoreCreation.Ready>(
        LuaNodeStore.create(screen, clientCapabilities = LuaCapabilities.foundation),
    ).store

    private fun invalid(creation: LuaNodeStoreCreation): LuaNodeStoreCreation.Invalid =
        assertIs(creation)

    private fun dashboard(price: String = "1,500,000 ₫"): LuaScreen {
        val root = LuaColumnNode(
            id = LuaNodeId("dashboard.root"),
            children = listOf(
                LuaTextNode(LuaNodeId("dashboard.title"), "Dashboard"),
                LuaColumnNode(
                    id = LuaNodeId("dashboard.content"),
                    children = listOf(
                        LuaTextNode(LuaNodeId("dashboard.price"), price),
                    ),
                ),
                LuaButtonNode(
                    id = LuaNodeId("dashboard.refresh"),
                    label = "Refresh",
                    onClick = LuaAction.Submit(LuaActionId("dashboard.refresh")),
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
