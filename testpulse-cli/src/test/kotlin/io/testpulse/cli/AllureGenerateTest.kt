package io.testpulse.cli

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.nio.file.Path

class AllureGenerateTest {

    @Test
    fun `windows command goes through cmd and requests a single file`() {
        val command = AllureGenerate.buildCommand(Path.of("results"), Path.of("out"), "allure", windows = true)
        assertEquals(
            listOf("cmd", "/c", "allure", "generate", "results", "-o", "out", "--clean", "--single-file"),
            command,
        )
    }

    @Test
    fun `unix command runs allure directly with single file`() {
        val command = AllureGenerate.buildCommand(Path.of("results"), Path.of("out"), "allure", windows = false)
        assertEquals(
            listOf("allure", "generate", "results", "-o", "out", "--clean", "--single-file"),
            command,
        )
    }

    @Test
    fun `honours a custom allure command`() {
        val command = AllureGenerate.buildCommand(Path.of("r"), Path.of("o"), "/opt/allure/bin/allure", windows = false)
        assertEquals("/opt/allure/bin/allure", command.first())
    }
}
