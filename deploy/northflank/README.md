# Northflank demo deployment

Current snapshot: 2026-10-07, V18. Initial migration accepted on 2026-10-05. Project `de-moderation-demo`, service
`de-moderation-api`, Northflank Free, US Central (Council Bluffs), one instance,
0.2 shared vCPU / 512 MB. Startup, readiness and liveness passed; zero restarts
were observed at acceptance.

- API: [https://p01--de-moderation-api--z48dx52bgz5k.code.run](https://p01--de-moderation-api--z48dx52bgz5k.code.run)
- Admin: [existing Pages site](https://de-moderation-review-demo.pages.dev/)
- Database: existing Neon PostgreSQL 18 in Sydney, schema V18.
- Media: existing private R2 bucket and `render-production/media/` prefix.
- Render: historical V14 backup at https://de-moderation-api-demo.onrender.com; it is not API-compatible with every feature of the current app.

## Image and configuration

```text
ghcr.io/mingjie-mao/de-moderation-backend@sha256:ca65213278b8432d5c82ff17689f752dc0bb7b3284457f1854e7c6cbddf0b24b
```

The private registry credential is restricted to read access and this project.
Only HTTP port 10000 is exposed through HTTPS. No persistent disk is needed.
Import existing database, JWT, Gemini and R2 secrets separately; never commit
runtime secrets. Administrator bootstrap passwords are unnecessary for an
existing database. The non-secret overrides are in `runtime.env.example`.

| Probe | Path | Interval | Failure threshold |
| --- | --- | --- | --- |
| Startup | `/actuator/health/liveness` | 10 s | 60 |
| Readiness | `/actuator/health/readiness` | 10 s | 3 |
| Liveness | `/actuator/health/liveness` | 60 s | 3 |

All probes use port 10000, a 10-second timeout and a 5-second initial delay.
Readiness success threshold is 1. The root path requires authentication and is
not a health probe.

Reports trigger the worker after their transaction commits. A bounded executor
coalesces duplicate wake-up hints; durable database claims prevent lost cases.
Recovery polling is 30 minutes, so missed hints/stalled claims can wait up to
that interval. Hikari minimum idle is zero, idle timeout 600 seconds and keepalive
zero. This permits database suspension between requests; continuous traffic and
an awake Render replica can still consume Neon compute. Media sweeping and mail
remain disabled for this demo.

## Initial migration acceptance (2026-10-05, historical)

- Real Member `1234` / `1234` and Admin `12345` / `12345` logins passed.
- Member access to administrator cases returned 403; administrator access 200.
- Existing 11 ANU posts and a private R2 image were read through the new host.
- A disposable post vote persisted through another state read and was restored.
- A harmless report reached AWAITING_REVIEW within approximately 10 seconds of
  polling; actual engine `gemini-3.5-flash-lite/v2` recommended ALLOW.
- Administrator resolved it with NONE. CASE_OPENED, REPORT_FILED, CASE_CLAIMED,
  VERDICT_RECORDED and CASE_RESOLVED remain in audit; test post was soft-deleted.
- Pages was deployed with the new API URL; browser administrator login loaded live cases.
- Android default and saved old Render default migrate to Northflank. Custom
  hosts and a later explicit rollback remain possible. 57 unit tests passed and
  a new debug APK was built and installed on emulator-5554. A temporary real-network
  Espresso smoke test passed: both accounts signed in and loaded all 11 live posts.
  The temporary test source was removed afterward.
- Related backend checks passed: 8 report API tests and 17 queue/workflow/recovery
  tests, including transaction rollback and full-batch draining.

Sanitized live workflow evidence is in `verification.json`.

## Historical warm latency (2026-10-05, before V18)

Sydney client, existing Sydney database, reused HTTPS connection, median of
three sequential samples per endpoint (before the queue-only image update).
This is a small deployment comparison, not a load benchmark.

| Endpoint | Northflank | Render |
| --- | ---: | ---: |
| Feed | 0.964 s | 0.883 s |
| Comments | 1.158 s | 1.057 s |
| Social state | 0.774 s | 0.713 s |
| Market | 4.450 s | 3.910 s |
| Leaderboard | 4.037 s | 3.648 s |

The change avoids Render's observed multi-minute backend cold start. Warm requests
were slightly slower across the US Central–Sydney link. Market still takes
several seconds due to multiple database round trips; this migration does not
make every interaction instant. An idle Neon database can still need to resume.

## Rollback compatibility

The retained V14 Render image lacks newer V16–V18 APIs. Verify a compatible image,
client feature set and migration history before changing either client origin.
The following procedure describes the original migration rollback, not an
automatically compatible fallback for today’s app.

Retain the original Render image/configuration. Set Android's backend URL to
`https://de-moderation-api-demo.onrender.com` explicitly, and rebuild/deploy
admin-web with `NEXT_PUBLIC_API_BASE_URL` set to that origin. Both services use
the same schema and storage; no database restore is required for this change.
No Git commit or push was made as part of this migration.

## References

- [Northflank pricing](https://northflank.com/pricing)
- [Registry images](https://northflank.com/docs/v1/application/run/run-an-image-from-a-container-registry)
- [Health probes](https://northflank.com/docs/v1/application/observe/configure-health-checks)
- [Neon compute and scale to zero](https://neon.com/docs/introduction/scale-to-zero)

## Interactive optimization accepted (2026-10-06)

The current pinned image batches Market queries. Snapshot SQL round trips fall
from 19 to 6 and leaderboard from 17 to 3, excluding authentication/transaction
control. Trades and ranking reads skip candle history. The wallet UPSERT retains
per-account transaction serialization, UTC daily crediting and idempotent trades.
No database migration was added. Previous Northflank image `sha256:2de1266df2cc10523f8dfc6cf9543ab8c9ec7b6ee239d01b517f2e3651bd6b5a`
remains available for image rollback.

Same Northflank origin, database and client, reused HTTPS, three warm samples:

| Endpoint | Before | After |
| --- | ---: | ---: |
| Feed | 0.969 s | 0.974 s |
| Comments | 1.173 s | 1.243 s |
| Market | 4.440 s | 1.925 s |
| Leaderboard | 4.142 s | 1.349 s |

Market improved by approximately 57%, leaderboard by 67% in this small sample.
Feed/comment backend queries were not changed. Android votes now show a pending
preview immediately with failure rollback; fresh loaded comments/cursors are
reused for 15 seconds, and expanding loaded replies makes no server request.
The new APK was installed on emulator-5554. Acceptance: 57 unit tests, 10 device
workflows and 5 PostgreSQL integration tests passed. New pod passed all three
probes with zero restarts. See `latency-verification.json` for measurements and
[reliability decisions](../../docs/reliability.md) for consistency boundaries.


## Complete dataset update (2026-10-06)

V15 adds persisted post categories/pins and imported-market provenance. The original
47 posts, 108 comments, six attachments, interaction scores and profile relations
are stored in the existing database. Current member/admin demo accounts own their
original profile content. Eight original virtual market participants have persisted
wallets and closed ledgers; 24 chart samples have `source=IMPORTED_DEMO`, while later
observations/trades have `source=LIVE`. Existing user activity and real moderation
records were preserved. These are imported interview sample identities and game
history, not organic user or financial-return claims.

Acceptance: [complete-demo-verification.json](complete-demo-verification.json).
The explicit import uses one transaction and an idempotent batch marker; no data
reset runs during application startup. Private generated passwords and row mappings
remain in ignored local files. This update does not add mail, monitoring or backup
services and does not require a new database, bucket or credentials.

## Historical V16 preparation (2026-10-07, superseded by deployment below)

V16 adds account-owned avatars and private language/theme settings. Android stores
rotating sessions in Keystore-encrypted atomic files excluded from backups and
restores them after process death. Only the visible tab refreshes its data. Market
snapshot database calls fall from 6 to 4 and leaderboard from 3 to 2, excluding
authentication/transaction control. Media visibility is checked in one SQL call,
including active account avatars; media sweep retains referenced avatars.

Acceptance before deployment: 64 Android unit tests, 35 backend integration tests,
and 19 distinct device checks (including a real process force-stop/restart).
The final APK is now also installed on user emulator-5556. Two opt-in live device
checks passed: real member/admin encrypted session persistence, then force-stop
and restore in a new process with real profile/post reads and no credential resend.
Restore was rechecked successfully after the diagnostic restart. At that preparation checkpoint the public API was V15. V16 preferences have
since been deployed; the current service runs V18.

Local deployment image:
ghcr.io/mingjie-mao/de-moderation-backend:account-settings-20261007.
Three registry push attempts failed because macOS Keychain could not supply the
existing GHCR credential (OSStatus -25293). The memory-only publisher later resolved this upload without modifying Keychain.
The original pre-release plan was to restore that login, push this image,
pin its registry digest on the existing service, apply the runtime pool settings,
verify V16/probes/real R2 avatar access, then upgrade the user APK. No Git commit
or Git push has been performed.

Three reused-HTTPS samples on the old live image measured medians of 1.080s feed,
1.270s comments, 0.890s social state and 2.040s market; login took 3.358s.
These are a before-release sample, not proof of the new release's latency.
Occasional long timeouts still require correlation using the added request IDs
and slow-request logs. Region relocation has not been performed.

## Live diagnosis and device acceptance (2026-10-07)

38 warm API requests passed. Medians: readiness 194 ms, feed 966 ms, comments
1151 ms, market 1944 ms. The first profile request after 120 seconds without this
client doing business requests took 2326 ms, followed by a 1156 ms warm median.
This is not a controlled Neon suspension experiment. SELECT 1 from the backend
container took about 189–191 ms per round trip; a warm diagnostic JVM built a
new JDBC connection in 1421 ms. These explain part of the latency, but no
historical timeout was reproduced or correlated.

An additional diagnostic JVM triggered one OOM restart on the 512 MB instance
at 2026-10-07 02:30:56 Australia/Sydney. This diagnostic method was stopped.
The instance recovered all three probes and real account/data checks; 47 visible
posts, member social data, imported traders and administrator API remained intact.
Do not launch additional JVMs in this small live container. Use client timing,
application request IDs and platform metrics for further diagnosis instead.
This incident is not evidence of the earlier occasional timeout root cause.

The registry credential failure above described the pre-release state. The
prepared image was subsequently published with a memory-only credential helper;
see the V16 acceptance below for that release; the current snapshot is V18.


## V16 deployed and accepted (2026-10-07)

Pinned image:

```text
ghcr.io/mingjie-mao/de-moderation-backend@sha256:ece974cdd984c4696bf5781c6372cf279c1ac6e74838ca24d11dd3ecd30a2fe7
```

Final deployment `de-moderation-api-57746f79b5`, pod
`de-moderation-api-57746f79b5-wcn7q`: startup/readiness/liveness passed, zero
restarts at acceptance. Flyway validated all 16 migrations and reported schema
version 16. PostgreSQL 18 remains newer than Flyway's tested maximum 17; the
existing compatibility warning is retained rather than concealed.

The original Docker ENTRYPOINT is retained. Non-secret Hikari settings are
applied through the service's **Custom command** arguments, without opening or
exporting the environment-secret table:

```text
--spring.datasource.hikari.maximum-pool-size=5 --spring.datasource.hikari.minimum-idle=0 --spring.datasource.hikari.idle-timeout=600000 --spring.datasource.hikari.keepalive-time=0 --spring.datasource.hikari.connection-timeout=5000 --spring.datasource.hikari.validation-timeout=3000
```

A dedicated real QA account passed private language/theme/color persistence after
fresh login, public-profile preference isolation, owned-avatar persistence and
reading avatar bytes through the existing R2 media API. The independent account
and referenced test image are retained for verification; original accounts were
not modified. Original forum/market acceptance passed again: 47 visible posts,
member 3 posts/16 comments, votes/bookmarks/follows, notifications, 4 markets,
24 imported candles, 8 original traders and their ledgers; both roles and the
administrator API still work.

The Android APK remains installed on emulator-5556. Server synchronization now
uses the V16 API. Session restoration was already verified with two separate
app processes before this release; release-specific device and latency evidence
are recorded in De-discussion's account/session verification document.


### Post-release measurements

The installed user APK passed the real-server session restoration check again
after V16 rollout (5.646 seconds, no password resend). 38 measured API requests
returned 200 with no timeouts. Five warm samples per business endpoint gave
medians: profile 1044 ms, feed 1137 ms, comments 1153 ms, social state 949 ms,
market 1900 ms and leaderboard 1377 ms. One login took 1976 ms. Compared with
pre-release measurements, Market improved only about 2.3%; comments were similar
and some other endpoints were slightly slower. This small sample does not prove
uniform latency improvement or resolution of historical intermittent timeouts.
Request IDs and slow-request logging are now active for correlation.

## V17 translation rollout (2026-10-07)

The user published `translation-reliability-20261007` through the memory-only
GHCR helper. Northflank verified the existing read-only registry integration and
the service was updated to this pinned digest:

```text
ghcr.io/mingjie-mao/de-moderation-backend@sha256:da78d3b2ed1af403001e9819adef43f5a8be39cd17798281078db676881c1d4b
```

Rollout started at 14:46:48 Australia/Sydney. Deployment
`de-moderation-api-77f4d574fb`, pod `de-moderation-api-77f4d574fb-vxx9l`.
Flyway validated 17 migrations and applied V17 at 14:48:30, creating the
translation cache table. The service retains its existing Hikari arguments,
Neon database, Gemini configuration and private R2 store.

Cache misses now run in one background worker with at most eight waiting
batches. Readers get cached translations and a `pending` flag without waiting
for the model. Concurrent reads of the same source hash share one job; model
work does not hold a database connection. Moderation continues to use original
content. See De-discussion's `docs/translation-reliability.zh-CN.md` for local
tests, device restoration and latency evidence. No Git commit or push was
performed for this rollout.

### V17 acceptance

The application started at 14:49:48 Australia/Sydney (149.898 seconds).
Startup/readiness/liveness all returned 200 by 14:50:52; the new pod had zero
restarts. The translated reading API returned 200, replacing the previous 404.
One existing English post and comment were translated into Chinese by the
configured Gemini provider. The cache miss returned `pending=true` in 1602 ms;
the next poll read the completed result, and the cached read took 1338 ms.

The installed Android app passed its selected-account restoration test in a new
process against V17 (4.847 seconds, no password resend). Eleven live checks passed
for both account roles, administrator access control, existing ANU posts, private
R2 image bytes, market reads and leaderboard. Thirty-eight post-release timing
requests returned 200 without timeouts; medians were 1046 ms for feed, 1203 ms for
comments, 1922 ms for market and 1169 ms for leaderboard. The first login after
restart took 5034 ms. These samples do not establish a general latency improvement
or resolve the historical intermittent timeout root cause; the bounded async
translation fix specifically removes waiting on the model from the HTTP path.


## V18 review content and latency rollout (2026-10-07)

Pinned published image:

```text
ghcr.io/mingjie-mao/de-moderation-backend@sha256:ca65213278b8432d5c82ff17689f752dc0bb7b3284457f1854e7c6cbddf0b24b
```

Deployment `de-moderation-api-7668868c44`, pod
`de-moderation-api-7668868c44-lp2xd`, has 3/3 passing health checks.
Flyway applied V18 at 16:49:14 Australia/Sydney. V18 adds only the partial
index for author cursor pagination. Existing database, R2 and pool settings
were retained. Review queue responses now include bounded report-time content
summaries, with batched fallback reads for older cases.

Five before/after samples per read gave medians: profile 962 -> 423 ms,
public feed 590 -> 464 ms, personal posts 2354 -> 402 ms, comments 797 -> 802 ms.
Personal posts previously summed four sequential forum reads; the new path is
one bounded author request. These are small diagnostic samples, not p95 or a
stability guarantee. All 25 recorded post-release requests returned 200.
First Member login after startup took 5812 ms; two subsequent logins took
2589/2008 ms. Cross-region latency and historical intermittent timeouts remain
limitations; comment first-load latency did not improve in this sample.

The updated user APK was installed without clearing data. Both real-server
instrumented tests passed: selected-account restoration after process stop and
actual review content/status without UUID/model debug metadata in list cards.
23 targeted backend tests, 71 Android unit tests and five isolated device
scenarios also passed. No Git commit or Git push was performed.


## Current complete regression (2026-10-07)

The V18 runtime passed full default Maven verification and separate real R2/model
checks: 418 distinct test methods passed; two opt-in real investigation checks
were not run because the public demo keeps that feature disabled. Android passed
71 unit and 26 device methods; Debug Lint has zero errors and 283 warnings.
The final APK includes a startup/resume session-revocation race fix and was
installed preserving user data. Pages lint, type checking, static build and live
queue/detail/audit reads passed. A dedicated QA post completed real Gemini review,
human hide, appeal restoration and audit; it was soft-deleted afterwards.
No new backend rollout was required for these Android/test/doc changes.
See [the full record](../../docs/current-regression.zh-CN.md) for scope, warnings,
separate default/external counts and reproducibility. No Git commit or push.
