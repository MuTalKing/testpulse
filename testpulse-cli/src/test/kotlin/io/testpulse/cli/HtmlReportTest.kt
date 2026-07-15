package io.testpulse.cli

import io.testpulse.report.model.AttachmentIngest
import io.testpulse.report.model.RunIngest
import io.testpulse.report.model.TestResultIngest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HtmlReportTest {

    @Test
    fun `renders totals, attachments, and escapes html`() {
        val run = RunIngest(
            project = "shop",
            environment = "ci",
            branch = "main",
            tests = listOf(
                TestResultIngest(
                    testId = "a", name = "checkout <script>", status = "failed", durationMs = 250,
                    message = "expected <b>", flaky = true,
                    attachments = listOf(AttachmentIngest(name = "shot.png", type = "image/png", contentBase64 = "QUJD")),
                ),
                TestResultIngest(testId = "b", name = "ok", status = "passed", durationMs = 1500),
            ),
        )

        val html = HtmlReport.render(run)

        assertTrue(html.startsWith("<!doctype html>"), "is a full HTML document")
        assertTrue(html.contains("shop / ci"))
        assertTrue(html.contains("passed <b>1</b>"))
        assertTrue(html.contains("failed <b>1</b>"))

        // HTML in test data must be escaped, never emitted raw
        assertFalse(html.contains("checkout <script>"), "raw markup must not survive")
        assertTrue(html.contains("checkout &lt;script&gt;"))
        assertTrue(html.contains("expected &lt;b&gt;"))

        // attachment embedded inline, no external reference
        assertTrue(html.contains("data:image/png;base64,QUJD"))
        assertTrue(html.contains("flaky"))
        assertTrue(html.contains("1.50s"))
        assertTrue(html.contains("250ms"))
    }

    @Test
    fun `report is fully self-contained - no external requests`() {
        val html = HtmlReport.render(RunIngest(tests = listOf(TestResultIngest(status = "passed"))))
        assertFalse(html.contains("http://"), "no external http references")
        assertFalse(html.contains("https://"), "no external https references")
    }
}
