package io.github.nguyenphuc22.luaui.transport

import io.github.nguyenphuc22.luaui.core.LuaActionRequest
import io.github.nguyenphuc22.luaui.core.LuaActionResponse
import io.github.nguyenphuc22.luaui.core.LuaError
import io.github.nguyenphuc22.luaui.core.LuaErrorCode
import io.github.nguyenphuc22.luaui.core.LUA_MAX_PAYLOAD_BYTES
import io.github.nguyenphuc22.luaui.core.LuaProtocolJson
import io.github.nguyenphuc22.luaui.core.LuaProtocolValidator
import io.github.nguyenphuc22.luaui.core.LuaScreenRequest
import io.github.nguyenphuc22.luaui.core.LuaScreenResponse
import io.github.nguyenphuc22.luaui.core.LuaScreenValidationResult
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import java.io.Closeable
import kotlinx.serialization.decodeFromString

class HttpLuaTransport(
    baseUrl: String,
    private val client: HttpClient = defaultHttpClient(),
) : LuaTransport, Closeable {
    private val baseUrl = baseUrl.trimEnd('/')

    override suspend fun loadScreen(request: LuaScreenRequest): LuaScreenResponse = try {
        val responseBody = client
            .post("$baseUrl/v1/screens/${request.screenId.value}") {
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                setBody(request)
            }
            .bodyAsText()
        if (responseBody.encodeToByteArray().size > LUA_MAX_PAYLOAD_BYTES) {
            LuaScreenResponse.Failure(
                LuaError(
                    code = LuaErrorCode.INVALID_SCREEN,
                    message = "Received a LuaUI screen response that exceeds the payload limit.",
                ),
            )
        } else {
            val response = LuaProtocolJson.decodeFromString<LuaScreenResponse>(responseBody)
            response.validatedFor(request.clientCapabilities)
        }
    } catch (_: Exception) {
        LuaScreenResponse.Failure(
            LuaError(
                code = LuaErrorCode.TRANSPORT,
                message = "Unable to load the LuaUI screen.",
            ),
        )
    }

    override suspend fun dispatchAction(request: LuaActionRequest): LuaActionResponse = try {
        val responseBody = client
            .post("$baseUrl/v1/actions") {
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                setBody(request)
            }
            .bodyAsText()
        if (responseBody.encodeToByteArray().size > LUA_MAX_PAYLOAD_BYTES) {
            LuaActionResponse.Failure(
                LuaError(
                    code = LuaErrorCode.INVALID_SCREEN,
                    message = "Received a LuaUI action response that exceeds the payload limit.",
                ),
            )
        } else {
            val response = LuaProtocolJson.decodeFromString<LuaActionResponse>(responseBody)
            response.validatedFor(request.clientCapabilities)
        }
    } catch (_: Exception) {
        LuaActionResponse.Failure(
            LuaError(
                code = LuaErrorCode.TRANSPORT,
                message = "Unable to dispatch the LuaUI action.",
            ),
        )
    }

    override fun close() {
        client.close()
    }

    private fun LuaScreenResponse.validatedFor(
        clientCapabilities: Set<io.github.nguyenphuc22.luaui.core.LuaCapability>,
    ): LuaScreenResponse = when (this) {
        is LuaScreenResponse.Screen -> when (
            LuaProtocolValidator.validateScreen(screen, clientCapabilities)
        ) {
            is LuaScreenValidationResult.Valid -> this
            is LuaScreenValidationResult.Invalid -> LuaScreenResponse.Failure(
                LuaError(
                    code = LuaErrorCode.INVALID_SCREEN,
                    message = "Received an invalid or incompatible LuaUI screen.",
                ),
            )
        }

        is LuaScreenResponse.Incompatible,
        is LuaScreenResponse.Failure,
        -> this
    }

    private fun LuaActionResponse.validatedFor(
        clientCapabilities: Set<io.github.nguyenphuc22.luaui.core.LuaCapability>,
    ): LuaActionResponse = when (this) {
        is LuaActionResponse.Screen -> when (
            LuaProtocolValidator.validateScreen(screen, clientCapabilities)
        ) {
            is LuaScreenValidationResult.Valid -> this
            is LuaScreenValidationResult.Invalid -> LuaActionResponse.Failure(
                LuaError(
                    code = LuaErrorCode.INVALID_SCREEN,
                    message = "Received an invalid or incompatible LuaUI screen.",
                ),
            )
        }

        is LuaActionResponse.Failure -> this
    }

    private companion object {
        fun defaultHttpClient(): HttpClient = HttpClient(CIO) {
            install(ContentNegotiation) {
                json(LuaProtocolJson)
            }
            expectSuccess = false
        }
    }
}
