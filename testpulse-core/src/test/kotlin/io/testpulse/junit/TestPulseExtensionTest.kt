package io.testpulse.junit

import org.junit.jupiter.api.Assertions.assertAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.junit.platform.engine.discovery.DiscoverySelectors.selectClass
import org.junit.platform.testkit.engine.EngineTestKit
import java.nio.file.Files
import java.nio.file.Path

class TestPulseExtensionTest {

    @Test
    fun `writes one line-protocol sample per test in FILE mode`(@TempDir tmp: Path) {
        withConfig(
            "testpulse.output" to "file",
            "testpulse.output-dir" to tmp.toString(),
            "testpulse.project" to "demo",
            "testpulse.environment" to "ci",
        ) {
            EngineTestKit.engine("junit-jupiter")
                .selectors(selectClass(SampleTests::class.java))
                .execute()
        }

        val lines = Files.readAllLines(tmp.resolve("metrics.influx"))

        assertAll(
            { assertEquals(5, lines.size, "one line per executed test: $lines") },
            { assertEquals(1, lines.count { it.contains("passed=0i") }, "exactly one failure") },
            { assertEquals(4, lines.count { it.contains("passed=1i") }, "four passing") },
            { assertTrue(lines.any { it.contains("#passes") }, "default id from class#method") },
            { assertTrue(lines.any { it.contains("test_id=pinned.id") }, "@StableId overrides the id") },
            { assertTrue(lines.any { it.contains("#param[1]") }, "parameterized invocation index #1") },
            { assertTrue(lines.any { it.contains("#param[2]") }, "parameterized invocation index #2") },
            { assertTrue(lines.all { it.contains("project=demo") && it.contains("environment=ci") }, "config labels applied") },
        )
    }

    private inline fun withConfig(vararg props: Pair<String, String>, block: () -> Unit) {
        props.forEach { (k, v) -> System.setProperty(k, v) }
        try {
            block()
        } finally {
            props.forEach { (k, _) -> System.clearProperty(k) }
        }
    }

    /**
     * Static-nested sample suite driven by [EngineTestKit]. It is NOT auto-discovered by the
     * outer Gradle test run (only top-level and `@Nested` classes are), so [fails] does not break
     * the build — it is exercised only via the explicit class selector above.
     */
    @ExtendWith(TestPulseExtension::class)
    class SampleTests {

        @Test
        fun passes() = Unit

        @Test
        fun fails(): Unit = throw AssertionError("intentional failure")

        @StableId("pinned.id")
        @Test
        fun pinnedName() = Unit

        @ParameterizedTest
        @ValueSource(ints = [1, 2])
        fun param(value: Int) {
            assertTrue(value > 0)
        }
    }
}
