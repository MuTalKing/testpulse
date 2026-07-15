package io.testpulse.junit

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.platform.engine.discovery.DiscoverySelectors.selectClass
import org.junit.platform.testkit.engine.EngineTestKit
import java.nio.file.Files
import java.nio.file.Path

class AutoRegistrationTest {

    @Test
    fun `extension auto-registers via ServiceLoader when autodetection is enabled`(@TempDir tmp: Path) {
        withConfig(
            "testpulse.output" to "file",
            "testpulse.output-dir" to tmp.toString(),
            "testpulse.project" to "auto",
            "testpulse.environment" to "ci",
        ) {
            EngineTestKit.engine("junit-jupiter")
                .configurationParameter("junit.jupiter.extensions.autodetection.enabled", "true")
                .selectors(selectClass(AutoSampleTests::class.java))
                .execute()
                .testEvents()
                .assertStatistics { stats -> stats.started(1).succeeded(1) }
        }

        val lines = Files.readAllLines(tmp.resolve("metrics.influx"))

        assertEquals(1, lines.size, "the auto-registered extension should have written one sample: $lines")
        assertTrue(lines.single().contains("#autoDetected"), "sample from the un-annotated test: ${lines.single()}")
        assertTrue(lines.single().contains("project=auto"), "config applied: ${lines.single()}")
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
     * Note: NO `@ExtendWith` here — registration relies solely on the ServiceLoader entry plus
     * the `autodetection.enabled` parameter above. Named `*SampleTests` so the outer Gradle run
     * excludes it (it is driven only by the explicit selector).
     */
    class AutoSampleTests {

        @Test
        fun autoDetected() = Unit
    }
}
