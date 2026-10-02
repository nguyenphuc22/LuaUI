package io.github.nguyenphuc22.luaui.proto.jvm

import com.google.protobuf.InvalidProtocolBufferException
import com.google.protobuf.MessageLite
import io.github.nguyenphuc22.luaui.core.LUA_MAX_PAYLOAD_BYTES
import io.github.nguyenphuc22.luaui.core.LuaAction
import io.github.nguyenphuc22.luaui.core.LuaActionId
import io.github.nguyenphuc22.luaui.core.LuaActionRequest
import io.github.nguyenphuc22.luaui.core.LuaActionResponse
import io.github.nguyenphuc22.luaui.core.LuaButtonNode
import io.github.nguyenphuc22.luaui.core.LuaCapability
import io.github.nguyenphuc22.luaui.core.LuaColumnNode
import io.github.nguyenphuc22.luaui.core.LuaError
import io.github.nguyenphuc22.luaui.core.LuaErrorCode
import io.github.nguyenphuc22.luaui.core.LuaNode
import io.github.nguyenphuc22.luaui.core.LuaNodeId
import io.github.nguyenphuc22.luaui.core.LuaProtocolValidator
import io.github.nguyenphuc22.luaui.core.LuaScreen
import io.github.nguyenphuc22.luaui.core.LuaScreenId
import io.github.nguyenphuc22.luaui.core.LuaScreenRequest
import io.github.nguyenphuc22.luaui.core.LuaScreenResponse
import io.github.nguyenphuc22.luaui.core.LuaScreenValidationResult
import io.github.nguyenphuc22.luaui.core.LuaTextFieldNode
import io.github.nguyenphuc22.luaui.core.LuaTextNode
import io.github.nguyenphuc22.luaui.proto.v1.Action as ProtoAction
import io.github.nguyenphuc22.luaui.proto.v1.ActionRequest as ProtoActionRequest
import io.github.nguyenphuc22.luaui.proto.v1.ActionResponse as ProtoActionResponse
import io.github.nguyenphuc22.luaui.proto.v1.ButtonNode as ProtoButtonNode
import io.github.nguyenphuc22.luaui.proto.v1.Capability as ProtoCapability
import io.github.nguyenphuc22.luaui.proto.v1.ColumnNode as ProtoColumnNode
import io.github.nguyenphuc22.luaui.proto.v1.ErrorCode as ProtoErrorCode
import io.github.nguyenphuc22.luaui.proto.v1.IncompatibleClient as ProtoIncompatibleClient
import io.github.nguyenphuc22.luaui.proto.v1.LuaError as ProtoLuaError
import io.github.nguyenphuc22.luaui.proto.v1.Node as ProtoNode
import io.github.nguyenphuc22.luaui.proto.v1.Screen as ProtoScreen
import io.github.nguyenphuc22.luaui.proto.v1.ScreenRequest as ProtoScreenRequest
import io.github.nguyenphuc22.luaui.proto.v1.ScreenResponse as ProtoScreenResponse
import io.github.nguyenphuc22.luaui.proto.v1.SubmitAction as ProtoSubmitAction
import io.github.nguyenphuc22.luaui.proto.v1.TextFieldNode as ProtoTextFieldNode
import io.github.nguyenphuc22.luaui.proto.v1.TextNode as ProtoTextNode

/** Raised when a Proto3 payload cannot be safely represented by the LuaUI core model. */
class LuaProtoDecodingException(
    message: String,
    cause: Throwable? = null,
) : IllegalArgumentException(message, cause)

/**
 * JVM-only binary codec for the Foundation 0.1 Proto3 schema.
 *
 * The common core stays serialization-agnostic. Every decoded screen is structurally validated
 * before it is returned, and every encoded screen is validated before bytes are emitted.
 */
object LuaProtoCodec {
    fun encode(screen: LuaScreen): ByteArray = encodeMessage(screen.toProto())

    fun decodeScreen(payload: ByteArray): LuaScreen =
        decodeMessage(payload) { bytes -> ProtoScreen.parseFrom(bytes) }.toLuaScreen()

    fun encode(request: LuaScreenRequest): ByteArray = encodeMessage(request.toProto())

    fun decodeScreenRequest(payload: ByteArray): LuaScreenRequest =
        decodeMessage(payload) { bytes -> ProtoScreenRequest.parseFrom(bytes) }.toLuaScreenRequest()

    fun encode(request: LuaActionRequest): ByteArray = encodeMessage(request.toProto())

    fun decodeActionRequest(payload: ByteArray): LuaActionRequest =
        decodeMessage(payload) { bytes -> ProtoActionRequest.parseFrom(bytes) }.toLuaActionRequest()

