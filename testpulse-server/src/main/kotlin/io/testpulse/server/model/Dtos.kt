package io.testpulse.server.model

import kotlinx.serialization.Serializable

// Ingest DTOs (RunIngest, TestResultIngest) live in the shared :testpulse-report-model module.

@Serializable
data class RunCreated(val id: String)

/** UI bootstrap config: external links the report UI needs (metrics dashboard, Allure report). */
@Serializable
data class UiConfig(
    val grafanaUrl: String? = null,
    val allureUrl: String? = null,
)

@Serializable
data class RunSummary(
    val id: String,
    val project: String?,
    val environment: String?,
    val startedAt: Long?,
    val finishedAt: Long?,
    val branch: String?,
    val gitSha: String?,
    val buildUrl: String?,
    val total: Int,
    val passed: Int,
    val failed: Int,
    val broken: Int,
    val skipped: Int,
)

@Serializable
data class RunDetail(
    val run: RunSummary,
    val tests: List<TestResultView>,
)

@Serializable
data class TestResultView(
    val testId: String?,
    val fullName: String?,
    val name: String?,
    val status: String,
    val durationMs: Long,
    val message: String?,
    val trace: String?,
    val retries: Int,
    val flaky: Boolean,
    val attachments: List<AttachmentView> = emptyList(),
)

@Serializable
data class AttachmentView(
    val id: String,
    val name: String?,
    val type: String?,
)

/** One point in a single test's cross-run history. */
@Serializable
data class TestHistoryEntry(
    val runId: String,
    val finishedAt: Long?,
    val status: String,
    val durationMs: Long,
    val flaky: Boolean,
)
