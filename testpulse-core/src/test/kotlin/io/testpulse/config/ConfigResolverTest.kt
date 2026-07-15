package io.testpulse.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ConfigResolverTest {

    private fun source(p: Int, props: Map<String, String>) = object : TestPulseConfigSource {
        override val priority = p
        override fun properties() = props
    }

    @Test
    fun `defaults apply when no source sets a value`() {
        val cfg = ConfigResolver.resolveFrom(emptyList())

        assertTrue(cfg.enabled)
        assertNull(cfg.project)
        assertEquals("default", cfg.environment)
        assertEquals(OutputMode.FILE, cfg.output)
        assertEquals("build/testpulse", cfg.outputDir)
        assertNull(cfg.endpoint)
    }

    @Test
    fun `higher priority source wins per-key, others still contribute`() {
        val cfg = ConfigResolver.resolveFrom(
            listOf(
                // Spring-like (100): base config
                source(100, mapOf("endpoint" to "http://yaml", "project" to "orders", "output-dir" to "yaml-dir")),
                // env (200): overrides only the endpoint
                source(200, mapOf("endpoint" to "http://env")),
            ),
        )

        assertEquals("http://env", cfg.endpoint)   // env wins
        assertEquals("orders", cfg.project)          // only yaml set it
        assertEquals("yaml-dir", cfg.outputDir)      // only yaml set it
    }

    @Test
    fun `programmatic overrides beat every source`() {
        val cfg = ConfigResolver.resolveFrom(
            sources = listOf(source(300, mapOf("endpoint" to "http://sysprop"))),
            overrides = mapOf("endpoint" to "http://code"),
        )

        assertEquals("http://code", cfg.endpoint)
    }

    @Test
    fun `keys are relaxed - camelCase and SNAKE_CASE normalize to canonical`() {
        val cfg = ConfigResolver.resolveFrom(
            listOf(source(100, mapOf("outputDir" to "x", "OUTPUT" to "push", "ENVIRONMENT" to "ci"))),
        )

        assertEquals("x", cfg.outputDir)
        assertEquals(OutputMode.PUSH, cfg.output)
        assertEquals("ci", cfg.environment)
    }

    @Test
    fun `unparseable enum falls back to default`() {
        val cfg = ConfigResolver.resolveFrom(
            listOf(source(100, mapOf("output" to "nonsense"))),
        )

        assertEquals(OutputMode.FILE, cfg.output)
    }
}
