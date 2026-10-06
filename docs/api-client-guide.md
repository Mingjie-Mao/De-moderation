# Client integration guide

The backend is the source of truth whenever Android's backend switch is enabled.
UI data in `AppData` is a cache only: content and trade writes wait for a successful
server response. Votes display a pending optimistic preview immediately; success
reconciles it with server state and failure removes it and triggers a re-read.
Forum/thread screens refresh from the API; fresh loaded comments and their cursor
can be reused for 15 seconds when returning to the same thread.

## Authentication

Register or log in to obtain an access token and a rotating refresh token. Send
the access token as `Authorization: Bearer …`. On a 401, rotate once through
`POST /api/auth/refresh`; if rotation is rejected as invalid/expired, return to login. On a network
failure or server error, preserve the session and allow retry. Never store an
administrator password in a mobile build.

Password changes and `POST /api/auth/logout-all` increment a per-user token
version, so every older access token is rejected on its next request. Refresh
tokens are stored only as SHA-256 digests and each token can be consumed once.

Android Member mode signs in to a real server account through `POST /api/auth/login`;
it also offers normal registration. The public interview account is `1234` / `1234`.
Admin uses `12345` / `12345`. These are database accounts, not local role switches.
The default API URL is
`https://p01--de-moderation-api--z48dx52bgz5k.code.run`; Settings can override it with
`http://10.0.2.2:8080` for local emulator development. Existing installations using
the old Render default migrate once; custom hosts and a later explicit rollback
are preserved.
Admin selection instead requires real `POST /api/auth/login` credentials and
verifies `GET /api/users/me` reports an active `ADMIN`. Passwords are not saved.
Administrator access and rotating refresh tokens live in process memory: a
process restart, sign-out, invalid refresh or API-origin change requires login
again. Concurrent expired requests share a single refresh; network/5xx failures
preserve the session, and a 403 is never treated as access-token expiry.

The in-app administrator workspace and browser console use the same case,
assignment, decision and appeal APIs. Android queues load 30 rows per page;
case views render reported snapshots, changed current content, authenticated
private images, model advice, decision notes and audit history. Member and administrator sessions are separate. A signed-in administrator
uses the real server user id and the same rotating session for forum, profile
and notification calls; an ended admin session never falls back to a generated
member identity.
Password-reset email is disabled in the public demo.

## Forum and media flow

1. If an image is present, upload it first with multipart `POST /api/media`.
2. Pass the returned `mediaId` in a post or comment create/update request.
3. Read the canonical item returned by the write; do not invent a client id.
4. Refresh `GET /api/posts?forum=…` or
   `GET /api/posts/{id}/comments` after reconnecting.

The comments endpoint returns a flat page of at most `size` comments, counting
roots and replies together. Follow `nextCursor` while `hasMore` is true.
Each item's `parentCommentId` identifies its parent, which can be on an
earlier page; assemble the tree after merging pages. `replies` is retained as
an empty compatibility field.

Uploads are limited to 8 MiB and 20 megapixels. The server decodes and rewrites
JPEG/PNG content, stripping metadata before storage. Only the uploader can attach
an object; media referenced by visible content is publicly readable and carries
`Cache-Control: no-store` headers. A moderation decision or author edit can
remove that public reference at any time. Clients must not persist the bytes as
an indefinitely available public URL.
New responses use `?v=2` on public media URLs, bypassing browser entries
cached under the earlier immutable URL. A CDN that ignores query strings still
needs an explicit purge of the old `/api/media/*` cache.

## Moderation, notifications and appeals

Reports accept an optional `details` field. A report produces or joins one
durable moderation case. The reporter and affected author receive a
`MODERATION_DECISION` notification when a human decides it.
The first report freezes the target's text, author and attachment reference.
The reviewer sees that version even if the author later edits the content.
The case detail response exposes it as `content`, with `currentContent` and
`contentChanged` when the live version differs. Its media URL is under
`/api/admin/moderation-cases/{caseId}/media/{mediaId}` and requires an admin
bearer token.

The affected author can appeal a resolved `HIDE`, `DELETE` or `BAN` action with
`POST /api/appeals`. Only one pending appeal per author/case is allowed. Admins
process appeals in the app or web console; an overturn revises the original case to
`NONE`, reverses its effects and appends audit entries. Clients list and mark
notifications read through `/api/notifications`.

