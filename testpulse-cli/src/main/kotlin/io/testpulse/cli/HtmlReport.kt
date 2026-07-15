package io.testpulse.cli

import io.testpulse.report.model.RunIngest
import io.testpulse.report.model.TestResultIngest

/**
 * Renders a run into a single self-contained static HTML file — no backend, no JS runtime, no
 * external requests. Attachments are embedded as `data:` URIs. This is the zero-infra entry point:
 * try TestPulse in five minutes, then graduate to the server for history and dashboards.
 */
object HtmlReport {

    fun render(run: RunIngest): String {
        val byStatus = run.tests.groupingBy { it.status.lowercase() }.eachCount()
        val passed = byStatus["passed"] ?: 0
        val failed = byStatus["failed"] ?: 0
        val broken = byStatus["broken"] ?: 0
        val skipped = byStatus["skipped"] ?: 0

        val title = listOfNotNull(run.project, run.environment).joinToString(" / ").ifEmpty { "TestPulse report" }

        val rows = run.tests.joinToString("\n") { testRow(it) }

        return """
<!doctype html>
<html lang="en"><head>
<meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>TestPulse — ${esc(title)}</title>
<style>${CSS}</style>
</head><body>
<div class="wrap">
  <header><span class="brand">TestPulse</span> <span class="muted">static report</span></header>
  <div class="card">
    <h1>${esc(title)}</h1>
    <div class="muted">${esc(run.branch ?: "")}${run.gitSha?.let { " · " + esc(it.take(8)) } ?: ""}</div>
    <div class="counts">
      <span class="count">total <b>${run.tests.size}</b></span>
      <span class="count passed">passed <b>$passed</b></span>
      <span class="count failed">failed <b>$failed</b></span>
      <span class="count broken">broken <b>$broken</b></span>
      <span class="count skipped">skipped <b>$skipped</b></span>
    </div>
  </div>
  <table>
    <thead><tr><th>Status</th><th>Test</th><th>Duration</th></tr></thead>
    <tbody>
$rows
    </tbody>
  </table>
</div>
</body></html>
""".trimIndent()
    }

    private fun testRow(test: TestResultIngest): String {
        val status = test.status.lowercase()
        val flaky = if (test.flaky) """ <span class="badge flaky">flaky</span>""" else ""
        val failInfo = if (status == "failed" || status == "broken") {
            val msg = test.message?.let { """<div class="msg">${esc(it)}</div>""" } ?: ""
            val trace = test.trace?.let { """<details><summary>trace</summary><pre>${esc(it)}</pre></details>""" } ?: ""
            msg + trace
        } else {
            ""
        }
        val attachments = test.attachments.joinToString("") { attachment ->
            val type = attachment.type ?: "application/octet-stream"
            val dataUri = "data:$type;base64,${attachment.contentBase64}"
            if (type.startsWith("image/")) {
                """<a href="$dataUri" target="_blank"><img class="thumb" src="$dataUri" alt="${esc(attachment.name)}"></a>"""
            } else {
                """<a class="btn" href="$dataUri" download="${esc(attachment.name ?: "attachment")}">${esc(attachment.name ?: "attachment")}</a>"""
            }
        }
        val attBlock = if (attachments.isNotEmpty()) """<div class="atts">$attachments</div>""" else ""
        val name = esc(test.name ?: test.fullName ?: test.testId ?: "—")

        return """      <tr>
        <td><span class="badge $status">${esc(status)}</span>$flaky</td>
        <td>$name$failInfo$attBlock</td>
        <td>${fmtDuration(test.durationMs)}</td>
      </tr>"""
    }

    private fun fmtDuration(ms: Long): String =
        if (ms < 1000) "${ms}ms" else String.format(java.util.Locale.ROOT, "%.2fs", ms / 1000.0)

    private fun esc(s: String?): String = (s ?: "")
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

    private val CSS = """
:root{--bg:#f6f7f9;--surface:#fff;--border:#e3e6ea;--text:#1c2024;--muted:#6b7280;--green:#16a34a;--red:#dc2626;--amber:#d97706;--slate:#64748b}
@media(prefers-color-scheme:dark){:root{--bg:#0f1115;--surface:#171a21;--border:#262b33;--text:#e6e8eb;--muted:#9aa4b2;--green:#4ade80;--red:#f87171;--amber:#fbbf24;--slate:#94a3b8}}
*{box-sizing:border-box}body{margin:0;font:14px/1.5 system-ui,-apple-system,Segoe UI,Roboto,sans-serif;background:var(--bg);color:var(--text)}
.wrap{max-width:1000px;margin:24px auto;padding:0 20px}
header{margin-bottom:16px}.brand{font-weight:700;font-size:18px}.muted{color:var(--muted)}
h1{font-size:20px;margin:0 0 4px}
.card{background:var(--surface);border:1px solid var(--border);border-radius:10px;padding:16px;margin-bottom:16px}
.counts{display:flex;gap:14px;flex-wrap:wrap;margin-top:8px}.count b{font-variant-numeric:tabular-nums}
.count.passed b{color:var(--green)}.count.failed b{color:var(--red)}.count.broken b{color:var(--amber)}.count.skipped b{color:var(--slate)}
table{width:100%;border-collapse:collapse;background:var(--surface);border:1px solid var(--border);border-radius:10px;overflow:hidden}
th,td{text-align:left;padding:10px 14px;border-bottom:1px solid var(--border);vertical-align:top}
th{font-size:12px;text-transform:uppercase;letter-spacing:.03em;color:var(--muted)}
tbody tr:last-child td{border-bottom:none}
.badge{display:inline-block;padding:2px 9px;border-radius:999px;font-size:12px;font-weight:600}
.badge.passed{color:var(--green);background:color-mix(in srgb,var(--green) 15%,transparent)}
.badge.failed{color:var(--red);background:color-mix(in srgb,var(--red) 15%,transparent)}
.badge.broken{color:var(--amber);background:color-mix(in srgb,var(--amber) 15%,transparent)}
.badge.skipped{color:var(--slate);background:color-mix(in srgb,var(--slate) 15%,transparent)}
.badge.flaky{color:var(--amber);background:color-mix(in srgb,var(--amber) 15%,transparent)}
.msg{color:var(--red);font-size:13px;margin-top:4px}
pre{margin:6px 0 0;padding:10px;background:var(--bg);border:1px solid var(--border);border-radius:7px;overflow-x:auto;font-size:12px}
details summary{cursor:pointer;color:var(--muted);font-size:12px;margin-top:4px}
.atts{display:flex;gap:8px;flex-wrap:wrap;margin-top:8px;align-items:center}
.thumb{height:56px;max-width:120px;object-fit:cover;border:1px solid var(--border);border-radius:6px;display:block}
.btn{display:inline-block;padding:4px 10px;border:1px solid var(--border);border-radius:7px;font-size:12px;text-decoration:none;color:var(--text)}
""".trim()
}
