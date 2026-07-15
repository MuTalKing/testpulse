# TestPulse

Trustworthy autotest metrics over time — an open-source take on the gap between the free,
static [Allure Report](https://allurereport.org/) and paid Allure TestOps.

TestPulse is a small JUnit 5 library that records **how long each test takes** and **whether it
passed**, as clean time-series you can actually trust: duration trends, degradation, pass-rate,
slowest/flakiest tests — straight into VictoriaMetrics and Grafana. No test-management, no Jira
sync, no RBAC. Just honest numbers and a light run report.

> **Status: early / work in progress.** The JUnit extension, the FILE output path and the PUSH sink
> (direct to VictoriaMetrics) are working and tested. The CLI uploader and the backend (Postgres +
> report UI, Grafana dashboards) are on the roadmap below. Not yet published to a Maven repository —
> build from source.

## Why

Allure TestOps is useful but expensive ($30–39/user/mo) and its timeline/history numbers are hard
to trust. TestPulse focuses on exactly that missing piece: **metrics you can rely on**, built on a
few deliberate decisions (see [Design](#design)).

## How it works

```
JUnit 5 test run
   │
   ├─ TestPulseExtension  ──▶  metric sample per test  ──▶  <outputDir>/metrics.influx   (FILE mode)
   │                                                         └─▶  VictoriaMetrics /write  (PUSH mode)
   └─ (run details: steps/logs/attachments come from Allure — planned CLI upload)
```

Each finished test produces one InfluxDB line-protocol record:

```
autotest,test_id=io.shop.OrderTest#checkout,class=io.shop.OrderTest,suite=io.shop,project=shop,environment=ci duration_seconds=1.234000,passed=1i
```

VictoriaMetrics exposes this as two low-cardinality series:

- `autotest_duration_seconds{test_id,class,suite,project,environment}`
- `autotest_passed{...}` = `1 | 0`
- `autotest_flaky{...}` = `1` — emitted when a retry flips a `test_id` between pass and fail in one run

## Modules

| Module             | Purpose                                                                 |
|--------------------|-------------------------------------------------------------------------|
| `testpulse-core`   | JUnit 5 extension, metric model, config (env + system properties), FILE sink. No Spring dependency. |
| `testpulse-spring` | Optional config source that reads `testpulse.*` from `application.yml`/`.properties`. Spring is `compileOnly`. |

## Quick start

Add `testpulse-core` to your test dependencies (and `testpulse-spring` if you configure via
`application.yml`). Then register the extension one of two ways:

**1. Auto-registration (plug-and-play).** Enable JUnit's extension auto-detection once in
`src/test/resources/junit-platform.properties`:

```properties
junit.jupiter.extensions.autodetection.enabled=true
```

That's it — TestPulse attaches to every test via `ServiceLoader`. No `@ExtendWith` anywhere.

**2. Explicit.** Skip the flag and annotate the classes you want:

```kotlin
@ExtendWith(TestPulseExtension::class)
class OrderTest { /* ... */ }
```

## Configuration

Configuration is assembled from several sources. Precedence, low → high (later wins), mirroring
Spring's own ordering:

```
defaults  <  application.yml  <  TESTPULSE_* env  <  -Dtestpulse.*  <  programmatic overrides
```

A Spring project keeps base config in `application.yml`; CI can override any single value via an
env var or system property without touching code.

| Setting        | `application.yml`      | Environment variable    | Default          | Notes                                  |
|----------------|------------------------|-------------------------|------------------|----------------------------------------|
| Enabled        | `testpulse.enabled`    | `TESTPULSE_ENABLED`     | `true`           |                                        |
| Project        | `testpulse.project`    | `TESTPULSE_PROJECT`     | —                | Metric label                           |
| Environment    | `testpulse.environment`| `TESTPULSE_ENVIRONMENT` | `default`        | Metric label (`ci`, `staging`, …)      |
| Output mode    | `testpulse.output`     | `TESTPULSE_OUTPUT`      | `file`           | `file` \| `push`                       |
| Output dir     | `testpulse.output-dir` | `TESTPULSE_OUTPUT_DIR`  | `build/testpulse`| FILE mode target directory             |
| Endpoint       | `testpulse.endpoint`   | `TESTPULSE_ENDPOINT`    | —                | VictoriaMetrics URL (for PUSH/CLI)     |

Non-Spring projects use env vars only — no Spring on the classpath. The Spring config source
activates automatically the moment `testpulse-spring` is present (via `ServiceLoader`).

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

## Design

- **Two data planes.** Metrics (aggregates) → VictoriaMetrics; run details (logs, traces, steps,
  attachments) → report store. They are joined by `test_id` + the run-finish timestamp.
- **Low-cardinality labels.** No `run_id`, `build`, `git_sha`, `timestamp`, `thread` or `host` in
  metric labels — that is the usual cause of "history looks weird / numbers can't be trusted". Run
  identity lives in the report store, not in labels.
- **One sample per test per run**, batched to the backend as a post-CI step — tests never take a
  runtime dependency on the metrics backend.

## CLI uploader

In FILE mode the extension writes `<outputDir>/metrics.influx` with no timestamps. The
`testpulse-cli` uploader stamps every record with a single run-finish time and posts the batch to
VictoriaMetrics — run this as a post-test CI step:

```bash
./gradlew :testpulse-cli:installDist
./testpulse-cli/build/install/testpulse/bin/testpulse \
  upload --file build/testpulse/metrics.influx --endpoint http://localhost:8428
```

`--endpoint` falls back to `TESTPULSE_ENDPOINT`; `--timestamp <epochMillis>` overrides the default
(now). The uploader exits non-zero on failure so CI surfaces upload problems — unlike the extension,
which never affects the test run.

## Backend

A ready-to-run metrics backend (VictoriaMetrics + Grafana with provisioned dashboards) lives in
[`deploy/`](deploy/) — deployed once per team, never bundled in the library:

```bash
cd deploy && docker compose up -d
# Grafana http://localhost:3000 (admin/admin) → "TestPulse — Overview"
```

Then point your tests at it with `TESTPULSE_OUTPUT=push` and `TESTPULSE_ENDPOINT=http://localhost:8428`.
See [deploy/README.md](deploy/README.md).

## Building

Requires a JDK (17+). Uses the Gradle wrapper.

```bash
./gradlew build      # compile + run tests for all modules
./gradlew test       # tests only
```

Toolchain: Gradle 9.1, Kotlin 2.2.20, JVM target 17.

## Roadmap

- [x] JUnit 5 extension: per-test duration + outcome
- [x] Readable `test_id` with `@StableId` override
- [x] FILE sink (InfluxDB line protocol)
- [x] Config via env / system properties / Spring `application.yml`
- [x] Auto-registration via `ServiceLoader`
- [x] PUSH sink → VictoriaMetrics
- [x] Flaky detection (fail-then-pass within a run, same JVM)
- [x] Backend — metrics plane: `docker compose up` (VictoriaMetrics + Grafana dashboards, `deploy/`)
- [x] CLI uploader — metrics: `metrics.influx` → VictoriaMetrics (run-finish timestamp)
- [ ] CLI uploader — run details: `allure-results` → report store
- [ ] Backend — report plane: Postgres + object storage + report UI
- [ ] Zero-infra static HTML report fallback

## License

TBD.
