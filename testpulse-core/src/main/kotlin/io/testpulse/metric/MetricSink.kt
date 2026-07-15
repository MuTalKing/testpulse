package io.testpulse.metric

/**
 * Destination for [MetricSample]s produced by the JUnit extension.
 *
 * Implementations: [FileMetricSink] (write line protocol to disk for a CLI upload step) and,
 * later, a push sink that streams straight to VictoriaMetrics.
 */
interface MetricSink {
    fun emit(sample: MetricSample)
}
