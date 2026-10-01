package io.github.nguyenphuc22.luaui.core

import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

class LuaProtocolValidationTest {
    @Test
    fun `valid foundation screen passes structural and capability validation`() {
        val result = LuaProtocolValidator.validateScreen(
            screen = dashboard(),
            clientCapabilities = LuaCapabilities.foundation,
        )

        assertIs<LuaScreenValidationResult.Valid>(result)
    }

    @Test
    fun `duplicate node IDs are rejected`() {
        val screen = dashboard(
            root = LuaColumnNode(
                id = LuaNodeId("dashboard.root"),
                children = listOf(
                    LuaTextNode(LuaNodeId("dashboard.value"), "First"),
                    LuaTextNode(LuaNodeId("dashboard.value"), "Second"),
                ),
            ),
        )

        val result = assertIs<LuaScreenValidationResult.Invalid>(
            LuaProtocolValidator.validateScreen(screen),
        )

        assertTrue(result.issues.any { issue -> issue.code == "duplicate_node_id" })
    }

    @Test
    fun `missing client capability is rejected before rendering`() {
        val result = assertIs<LuaScreenValidationResult.Invalid>(
            LuaProtocolValidator.validateScreen(
                screen = dashboard(),
                clientCapabilities = setOf(LuaCapabilities.column, LuaCapabilities.text),
            ),
        )

        assertTrue(result.issues.any { issue -> issue.code == "client_capability_missing" })
    }

    @Test
    fun `screen requests validate identifiers and client capabilities`() {
        val issues = LuaProtocolValidator.validateScreenRequest(
            LuaScreenRequest(
                screenId = LuaScreenId("invalid id"),
                clientCapabilities = setOf(LuaCapability("component.text", 0)),
            ),
        )

        assertTrue(issues.any { issue -> issue.code == "invalid_identifier" })
        assertTrue(issues.any { issue -> issue.code == "invalid_capability_version" })
    }

    private fun dashboard(root: LuaNode = defaultRoot()): LuaScreen = LuaScreen(
        id = LuaScreenId("dashboard"),
        root = root,
        requiredCapabilities = LuaCapabilities.requiredBy(root),
    )

    private fun defaultRoot(): LuaColumnNode = LuaColumnNode(
        id = LuaNodeId("dashboard.root"),
        children = listOf(
            LuaTextNode(LuaNodeId("dashboard.title"), "Dashboard"),
            LuaButtonNode(
                id = LuaNodeId("dashboard.refresh"),
                label = "Refresh",
                onClick = LuaAction.Submit(LuaActionId("dashboard.refresh")),
            ),
        ),
    )
}
