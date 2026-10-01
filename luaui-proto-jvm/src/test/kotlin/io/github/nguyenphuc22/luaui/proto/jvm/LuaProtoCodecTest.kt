package io.github.nguyenphuc22.luaui.proto.jvm

import io.github.nguyenphuc22.luaui.core.LUA_MAX_PAYLOAD_BYTES
import io.github.nguyenphuc22.luaui.core.LuaAction
import io.github.nguyenphuc22.luaui.core.LuaActionId
import io.github.nguyenphuc22.luaui.core.LuaActionRequest
import io.github.nguyenphuc22.luaui.core.LuaActionResponse
import io.github.nguyenphuc22.luaui.core.LuaButtonNode
import io.github.nguyenphuc22.luaui.core.LuaCapabilities
import io.github.nguyenphuc22.luaui.core.LuaCapability
import io.github.nguyenphuc22.luaui.core.LuaColumnNode
import io.github.nguyenphuc22.luaui.core.LuaError
import io.github.nguyenphuc22.luaui.core.LuaErrorCode
import io.github.nguyenphuc22.luaui.core.LuaNodeId
import io.github.nguyenphuc22.luaui.core.LuaProtocolJson
import io.github.nguyenphuc22.luaui.core.LuaProtocolValidator
import io.github.nguyenphuc22.luaui.core.LuaScreen
import io.github.nguyenphuc22.luaui.core.LuaScreenId
import io.github.nguyenphuc22.luaui.core.LuaScreenRequest
import io.github.nguyenphuc22.luaui.core.LuaScreenResponse
import io.github.nguyenphuc22.luaui.core.LuaScreenValidationResult
import io.github.nguyenphuc22.luaui.core.LuaTextNode
import io.github.nguyenphuc22.luaui.proto.v1.ButtonNode as ProtoButtonNode
import io.github.nguyenphuc22.luaui.proto.v1.Capability as ProtoCapability
import io.github.nguyenphuc22.luaui.proto.v1.LuaError as ProtoLuaError
import io.github.nguyenphuc22.luaui.proto.v1.Node as ProtoNode
import io.github.nguyenphuc22.luaui.proto.v1.Screen as ProtoScreen
import io.github.nguyenphuc22.luaui.proto.v1.ScreenRequest as ProtoScreenRequest
import io.github.nguyenphuc22.luaui.proto.v1.ScreenResponse as ProtoScreenResponse
import java.util.Base64
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class LuaProtoCodecTest {
    @Test
    fun `dashboard JSON and Proto3 round trips retain the same semantics`() {
        val screen = dashboard()

        val jsonRoundTrip = LuaProtocolJson.decodeFromString<LuaScreen>(
            LuaProtocolJson.encodeToString(screen),
        )
        val protoPayload = LuaProtoCodec.encode(screen)
        val protoRoundTrip = LuaProtoCodec.decodeScreen(protoPayload)

        assertEquals(screen, jsonRoundTrip)
        assertEquals(screen, protoRoundTrip)
        assertIs<LuaScreenValidationResult.Valid>(LuaProtocolValidator.validateScreen(protoRoundTrip))
        assertEquals(
            "CglkYXNoYm9hcmQQARgBIrkBCg5kYXNoYm9hcmQucm9vdBKmAQokCg9kYXNoYm9hcmQudGl0bGUaEQoPTHVhVUkgRGFzaGJvYXJkCkQKEWRhc2hib2FyZC5jb250ZW50Ei8KLQoPZGFzaGJvYXJkLnByaWNlGhoKGEdpw6EgbMO6YTogMS41MDAuMDAwIOKCqwo4ChFkYXNoYm9hcmQucmVmcmVzaCIjCgpMw6BtIG3hu5tpEhUKEWRhc2hib2FyZC5yZWZyZXNoEgAqEQoNYWN0aW9uLnN1Ym1pdBABKhQKEGNvbXBvbmVudC5idXR0b24QASoUChBjb21wb25lbnQuY29sdW1uEAEqEgoOY29tcG9uZW50LnRleHQQAQ==",
            Base64.getEncoder().encodeToString(protoPayload),
        )
    }

    @Test
    fun `requests responses and error codes preserve their typed variants`() {
        val screen = dashboard()
        val screenRequest = LuaScreenRequest(
            screenId = screen.id,
            clientCapabilities = LuaCapabilities.foundation,
        )
        val actionRequest = LuaActionRequest(
            screenId = screen.id,
            sourceNodeId = LuaNodeId("dashboard.refresh"),
            actionId = LuaActionId("dashboard.refresh"),
            clientCapabilities = LuaCapabilities.foundation,
        )

        assertEquals(
            screenRequest,
            LuaProtoCodec.decodeScreenRequest(LuaProtoCodec.encode(screenRequest)),
        )
        assertEquals(
            actionRequest,
            LuaProtoCodec.decodeActionRequest(LuaProtoCodec.encode(actionRequest)),
        )

        val screenResponses = listOf(
            LuaScreenResponse.Screen(screen),
            LuaScreenResponse.Incompatible(setOf(LuaCapabilities.button)),
            LuaScreenResponse.Failure(LuaError(LuaErrorCode.INVALID_SCREEN, "Invalid screen")),
        )
        screenResponses.forEach { response ->
            assertEquals(response, LuaProtoCodec.decodeScreenResponse(LuaProtoCodec.encode(response)))
        }

        val actionResponses = listOf(
            LuaActionResponse.Screen(screen),
            LuaActionResponse.Failure(LuaError(LuaErrorCode.UNAUTHORIZED_ACTION, "Denied")),
        )
        actionResponses.forEach { response ->
            assertEquals(response, LuaProtoCodec.decodeActionResponse(LuaProtoCodec.encode(response)))
        }

        LuaErrorCode.entries.forEach { code ->
            val error = LuaError(code, "Message for $code")
            assertEquals(error, error.toProto().toLuaError())
        }
    }

    @Test
    fun `encoder rejects invalid incompatible capabilities`() {
        assertFailsWith<IllegalArgumentException> {
            LuaProtoCodec.encode(
                LuaScreenResponse.Incompatible(
                    missingCapabilities = setOf(LuaCapability(name = "", version = 0)),
                ),
            )
        }
    }

    @Test
    fun `decoder rejects unset variants unknown enum values and duplicate capability names`() {
        assertFailsWith<LuaProtoDecodingException> {
            ProtoNode.newBuilder().setId("dashboard.root").build().toLuaNode()
        }
        assertFailsWith<LuaProtoDecodingException> {
            ProtoNode.newBuilder()
                .setId("dashboard.refresh")
                .setButton(ProtoButtonNode.newBuilder().setLabel("Refresh"))
                .build()
                .toLuaNode()
        }
        assertFailsWith<LuaProtoDecodingException> {
            ProtoLuaError.newBuilder().setCodeValue(999).setMessage("Unknown").build().toLuaError()
        }
        assertFailsWith<LuaProtoDecodingException> {
            ProtoScreenRequest.newBuilder()
                .setScreenId("dashboard")
                .addClientCapabilities(
                    ProtoCapability.newBuilder().setName("component.text").setVersion(1),
                )
                .addClientCapabilities(
                    ProtoCapability.newBuilder().setName("component.text").setVersion(2),
                )
                .build()
                .toLuaScreenRequest()
        }
        assertFailsWith<LuaProtoDecodingException> {
            ProtoScreenResponse.getDefaultInstance().toLuaScreenResponse()
        }
        assertFailsWith<LuaProtoDecodingException> {
            LuaProtoCodec.decodeScreen(ByteArray(LUA_MAX_PAYLOAD_BYTES + 1))
        }
    }

    @Test
    fun `descriptor tags and oneof membership stay stable`() {
        assertEquals(1, ProtoScreen.getDescriptor().findFieldByName("id").number)
        assertEquals(4, ProtoScreen.getDescriptor().findFieldByName("root").number)
        assertEquals(
            setOf("column", "text", "button"),
            ProtoNode.getDescriptor()
                .oneofs
                .single { descriptor -> descriptor.name == "kind" }
                .fields
                .map { field -> field.name }
                .toSet(),
        )
    }

    private fun dashboard(): LuaScreen {
        val root = LuaColumnNode(
            id = LuaNodeId("dashboard.root"),
            children = listOf(
                LuaTextNode(LuaNodeId("dashboard.title"), "LuaUI Dashboard"),
                LuaColumnNode(
                    id = LuaNodeId("dashboard.content"),
                    children = listOf(
                        LuaTextNode(LuaNodeId("dashboard.price"), "Giá lúa: 1.500.000 ₫"),
                    ),
                ),
                LuaButtonNode(
                    id = LuaNodeId("dashboard.refresh"),
                    label = "Làm mới",
                    onClick = LuaAction.Submit(LuaActionId("dashboard.refresh")),
                ),
            ),
        )
        return LuaScreen(
            id = LuaScreenId("dashboard"),
            root = root,
            requiredCapabilities = LuaCapabilities.requiredBy(root),
        )
    }
}
