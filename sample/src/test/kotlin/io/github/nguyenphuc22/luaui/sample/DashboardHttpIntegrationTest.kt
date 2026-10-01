package io.github.nguyenphuc22.luaui.sample

import io.github.nguyenphuc22.luaui.core.LuaActionRequest
import io.github.nguyenphuc22.luaui.core.LuaActionResponse
import io.github.nguyenphuc22.luaui.core.LuaCapability
import io.github.nguyenphuc22.luaui.core.LuaErrorCode
import io.github.nguyenphuc22.luaui.core.LuaCapabilities
import io.github.nguyenphuc22.luaui.core.LuaButtonNode
import io.github.nguyenphuc22.luaui.core.LuaNodeId
import io.github.nguyenphuc22.luaui.core.LuaProtocolJson
import io.github.nguyenphuc22.luaui.core.LuaScreenId
import io.github.nguyenphuc22.luaui.core.LuaScreenRequest
import io.github.nguyenphuc22.luaui.core.LuaScreenResponse
import io.github.nguyenphuc22.luaui.core.LuaTextNode
import io.github.nguyenphuc22.luaui.runtime.LuaScreenStore
import io.github.nguyenphuc22.luaui.runtime.LuaScreenStoreState
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotSame

class DashboardHttpIntegrationTest {
    @Test
    fun `HTTP screen load and submit return a validated full replacement`() = testApplication {
        application {
            dashboardModule(DashboardController())
        }
        val client = createClient {
            install(ContentNegotiation) {
                json(LuaProtocolJson)
            }
        }

        val initialResponse = client.post("/v1/screens/dashboard") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(
                LuaScreenRequest(
                    screenId = LuaScreenId("dashboard"),
                    clientCapabilities = LuaCapabilities.foundation,
                ),
            )
        }.body<LuaScreenResponse>()

        val screenStore = LuaScreenStore.loading(
            screenId = LuaScreenId("dashboard"),
            clientCapabilities = LuaCapabilities.foundation,
        )
        val initialState = screenStore.accept(initialResponse)
        val initialStore = assertIs<LuaScreenStoreState.Ready>(initialState.state).nodeStore
        val refreshNode = initialStore.find(LuaNodeId("dashboard.refresh"))?.node

        val refreshedResponse = client.post("/v1/actions") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(
                LuaActionRequest(
                    screenId = initialStore.screenId,
                    sourceNodeId = assertIs<LuaButtonNode>(refreshNode).id,
                    actionId = io.github.nguyenphuc22.luaui.core.LuaActionId("dashboard.refresh"),
                    clientCapabilities = LuaCapabilities.foundation,
                ),
            )
        }.body<LuaActionResponse>()

        val refreshedState = initialState.accept(refreshedResponse)
        val refreshedStore = assertIs<LuaScreenStoreState.Ready>(refreshedState.state).nodeStore
        val refreshedPrice = assertIs<LuaTextNode>(
            refreshedStore.find(LuaNodeId("dashboard.price"))?.node,
        )

        assertEquals("1501000 ₫", refreshedPrice.text)
        assertEquals(LuaNodeId("dashboard.refresh"), refreshNode?.id)
        assertNotSame(initialState, refreshedState)
        assertNotSame(initialStore, refreshedStore)
        assertEquals(
            "1500000 ₫",
            assertIs<LuaTextNode>(initialStore.find(LuaNodeId("dashboard.price"))?.node).text,
        )
        assertEquals(
            LuaNodeId("dashboard.refresh"),
            refreshedStore.find(LuaNodeId("dashboard.refresh"))?.node?.id,
        )
    }

    @Test
    fun `server reauthorizes action IDs instead of trusting the client`() = testApplication {
        application {
            dashboardModule(DashboardController())
        }
        val client = createClient {
            install(ContentNegotiation) {
                json(LuaProtocolJson)
            }
        }

        val response = client.post("/v1/actions") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(
                LuaActionRequest(
                    screenId = LuaScreenId("dashboard"),
                    sourceNodeId = LuaNodeId("dashboard.refresh"),
                    actionId = io.github.nguyenphuc22.luaui.core.LuaActionId("dashboard.delete_all"),
                    clientCapabilities = LuaCapabilities.foundation,
                ),
            )
        }.body<LuaActionResponse>()

        val failure = assertIs<LuaActionResponse.Failure>(response)
        assertEquals(LuaErrorCode.UNAUTHORIZED_ACTION, failure.error.code)
    }

    @Test
    fun `server rejects invalid screen requests with the same semantics as the Proto3 mapper`() = testApplication {
        application {
            dashboardModule(DashboardController())
        }
        val client = createClient {
            install(ContentNegotiation) {
                json(LuaProtocolJson)
            }
        }

        val response = client.post("/v1/screens/dashboard") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(
                LuaScreenRequest(
                    screenId = LuaScreenId("dashboard"),
                    clientCapabilities = setOf(LuaCapability(name = "", version = 0)),
                ),
            )
        }.body<LuaScreenResponse>()

        val failure = assertIs<LuaScreenResponse.Failure>(response)
        assertEquals(LuaErrorCode.INVALID_REQUEST, failure.error.code)
    }
}
