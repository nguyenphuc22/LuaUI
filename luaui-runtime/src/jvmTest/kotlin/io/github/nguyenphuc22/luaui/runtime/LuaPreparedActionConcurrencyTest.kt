package io.github.nguyenphuc22.luaui.runtime

import io.github.nguyenphuc22.luaui.core.LuaAcknowledgedTextField
import io.github.nguyenphuc22.luaui.core.LuaActionId
import io.github.nguyenphuc22.luaui.core.LuaActionRequest
import io.github.nguyenphuc22.luaui.core.LuaCapabilities
import io.github.nguyenphuc22.luaui.core.LuaNodeId
import io.github.nguyenphuc22.luaui.core.LuaScreenId
import io.github.nguyenphuc22.luaui.core.LuaSubmissionId
import io.github.nguyenphuc22.luaui.core.LuaSubmittedTextField
import io.github.nguyenphuc22.luaui.core.LuaTextFieldAcknowledgement
import io.github.nguyenphuc22.luaui.core.LuaTextFieldSubmission
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LuaPreparedActionConcurrencyTest {
    @Test
    fun `only one concurrent acknowledgement can claim a prepared invocation`() {
        val fieldId = LuaNodeId("dashboard.filter")
        val submission = LuaTextFieldSubmission(
            submissionId = LuaSubmissionId("dashboard.submit.1"),
            fields = listOf(
                LuaSubmittedTextField(
                    nodeId = fieldId,
                    value = "rice",
                    draftRevision = 1,
                ),
            ),
        )
        val prepared = LuaPreparedAction(
            request = LuaActionRequest(
                screenId = LuaScreenId("dashboard"),
                sourceNodeId = LuaNodeId("dashboard.apply"),
                actionId = LuaActionId("dashboard.apply"),
                clientCapabilities = LuaCapabilities.foundation + setOf(
                    LuaCapabilities.textField,
                    LuaCapabilities.submitTextFields,
                ),
                textFieldSubmission = submission,
            ),
            lifetime = LuaScreenSessionLifetime(),
            submission = submission,
            localSnapshots = listOf(
                LuaSubmittedTextFieldSnapshot(
                    nodeId = fieldId,
                    value = "rice",
                    draftRevision = 1,
                    slotGeneration = 1,
                ),
            ),
        )
        val acknowledgement = LuaTextFieldAcknowledgement(
            submissionId = submission.submissionId,
            fields = listOf(LuaAcknowledgedTextField(fieldId, draftRevision = 1)),
        )
        val workers = 16
        val ready = CountDownLatch(workers)
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(workers)

        try {
            val claims = List(workers) {
                executor.submit<Boolean> {
                    ready.countDown()
                    check(start.await(5, TimeUnit.SECONDS))
                    prepared.tryConsumeAcknowledgement(acknowledgement)
                }
            }
            assertTrue(ready.await(5, TimeUnit.SECONDS))
            start.countDown()

            assertEquals(1, claims.count { claim -> claim.get(5, TimeUnit.SECONDS) })
        } finally {
            executor.shutdownNow()
        }
    }
}
