package io.testpulse.config

/** Where the extension sends the collected metric samples. */
enum class OutputMode {
    /** Write samples to a file for a later CLI upload step (CI-friendly, no runtime dependency on the backend). */
    FILE,

    /** Fire-and-forget push straight to VictoriaMetrics from the test JVM. */
    PUSH,
}

/**
 * Fully resolved TestPulse configuration.
 *
 * Assembled by [ConfigResolver] from every [TestPulseConfigSource] on the classpath
 * (env, system properties, Spring `application.yml`, ...) plus optional programmatic overrides.
 * Sources contribute partial values; missing fields fall back to the defaults below.
 */
data class TestPulseConfig(
    val enabled: Boolean = true,
    val project: String? = null,
    /** Environment label attached to every metric series (e.g. `ci`, `staging`). NOT a JVM env var. */
    val environment: String = "default",
    val output: OutputMode = OutputMode.FILE,
    val outputDir: String = "build/testpulse",
    /** VictoriaMetrics base URL. Required for [OutputMode.PUSH]; also used by the CLI uploader. */
    val endpoint: String? = null,
) {
    /** Human-readable reasons the config is unusable, empty when it is fine to run. */
    fun problems(): List<String> = buildList {
        if (!enabled) return@buildList
        if (project.isNullOrBlank()) {
            add("testpulse.project is not set — metrics will not carry a project label")
        }
        if (output == OutputMode.PUSH && endpoint.isNullOrBlank()) {
            add("output=push requires testpulse.endpoint (VictoriaMetrics URL)")
        }
    }
}

/** Canonical, kebab-cased property keys shared by every config source. */
object ConfigKeys {
    const val ENABLED = "enabled"
    const val PROJECT = "project"
    const val ENVIRONMENT = "environment"
    const val OUTPUT = "output"
    const val OUTPUT_DIR = "output-dir"
    const val ENDPOINT = "endpoint"

    val ALL = listOf(ENABLED, PROJECT, ENVIRONMENT, OUTPUT, OUTPUT_DIR, ENDPOINT)
}
