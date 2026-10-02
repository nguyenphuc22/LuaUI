package io.github.nguyenphuc22.luaui.material3

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import io.github.nguyenphuc22.luaui.annotations.LuaRenderer
import io.github.nguyenphuc22.luaui.compose.LuaRenderContext
import io.github.nguyenphuc22.luaui.core.LuaButtonNode
import io.github.nguyenphuc22.luaui.core.LuaColumnNode
import io.github.nguyenphuc22.luaui.core.LuaTextFieldNode
import io.github.nguyenphuc22.luaui.core.LuaTextNode

@LuaRenderer(LuaColumnNode::class)
@Composable
fun renderColumn(node: LuaColumnNode, context: LuaRenderContext) {
    Column {
        node.children.forEach { child -> context.RenderChild(child) }
    }
}

@LuaRenderer(LuaTextNode::class)
@Composable
fun renderText(node: LuaTextNode, context: LuaRenderContext) {
    Text(text = node.text)
}

@LuaRenderer(LuaTextFieldNode::class)
@Composable
fun renderTextField(node: LuaTextFieldNode, context: LuaRenderContext) {
    OutlinedTextField(
        value = context.textFieldValue(node),
        onValueChange = { value -> context.updateTextField(node, value) },
        label = { Text(text = node.label) },
    )
}

@LuaRenderer(LuaButtonNode::class)
@Composable
fun renderButton(node: LuaButtonNode, context: LuaRenderContext) {
    Button(onClick = { context.onAction(node.id, node.onClick) }) {
        Text(text = node.label)
    }
}
