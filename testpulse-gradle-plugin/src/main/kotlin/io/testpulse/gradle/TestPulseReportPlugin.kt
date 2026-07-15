package io.testpulse.gradle

import io.testpulse.cli.AllureGenerate
import io.testpulse.cli.AllureResults
import io.testpulse.cli.HtmlReport
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import java.nio.file.Files

/** Configuration for the `testpulseReport` task. */
abstract class TestPulseReportExtension {
    /** Directory the Allure adapter writes results to (default `build/allure-results`). */
    abstract val allureResults: DirectoryProperty

    /** Where the rendered report goes (default `build/testpulse/report/index.html`). */
    abstract val output: RegularFileProperty

    /** Also run `allure generate --single-file` and link it (default true; needs the Allure CLI). */
    abstract val withAllure: Property<Boolean>

    /** Open the report in the default browser after rendering (default true). */
    abstract val openInBrowser: Property<Boolean>

    abstract val projectLabel: Property<String>
    abstract val environment: Property<String>

    /** TestPulse server URL — adds a per-test "History" link when set. */
    abstract val server: Property<String>

    /** Grafana URL — adds a per-test "Metrics" link when set. */
    abstract val grafana: Property<String>
}

/**
 * Registers a `testpulseReport` task. Applying the plugin makes the task appear automatically — no
 * task definition to copy into the build. It renders the TestPulse report from the latest
 * `allure-results` and opens it, so after a local test run you press one button to see the report.
 */
class TestPulseReportPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        val ext = project.extensions.create("testpulseReport", TestPulseReportExtension::class.java)
        ext.allureResults.convention(project.layout.buildDirectory.dir("allure-results"))
        ext.output.convention(project.layout.buildDirectory.file("testpulse/report/index.html"))
        ext.withAllure.convention(true)
        ext.openInBrowser.convention(true)

        val projectName = project.name // captured so the task action doesn't reference Project

        project.tasks.register("testpulseReport") { task ->
            task.group = "verification"
            task.description = "Render the TestPulse report from the latest allure-results and open it"

            // Capture properties (not the Project) so the action stays configuration-cache friendly.
            val results = ext.allureResults
            val output = ext.output
            val withAllure = ext.withAllure
            val open = ext.openInBrowser
            val projectLabel = ext.projectLabel
            val environment = ext.environment
            val server = ext.server
            val grafana = ext.grafana

            task.doLast {
                val resultsDir = results.get().asFile.toPath()
                if (!Files.isDirectory(resultsDir)) {
                    task.logger.warn("[testpulse] no allure-results at $resultsDir — run your tests first.")
                    return@doLast
                }

                val outFile = output.get().asFile
                Files.createDirectories(outFile.toPath().toAbsolutePath().parent)

                var allureUrl: String? = null
                if (withAllure.get()) {
                    val reportDir = outFile.toPath().toAbsolutePath().parent.resolve("allure-report")
                    if (AllureGenerate.run(resultsDir, reportDir)) allureUrl = "allure-report/index.html"
                }

                val run = AllureResults.readRun(
                    dir = resultsDir,
                    project = projectLabel.orNull ?: projectName,
                    environment = environment.orNull,
                    branch = null,
                    gitSha = null,
                    buildUrl = null,
                ).copy(allureReportUrl = allureUrl)

                if (run.tests.isEmpty()) {
                    task.logger.warn("[testpulse] no results in $resultsDir — nothing to render.")
                    return@doLast
                }

                Files.writeString(outFile.toPath(), HtmlReport.render(run, serverUrl = server.orNull, grafanaUrl = grafana.orNull))
                task.logger.lifecycle("[testpulse] report: ${outFile.absolutePath} (${run.tests.size} tests)")
                if (open.get()) openInBrowser(outFile.absolutePath, task.logger)
            }
        }
    }

    private fun openInBrowser(path: String, logger: org.gradle.api.logging.Logger) {
        val os = System.getProperty("os.name").lowercase()
        val command = when {
            os.contains("win") -> listOf("cmd", "/c", "start", "", path)
            os.contains("mac") -> listOf("open", path)
            else -> listOf("xdg-open", path)
        }
        runCatching { ProcessBuilder(command).start() }
            .onFailure { logger.warn("[testpulse] could not open the browser: ${it.message}") }
    }
}