    fun encode(response: LuaScreenResponse): ByteArray = encodeMessage(response.toProto())

    fun decodeScreenResponse(payload: ByteArray): LuaScreenResponse =
        decodeMessage(payload) { bytes -> ProtoScreenResponse.parseFrom(bytes) }.toLuaScreenResponse()

    fun encode(response: LuaActionResponse): ByteArray = encodeMessage(response.toProto())

    fun decodeActionResponse(payload: ByteArray): LuaActionResponse =
        decodeMessage(payload) { bytes -> ProtoActionResponse.parseFrom(bytes) }.toLuaActionResponse()

    private fun encodeMessage(message: MessageLite): ByteArray = message.toByteArray().also { payload ->
        require(payload.size <= LUA_MAX_PAYLOAD_BYTES) {
            "LuaUI Proto3 payload exceeds the $LUA_MAX_PAYLOAD_BYTES-byte limit."
        }
    }

    private fun <T> decodeMessage(
        payload: ByteArray,
        decode: (ByteArray) -> T,
    ): T {
        if (payload.size > LUA_MAX_PAYLOAD_BYTES) {
            throw LuaProtoDecodingException(
                "LuaUI Proto3 payload exceeds the $LUA_MAX_PAYLOAD_BYTES-byte limit.",
            )
        }

        return try {
            decode(payload)
        } catch (exception: InvalidProtocolBufferException) {
            throw LuaProtoDecodingException("LuaUI Proto3 payload is malformed.", exception)
        }
    }
}

fun LuaScreen.toProto(): ProtoScreen {
    requireValidScreen(this, phase = "encode")
    return ProtoScreen.newBuilder()
        .setId(id.value)
        .setProtocolVersion(protocolVersion)
        .setSchemaVersion(schemaVersion)
        .setRoot(root.toProto())
        .addAllRequiredCapabilities(requiredCapabilities.sortedForProto().map { it.toProto() })
        .build()
}

fun ProtoScreen.toLuaScreen(): LuaScreen {
    if (!hasRoot()) malformed("Screen.root must be present.")

    return LuaScreen(
        id = LuaScreenId(getId()),
        protocolVersion = getProtocolVersion(),
        schemaVersion = getSchemaVersion(),
        root = getRoot().toLuaNode(),
        requiredCapabilities = getRequiredCapabilitiesList().toLuaCapabilities(
            path = "screen.requiredCapabilities",
        ),
    ).also { screen -> requireValidScreen(screen, phase = "decode") }
}

fun LuaNode.toProto(): ProtoNode = ProtoNode.newBuilder()
    .setId(id.value)
    .apply {
        when (this@toProto) {
            is LuaColumnNode -> setColumn(
                ProtoColumnNode.newBuilder()
                    .addAllChildren(this@toProto.children.map { child -> child.toProto() })
                    .build(),
            )

            is LuaTextNode -> setText(
                ProtoTextNode.newBuilder()
                    .setText(this@toProto.text)
                    .build(),
            )

            is LuaTextFieldNode -> setTextField(
                ProtoTextFieldNode.newBuilder()
                    .setLabel(this@toProto.label)
                    .setInitialValue(this@toProto.initialValue)
                    .build(),
            )

            is LuaButtonNode -> setButton(
                ProtoButtonNode.newBuilder()
                    .setLabel(this@toProto.label)
                    .setOnClick(this@toProto.onClick.toProto())
                    .build(),
            )
        }
    }
    .build()

fun ProtoNode.toLuaNode(): LuaNode = when (getKindCase()) {
    ProtoNode.KindCase.COLUMN -> LuaColumnNode(
        id = LuaNodeId(getId()),
        children = getColumn().getChildrenList().map { child -> child.toLuaNode() },
    )

    ProtoNode.KindCase.TEXT -> LuaTextNode(
        id = LuaNodeId(getId()),
        text = getText().getText(),
    )

    ProtoNode.KindCase.TEXT_FIELD -> LuaTextFieldNode(
        id = LuaNodeId(getId()),
        label = getTextField().getLabel(),
        initialValue = getTextField().getInitialValue(),
    )

    ProtoNode.KindCase.BUTTON -> {
        val button = getButton()
        if (!button.hasOnClick()) malformed("ButtonNode.on_click must be present.")
        LuaButtonNode(
            id = LuaNodeId(getId()),
            label = button.getLabel(),
            onClick = button.getOnClick().toLuaAction(),
        )
    }

    ProtoNode.KindCase.KIND_NOT_SET,
    null,
    -> malformed("Node.kind must be set to a supported variant.")
}

