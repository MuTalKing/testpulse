package io.testpulse.junit

import io.testpulse.config.ConfigResolver
import io.testpulse.config.TestPulseConfig
import org.junit.jupiter.api.extension.AfterTestExecutionCallback
import org.junit.jupiter.api.extension.BeforeTestExecutionCallback
import org.junit.jupiter.api.extension.ExtensionContext

/**
 * JUnit 5 extension that times each test and records its outcome as a TestPulse metric sample.
 *
 * Auto-registered via [java.util.ServiceLoader] when
 * `junit.jupiter.extensions.autodetection.enabled=true`, so consumers only add the dependency
 * and a couple of config values — no `@ExtendWith` needed.
 *
 * Configuration is resolved once, lazily, on first use. See [ConfigResolver].
 *
 * NOTE: metric emission (duration + status → VictoriaMetrics line protocol) is not wired yet;
 * this scaffold establishes the config wiring and lifecycle hooks. See the design notes.
 */
class TestPulseExtension : BeforeTestExecutionCallback, AfterTestExecutionCallback {

    private val config: TestPulseConfig by lazy { ConfigResolver.resolve() }

    override fun beforeTestExecution(context: ExtensionContext) {
        if (!config.enabled) return
        store(context).put(START_NANOS, System.nanoTime())
    }

    override fun afterTestExecution(context: ExtensionContext) {
        if (!config.enabled) return
        val start = store(context).remove(START_NANOS) as? Long ?: return
        @Suppress("UNUSED_VARIABLE")
        val durationMs = (System.nanoTime() - start) / 1_000_000
        val passed = context.executionException.isEmpty
        // TODO: emit sample -> autotest_duration_seconds / autotest_passed keyed by test_id(context).
        //       Sink selected by config.output (FILE | PUSH).
    }

    private fun store(context: ExtensionContext): ExtensionContext.Store =
        context.getStore(NAMESPACE)

    private companion object {
        val NAMESPACE: ExtensionContext.Namespace =
            ExtensionContext.Namespace.create(TestPulseExtension::class.java)
        const val START_NANOS = "startNanos"
    }
}
