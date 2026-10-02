package io.github.nguyenphuc22.luaui.compose

import androidx.compose.runtime.Composable
import io.github.nguyenphuc22.luaui.core.LuaAction
import io.github.nguyenphuc22.luaui.core.LuaNode
import io.github.nguyenphuc22.luaui.core.LuaNodeId
import io.github.nguyenphuc22.luaui.core.LuaTextFieldNode

interface LuaNodeDispatcher {
    /** Foundation renderer entry point retained for dispatchers that do not support TextField. */
    @Composable
    fun Render(
        node: LuaNode,
        onAction: (LuaNodeId, LuaAction) -> Unit,
    )

    /**
     * Stateful-control entry point. The default preserves Foundation dispatcher compatibility;
     * a dispatcher that advertises `component.text_field@1` overrides this overload.
     */
    @Composable
    fun Render(
        node: LuaNode,
        onAction: (LuaNodeId, LuaAction) -> Unit,
        textFieldValue: (LuaTextFieldNode) -> String,
        onTextFieldValueChange: (LuaNodeId, String) -> Unit,
    ) {
        Render(node = node, onAction = onAction)
    }
}
