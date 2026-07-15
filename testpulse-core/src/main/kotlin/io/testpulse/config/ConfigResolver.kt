package io.testpulse.config

import java.util.ServiceLoader

/**
 * Assembles the effective [TestPulseConfig] from every [TestPulseConfigSource] on the classpath.
 *
 * Precedence, low to high (later overrides earlier), mirroring Spring's own ordering:
 *
 *     defaults  <  application.yml (100)  <  TESTPULSE_* env (200)  <  -Dtestpulse.* (300)  <  programmatic overrides
 *
 * A Spring project keeps base config in `application.yml` while CI can still override any single
 * value via env or a system property.
 */
object ConfigResolver {

    /** Resolve using all [TestPulseConfigSource]s discovered via [ServiceLoader]. */
    fun resolve(overrides: Map<String, String> = emptyMap()): TestPulseConfig =
        resolveFrom(ServiceLoader.load(TestPulseConfigSource::class.java).toList(), overrides)

    /** Testable core: merge an explicit list of sources plus programmatic overrides. */
    fun resolveFrom(
        sources: List<TestPulseConfigSource>,
        overrides: Map<String, String> = emptyMap(),
    ): TestPulseConfig {
        val merged = LinkedHashMap<String, String>()
        // Ascending priority: higher-priority sources are applied last and win.
        for (source in sources.sortedBy { it.priority }) {
            for ((key, value) in source.properties()) {
                merged[normalizeKey(key)] = value
            }
        }
        for ((key, value) in overrides) {
            merged[normalizeKey(key)] = value
        }
        return bind(merged)
    }

    private fun bind(props: Map<String, String>): TestPulseConfig {
        fun get(key: String): String? = props[key]?.trim()?.takeIf { it.isNotEmpty() }

        val defaults = TestPulseConfig()
        return TestPulseConfig(
            enabled = get(ConfigKeys.ENABLED)?.toBooleanStrictOrNull() ?: defaults.enabled,
            project = get(ConfigKeys.PROJECT),
            environment = get(ConfigKeys.ENVIRONMENT) ?: defaults.environment,
            output = get(ConfigKeys.OUTPUT)
                ?.let { runCatching { OutputMode.valueOf(it.uppercase()) }.getOrNull() }
                ?: defaults.output,
            outputDir = get(ConfigKeys.OUTPUT_DIR) ?: defaults.outputDir,
            endpoint = get(ConfigKeys.ENDPOINT),
        )
    }

    /**
     * Relaxed-binding normalization so sources can be loose about key casing:
     * `outputDir`, `OUTPUT_DIR`, `output-dir` all collapse to the canonical `output-dir`.
     */
    internal fun normalizeKey(raw: String): String {
        val sb = StringBuilder()
        var prevLower = false
        for (c in raw) {
            when {
                c == '_' || c == '.' || c == '-' -> {
                    sb.append('-'); prevLower = false
                }
                c.isUpperCase() -> {
                    if (prevLower) sb.append('-')
                    sb.append(c.lowercaseChar()); prevLower = false
                }
                else -> {
                    sb.append(c); prevLower = c.isLowerCase()
                }
            }
        }
        return sb.toString().replace(Regex("-+"), "-").trim('-')
    }
}
