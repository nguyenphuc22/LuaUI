package io.github.nguyenphuc22.luaui.runtime

import io.github.nguyenphuc22.luaui.core.LuaAction
import io.github.nguyenphuc22.luaui.core.LuaButtonNode
import io.github.nguyenphuc22.luaui.core.LuaCapability
import io.github.nguyenphuc22.luaui.core.LuaColumnNode
import io.github.nguyenphuc22.luaui.core.LuaNode
import io.github.nguyenphuc22.luaui.core.LuaNodeId
import io.github.nguyenphuc22.luaui.core.LuaProtocolLimits
import io.github.nguyenphuc22.luaui.core.LuaProtocolValidator
import io.github.nguyenphuc22.luaui.core.LuaScreen
import io.github.nguyenphuc22.luaui.core.LuaScreenId
import io.github.nguyenphuc22.luaui.core.LuaScreenValidationResult
import io.github.nguyenphuc22.luaui.core.LuaTextNode
import io.github.nguyenphuc22.luaui.core.LuaValidationIssue

/** The result of validating and indexing an inbound screen definition. */
sealed interface LuaNodeStoreCreation {
    data class Ready(val store: LuaNodeStore) : LuaNodeStoreCreation

    data class Invalid(val issues: List<LuaValidationIssue>) : LuaNodeStoreCreation
}

/**
 * One indexed node in a [LuaNodeStore]. A null [parentId] identifies the root node.
 *
 * [childIds] is a detached read-only list, so callers cannot alter the store index.
 */
class LuaNodeStoreEntry internal constructor(
    val node: LuaNode,
    val parentId: LuaNodeId?,
    childIds: List<LuaNodeId>,
) {
    private val childIdsSnapshot: List<LuaNodeId> = ImmutableList(childIds)

    val childIds: List<LuaNodeId>
        get() = childIdsSnapshot
}

/**
 * An immutable, screen-scoped index over one validated LuaUI definition.
 *
 * The store owns a deep snapshot of the input tree, so later mutation of a caller-owned
 * `MutableList` or `MutableSet` cannot change the rendered definition or its index. It has no
 * patch, revision, local-input, or Compose-state behavior; those are later runtime phases.
 */
class LuaNodeStore private constructor(
    val screenId: LuaScreenId,
    val protocolVersion: Int,
    val schemaVersion: Int,
    requiredCapabilities: Set<LuaCapability>,
    val rootId: LuaNodeId,
    private val entriesById: Map<LuaNodeId, LuaNodeStoreEntry>,
    preorderIds: List<LuaNodeId>,
) {
    /** A detached read-only capability snapshot for this screen. */
    val requiredCapabilities: Set<LuaCapability> = ImmutableSet(requiredCapabilities)

    private val preorderIds: List<LuaNodeId> = ImmutableList(preorderIds)

    /** The root node from this store's immutable screen snapshot. */
    val root: LuaNode
        get() = entriesById.getValue(rootId).node

    val size: Int
        get() = entriesById.size

    /** A stable pre-order traversal of this screen snapshot. */
    val nodeIdsInPreorder: List<LuaNodeId>
        get() = preorderIds

    fun contains(id: LuaNodeId): Boolean = id in entriesById

    /** O(1) lookup by node ID. Returns null when [id] is not in this screen. */
    fun find(id: LuaNodeId): LuaNodeStoreEntry? = entriesById[id]

    /** Returns null for an unknown ID and an empty list for a leaf node. */
    fun childrenOf(id: LuaNodeId): List<LuaNodeStoreEntry>? = entriesById[id]
        ?.childIds
        ?.map { childId -> entriesById.getValue(childId) }

    /** Returns null for the root node and for an unknown ID. */
    fun parentOf(id: LuaNodeId): LuaNodeStoreEntry? = entriesById[id]
        ?.parentId
        ?.let { parentId -> entriesById.getValue(parentId) }

    companion object {
        /**
         * Validates [screen] before indexing it. Invalid inbound data never creates a partial
         * store and is represented by [LuaNodeStoreCreation.Invalid].
         */
        fun create(
            screen: LuaScreen,
            clientCapabilities: Set<LuaCapability>,
            limits: LuaProtocolLimits = LuaProtocolLimits(),
        ): LuaNodeStoreCreation = when (
            val validation = LuaProtocolValidator.validateScreen(
                screen = screen,
                clientCapabilities = ImmutableSet(clientCapabilities),
                limits = limits,
            )
        ) {
            is LuaScreenValidationResult.Invalid -> LuaNodeStoreCreation.Invalid(validation.issues)
            is LuaScreenValidationResult.Valid -> LuaNodeStoreCreation.Ready(index(snapshotOf(validation.screen)))
        }

        private fun index(screen: LuaScreen): LuaNodeStore {
            val entries = linkedMapOf<LuaNodeId, LuaNodeStoreEntry>()
            val traversal = mutableListOf<LuaNodeId>()

            fun visit(node: LuaNode, parentId: LuaNodeId?) {
                val childIds = (node as? LuaColumnNode)?.children?.map { child -> child.id }.orEmpty()
                entries[node.id] = LuaNodeStoreEntry(
                    node = node,
                    parentId = parentId,
                    childIds = childIds,
                )
                traversal += node.id
                (node as? LuaColumnNode)?.children?.forEach { child -> visit(child, node.id) }
            }

            visit(screen.root, parentId = null)
            return LuaNodeStore(
                screenId = screen.id,
                protocolVersion = screen.protocolVersion,
                schemaVersion = screen.schemaVersion,
                requiredCapabilities = ImmutableSet(screen.requiredCapabilities),
                rootId = screen.root.id,
                entriesById = entries.toMap(),
                preorderIds = ImmutableList(traversal),
            )
        }

        private fun snapshotOf(screen: LuaScreen): LuaScreen = screen.copy(
            root = snapshotNode(screen.root),
            requiredCapabilities = ImmutableSet(screen.requiredCapabilities),
        )

        private fun snapshotNode(node: LuaNode): LuaNode = when (node) {
            is LuaColumnNode -> LuaColumnNode(
                id = node.id,
                children = ImmutableList(node.children.map(::snapshotNode)),
            )

            is LuaTextNode -> LuaTextNode(
                id = node.id,
                text = node.text,
            )

            is LuaButtonNode -> LuaButtonNode(
                id = node.id,
                label = node.label,
                onClick = snapshotAction(node.onClick),
            )
        }

        private fun snapshotAction(action: LuaAction): LuaAction = when (action) {
            is LuaAction.Submit -> LuaAction.Submit(action.actionId)
        }
    }
}

internal class ImmutableList<T>(values: Collection<T>) : AbstractList<T>() {
    private val snapshot = values.toList()

    override val size: Int
        get() = snapshot.size

    override fun get(index: Int): T = snapshot[index]
}

internal class ImmutableSet<T>(values: Collection<T>) : AbstractSet<T>() {
    private val membership = values.toSet()
    private val iterationOrder = ImmutableList(membership)

    override val size: Int
        get() = membership.size

    override fun contains(element: @UnsafeVariance T): Boolean = element in membership

    override fun iterator(): Iterator<T> = iterationOrder.iterator()
}
