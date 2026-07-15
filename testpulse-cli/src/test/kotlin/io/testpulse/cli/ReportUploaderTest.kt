package io.testpulse.cli

import com.sun.net.httpserver.HttpServer
import io.testpulse.report.model.RunIngest
import io.testpulse.report.model.TestResultIngest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList

class ReportUploaderTest {

    @Test
    fun `posts run json to api runs and returns the created id`() {
        val requests = CopyOnWriteArrayList<Pair<String, String>>() // path, body
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/api/runs") { exchange ->
            val body = exchange.requestBody.readBytes().toString(Charsets.UTF_8)
            requests.add(exchange.requestURI.path to body)
            val response = """{"id":"run-42"}""".toByteArray()
            exchange.sendResponseHeaders(201, response.size.toLong())
            exchange.responseBody.use { it.write(response) }
        }
        server.start()

        try {
            val uploader = ReportUploader("http://127.0.0.1:${server.address.port}")
            val id = uploader.upload(
                RunIngest(
                    project = "shop",
                    tests = listOf(TestResultIngest(testId = "orders.checkout", status = "passed", durationMs = 250)),
                ),
            )

            assertEquals("run-42", id)
            assertEquals(1, requests.size)
            val (path, body) = requests.single()
            assertEquals("/api/runs", path)
            assertTrue(body.contains("\"project\":\"shop\""), body)
            assertTrue(body.contains("\"testId\":\"orders.checkout\""), body)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `throws on a non-2xx response`() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/api/runs") { exchange ->
            exchange.sendResponseHeaders(500, -1)
            exchange.close()
        }
        server.start()

        try {
            val uploader = ReportUploader("http://127.0.0.1:${server.address.port}")
            assertThrows(IOException::class.java) {
                uploader.upload(RunIngest(project = "x", tests = listOf(TestResultIngest(status = "passed"))))
            }
        } finally {
            server.stop(0)
        }
    }
}
