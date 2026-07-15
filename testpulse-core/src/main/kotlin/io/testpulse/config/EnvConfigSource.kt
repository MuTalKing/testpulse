package io.testpulse.config

/**
 * Reads configuration from environment variables: `TESTPULSE_PROJECT`, `TESTPULSE_ENDPOINT`, etc.
 *
 * This is the primary path for projects that do not use Spring — add the `testpulse-core`
 * dependency and set a couple of env vars in CI.
 */
class EnvConfigSource : TestPulseConfigSource {
    override val priority: Int = 200

    override fun properties(): Map<String, String> =
        ConfigKeys.ALL.mapNotNull { key ->
            val envName = "TESTPULSE_" + key.uppercase().replace('-', '_')
            System.getenv(envName)?.let { key to it }
        }.toMap()
}
