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
