package io.testpulse.server.db

import io.testpulse.report.model.RunIngest
import io.testpulse.report.model.TestResultIngest
import io.testpulse.server.model.AttachmentView
import io.testpulse.server.model.RunDetail
import io.testpulse.server.model.RunSummary
import io.testpulse.server.model.TestHistoryEntry
import io.testpulse.server.model.TestResultView
import io.testpulse.server.storage.Blob
import io.testpulse.server.storage.BlobStore
import java.sql.ResultSet
import java.util.Base64
import java.util.UUID
import javax.sql.DataSource

/**
 * Persistence for runs, test results and attachments. Plain JDBC with portable SQL (Postgres in
 * production, H2 in PostgreSQL mode in tests). Attachment blobs go to the [BlobStore]; only their
 * metadata lives in the database.
 */
class RunRepository(
    private val dataSource: DataSource,
    private val blobStore: BlobStore,
) {

    fun init() {
        dataSource.connection.use { conn ->
            conn.createStatement().use { st ->
                st.executeUpdate(
                    """
                    CREATE TABLE IF NOT EXISTS runs (
                        id           VARCHAR(36) PRIMARY KEY,
                        project      VARCHAR(255),
                        environment  VARCHAR(255),
                        started_at   BIGINT,
                        finished_at  BIGINT,
                        branch       VARCHAR(255),
                        git_sha      VARCHAR(64),
                        build_url    VARCHAR(1024),
                        allure_url   VARCHAR(1024),
                        total        INT NOT NULL,
                        passed       INT NOT NULL,
                        failed       INT NOT NULL,
                        broken       INT NOT NULL,
                        skipped      INT NOT NULL
                    )
                    """.trimIndent(),
                )
                st.executeUpdate(
                    """
                    CREATE TABLE IF NOT EXISTS test_results (
                        id           VARCHAR(36) PRIMARY KEY,
                        run_id       VARCHAR(36) NOT NULL REFERENCES runs(id),
                        test_id      VARCHAR(512),
                        full_name    VARCHAR(1024),
                        name         VARCHAR(1024),
                        status       VARCHAR(32),
                        duration_ms  BIGINT NOT NULL,
                        message      TEXT,
                        trace        TEXT,
                        retries      INT NOT NULL DEFAULT 0,
                        flaky        BOOLEAN NOT NULL DEFAULT FALSE
                    )
                    """.trimIndent(),
                )
                st.executeUpdate(
                    """
                    CREATE TABLE IF NOT EXISTS attachments (
                        id             VARCHAR(36) PRIMARY KEY,
                        test_result_id VARCHAR(36) NOT NULL REFERENCES test_results(id),
                        name           VARCHAR(512),
                        type           VARCHAR(255),
                        storage_key    VARCHAR(128) NOT NULL,
                        size           BIGINT NOT NULL
                    )
                    """.trimIndent(),
                )
                // For databases created before allure_url existed.
                st.executeUpdate("ALTER TABLE runs ADD COLUMN IF NOT EXISTS allure_url VARCHAR(1024)")
                st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_test_results_run ON test_results(run_id)")
                st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_test_results_testid ON test_results(test_id)")
                st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_attachments_tr ON attachments(test_result_id)")
            }
        }
    }

    fun insertRun(ingest: RunIngest): String {
        val runId = UUID.randomUUID().toString()
        val totals = Totals.of(ingest.tests)

        dataSource.connection.use { conn ->
            conn.autoCommit = false
            try {
                conn.prepareStatement(
                    """
                    INSERT INTO runs (id, project, environment, started_at, finished_at, branch, git_sha, build_url,
                                      allure_url, total, passed, failed, broken, skipped)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """.trimIndent(),
                ).use { ps ->
                    ps.setString(1, runId)
                    ps.setString(2, ingest.project)
                    ps.setString(3, ingest.environment)
                    ps.setNullableLong(4, ingest.startedAt)
                    ps.setNullableLong(5, ingest.finishedAt)
                    ps.setString(6, ingest.branch)
                    ps.setString(7, ingest.gitSha)
                    ps.setString(8, ingest.buildUrl)
                    ps.setString(9, ingest.allureReportUrl)
                    ps.setInt(10, totals.total)
                    ps.setInt(11, totals.passed)
                    ps.setInt(12, totals.failed)
                    ps.setInt(13, totals.broken)
                    ps.setInt(14, totals.skipped)
                    ps.executeUpdate()
                }

                for (test in ingest.tests) {
                    val testResultId = UUID.randomUUID().toString()
                    insertTestResult(conn, testResultId, runId, test)
                    insertAttachments(conn, testResultId, test)
                }

                conn.commit()
            } catch (e: Exception) {
                conn.rollback()
                throw e
            } finally {
                conn.autoCommit = true
            }
        }
        return runId
    }

    private fun insertTestResult(conn: java.sql.Connection, id: String, runId: String, test: TestResultIngest) {
        conn.prepareStatement(
            """
            INSERT INTO test_results (id, run_id, test_id, full_name, name, status, duration_ms,
                                      message, trace, retries, flaky)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
        ).use { ps ->
            ps.setString(1, id)
            ps.setString(2, runId)
            ps.setString(3, test.testId)
            ps.setString(4, test.fullName)
            ps.setString(5, test.name)
            ps.setString(6, test.status.lowercase())
            ps.setLong(7, test.durationMs)
            ps.setString(8, test.message)
            ps.setString(9, test.trace)
            ps.setInt(10, test.retries)
            ps.setBoolean(11, test.flaky)
            ps.executeUpdate()
        }
    }

    private fun insertAttachments(conn: java.sql.Connection, testResultId: String, test: TestResultIngest) {
        if (test.attachments.isEmpty()) return
        conn.prepareStatement(
            "INSERT INTO attachments (id, test_result_id, name, type, storage_key, size) VALUES (?, ?, ?, ?, ?, ?)",
        ).use { ps ->
            for (attachment in test.attachments) {
                val bytes = Base64.getDecoder().decode(attachment.contentBase64)
                val key = UUID.randomUUID().toString()
                blobStore.put(key, bytes, attachment.type)
                ps.setString(1, UUID.randomUUID().toString())
                ps.setString(2, testResultId)
                ps.setString(3, attachment.name)
                ps.setString(4, attachment.type)
                ps.setString(5, key)
                ps.setLong(6, bytes.size.toLong())
                ps.addBatch()
            }
            ps.executeBatch()
        }
    }

    fun listRuns(project: String?, environment: String?, limit: Int): List<RunSummary> {
        val where = buildList {
            if (project != null) add("project = ?")
            if (environment != null) add("environment = ?")
        }.joinToString(" AND ").ifEmpty { "1 = 1" }

        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "SELECT * FROM runs WHERE $where ORDER BY finished_at DESC NULLS LAST LIMIT ?",
            ).use { ps ->
                var idx = 1
                if (project != null) ps.setString(idx++, project)
                if (environment != null) ps.setString(idx++, environment)
                ps.setInt(idx, limit)
                return ps.executeQuery().map { it.toRunSummary() }
            }
        }
    }

    fun getRun(id: String): RunDetail? {
        dataSource.connection.use { conn ->
            val summary = conn.prepareStatement("SELECT * FROM runs WHERE id = ?").use { ps ->
                ps.setString(1, id)
                ps.executeQuery().map { it.toRunSummary() }.firstOrNull()
            } ?: return null

            val rows = conn.prepareStatement(
                "SELECT * FROM test_results WHERE run_id = ? ORDER BY status, full_name",
            ).use { ps ->
                ps.setString(1, id)
                ps.executeQuery().map { it.getString("id") to it.toTestResultView() }
            }

            val attachmentsByTest = conn.prepareStatement(
                """
                SELECT a.id, a.test_result_id, a.name, a.type
                FROM attachments a JOIN test_results tr ON tr.id = a.test_result_id
                WHERE tr.run_id = ?
                """.trimIndent(),
            ).use { ps ->
                ps.setString(1, id)
                ps.executeQuery().map {
                    it.getString("test_result_id") to AttachmentView(it.getString("id"), it.getString("name"), it.getString("type"))
                }.groupBy({ it.first }, { it.second })
            }

            val tests = rows.map { (testResultId, view) ->
                view.copy(attachments = attachmentsByTest[testResultId] ?: emptyList())
            }
            return RunDetail(summary, tests)
        }
    }

    fun testHistory(testId: String, limit: Int): List<TestHistoryEntry> {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """
                SELECT tr.run_id, r.finished_at, tr.status, tr.duration_ms, tr.flaky
                FROM test_results tr
                JOIN runs r ON r.id = tr.run_id
                WHERE tr.test_id = ?
                ORDER BY r.finished_at DESC NULLS LAST
                LIMIT ?
                """.trimIndent(),
            ).use { ps ->
                ps.setString(1, testId)
                ps.setInt(2, limit)
                return ps.executeQuery().map {
                    TestHistoryEntry(
                        runId = it.getString("run_id"),
                        finishedAt = it.getNullableLong("finished_at"),
                        status = it.getString("status"),
                        durationMs = it.getLong("duration_ms"),
                        flaky = it.getBoolean("flaky"),
                    )
                }
            }
        }
    }

    /** Fetch an attachment's bytes for the download endpoint. */
    fun readAttachment(id: String): Blob? {
        val meta = dataSource.connection.use { conn ->
            conn.prepareStatement("SELECT storage_key, type FROM attachments WHERE id = ?").use { ps ->
                ps.setString(1, id)
                ps.executeQuery().map { it.getString("storage_key") to it.getString("type") }.firstOrNull()
            }
        } ?: return null

        val (key, type) = meta
        val blob = blobStore.get(key) ?: return null
        return Blob(blob.bytes, type ?: blob.contentType)
    }

    private fun ResultSet.toRunSummary() = RunSummary(
        id = getString("id"),
        project = getString("project"),
        environment = getString("environment"),
        startedAt = getNullableLong("started_at"),
        finishedAt = getNullableLong("finished_at"),
        branch = getString("branch"),
        gitSha = getString("git_sha"),
        buildUrl = getString("build_url"),
        allureUrl = getString("allure_url"),
        total = getInt("total"),
        passed = getInt("passed"),
        failed = getInt("failed"),
        broken = getInt("broken"),
        skipped = getInt("skipped"),
    )

    private fun ResultSet.toTestResultView() = TestResultView(
        testId = getString("test_id"),
        fullName = getString("full_name"),
        name = getString("name"),
        status = getString("status"),
        durationMs = getLong("duration_ms"),
        message = getString("message"),
        trace = getString("trace"),
        retries = getInt("retries"),
        flaky = getBoolean("flaky"),
    )

    private data class Totals(val total: Int, val passed: Int, val failed: Int, val broken: Int, val skipped: Int) {
        companion object {
            fun of(tests: List<TestResultIngest>): Totals {
                val byStatus = tests.groupingBy { it.status.lowercase() }.eachCount()
                return Totals(
                    total = tests.size,
                    passed = byStatus["passed"] ?: 0,
                    failed = byStatus["failed"] ?: 0,
                    broken = byStatus["broken"] ?: 0,
                    skipped = byStatus["skipped"] ?: 0,
                )
            }
        }
    }
}

private fun java.sql.PreparedStatement.setNullableLong(index: Int, value: Long?) {
    if (value == null) setNull(index, java.sql.Types.BIGINT) else setLong(index, value)
}

private fun ResultSet.getNullableLong(column: String): Long? {
    val value = getLong(column)
    return if (wasNull()) null else value
}

private inline fun <T> ResultSet.map(transform: (ResultSet) -> T): List<T> {
    val results = ArrayList<T>()
    use { rs ->
        while (rs.next()) results.add(transform(rs))
    }
    return results
}
