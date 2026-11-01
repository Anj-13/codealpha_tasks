# API and Integration

All request and response bodies are JSON. In unified mode, the web app forwards the same paths to each service.

## Error format

Every service sends errors in this shape. `fieldErrors` only appears on validation failures.

```json
{
  "timestamp": "2026-10-31T22:03:39.704Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "fieldErrors": { "email": "Invalid email format" }
}
```

## Data Validation (`:8081`)

| Method | Path | Purpose |
| --- | --- | --- |
| `POST` | `/api/records` | Check a record and store it if unique |
| `POST` | `/api/records/validate` | Check a record without storing anything |
| `GET` | `/api/records` | List records, masked unless a valid admin key is sent |
| `GET` | `/api/records/{id}` | Get one record, masked unless a valid admin key is sent |
| `GET` | `/api/records/summary` | Get the record count and last stored time |
| `GET` | `/actuator/health` | Health check |

**Request**

```json
{ "fullName": "Ava Thompson", "email": "ava@example.com", "phone": "07123 456 789", "address": "21 King Street" }
```

`fullName`, `email` and `phone` are required. `phone` takes 7 to 20 characters of digits, spaces and `+ ( ) -`.

**Outcomes**

| `status` | `POST /api/records` | `POST /validate` | Meaning |
| --- | --- | --- | --- |
| `UNIQUE` | `201`, stored | `200` | No match |
| `REDUNDANT` | `409`, not stored | `200` | Same record, or same email and phone |
| `FALSE_POSITIVE` | `422`, not stored | `200` | Email or phone matches a different record |

**Masking:** without an admin key, `fullName` becomes `A*** T***`, `email` becomes `a***@example.com`, `phone` shows its last three digits, `address` is `null`, and `masked` is `true`.

**Admin access:** send `X-Admin-Key`. A wrong key returns `403`. It is never treated as a request for masked data.

## Bus Pass (`:8082`)

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/api/buspass/routes` | List routes and base fares |
| `POST` | `/api/buspass/tickets/book` | Book a ticket |
| `POST` | `/api/buspass/tickets/lookup` | Read a ticket, given its number and email |
| `GET` | `/api/buspass/tickets/{ticketNumber}/verify` | Verify a ticket, with optional `?token=` |
| `GET` | `/api/buspass/metrics/scale` | Get the advisory server recommendation |
| `GET` | `/actuator/health` | Health check |

**Book a ticket**

```json
{
  "passengerName": "Pax One",
  "email": "pax.one@example.com",
  "phone": "07000 100 001",
  "routeCode": "R-100",
  "travelDate": "2030-01-15",
  "passengerCategory": "STUDENT"
}
```

`passengerCategory` is `ADULT` (full fare), `STUDENT` (80%) or `SENIOR` (70%).

| Status | Meaning |
| --- | --- |
| `201` | Ticket issued. Check `validationStatus` in the body. |
| `400` | A field is missing or invalid, or the date is in the past |
| `404` | Unknown route code |
| `409` | This passenger already has a booking on this route and date |
| `503` | The route stayed locked too long. Try again. |

**Look up a ticket**

```json
{ "ticketNumber": "123e4567-e89b-12d3-a456-426614174000", "email": "pax.one@example.com" }
```

A wrong number and a wrong email both return the same `404`.

**Verify a ticket**

```json
{ "ticketNumber": "123e4567-e89b-12d3-a456-426614174000", "valid": true, "reason": "VALID" }
```

| `reason` | Meaning |
| --- | --- |
| `VALID` | The signatures match and the ticket is active |
| `TAMPERED` | The fare, route, email or date changed after issue |
| `TOKEN_MISMATCH` | The supplied `token` is wrong |
| `NOT_ACTIVE` | The ticket is not active |
| `LEGACY_UNSIGNED` | Issued before signing, so it cannot be verified |

## Assistant (`:8090`)

| Method | Path | Purpose |
| --- | --- | --- |
| `POST` | `/api/chat` | Send a message of up to 500 characters |
| `GET` | `/actuator/health` | Health check |
| `GET` | `/` | The Assistant's own standalone page |

```json
{ "message": "show routes" }
```

```json
{ "intent": "ROUTES", "response": "Available routes: R-100 Leeds -> Manchester ...", "source": "LIVE" }
```

| Example message | Intent | Source |
| --- | --- | --- |
| `show routes` | `ROUTES` | Live |
| `what are the fares` | `PRICING` | Live, with fixed fare rules |
| `check booking <ticket number> <email>` | `BOOKING_LOOKUP` | Live |
| `validate <name> <email> <phone>` | `VALIDATION_CHECK` | Live, stores nothing |
| `show redundancy records` | `REDUNDANCY_STATUS` | Live, masked |
| `server scale status` | `SCALING` | Live |
| `how does validation work` | `VALIDATION_HELP` | Static |
| `why was my record flagged` | `DUPLICATE_HELP` | Static |
| `my booking failed` | `BOOKING_ERROR_HELP` | Static |
| `can i get a refund` | `TICKET_RULES` | Static |

## Service-to-service calls

| Caller | Call | Timeouts | If it fails |
| --- | --- | --- | --- |
| Bus Pass | `POST /api/records/validate` | 2 s connect, 3 s request | Ticket is issued as `PENDING` |
| Bus Pass | `POST /api/records` | 2 s connect, 3 s request | Ticket stays `PENDING` and is retried |
| Assistant | Task 1 and Task 3 reads | 2 s connect, 3 s request | A plain-text "could not reach" reply |

The retry job runs every 30 seconds by default, handles up to 50 tickets, and stops at the first failure. Repeating a store is safe, because a repeat comes back as a duplicate.

## Changes from the original API

| Change | Reason |
| --- | --- |
| `POST /tickets/book` requires `phone` and returns `201` instead of `200` | Every passenger used to share a placeholder phone number, so most never reached Task 1 |
| `GET /tickets/{ticketNumber}` is replaced by `POST /tickets/lookup` | Booking data needs the email as well as the number |
| `verify` returns a `reason` | A plain boolean could not explain a failure |
| `GET /api/records` is masked by default | It exposed personal data to anyone |
| Error bodies use one format | Each service used to differ |
| `fullname` in field errors is now `fullName` | It did not match the request field |
| Chatbot replies add `source` | Static and live answers are now separate |
| The H2 console settings are removed | The console never served a page, returning `404` |
