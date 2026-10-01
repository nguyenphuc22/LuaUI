package io.github.nguyenphuc22.luaui.server

import io.github.nguyenphuc22.luaui.core.LuaAction
import io.github.nguyenphuc22.luaui.core.LuaActionId
import io.github.nguyenphuc22.luaui.core.LuaButtonNode
import io.github.nguyenphuc22.luaui.core.LuaCapabilities
import io.github.nguyenphuc22.luaui.core.LuaColumnNode
import io.github.nguyenphuc22.luaui.core.LuaNode
import io.github.nguyenphuc22.luaui.core.LuaNodeId
import io.github.nguyenphuc22.luaui.core.LuaScreen
import io.github.nguyenphuc22.luaui.core.LuaScreenId
import io.github.nguyenphuc22.luaui.core.LuaTextNode

@DslMarker
annotation class LuaDsl

fun luaScreen(
    id: String,
    block: LuaScreenBuilder.() -> Unit,
): LuaScreen = LuaScreenBuilder(LuaScreenId(id)).apply(block).build()

@LuaDsl
class LuaScreenBuilder internal constructor(
    private val id: LuaScreenId,
) {
    private var root: LuaNode? = null

    fun column(
        id: String,
        block: LuaColumnBuilder.() -> Unit,
    ) {
        check(root == null) { "A LuaScreen has exactly one root node." }
        root = LuaColumnBuilder(LuaNodeId(id)).apply(block).build()
    }

    internal fun build(): LuaScreen {
        val rootNode = checkNotNull(root) { "A LuaScreen must declare a root node." }
        return LuaScreen(
            id = id,
            root = rootNode,
            requiredCapabilities = LuaCapabilities.requiredBy(rootNode),
        )
    }
}

@LuaDsl
class LuaColumnBuilder internal constructor(
    private val id: LuaNodeId,
) {
    private val children = mutableListOf<LuaNode>()

    fun text(id: String, value: String) {
        children += LuaTextNode(id = LuaNodeId(id), text = value)
    }

    fun button(id: String, label: String, action: LuaAction) {
        children += LuaButtonNode(
            id = LuaNodeId(id),
            label = label,
            onClick = action,
        )
    }

    fun column(id: String, block: LuaColumnBuilder.() -> Unit) {
        children += LuaColumnBuilder(LuaNodeId(id)).apply(block).build()
    }

    internal fun build(): LuaColumnNode = LuaColumnNode(
        id = id,
        children = children.toList(),
    )
}

fun submit(actionId: String): LuaAction.Submit = LuaAction.Submit(LuaActionId(actionId))
