package io.testpulse.cli

/**
 * Prepares the line-protocol records written by the FILE sink for upload.
 *
 * The sink writes records without a timestamp; the uploader stamps them all with a single
 * run-finish time, honouring the "one timestamp per run" invariant. Blank lines and `#` comments
 * are dropped.
 */
object MetricsFile {

    fun stamp(rawLines: List<String>, timestampMillis: Long): List<String> =
        rawLines.asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .map { "$it $timestampMillis" }
            .toList()
}
