# Interview demo deployment

This project is maintained as a resume/interview demo. The scope is the Android
client, Spring Boot moderation backend, admin console, real model integration,
persistent media and a working deployment. It does not require Kubernetes,
a separate cloud monitor, alert email, unattended backups or bucket replication.

## Existing demo

- API: https://p01--de-moderation-api--z48dx52bgz5k.code.run
- Admin: [review console](https://de-moderation-review-demo.pages.dev/)
- Backend: Northflank Free Docker, US Central, 0.2 shared vCPU / 512 MB, one instance.
- Database: existing Neon PostgreSQL 18 in Sydney. Render Free Singapore remains available for explicit rollback.
- Media: existing private S3-compatible R2 bucket, prefix `render-production/media/`.
- Model: `gemini-3.5-flash-lite/v2`, with `keyword-v1` fallback.
- Schema: V15, including comment pagination, community relations, persisted categories/pins and virtual market provenance.

The earlier Render backend image introduced the real community and virtual market APIs:

```text
ghcr.io/mingjie-mao/de-moderation-backend@sha256:d8312bba5ead9795523b266c2d5e4a38defb749e879380e4791997bf31b31cb7
```

Render deployment `dep-db1h3vvavr4c73bsvhf0` is Live (2026-10-05). Flyway
validated 14 migrations and applied V14 successfully. The full `mvn verify`
passed; four focused PostgreSQL community/market tests and six Android device
workflow tests also passed. Live checks with the real `1234` and `12345` accounts
verified social persistence after signing in again, account isolation, long and
short trades, idempotent retries, insufficient-funds rejection and server wallet
rankings. A device test also verified retry after losing a successful trade receipt
and receiving a changed quote, without another debit. Social test changes were restored and virtual trades were closed;
four legitimate ledger records remain. Sanitized evidence is stored in the
Android checkout's ignored `.local/five-features-live-verification.json`.

Use the existing Render GHCR pull credential. Keep `SERVER_PORT=10000`,
`MANAGEMENT_SERVER_PORT=10000`, `EXPOSE_PROMETHEUS=false` and `MAIL_ENABLED=false`
on this demo. No new mail provider, sender domain, alert recipient, database
monitoring role or paid backup job is required. The independent Worker and its
schedule are retired as part of the 2026-10-03 simplification.

## Northflank migration (2026-10-05)

Northflank migration image (superseded by the complete dataset update below):

```text
ghcr.io/mingjie-mao/de-moderation-backend@sha256:57289bf921ed1bcd1d0428ffc24f20829f9b51d08abb6e6ea4cbbb69c3ccfe98
```

The service passed startup, readiness and liveness probes with zero restarts at
acceptance. It uses the existing database, JWT secret, R2 objects and Gemini
configuration; Flyway validated V14 without another migration. A real test post
was reported, analysed by `gemini-3.5-flash-lite/v2` (ALLOW), resolved by administrator
12345 with NONE, and then soft-deleted. Its audit trail remains in the database.
Member login, administrator login, permission isolation, social persistence and
existing R2 image reads were verified. The admin Pages site was redeployed using
the new API origin, and a real browser login loaded live cases.

New reports wake the bounded worker after transaction commit. Periodic recovery
runs every 30 minutes; an orphaned/stalled case may therefore wait until the next
recovery unless another report wakes the worker. Hikari minimum idle is zero,
idle timeout is 60 seconds, and keepalive is disabled to permit idle Neon compute
to suspend. This reduces polling; it does not guarantee free database usage under
continuous traffic. The retained Render service still uses its original polling
configuration while awake.

See [Northflank deployment and measurements](../deploy/northflank/README.md).
The original Render image and deployment details above are rollback evidence.
Use the old Render URL explicitly in Android Settings, or rebuild and redeploy
admin-web with that URL. Both hosts use the same database; no restore is needed.

## Interaction optimization (2026-10-06)

The current image includes batched Market queries. A three-sample warm comparison
on the same origin measured Market 4.440 → 1.925 seconds and leaderboard
4.142 → 1.349 seconds; feed/comment server timings were essentially unchanged.
The new Android APK provides vote previews with rollback and a 15-second thread
freshness cache; all data remains backed by the existing real accounts/database.
57 unit, 10 emulator workflow and 5 PostgreSQL integration tests passed.
No schema migration is needed for this update. Measurement details are in
`deploy/northflank/latency-verification.json`.

## Complete dataset (2026-10-06)

Current deployed image:

```text
ghcr.io/mingjie-mao/de-moderation-backend@sha256:c43500ead249d5878673fd313c96b7873a628a9946b2896e83888f7b6dfcbaaa
```

Flyway applied V15. The explicit transactional import restored the original 47 posts,
108 comments, six images, categories, two pins, vote scores, follows and bookmarks.
`campusviewer` content belongs to the real member `1234`; `modmentor` content belongs
to administrator `12345`. Existing permissions, later content, moderation decisions
and audit records were preserved. The import marker skips a repeated run.

The database contains 66 accounts, 50 posts and 115 comments (including earlier
soft-deleted test content), 1041 post votes and 1018 comment votes. The public feed
contains 47 visible posts. Member 1234 has three visible original posts and 16
visible comments; personal/public comments load directly with bounded pagination.
The feed uses database comment counts without loading each thread.

The original eight virtual market participants have real database accounts, wallets
and 80 imported closed trades. Their cash, active days and ledger continuity were
verified. Four schools retain 24 original OHLC samples. These imported samples/trades
carry `IMPORTED_DEMO`; new activity carries `LIVE`. They describe a sample game
scenario, not organic users or financial performance. No fake moderation history
was created from the former local simulated penalties.

Read-only API acceptance verified public/member/admin authentication, an imported
reader and trader login, original content/attachments, social collections, authored
comments, notifications, market history/leaderboard and administrator cases. Checks
passed: 28 focused backend integration tests, 59 Android unit tests and 12 emulator
workflow tests. Final APK was installed on the existing API 33 and API 36.1 devices.
Details: `deploy/northflank/complete-demo-verification.json` and Android
`scripts/complete-demo-import.md`. Private checkpoints and generated passwords stay
in ignored `.local` files. No Git commit/push was created.

## Before an interview

1. Check `/actuator/health/readiness` ahead of time. Northflank is configured with
   one running instance; deployment restarts, network failures and an idle Neon
   database can still delay requests. Render rollback may take minutes to wake.
2. Open the admin site and log in; root API HTTP 401 is expected.
3. Check `/api/moderation/status` for the active model and fallback.
4. Demonstrate one post/report/review flow and its audit record. Existing real
   model and S3 acceptance evidence is already saved; routine demonstrations
   do not require repeating the full release/recovery exercise.
5. Demonstrate bounded comments and admin session restoration on reload.
6. Android Member and Admin authenticate against the hosted API. The login page
   lists the requested public interview accounts: Member `1234` / `1234`, Admin
   `12345` / `12345`. These database accounts were provisioned on 2026-10-05 and
   verified through the live login API: member admin access returns 403, admin
   access returns 200, and incorrect passwords return 401. Members can also
   register their own account; normal registrations require 8–128 character passwords.

Mail delivery is disabled. Password-reset mail cannot be demonstrated without
an external mail service; account login, refresh, moderation and review still
work. Avoid listing unconfigured mail or production operations as completed
resume features.

## Local development

Follow the root README for PostgreSQL, backend and admin startup. Core checks:

```bash
mvn verify
(cd admin-web && npm ci && npm run lint && npm run build)
```

Backend integration tests use disposable PostgreSQL/S3 containers. These tests
are development checks; they are not scheduled cloud jobs.

## Optional single-host HTTPS demo

`docker-compose.prod.yml` contains four services: PostgreSQL, backend, admin-web
and Caddy. Set `.env` database/JWT/admin values and `DOMAIN`, then run:

```bash
docker compose -f docker-compose.prod.yml up -d --build
```

It requires no Grafana password, alert SMTP configuration or rendered monitoring
volume. Backend health checks use management port 9090; only Caddy publishes
ports. Keep private credentials out of source and Docker build contexts.

## Manual backup only

For the optional local Compose deployment, run `scripts/backup.sh` when a manual
snapshot is useful. It creates a custom database dump, verifies required tables
and writes SHA-256 checksums. Filesystem media is archived; S3 media stays in its
bucket. The script has no off-site upload, timer or automatic deletion policy.

An optional `scripts/restore-drill.sh /path/to/dump` restores into a disposable
container. For a PostgreSQL 18 dump set `DRILL_IMAGE=postgres:18-alpine`. The
actual `scripts/restore.sh` requires explicit replacement confirmation and is
not an interview demonstration step.

Previously verified target backups and acceptance evidence remain in the
private, gitignored `/Users/mingjie/Desktop/De-moderation/backups/` directory
and their existing bucket copies. They are not deleted by this simplification.
Removed source/configuration has a private, credential-free recovery archive
there, `project-simplification-20261003.tar.gz`. The database, media, migrations,
comment/session/security fixes and model evaluation remain in the project.

Android Admin mode uses a real server administrator account and the same APIs as
the web console. Administrator tokens live only for the current app process;
restarting the app process requires signing in again.
