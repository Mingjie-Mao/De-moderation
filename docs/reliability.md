# Reliability decisions

What happens when something fails, and what stops one account from overwhelming
the thing that is supposed to police it.

## The queue is durable, not merely asynchronous

A committed report publishes an after-commit wake-up hint to a bounded background
worker (one running drain and one pending hint). Rollback publishes no runnable
work. The hint carries no queue payload: persisted case rows remain the source of
truth, and redundant hints can be coalesced without losing cases. Full batches
are drained immediately. Periodic polling recovers missed hints and stalled
claims; the Northflank demo uses a 30-minute interval to allow idle Neon compute
to suspend, while the development default remains two seconds. Recovery after a
process failure can therefore wait up to the configured interval.

Reports become moderation cases; a worker claims a batch and analyses it. The
claim uses `SELECT ... FOR UPDATE SKIP LOCKED`, so a second instance takes
different rows rather than queueing behind rows the first already holds, and the
claim commits before analysis starts so no row lock is held across a model call.

That leaves one hole: a worker that dies between claiming a case and finishing it
leaves a row in `ANALYSING` that nothing would ever look at again — not the claim
query, which only reads `QUEUED`, and not a person, because the reporter was
already told it would be reviewed.

A sweep returns those cases to the queue. It is tested in both directions,
because it can be wrong twice:

- **Too slow**, and reports die quietly.
- **Too eager**, and it hands a case to a second worker while the first is still
  mid-call — the same content judged twice and billed twice.

The tests were verified by breaking the sweep's query and watching the right one
fail, rather than by trusting four green ticks.

## Model failure degrades; it does not stop

A missing API key, a timeout, a rate limit, a provider outage or a response that
fails validation all fall back to the deterministic rule engine. The verdict
records which engine actually answered, so a rule-engine result is never
attributed to the model — that would corrupt the comparison the evaluation exists
to make.

Running with no model configured at all is a supported configuration, not an
error. With `AI_CHAT_MODEL` unset there is no model engine registered and
everything else works unchanged.

Around the call itself:

- **Timeout, then circuit.** A timeout alone spends the full budget on every
  request while a provider is down. The circuit turns a thirty-second failure
  into an instant one, so the queue degrades at full speed instead of crawling.
- **Backoff only where it helps.** Rate limiting is retried with exponential
  backoff and jitter, outside the call budget, because waiting is the one thing
  that actually fixes it. A refused credential is not retried — it will still be
  refused in two seconds.
- **Validation, then one corrective retry.** A response that parses is not a
  response that is correct. A confidence outside `[0,1]`, a decision that is not
  one of the three, or a rule code that does not exist are rejected and the
  specific complaint is fed back. It is load-bearing: across one 192-sample run
  it caught forty answers whose rule code carried its severity along with it
  (`"ABUSE (HIGH)"`), and the retry fixed every one.
- **Every call recorded.** `ai_invocations` holds model, prompt version, tokens,
  latency, status and the raw response, for successes and failures alike. The
  failure rate is the number that says whether the fallback is load-bearing or
  decorative.

## Reply nesting is capped in the schema

The original thread reader assembled replies recursively, and nothing limited
how deep a reply could go. A chain of eight thousand answered the public comments
endpoint with a `StackOverflowError` — measured on a running server, and
reachable by a single account replying to itself.

The ceiling is a `CHECK` constraint on a stored `depth` column, not a guard in
the service that happens to write comments today. A constraint binds every
writer: an import, a backfill, a second service. A check in one method binds only
callers who go through that method. Verified by inserting a depth of 99 straight
past the application and watching the database refuse it.

The service checks first anyway, so the answer is a sentence somebody can act on
rather than a constraint violation arriving as a 500. Ten levels is past the
point where a client renders the nesting at all.

## Threads and feeds page by cursor

An offset is wrong for a feed. New rows arrive at the top, so every insertion
shifts the page and a reader paging through sees some posts twice and never sees
others. Keyset paging seeks past a known position instead, and lets the database
stop reading once it has enough rows rather than counting past the ones it skips.

