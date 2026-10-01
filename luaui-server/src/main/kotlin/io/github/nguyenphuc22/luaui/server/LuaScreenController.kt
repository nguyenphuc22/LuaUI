package io.github.nguyenphuc22.luaui.server

import io.github.nguyenphuc22.luaui.core.LuaActionRequest
import io.github.nguyenphuc22.luaui.core.LuaActionResponse
import io.github.nguyenphuc22.luaui.core.LuaScreenRequest
import io.github.nguyenphuc22.luaui.core.LuaScreenResponse

interface LuaScreenController {
    suspend fun load(request: LuaScreenRequest): LuaScreenResponse

    suspend fun dispatch(request: LuaActionRequest): LuaActionResponse
}
