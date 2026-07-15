package io.testpulse.config

/**
 * Reads configuration from JVM system properties: `-Dtestpulse.project=...`, `-Dtestpulse.endpoint=...`.
 *
 * Highest precedence of the built-in sources, so a Gradle/CI invocation can override anything
 * declared in env or `application.yml` without touching code.
 */
class SystemPropertiesConfigSource : TestPulseConfigSource {
    override val priority: Int = 300

    override fun properties(): Map<String, String> =
        ConfigKeys.ALL.mapNotNull { key ->
            System.getProperty("testpulse.$key")?.let { key to it }
        }.toMap()
}
