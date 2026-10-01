package io.github.nguyenphuc22.luaui.compose

import androidx.compose.runtime.Composable
import io.github.nguyenphuc22.luaui.core.LuaAction
import io.github.nguyenphuc22.luaui.core.LuaNode
import io.github.nguyenphuc22.luaui.core.LuaNodeId

class LuaRenderContext(
    val onAction: (LuaNodeId, LuaAction) -> Unit,
    private val renderChild: @Composable (LuaNode) -> Unit,
) {
    @Composable
    fun RenderChild(node: LuaNode) {
        renderChild(node)
    }
}
