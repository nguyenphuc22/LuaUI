package io.github.nguyenphuc22.luaui.compose

import androidx.compose.runtime.Composable
import io.github.nguyenphuc22.luaui.core.LuaAction
import io.github.nguyenphuc22.luaui.core.LuaNode
import io.github.nguyenphuc22.luaui.core.LuaNodeId

interface LuaNodeDispatcher {
    @Composable
    fun Render(
        node: LuaNode,
        onAction: (LuaNodeId, LuaAction) -> Unit,
    )
}
