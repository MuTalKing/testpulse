package io.testpulse.report.model

import kotlinx.serialization.Serializable

/**
 * The run-ingest contract shared by the CLI (sender) and the server (receiver).
 *
 * Kept in a dedicated module so the two sides cannot drift apart.
 */
@Serializable
data class RunIngest(
    val project: String? = null,
    val environment: String? = null,
    val startedAt: Long? = null,   // epoch millis
    val finishedAt: Long? = null,  // epoch millis
    val branch: String? = null,
    val gitSha: String? = null,
    val buildUrl: String? = null,
    val tests: List<TestResultIngest> = emptyList(),
)

@Serializable
data class TestResultIngest(
    val testId: String? = null,
    val fullName: String? = null,
    val name: String? = null,
    val status: String,          // passed | failed | broken | skipped
    val durationMs: Long = 0,
    val message: String? = null,
    val trace: String? = null,
    val retries: Int = 0,
    val flaky: Boolean = false,
    val attachments: List<AttachmentIngest> = emptyList(),
)

/** An attachment blob inlined as base64. Simple for typical screenshots/logs; large blobs later. */
@Serializable
data class AttachmentIngest(
    val name: String? = null,
    val type: String? = null,        // mime type, e.g. image/png
    val contentBase64: String,
)
