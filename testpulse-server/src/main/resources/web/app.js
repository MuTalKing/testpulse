"use strict";

const app = document.getElementById("app");
let config = { grafanaUrl: null, allureUrl: null };

// ---- helpers ---------------------------------------------------------------

function esc(s) {
    if (s === null || s === undefined) return "";
    return String(s).replace(/[&<>"']/g, c =>
        ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
}

async function api(path) {
    const res = await fetch(path);
    if (!res.ok) throw new Error("HTTP " + res.status);
    return res.json();
}

function fmtDate(ms) {
    if (!ms) return "—";
    return new Date(ms).toLocaleString();
}

function fmtDuration(ms) {
    if (ms == null) return "—";
    if (ms < 1000) return ms + "ms";
    return (ms / 1000).toFixed(2) + "s";
}

function statusBadge(status) {
    const s = esc(status || "unknown");
    return `<span class="badge ${s}">${s}</span>`;
}

function isText(type) {
    return !!type && (type.startsWith("text/") || type.includes("json") || type.includes("xml") || type.includes("yaml"));
}

function metricsLink(testId) {
    if (!config.grafanaUrl || !testId) return "";
    const url = `${config.grafanaUrl}/d/testpulse-overview?var-test_id=${encodeURIComponent(testId)}`;
    return `<a class="btn" href="${esc(url)}" target="_blank" rel="noopener">Metrics</a>`;
}

function loading() { app.innerHTML = `<p class="muted">Loading…</p>`; }
function fail(e) { app.innerHTML = `<div class="empty">Failed to load: ${esc(e.message)}</div>`; }

// ---- views -----------------------------------------------------------------

async function viewRuns() {
    loading();
    const params = new URLSearchParams(location.hash.split("?")[1] || "");
    const project = params.get("project") || "";
    const query = project ? `?project=${encodeURIComponent(project)}` : "";
    let runs;
    try { runs = await api("/api/runs" + query); } catch (e) { return fail(e); }

    const rows = runs.map(r => `
        <tr class="clickable" data-id="${esc(r.id)}">
            <td>${esc(fmtDate(r.finishedAt))}</td>
            <td>${esc(r.project || "—")}</td>
            <td>${esc(r.environment || "—")}</td>
            <td>${esc(r.branch || "—")}</td>
            <td>${r.total}</td>
            <td><span class="badge passed">${r.passed}</span></td>
            <td>${r.failed ? `<span class="badge failed">${r.failed}</span>` : "0"}</td>
            <td>${r.broken ? `<span class="badge broken">${r.broken}</span>` : "0"}</td>
            <td>${r.skipped ? `<span class="badge skipped">${r.skipped}</span>` : "0"}</td>
        </tr>`).join("");

    app.innerHTML = `
        <h1>Runs</h1>
        <div class="toolbar">
            <input type="text" id="project" placeholder="Filter by project…" value="${esc(project)}">
            <button class="btn" id="apply">Filter</button>
        </div>
        ${runs.length === 0 ? `<div class="empty">No runs yet.</div>` : `
        <div class="table-wrap"><table>
            <thead><tr>
                <th>Finished</th><th>Project</th><th>Environment</th><th>Branch</th>
                <th>Total</th><th>Passed</th><th>Failed</th><th>Broken</th><th>Skipped</th>
            </tr></thead>
            <tbody>${rows}</tbody>
        </table></div>`}`;

    app.querySelectorAll("tr.clickable").forEach(tr =>
        tr.addEventListener("click", () => { location.hash = "#/runs/" + tr.dataset.id; }));
    const applyFilter = () => {
        const v = document.getElementById("project").value.trim();
        location.hash = v ? `#/?project=${encodeURIComponent(v)}` : "#/";
    };
    document.getElementById("apply").addEventListener("click", applyFilter);
    document.getElementById("project").addEventListener("keydown", e => { if (e.key === "Enter") applyFilter(); });
}

async function viewRun(id) {
    loading();
    let detail;
    try { detail = await api("/api/runs/" + encodeURIComponent(id)); } catch (e) { return fail(e); }
    const r = detail.run;

    const testRows = detail.tests.map(t => {
        const failInfo = (t.status === "failed" || t.status === "broken")
            ? `${t.message ? `<div class="msg">${esc(t.message)}</div>` : ""}${t.trace ? `<details><summary>trace</summary><pre class="trace">${esc(t.trace)}</pre></details>` : ""}`
            : "";
        const atts = (t.attachments || []).map(a => {
            const url = `/api/attachments/${encodeURIComponent(a.id)}`;
            const label = esc(a.name || "attachment");
            if (a.type && a.type.startsWith("image/")) {
                return `<a href="${url}" target="_blank" rel="noopener" title="${label}"><img class="thumb" src="${url}" alt="${label}"></a>`;
            }
            if (isText(a.type)) {
                // Lazily fetched on expand (see the toggle listener below).
                return `<details class="att" data-url="${url}"><summary>${label}</summary><pre>…</pre></details>`;
            }
            return `<a class="btn" href="${url}" target="_blank" rel="noopener">${label}</a>`;
        }).join("");
        const attsBlock = atts ? `<div class="atts">${atts}</div>` : "";
        const histHref = t.testId ? `#/tests/${encodeURIComponent(t.testId)}` : null;
        return `
            <tr>
                <td>${statusBadge(t.status)}${t.flaky ? ` <span class="badge flaky">flaky</span>` : ""}</td>
                <td class="wrap">${esc(t.name || t.fullName || t.testId || "—")}${failInfo}${attsBlock}</td>
                <td>${esc(fmtDuration(t.durationMs))}</td>
                <td><div class="actions">
                    ${histHref ? `<a class="btn" href="${histHref}">History</a>` : ""}
                    ${metricsLink(t.testId)}
                </div></td>
            </tr>`;
    }).join("");

    app.innerHTML = `
        <a class="backlink btn" href="#/">← Runs</a>
        <div class="card">
            <h1>${esc(r.project || "run")} <span class="muted">/ ${esc(r.environment || "—")}</span></h1>
            <div class="muted">${esc(fmtDate(r.finishedAt))} · branch ${esc(r.branch || "—")}${r.gitSha ? " · " + esc(r.gitSha.slice(0, 8)) : ""}</div>
            <div class="counts">
                <span class="count">total <b>${r.total}</b></span>
                <span class="count passed">passed <b>${r.passed}</b></span>
                <span class="count failed">failed <b>${r.failed}</b></span>
                <span class="count broken">broken <b>${r.broken}</b></span>
                <span class="count skipped">skipped <b>${r.skipped}</b></span>
            </div>
            ${(r.allureUrl || config.allureUrl) ? `<div class="actions" style="margin-top:10px"><a class="btn primary" href="${esc(r.allureUrl || config.allureUrl)}" target="_blank" rel="noopener">Open in Allure ↗</a></div>` : ""}
        </div>
        <div class="table-wrap"><table>
            <thead><tr><th>Status</th><th>Test</th><th>Duration</th><th></th></tr></thead>
            <tbody>${testRows}</tbody>
        </table></div>`;

    // Lazily load text attachment bodies the first time they are expanded.
    app.querySelectorAll("details.att[data-url]").forEach(details => {
        details.addEventListener("toggle", async () => {
            if (!details.open || details.dataset.loaded) return;
            details.dataset.loaded = "1";
            const pre = details.querySelector("pre");
            try {
                const res = await fetch(details.dataset.url);
                pre.textContent = await res.text();
            } catch (e) {
                pre.textContent = "failed to load: " + e.message;
            }
        });
    });
}

async function viewTestHistory(testId) {
    loading();
    let history;
    try { history = await api(`/api/tests/${encodeURIComponent(testId)}/history`); } catch (e) { return fail(e); }

    const rows = history.map(h => `
        <tr class="clickable" data-id="${esc(h.runId)}">
            <td>${esc(fmtDate(h.finishedAt))}</td>
            <td>${statusBadge(h.status)}${h.flaky ? ` <span class="badge flaky">flaky</span>` : ""}</td>
            <td>${esc(fmtDuration(h.durationMs))}</td>
        </tr>`).join("");

    app.innerHTML = `
        <a class="backlink btn" href="#/">← Runs</a>
        <div class="card">
            <h1 style="word-break:break-all">${esc(testId)}</h1>
            <div class="actions" style="margin-top:8px">${metricsLink(testId) || `<span class="muted">metrics link unavailable</span>`}</div>
        </div>
        ${history.length === 0 ? `<div class="empty">No history for this test.</div>` : `
        <div class="table-wrap"><table>
            <thead><tr><th>Finished</th><th>Status</th><th>Duration</th></tr></thead>
            <tbody>${rows}</tbody>
        </table></div>`}`;

    app.querySelectorAll("tr.clickable").forEach(tr =>
        tr.addEventListener("click", () => { location.hash = "#/runs/" + tr.dataset.id; }));
}

// ---- router ----------------------------------------------------------------

function route() {
    const hash = location.hash || "#/";
    const path = hash.split("?")[0];
    const runMatch = path.match(/^#\/runs\/(.+)$/);
    const testMatch = path.match(/^#\/tests\/(.+)$/);

    if (runMatch) return viewRun(decodeURIComponent(runMatch[1]));
    if (testMatch) return viewTestHistory(decodeURIComponent(testMatch[1]));
    return viewRuns();
}

window.addEventListener("hashchange", route);

(async function start() {
    try { config = await api("/api/config"); } catch (_) { /* links just get hidden */ }
    route();
})();
