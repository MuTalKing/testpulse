package io.testpulse.metric

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Discards samples. Used as the placeholder for [io.testpulse.config.OutputMode.PUSH] until the
 * VictoriaMetrics push sink is implemented. Warns once so the behaviour is not silent.
 */
object NoopMetricSink : MetricSink {

    private val warned = AtomicBoolean(false)

    override fun emit(sample: MetricSample) {
        if (warned.compareAndSet(false, true)) {
            System.err.println("[testpulse] output=push is not implemented yet — metrics are being dropped.")
        }
    }
}
