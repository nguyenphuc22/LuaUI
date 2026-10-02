package io.github.nguyenphuc22.luaui.compose

import androidx.compose.runtime.Composable
import io.github.nguyenphuc22.luaui.core.LuaAction
import io.github.nguyenphuc22.luaui.core.LuaNode
import io.github.nguyenphuc22.luaui.core.LuaNodeId
import io.github.nguyenphuc22.luaui.core.LuaTextFieldNode

class LuaRenderContext(
    val onAction: (LuaNodeId, LuaAction) -> Unit,
    private val textFieldValueProvider: (LuaTextFieldNode) -> String,
    private val onTextFieldValueChange: (LuaNodeId, String) -> Unit,
    private val renderChild: @Composable (LuaNode) -> Unit,
) {
    /** Reads the client-owned draft for this server-described TextField. */
    fun textFieldValue(node: LuaTextFieldNode): String = textFieldValueProvider(node)

    /** Requests a local-only draft successor; no action payload is emitted for keystrokes. */
    fun updateTextField(node: LuaTextFieldNode, value: String) {
        onTextFieldValueChange(node.id, value)
    }

    @Composable
    fun RenderChild(node: LuaNode) {
        renderChild(node)
    }
}
