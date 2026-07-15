package io.testpulse.cli

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MetricsFileTest {

    @Test
    fun `appends the same timestamp to every record`() {
        val stamped = MetricsFile.stamp(
            listOf(
                "autotest,test_id=a duration_seconds=1.0,passed=1i",
                "autotest,test_id=b duration_seconds=2.0,passed=0i",
            ),
            timestampMillis = 1_700_000_000_000,
        )

        assertEquals(
            listOf(
                "autotest,test_id=a duration_seconds=1.0,passed=1i 1700000000000",
                "autotest,test_id=b duration_seconds=2.0,passed=0i 1700000000000",
            ),
            stamped,
        )
    }

    @Test
    fun `drops blank lines and comments`() {
        val stamped = MetricsFile.stamp(
            listOf("", "  ", "# a comment", "autotest,test_id=a duration_seconds=1.0,passed=1i", ""),
            timestampMillis = 42,
        )

        assertEquals(listOf("autotest,test_id=a duration_seconds=1.0,passed=1i 42"), stamped)
    }
}
