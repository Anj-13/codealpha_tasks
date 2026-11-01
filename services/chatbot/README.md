# Assistant Service (Task 4)

A retrieval-based chatbot that answers from predefined rules and live lookups against Tasks 1 and 3. It has no machine learning model and generates no text.

**Stack:** Java 21, JDK `HttpServer`, Jackson. **Port:** `8090`.

## Run

```bash
./mvnw -DskipTests package
java -jar target/chatbot-1.0.0.jar
```

Open <http://localhost:8090> for its standalone page. It runs on its own: fixed answers always work, and live lookups say so when a service is unavailable.

## How it answers

| Class | Job |
| --- | --- |
| `IntentService` | Picks an intent from an ordered list of patterns. The first match wins. |
| `MessageDetails` | Pulls a ticket number, email, phone and name from the message. |
| `StaticResponses` | Holds every fixed answer. |
| `ExternalKnowledgeService` | Holds every live lookup. |
| `ChatService` | Connects the three, and labels each reply `STATIC` or `LIVE`. |

To add an answer, add an `Intent`, add a rule to `IntentService` above any broader rule, then add the text to `StaticResponses` or a lookup to `ExternalKnowledgeService`.

## What it can do

- **Bus pass:** list routes, give fares and ticket rules, explain booking errors, and check a booking given its ticket number and email.
- **Data validation:** explain the checks and duplicate errors, check a record without storing it, and list masked records.
- **Platform:** report the advisory scaling figures.

## Privacy

The Assistant holds no admin key, so it only ever sees masked records. A booking lookup needs the same ticket number and email as anyone else, and messages are not logged.

## Configuration

| Variable | Default | Purpose |
| --- | --- | --- |
| `CHATBOT_PORT` | `8090` | Port |
| `BUSPASS_BASE_URL` | `http://localhost:8082/api/buspass` | Task 3 address |
| `REMOVAL_BASE_URL` | `http://localhost:8081/api/records` | Task 1 address |

## Test

```bash
./mvnw test
```

The endpoint and example messages are in [`docs/api-integration.md`](../../docs/api-integration.md#assistant-8090).
