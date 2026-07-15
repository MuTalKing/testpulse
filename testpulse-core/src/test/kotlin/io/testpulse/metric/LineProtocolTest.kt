package io.testpulse.metric

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LineProtocolTest {

    @Test
    fun `formats a full sample as line protocol`() {
        val line = LineProtocol.format(
            MetricSample(
                testId = "orders.checkout",
                testClass = "io.shop.OrderTest",
                suite = "io.shop",
                project = "shop",
                environment = "ci",
                durationSeconds = 1.234,
                passed = true,
            ),
        )

        assertEquals(
            "autotest,test_id=orders.checkout,class=io.shop.OrderTest,suite=io.shop,project=shop,environment=ci " +
                "duration_seconds=1.234000,passed=1i",
            line,
        )
    }

    @Test
    fun `omits null suite and project tags`() {
        val line = LineProtocol.format(
            MetricSample(
                testId = "t",
                testClass = "C",
                suite = null,
                project = null,
                environment = "default",
                durationSeconds = 0.5,
                passed = false,
            ),
        )

        assertEquals("autotest,test_id=t,class=C,environment=default duration_seconds=0.500000,passed=0i", line)
    }

    @Test
    fun `appends flaky field only when flaky`() {
        val line = LineProtocol.format(
            MetricSample(
                testId = "orders.checkout",
                testClass = "io.shop.OrderTest",
                suite = null,
                project = null,
                environment = "ci",
                durationSeconds = 2.0,
                passed = true,
                flaky = true,
            ),
        )

        assertTrue(line.endsWith("duration_seconds=2.000000,passed=1i,flaky=1i"), line)
    }

    @Test
    fun `escapes spaces commas and equals in tag values`() {
        val line = LineProtocol.format(
            MetricSample(
                testId = "a b,c=d",
                testClass = "C",
                suite = null,
                project = null,
                environment = "ci",
                durationSeconds = 0.0,
                passed = true,
            ),
        )

        assertTrue(line.contains("""test_id=a\ b\,c\=d"""), "tag value should be escaped: $line")
        assertFalse(line.contains("test_id=a b"), "raw space must not survive: $line")
    }
}