fun LuaAction.toProto(): ProtoAction = ProtoAction.newBuilder()
    .setActionId(actionId.value)
    .apply {
        when (this@toProto) {
            is LuaAction.Submit -> setSubmit(ProtoSubmitAction.getDefaultInstance())
        }
    }
    .build()

fun ProtoAction.toLuaAction(): LuaAction = when (getKindCase()) {
    ProtoAction.KindCase.SUBMIT -> LuaAction.Submit(LuaActionId(getActionId()))
    ProtoAction.KindCase.KIND_NOT_SET,
    null,
    -> malformed("Action.kind must be set to a supported variant.")
}

fun LuaScreenRequest.toProto(): ProtoScreenRequest {
    requireValidScreenRequest(this, phase = "encode")
    return ProtoScreenRequest.newBuilder()
        .setScreenId(screenId.value)
        .addAllClientCapabilities(clientCapabilities.sortedForProto().map { it.toProto() })
        .build()
}

fun ProtoScreenRequest.toLuaScreenRequest(): LuaScreenRequest = LuaScreenRequest(
    screenId = LuaScreenId(getScreenId()),
    clientCapabilities = getClientCapabilitiesList().toLuaCapabilities(
        path = "screenRequest.clientCapabilities",
    ),
).also { request -> requireValidScreenRequest(request, phase = "decode") }

fun LuaActionRequest.toProto(): ProtoActionRequest {
    requireValidActionRequest(this, phase = "encode")
    return ProtoActionRequest.newBuilder()
        .setScreenId(screenId.value)
        .setSourceNodeId(sourceNodeId.value)
        .setActionId(actionId.value)
        .addAllClientCapabilities(clientCapabilities.sortedForProto().map { it.toProto() })
        .build()
}

fun ProtoActionRequest.toLuaActionRequest(): LuaActionRequest = LuaActionRequest(
    screenId = LuaScreenId(getScreenId()),
    sourceNodeId = LuaNodeId(getSourceNodeId()),
    actionId = LuaActionId(getActionId()),
    clientCapabilities = getClientCapabilitiesList().toLuaCapabilities(
        path = "action.clientCapabilities",
    ),
).also { request -> requireValidActionRequest(request, phase = "decode") }

fun LuaScreenResponse.toProto(): ProtoScreenResponse = ProtoScreenResponse.newBuilder()
    .apply {
        when (this@toProto) {
            is LuaScreenResponse.Screen -> setScreen(this@toProto.screen.toProto())
            is LuaScreenResponse.Incompatible -> setIncompatible(
                ProtoIncompatibleClient.newBuilder()
                    .addAllMissingCapabilities(
                        this@toProto.missingCapabilities
                            .sortedForProto()
                            .map { capability -> capability.toProto() },
                    )
                    .build(),
            )

            is LuaScreenResponse.Failure -> setFailure(this@toProto.error.toProto())
        }
    }
    .build()

fun ProtoScreenResponse.toLuaScreenResponse(): LuaScreenResponse = when (getResultCase()) {
    ProtoScreenResponse.ResultCase.SCREEN -> LuaScreenResponse.Screen(getScreen().toLuaScreen())
    ProtoScreenResponse.ResultCase.INCOMPATIBLE -> LuaScreenResponse.Incompatible(
        getIncompatible().getMissingCapabilitiesList().toLuaCapabilities(
            path = "screenResponse.incompatible.missingCapabilities",
        ),
    )

    ProtoScreenResponse.ResultCase.FAILURE -> LuaScreenResponse.Failure(getFailure().toLuaError())
    ProtoScreenResponse.ResultCase.RESULT_NOT_SET,
    null,
    -> malformed("ScreenResponse.result must be set to a supported variant.")
}

fun LuaActionResponse.toProto(): ProtoActionResponse = ProtoActionResponse.newBuilder()
    .apply {
        when (this@toProto) {
            is LuaActionResponse.Screen -> setScreen(this@toProto.screen.toProto())
            is LuaActionResponse.Failure -> setFailure(this@toProto.error.toProto())
        }
    }
    .build()

fun ProtoActionResponse.toLuaActionResponse(): LuaActionResponse = when (getResultCase()) {
    ProtoActionResponse.ResultCase.SCREEN -> LuaActionResponse.Screen(getScreen().toLuaScreen())
    ProtoActionResponse.ResultCase.FAILURE -> LuaActionResponse.Failure(getFailure().toLuaError())
    ProtoActionResponse.ResultCase.RESULT_NOT_SET,
    null,
    -> malformed("ActionResponse.result must be set to a supported variant.")
}

fun LuaCapability.toProto(): ProtoCapability = ProtoCapability.newBuilder()
    .setName(name)
    .setVersion(version)
    .build()

