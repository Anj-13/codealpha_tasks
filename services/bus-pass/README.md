# Bus Pass Service (Task 3)

Sells bus tickets with server-side pricing, assigned seats and tamper-evident signatures.

**Stack:** Java 21, Spring Boot 4, Spring Data JPA, H2. **Port:** `8082`.

## Run

```bash
./mvnw -DskipTests package
java -jar target/buspass-0.0.1-SNAPSHOT.jar
```

Data goes to `~/buspassdb`, and four routes are added to an empty database.

It runs without Task 1. Tickets are then issued as `PENDING` and checked once Task 1 is back. Set `REMOVAL_INTEGRATION_ENABLED=false` to turn the integration off, which marks tickets `NOT_CHECKED`.

## Fares

| Category | Fare |
| --- | --- |
| `ADULT` | 100% |
| `STUDENT` | 80% |
| `SENIOR` | 70% |

## Tickets

- **Signed:** the checksum covers the ticket number, route, fare and currency. The QR token covers the ticket number, email and travel date. Both are HMAC-SHA256 signatures.
- **Seats:** bookings on a route take turns under a database lock, and each takes the next seat for that date. Seats continue correctly after a restart.
- **One booking per passenger:** the same email and name on the same route and date returns `409`, ignoring case and spacing.
- **Private:** reading a ticket needs its number and the booking email.

Tickets issued before signing was added stay readable, but verification reports `LEGACY_UNSIGNED`.

## Task 1 integration

See the [booking flow](../../docs/architecture.md#booking-flow) and [failure policy](../../docs/architecture.md#failure-policy).

## Scaling metric

`GET /api/buspass/metrics/scale` recommends one server plus one for every 25 bookings, up to 20. This is advisory, and nothing is provisioned. It counts all bookings ever made, so the figure never falls.

## Configuration

| Variable | Default | Purpose |
| --- | --- | --- |
| `SERVER_PORT` | `8082` | Port |
| `DB_URL` | `jdbc:h2:file:~/buspassdb;AUTO_SERVER=TRUE` | Database |
| `DB_USERNAME`, `DB_PASSWORD` | `sa`, empty | Database credentials |
| `REMOVAL_INTEGRATION_ENABLED` | `true` | Use Task 1 |
| `REMOVAL_BASE_URL` | `http://localhost:8081/api/records` | Task 1 address |
| `REMOVAL_RETRY_DELAY_MS` | `30000` | Delay between retries of `PENDING` tickets |
| `TICKET_SIGNING_SECRET` | Empty | Signing key. Empty creates `~/.buspass-signing-key`. |

Keep the signing key. Changing it makes every existing ticket fail verification.

## Test

```bash
./mvnw test
```

Tests run on an in-memory H2 database, so your data is never touched.

The endpoints are listed in [`docs/api-integration.md`](../../docs/api-integration.md#bus-pass-8082).
