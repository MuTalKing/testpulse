package io.testpulse.cli

import io.testpulse.report.model.RunIngest
import io.testpulse.report.model.TestResultIngest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText

/**
 * Reads an `allure-results` directory (as written by allure-junit5) into a [RunIngest].
 *
 * Each `*-result.json` becomes a test result. The stable `test_id` is taken from the
 * `testpulse.id` label the extension stamped (falling back to `fullName`), so report rows join the
 * metric series by the exact same id. Run metadata (project, environment, ...) is supplied by the
 * caller; started/finished are derived from the min/max test timestamps.
 */
object AllureResults {

    private const val TESTPULSE_ID_LABEL = "testpulse.id"
    private val json = Json { ignoreUnknownKeys = true }

    fun readRun(
        dir: Path,
        project: String?,
        environment: String?,
        branch: String?,
        gitSha: String?,
        buildUrl: String?,
    ): RunIngest {
        val results = readResults(dir)
        return RunIngest(
            project = project,
            environment = environment,
            startedAt = results.mapNotNull { it.start }.minOrNull(),
            finishedAt = results.mapNotNull { it.stop }.maxOrNull(),
            branch = branch,
            gitSha = gitSha,
            buildUrl = buildUrl,
            tests = results.map { it.toIngest() },
        )
    }

    private fun readResults(dir: Path): List<AllureResult> {
        if (!Files.isDirectory(dir)) return emptyList()
        Files.newDirectoryStream(dir, "*-result.json").use { stream ->
            return stream.filter { Files.isRegularFile(it) }
                .mapNotNull { file ->
                    runCatching { json.decodeFromString<AllureResult>(file.readText()) }.getOrNull()
                }
        }
    }

    private fun AllureResult.toIngest(): TestResultIngest {
        val testId = labels.firstOrNull { it.name == TESTPULSE_ID_LABEL }?.value ?: fullName
        val duration = if (start != null && stop != null && stop >= start) stop - start else 0
        return TestResultIngest(
            testId = testId,
            fullName = fullName,
            name = name,
            status = status ?: "unknown",
            durationMs = duration,
            message = statusDetails?.message,
            trace = statusDetails?.trace,
            flaky = statusDetails?.flaky ?: false,
        )
    }

    @Serializable
    private data class AllureResult(
        val name: String? = null,
        val fullName: String? = null,
        val status: String? = null,
        val start: Long? = null,
        val stop: Long? = null,
        val statusDetails: AllureStatusDetails? = null,
        val labels: List<AllureLabel> = emptyList(),
    )

    @Serializable
    private data class AllureStatusDetails(
        val message: String? = null,
        val trace: String? = null,
        val flaky: Boolean = false,
    )

    @Serializable
    private data class AllureLabel(
        val name: String? = null,
        val value: String? = null,
    )
}
