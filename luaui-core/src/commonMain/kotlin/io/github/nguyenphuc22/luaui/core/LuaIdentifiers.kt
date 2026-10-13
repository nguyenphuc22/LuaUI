package io.github.nguyenphuc22.luaui.core

import kotlinx.serialization.Serializable

@JvmInline
@Serializable
value class LuaScreenId(val value: String)

@JvmInline
@Serializable
value class LuaNodeId(val value: String)

@JvmInline
@Serializable
value class LuaActionId(val value: String)

/**
 * An opaque client-generated ID for one stateful action submission.
 *
 * It correlates an explicit server acknowledgement with the exact local draft snapshot that
 * produced the request. It is not an idempotency key or an authorization credential.
 */
@JvmInline
@Serializable
value class LuaSubmissionId(val value: String)
