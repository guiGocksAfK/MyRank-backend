# Security — MyRank API

Summary of protections currently in place and what still requires a decision. The
frontend has its own client-side protections (CSP and security headers via
`vercel.json`) described in the frontend repository.

## In place

| Area | How |
|---|---|
| Passwords | BCrypt with per-user salt (Spring default cost, 10). Never returned in responses. Minimum of 8 characters on creation and reset. Accounts created with Google or Discord have no password. |
| Sign-up | E-mail ownership is proven **before** an account row is created: 6-digit e-mail code, and final sign-up is accepted only with its pass. Typing someone else's e-mail cannot reserve anything. |
| E-mail codes | Sign-up, password recovery and account deletion use codes stored only as SHA-256 hashes (never the plain value) and compared in constant time. Sign-up and recovery: 6 digits, 15-minute TTL, invalidated after 5 wrong attempts, 30s resend cooldown, in-memory cap of 10,000 pending entries (`EmailCodeStore` evicts expired entries and rejects floods with 503). Deletion: 8 characters, 10-minute TTL, 1-minute resend cooldown, stored on the user row and protected by IP rate limiting (see [docs/autenticacao.md](docs/autenticacao.md)). |
| Step passes | Entering a valid code returns a short-lived JWT (`EmailPass`, 30 min sign-up / 15 min recovery) that unlocks the next step. Each flow signs with a key derived from `JWT_SECRET` + purpose (`signup-pass`, `reset-pass`): a sign-up pass cannot reset passwords, and no pass works as a session token. |
| Login | Social-only account, unknown e-mail and wrong password return the **exact same** error message. `"Email not verified"` is checked only after the password matches, so the endpoint never leaks which e-mails have pending sign-ups. |
| Password recovery | Unknown e-mails follow the same code-generation path as a real account (code generated, nothing sent), so responses and errors never reveal who has an account. Explicit product exception: social-only Google/Discord accounts immediately receive which provider to use. |
| Session | 24h HS256 JWT stored in the `myrank_session` cookie (`HttpOnly`, `Secure`, `SameSite=Strict`, `Path=/`). `JWT_SECRET` (32+ chars) is required at boot. The user is loaded from the database on every request, checking `users.token_version` against the token's `ver` claim: changing the password, calling "sign out of other devices" (`POST /api/users/me/sessions/revoke`) or signing out from Settings increments `token_version` and invalidates all active tokens at once. `POST /api/auth/logout` clears the cookie in the current browser. |
| Authentication | Every route requires a valid session (`myrank_session` cookie or `Authorization: Bearer` header during transition), except explicitly open routes: `/api/auth/**`, `POST /api/users` (final sign-up, gated by the step pass), `GET /api/health`, `GET /api/external/showcase` and the WebSocket handshake (`/ws/**`). For cookie-authenticated requests, state-changing methods (`POST`, `PUT`, `PATCH`, `DELETE`) require an `Origin` header matching `CORS_ALLOWED_ORIGINS` (CSRF defense alongside `SameSite=Strict`). Unauthenticated requests receive **401**; unauthorized ones receive **403**. |
| Authorization | Resource ownership verified on every table, work, subcategory, custom field and table group operation. Private profiles restrict works, takes, comments and comparisons to followers. Direct messages honor the recipient's `dmPolicy` (`EVERYONE` or `FOLLOWING` for new conversations); presence ("last seen") is exposed only between mutual followers. Direct group additions require mutual follow; kicking a member records a ban in `conversation_bans` (`V12`), blocking re-entry via directory, request or invite link until a moderator adds them back. Works only accept templates enabled on their table. |
| Real-time chat | SockJS handshake reads the `myrank_session` cookie and STOMP `CONNECT` validates the JWT (from cookie or `Authorization` header, with the same `token_version` check). Subscriptions are authorized per conversation, and direct publishing to broker destinations is blocked: messages enter only through the REST API. |
| Discord bot | `X-Bot-Key` header verified in constant time, restricted to an explicit allowlist of routes (`BOT_ROUTES` in `BotAuthenticationFilter` — never `/api/auth/**`, account deletion, session revocation or Discord linking; `/api/users/me` allows only exact `GET` and `PUT`). Disabled completely when `MYRANK_BOT_API_KEY` is blank. Requires a linked `discord_id` on the account and rate limits by Discord user ID rather than the bot's IP. |
| Rate limiting | Fixed 1-minute in-memory window (20,000 keys cap), matching decoded application paths (`UrlPathHelper` to block URL-encoding bypasses such as `/api/auth/log%69n`). Per-IP (or per-Discord-user for bot calls) limits: login 10, OAuth 15, forgot-password 3, forgot-password verify 10, reset-password 10, sign-up code 3, sign-up verify 10, registration (`POST /api/users`) 5, deletion code 3, account deletion (`DELETE /api/users/me`) 5, Discord link (`POST /api/users/me/discord`) 10, external search/details (`/api/external/**`) 40, bulk details refresh (`POST /api/works/refresh-details`) 2. Does not trust raw `X-Forwarded-For` directly (`SERVER_FORWARD_HEADERS_STRATEGY=native` behind Caddy). |
| Input validation | Bean Validation on DTOs with length limits. Per-work `details` is always a JSON object capped at 8 KB. Custom fields are strictly type-checked (`TEXT` up to 200 chars, finite `NUMBER`, valid ISO `DATE`, strict `BOOLEAN`). |
| Concurrency | Saving a work and updating table templates or custom fields cannot overlap (pessimistic row lock on the category), preventing a removed field from being restored by a concurrent write. |
| Error leakage | Closed error catalog (`src/main/resources/i18n/errors.json` + `ErrorMessages`): only cataloged user-facing messages are returned (translated to PT, EN or ES via `Accept-Language`); library exceptions, server misconfigurations and unmapped errors become a generic message while technical details stay in server logs. |
| Stored data | One-time codes are stored only as SHA-256 hashes (legacy link-based token columns were removed in `V16`). Account deletion is hard-delete, confirmed by e-mail code and username, with a confirmation e-mail sent afterwards. |
| HTTP headers | Spring Security defaults: HSTS (1 year, `includeSubDomains`; Spring recognizes HTTPS forwarded by Caddy), `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY` and `Cache-Control: no-store`. Verified in production. |
| CORS | Explicit origins in `CORS_ALLOWED_ORIGINS`, no wildcards. |
| Secrets | All secrets supplied via environment variables. Production `.env` stays off git. |
| Container | Final image runs as an unprivileged user (`myrank:myrank`). |
| Database | Neon PostgreSQL with mandatory TLS (`sslmode=require`). Schema owned by Flyway; Hibernate only validates (`ddl-auto=validate`). |

