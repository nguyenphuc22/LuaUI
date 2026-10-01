package io.github.nguyenphuc22.luaui.compose

import androidx.compose.runtime.Composable
import io.github.nguyenphuc22.luaui.core.LuaAction
import io.github.nguyenphuc22.luaui.core.LuaCapability
import io.github.nguyenphuc22.luaui.core.LuaNodeId
import io.github.nguyenphuc22.luaui.core.LuaScreen
import io.github.nguyenphuc22.luaui.core.LuaScreenValidationResult
import io.github.nguyenphuc22.luaui.core.LuaProtocolValidator
import io.github.nguyenphuc22.luaui.core.LuaValidationIssue
import io.github.nguyenphuc22.luaui.runtime.LuaNodeStore

/**
 * Renders a screen that has already been validated and indexed at the response boundary.
 *
 * [LuaNodeStore] is immutable, so recomposition can render its root without revalidating the
 * full network definition on every composition.
 */
@Composable
fun LuaScreenHost(
    nodeStore: LuaNodeStore,
    dispatcher: LuaNodeDispatcher,
    onAction: (LuaNodeId, LuaAction) -> Unit,
) {
    dispatcher.Render(
        node = nodeStore.root,
        onAction = onAction,
    )
}

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
