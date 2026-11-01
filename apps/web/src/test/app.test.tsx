import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { ALL_UP, renderApp, stubFetch } from './helpers';

const ROUTES = [
  { code: 'R-100', source: 'Leeds', destination: 'Manchester', baseFare: 12.5 },
  { code: 'R-110', source: 'Leeds', destination: 'York', baseFare: 8 },
];

const TICKET = {
  ticketNumber: '123e4567-e89b-12d3-a456-426614174000',
  passengerName: 'Pax One',
  email: 'pax@example.com',
  phone: '07000 100 001',
  routeCode: 'R-100',
  routeName: 'Leeds -> Manchester',
  travelDate: '2030-01-15',
  seatNumber: 4,
  farePaid: 10,
  currency: 'GBP',
  status: 'ACTIVE',
  validationStatus: 'VERIFIED',
  createdAt: '2026-10-31T12:00:00',
};

const MASKED_RECORD = {
  id: 1,
  fullName: 'A*** T***',
  email: 'a***@example.com',
  phone: '********789',
  address: null,
  status: 'UNIQUE',
  validationReason: 'Record validated and accepted',
  stored: true,
  masked: true,
  createdAt: '2026-10-31T12:00:00',
};

const VALIDATION = { ...MASKED_RECORD, id: null, stored: false, masked: false, validationReason: 'No matching record found' };