The cursor carries `(created_at, id)` together, because the instant alone is not
a unique position: two rows created in the same microsecond leave the boundary
ambiguous. It is base64-encoded so clients treat it as a token to hand back
rather than a timestamp to do arithmetic on.

Threads use the same keyset cursor, but count roots and replies alike toward the
page size. Paging roots alone let one root with many replies cause an unbounded
database read and response. The flat rows carry `parentCommentId`; Android
merges pages and assembles the tree locally. The partial index on
`(post_id, created_at, id)` keeps each seek bounded.

## Authoring is rate limited, not just reporting

Reporting was capped from the first version; authoring was not. That had the
asymmetry backwards. A report costs a moderator one glance at something a person
already chose to flag. A post costs an engine call, a queue slot and a reviewer's
attention the moment anyone reports it. Registration is open, so being signed in
was never a brake on a script.

Generous on purpose — a ceiling on flooding, not a throttle on enthusiasm. A
person arguing at midnight should never meet it; a script should meet it almost
at once.

## Concurrent reports collapse into one case

Concurrent reports on one target join one open case, avoiding duplicate
analysis jobs. This is not an exactly-once guarantee for vendor requests: output
correction, transport retries and recovery after a crash can call the model again. The
guarantee is a partial unique index rather than a check in application code, so
two reports arriving at the same instant cannot both create a case. It is covered
by a test that fires two of them at once against a real PostgreSQL.

## Schema drift fails at startup

Flyway owns the schema and Hibernate runs with `ddl-auto: validate`. An entity
that disagrees with a migration stops the application from starting instead of
silently mutating the database. It has caught real drift more than once —
including a `CHAR(64)` column that should have been `VARCHAR`.

`open-in-view` is off, so an unfetched association fails loudly rather than
turning a feed into one query per row.

## Interactive latency (2026-10-06)

Android votes are optimistic projections over the last server state: icon and
score change immediately, duplicate submissions are suppressed until the response,
and success replaces the preview with authoritative state. Failure removes the
preview and invalidates freshness so the next render reads the server again;
a missing response is not proof that a write failed to commit.

Comments already in AppData remain visible while a stale thread refreshes.
Freshness and pagination metadata are memory-only, account/origin scoped, limited
to 30 entries and valid for 15 seconds. Returning within that window avoids another
request and preserves loaded later pages. Older data is refreshed from the first
bounded page; a cache reset or scope change forces a read. Expanding loaded
replies is local. RecyclerView retains its adapter and applies row differences;
change cross-fades are disabled to avoid rebind flicker.

Market batches forum activity counts, candle observations and history reads
across the four forums. Snapshot SQL round trips fall from 19 to 6, excluding
authentication and transaction control; leaderboard falls from 17 to 3. Rankings
and trades use current prices without reading history. Wallet initialization,
UTC daily crediting and row locking use one UPSERT RETURNING; the transaction
still serializes trades, and request IDs retain their original idempotency checks.
This adds no schema migration, distributed cache or periodic background queries.

Acceptance: 57 Android unit tests, 10 emulator workflow tests and 5 PostgreSQL
community/market integration tests passed. Device tests cover a held vote response,
immediate preview, duplicate rejection, failed-response rollback and reopening/
expanding cached comments without an additional request. PostgreSQL checks cover
concurrent replay, insufficient funds, daily crediting, candle ordering/limits and
the real daily baseline. Daily-credit test setup uses UTC rather than database
session current_date, which can differ after Sydney midnight.

The 512 MiB deployment briefly displayed near-limit memory after startup. A
container check at minute 13 measured 486.8 MiB total, including 108.9 MiB file
cache (71.9 MiB inactive); Java RSS was about 395 MiB. OOM and OOM-kill counters
were zero, with zero restarts and all three probes passing. This is a single
observation, not evidence of capacity under sustained load or absence of leaks.
The detailed latency and runtime observations are recorded in
`deploy/northflank/latency-verification.json`.
