package io.github.nguyenphuc22.luaui.sample

import io.github.nguyenphuc22.luaui.core.LuaActionRequest
import io.github.nguyenphuc22.luaui.core.LuaError
import io.github.nguyenphuc22.luaui.core.LuaErrorCode
import io.github.nguyenphuc22.luaui.core.LUA_MAX_PAYLOAD_BYTES
import io.github.nguyenphuc22.luaui.core.LuaProtocolJson
import io.github.nguyenphuc22.luaui.core.LuaScreenId
import io.github.nguyenphuc22.luaui.core.LuaScreenRequest
import io.github.nguyenphuc22.luaui.core.LuaScreenResponse
import io.github.nguyenphuc22.luaui.server.LuaScreenController
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.serialization.decodeFromString

fun startDashboardServer(port: Int = 8080): EmbeddedServer<*, *> = embeddedServer(
    factory = Netty,
    port = port,
    host = "127.0.0.1",
) {
    dashboardModule(DashboardController())
}.start(wait = false)

fun Application.dashboardModule(controller: LuaScreenController) {
    install(ContentNegotiation) {
        json(LuaProtocolJson)
    }

    routing {
        post("/v1/screens/{screenId}") {
            val request = call.receiveLuaPayload<LuaScreenRequest> { message ->
                LuaScreenResponse.Failure(
                    LuaError(
                        code = LuaErrorCode.INVALID_REQUEST,
                        message = message,
                    ),
                )
            } ?: return@post
            val routeScreenId = call.parameters["screenId"]
            if (routeScreenId != request.screenId.value) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    LuaScreenResponse.Failure(
                        LuaError(
                            code = LuaErrorCode.INVALID_REQUEST,
                            message = "Route and request screen IDs do not match.",
                        ),
                    ),
                )
            } else {
                call.respond(controller.load(request))
            }
        }

        post("/v1/actions") {
            val request = call.receiveLuaPayload<LuaActionRequest> { message ->
                io.github.nguyenphuc22.luaui.core.LuaActionResponse.Failure(
                    LuaError(
                        code = LuaErrorCode.INVALID_REQUEST,
                        message = message,
                    ),
                )
            } ?: return@post
            call.respond(controller.dispatch(request))
        }
    }
}

private suspend inline fun <reified T> io.ktor.server.application.ApplicationCall.receiveLuaPayload(
    errorResponse: (String) -> Any,
): T? {
    val body = receiveText()
    if (body.encodeToByteArray().size > LUA_MAX_PAYLOAD_BYTES) {
        respond(
            HttpStatusCode.PayloadTooLarge,
            errorResponse("LuaUI request exceeds the payload limit."),
        )
        return null
    }

    return runCatching { LuaProtocolJson.decodeFromString<T>(body) }
        .getOrElse {
            respond(
                HttpStatusCode.BadRequest,
                errorResponse("LuaUI request is malformed."),
            )
            null
        }
}
