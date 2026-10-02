package io.github.nguyenphuc22.luaui.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

const val LUA_PROTOCOL_VERSION: Int = 1
const val LUA_SCHEMA_VERSION: Int = 1
const val LUA_MAX_PAYLOAD_BYTES: Int = 256 * 1024

@Serializable
data class LuaCapability(
    val name: String,
    val version: Int,
)

object LuaCapabilities {
    val column = LuaCapability(name = "component.column", version = 1)
    val text = LuaCapability(name = "component.text", version = 1)
    val button = LuaCapability(name = "component.button", version = 1)
    /**
     * Experimental, additive control capability. It intentionally is not part of [foundation],
     * so a Foundation 0.1 client never advertises a renderer it does not ship.
     */
    val textField = LuaCapability(name = "component.text_field", version = 1)
    val submit = LuaCapability(name = "action.submit", version = 1)

    val foundation: Set<LuaCapability> = setOf(column, text, button, submit)

    fun requiredBy(node: LuaNode): Set<LuaCapability> = buildSet {
        val visitedIds = mutableSetOf<LuaNodeId>()
        val pending = mutableListOf(node)

        while (pending.isNotEmpty()) {
            val current = pending.removeAt(pending.lastIndex)
            // A malformed in-memory tree can be cyclic through a MutableList. Validation will
            // report the duplicate ID; capability discovery must remain safe before that point.
            if (!visitedIds.add(current.id)) continue

            when (current) {
                is LuaColumnNode -> {
                    add(column)
                    current.children.asReversed().forEach { child -> pending += child }
                }

                is LuaTextNode -> add(text)
                is LuaTextFieldNode -> add(textField)
                is LuaButtonNode -> {
                    add(button)
                    addAll(requiredBy(current.onClick))
                }
            }
        }
    }

    fun requiredBy(action: LuaAction): Set<LuaCapability> = when (action) {
        is LuaAction.Submit -> setOf(submit)
    }
}

@Serializable
data class LuaScreen(
    val id: LuaScreenId,
    val protocolVersion: Int = LUA_PROTOCOL_VERSION,
    val schemaVersion: Int = LUA_SCHEMA_VERSION,
    val root: LuaNode,
    val requiredCapabilities: Set<LuaCapability>,
)

@Serializable
sealed interface LuaNode {
    val id: LuaNodeId
}

@Serializable
@SerialName("column")
data class LuaColumnNode(
    override val id: LuaNodeId,
    val children: List<LuaNode>,
) : LuaNode

@Serializable
@SerialName("text")
data class LuaTextNode(
    override val id: LuaNodeId,
    val text: String,
) : LuaNode

/**
 * A server-described input definition.
 *
 * The current value remains client-owned in the Runtime local-state layer. In particular, this
 * node carries no mutable draft, acknowledgement, or action-payload field.
 */
@Serializable
@SerialName("text_field")
data class LuaTextFieldNode(
    override val id: LuaNodeId,
    val label: String,
    val initialValue: String,
) : LuaNode

@Serializable
@SerialName("button")
data class LuaButtonNode(
    override val id: LuaNodeId,
    val label: String,
    val onClick: LuaAction,
) : LuaNode

@Serializable
sealed interface LuaAction {
    val actionId: LuaActionId

    @Serializable
    @SerialName("submit")
    data class Submit(
        override val actionId: LuaActionId,
    ) : LuaAction
}

@Serializable
data class LuaScreenRequest(
    val screenId: LuaScreenId,
    val clientCapabilities: Set<LuaCapability>,
)

@Serializable
data class LuaActionRequest(
    val screenId: LuaScreenId,
    val sourceNodeId: LuaNodeId,
    val actionId: LuaActionId,
    val clientCapabilities: Set<LuaCapability>,
)

@Serializable
sealed interface LuaScreenResponse {
    @Serializable
    @SerialName("screen")
    data class Screen(
        val screen: LuaScreen,
    ) : LuaScreenResponse

    @Serializable
    @SerialName("incompatible")
    data class Incompatible(
        val missingCapabilities: Set<LuaCapability>,
    ) : LuaScreenResponse

    @Serializable
    @SerialName("failure")
    data class Failure(
        val error: LuaError,
    ) : LuaScreenResponse
}

@Serializable
sealed interface LuaActionResponse {
    @Serializable
    @SerialName("screen")
    data class Screen(
        val screen: LuaScreen,
    ) : LuaActionResponse

    @Serializable
    @SerialName("failure")
    data class Failure(
        val error: LuaError,
    ) : LuaActionResponse
}

@Serializable
data class LuaError(
    val code: LuaErrorCode,
    val message: String,
)

@Serializable
enum class LuaErrorCode {
    INVALID_REQUEST,
    INVALID_SCREEN,
    INCOMPATIBLE_CLIENT,
    NOT_FOUND,
    UNAUTHORIZED_ACTION,
    TRANSPORT,
}
