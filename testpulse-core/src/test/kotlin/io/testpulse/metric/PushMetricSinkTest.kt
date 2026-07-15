package io.testpulse.metric

import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList

class PushMetricSinkTest {

    @Test
    fun `flushes buffered samples to the write endpoint as line protocol`() {
        val requests = CopyOnWriteArrayList<Pair<String, String>>() // path to body
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/write") { exchange ->
            val body = exchange.requestBody.readBytes().toString(Charsets.UTF_8)
            requests.add(exchange.requestURI.path to body)
            exchange.sendResponseHeaders(204, -1)
            exchange.close()
        }
        server.start()

        try {
            val sink = PushMetricSink("http://127.0.0.1:${server.address.port}")
            sink.emit(sample("orders.a"))
            sink.emit(sample("orders.b"))
            sink.flush()

            assertEquals(1, requests.size, "one batched POST")
            val (path, body) = requests.single()
            assertEquals("/write", path)
            assertTrue(body.contains("test_id=orders.a"), body)
            assertTrue(body.contains("test_id=orders.b"), body)
            assertEquals(2, body.lines().count { it.isNotBlank() }, "two line-protocol records: $body")
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `a down endpoint never throws`() {
        // Nothing listening on this port — emit + flush must swallow the failure.
        val sink = PushMetricSink("http://127.0.0.1:1")
        sink.emit(sample("x"))
        sink.flush()
    }

    private fun sample(id: String) = MetricSample(
        testId = id,
        testClass = "C",
        suite = null,
        project = "p",
        environment = "ci",
        durationSeconds = 1.0,
        passed = true,
    )
}
