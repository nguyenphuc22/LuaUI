package io.github.nguyenphuc22.luaui.compose

import androidx.compose.runtime.Composable
import io.github.nguyenphuc22.luaui.core.LuaAction
import io.github.nguyenphuc22.luaui.core.LuaCapability
import io.github.nguyenphuc22.luaui.core.LuaCapabilities
import io.github.nguyenphuc22.luaui.core.LuaNodeId
import io.github.nguyenphuc22.luaui.core.LuaScreen
import io.github.nguyenphuc22.luaui.core.LuaScreenValidationResult
import io.github.nguyenphuc22.luaui.core.LuaProtocolValidator
import io.github.nguyenphuc22.luaui.core.LuaValidationIssue
import io.github.nguyenphuc22.luaui.runtime.LuaLocalStateStore
import io.github.nguyenphuc22.luaui.runtime.LuaNodeStore
import io.github.nguyenphuc22.luaui.runtime.LuaScreenSession
import io.github.nguyenphuc22.luaui.runtime.LuaScreenStoreState

/**
 * Renders the `Ready` definition and reconciled local state from one immutable screen session.
 *
 * This is the preferred host entry point for stateful controls because both layers originate
 * from the same [LuaScreenSession] publication unit. Non-ready sessions intentionally render no
 * tree; the application host remains responsible for its loading/error UI.
 */
@Composable
fun LuaScreenHost(
    screenSession: LuaScreenSession,
    dispatcher: LuaNodeDispatcher,
    onTextFieldValueChange: (LuaNodeId, String) -> Unit,
    onAction: (LuaNodeId, LuaAction) -> Unit,
) {
    val ready = screenSession.screenStore.state as? LuaScreenStoreState.Ready ?: return
    LuaScreenHost(
        nodeStore = ready.nodeStore,
        localStateStore = screenSession.localStateStore,
        dispatcher = dispatcher,
        onTextFieldValueChange = onTextFieldValueChange,
        onAction = onAction,
    )
}

/**
 * Renders a screen that has already been validated and indexed at the response boundary.
 *
 * [LuaNodeStore] is immutable, so recomposition can render its root without revalidating the
 * full network definition on every composition.
 */
@Composable
fun LuaScreenHost(
    nodeStore: LuaNodeStore,
    localStateStore: LuaLocalStateStore,
    dispatcher: LuaNodeDispatcher,
    onTextFieldValueChange: (LuaNodeId, String) -> Unit,
    onAction: (LuaNodeId, LuaAction) -> Unit,
) {
    require(nodeStore.screenId == localStateStore.screenId) {
        "LuaNodeStore and LuaLocalStateStore must belong to the same screen session."
    }
    dispatcher.Render(
        node = nodeStore.root,
        onAction = onAction,
        textFieldValue = { node -> localStateStore.draftFor(node).value },
        onTextFieldValueChange = onTextFieldValueChange,
    )
}

/**
 * Foundation-only raw-screen host retained for source compatibility.
 *
 * It deliberately rejects stateful screens instead of rendering an enabled-looking TextField
 * with no local-state owner. Use the overload that receives a [LuaLocalStateStore], or preferably
 * the [LuaScreenSession] overload, for `component.text_field@1`.
 */
@Deprecated(
    message = "Use a LuaScreenSession or a LuaLocalStateStore-backed LuaScreenHost for stateful controls.",
)
@Composable
fun LuaScreenHost(
    screen: LuaScreen,
    dispatcher: LuaNodeDispatcher,
    clientCapabilities: Set<LuaCapability>,
    onAction: (LuaNodeId, LuaAction) -> Unit,
    errorContent: @Composable (List<LuaValidationIssue>) -> Unit,
) {
    when (val validation = LuaProtocolValidator.validateScreen(screen, clientCapabilities)) {
        is LuaScreenValidationResult.Valid -> {
            if (LuaCapabilities.textField in validation.screen.requiredCapabilities) {
                errorContent(
                    listOf(
                        LuaValidationIssue(
                            code = "local_state_store_required",
                            path = "screen.requiredCapabilities",
                            message = "TextField requires a LuaLocalStateStore-backed LuaScreenHost.",
                        ),
                    ),
                )
            } else {
                dispatcher.Render(node = validation.screen.root, onAction = onAction)
            }
        }

        is LuaScreenValidationResult.Invalid -> errorContent(validation.issues)
    }
}

@Composable
fun LuaScreenHost(
    screen: LuaScreen,
    localStateStore: LuaLocalStateStore,
    dispatcher: LuaNodeDispatcher,
    clientCapabilities: Set<LuaCapability>,
    onTextFieldValueChange: (LuaNodeId, String) -> Unit,
    onAction: (LuaNodeId, LuaAction) -> Unit,
    errorContent: @Composable (List<LuaValidationIssue>) -> Unit,
) {
    when (val validation = LuaProtocolValidator.validateScreen(screen, clientCapabilities)) {
        is LuaScreenValidationResult.Valid -> {
            require(validation.screen.id == localStateStore.screenId) {
                "LuaScreen and LuaLocalStateStore must belong to the same screen session."
            }
            dispatcher.Render(
                node = validation.screen.root,
                onAction = onAction,
                textFieldValue = { node -> localStateStore.draftFor(node).value },
                onTextFieldValueChange = onTextFieldValueChange,
            )
        }

        is LuaScreenValidationResult.Invalid -> errorContent(validation.issues)
    }
}