## Open items and pending decisions

1. **Finish removing the legacy `token` field from login responses.** The domain
   proxy (`myrank.duckdns.org` via Caddy), the `myrank_session` `HttpOnly; Secure; SameSite=Strict`
   cookie, `Origin` verification on writes, SockJS cookie handshake,
   `withCredentials: true` on the frontend, and `POST /api/auth/logout` are
   already live. The remaining cleanup step is removing `token` from
   `LoginResponseDTO` and dropping the `Authorization: Bearer` fallback in
   `JwtAuthenticationFilter` and `StompAuthChannelInterceptor`.

2. **Unencrypted backups stored only on the VM.** The daily dump keeps 14 days
   of history, but in plain text on the same VM as the API: if the VM is lost,
   only Neon PITR remains. Missing piece: client-side `age` encryption, off-site
   replication to Backblaze B2 with Object Lock, and failure alerting
   (e.g. healthchecks.io).

3. **In-memory state: single-instance only.** Rate limiting, e-mail codes and
   the music search cache live in process memory. Running more than one API
   instance would split rate-limit counters and e-mail codes across nodes, and
   restarting the API clears pending e-mail codes (users just request a new one).
   If the API ever scales beyond one instance, move all three to a shared store
   (Redis).

4. **Default BCrypt cost (10).** Raising the cost to 12 doubles cracking work
   twice (~200 ms per login, barely noticeable). Existing hashes remain valid
   because the cost is embedded in each hash and upgrade automatically on the
   next password change.

5. **`POST /api/auth/logout` alone only clears the browser cookie.** Signing out
   from the Settings UI already calls `POST /api/users/me/sessions/revoke`
   first, incrementing `token_version` and revoking all active tokens on the
   server before clearing the cookie. However, calling `POST /api/auth/logout`
   by itself without `/sessions/revoke` only clears the cookie in that browser,
   leaving any previously copied token valid until it expires (24h) or
   `token_version` changes.

6. **No per-account login lockout.** Rate limiting is per IP; an attacker with
   many IPs could still try passwords slowly against a single account. Options:
   progressive per-account delay, or an e-mail verification code after N failed
   attempts.

7. **No CSP or `Referrer-Policy` headers on API responses.** The API sends
   Spring Security's defaults (see [In place](#in-place)), but not these two.
   Because the API only returns JSON, the risk is low; both headers mainly
   protect the frontend, which already sets them.

8. **External URLs for avatars, group photos and work covers.** Profile photos,
   group images and work covers are URLs loaded directly by viewers' browsers,
   exposing each viewer's IP to whichever server hosts the image (`http` URLs are
   upgraded to `https` by the frontend CSP when possible). Alternatives:
   self-hosted uploads or an image proxy.

9. **LGPD data export covers only works.** Account deletion exists, and Settings
   lets users export their works as CSV or JSON. Takes, comments, chat messages
   and followers are not yet included in an export bundle.

10. **No audit trail or automated dependency CVE checks.** There is no audit
    log of who changed what, and no CI pipeline running tests or checking
    dependency CVEs (e.g. OWASP Dependency-Check).

11. **Open WebSocket connections are not terminated immediately when `token_version` changes.**
    The JWT and `token_version` are checked at STOMP `CONNECT`. After a password
    change or session revocation, an already-open WebSocket keeps receiving
    conversation events until it disconnects (though sending messages and
    creating subscriptions still check authorization and membership). Fix: close
    open WebSocket sessions for the user whenever `token_version` changes.