## Browser admin application

The hosted reviewer console is [de-moderation-review-demo.pages.dev](https://de-moderation-review-demo.pages.dev/).

Build `admin-web` with `NEXT_PUBLIC_API_BASE_URL` set to the public API origin.
The admin site's origin (not the API origin) must be in `CORS_ALLOWED_ORIGINS`. The admin application uses a
session-scoped access and rotating refresh tokens, retries a request once after
an access-token expiry, supports case claim/release, blocks conflicting
reviewers, records decision notes, displays reported and current media evidence
with audit trails, and processes appeals. Case and appeal queues use pages of
up to 100 rows. It contains no bundled administrator credential.

## Community and virtual Heat Market

All routes below require the signed-in account's JWT; the actor ID is never taken from a request body.

| Route | Purpose |
| --- | --- |
| `POST /api/community/state` | Batch `postIds`, `commentIds`, `userIds` (at most 100 each): scores, visible comment counts, own votes/bookmarks/follows, and real profile counts |
| `PUT /api/community/posts/{id}/vote` | Set vote to `-1`, `0` (cancel), or `1`; comments use `/comments/{id}/vote` |
| `PUT` / `DELETE /api/community/posts/{id}/bookmark` | Save / remove bookmark; repeated requests are idempotent |
| `PUT` / `DELETE /api/community/users/{id}/follow` | Follow / unfollow; self-follow is rejected |
| `GET /api/community/posts?kind=LIKED\|BOOKMARKED` | Current account's collection; UUID cursor and size 1–100; hidden/deleted posts excluded |
| `GET /api/community/users/{id}/comments?size=30&cursor=UUID` | Public authored comments and parent post metadata, size 1–100; hidden/deleted threads excluded |
| `GET /api/community/users/{id}/following` or `/followers` | Real relation list, UUID cursor and size 1–100 |
| `GET /api/market` | Activity-based quotes, five-minute candles with `source`, and current account's portfolio |
| `POST /api/market/trades` | `requestId` UUID, `forumKey`, `action` (`BUY`, `SELL`, `OPEN_SHORT`, `CLOSE_SHORT`), `units` 1–10000, `expectedPrice` |
| `GET /api/market/trades` | Current account's immutable ledger, UUID cursor and size 1–100 |
| `GET /api/market/leaderboard?page=0&size=30` | Active real wallet accounts, ordered by marked assets; bounded page size |

Market points are virtual. Initial cash is 1000. On the first wallet access on a new **UTC date**, cash below 1000 is topped up once; cumulative grants are recorded so returns exclude top-ups. Price is the app's heat formula applied to actual live posts/comments/votes in the previous 24 hours, bounded to 1–10000. Quotes are observed on requests; normal candles have `source=LIVE`. The interview database also preserves 24 samples from the original App scenario with `source=IMPORTED_DEMO`; they are imported virtual history, not measured prices. Each school/side position is limited to 10000 units and wallet cash to 500 million points.

The wallet row lock serializes trades and daily crediting. Each successful request ID is unique per account; exact retries return the original receipt, changed payloads return 409. Stale quotes, insufficient cash or holdings also return 409 without partial writes. Shorts reserve collateral, have losses capped at that collateral, and return the exact allocated collateral plus P/L when closed. Total assets include both long value and marked short collateral. Leaderboard returns use cumulative grants rather than a fixed initial balance. Android keeps account/origin-scoped UI projections only; the original eight sample market participants are backed by persisted wallets and closed virtual ledgers marked `IMPORTED_DEMO`. Later account activity is preserved. They are sample identities, not organic user or financial-return claims.


Post create/edit accepts optional `category` (maximum 50 characters). Responses include persisted `category` and nullable `pinRank`; authors cannot set pin rank through these routes. Social votes, bookmarks, follows, comments/replies and existing-user @ mentions generate in-app notifications in the same transaction. Repeat identical actions do not create duplicate notifications. This does not require email.

The explicit `CompleteDemoImport.java` in the Android repository uses V15 and one transaction to restore the original full interview dataset. It is never run at application startup. The batch marker prevents duplicate imports; current account permissions and existing moderation/audit records are preserved.
