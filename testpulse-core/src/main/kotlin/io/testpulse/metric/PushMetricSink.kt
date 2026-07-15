package io.testpulse.metric

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Streams samples to VictoriaMetrics as InfluxDB line protocol (`POST <endpoint>/write`).
 *
 * Tests never block on the backend: samples are buffered and sent in batches on a daemon
 * background thread when the buffer fills, with the remainder flushed at JVM shutdown. Network
 * failures are swallowed (warned once) so a down backend can never fail or hang a test run.
 *
 * For large suites the FILE sink + CLI uploader is still preferred; PUSH is the zero-extra-step
 * convenience path.
 */
class PushMetricSink(
    endpoint: String,
    private val batchSize: Int = DEFAULT_BATCH_SIZE,
) : MetricSink {

    private val writeUri: URI = URI.create(endpoint.trimEnd('/') + "/write")

    private val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build()

    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "testpulse-push").apply { isDaemon = true }
    }

    private val lock = Any()
    private val buffer = ArrayDeque<String>()
    private val warned = AtomicBoolean(false)

    init {
        Runtime.getRuntime().addShutdownHook(
            Thread(
                {
                    executor.shutdown()
                    runCatching { executor.awaitTermination(5, TimeUnit.SECONDS) }
                    flush()
                },
                "testpulse-push-shutdown",
            ),
        )
    }

    override fun emit(sample: MetricSample) {
        val line = LineProtocol.format(sample)
        val batch = synchronized(lock) {
            buffer.addLast(line)
            if (buffer.size >= batchSize) drainLocked() else null
        }
        if (batch != null) executor.execute { send(batch) }
    }

    /** Synchronously send everything buffered. Used by the shutdown hook (and tests). */
    override fun flush() {
        val batch = synchronized(lock) { drainLocked() }
        send(batch)
    }

    private fun drainLocked(): List<String> {
        if (buffer.isEmpty()) return emptyList()
        val copy = ArrayList(buffer)
        buffer.clear()
        return copy
    }

    private fun send(lines: List<String>) {
        if (lines.isEmpty()) return
        val request = HttpRequest.newBuilder(writeUri)
            .timeout(Duration.ofSeconds(10))
            .header("Content-Type", "text/plain; charset=utf-8")
            .POST(HttpRequest.BodyPublishers.ofString(lines.joinToString("\n")))
            .build()
        runCatching { client.send(request, HttpResponse.BodyHandlers.discarding()) }
            .onFailure { error ->
                if (warned.compareAndSet(false, true)) {
                    System.err.println("[testpulse] push to $writeUri failed: ${error.message} (further errors suppressed)")
                }
            }
    }

    private companion object {
        const val DEFAULT_BATCH_SIZE = 200
    }
}
