package io.testpulse.server.db

import io.testpulse.report.model.RunIngest
import io.testpulse.report.model.TestResultIngest
import io.testpulse.server.model.RunDetail
import io.testpulse.server.model.RunSummary
import io.testpulse.server.model.TestHistoryEntry
import io.testpulse.server.model.TestResultView
import java.sql.Connection
import java.sql.ResultSet
import java.util.UUID
import javax.sql.DataSource

/**
 * Persistence for runs and their test results. Plain JDBC with portable SQL so the same schema
 * runs on PostgreSQL (production) and H2 in PostgreSQL mode (tests).
 */
class RunRepository(private val dataSource: DataSource) {

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
                st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_test_results_run ON test_results(run_id)")
                st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_test_results_testid ON test_results(test_id)")
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
                                      total, passed, failed, broken, skipped)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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
                    ps.setInt(9, totals.total)
                    ps.setInt(10, totals.passed)
                    ps.setInt(11, totals.failed)
                    ps.setInt(12, totals.broken)
                    ps.setInt(13, totals.skipped)
                    ps.executeUpdate()
                }

                conn.prepareStatement(
                    """
                    INSERT INTO test_results (id, run_id, test_id, full_name, name, status, duration_ms,
                                              message, trace, retries, flaky)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """.trimIndent(),
                ).use { ps ->
                    for (test in ingest.tests) {
                        ps.setString(1, UUID.randomUUID().toString())
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
                        ps.addBatch()
                    }
                    ps.executeBatch()
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

            val tests = conn.prepareStatement(
                "SELECT * FROM test_results WHERE run_id = ? ORDER BY status, full_name",
            ).use { ps ->
                ps.setString(1, id)
                ps.executeQuery().map { it.toTestResultView() }
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

    private fun ResultSet.toRunSummary() = RunSummary(
        id = getString("id"),
        project = getString("project"),
        environment = getString("environment"),
        startedAt = getNullableLong("started_at"),
        finishedAt = getNullableLong("finished_at"),
        branch = getString("branch"),
        gitSha = getString("git_sha"),
        buildUrl = getString("build_url"),
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
