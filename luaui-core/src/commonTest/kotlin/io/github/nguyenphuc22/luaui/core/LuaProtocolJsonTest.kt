package io.github.nguyenphuc22.luaui.core

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

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

    @Test
    fun `typed TextField submission and acknowledged screen survive strict JSON round trips`() {
        val screen = LuaScreen(
            id = LuaScreenId("dashboard"),
            root = LuaColumnNode(
                id = LuaNodeId("dashboard.root"),
                children = listOf(
                    LuaTextFieldNode(LuaNodeId("dashboard.filter"), "Filter", "rice"),
                    LuaButtonNode(
                        id = LuaNodeId("dashboard.apply"),
                        label = "Apply",
                        onClick = LuaAction.SubmitTextFields(
                            actionId = LuaActionId("dashboard.apply"),
                            textFieldNodeIds = listOf(LuaNodeId("dashboard.filter")),
                        ),
                    ),
                ),
            ),
            requiredCapabilities = setOf(
                LuaCapabilities.column,
                LuaCapabilities.button,
                LuaCapabilities.textField,
                LuaCapabilities.submitTextFields,
            ),
        )
        val request = LuaActionRequest(
            screenId = screen.id,
            sourceNodeId = LuaNodeId("dashboard.apply"),
            actionId = LuaActionId("dashboard.apply"),
            clientCapabilities = screen.requiredCapabilities,
            textFieldSubmission = LuaTextFieldSubmission(
                submissionId = LuaSubmissionId("dashboard.submit.1"),
                fields = listOf(
                    LuaSubmittedTextField(
                        nodeId = LuaNodeId("dashboard.filter"),
                        value = " rice ",
                        draftRevision = 4,
                    ),
                ),
            ),
        )
        val response = LuaActionResponse.AcknowledgedScreen(
            screen = screen,
            acknowledgement = LuaTextFieldAcknowledgement(
                submissionId = LuaSubmissionId("dashboard.submit.1"),
                fields = listOf(
                    LuaAcknowledgedTextField(LuaNodeId("dashboard.filter"), draftRevision = 4),
                ),
            ),
        )

        assertEquals(
            request,
            LuaProtocolJson.decodeFromString<LuaActionRequest>(LuaProtocolJson.encodeToString(request)),
        )
        assertEquals(
            response,
            LuaProtocolJson.decodeFromString<LuaActionResponse>(
                LuaProtocolJson.encodeToString<LuaActionResponse>(response),
            ),
        )
    }

    @Test
    fun `legacy Submit JSON does not emit the experimental submission field`() {
        val request = LuaActionRequest(
            screenId = LuaScreenId("dashboard"),
            sourceNodeId = LuaNodeId("dashboard.refresh"),
            actionId = LuaActionId("dashboard.refresh"),
            clientCapabilities = LuaCapabilities.foundation,
        )

        val encoded = LuaProtocolJson.encodeToString(request)

        assertFalse(encoded.contains("textFieldSubmission"))
        assertEquals(request, LuaProtocolJson.decodeFromString<LuaActionRequest>(encoded))
    }
}