describe('navigation', () => {
  it('moves between all five pages and marks the current one', async () => {
    stubFetch(ALL_UP);
    const user = userEvent.setup();
    renderApp();

    const nav = screen.getByRole('navigation', { name: 'Main' });
    expect(within(nav).getByRole('link', { name: 'Dashboard' })).toHaveAttribute('aria-current', 'page');

    for (const [link, heading] of [
      ['Data Validation', 'Data Validation'],
      ['Bus Pass', 'Bus Pass'],
      ['Assistant', 'Assistant'],
      ['Architecture', 'Architecture'],
    ]) {
      await user.click(within(nav).getByRole('link', { name: link }));
      expect(await screen.findByRole('heading', { level: 1, name: heading })).toBeInTheDocument();
      expect(within(nav).getByRole('link', { name: link })).toHaveAttribute('aria-current', 'page');
    }
  });

  it('shows a way back from an unknown page', async () => {
    stubFetch(ALL_UP);
    renderApp('/nowhere');

    expect(await screen.findByRole('heading', { name: 'Page not found' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Back to the dashboard' })).toBeInTheDocument();
  });
});

describe('dashboard', () => {
  it('shows real figures and service status', async () => {
    stubFetch({
      ...ALL_UP,
      'GET /api/records/summary': { body: { totalRecords: 7, lastStoredAt: '2026-10-31T12:00:00' } },
      'GET /api/buspass/routes': { body: ROUTES },
      'GET /api/buspass/metrics/scale': { body: { totalBookings: 30, recommendedServers: 2, maxServers: 20 } },
    });
    renderApp();

    expect(await screen.findByText('7')).toBeInTheDocument();
    expect(await screen.findByText('30')).toBeInTheDocument();
    expect(await screen.findByText('2 of 20')).toBeInTheDocument();
    expect(screen.getByText('Advisory only. No servers are provisioned.')).toBeInTheDocument();
    await waitFor(() => expect(screen.getAllByText('Online').length).toBeGreaterThanOrEqual(3));
  });

  it('keeps working and says what is unavailable when a service is offline', async () => {
    stubFetch({
      ...ALL_UP,
      'GET /health/records': 'offline',
      'GET /api/records/summary': 'offline',
      'GET /api/buspass/routes': { body: ROUTES },
      'GET /api/buspass/metrics/scale': { body: { totalBookings: 30, recommendedServers: 2, maxServers: 20 } },
    });
    renderApp();

    expect(await screen.findByText('Data Validation is offline')).toBeInTheDocument();
    expect(await screen.findByText('30')).toBeInTheDocument();
    expect(screen.getByText('Unavailable')).toBeInTheDocument();
  });
});

describe('data validation', () => {
  const routes = { ...ALL_UP, 'GET /api/records': { body: [MASKED_RECORD] } };

  async function fillForm(user: ReturnType<typeof userEvent.setup>) {
    await user.type(screen.getByLabelText('Full name'), 'Ava Thompson');
    await user.type(screen.getByLabelText('Email'), 'ava@example.com');
    await user.type(screen.getByLabelText('Phone'), '07123456789');
  }

  it('checks a record without storing it', async () => {
    const calls = stubFetch({ ...routes, 'POST /api/records/validate': { body: VALIDATION } });
    const user = userEvent.setup();
    renderApp('/validation');

    await fillForm(user);
    await user.click(screen.getByRole('button', { name: 'Check only' }));

    const result = (await screen.findByRole('heading', { name: 'Result' })).closest('.outcome') as HTMLElement;
    expect(within(result).getByText('Unique')).toBeInTheDocument();
    expect(within(result).getByText(/Nothing was stored/)).toBeInTheDocument();
    expect(calls.find((call) => call.key === 'POST /api/records/validate')?.body).toMatchObject({
      fullName: 'Ava Thompson',
      email: 'ava@example.com',
      phone: '07123456789',
    });
    expect(calls.some((call) => call.key === 'POST /api/records')).toBe(false);
  });

  it('treats a duplicate as a result, not a failure', async () => {
    stubFetch({
      ...routes,
      'POST /api/records': {
        status: 409,
        body: { ...VALIDATION, status: 'REDUNDANT', validationReason: 'Exact duplicate detected from fingerprint' },
      },
    });
    const user = userEvent.setup();
    renderApp('/validation');

    await fillForm(user);
    await user.click(screen.getByRole('button', { name: 'Check and store' }));

    expect(await screen.findByText('Duplicate')).toBeInTheDocument();
    expect(screen.getByText(/already exists/)).toBeInTheDocument();
    expect(screen.queryByText('The record could not be checked')).not.toBeInTheDocument();
  });

  it('shows validation errors beside their fields', async () => {
    stubFetch({
      ...routes,
      'POST /api/records/validate': {
        status: 400,
        body: {
          timestamp: 'now',
          status: 400,
          error: 'Bad Request',
          message: 'Validation failed',
          fieldErrors: { email: 'Invalid email format' },
        },
      },
    });
    const user = userEvent.setup();
    renderApp('/validation');

    await user.click(screen.getByRole('button', { name: 'Check only' }));

    expect(await screen.findByText('Invalid email format')).toBeInTheDocument();
    expect(screen.getByLabelText('Email')).toHaveAttribute('aria-invalid', 'true');
  });

  it('says so when the service goes offline during a check', async () => {
    stubFetch({ ...routes, 'POST /api/records/validate': 'network-error' });
    const user = userEvent.setup();
    renderApp('/validation');

    await user.click(screen.getByRole('button', { name: 'Check only' }));

    expect(await screen.findByText(/Your details have not been sent/)).toBeInTheDocument();
  });

  it('lists masked records and asks for the admin key to show more', async () => {
    const calls = stubFetch(routes);
    const user = userEvent.setup();
    renderApp('/validation');

    expect(await screen.findByText('a***@example.com')).toBeInTheDocument();
    expect(screen.getByText(/Personal details are masked/)).toBeInTheDocument();

    await user.type(screen.getByLabelText('Admin key'), 'my-key');
    await user.click(screen.getByRole('button', { name: 'Show full records' }));

    await waitFor(() => {
      expect(calls.some((call) => call.headers.get('X-Admin-Key') === 'my-key')).toBe(true);
    });
  });

  it('explains a rejected admin key', async () => {
    stubFetch({
      ...ALL_UP,
      'GET /api/records': () => Promise.resolve({ body: [MASKED_RECORD] }),
    });
    const user = userEvent.setup();
    renderApp('/validation');
    await screen.findByText('a***@example.com');

    stubFetch({
      ...ALL_UP,
      'GET /api/records': {
        status: 403,
        body: { timestamp: 'now', status: 403, error: 'Forbidden', message: 'Admin key is invalid or admin access is not configured' },
      },
    });
    await user.type(screen.getByLabelText('Admin key'), 'wrong');
    await user.click(screen.getByRole('button', { name: 'Show full records' }));

    expect(await screen.findByText(/Admin key is invalid/)).toBeInTheDocument();
  });

  it('shows an empty state when nothing is stored', async () => {
    stubFetch({ ...ALL_UP, 'GET /api/records': { body: [] } });
    renderApp('/validation');

    expect(await screen.findByText('No records are stored yet.')).toBeInTheDocument();
  });
});

describe('bus pass', () => {
  const routes = { ...ALL_UP, 'GET /api/buspass/routes': { body: ROUTES } };

  async function fillBooking(user: ReturnType<typeof userEvent.setup>) {
    await user.type(await screen.findByLabelText('Passenger name'), 'Pax One');
    await user.type(screen.getByLabelText('Email'), 'pax@example.com');
    await user.type(screen.getByLabelText('Phone'), '07000 100 001');
  }

  it('shows fares for every category', async () => {
    stubFetch(routes);
    renderApp('/bus-pass');

    const row = (await screen.findByRole('cell', { name: 'R-100' })).closest('tr') as HTMLElement;
    expect(within(row).getByText('£12.50')).toBeInTheDocument();
    expect(within(row).getByText('£10.00')).toBeInTheDocument();
    expect(within(row).getByText('£8.75')).toBeInTheDocument();
  });

  it('previews the fare for the chosen category', async () => {
    stubFetch(routes);
    const user = userEvent.setup();
    renderApp('/bus-pass');

    await user.selectOptions(await screen.findByLabelText('Passenger category'), 'STUDENT');

    expect(screen.getByText('Estimated fare').querySelector('strong')).toHaveTextContent('£10.00');
  });

  it('books a ticket and shows the confirmation', async () => {
    const calls = stubFetch({ ...routes, 'POST /api/buspass/tickets/book': { status: 201, body: TICKET } });
    const user = userEvent.setup();
    renderApp('/bus-pass');

    await fillBooking(user);
    await user.click(screen.getByRole('button', { name: 'Book ticket' }));

    expect(await screen.findByRole('heading', { name: 'Booking confirmed' })).toBeInTheDocument();
    expect(screen.getByText(TICKET.ticketNumber)).toBeInTheDocument();
    expect(screen.getByText('Verified')).toBeInTheDocument();
    expect(calls.find((call) => call.key === 'POST /api/buspass/tickets/book')?.body).toMatchObject({
      passengerName: 'Pax One',
      phone: '07000 100 001',
      routeCode: 'R-100',
    });
  });

  it('explains a ticket left pending by an outage', async () => {
    stubFetch({
      ...routes,
      'POST /api/buspass/tickets/book': { status: 201, body: { ...TICKET, validationStatus: 'PENDING' } },
    });
    const user = userEvent.setup();
    renderApp('/bus-pass');

    await fillBooking(user);
    await user.click(screen.getByRole('button', { name: 'Book ticket' }));

    expect(await screen.findByText('Pending')).toBeInTheDocument();
    expect(screen.getByText(/retried automatically/)).toBeInTheDocument();
  });

  it('explains a duplicate booking', async () => {
    stubFetch({
      ...routes,
      'POST /api/buspass/tickets/book': {
        status: 409,
        body: { timestamp: 'now', status: 409, error: 'Conflict', message: 'This passenger already has a booking on this route and date' },
      },
    });
    const user = userEvent.setup();
    renderApp('/bus-pass');

    await fillBooking(user);
    await user.click(screen.getByRole('button', { name: 'Book ticket' }));

    expect(await screen.findByText(/already has a booking/)).toBeInTheDocument();
  });

  it('finds a booking with the ticket number and email, then verifies it', async () => {
    const calls = stubFetch({
      ...routes,
      'POST /api/buspass/tickets/lookup': { body: TICKET },
      [`GET /api/buspass/tickets/${TICKET.ticketNumber}/verify`]: {
        body: { ticketNumber: TICKET.ticketNumber, valid: true, reason: 'VALID' },
      },
    });
    const user = userEvent.setup();
    renderApp('/bus-pass');

    await user.type(screen.getByLabelText('Ticket number'), TICKET.ticketNumber);
    await user.type(screen.getByLabelText('Booking email'), 'pax@example.com');
    await user.click(screen.getByRole('button', { name: 'Find booking' }));

    expect(await screen.findByRole('heading', { name: 'Your booking' })).toBeInTheDocument();
    expect(calls.find((call) => call.key === 'POST /api/buspass/tickets/lookup')?.body).toEqual({
      ticketNumber: TICKET.ticketNumber,
      email: 'pax@example.com',
    });

    await user.click(screen.getByRole('button', { name: 'Verify ticket' }));
    expect(await screen.findByText('This ticket is valid.')).toBeInTheDocument();
  });

  it('shows an offline notice and hides the booking form without routes', async () => {
    stubFetch({ ...ALL_UP, 'GET /health/buspass': 'offline', 'GET /api/buspass/routes': 'offline' });
    renderApp('/bus-pass');

    expect((await screen.findAllByText('Bus Pass is offline')).length).toBeGreaterThan(0);
    expect(screen.queryByRole('button', { name: 'Book ticket' })).not.toBeInTheDocument();
  });
});

describe('assistant', () => {
  it('sends a suggestion and labels a live answer', async () => {
    const calls = stubFetch({
      ...ALL_UP,
      'POST /api/chat': { body: { intent: 'ROUTES', response: 'Available routes: R-100', source: 'LIVE' } },
    });
    const user = userEvent.setup();
    renderApp('/assistant');

    await user.click(screen.getByRole('button', { name: 'show routes' }));

    expect(await screen.findByText('Available routes: R-100')).toBeInTheDocument();
    expect(screen.getByText('Live data')).toBeInTheDocument();
    expect(calls.find((call) => call.key === 'POST /api/chat')?.body).toEqual({ message: 'show routes' });
  });

  it('sends a typed message and clears the box', async () => {
    stubFetch({
      ...ALL_UP,
      'POST /api/chat': { body: { intent: 'VALIDATION_HELP', response: 'How data validation works', source: 'STATIC' } },
    });
    const user = userEvent.setup();
    renderApp('/assistant');

    const input = screen.getByLabelText('Your message');
    await user.type(input, 'how does validation work{Enter}');

    expect(await screen.findByText('How data validation works')).toBeInTheDocument();
    expect(input).toHaveValue('');
    expect(screen.queryByText('Live data')).not.toBeInTheDocument();
  });

  it('shows a clear message when the assistant is offline', async () => {
    stubFetch({ ...ALL_UP, 'POST /api/chat': 'offline' });
    const user = userEvent.setup();
    renderApp('/assistant');

    await user.click(screen.getByRole('button', { name: 'help' }));

    expect(await screen.findByText(/The assistant is offline/)).toBeInTheDocument();
  });

  it('does not call itself generative AI', () => {
    stubFetch(ALL_UP);
    renderApp('/assistant');

    expect(screen.getByText(/not a generative AI model/)).toBeInTheDocument();
  });
});

describe('theme', () => {
  it('switches between light and dark', async () => {
    stubFetch(ALL_UP);
    const user = userEvent.setup();
    renderApp('/architecture');

    const before = document.documentElement.dataset.theme;
    await user.click(screen.getByRole('button', { name: /Use (dark|light) theme/ }));

    expect(document.documentElement.dataset.theme).not.toBe(before);
  });
});
