package io.github.nguyenphuc22.luaui.sample

import io.github.nguyenphuc22.luaui.core.LuaActionRequest
import io.github.nguyenphuc22.luaui.core.LuaActionResponse
import io.github.nguyenphuc22.luaui.core.LuaCapability
import io.github.nguyenphuc22.luaui.core.LuaErrorCode
import io.github.nguyenphuc22.luaui.core.LuaCapabilities
import io.github.nguyenphuc22.luaui.core.LuaColumnNode
import io.github.nguyenphuc22.luaui.core.LuaNodeId
import io.github.nguyenphuc22.luaui.core.LuaProtocolJson
import io.github.nguyenphuc22.luaui.core.LuaScreenId
import io.github.nguyenphuc22.luaui.core.LuaScreenRequest
import io.github.nguyenphuc22.luaui.core.LuaScreenResponse
import io.github.nguyenphuc22.luaui.core.LuaTextNode
import io.github.nguyenphuc22.luaui.runtime.LuaNodeStore
import io.github.nguyenphuc22.luaui.runtime.LuaNodeStoreCreation
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

        val initialScreen = assertIs<LuaScreenResponse.Screen>(initialResponse).screen
        val initialStore = assertIs<LuaNodeStoreCreation.Ready>(
            LuaNodeStore.create(initialScreen, LuaCapabilities.foundation),
        ).store
        val refreshNode = assertIs<LuaColumnNode>(initialScreen.root).children.last()

        val refreshedResponse = client.post("/v1/actions") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(
                LuaActionRequest(
                    screenId = initialScreen.id,
                    sourceNodeId = refreshNode.id,
                    actionId = io.github.nguyenphuc22.luaui.core.LuaActionId("dashboard.refresh"),
                    clientCapabilities = LuaCapabilities.foundation,
                ),
            )
        }.body<LuaActionResponse>()

        val refreshedScreen = assertIs<LuaActionResponse.Screen>(refreshedResponse).screen
        val refreshedStore = assertIs<LuaNodeStoreCreation.Ready>(
            LuaNodeStore.create(refreshedScreen, LuaCapabilities.foundation),
        ).store
        val refreshedPrice = assertIs<LuaTextNode>(
            assertIs<LuaColumnNode>(refreshedScreen.root).children[1],
        )

        assertEquals("1501000 ₫", refreshedPrice.text)
        assertEquals(LuaNodeId("dashboard.refresh"), refreshNode.id)
        assertNotSame(initialStore, refreshedStore)
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