fun ProtoCapability.toLuaCapability(): LuaCapability = LuaCapability(
    name = getName(),
    version = getVersion(),
)

fun LuaError.toProto(): ProtoLuaError = ProtoLuaError.newBuilder()
    .setCode(code.toProtoErrorCode())
    .setMessage(message)
    .build()

fun ProtoLuaError.toLuaError(): LuaError = LuaError(
    code = getCode().toLuaErrorCode(),
    message = getMessage(),
)

private fun LuaErrorCode.toProtoErrorCode(): ProtoErrorCode = when (this) {
    LuaErrorCode.INVALID_REQUEST -> ProtoErrorCode.ERROR_CODE_INVALID_REQUEST
    LuaErrorCode.INVALID_SCREEN -> ProtoErrorCode.ERROR_CODE_INVALID_SCREEN
    LuaErrorCode.INCOMPATIBLE_CLIENT -> ProtoErrorCode.ERROR_CODE_INCOMPATIBLE_CLIENT
    LuaErrorCode.NOT_FOUND -> ProtoErrorCode.ERROR_CODE_NOT_FOUND
    LuaErrorCode.UNAUTHORIZED_ACTION -> ProtoErrorCode.ERROR_CODE_UNAUTHORIZED_ACTION
    LuaErrorCode.TRANSPORT -> ProtoErrorCode.ERROR_CODE_TRANSPORT
}

private fun ProtoErrorCode.toLuaErrorCode(): LuaErrorCode = when (this) {
    ProtoErrorCode.ERROR_CODE_INVALID_REQUEST -> LuaErrorCode.INVALID_REQUEST
    ProtoErrorCode.ERROR_CODE_INVALID_SCREEN -> LuaErrorCode.INVALID_SCREEN
    ProtoErrorCode.ERROR_CODE_INCOMPATIBLE_CLIENT -> LuaErrorCode.INCOMPATIBLE_CLIENT
    ProtoErrorCode.ERROR_CODE_NOT_FOUND -> LuaErrorCode.NOT_FOUND
    ProtoErrorCode.ERROR_CODE_UNAUTHORIZED_ACTION -> LuaErrorCode.UNAUTHORIZED_ACTION
    ProtoErrorCode.ERROR_CODE_TRANSPORT -> LuaErrorCode.TRANSPORT
    ProtoErrorCode.ERROR_CODE_UNSPECIFIED,
    ProtoErrorCode.UNRECOGNIZED,
    -> malformed("LuaError.code must be a supported LuaUI error code.")
}

private fun Iterable<ProtoCapability>.toLuaCapabilities(path: String): Set<LuaCapability> {
    val capabilities = map { capability -> capability.toLuaCapability() }
    if (capabilities.groupBy { capability -> capability.name }.any { (_, grouped) -> grouped.size > 1 }) {
        malformed("$path contains the same capability name more than once.")
    }
    return capabilities.toSet().also { decoded ->
        if (LuaProtocolValidator.validateCapabilities(decoded, path).isNotEmpty()) {
            malformed("$path contains an invalid capability.")
        }
    }
}

private fun Collection<LuaCapability>.sortedForProto(): List<LuaCapability> {
    if (groupBy { capability -> capability.name }.any { (_, grouped) -> grouped.size > 1 }) {
        throw IllegalArgumentException("A LuaUI capability name may appear only once in a Proto3 payload.")
    }
    if (LuaProtocolValidator.validateCapabilities(toSet()).isNotEmpty()) {
        throw IllegalArgumentException("A Proto3 capability must have a valid name and positive version.")
    }
    return sortedWith(compareBy<LuaCapability> { capability -> capability.name }.thenBy { capability -> capability.version })
}

private fun requireValidScreen(screen: LuaScreen, phase: String) {
    if (LuaProtocolValidator.validateScreen(screen) is LuaScreenValidationResult.Invalid) {
        throw LuaProtoDecodingException("Cannot $phase an invalid LuaUI screen as Proto3.")
    }
}

private fun requireValidScreenRequest(request: LuaScreenRequest, phase: String) {
    if (LuaProtocolValidator.validateScreenRequest(request).isNotEmpty()) {
        throw LuaProtoDecodingException("Cannot $phase an invalid LuaUI screen request as Proto3.")
    }
}

private fun requireValidActionRequest(request: LuaActionRequest, phase: String) {
    if (LuaProtocolValidator.validateActionRequest(request).isNotEmpty()) {
        throw LuaProtoDecodingException("Cannot $phase an invalid LuaUI action request as Proto3.")
    }
}

private fun malformed(message: String): Nothing = throw LuaProtoDecodingException(message)
