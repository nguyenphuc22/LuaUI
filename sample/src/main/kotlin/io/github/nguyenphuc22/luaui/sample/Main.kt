package io.github.nguyenphuc22.luaui.sample

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import io.github.nguyenphuc22.luaui.compose.LuaScreenHost
import io.github.nguyenphuc22.luaui.core.LuaActionResponse
import io.github.nguyenphuc22.luaui.core.LuaCapabilities
import io.github.nguyenphuc22.luaui.core.LuaError
import io.github.nguyenphuc22.luaui.core.LuaErrorCode
import io.github.nguyenphuc22.luaui.core.LuaScreenId
import io.github.nguyenphuc22.luaui.core.LuaScreenRequest
import io.github.nguyenphuc22.luaui.core.LuaScreenResponse
import io.github.nguyenphuc22.luaui.material3.generated.Material3GeneratedRendererDispatcher
import io.github.nguyenphuc22.luaui.runtime.LuaNodeStore
import io.github.nguyenphuc22.luaui.runtime.LuaNodeStoreCreation
import io.github.nguyenphuc22.luaui.transport.HttpLuaTransport
import kotlinx.coroutines.launch

fun main() {
    val server = startDashboardServer()

    application {
        val transport = remember { HttpLuaTransport("http://127.0.0.1:8080") }
        val coroutineScope = rememberCoroutineScope()
        var uiState by remember { mutableStateOf<DashboardUiState>(DashboardUiState.Loading) }

        LaunchedEffect(Unit) {
            uiState = transport.loadScreen(
                LuaScreenRequest(
                    screenId = LuaScreenId("dashboard"),
                    clientCapabilities = LuaCapabilities.foundation,
                ),
            ).toDashboardUiState()
        }

        Window(
            onCloseRequest = {
                transport.close()
                server.stop(gracePeriodMillis = 250, timeoutMillis = 1_000)
                exitApplication()
            },
            title = "LuaUI Foundation 0.1",
        ) {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    when (val current = uiState) {
                        DashboardUiState.Loading -> CircularProgressIndicator()
                        is DashboardUiState.Ready -> LuaScreenHost(
                            nodeStore = current.nodeStore,
                            dispatcher = Material3GeneratedRendererDispatcher,
                            onAction = { nodeId, action ->
                                coroutineScope.launch {
                                    uiState = transport.dispatchAction(
                                        request = io.github.nguyenphuc22.luaui.core.LuaActionRequest(
                                            screenId = current.nodeStore.screenId,
                                            sourceNodeId = nodeId,
                                            actionId = action.actionId,
                                            clientCapabilities = LuaCapabilities.foundation,
                                        ),
                                    ).asScreenResponse().toDashboardUiState()
                                }
                            },
                        )

                        is DashboardUiState.Incompatible -> Text(
                            "Client is missing: ${current.missingCapabilities.joinToString { capability -> capability.name }}",
                        )

                        is DashboardUiState.Failure -> Text(current.error.message)
                    }
                }
            }
        }
    }
}

private fun LuaActionResponse.asScreenResponse(): LuaScreenResponse = when (this) {
    is LuaActionResponse.Screen -> LuaScreenResponse.Screen(screen)
    is LuaActionResponse.Failure -> LuaScreenResponse.Failure(error)
}

private sealed interface DashboardUiState {
    data object Loading : DashboardUiState

    data class Ready(
        val nodeStore: LuaNodeStore,
    ) : DashboardUiState

    data class Incompatible(
        val missingCapabilities: Set<io.github.nguyenphuc22.luaui.core.LuaCapability>,
    ) : DashboardUiState

    data class Failure(
        val error: LuaError,
    ) : DashboardUiState
}

private fun LuaScreenResponse.toDashboardUiState(): DashboardUiState = when (this) {
    is LuaScreenResponse.Screen -> when (
        val creation = LuaNodeStore.create(
            screen = screen,
            clientCapabilities = LuaCapabilities.foundation,
        )
    ) {
        is LuaNodeStoreCreation.Ready -> DashboardUiState.Ready(creation.store)
        is LuaNodeStoreCreation.Invalid -> DashboardUiState.Failure(
            LuaError(
                code = LuaErrorCode.INVALID_SCREEN,
                message = "The client rejected an invalid LuaUI screen.",
            ),
        )
    }

    is LuaScreenResponse.Incompatible -> DashboardUiState.Incompatible(missingCapabilities)
    is LuaScreenResponse.Failure -> DashboardUiState.Failure(error)
}
