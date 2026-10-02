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
    fun `cyclic in-memory trees are rejected without recursive overflow`() {
        val children = mutableListOf<LuaNode>()
        val root = LuaColumnNode(
            id = LuaNodeId("dashboard.root"),
            children = children,
        )
        children += root
        val screen = LuaScreen(
            id = LuaScreenId("dashboard"),
            root = root,
            requiredCapabilities = LuaCapabilities.requiredBy(root),
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
    fun `TextField is capability gated and validates its server-provided baseline`() {
        val root = LuaColumnNode(
            id = LuaNodeId("dashboard.root"),
            children = listOf(
                LuaTextFieldNode(
                    id = LuaNodeId("dashboard.filter"),
                    label = "Filter",
                    initialValue = "all",
                ),
            ),
        )
        val screen = dashboard(root)

        assertIs<LuaScreenValidationResult.Valid>(
            LuaProtocolValidator.validateScreen(
                screen = screen,
                clientCapabilities = LuaCapabilities.foundation + LuaCapabilities.textField,
            ),
        )
        val missingCapability = assertIs<LuaScreenValidationResult.Invalid>(
            LuaProtocolValidator.validateScreen(
                screen = screen,
                clientCapabilities = LuaCapabilities.foundation,
            ),
        )
        assertTrue(missingCapability.issues.any { issue -> issue.code == "client_capability_missing" })

        val tooLong = dashboard(
            root = root.copy(
                children = listOf(
                    LuaTextFieldNode(
                        id = LuaNodeId("dashboard.filter"),
                        label = "x".repeat(3),
                        initialValue = "x".repeat(5),
                    ),
                ),
            ),
        )
        val constrained = assertIs<LuaScreenValidationResult.Invalid>(
            LuaProtocolValidator.validateScreen(
                screen = tooLong,
                clientCapabilities = LuaCapabilities.foundation + LuaCapabilities.textField,
                limits = LuaProtocolLimits(
                    maxTextFieldLabelLength = 2,
                    maxTextFieldInitialValueLength = 4,
                ),
            ),
        )
        assertTrue(constrained.issues.any { issue -> issue.code == "text_field_label_length_exceeded" })
        assertTrue(
            constrained.issues.any { issue -> issue.code == "text_field_initial_value_length_exceeded" },
        )
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
