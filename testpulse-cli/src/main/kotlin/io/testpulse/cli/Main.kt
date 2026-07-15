package io.testpulse.cli

import java.nio.file.Files
import java.nio.file.Path
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    exitProcess(run(args))
}

/** Testable entry point: returns the process exit code instead of calling [exitProcess]. */
internal fun run(args: Array<String>): Int {
    if (args.isEmpty()) {
        printUsage()
        return 1
    }
    return when (val command = args[0]) {
        "-h", "--help", "help" -> {
            printUsage()
            0
        }
        "upload" -> upload(args.drop(1))
        else -> {
            System.err.println("Unknown command: $command")
            printUsage()
            1
        }
    }
}

private fun upload(args: List<String>): Int {
    val flags = parseFlags(args)

    val file = flags["file"] ?: flags["f"]
    if (file == null) {
        System.err.println("error: --file is required")
        return 2
    }
    val endpoint = flags["endpoint"] ?: flags["e"] ?: System.getenv("TESTPULSE_ENDPOINT")
    if (endpoint.isNullOrBlank()) {
        System.err.println("error: --endpoint (or TESTPULSE_ENDPOINT) is required")
        return 2
    }
    val timestamp = (flags["timestamp"] ?: flags["t"])?.toLongOrNull() ?: System.currentTimeMillis()

    val path = Path.of(file)
    if (!Files.exists(path)) {
        System.err.println("error: file not found: $path")
        return 2
    }

    val lines = MetricsFile.stamp(Files.readAllLines(path), timestamp)
    if (lines.isEmpty()) {
        println("No samples in $path — nothing to upload.")
        return 0
    }

    return try {
        val uploaded = MetricsUploader(endpoint).upload(lines)
        println("Uploaded $uploaded samples to $endpoint (ts=$timestamp).")
        0
    } catch (e: Exception) {
        System.err.println("error: ${e.message}")
        1
    }
}

private fun parseFlags(args: List<String>): Map<String, String> {
    val flags = HashMap<String, String>()
    var i = 0
    while (i < args.size) {
        val arg = args[i]
        val key = when {
            arg.startsWith("--") -> arg.removePrefix("--")
            arg.startsWith("-") -> arg.removePrefix("-")
            else -> { i++; continue }
        }
        val next = args.getOrNull(i + 1)
        if (next != null && !next.startsWith("-")) {
            flags[key] = next
            i += 2
        } else {
            flags[key] = "true"
            i += 1
        }
    }
    return flags
}

private fun printUsage() {
    println(
        """
        testpulse — upload TestPulse metrics to VictoriaMetrics

        Usage:
          testpulse upload --file <metrics.influx> [--endpoint <url>] [--timestamp <epochMillis>]

        Options:
          -f, --file        Path to the metrics.influx written by the FILE sink (required)
          -e, --endpoint    VictoriaMetrics base URL (or the TESTPULSE_ENDPOINT env var)
          -t, --timestamp   Run-finish time in epoch millis (default: now)
        """.trimIndent(),
    )
}
