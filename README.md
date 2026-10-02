# MyRank — API

Rate and rank anything you love. MyRank is built around pop culture (movies,
series, anime, manga, games, books and music), but a table can hold anything:
scores become tables, tables merge into a single ranking, and the ranking
becomes a profile of your taste.

A personal project, live at **[myrank-oficial.vercel.app](https://myrank-oficial.vercel.app/)**.
This repository contains the API; the web app lives in
[MyRank-frontend](https://github.com/guiGocksAfK/MyRank).

Detailed operational and security docs ([SECURITY.md](SECURITY.md),
[deploy/README.md](deploy/README.md) and [`docs/`](docs)) are written in Portuguese.

## Features

- **Tables built from templates.** Movies, series, anime, manga, games, books,
  songs and albums, each searching its own source (TMDB, MyAnimeList, RAWG,
  Google Books, Deezer). A table can mix templates (e.g. Games + Series), and the
  **Custom** template takes up to five fields of its own (text, number, date, yes/no).
- **Automatic metadata.** Picking a search result fills in cover, creator, date,
  duration and the details each card needs (seasons, episodes, volumes, pages,
  album, track count), plus genres kept for the AI analysis. A refresh action
  updates ongoing works such as airing series.
- **Unified ranking** across any selection of tables, with manual reordering and
  an optional **time-weighted score** (`score + log10(minutes / 60)`) for the
  templates where time spent means something.
- **AI Insights**: a taste profile generated from your scores (Gemini), with a
  follow-up chat and a daily budget per user.
- **Social layer, private by default.** Follow requests for private profiles,
  a feed, reactions, **takes** with threaded comments and taste affinity.
- **Chat** with direct messages, groups and invite links, in real time over
  WebSocket.
- **Achievements**: 48 badges evaluated server-side whenever a work changes.
- **Accounts** with e-mail and password, Google or Discord. E-mail ownership is
  proven by a code before the account exists; password recovery and account
  deletion also work by e-mail code. A short onboarding lets new users pick
  their tables and rank a first work.
- **Discord bot** support through a service key, so the bot can act for users
  who linked their Discord account.

## Architecture and hosting

| Component | Runs on | Notes |
|---|---|---|
| Frontend | Vercel | React SPA served with CSP and security headers. |
| API | Oracle Cloud VM (ARM64), in Docker | Behind a shared Caddy, which issues and renews the TLS certificate. |
| Database | Neon (PostgreSQL, São Paulo region) | TLS required on every connection; Neon's own point-in-time recovery. |
| E-mail | Brevo | Sign-up, password recovery and account deletion codes. |
| Backups | VM | See [Backups](#backups). |

A few operational details:

- **Automatic migrations on boot.** Flyway applies pending migrations before
  the API starts, and Hibernate only *validates* the schema against the entities.
- **Configuration validated at boot.** The API refuses to start without a
  `JWT_SECRET` of at least 32 characters, so it can never sign tokens with a
  placeholder.
- **Multi-arch image.** Multi-stage build on images that run on ARM64, since
  Alpine variants of the JDK are amd64-only.
- **Scale to zero.** The connection pool keeps no idle connections, so Neon can
  suspend the database when nobody is using the site.

The entire infrastructure runs on free tiers. The step-by-step deploy and VM
guide is in [deploy/README.md](deploy/README.md).

## Backups

Two layers today, each covering a different failure:

| Layer | Covers | Does not cover |
|---|---|---|
| Neon point-in-time recovery | Recent mistakes | Losing access to the Neon account |
| Daily dump on the VM (14 days) | Lost database, bad migration, deletion noticed later | Losing the VM |

The dump has been restored into a clean PostgreSQL and checked table by table.
It is **not yet encrypted nor copied off the VM**; closing that gap (an
encrypted, immutable off-site copy) is listed in [SECURITY.md](SECURITY.md).

## Tech stack

| Layer | Technology |
|---|---|
| Runtime | Java 17 |
| Framework | Spring Boot 3.5 (Web, Data JPA, Security, Validation, WebSocket) |
| Database | PostgreSQL + Flyway |
| Authentication | Spring Security, JWT ([jjwt](https://github.com/jwtk/jjwt)), BCrypt, Google and Discord OAuth |
| Real time | STOMP over WebSocket (SockJS) |
| External data | TMDB, MyAnimeList, RAWG, Google Books, Deezer (iTunes as fallback) |
| Cache | Caffeine (in-memory) |
| E-mail and AI | Brevo, Gemini (OpenAI-compatible endpoint) |
| Tests | JUnit 5, Mockito, AssertJ, Spring Boot Test against a disposable PostgreSQL |
| Infrastructure | Docker, Caddy |

## Security

A summary of what is in place. The full breakdown, including the reasoning
behind each decision and the known limitations, is in [SECURITY.md](SECURITY.md).

- **Authentication required by default.** Only sign-up, login, recovery, health
  and the public showcase are open. The user is loaded from the database on
  every request, and each token carries a session version: changing the
  password signs out every other device at once.
- **E-mail ownership proven before anything is created.** Six-digit codes are
  stored only as hashes, expire in 15 minutes and die after five wrong attempts.
  Recovery responds the same way whether or not an account exists.
- **Ownership checked on every operation**, private profiles respected in the
  feed, profiles and comparisons, and chat subscriptions authorized per
  conversation.
- **Rate limiting** on login, sign-up, codes and the external search proxy.
- **Strict input validation**, including the free-form `details` of each work
  (8 KB cap) and type-checked custom fields.
- **No internal details leaked in errors**; one-time tokens and codes are never
  stored in plain text.

## Running locally

Requirements: JDK 17 and Docker.

```bash
docker compose up -d db                # local PostgreSQL
./mvnw spring-boot:run                 # API at http://localhost:8080
```

Set at least `JWT_SECRET` (32+ characters) before starting; on Windows use
`mvnw.cmd`. The frontend must run on `http://localhost:5173`, the origin
allowed by CORS in development.

### Tests

Unit tests run anywhere. The integration and migration tests need a PostgreSQL;
run the whole suite against a disposable one:

```bash
docker run -d --name mr-test-db -e POSTGRES_PASSWORD=x -e POSTGRES_DB=myrank_test -p 55432:5432 postgres:15
./mvnw test -Dspring.datasource.url=jdbc:postgresql://localhost:55432/myrank_test -Dspring.datasource.username=postgres -Dspring.datasource.password=x
docker rm -f mr-test-db
```

Migration tests apply the schema to throwaway schemas, insert data in the old
format and check what each migration does to it. External APIs are always
mocked.

## Database and migrations

The schema is owned by Flyway in
[`src/main/resources/db/migration`](src/main/resources/db/migration):

- **V1–V8** consolidate the original sixteen migrations (a squash, proven
  identical to the previous schema by comparing both).
- **V9** content templates and per-work `details`.
- **V10** custom fields of the Custom template.
- **V11** onboarding progress, with new accounts private by default.

Applied migrations are never edited, and no change requires dropping the
database. Upgrading an existing production database to this version (the
baseline of the squash, then V9–V11) is described in
[deploy/README.md](deploy/README.md#atualizar-a-produção-para-esta-versão).
Templates and custom fields are documented in
[`docs/templates-phase1.md`](docs/templates-phase1.md), music search in
[`docs/music-search-integration.md`](docs/music-search-integration.md) and
onboarding in [`docs/onboarding.md`](docs/onboarding.md).

## Environment variables

The full, commented list is in [`deploy/.env.example`](deploy/.env.example).
The ones that differ between development and production:

| Variable | Development | Production |
|---|---|---|
| `DB_URL` | PostgreSQL from `docker-compose.yml` | Neon, with `sslmode=require` |
| `JWT_SECRET` | Any value with 32+ characters | Dedicated secret (`openssl rand -base64 48`) |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173` | Frontend domain |
| `FRONTEND_URL` | `http://localhost:5173` | Frontend domain, used in e-mails |
| `BREVO_API_KEY`, `MAIL_FROM_EMAIL` | Empty (e-mail features answer that sending is unavailable) | Brevo key and a verified sender |
| `SERVER_FORWARD_HEADERS_STRATEGY` | (empty) | `native`, since the API runs behind Caddy |

API keys for the external sources (`TMDB_API_TOKEN`, `RAWG_API_KEY`,
`GOOGLE_BOOKS_API_KEY`, `MAL_CLIENT_ID`) and `GEMINI_API_KEY` are optional: each
missing key only disables its own search or feature.

## Project structure

```
src/main/java/br/com/myrank/
├── controller/       thin REST and WebSocket entry points
├── service/          business rules
│   ├── badge/        badge catalog and rule engine
│   ├── external/     TMDB, MyAnimeList, RAWG, Google Books, Deezer and iTunes clients
│   ├── social/       follows, feed, takes, notifications and chat
│   ├── ai/           AI Insights and the daily budget
│   ├── code/         e-mail codes and signed passes (sign-up, recovery)
│   └── email/        Brevo client and the shared e-mail layout
├── domain/           JPA entities, enums and value objects
├── dto/              request and response records
├── repository/       Spring Data repositories
├── security/         JWT, rate limit, Discord bot key, WebSocket auth
└── config/           security, CORS, clock and error handling
src/main/resources/db/migration/   Flyway migrations
deploy/               production compose, VM setup and runbook
docs/                 feature contracts (Portuguese)
```
