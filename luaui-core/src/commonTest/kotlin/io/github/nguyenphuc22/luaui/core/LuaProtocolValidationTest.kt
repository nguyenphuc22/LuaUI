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

    @Test
    fun `SubmitTextFields is capability gated and may reference only declared TextFields`() {
        val root = LuaColumnNode(
            id = LuaNodeId("dashboard.root"),
            children = listOf(
                LuaTextFieldNode(LuaNodeId("dashboard.filter"), "Filter", ""),
                LuaButtonNode(
                    id = LuaNodeId("dashboard.apply"),
                    label = "Apply",
                    onClick = LuaAction.SubmitTextFields(
                        actionId = LuaActionId("dashboard.apply"),
                        textFieldNodeIds = listOf(LuaNodeId("dashboard.filter")),
                    ),
                ),
            ),
        )
        val screen = dashboard(root)
        val supportedCapabilities = LuaCapabilities.foundation + setOf(
            LuaCapabilities.textField,
            LuaCapabilities.submitTextFields,
        )

        assertIs<LuaScreenValidationResult.Valid>(
            LuaProtocolValidator.validateScreen(screen, supportedCapabilities),
        )
        val missingActionCapability = assertIs<LuaScreenValidationResult.Invalid>(
            LuaProtocolValidator.validateScreen(
                screen,
                LuaCapabilities.foundation + LuaCapabilities.textField,
            ),
        )
        assertTrue(
            missingActionCapability.issues.any { issue -> issue.code == "client_capability_missing" },
        )

        val invalidRoot = root.copy(
            children = root.children.dropLast(1) + LuaButtonNode(
                id = LuaNodeId("dashboard.apply"),
                label = "Apply",
                onClick = LuaAction.SubmitTextFields(
                    actionId = LuaActionId("dashboard.apply"),
                    textFieldNodeIds = listOf(
                        LuaNodeId("dashboard.filter"),
                        LuaNodeId("dashboard.filter"),
                        LuaNodeId("dashboard.missing"),
                        LuaNodeId("dashboard.apply"),
                    ),
                ),
            ),
        )
        val invalid = assertIs<LuaScreenValidationResult.Invalid>(
            LuaProtocolValidator.validateScreen(dashboard(invalidRoot)),
        )

        assertTrue(invalid.issues.any { issue -> issue.code == "duplicate_submit_text_field_node_id" })
        assertTrue(invalid.issues.any { issue -> issue.code == "submit_text_fields_missing_node" })
        assertTrue(
            invalid.issues.any { issue -> issue.code == "submit_text_fields_requires_text_field" },
        )
    }

    @Test
    fun `TextField submission and acknowledgement validate bounded typed fields`() {
        val request = LuaActionRequest(
            screenId = LuaScreenId("dashboard"),
            sourceNodeId = LuaNodeId("dashboard.apply"),
            actionId = LuaActionId("dashboard.apply"),
            clientCapabilities = LuaCapabilities.foundation + LuaCapabilities.submitTextFields,
            textFieldSubmission = LuaTextFieldSubmission(
                submissionId = LuaSubmissionId("invalid id"),
                fields = listOf(
                    LuaSubmittedTextField(LuaNodeId("dashboard.filter"), "long", draftRevision = -1),
                    LuaSubmittedTextField(LuaNodeId("dashboard.filter"), "again", draftRevision = 0),
                ),
            ),
        )

        val requestIssues = LuaProtocolValidator.validateActionRequest(
            request,
            limits = LuaProtocolLimits(maxSubmittedTextFieldValueLength = 3),
        )
        assertTrue(requestIssues.any { issue -> issue.code == "invalid_identifier" })
        assertTrue(requestIssues.any { issue -> issue.code == "duplicate_submitted_field_node_id" })
        assertTrue(
            requestIssues.any { issue -> issue.code == "submitted_text_field_value_length_exceeded" },
        )
        assertTrue(requestIssues.any { issue -> issue.code == "negative_submitted_draft_revision" })

        val acknowledgementIssues = LuaProtocolValidator.validateTextFieldAcknowledgement(
            LuaTextFieldAcknowledgement(
                submissionId = LuaSubmissionId("dashboard.submit.1"),
                fields = listOf(
                    LuaAcknowledgedTextField(LuaNodeId("dashboard.filter"), draftRevision = -1),
                    LuaAcknowledgedTextField(LuaNodeId("dashboard.filter"), draftRevision = 0),
                ),
            ),
        )
        assertTrue(
            acknowledgementIssues.any { issue -> issue.code == "duplicate_acknowledged_field_node_id" },
        )
        assertTrue(
            acknowledgementIssues.any { issue -> issue.code == "negative_acknowledged_draft_revision" },
        )
    }

    @Test
    fun `AcknowledgedScreen requires both TextField extension capabilities from a client`() {
        val response = LuaActionResponse.AcknowledgedScreen(
            screen = dashboard(),
            acknowledgement = LuaTextFieldAcknowledgement(
                submissionId = LuaSubmissionId("dashboard.submit.1"),
                fields = listOf(
                    LuaAcknowledgedTextField(LuaNodeId("dashboard.filter"), draftRevision = 0),
                ),
            ),
        )

        val foundationOnly = LuaProtocolValidator.validateActionResponse(
            response = response,
            clientCapabilities = LuaCapabilities.foundation,
        )
        val missingTextField = LuaProtocolValidator.validateActionResponse(
            response = response,
            clientCapabilities = LuaCapabilities.foundation + LuaCapabilities.submitTextFields,
        )
        val supported = LuaProtocolValidator.validateActionResponse(
            response = response,
            clientCapabilities = LuaCapabilities.foundation + setOf(
                LuaCapabilities.textField,
                LuaCapabilities.submitTextFields,
            ),
        )

        assertTrue(
            foundationOnly.any { issue -> issue.code == "acknowledged_screen_client_capability_missing" },
        )
        assertTrue(
            missingTextField.any { issue -> issue.code == "acknowledged_screen_client_capability_missing" },
        )
        assertTrue(supported.isEmpty())
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
