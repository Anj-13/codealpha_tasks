# Development

## Prerequisites

- Java 21
- Node.js 22.12 or later, or Node.js 24, for the web app and the scripts

Maven is not needed, because each service includes the Maven Wrapper. Docker is not used.

## Start everything

```bash
node scripts/start-all.mjs
```

This builds any missing jars, starts the three services one at a time, then starts the web app at <http://localhost:5173>. Press `Ctrl+C` to stop everything. Add `--rebuild` after changing Java code, because existing jars are otherwise reused.

If a service fails to start, the script says so and carries on, and the web app shows that service as offline.

## Start one service

Run each from the repository root. On Windows, use `mvnw.cmd`. On macOS or Linux, use `./mvnw`.

```bash
# Task 1: Data Validation, port 8081
cd services/data-redundancy
./mvnw -DskipTests package
java -jar target/removalsystem-0.0.1-SNAPSHOT.jar

# Task 3: Bus Pass, port 8082
cd services/bus-pass
./mvnw -DskipTests package
java -jar target/buspass-0.0.1-SNAPSHOT.jar

# Task 4: Assistant, port 8090, with its own page at http://localhost:8090
cd services/chatbot
./mvnw -DskipTests package
java -jar target/chatbot-1.0.0.jar

# Web app, port 5173
cd apps/web
npm install
npm run dev
```

## Configuration

Everything has a local default, so no configuration is needed. To change something, set an environment variable. [`.env.example`](../.env.example) lists them all as a reference, and nothing loads that file automatically.

| Component | Variable | Default |
| --- | --- | --- |
| Data Validation | `SERVER_PORT` | `8081` |
| | `DB_URL` | SQLite at `~/removalsystem.db` |
| | `DB_USERNAME`, `DB_PASSWORD` | Empty |
| | `ADMIN_API_KEY` | Empty, which refuses all admin requests |
| Bus Pass | `SERVER_PORT` | `8082` |
| | `DB_URL` | H2 at `~/buspassdb` |
| | `DB_USERNAME`, `DB_PASSWORD` | `sa`, empty |
| | `REMOVAL_INTEGRATION_ENABLED` | `true` |
| | `REMOVAL_BASE_URL` | `http://localhost:8081/api/records` |
| | `REMOVAL_RETRY_DELAY_MS` | `30000` |
| | `TICKET_SIGNING_SECRET` | Empty, which creates `~/.buspass-signing-key` |
| Assistant | `CHATBOT_PORT` | `8090` |
| | `BUSPASS_BASE_URL` | `http://localhost:8082/api/buspass` |
| | `REMOVAL_BASE_URL` | `http://localhost:8081/api/records` |
| Web app | `WEB_PORT` | `5173` |
| | `RECORDS_URL` | `http://localhost:8081` |
| | `BUSPASS_URL` | `http://localhost:8082` |
| | `CHATBOT_URL` | `http://localhost:8090` |
| `start-all.mjs` | `RECORDS_PORT`, `BUSPASS_PORT` | `8081`, `8082` |

**Keep the signing key.** Changing it, or deleting `~/.buspass-signing-key`, makes every existing ticket fail verification as `TAMPERED`.

## Testing

| What | Command | Run from |
| --- | --- | --- |
| Data Validation | `./mvnw test` | `services/data-redundancy` |
| Bus Pass | `./mvnw test` | `services/bus-pass` |
| Assistant | `./mvnw test` | `services/chatbot` |
| Web app | `npm test` | `apps/web` |
| Web app types | `npm run typecheck` | `apps/web` |
| Everything together | `node scripts/integration-test.mjs --rebuild` | Repository root |

The integration test runs the real jars on spare ports with throwaway databases, so it never touches your data. It covers concurrent requests, outages, recovery and restarts. It needs ports `18081`, `18082` and `18090`.

Service tests use an in-memory database. The web app's tests stub `fetch`, so they do not need the services running.

[`INTEGRATION_CHECKLIST.md`](../INTEGRATION_CHECKLIST.md) has the same checks as manual steps.

## Data

- **Local databases:** `~/removalsystem.db` and `~/buspassdb`. They sit outside the repository, and `.gitignore` excludes database files.
- **Seed data:** Task 1 adds three demo records to an empty database, and Task 3 adds four routes.
- **Schema changes:** both services use `ddl-auto=update`. New columns must be nullable, or Hibernate cannot add them to a table that already has rows.
- **Before changing a database's structure,** test against a copy by pointing `DB_URL` at it.
