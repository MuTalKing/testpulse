package io.testpulse.metric

/**
 * One measurement of a single test execution within a run.
 *
 * Maps to two VictoriaMetrics series (via [io.testpulse.metric.LineProtocol]):
 *   - `autotest_duration_seconds{...}` = [durationSeconds]
 *   - `autotest_passed{...}`           = 1 | 0
 *
 * The label set is deliberately low-cardinality — no `run_id`/`build`/`timestamp` here.
 * Run identity lives in the report store, not in metric labels.
 */
data class MetricSample(
    /** Stable identity of the test across runs — the join key with the run report. */
    val testId: String,
    /** Fully-qualified test class name. */
    val testClass: String,
    /** Coarse grouping label (package name), null if unavailable. */
    val suite: String?,
    /** Logical project name, null if not configured. */
    val project: String?,
    /** Environment label (e.g. `ci`, `staging`). */
    val environment: String,
    val durationSeconds: Double,
    val passed: Boolean,
)
