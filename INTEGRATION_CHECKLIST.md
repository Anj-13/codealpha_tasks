# Cloud Assignment Integration Checklist

This checklist proves that Task 1, Task 3, and Task 4 are connected and working as one cloud system.

## Services and ports

- Task 1 (Data Redundancy Removal): `http://localhost:8081`
- Task 3 (Cloud Bus Pass): `http://localhost:8082`
- Task 4 (Chatbot): `http://localhost:8090`

## 1. Build all services

Run each command in its own terminal from the repo root:

```cmd
cd CodeAlpha_RemovalSystem
.\mvnw.cmd -DskipTests package
```

```cmd
cd CodeAlpha_BusPass
.\mvnw.cmd -DskipTests package
```

```cmd
cd CodeAlpha_Chatbot
.\mvnw.cmd -DskipTests package
```

Expected result:

- All commands end with `BUILD SUCCESS`.

## 2. Start all services

Run each block in its own terminal from the repo root:

**Terminal 1 — Task 1 (port 8081):**
```cmd
cd CodeAlpha_RemovalSystem
java -jar target\removalsystem-0.0.1-SNAPSHOT.jar
```

**Terminal 2 — Task 3 (port 8082):**
```cmd
cd CodeAlpha_BusPass
java -jar target\buspass-0.0.1-SNAPSHOT.jar
```

**Terminal 3 — Task 4 (port 8090):**
```cmd
cd CodeAlpha_Chatbot
set BUSPASS_BASE_URL=http://localhost:8082/api/buspass
set REMOVAL_BASE_URL=http://localhost:8081/api/records
.\mvnw.cmd -q exec:java
```

Expected result:

- Task 1 logs show startup on port `8081`.
- Task 3 logs show startup on port `8082`.
- Task 4 logs show `Chatbot started on http://localhost:8090`.

## 3. Verify Task 3 API and dynamic scale metrics

Run each in any terminal:

```cmd
curl http://localhost:8082/api/buspass/routes
curl http://localhost:8082/api/buspass/metrics/scale
```

Expected result:

- Routes list returned.
- Metrics object returned with fields: `totalBookings`, `recommendedServers`, `maxServers`.

## 4. Verify Task 1 redundancy detection and duplicate prevention

Submit a unique record once, then submit the same payload again.

```cmd
curl -X POST http://localhost:8081/api/records -H "Content-Type: application/json" -d "{\"fullName\":\"Cloud Test User\",\"email\":\"cloud.test.user@example.com\",\"phone\":\"07000111222\",\"address\":\"Leeds\"}"
curl -X POST http://localhost:8081/api/records -H "Content-Type: application/json" -d "{\"fullName\":\"Cloud Test User\",\"email\":\"cloud.test.user@example.com\",\"phone\":\"07000111222\",\"address\":\"Leeds\"}"
```

Expected result:

- First call returns a stored `UNIQUE` record (HTTP 201).
- Second call is rejected as `REDUNDANT` (HTTP 409).

## 5. Verify Task 3 -> Task 1 integration path

Get a route code first, then book a ticket.

```cmd
curl http://localhost:8082/api/buspass/routes
```

Pick a route code from the response (e.g. `LON-MAN`), then run:

```cmd
curl -X POST http://localhost:8082/api/buspass/tickets/book -H "Content-Type: application/json" -d "{\"passengerName\":\"Integration Demo\",\"email\":\"integration.demo@example.com\",\"routeCode\":\"LON-MAN\",\"travelDate\":\"2026-06-16\",\"passengerCategory\":\"ADULT\"}"
```

Expected result:

- Ticket is created by Task 3.
- Task 3 sends passenger metadata to Task 1 through its integration client.
- Task 3 remains available even if Task 1 flags redundancy.

## 6. Verify Task 4 chatbot reads Task 1 and Task 3

```cmd
curl -X POST http://localhost:8090/api/chat -H "Content-Type: application/json" -d "{\"message\":\"show routes\"}"
curl -X POST http://localhost:8090/api/chat -H "Content-Type: application/json" -d "{\"message\":\"server scale status\"}"
curl -X POST http://localhost:8090/api/chat -H "Content-Type: application/json" -d "{\"message\":\"show redundancy records\"}"
```

Expected result:

- Chatbot returns route summaries from Task 3.
- Chatbot returns scale metrics from Task 3.
- Chatbot returns record summary from Task 1.

## 7. Evidence captured in this environment

Observed from live run:

- `TOTAL_BOOKINGS=4`
- `RECOMMENDED_SERVERS=1`
- `RECORDS_COUNT=5`
- `CHAT_INTENT=SCALING`
- `CHAT_RESPONSE=Total bookings: 4, recommended active servers: 1, max servers: 20.`

This confirms cross-service communication is active for cloud deployment demo purposes.
