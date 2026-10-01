package io.github.nguyenphuc22.luaui.server

import io.github.nguyenphuc22.luaui.core.LuaCapabilities
import io.github.nguyenphuc22.luaui.core.LuaProtocolValidator
import io.github.nguyenphuc22.luaui.core.LuaScreenValidationResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class LuaScreenDslTest {
    @Test
    fun `DSL builds a validated foundation screen`() {
        val screen = luaScreen("dashboard") {
            column("dashboard.root") {
                text("dashboard.title", "Dashboard")
                button("dashboard.refresh", "Refresh", submit("dashboard.refresh"))
            }
        }

        assertEquals(LuaCapabilities.foundation, screen.requiredCapabilities)
        assertIs<LuaScreenValidationResult.Valid>(
            LuaProtocolValidator.validateScreen(screen, LuaCapabilities.foundation),
        )
    }
}
