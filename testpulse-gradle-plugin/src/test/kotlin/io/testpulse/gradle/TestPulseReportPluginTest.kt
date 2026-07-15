package io.testpulse.gradle

import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class TestPulseReportPluginTest {

    @Test
    fun `applying the plugin registers the task and it renders from allure-results`(@TempDir projectDir: Path) {
        Files.writeString(projectDir.resolve("settings.gradle.kts"), "rootProject.name = \"demo\"\n")
        Files.writeString(
            projectDir.resolve("build.gradle.kts"),
            """
            plugins { id("io.github.mutalking.testpulse") }
            testpulseReport {
                withAllure.set(false)
                openInBrowser.set(false)
                allureResults.set(layout.projectDirectory.dir("allure-results"))
                output.set(layout.buildDirectory.file("report/index.html"))
                projectLabel.set("demo")
                environment.set("ci")
            }
            """.trimIndent(),
        )

        val results = Files.createDirectories(projectDir.resolve("allure-results"))
        Files.writeString(
            results.resolve("a-result.json"),
            """{"name":"checkout","fullName":"io.shop.OrderTest.checkout","status":"passed","start":1000,"stop":1250,"labels":[{"name":"testpulse.id","value":"orders.checkout"}]}""",
        )

        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withArguments("testpulseReport")
            .withPluginClasspath()
            .build()

        assertTrue(result.output.contains("[testpulse] report:"), result.output)
        val html = projectDir.resolve("build/report/index.html")
        assertTrue(Files.exists(html), "report html should exist")
        assertTrue(Files.readString(html).contains("checkout"), "report should contain the test name")
    }

    @Test
    fun `warns when there are no allure-results yet`(@TempDir projectDir: Path) {
        Files.writeString(projectDir.resolve("settings.gradle.kts"), "rootProject.name = \"demo\"\n")
        Files.writeString(
            projectDir.resolve("build.gradle.kts"),
            """
            plugins { id("io.github.mutalking.testpulse") }
            testpulseReport { withAllure.set(false); openInBrowser.set(false) }
            """.trimIndent(),
        )

        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withArguments("testpulseReport")
            .withPluginClasspath()
            .build()

        assertTrue(result.output.contains("no allure-results"), result.output)
    }
}
