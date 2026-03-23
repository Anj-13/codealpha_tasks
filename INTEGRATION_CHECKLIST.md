# Cloud Assignment Integration Checklist

This checklist proves that Task 1, Task 3, and Task 4 are connected and working as one cloud system.

## Services and ports

- Task 1 (Data Redundancy Removal): `http://localhost:8081`
- Task 3 (Cloud Bus Pass): `http://localhost:8082`
- Task 4 (Chatbot): `http://localhost:8090`

## 1. Build all services

Run in three separate terminals:

```powershell
Set-Location .\removalsystem
.\mvnw.cmd -DskipTests package
```

```powershell
Set-Location .\buspass
.\mvnw.cmd -DskipTests package
```

```powershell
Set-Location .\chatbot
.\mvnw.cmd -DskipTests package
```

Expected result:

- All commands end with `BUILD SUCCESS`.

## 2. Start all services

Run in three separate terminals:

```powershell
Set-Location .\removalsystem
java -jar target\removalsystem-0.0.1-SNAPSHOT.jar
```

```powershell
Set-Location .\buspass
java -jar target\buspass-0.0.1-SNAPSHOT.jar
```

```powershell
Set-Location .\chatbot
$env:BUSPASS_BASE_URL="http://localhost:8082/api/buspass"
$env:REMOVAL_BASE_URL="http://localhost:8081/api/records"
.\mvnw.cmd -q exec:java
```

Expected result:

- Task 1 logs show startup on port `8081`.
- Task 3 logs show startup on port `8082`.
- Task 4 logs show `Chatbot started on http://localhost:8090`.

## 3. Verify Task 3 API and dynamic scale metrics

```powershell
Invoke-RestMethod -Uri "http://localhost:8082/api/buspass/routes" -Method Get
Invoke-RestMethod -Uri "http://localhost:8082/api/buspass/metrics/scale" -Method Get
```

Expected result:

- Routes list returned.
- Metrics object returned with fields: `totalBookings`, `recommendedServers`, `maxServers`.

## 4. Verify Task 1 redundancy detection and duplicate prevention

Submit a unique record once, then submit the same payload again.

```powershell
$payload = '{"fullName":"Cloud Test User","email":"cloud.test.user@example.com","phone":"07000111222","address":"Leeds"}'
Invoke-RestMethod -Uri "http://localhost:8081/api/records" -Method Post -ContentType "application/json" -Body $payload
Invoke-RestMethod -Uri "http://localhost:8081/api/records" -Method Post -ContentType "application/json" -Body $payload
```

Expected result:

- First call returns a stored `UNIQUE` record.
- Second call is rejected as `REDUNDANT` (duplicate not added).

## 5. Verify Task 3 -> Task 1 integration path

Book one ticket using a real route code from step 3.

```powershell
$routes = Invoke-RestMethod -Uri "http://localhost:8082/api/buspass/routes" -Method Get
$routeCode = $routes[0].code
$book = @{
	passengerName = "Integration Demo"
	email = "integration.demo.$((Get-Random -Maximum 100000))@example.com"
	routeCode = $routeCode
	travelDate = (Get-Date).AddDays(1).ToString("yyyy-MM-dd")
	passengerCategory = "ADULT"
} | ConvertTo-Json
Invoke-RestMethod -Uri "http://localhost:8082/api/buspass/tickets/book" -Method Post -ContentType "application/json" -Body $book
```

Expected result:

- Ticket is created by Task 3.
- Task 3 sends passenger metadata to Task 1 through its integration client.
- Task 3 remains available even if Task 1 flags redundancy.

## 6. Verify Task 4 chatbot reads Task 1 and Task 3

```powershell
Invoke-RestMethod -Uri "http://localhost:8090/api/chat" -Method Post -ContentType "application/json" -Body '{"message":"show routes"}'
Invoke-RestMethod -Uri "http://localhost:8090/api/chat" -Method Post -ContentType "application/json" -Body '{"message":"server scale status"}'
Invoke-RestMethod -Uri "http://localhost:8090/api/chat" -Method Post -ContentType "application/json" -Body '{"message":"show redundancy records"}'
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
