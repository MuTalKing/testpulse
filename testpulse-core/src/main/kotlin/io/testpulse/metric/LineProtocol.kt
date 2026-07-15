package io.testpulse.metric

import java.util.Locale

/**
 * Formats a [MetricSample] as a single InfluxDB line-protocol record.
 *
 * VictoriaMetrics ingests this via `/write` (or `/api/v1/import/prometheus`) and exposes the
 * fields as the metrics `autotest_duration_seconds` and `autotest_passed`, keyed by the tags.
 *
 * No timestamp is written: FILE-mode samples are stamped with the run-finish time by the CLI
 * uploader, keeping the "one timestamp per run" invariant.
 *
 * Example:
 * ```
 * autotest,test_id=orders.checkout,class=io.shop.OrderTest,suite=io.shop,project=shop,environment=ci duration_seconds=1.234000,passed=1i
 * ```
 */
object LineProtocol {

    private const val MEASUREMENT = "autotest"

    fun format(sample: MetricSample): String {
        val tags = buildList {
            add("test_id" to sample.testId)
            add("class" to sample.testClass)
            sample.suite?.let { add("suite" to it) }
            sample.project?.let { add("project" to it) }
            add("environment" to sample.environment)
        }
        val tagSet = tags.joinToString(",") { (key, value) -> "$key=${escape(value)}" }

        val passed = if (sample.passed) 1 else 0
        // Fixed decimal (Locale.ROOT) avoids scientific notation and locale decimal commas.
        val duration = String.format(Locale.ROOT, "%.6f", sample.durationSeconds)
        val fields = buildString {
            append("duration_seconds=").append(duration)
            append(",passed=").append(passed).append('i')
            if (sample.flaky) append(",flaky=1i")
        }

        return "$MEASUREMENT,$tagSet $fields"
    }

    /** Escape line-protocol tag values: backslash-escape spaces, commas and equals. */
    private fun escape(value: String): String =
        value.replace("\\", "\\\\")
            .replace(" ", "\\ ")
            .replace(",", "\\,")
            .replace("=", "\\=")
}
