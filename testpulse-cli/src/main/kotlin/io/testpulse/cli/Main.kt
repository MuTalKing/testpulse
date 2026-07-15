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
        "report" -> report(args.drop(1))
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

private fun report(args: List<String>): Int {
    val flags = parseFlags(args)

    val allure = flags["allure"] ?: flags["a"]
    if (allure == null) {
        System.err.println("error: --allure is required")
        return 2
    }
    val server = flags["server"] ?: flags["s"] ?: System.getenv("TESTPULSE_SERVER")
    if (server.isNullOrBlank()) {
        System.err.println("error: --server (or TESTPULSE_SERVER) is required")
        return 2
    }
    val dir = Path.of(allure)
    if (!Files.isDirectory(dir)) {
        System.err.println("error: allure-results directory not found: $dir")
        return 2
    }

    val run = AllureResults.readRun(
        dir = dir,
        project = flags["project"] ?: System.getenv("TESTPULSE_PROJECT"),
        environment = flags["environment"] ?: System.getenv("TESTPULSE_ENVIRONMENT"),
        branch = flags["branch"],
        gitSha = flags["git-sha"],
        buildUrl = flags["build-url"],
    )
    if (run.tests.isEmpty()) {
        println("No allure results in $dir — nothing to upload.")
        return 0
    }

    return try {
        val id = ReportUploader(server).upload(run)
        println("Uploaded run $id (${run.tests.size} tests) to $server.")
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
        testpulse — upload TestPulse metrics and run reports

        Usage:
          testpulse upload --file <metrics.influx> [--endpoint <url>] [--timestamp <epochMillis>]
          testpulse report --allure <allure-results> [--server <url>] [--project <p>] [--environment <e>]
                           [--branch <b>] [--git-sha <s>] [--build-url <u>]

        upload — send FILE-sink metrics to VictoriaMetrics:
          -f, --file        Path to the metrics.influx written by the FILE sink (required)
          -e, --endpoint    VictoriaMetrics base URL (or the TESTPULSE_ENDPOINT env var)
          -t, --timestamp   Run-finish time in epoch millis (default: now)

        report — send an allure-results run to the TestPulse server:
          -a, --allure      Path to the allure-results directory (required)
          -s, --server      TestPulse server base URL (or the TESTPULSE_SERVER env var)
              --project     Project label (or TESTPULSE_PROJECT)
              --environment Environment label (or TESTPULSE_ENVIRONMENT)
              --branch, --git-sha, --build-url   Optional run metadata
        """.trimIndent(),
    )
}
