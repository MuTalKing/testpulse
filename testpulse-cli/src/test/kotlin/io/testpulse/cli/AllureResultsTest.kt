package io.testpulse.cli

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Path

class AllureResultsTest {

    private fun resultsDir(): Path = Path.of(javaClass.getResource("/allure-results")!!.toURI())

    @Test
    fun `parses results, reads testpulse_id label, ignores containers`() {
        val run = AllureResults.readRun(resultsDir(), project = "shop", environment = "ci", branch = "main", gitSha = null, buildUrl = null)

        assertEquals("shop", run.project)
        assertEquals("ci", run.environment)
        assertEquals("main", run.branch)
        assertEquals(1000, run.startedAt)   // min start
        assertEquals(1450, run.finishedAt)  // max stop
        assertEquals(2, run.tests.size)     // container file ignored

        val passed = run.tests.first { it.name == "checkout happy path" }
        assertEquals("orders.checkout", passed.testId) // from testpulse.id label, not fullName
        assertEquals("passed", passed.status)
        assertEquals(250, passed.durationMs)

        val failed = run.tests.first { it.status == "failed" }
        assertEquals("orders.checkout.invalid", failed.testId)
        assertTrue(failed.flaky)
        assertEquals("boom", failed.message)
        assertEquals("java.lang.AssertionError", failed.trace)
    }

    @Test
    fun `a missing directory yields an empty run`() {
        val run = AllureResults.readRun(Path.of("no-such-dir"), null, null, null, null, null)
        assertTrue(run.tests.isEmpty())
    }
}
