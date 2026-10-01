package io.github.nguyenphuc22.luaui.transport

import io.github.nguyenphuc22.luaui.core.LuaActionRequest
import io.github.nguyenphuc22.luaui.core.LuaActionResponse
import io.github.nguyenphuc22.luaui.core.LuaScreenRequest
import io.github.nguyenphuc22.luaui.core.LuaScreenResponse

interface LuaTransport {
    suspend fun loadScreen(request: LuaScreenRequest): LuaScreenResponse

    suspend fun dispatchAction(request: LuaActionRequest): LuaActionResponse
}
