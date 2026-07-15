package io.testpulse.server.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import javax.sql.DataSource

/** DataSource factory. Configuration comes from env vars, with a local-Postgres default. */
object Database {

    fun fromEnv(): DataSource {
        val url = System.getenv("TESTPULSE_DB_URL")
            ?: "jdbc:postgresql://localhost:5432/testpulse"
        val user = System.getenv("TESTPULSE_DB_USER") ?: "testpulse"
        val password = System.getenv("TESTPULSE_DB_PASSWORD") ?: "testpulse"
        return hikari(url, user, password)
    }

    fun hikari(jdbcUrl: String, user: String, password: String): HikariDataSource {
        val config = HikariConfig().apply {
            this.jdbcUrl = jdbcUrl
            this.username = user
            this.password = password
            maximumPoolSize = 8
            poolName = "testpulse"
        }
        return HikariDataSource(config)
    }
}
