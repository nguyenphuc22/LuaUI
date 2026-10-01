package io.github.nguyenphuc22.luaui.core

import kotlinx.serialization.json.Json

val LuaProtocolJson: Json = Json {
    classDiscriminator = "type"
    encodeDefaults = true
    ignoreUnknownKeys = false
}
