package io.testpulse.metric

import java.util.concurrent.ConcurrentHashMap

/**
 * Detects flaky tests within a single JVM run: a `test_id` that records both a passing and a
 * failing execution (a retry flipped it) is flaky. This is the most honest signal — no cross-run
 * history guessing.
 *
 * Scope: catches retries that re-run the **same** `test_id` in the **same** JVM (e.g. the Gradle
 * `test-retry` plugin in the same fork). Retries in a separate fork are aggregated later by the
 * backend, which sees the same `test_id` with mixed outcomes across the run.
 */
class FlakyTracker {

    private val seen = ConcurrentHashMap<String, Outcomes>()

    /**
     * Record one execution outcome. Returns `true` exactly once per test — on the execution that
     * first makes its outcomes mixed — so the caller emits a single `autotest_flaky=1` sample.
     */
    fun record(testId: String, passed: Boolean): Boolean {
        val outcomes = seen.computeIfAbsent(testId) { Outcomes() }
        synchronized(outcomes) {
            val wasMixed = outcomes.mixed
            if (passed) outcomes.sawPass = true else outcomes.sawFail = true
            return !wasMixed && outcomes.mixed
        }
    }

    private class Outcomes {
        var sawPass = false
        var sawFail = false
        val mixed: Boolean get() = sawPass && sawFail
    }
}
