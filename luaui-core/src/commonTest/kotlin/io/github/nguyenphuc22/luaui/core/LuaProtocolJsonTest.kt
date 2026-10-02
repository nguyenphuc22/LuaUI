package io.github.nguyenphuc22.luaui.core

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlin.test.Test
import kotlin.test.assertEquals

class LuaProtocolJsonTest {
    @Test
    fun `screen survives strict JSON round trip`() {
        val screen = LuaScreen(
            id = LuaScreenId("dashboard"),
            root = LuaColumnNode(
                id = LuaNodeId("dashboard.root"),
                children = listOf(
                    LuaTextNode(LuaNodeId("dashboard.title"), "Dashboard"),
                    LuaButtonNode(
                        id = LuaNodeId("dashboard.refresh"),
                        label = "Refresh",
                        onClick = LuaAction.Submit(LuaActionId("dashboard.refresh")),
                    ),
                ),
            ),
            requiredCapabilities = LuaCapabilities.foundation,
        )

        val encoded = LuaProtocolJson.encodeToString(screen)
        val decoded = LuaProtocolJson.decodeFromString<LuaScreen>(encoded)

        assertEquals(screen, decoded)
    }

    @Test
    fun `TextField definition survives strict JSON round trip without a local draft`() {
        val screen = LuaScreen(
            id = LuaScreenId("dashboard"),
            root = LuaColumnNode(
                id = LuaNodeId("dashboard.root"),
                children = listOf(
                    LuaTextFieldNode(
                        id = LuaNodeId("dashboard.filter"),
                        label = "Filter",
                        initialValue = "all",
                    ),
                ),
            ),
            requiredCapabilities = setOf(LuaCapabilities.column, LuaCapabilities.textField),
        )

        val encoded = LuaProtocolJson.encodeToString(screen)
        val decoded = LuaProtocolJson.decodeFromString<LuaScreen>(encoded)

        assertEquals(screen, decoded)
    }
}
