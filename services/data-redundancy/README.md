# Data Validation Service (Task 1)

Classifies customer records as unique, duplicate or in need of review, and stores only unique ones.

**Stack:** Java 21, Spring Boot 4, Spring Data JPA, SQLite. **Port:** `8081`.

## Run

```bash
./mvnw -DskipTests package
java -jar target/removalsystem-0.0.1-SNAPSHOT.jar
```

It runs on its own and calls no other service. Data goes to `~/removalsystem.db`, and three demo records are added to an empty database. Set `app.seed.enabled=false` to skip them.

## How a record is checked

1. Text is trimmed, repeated spaces are collapsed, emails are lowercased, and phone numbers are reduced to digits.
2. A SHA-256 fingerprint of the name, email, phone and address is compared with stored records.
3. The email and phone are then checked together and separately.

| Result | Status | Stored |
| --- | --- | --- |
| No match | `UNIQUE` | Yes |
| Same fingerprint, or same email and phone | `REDUNDANT` | No |
| Email or phone matches a different record | `FALSE_POSITIVE` | No |

The `FALSE_POSITIVE` name comes from the original project. It means a partial match held for manual review.

## Concurrency

The connection pool has one connection, so each check-and-insert transaction runs in turn, which matches SQLite's single writer. The unique fingerprint constraint is a backstop, in case two instances ever share a database.

## Configuration

| Variable | Default | Purpose |
| --- | --- | --- |
| `SERVER_PORT` | `8081` | Port |
| `DB_URL` | `jdbc:sqlite:~/removalsystem.db` | Database |
| `DB_USERNAME`, `DB_PASSWORD` | Empty | Database credentials |
| `ADMIN_API_KEY` | Empty | Key for full records. Empty refuses all admin requests. |

## Test

```bash
./mvnw test
```

Tests run on an in-memory H2 database, so your data is never touched.

The endpoints are listed in [`docs/api-integration.md`](../../docs/api-integration.md#data-validation-8081).
