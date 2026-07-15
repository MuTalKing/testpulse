package io.testpulse.cli

import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Uploads stamped line-protocol records to VictoriaMetrics (`POST <endpoint>/write?precision=ms`).
 *
 * Unlike the in-test [io.testpulse.metric.PushMetricSink], this is an explicit CI step: failures
 * throw so the pipeline surfaces them.
 */
class MetricsUploader(
    endpoint: String,
    private val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build(),
) {
    private val writeUri: URI = URI.create(endpoint.trimEnd('/') + "/write?precision=ms")

    /** Send all [lines] as one batch. Returns the number uploaded. Throws on a non-2xx response. */
    fun upload(lines: List<String>): Int {
        if (lines.isEmpty()) return 0
        val request = HttpRequest.newBuilder(writeUri)
            .timeout(Duration.ofSeconds(30))
            .header("Content-Type", "text/plain; charset=utf-8")
            .POST(HttpRequest.BodyPublishers.ofString(lines.joinToString("\n")))
            .build()

        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) {
            throw IOException("upload to $writeUri failed: HTTP ${response.statusCode()} ${response.body()}")
        }
        return lines.size
    }
}
