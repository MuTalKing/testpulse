package io.testpulse.server

import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.http.content.staticResources
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.testpulse.report.model.RunIngest
import io.testpulse.server.db.Database
import io.testpulse.server.db.RunRepository
import io.testpulse.server.model.RunCreated
import io.testpulse.server.model.UiConfig
import kotlinx.serialization.json.Json

/** External links the UI needs. */
data class ServerConfig(val grafanaUrl: String?, val allureUrl: String?) {
    companion object {
        fun fromEnv() = ServerConfig(
            grafanaUrl = System.getenv("TESTPULSE_GRAFANA_URL") ?: "http://localhost:3000",
            allureUrl = System.getenv("TESTPULSE_ALLURE_URL"),
        )
    }
}

fun Application.module(repository: RunRepository, config: ServerConfig = ServerConfig(null, null)) {
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true; encodeDefaults = true })
    }

    routing {
        get("/health") { call.respondText("OK") }

        get("/api/config") { call.respond(UiConfig(config.grafanaUrl, config.allureUrl)) }

        post("/api/runs") {
            val ingest = call.receive<RunIngest>()
            val id = repository.insertRun(ingest)
            call.respond(HttpStatusCode.Created, RunCreated(id))
        }

        get("/api/runs") {
            val project = call.request.queryParameters["project"]
            val environment = call.request.queryParameters["environment"]
            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 50
            call.respond(repository.listRuns(project, environment, limit))
        }

        get("/api/runs/{id}") {
            val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
            val detail = repository.getRun(id)
            if (detail == null) call.respond(HttpStatusCode.NotFound) else call.respond(detail)
        }

        get("/api/tests/{testId}/history") {
            val testId = call.parameters["testId"] ?: return@get call.respond(HttpStatusCode.BadRequest)
            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 50
            call.respond(repository.testHistory(testId, limit))
        }

        // Report UI (static, vanilla JS calling the API above). Registered last so it never
        // shadows the /api routes.
        staticResources("/", "web") {
            default("index.html")
        }
    }
}

fun main() {
    val repository = RunRepository(Database.fromEnv()).apply { init() }
    val config = ServerConfig.fromEnv()
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    embeddedServer(Netty, port = port, host = "0.0.0.0") { module(repository, config) }.start(wait = true)
}
