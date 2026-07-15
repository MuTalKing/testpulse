package io.testpulse.cli

import io.testpulse.report.model.RunIngest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Posts a parsed run to the TestPulse server (`POST <server>/api/runs`) and returns the new run id.
 * Throws on a non-2xx response so the CI step fails loudly.
 */
class ReportUploader(
    server: String,
    private val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build(),
) {
    private val runsUri: URI = URI.create(server.trimEnd('/') + "/api/runs")
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    fun upload(run: RunIngest): String {
        val body = json.encodeToString(RunIngest.serializer(), run)
        val request = HttpRequest.newBuilder(runsUri)
            .timeout(Duration.ofSeconds(30))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()

        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) {
            throw IOException("report upload to $runsUri failed: HTTP ${response.statusCode()} ${response.body()}")
        }
        return json.decodeFromString(CreatedResponse.serializer(), response.body()).id
    }

    @Serializable
    private data class CreatedResponse(val id: String)
}
