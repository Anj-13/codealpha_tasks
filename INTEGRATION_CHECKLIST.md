# Integration Checklist

Manual steps to confirm that the services work together. The same checks, and many more, run automatically with:

```bash
node scripts/integration-test.mjs --rebuild
```

Use a date in the future wherever these steps book a ticket.

## Services and ports

- Task 1 (Data Validation): `http://localhost:8081`
- Task 3 (Bus Pass): `http://localhost:8082`
- Task 4 (Assistant): `http://localhost:8090`
- Web app: `http://localhost:5173`

## 1. Start everything

```bash
node scripts/start-all.mjs
```

Expected result: each service reports that it is up, and the dashboard shows all three as online.

## 2. Check health

```cmd
curl http://localhost:8081/actuator/health
curl http://localhost:8082/actuator/health
curl http://localhost:8090/actuator/health
```

Expected result: each returns `"status":"UP"`.

## 3. Check Task 1 duplicate detection

Send the same record twice.

```cmd
curl -X POST http://localhost:8081/api/records -H "Content-Type: application/json" -d "{\"fullName\":\"Cloud Test User\",\"email\":\"cloud.test.user@example.com\",\"phone\":\"07000111222\",\"address\":\"Leeds\"}"
curl -X POST http://localhost:8081/api/records -H "Content-Type: application/json" -d "{\"fullName\":\"Cloud Test User\",\"email\":\"cloud.test.user@example.com\",\"phone\":\"07000111222\",\"address\":\"Leeds\"}"
```

Expected result: the first returns `UNIQUE` (`201`), and the second returns `REDUNDANT` (`409`).

## 4. Check Task 3 and Task 1 together

```cmd
curl http://localhost:8082/api/buspass/routes
curl -X POST http://localhost:8082/api/buspass/tickets/book -H "Content-Type: application/json" -d "{\"passengerName\":\"Integration Demo\",\"email\":\"integration.check@example.com\",\"phone\":\"07000333444\",\"routeCode\":\"R-100\",\"travelDate\":\"2030-01-15\",\"passengerCategory\":\"ADULT\"}"
```

Expected result: the booking returns `201` with `"validationStatus":"VERIFIED"`, and sending it again returns `409`.

Then confirm that the passenger reached Task 1, masked:

```cmd
curl http://localhost:8081/api/records
```

Expected result: a new `I*** D***` record, with `"masked":true`.

## 5. Check ticket lookup and verification

Use the `ticketNumber` from step 4.

```cmd
curl -X POST http://localhost:8082/api/buspass/tickets/lookup -H "Content-Type: application/json" -d "{\"ticketNumber\":\"TICKET_NUMBER\",\"email\":\"integration.check@example.com\"}"
curl http://localhost:8082/api/buspass/tickets/TICKET_NUMBER/verify
```

Expected result: the lookup returns the ticket, a different email returns `404`, and verification returns `"valid":true`.

## 6. Check the Assistant

```cmd
curl -X POST http://localhost:8090/api/chat -H "Content-Type: application/json" -d "{\"message\":\"show routes\"}"
curl -X POST http://localhost:8090/api/chat -H "Content-Type: application/json" -d "{\"message\":\"show redundancy records\"}"
curl -X POST http://localhost:8090/api/chat -H "Content-Type: application/json" -d "{\"message\":\"how does validation work\"}"
```

Expected result: routes come from Task 3, masked records from Task 1, and the last answer is fixed text with `"source":"STATIC"`.

## 7. Check an outage

1. Stop Task 1.
2. Book a ticket for a new passenger.
3. Open the dashboard.
4. Start Task 1 again and wait 30 seconds.
5. Look up the ticket.

Expected result: the booking returns `201` with `"validationStatus":"PENDING"`, the dashboard shows Data Validation as offline, and the lookup then shows `VERIFIED`.
