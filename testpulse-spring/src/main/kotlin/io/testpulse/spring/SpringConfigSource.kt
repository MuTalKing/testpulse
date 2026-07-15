package io.testpulse.spring

import io.testpulse.config.ConfigKeys
import io.testpulse.config.TestPulseConfigSource
import org.springframework.boot.env.PropertiesPropertySourceLoader
import org.springframework.boot.env.PropertySourceLoader
import org.springframework.boot.env.YamlPropertySourceLoader
import org.springframework.core.env.StandardEnvironment
import org.springframework.core.io.ClassPathResource

/**
 * Reads TestPulse config from a Spring project's `application.yml` / `application.properties`
 * under the `testpulse.*` prefix.
 *
 * Registered via `ServiceLoader`, so simply having `testpulse-spring` on the classpath activates
 * the "Spring way" — no code, no `@ConfigurationProperties` in the consumer.
 *
 * Deliberately does NOT boot an `ApplicationContext` or hook the live test context: it loads the
 * config files standalone. This works for every test (not just `@SpringBootTest`) and adds no
 * bootstrap cost. Trade-off: profile-only files (`application-ci.yml`) are not merged unless the
 * profile is active in the base file; TestPulse's URLs are static per run, so that is acceptable.
 */
class SpringConfigSource : TestPulseConfigSource {

    override val priority: Int = 100

    override fun properties(): Map<String, String> {
        val environment = StandardEnvironment().apply {
            load(this, "application.properties", PropertiesPropertySourceLoader())
            load(this, "application.yml", YamlPropertySourceLoader())
            load(this, "application.yaml", YamlPropertySourceLoader())
        }

        return ConfigKeys.ALL.mapNotNull { key ->
            // Support both kebab (output-dir) and camelCase (outputDir) spellings in the file.
            val value = environment.getProperty("testpulse.$key")
                ?: environment.getProperty("testpulse.${toCamelCase(key)}")
            value?.let { key to it }
        }.toMap()
    }

    private fun load(env: StandardEnvironment, name: String, loader: PropertySourceLoader) {
        val resource = ClassPathResource(name)
        if (!resource.exists()) return
        runCatching { loader.load(name, resource) }
            .getOrNull()
            ?.forEach { env.propertySources.addLast(it) }
    }

    private fun toCamelCase(kebab: String): String =
        kebab.split('-').mapIndexed { i, part ->
            if (i == 0) part else part.replaceFirstChar { it.uppercase() }
        }.joinToString("")
}
