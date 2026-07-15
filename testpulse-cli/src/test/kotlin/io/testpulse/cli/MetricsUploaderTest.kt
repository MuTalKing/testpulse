package io.testpulse.cli

import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.io.IOException
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList

class MetricsUploaderTest {

    @Test
    fun `posts stamped lines to write endpoint with millisecond precision`() {
        val requests = CopyOnWriteArrayList<Triple<String, String, String>>() // path, query, body
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/write") { exchange ->
            val body = exchange.requestBody.readBytes().toString(Charsets.UTF_8)
            requests.add(Triple(exchange.requestURI.path, exchange.requestURI.query, body))
            exchange.sendResponseHeaders(204, -1)
            exchange.close()
        }
        server.start()

        try {
            val uploader = MetricsUploader("http://127.0.0.1:${server.address.port}")
            val count = uploader.upload(
                listOf(
                    "autotest,test_id=a duration_seconds=1.0,passed=1i 100",
                    "autotest,test_id=b duration_seconds=2.0,passed=0i 100",
                ),
            )

            assertEquals(2, count)
            assertEquals(1, requests.size)
            val (path, query, body) = requests.single()
            assertEquals("/write", path)
            assertEquals("precision=ms", query)
            assertEquals(2, body.lines().count { it.isNotBlank() })
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `throws on a non-2xx response`() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/write") { exchange ->
            exchange.sendResponseHeaders(400, -1)
            exchange.close()
        }
        server.start()

        try {
            val uploader = MetricsUploader("http://127.0.0.1:${server.address.port}")
            assertThrows(IOException::class.java) {
                uploader.upload(listOf("autotest,test_id=a duration_seconds=1.0,passed=1i 100"))
            }
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `empty input uploads nothing`() {
        val uploader = MetricsUploader("http://127.0.0.1:1")
        assertEquals(0, uploader.upload(emptyList()))
    }
}
