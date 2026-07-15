# TestPulse backend

A self-contained backend, one command up, no configuration:

- **VictoriaMetrics** — metric storage
- **Grafana** — provisioned dashboards
- **Postgres** — run/report storage
- **MinIO** — attachment (screenshot/log) storage
- **testpulse-server** — report ingest API + report UI (built from source on first run)

> This is *not* part of the published library and never ships in the jar — it is deployed once per
> team. Developers only add the `testpulse-core` dependency; they point their tests at this backend.

## Run

```bash
cd deploy
docker compose pull           # pull the published server image from GHCR
docker compose up -d
```

Or build the server from source instead of pulling:

```bash
docker compose up -d --build  # first build ~3 min
```

The published image is `ghcr.io/mutalking/testpulse-server` (pin a version with
`TESTPULSE_SERVER_TAG=0.0.1`); it's built and pushed by the `publish-server` GitHub Actions
workflow on a `v*` tag.

- Report UI:       http://localhost:8088
- Grafana:         http://localhost:3000 (admin / admin) → **TestPulse — Overview**
- VictoriaMetrics: http://localhost:8428
- Postgres:        localhost:5432 (testpulse / testpulse)
- MinIO console:   http://localhost:9001 (testpulse / testpulse)

Rebuild the server after code changes: `docker compose up -d --build testpulse-server`.
Stop (keep data): `docker compose down`. Wipe data too: `docker compose down -v`.

## Point tests at it

Configure the library to push straight to VictoriaMetrics:

```bash
TESTPULSE_OUTPUT=push
TESTPULSE_ENDPOINT=http://localhost:8428
TESTPULSE_PROJECT=my-service
TESTPULSE_ENVIRONMENT=local
```

(or the `testpulse.*` equivalents in `application.yml`). Run your tests — samples land in
VictoriaMetrics and the dashboard fills in.

## Smoke test

Push a sample point by hand and confirm ingestion:

```bash
curl -sS --data-binary \
  'autotest,test_id=demo.check,class=demo.DemoTest,project=demo,environment=local duration_seconds=0.42,passed=1i' \
  http://localhost:8428/write

curl -sS 'http://localhost:8428/api/v1/query?query=autotest_duration_seconds'
```

> **Note:** VictoriaMetrics hides data newer than ~30s from `now` queries by default
> (`-search.latencyOffset`). Right after a test run, metrics take about half a minute to appear on
> the dashboard — that's expected, not a lost sample.

## What's on the dashboard

| Panel                         | Query (MetricsQL)                                                        |
|-------------------------------|-------------------------------------------------------------------------|
| Pass rate (7d)                | `avg(avg_over_time(autotest_passed[7d]))`                                |
| Flaky tests (7d)              | `count(max_over_time(autotest_flaky[7d]))`                              |
| Tests tracked                 | `count(count by (test_id) (last_over_time(autotest_passed[7d])))`       |
| Avg duration (1d)             | `avg(avg_over_time(autotest_duration_seconds[1d]))`                     |
| Duration trend                | `avg_over_time(autotest_duration_seconds[1d])`                          |
| Pass rate trend               | `avg_over_time(autotest_passed[7d])`                                    |
| Top 10 slowest                | `topk(10, avg_over_time(autotest_duration_seconds[1d]))`               |
| Least stable                  | `bottomk(10, avg_over_time(autotest_passed[7d]))`                       |

Degradation (now vs a week ago) for a single test:

```
avg_over_time(autotest_duration_seconds{test_id="X"}[1d])
  / avg_over_time(autotest_duration_seconds{test_id="X"}[1d] offset 7d)
```

Filter everything by the `environment`, `project` and `test_id` dashboard variables.

## Roadmap

The run-details plane — Postgres (run/report metadata) + object storage (logs, attachments) +
a report UI, fed by the CLI uploader — will be added here as additional services.
