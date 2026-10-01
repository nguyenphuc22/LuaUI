package io.github.nguyenphuc22.luaui.compose

import androidx.compose.runtime.Composable
import io.github.nguyenphuc22.luaui.core.LuaAction
import io.github.nguyenphuc22.luaui.core.LuaCapability
import io.github.nguyenphuc22.luaui.core.LuaNodeId
import io.github.nguyenphuc22.luaui.core.LuaScreen
import io.github.nguyenphuc22.luaui.core.LuaScreenValidationResult
import io.github.nguyenphuc22.luaui.core.LuaProtocolValidator
import io.github.nguyenphuc22.luaui.core.LuaValidationIssue

@Composable
fun LuaScreenHost(
    screen: LuaScreen,
    dispatcher: LuaNodeDispatcher,
    clientCapabilities: Set<LuaCapability>,
    onAction: (LuaNodeId, LuaAction) -> Unit,
    errorContent: @Composable (List<LuaValidationIssue>) -> Unit,
) {
    when (val validation = LuaProtocolValidator.validateScreen(screen, clientCapabilities)) {
        is LuaScreenValidationResult.Valid -> dispatcher.Render(
            node = validation.screen.root,
            onAction = onAction,
        )

        is LuaScreenValidationResult.Invalid -> errorContent(validation.issues)
    }
}
