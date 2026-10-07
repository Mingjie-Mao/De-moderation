# Interview demo deployment

Updated: 7 October 2026. This is a resume/interview demo, not a production SLA.

## Current deployment

| Component | Current configuration |
| --- | --- |
| Android and API | `https://p01--de-moderation-api--z48dx52bgz5k.code.run` |
| Browser console | [Cloudflare Pages](https://de-moderation-review-demo.pages.dev/) |
| Backend | Northflank Free Docker, US Central (Council Bluffs), 0.2 shared vCPU / 512 MB, one instance |
| Database | Existing Neon PostgreSQL 18.6 in Sydney; Flyway V18 |
| Media | Existing private R2 bucket, `render-production/media/` prefix |
| Moderation | `gemini-3.5-flash-lite/v2`, with `keyword-v1` fallback; human final decisions |
| Optional investigator | Disabled on the public demo |

The active image is pinned to:

```text
ghcr.io/mingjie-mao/de-moderation-backend@sha256:ca65213278b8432d5c82ff17689f752dc0bb7b3284457f1854e7c6cbddf0b24b
```

Deployment `de-moderation-api-7668868c44` applied V18 on 7 October and passed
startup, readiness and liveness. Release-specific evidence and historical
images are in [Northflank notes](../deploy/northflank/README.md).
Local working-tree changes are not committed or pushed; a successful image
publication or local regression is not a GitHub CI result.

## Accounts and data

- Public Member: `1234` / `1234`; public Admin: `12345` / `12345`.
- Both are real database accounts. Choosing Admin does not grant privileges;
  the server authenticates the account and checks its current role.
- Android saves rotating sessions in Keystore-encrypted files excluded from
  backups. Process exit does not require another password login. Logout,
  origin changes and confirmed revocation clear the appropriate saved session.
- Avatar, language and theme settings persist through the V16 profile API.
- Original posts/comments, social relations and virtual Market histories were
  explicitly imported once into the real database. They are sample content,
  not organic user growth or financial performance claims. New writes persist.
- V17 adds cached background translation. V18 adds author cursor indexing;
  review lists use reported content summaries and localized statuses.

## Runtime and verification

Only HTTPS access to the application on port 10000 is exposed. Import existing
JWT, database, Gemini and R2 secrets through the platform; do not commit them.
The non-secret settings are in
[`runtime.env.example`](../deploy/northflank/runtime.env.example).

Current pool overrides: maximum 5, minimum idle 0, idle timeout 600000 ms,
keepalive 0, connection timeout 5000 ms and validation timeout 3000 ms.
`JAVA_TOOL_OPTIONS` limits heap to 60% of available RAM. Never launch another
diagnostic JVM inside the 512 MB production container; an earlier diagnostic
attempt caused an OOM restart.

Reports wake the worker after transaction commit. Durable claims and bounded
wake-up coalescing retain cases; recovery polling is every 30 minutes, so a
missed hint or stalled claim may wait until recovery. No independent monitoring,
SMTP alerts, unattended backups or bucket replication are required for this demo.
Mail, media sweeping, public Prometheus and production Swagger are disabled.
Basic probes, application logs and manual recovery tooling remain.

Before a deployment, build and test locally. Deploy a pinned digest, let Flyway
apply migrations, verify all three probes, then check both roles, existing data,
R2 images and the client APIs. Install the compatible APK without clearing data.
Do not claim live compatibility based only on local tests.

## Latency and limitations

The backend and database are in different regions. Query consolidation reduces
round trips but does not eliminate geographic or idle connection recovery costs.
Five-sample V18 comparisons improved profile and author-page reads; comment
first-load remained about 0.8–1.0 seconds. Login still takes seconds. Recent
successful samples do not establish p95/p99, capacity, or a permanent fix for
historical intermittent timeouts. Request IDs and bounded slow-request logging
help correlate future failures without recording credentials or content.

Render Singapore remains a historical backup on an older V14 image. Its free
instance sleeps and can need minutes to start. It does not provide the current
profile, translation and author-page API set: do not direct the current app
there as a complete fallback. A rollback requires checking image/API/schema
compatibility, probes, R2 and both clients; no database restore is needed merely
to change hosts, but application compatibility must be verified.

## Regression evidence

See [current regression](current-regression.zh-CN.md) for the latest local gates,
live acceptance and any skipped or unresolved checks. Historical acceptance
counts are retained in the release journals with their dates and are not the
current full test count. Model evaluations, load tests, optional investigation
benchmarks and cloud CI are separate from routine functional regression.
