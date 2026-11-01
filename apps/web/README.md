# Web App

The unified frontend for all three services.

**Stack:** React, TypeScript, Vite, React Router. **Port:** `5173`.

## Run

```bash
npm install
npm run dev
```

Open <http://localhost:5173>. Any service that is not running shows as offline, and the rest keep working. `npm run build` then `npm run preview` serves the production build through the same proxy.

## Pages

| Page | Purpose |
| --- | --- |
| Dashboard | Service status and live figures |
| Data Validation | Check and store records, and view stored records |
| Bus Pass | View routes and fares, book a ticket, and find and verify a booking |
| Assistant | Talk to the chatbot |
| Architecture | How the platform fits together |

Every figure comes from a service. When one cannot be loaded, the page says "Unavailable" rather than showing a placeholder.

## How it reaches the services

The browser only calls this app's origin, and [`vite.config.ts`](vite.config.ts) forwards each path:

| Path | Service |
| --- | --- |
| `/api/records` | Data Validation |
| `/api/buspass` | Bus Pass |
| `/api/chat` | Assistant |
| `/health/{records,buspass,chatbot}` | Each service's health check |

Set `RECORDS_URL`, `BUSPASS_URL`, `CHATBOT_URL` or `WEB_PORT` to change the defaults.

## Structure

```text
src/
├── api/          # One file per service, plus the fetch client
├── components/   # Layout and shared UI
├── hooks/        # Data loading, service health, theme
├── pages/        # One file per page
├── test/         # Page tests
├── format.ts     # Labels and number and date formats
└── styles.css    # Design tokens and all styles
```

Fare estimates on the Bus Pass page repeat the service's discount rules for display. The fare on the confirmed ticket comes from the service and is the one that counts.

## Test

```bash
npm test
npm run typecheck
```
