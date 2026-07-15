package io.testpulse.metric

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FlakyTrackerTest {

    @Test
    fun `fail then pass flags flaky exactly once`() {
        val tracker = FlakyTracker()

        assertFalse(tracker.record("t", passed = false), "first outcome is not yet mixed")
        assertTrue(tracker.record("t", passed = true), "fail then pass -> flaky on the transition")
        assertFalse(tracker.record("t", passed = true), "no re-emit once already flagged")
        assertFalse(tracker.record("t", passed = false), "still no re-emit")
    }

    @Test
    fun `pass then fail also flags flaky`() {
        val tracker = FlakyTracker()

        assertFalse(tracker.record("t", passed = true))
        assertTrue(tracker.record("t", passed = false))
    }

    @Test
    fun `consistent outcomes are never flaky`() {
        val tracker = FlakyTracker()

        assertFalse(tracker.record("always-green", passed = true))
        assertFalse(tracker.record("always-green", passed = true))
        assertFalse(tracker.record("always-red", passed = false))
        assertFalse(tracker.record("always-red", passed = false))
    }

    @Test
    fun `distinct test ids are tracked independently`() {
        val tracker = FlakyTracker()

        assertFalse(tracker.record("a", passed = false))
        assertFalse(tracker.record("b", passed = true))
        assertTrue(tracker.record("a", passed = true), "only 'a' went mixed")
        assertFalse(tracker.record("b", passed = true), "'b' still consistent")
    }
}
