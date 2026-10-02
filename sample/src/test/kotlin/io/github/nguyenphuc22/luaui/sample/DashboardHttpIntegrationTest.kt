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
import io.github.nguyenphuc22.luaui.core.LuaTextFieldNode
import io.github.nguyenphuc22.luaui.core.LuaTextNode
import io.github.nguyenphuc22.luaui.runtime.LuaScreenSession
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
import kotlin.test.assertTrue

class DashboardHttpIntegrationTest {
    @Test
    fun `HTTP screen load submit and local draft return a validated full replacement`() = testApplication {
        application {
            dashboardModule(DashboardController())
        }
        val client = createClient {
            install(ContentNegotiation) {
                json(LuaProtocolJson)
            }
        }

        val clientCapabilities = LuaCapabilities.foundation + LuaCapabilities.textField
        val initialResponse = client.post("/v1/screens/dashboard") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(
                LuaScreenRequest(
                    screenId = LuaScreenId("dashboard"),
                    clientCapabilities = clientCapabilities,
                ),
            )
        }.body<LuaScreenResponse>()

        val session = LuaScreenSession.loading(
            screenId = LuaScreenId("dashboard"),
            clientCapabilities = clientCapabilities,
        )
        val initialSession = session.accept(initialResponse)
        val initialStore = assertIs<LuaScreenStoreState.Ready>(initialSession.screenStore.state).nodeStore
        val refreshNode = initialStore.find(LuaNodeId("dashboard.refresh"))?.node
        val filterNode = assertIs<LuaTextFieldNode>(
            initialStore.find(LuaNodeId("dashboard.filter"))?.node,
        )
        val editedSession = initialSession.updateTextField(filterNode.id, "rice")

        val refreshedResponse = client.post("/v1/actions") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(
                LuaActionRequest(
                    screenId = initialStore.screenId,
                    sourceNodeId = assertIs<LuaButtonNode>(refreshNode).id,
                    actionId = io.github.nguyenphuc22.luaui.core.LuaActionId("dashboard.refresh"),
                    clientCapabilities = clientCapabilities,
                ),
            )
        }.body<LuaActionResponse>()

        val refreshedSession = editedSession.accept(refreshedResponse)
        val refreshedStore = assertIs<LuaScreenStoreState.Ready>(refreshedSession.screenStore.state).nodeStore
        val refreshedPrice = assertIs<LuaTextNode>(
            refreshedStore.find(LuaNodeId("dashboard.price"))?.node,
        )
        val refreshedFilter = assertIs<LuaTextFieldNode>(
            refreshedStore.find(LuaNodeId("dashboard.filter"))?.node,
        )

        assertEquals("1501000 ₫", refreshedPrice.text)
        assertEquals("rice", refreshedSession.localStateStore.draftFor(refreshedFilter).value)
        assertTrue(refreshedSession.localStateStore.draftFor(refreshedFilter).isDirty)
        assertEquals(LuaNodeId("dashboard.refresh"), refreshNode?.id)
        assertNotSame(initialSession, refreshedSession)
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
    fun `server reports the TextField extension as incompatible to a foundation-only client`() = testApplication {
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
                    clientCapabilities = LuaCapabilities.foundation,
                ),
            )
        }.body<LuaScreenResponse>()

        val incompatible = assertIs<LuaScreenResponse.Incompatible>(response)
        assertEquals(setOf(LuaCapabilities.textField), incompatible.missingCapabilities)
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
