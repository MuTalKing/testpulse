package io.testpulse.cli

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MainTest {

    @Test
    fun `no args prints usage and fails`() {
        assertEquals(1, run(emptyArray()))
    }

    @Test
    fun `help succeeds`() {
        assertEquals(0, run(arrayOf("--help")))
    }

    @Test
    fun `unknown command fails`() {
        assertEquals(1, run(arrayOf("frobnicate")))
    }

    @Test
    fun `upload without file is a usage error`() {
        assertEquals(2, run(arrayOf("upload", "--endpoint", "http://localhost:8428")))
    }

    @Test
    fun `upload with missing file is a usage error`() {
        assertEquals(
            2,
            run(arrayOf("upload", "--file", "does-not-exist.influx", "--endpoint", "http://localhost:8428")),
        )
    }
}
