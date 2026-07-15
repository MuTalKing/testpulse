package io.testpulse.config

/**
 * A pluggable source of TestPulse configuration, discovered via [java.util.ServiceLoader].
 *
 * The core module ships env and system-property sources. The optional `testpulse-spring`
 * module registers a source that reads `application.yml`/`application.properties` — it is
 * picked up automatically the moment its jar is on the classpath, with no code changes.
 *
 * Implementations return only the keys they actually define; [ConfigResolver] merges every
 * source, letting higher [priority] win. Keys are relaxed (camelCase / SNAKE_CASE / kebab-case
 * are all accepted) — the resolver normalizes them.
 */
interface TestPulseConfigSource {
    /** Higher wins when several sources define the same key. See [ConfigResolver] for the ordering. */
    val priority: Int

    /** The subset of [ConfigKeys] this source defines, as raw string values. */
    fun properties(): Map<String, String>
}
