package io.testpulse.spring

import io.testpulse.config.ConfigResolver
import io.testpulse.config.OutputMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SpringConfigSourceTest {

    @Test
    fun `reads testpulse block from application yml`() {
        val props = SpringConfigSource().properties()

        assertEquals("billing", props["project"])
        assertEquals("staging", props["environment"])
        assertEquals("push", props["output"])
        assertEquals("build/tp-out", props["output-dir"])
        assertEquals("http://victoria:8428", props["endpoint"])
    }

    @Test
    fun `resolves through ConfigResolver into a typed config`() {
        val cfg = ConfigResolver.resolveFrom(listOf(SpringConfigSource()))

        assertEquals("billing", cfg.project)
        assertEquals("staging", cfg.environment)
        assertEquals(OutputMode.PUSH, cfg.output)
        assertEquals("http://victoria:8428", cfg.endpoint)
    }
}
