package io.testpulse.cli

import java.nio.file.Path

/**
 * Invokes the `allure` command-line tool to generate a **single-file** Allure report next to the
 * static report, so `testpulse html --with-allure` produces both in one step and links them.
 *
 * `--single-file` inlines everything into one `index.html` (no `data/`/`widgets/` siblings), so it
 * opens directly over `file://` — no web server needed, unlike a regular Allure report.
 *
 * Requires the `allure` CLI on PATH (override with the `ALLURE_CMD` env var).
 */
object AllureGenerate {

    /** OS-aware command line — on Windows `allure` is a `.bat`, so it must go through `cmd /c`. */
    fun buildCommand(resultsDir: Path, outputDir: Path, allureCmd: String, windows: Boolean): List<String> {
        val base = listOf(allureCmd, "generate", resultsDir.toString(), "-o", outputDir.toString(), "--clean", "--single-file")
        return if (windows) listOf("cmd", "/c") + base else base
    }

    /** Returns true if the report was generated. Never throws; prints a diagnostic on failure. */
    fun run(
        resultsDir: Path,
        outputDir: Path,
        allureCmd: String = System.getenv("ALLURE_CMD") ?: "allure",
    ): Boolean {
        val windows = System.getProperty("os.name").lowercase().contains("win")
        val command = buildCommand(resultsDir, outputDir, allureCmd, windows)
        return try {
            val process = ProcessBuilder(command).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().readText()
            val exit = process.waitFor()
            if (exit != 0) System.err.println("[testpulse] allure generate failed (exit $exit): ${output.trim()}")
            exit == 0
        } catch (e: Exception) {
            System.err.println("[testpulse] could not run '$allureCmd generate' — is the Allure CLI installed? ${e.message}")
            false
        }
    }
}
