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
import io.github.nguyenphuc22.luaui.core.LuaCapabilities
import io.github.nguyenphuc22.luaui.core.LuaScreenId
import io.github.nguyenphuc22.luaui.core.LuaScreenRequest
import io.github.nguyenphuc22.luaui.material3.generated.Material3GeneratedRendererDispatcher
import io.github.nguyenphuc22.luaui.runtime.LuaScreenStore
import io.github.nguyenphuc22.luaui.runtime.LuaScreenStoreState
import io.github.nguyenphuc22.luaui.transport.HttpLuaTransport
import kotlinx.coroutines.launch

fun main() {
    val server = startDashboardServer()

    application {
        val transport = remember { HttpLuaTransport("http://127.0.0.1:8080") }
        val coroutineScope = rememberCoroutineScope()
        var screenStore by remember {
            mutableStateOf(
                LuaScreenStore.loading(
                    screenId = LuaScreenId("dashboard"),
                    clientCapabilities = LuaCapabilities.foundation,
                ),
            )
        }

        LaunchedEffect(Unit) {
            screenStore = screenStore.accept(
                transport.loadScreen(
                    LuaScreenRequest(
                        screenId = screenStore.screenId,
                        clientCapabilities = LuaCapabilities.foundation,
                    ),
                ),
            )
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
                    when (val current = screenStore.state) {
                        LuaScreenStoreState.Loading -> CircularProgressIndicator()
                        is LuaScreenStoreState.Ready -> LuaScreenHost(
                            nodeStore = current.nodeStore,
                            dispatcher = Material3GeneratedRendererDispatcher,
                            onAction = { nodeId, action ->
                                coroutineScope.launch {
                                    screenStore = screenStore.accept(
                                        transport.dispatchAction(
                                            request = io.github.nguyenphuc22.luaui.core.LuaActionRequest(
                                                screenId = current.nodeStore.screenId,
                                                sourceNodeId = nodeId,
                                                actionId = action.actionId,
                                                clientCapabilities = LuaCapabilities.foundation,
                                            ),
                                        ),
                                    )
                                }
                            },
                        )

                        is LuaScreenStoreState.Incompatible -> Text(
                            "Client is missing: ${current.missingCapabilities.joinToString { capability -> capability.name }}",
                        )

                        is LuaScreenStoreState.Failure -> Text(current.error.message)
                    }
                }
            }
        }
    }
}
