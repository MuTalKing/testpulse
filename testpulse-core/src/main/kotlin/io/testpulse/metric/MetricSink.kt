package io.testpulse.metric

/**
 * Destination for [MetricSample]s produced by the JUnit extension.
 *
 * Implementations: [FileMetricSink] (write line protocol to disk for a CLI upload step) and
 * [PushMetricSink] (stream straight to VictoriaMetrics).
 */
interface MetricSink {
    fun emit(sample: MetricSample)

    /** Flush any buffered samples. No-op for sinks that write eagerly. */
    fun flush() {}
}
