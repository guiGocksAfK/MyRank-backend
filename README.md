# MyRank — Backend

REST API for **MyRank**, a platform where you rate and rank the movies, TV shows,
games, books and anime you consume, unify everything into a single ranking,
compare with friends and unlock achievements.

This repository contains the **Spring Boot API**. The React client lives in
[`guiGocksAfK/MyRank`](https://github.com/guiGocksAfK/MyRank).

![Java](https://img.shields.io/badge/Java-17-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5-6DB33F?logo=springboot&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-15-4169E1?logo=postgresql&logoColor=white)
![Build](https://img.shields.io/badge/build-Maven-C71A36?logo=apachemaven&logoColor=white)

## Table of Contents

- [Features](#features)
- [Tech Stack](#tech-stack)
- [Architecture](#architecture)
- [Getting Started](#getting-started)
- [Configuration](#configuration)
- [Database & Migrations](#database--migrations)
- [API Reference](#api-reference)
- [Badge System](#badge-system)
- [Project Structure](#project-structure)
- [Roadmap](#roadmap)
- [Contributing](#contributing)
- [License](#license)

## Features

- **Authentication** — e-mail/password with JWT, plus OAuth via Google and Discord.
- **Works & rankings** — CRUD for rated works, per-category tables and a unified
  cross-category ranking. Final score blends the user's rating with a
  time-invested bonus (`score + log10(minutes / 60)`).
- **Categories & master groups** — default tables (Games, Anime, Movies, Series)
  plus custom categories, grouped into "master tables".
- **Profile & avatar** — bio, plan, visibility and an uploadable profile picture
  (stored in Postgres, served from a public endpoint with cache headers).
- **Achievements** — 48 badges across 7 buckets, evaluated server-side and
  recalculated automatically whenever a work changes. See [Badge System](#badge-system).
- **External metadata** — search and detail proxies for TMDB (movies/TV),
  RAWG (games), Google Books (books) and Jikan/MyAnimeList (anime), plus a
  public showcase endpoint that feeds the landing page poster grid.

## Tech Stack

| Layer          | Choice                                        |
| -------------- | --------------------------------------------- |
| Language       | Java 17                                       |
| Framework      | Spring Boot 3.5 (Web, Data JPA, Security, Validation) |
| Database       | PostgreSQL 15                                 |
| Migrations     | Flyway                                        |
| Auth           | Spring Security + JWT ([jjwt](https://github.com/jwtk/jjwt)) |
| Build          | Maven (wrapper included)                      |
| Container      | Docker / Docker Compose                       |

## Architecture

```
controller/      HTTP layer — thin, delegates to services
service/         business logic
  service/badge/ badge catalog (enum), rule engine, recalculation
  service/external/ third-party API clients (TMDB, RAWG, Jikan, Google Books)
domain/entity/   JPA entities
repository/       Spring Data repositories
dto/             request/response records
security/        JWT filter, user details, auth helpers
config/          security, CORS, multipart, exception handling
```

- Stateless: no server sessions, every request carries a `Bearer` token.
- `spring.jpa.hibernate.ddl-auto=validate` — the schema is owned by Flyway, JPA
  only validates it against the entities.

## Getting Started

### Prerequisites

- JDK 17+
- Docker & Docker Compose (recommended) **or** a local PostgreSQL 15

### Run with Docker Compose

Brings up PostgreSQL and the API together:

```bash
docker compose up --build
```

The API listens on `http://localhost:8080`.

### Run locally (Maven wrapper)

Start only the database:

```bash
docker compose up -d db
```

Then run the app:

```bash
./mvnw spring-boot:run
```

On Windows:

```bash
mvnw.cmd spring-boot:run
```

### Build a jar

```bash
./mvnw clean package
java -jar target/MyRank-0.0.1-SNAPSHOT.jar
```

## Configuration

Defaults live in [`src/main/resources/application.properties`](src/main/resources/application.properties).
For local overrides, create `src/main/resources/application-local.properties`
(git-ignored) and run with `--spring.profiles.active=local`, or export the
environment variables below.

| Variable                 | Used for                              | Default (dev)                     |
| ------------------------ | ------------------------------------- | -------------------------------- |
| `SPRING_DATASOURCE_URL`  | JDBC URL                              | `jdbc:postgresql://localhost:5432/myrank` |
| `SPRING_DATASOURCE_USERNAME` | DB user                          | `postgres`                       |
| `SPRING_DATASOURCE_PASSWORD` | DB password                      | `postgres`                       |
| `jwt.secret`             | HMAC key for signing JWTs (**override in prod**, 32+ chars) | placeholder |
| `jwt.expiration`         | token lifetime in ms                  | `86400000` (24h)                 |
| `GOOGLE_CLIENT_ID`       | Google OAuth client id                | test client                     |
| `DISCORD_CLIENT_ID`      | Discord OAuth client id               | test client                     |
| `DISCORD_CLIENT_SECRET`  | Discord OAuth secret                  | _(empty)_                        |
| `TMDB_API_TOKEN`         | TMDB API bearer token                 | _(empty — movie/TV search disabled)_ |
| `RAWG_API_KEY`           | RAWG API key                          | _(empty — game search disabled)_ |
| `GOOGLE_BOOKS_API_KEY`   | Google Books API key                  | _(empty — book search disabled)_ |

CORS is restricted to `http://localhost:5173` (the Vite dev server) in
[`SecurityConfig`](src/main/java/br/com/myrank/config/SecurityConfig.java).

## Database & Migrations

Schema is managed by Flyway under
[`src/main/resources/db/migration`](src/main/resources/db/migration). Migrations
run automatically on startup.

> **Project convention:** during early development the schema is edited **in
> place** in the existing `V1__Estrutura_MyRank.sql` file (no incremental
> `ALTER` migrations). Drop and recreate the database after pulling schema
> changes:
>
> ```bash
> docker compose down -v && docker compose up -d db
> ```

## API Reference

Base URL: `http://localhost:8080/api`. All endpoints require
`Authorization: Bearer <token>` unless marked **public**.

### Auth

| Method | Endpoint                        | Description                       |
| ------ | ------------------------------- | -------------------------------- |
| POST   | `/auth/login`                   | e-mail + password → JWT (**public**) |
| POST   | `/auth/oauth/google`            | Google id token → JWT (**public**) |
| POST   | `/auth/oauth/discord`           | Discord access token → JWT (**public**) |
| POST   | `/auth/oauth/discord/callback`  | Discord OAuth code → JWT (**public**) |

### Users

| Method | Endpoint                | Description                          |
| ------ | ----------------------- | ----------------------------------- |
| POST   | `/users`                | Register (**public**)               |
| GET    | `/users/me`             | Current user                        |
| PUT    | `/users/me`             | Update username / bio               |
| GET    | `/users/{id}`           | Public profile                      |
| PUT    | `/users/me/avatar`      | Upload avatar (`multipart/form-data`, field `file`, ≤ 1 MB, PNG/JPEG/WebP) |
| DELETE | `/users/me/avatar`      | Remove avatar                       |
| GET    | `/users/{id}/avatar`    | Avatar image bytes (**public**, cached 1h) |

### Categories

`GET` · `POST` · `PUT /{id}` · `DELETE /{id}` on `/categories`.

### Works

| Method | Endpoint                    | Description                     |
| ------ | --------------------------- | ------------------------------ |
| POST   | `/works`                    | Create a rated work            |
| GET    | `/works/category/{id}`      | Works in a category            |
| GET    | `/works/unified`            | All works, ordered by final score |
| PUT    | `/works/{id}`               | Update                         |
| DELETE | `/works/{id}`               | Delete                         |

### Master Table Groups

`GET` · `POST` · `PUT /{id}` · `DELETE /{id}` on `/master-table-groups`.

### Badges

| Method | Endpoint    | Description                                        |
| ------ | ----------- | ------------------------------------------------- |
| GET    | `/badges`   | Full catalog + the current user's progress/unlocks |

### External metadata

| Method | Endpoint                          | Description               |
| ------ | -------------------------------- | ------------------------ |
| GET    | `/external/showcase`             | Poster URLs for the landing page (**public**) |
| GET    | `/external/search/{type}`        | `?query=` — `type` ∈ `movies\|tv\|games\|anime\|books` |
| GET    | `/external/{type}/{id}`          | Detail lookup            |

## Badge System

- The catalog is defined as a Java enum
  ([`BadgeDefinition`](src/main/java/br/com/myrank/service/badge/BadgeDefinition.java)):
  each constant carries a bucket, name, description, icon, target and a rule
  (`ToIntFunction<BadgeContext>`).
- `BadgeCatalogInitializer` upserts the enum into the `badges` table on every
  startup (and prunes rows that left the enum), so definitions can be tweaked
  without dropping the database.
- `BadgeService.recalculate(userId)` rebuilds a `BadgeContext` snapshot from the
  user's works + account and re-evaluates every rule. It runs after any work
  create/update/delete and on every `GET /badges`. Once earned, a badge stays
  earned even if progress later drops.
- 7 buckets: Games, Movies, Series, Books, Anime, General, "Using the site".

## Project Structure

```
src/main/java/br/com/myrank/
├── controller/     REST controllers
├── service/        business logic
│   ├── badge/      badge catalog + rule engine
│   └── external/   TMDB / RAWG / Jikan / Google Books clients
├── domain/
│   ├── entity/     JPA entities
│   └── enums/      AuthProvider, PlanType
├── dto/            request/response records
├── repository/     Spring Data JPA repositories
├── security/       JWT filter, user details service, AuthUtils
└── config/         SecurityConfig, CORS, MultipartConfig, GlobalExceptionHandler
src/main/resources/
├── application.properties
└── db/migration/   Flyway scripts
```

## Roadmap

- [ ] Social graph endpoints (the `follow` table exists; API and social badges pending)
- [ ] Activity history endpoint (`user_activity_history` table exists)
- [ ] AI insights endpoint (`/api/insights`, consumed by the client)
- [ ] Externalize `jwt.secret` and rotate the checked-in OAuth test credentials

## Contributing

This is a personal project, but issues and PRs are welcome.

1. Branch from the active development branch.
2. Keep controllers thin — logic goes in services.
3. Schema changes: edit `V1__Estrutura_MyRank.sql` in place (see
   [Database & Migrations](#database--migrations)) and drop the DB.
4. Make sure `./mvnw clean package` passes before opening a PR.

## License

No license has been chosen yet — all rights reserved by the author until one is
added.
