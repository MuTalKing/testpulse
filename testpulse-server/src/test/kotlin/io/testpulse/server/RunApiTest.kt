package io.testpulse.server

import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import io.testpulse.report.model.RunIngest
import io.testpulse.report.model.TestResultIngest
import io.testpulse.server.db.Database
import io.testpulse.server.db.RunRepository
import io.testpulse.server.model.RunCreated
import io.testpulse.server.model.RunDetail
import io.testpulse.server.model.RunSummary
import io.testpulse.server.model.TestHistoryEntry
import io.testpulse.server.model.UiConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class RunApiTest {

    private fun freshRepository(): RunRepository {
        val url = "jdbc:h2:mem:tp_${UUID.randomUUID().toString().replace("-", "")};MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
        return RunRepository(Database.hikari(url, "sa", "")).apply { init() }
    }

    private val sampleRun = RunIngest(
        project = "demo",
        environment = "ci",
        startedAt = 1_000,
        finishedAt = 2_000,
        branch = "main",
        tests = listOf(
            TestResultIngest(testId = "p.C#a", fullName = "p.C#a", name = "a", status = "passed", durationMs = 100),
            TestResultIngest(testId = "p.C#b", fullName = "p.C#b", name = "b", status = "failed", durationMs = 200, message = "boom", flaky = true),
            TestResultIngest(testId = "p.C#c", fullName = "p.C#c", name = "c", status = "skipped", durationMs = 0),
        ),
    )

    @Test
    fun `post a run, then read it back with computed totals`() = testApplication {
        val repository = freshRepository()
        application { module(repository) }
        val client = createClient { install(ContentNegotiation) { json() } }

        val created = client.post("/api/runs") {
            contentType(ContentType.Application.Json)
            setBody(sampleRun)
        }.let {
            assertEquals(HttpStatusCode.Created, it.status)
            it.body<RunCreated>()
        }

        val detail = client.get("/api/runs/${created.id}").let {
            assertEquals(HttpStatusCode.OK, it.status)
            it.body<RunDetail>()
        }
        assertEquals(3, detail.run.total)
        assertEquals(1, detail.run.passed)
        assertEquals(1, detail.run.failed)
        assertEquals(1, detail.run.skipped)
        assertEquals("demo", detail.run.project)
        assertEquals(3, detail.tests.size)
        assertTrue(detail.tests.any { it.name == "b" && it.flaky && it.message == "boom" })
    }

    @Test
    fun `list runs filters by project`() = testApplication {
        val repository = freshRepository()
        application { module(repository) }
        val client = createClient { install(ContentNegotiation) { json() } }

        client.post("/api/runs") { contentType(ContentType.Application.Json); setBody(sampleRun) }
        client.post("/api/runs") { contentType(ContentType.Application.Json); setBody(sampleRun.copy(project = "other")) }

        val demo = client.get("/api/runs?project=demo").body<List<RunSummary>>()
        assertEquals(1, demo.size)
        assertEquals("demo", demo.single().project)

        val all = client.get("/api/runs").body<List<RunSummary>>()
        assertEquals(2, all.size)
    }

    @Test
    fun `test history returns per-run outcomes`() = testApplication {
        val repository = freshRepository()
        application { module(repository) }
        val client = createClient { install(ContentNegotiation) { json() } }

        client.post("/api/runs") { contentType(ContentType.Application.Json); setBody(sampleRun) }
        client.post("/api/runs") { contentType(ContentType.Application.Json); setBody(sampleRun) }

        val history = client.get("/api/tests/p.C%23a/history").body<List<TestHistoryEntry>>()
        assertEquals(2, history.size)
        assertTrue(history.all { it.status == "passed" })
    }

    @Test
    fun `config endpoint exposes ui links`() = testApplication {
        application { module(freshRepository(), ServerConfig("http://grafana:3000", null)) }
        val client = createClient { install(ContentNegotiation) { json() } }

        val cfg = client.get("/api/config").body<UiConfig>()
        assertEquals("http://grafana:3000", cfg.grafanaUrl)
    }

    @Test
    fun `missing run is 404`() = testApplication {
        val repository = freshRepository()
        application { module(repository) }
        val client = createClient { install(ContentNegotiation) { json() } }

        assertEquals(HttpStatusCode.NotFound, client.get("/api/runs/does-not-exist").status)
    }
}
