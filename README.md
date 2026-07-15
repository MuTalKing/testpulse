# TestPulse

Trustworthy autotest metrics over time — an open-source take on the gap between the free,
static [Allure Report](https://allurereport.org/) and paid Allure TestOps.

TestPulse records **how long each test takes** and **whether it passed**, as clean time-series you
can actually trust — duration trends, degradation, pass-rate, slowest/flakiest tests — into
VictoriaMetrics + Grafana, and a light run report joined to those metrics by a stable `test_id`.
No test-management, no Jira sync, no RBAC. Just honest numbers and a readable report.

> **Status:** functional, first release `0.0.1`. Libraries are on **Maven Central**
> (`io.github.mutalking:testpulse-*`), the Gradle plugin on the **Gradle Plugin Portal**
> (`io.github.mutalking.testpulse`), and the backend server as a **GHCR image**
> (`ghcr.io/mutalking/testpulse-server`).

## Two things you get

1. **Metrics** — add the `testpulse-core` JUnit 5 extension; every test emits duration + outcome to
   VictoriaMetrics, viewable in provisioned Grafana dashboards.
2. **A report** — apply the Gradle plugin and press one task to render a run report (with the Allure
   drill-in) and open it; or ingest `allure-results` into the server for a browsable history UI.

Both are linked by `test_id`: from a test in the report you jump straight to its metrics.

## Quick start — metrics

Add the extension to your test dependencies:

```kotlin
testImplementation("io.github.mutalking:testpulse-core:0.0.1")
// optional, only if you configure via application.yml in a Spring project:
testImplementation("io.github.mutalking:testpulse-spring:0.0.1")
```

Register it one of two ways:

**1. Auto-registration (plug-and-play)** — enable JUnit's extension auto-detection once in
`src/test/resources/junit-platform.properties`:

```properties
junit.jupiter.extensions.autodetection.enabled=true
```

That's it — TestPulse attaches to every test via `ServiceLoader`, no `@ExtendWith` needed.

**2. Explicit** — annotate the classes you want:

```kotlin
@ExtendWith(TestPulseExtension::class)
class OrderTest { /* ... */ }
```

Then point it at a backend (see [Backend](#backend)) with `TESTPULSE_OUTPUT=push` and
`TESTPULSE_ENDPOINT=http://localhost:8428`, or write to a file and upload with the CLI.

## Quick start — report task

Apply the Gradle plugin and a `testpulseReport` task appears automatically (a plain dependency
can't add tasks — only a plugin can):

```kotlin
// settings.gradle.kts → pluginManagement { repositories { gradlePluginPortal(); mavenCentral() } }
plugins {
    id("io.github.mutalking.testpulse") version "0.0.1"
}

testpulseReport {          // all optional — conventions shown
    allureResults.set(layout.buildDirectory.dir("allure-results"))
    withAllure.set(true)   // also generate + link the Allure single-file report (needs the Allure CLI)
    openInBrowser.set(true)
    // server.set("http://localhost:8088")    // per-test History links
    // grafana.set("http://localhost:3000")   // per-test Metrics links
}
```

Run your tests, then press **`testpulseReport`** (Gradle tool window → `verification`, or
`./gradlew testpulseReport`) — it renders the report from `build/allure-results` and opens it.

## How it works

```
JUnit 5 test run
   │
   ├─ TestPulseExtension  ──▶  metric sample per test  ──▶  <outputDir>/metrics.influx   (FILE mode)
   │                                                         └─▶  VictoriaMetrics /write  (PUSH mode)
   └─ allure-results (steps/logs/attachments)  ──▶  testpulse report  ──▶  server (Postgres + minio)
```

Each finished test produces one InfluxDB line-protocol record:

```
autotest,test_id=io.shop.OrderTest#checkout,class=io.shop.OrderTest,suite=io.shop,project=shop,environment=ci duration_seconds=1.234000,passed=1i
```

VictoriaMetrics exposes this as low-cardinality series:

- `autotest_duration_seconds{test_id,class,suite,project,environment}`
- `autotest_passed{...}` = `1 | 0`
- `autotest_flaky{...}` = `1` — when a retry flips a `test_id` between pass and fail in one run

## Configuration

Assembled from several sources. Precedence, low → high (later wins), mirroring Spring's ordering:

```
defaults  <  application.yml  <  TESTPULSE_* env  <  -Dtestpulse.*  <  programmatic overrides
```

| Setting        | `application.yml`      | Environment variable    | Default          | Notes                               |
|----------------|------------------------|-------------------------|------------------|-------------------------------------|
| Enabled        | `testpulse.enabled`    | `TESTPULSE_ENABLED`     | `true`           |                                     |
| Project        | `testpulse.project`    | `TESTPULSE_PROJECT`     | —                | Metric label                        |
| Environment    | `testpulse.environment`| `TESTPULSE_ENVIRONMENT` | `default`        | Metric label (`ci`, `staging`, …)   |
| Output mode    | `testpulse.output`     | `TESTPULSE_OUTPUT`      | `file`           | `file` \| `push`                    |
| Output dir     | `testpulse.output-dir` | `TESTPULSE_OUTPUT_DIR`  | `build/testpulse`| FILE mode target directory          |
| Endpoint       | `testpulse.endpoint`   | `TESTPULSE_ENDPOINT`    | —                | VictoriaMetrics URL (PUSH / CLI)    |

Non-Spring projects use env vars only — no Spring on the classpath. The Spring config source
activates the moment `testpulse-spring` is present (via `ServiceLoader`).

### Test identity

Metric history hinges on a stable `test_id`. By default it is readable and derived from the test:

```
io.shop.OrderTest#checkout          # a plain @Test
io.shop.OrderTest#checkout[1]       # a @ParameterizedTest invocation (by index, not by value)
```

Renaming a method breaks that id. To pin it across refactors, annotate the test:

```kotlin
@StableId("orders.checkout.happy_path")
@Test
fun checkoutHappyPath() { /* ... */ }
```

When Allure is on the classpath, the extension stamps this `test_id` onto each result as a
`testpulse.id` label, so report rows join the metric series by the exact same id.

## CLI

The `testpulse-cli` jar is on Maven Central; for the `testpulse` command build the distribution
with `./gradlew :testpulse-cli:installDist` (bin under `testpulse-cli/build/install/testpulse/bin`).

```bash
# upload FILE-sink metrics to VictoriaMetrics (stamps one run-finish timestamp), a post-test CI step
testpulse upload --file build/testpulse/metrics.influx --endpoint http://localhost:8428

# ingest an allure-results run into the server
testpulse report --allure ./allure-results --server http://localhost:8088 --project my-service \
  --allure-report-url https://ci.example.com/job/123/allure/

# zero-infra: render a single self-contained HTML report (attachments embedded, opens from disk)
testpulse html --allure ./allure-results --out site/index.html --with-allure \
  --server http://localhost:8088 --grafana http://localhost:3000
```

`--with-allure` runs `allure generate --single-file` alongside and wires the "Open in Allure"
drill-in. `--server`/`--grafana` add per-test **History** / **Metrics** links (just links — the
report stays small regardless of history length).

## Backend

The whole backend — VictoriaMetrics, Grafana (provisioned dashboards), Postgres, MinIO, and the
report server/UI — runs from [`deploy/`](deploy/), deployed once per team:

```bash
cd deploy
docker compose pull          # pull the published server image from GHCR
docker compose up -d
```

- Report UI: http://localhost:8088
- Grafana:   http://localhost:3000 (admin/admin) → "TestPulse — Overview"

`docker compose up -d --build` builds the server from source instead. See
[deploy/README.md](deploy/README.md).

## Design

- **Two data planes.** Metrics (aggregates) → VictoriaMetrics; run details (logs, traces, steps,
  attachments) → Postgres + object storage. Joined by `test_id` + the run-finish timestamp.
- **Low-cardinality labels.** No `run_id`, `build`, `git_sha`, `timestamp`, `thread` or `host` in
  metric labels — the usual cause of "history looks weird / numbers can't be trusted". Run identity
  lives in the report store, not in labels.
- **One sample per test per run**, batched to the backend as a post-CI step — tests never take a
  runtime dependency on the metrics backend.
- **Hybrid detail.** TestPulse owns the run list, status, history and metrics link; the deep
  per-test step view reuses the Allure report (linked, not re-implemented).

## Modules

| Module             | Purpose                                                                 |
|--------------------|-------------------------------------------------------------------------|
| `testpulse-core`   | JUnit 5 extension, metric model, config (env + system properties), FILE & PUSH sinks. No Spring dependency. |
| `testpulse-spring` | Optional config source that reads `testpulse.*` from `application.yml`/`.properties`. Spring is `compileOnly`. |
| `testpulse-cli`    | CLI: `upload` (metrics → VictoriaMetrics), `report` (allure-results → server), `html` (static report). |
| `testpulse-server` | Ktor + Postgres + MinIO backend: ingests run reports, serves the run/detail/history API, hosts the report UI. |
| `testpulse-report-model` | Shared run-ingest DTOs, so the CLI (sender) and server (receiver) can't drift apart. |
| `testpulse-gradle-plugin`| Gradle plugin `io.github.mutalking.testpulse` — applying it makes a `testpulseReport` task appear. |
| `testpulse-example`| A runnable Allure + JUnit 5 example wired to the extension — doubles as an end-to-end test. |

## Building

Requires a JDK (Gradle 9.1 runs on 17–25). Uses the Gradle wrapper.

```bash
./gradlew build      # compile + run tests for all modules
```

Toolchain: Gradle 9.1, Kotlin 2.2.20, JVM target 17.

Publishing: `./gradlew publishToMavenCentral` (libraries), `./gradlew publishPlugins` (plugin), and
a `git tag v0.0.1` push triggers the GHCR server-image workflow.

## Roadmap

- [x] JUnit 5 extension: per-test duration + outcome, readable `test_id` with `@StableId`
- [x] FILE + PUSH sinks, flaky detection, auto-registration via `ServiceLoader`
- [x] Config via env / system properties / Spring `application.yml`
- [x] Metrics backend: VictoriaMetrics + provisioned Grafana dashboards
- [x] Report plane: Ktor + Postgres + MinIO, ingest/read/history API, report UI with attachments
- [x] CLI: `upload`, `report` (allure-results), `html` (self-contained static report + Allure drill-in)
- [x] Whole backend via `docker compose up`; server published as a GHCR image
- [x] Gradle plugin — `testpulseReport` task appears on apply
- [x] Published: libraries → Maven Central, plugin → Gradle Plugin Portal
- [ ] Cross-fork flaky aggregation in the backend
- [ ] Degradation alerts

## License

[Apache License 2.0](LICENSE).
