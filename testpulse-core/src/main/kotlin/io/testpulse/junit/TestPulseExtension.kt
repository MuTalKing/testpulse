package io.testpulse.junit

import io.testpulse.config.ConfigResolver
import io.testpulse.config.OutputMode
import io.testpulse.config.TestPulseConfig
import io.testpulse.metric.FileMetricSink
import io.testpulse.metric.FlakyTracker
import io.testpulse.metric.MetricSample
import io.testpulse.metric.MetricSink
import io.testpulse.metric.NoopMetricSink
import io.testpulse.metric.PushMetricSink
import org.junit.jupiter.api.extension.AfterTestExecutionCallback
import org.junit.jupiter.api.extension.BeforeTestExecutionCallback
import org.junit.jupiter.api.extension.ExtensionContext
import java.nio.file.Path

/**
 * JUnit 5 extension that times each test and records its outcome as a TestPulse metric sample.
 *
 * Auto-registered via [java.util.ServiceLoader] when
 * `junit.jupiter.extensions.autodetection.enabled=true`, so consumers only add the dependency
 * and a couple of config values — no `@ExtendWith` needed.
 *
 * Configuration and the metric sink are resolved once, lazily. See [ConfigResolver].
 */
class TestPulseExtension : BeforeTestExecutionCallback, AfterTestExecutionCallback {

    private val config: TestPulseConfig by lazy { ConfigResolver.resolve() }

    private val sink: MetricSink by lazy {
        when (config.output) {
            OutputMode.FILE -> FileMetricSink(Path.of(config.outputDir))
            OutputMode.PUSH -> config.endpoint
                ?.let { PushMetricSink(it) }
                ?: NoopMetricSink // push requested but no endpoint configured
        }
    }

    override fun beforeTestExecution(context: ExtensionContext) {
        if (!config.enabled) return
        store(context).put(START_NANOS, System.nanoTime())
    }

    override fun afterTestExecution(context: ExtensionContext) {
        if (!config.enabled) return
        val start = store(context).remove(START_NANOS) as? Long ?: return

        val testId = TestIds.compute(context)
        val passed = context.executionException.isEmpty
        val sample = MetricSample(
            testId = testId,
            testClass = context.testClass.map { it.name }.orElse("UnknownClass"),
            suite = context.testClass.map { it.packageName }.orElse(null)?.ifEmpty { null },
            project = config.project,
            environment = config.environment,
            durationSeconds = (System.nanoTime() - start) / NANOS_PER_SECOND,
            passed = passed,
            flaky = FLAKY.record(testId, passed),
        )

        runCatching { sink.emit(sample) }
            .onFailure { System.err.println("[testpulse] failed to record metric: ${it.message}") }
    }

    private fun store(context: ExtensionContext): ExtensionContext.Store =
        context.getStore(NAMESPACE)

    private companion object {
        val NAMESPACE: ExtensionContext.Namespace =
            ExtensionContext.Namespace.create(TestPulseExtension::class.java)
        const val START_NANOS = "startNanos"
        const val NANOS_PER_SECOND = 1_000_000_000.0

        /** Shared across all extension instances in the JVM so retries of a test are correlated. */
        val FLAKY = FlakyTracker()
    }
}
