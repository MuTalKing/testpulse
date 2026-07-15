package io.testpulse.metric

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.APPEND
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.TRUNCATE_EXISTING
import java.nio.file.StandardOpenOption.WRITE

/**
 * Appends one line-protocol record per test to `<outputDir>/metrics.influx`.
 *
 * The file is truncated once per JVM (on the first sample of a run) and appended to thereafter,
 * so a run produces exactly one clean file. Writes are serialized process-wide — safe under
 * parallel test execution. A later CLI step uploads this file to VictoriaMetrics.
 */
class FileMetricSink(outputDir: Path) : MetricSink {

    private val file: Path = outputDir.resolve(FILE_NAME)

    override fun emit(sample: MetricSample) {
        val line = LineProtocol.format(sample) + "\n"
        synchronized(LOCK) {
            Files.createDirectories(file.parent)
            val options = if (truncated) {
                arrayOf(CREATE, WRITE, APPEND)
            } else {
                truncated = true
                arrayOf(CREATE, WRITE, TRUNCATE_EXISTING)
            }
            Files.writeString(file, line, *options)
        }
    }

    private companion object {
        const val FILE_NAME = "metrics.influx"
        val LOCK = Any()

        /** Guarded by [LOCK]; drives truncate-on-first-write-per-JVM. */
        var truncated = false
    }
}
