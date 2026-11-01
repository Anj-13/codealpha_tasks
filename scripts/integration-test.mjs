#!/usr/bin/env node
// Runs the real service jars on spare ports with throwaway databases, then checks how they
// work together, including outages and restarts. Real databases are never touched.
import { createWriteStream, mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { SERVICES, buildJars, startService, stopProcess, waitForHealth } from './lib/services.mjs';

const PORTS = { records: 18081, buspass: 18082, chatbot: 18090 };
const ADMIN_KEY = 'integration-admin-key';
const RECORDS = `http://localhost:${PORTS.records}/api/records`;
const BUSPASS = `http://localhost:${PORTS.buspass}/api/buspass`;
const CHAT = `http://localhost:${PORTS.chatbot}/api/chat`;

const workDir = mkdtempSync(join(tmpdir(), 'cloud-services-it-'));
const databaseUrl = workDir.replaceAll('\\', '/');
const log = createWriteStream(join(workDir, 'services.log'));
const running = {};
const failures = [];
let passed = 0;

const ENV = {
  records: { DB_URL: `jdbc:sqlite:${databaseUrl}/records.db`, ADMIN_API_KEY: ADMIN_KEY },
  buspass: {
    DB_URL: `jdbc:h2:file:${databaseUrl}/buspass`,
    REMOVAL_BASE_URL: RECORDS,
    REMOVAL_RETRY_DELAY_MS: '1000',
    TICKET_SIGNING_SECRET: 'integration-signing-secret',
  },
  chatbot: { BUSPASS_BASE_URL: BUSPASS, REMOVAL_BASE_URL: RECORDS },
};

function check(label, condition, detail) {
  if (condition) {
    passed += 1;
    console.log(`  PASS  ${label}`);
  } else {
    failures.push(label);
    console.log(`  FAIL  ${label}${detail === undefined ? '' : ` -> ${JSON.stringify(detail)}`}`);
  }
}

function section(title) {
  console.log(`\n${title}`);
}

async function request(url, options = {}) {
  const response = await fetch(url, options);
  const text = await response.text();
  let body = text;
  try {
    body = JSON.parse(text);
  } catch {
    // Left as text.
  }
  return { status: response.status, body };
}

const post = (url, payload, headers = {}) => request(url, {
  method: 'POST',
  headers: { 'Content-Type': 'application/json', ...headers },
  body: typeof payload === 'string' ? payload : JSON.stringify(payload),
});
const chat = async (message) => (await post(CHAT, { message })).body;

async function start(key) {
  const service = SERVICES[key];
  running[key] = startService(service, PORTS[key], ENV[key], (data) => log.write(data));
  await waitForHealth(running[key], PORTS[key]);
}

async function stop(key) {
  await stopProcess(running[key]);
  delete running[key];
}

async function waitFor(condition, timeoutMs = 15000) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    if (await condition()) {
      return true;
    }
    await new Promise((resolve) => setTimeout(resolve, 250));
  }
  return false;
}

function isErrorShape(body) {
  return typeof body?.timestamp === 'string' && typeof body?.status === 'number'
    && typeof body?.error === 'string' && typeof body?.message === 'string';
}

const nextMonth = new Date(Date.now() + 30 * 86400000).toISOString().slice(0, 10);
const booking = (overrides = {}) => ({
  passengerName: 'Pax One',
  email: 'pax.one@example.com',
  phone: '07000 100 001',
  routeCode: 'R-100',
  travelDate: nextMonth,
  passengerCategory: 'STUDENT',
  ...overrides,
});

async function testRecords() {
  section('Task 1: records');
  const record = { fullName: 'It Person', email: 'it.person@example.com', phone: '07000 111 222', address: 'Leeds' };

  const before = (await request(`${RECORDS}/summary`)).body.totalRecords;
  const dryRun = await post(`${RECORDS}/validate`, record);
  check('validate classifies a new record as UNIQUE', dryRun.status === 200 && dryRun.body.status === 'UNIQUE', dryRun);
  check('validate stores nothing', (await request(`${RECORDS}/summary`)).body.totalRecords === before);

  check('new record returns 201', (await post(RECORDS, record)).status === 201);
  check('exact duplicate returns 409', (await post(RECORDS, record)).status === 409);
  const reformatted = { ...record, fullName: ' IT   person ', email: 'IT.PERSON@example.com', phone: '07000111222' };
  check('formatting does not hide a duplicate', (await post(RECORDS, reformatted)).status === 409);
  check('partial match returns 422', (await post(RECORDS, { ...record, phone: '07000 999 888' })).status === 422);

  const invalid = await post(RECORDS, { fullName: '', email: 'x', phone: 'abcdefgh' });
  check('invalid record returns 400 with fieldErrors', invalid.status === 400 && invalid.body.fieldErrors?.fullName, invalid);
  check('field errors use fullName, not fullname', invalid.body.fieldErrors?.fullname === undefined, invalid.body);
  check('errors share one shape', isErrorShape(invalid.body), invalid.body);
  const malformed = await post(RECORDS, '{bad');
  check('malformed JSON returns 400 in the same shape', malformed.status === 400 && isErrorShape(malformed.body), malformed);
  const missing = await request(`${RECORDS}/999999`);
  check('missing record returns 404 in the same shape', missing.status === 404 && isErrorShape(missing.body), missing);
  const badId = await request(`${RECORDS}/not-a-number`);
  check('non-numeric id returns 400 in the same shape', badId.status === 400 && isErrorShape(badId.body), badId);

  const publicList = (await request(RECORDS)).body;
  const publicText = JSON.stringify(publicList);
  check('public records are masked', publicList.every((item) => item.masked && item.address === null));
  check('public records hide emails and phones', !publicText.includes('it.person@example.com') && !publicText.includes('07000111222'), publicText);
  const admin = await request(RECORDS, { headers: { 'X-Admin-Key': ADMIN_KEY } });
  check('admin key returns full records', admin.status === 200 && JSON.stringify(admin.body).includes('it.person@example.com'));
  const wrongKey = await request(RECORDS, { headers: { 'X-Admin-Key': 'wrong' } });
  check('wrong admin key returns 403', wrongKey.status === 403 && isErrorShape(wrongKey.body), wrongKey);

  const race = { fullName: 'Race', email: 'race@example.com', phone: '07111 222 333' };
  const exact = await Promise.all(Array.from({ length: 20 }, () => post(RECORDS, race)));
  const exactStatuses = exact.map((result) => result.status);
  check('20 identical requests store exactly one record',
    exactStatuses.filter((status) => status === 201).length === 1 && exactStatuses.filter((status) => status === 409).length === 19,
    exactStatuses);

  const nearRace = await Promise.all(Array.from({ length: 10 }, (_, index) => post(RECORDS, {
    fullName: `Race Variant ${index}`, email: 'near@example.com', phone: '07111 444 555',
  })));
  const nearStatuses = nearRace.map((result) => result.status);
  check('same email and phone under different names store exactly one record',
    nearStatuses.filter((status) => status === 201).length === 1 && !nearStatuses.includes(500), nearStatuses);
}

async function testBookings() {
  section('Task 3: bookings and Task 1 integration');
  const first = await post(`${BUSPASS}/tickets/book`, booking());
  check('booking returns 201', first.status === 201, first);
  check('student fare is 80% of the route fare', first.body.farePaid === 10, first.body);
  check('new passenger is verified by Task 1', first.body.validationStatus === 'VERIFIED', first.body);

  const second = await post(`${BUSPASS}/tickets/book`, booking({
    passengerName: 'Pax Two', email: 'pax.two@example.com', phone: '07000 100 002', passengerCategory: 'ADULT',
  }));
  check('second passenger is verified too', second.body.validationStatus === 'VERIFIED', second.body);
  const stored = (await request(RECORDS, { headers: { 'X-Admin-Key': ADMIN_KEY } })).body;
  check('both passengers reach Task 1',
    stored.some((item) => item.email === 'pax.one@example.com') && stored.some((item) => item.email === 'pax.two@example.com'));
  check('no placeholder phone numbers are stored', !stored.some((item) => item.phone === '0000000000'));

  const rebook = await post(`${BUSPASS}/tickets/book`, booking());
  check('identical rebooking returns 409', rebook.status === 409 && isErrorShape(rebook.body), rebook);
  const variant = await post(`${BUSPASS}/tickets/book`, booking({ passengerName: ' PAX   one ', email: 'PAX.ONE@example.com' }));
  check('rebooking with different formatting returns 409', variant.status === 409, variant);

  const returning = await post(`${BUSPASS}/tickets/book`, booking({ routeCode: 'R-110' }));
  check('returning passenger can book another route', returning.status === 201 && returning.body.validationStatus === 'VERIFIED', returning);
  const flagged = await post(`${BUSPASS}/tickets/book`, booking({ routeCode: 'R-120', phone: '07000 100 999' }));
  check('partial match still issues a ticket, marked REVIEW', flagged.status === 201 && flagged.body.validationStatus === 'REVIEW', flagged);

  const lookup = await post(`${BUSPASS}/tickets/lookup`, { ticketNumber: first.body.ticketNumber, email: 'PAX.ONE@example.com' });
  check('ticket lookup works with the booking email', lookup.status === 200 && lookup.body.seatNumber === first.body.seatNumber, lookup);
  const wrongEmail = await post(`${BUSPASS}/tickets/lookup`, { ticketNumber: first.body.ticketNumber, email: 'someone@example.com' });
  const wrongNumber = await post(`${BUSPASS}/tickets/lookup`, { ticketNumber: 'nope', email: 'pax.one@example.com' });
  check('wrong email returns 404', wrongEmail.status === 404, wrongEmail);
  check('wrong email and wrong number look identical', wrongEmail.body.message === wrongNumber.body.message, [wrongEmail.body, wrongNumber.body]);
  check('there is no open ticket read endpoint', (await request(`${BUSPASS}/tickets/${first.body.ticketNumber}`)).status === 404);

  const verify = await request(`${BUSPASS}/tickets/${first.body.ticketNumber}/verify`);
  check('ticket verifies as valid', verify.status === 200 && verify.body.valid === true, verify);
  check('verify exposes no personal data', !JSON.stringify(verify.body).includes('pax.one'), verify.body);
  const goodToken = await request(`${BUSPASS}/tickets/${first.body.ticketNumber}/verify?token=${first.body.qrToken}`);
  check('matching QR token verifies', goodToken.body.valid === true, goodToken);
  const badToken = await request(`${BUSPASS}/tickets/${first.body.ticketNumber}/verify?token=forged`);
  check('forged QR token fails', badToken.body.valid === false && badToken.body.reason === 'TOKEN_MISMATCH', badToken);

  const noPhone = await post(`${BUSPASS}/tickets/book`, { ...booking({ email: 'x@example.com' }), phone: undefined });
  check('missing phone returns 400', noPhone.status === 400 && noPhone.body.fieldErrors?.phone, noPhone);
  const pastDate = await post(`${BUSPASS}/tickets/book`, booking({ travelDate: '2020-01-01' }));
  check('past travel date returns 400', pastDate.status === 400 && pastDate.body.fieldErrors?.travelDate, pastDate);
  const badRoute = await post(`${BUSPASS}/tickets/book`, booking({ routeCode: 'LON-MAN' }));
  check('unknown route returns 404', badRoute.status === 404 && isErrorShape(badRoute.body), badRoute);
  const badEnum = await post(`${BUSPASS}/tickets/book`, booking({ passengerCategory: 'PILOT' }));
  check('unknown category returns 400', badEnum.status === 400 && isErrorShape(badEnum.body), badEnum);

  const parallel = await Promise.all(Array.from({ length: 20 }, (_, index) => post(`${BUSPASS}/tickets/book`, booking({
    routeCode: 'R-130',
    passengerName: `Parallel ${index}`,
    email: `parallel.${index}@example.com`,
    phone: `0711100${String(1000 + index)}`,
  }))));
  const seats = parallel.map((result) => result.body.seatNumber);
  check('20 parallel bookings all succeed', parallel.every((result) => result.status === 201), parallel.map((result) => result.status));
  check('20 parallel bookings get 20 different seats', new Set(seats).size === 20, seats);

  const sameBooking = booking({ routeCode: 'R-130', email: 'same@example.com', phone: '07111000999', passengerName: 'Same' });
  const duplicates = await Promise.all(Array.from({ length: 10 }, () => post(`${BUSPASS}/tickets/book`, sameBooking)));
  const statuses = duplicates.map((result) => result.status);
  check('10 identical parallel bookings create one ticket',
    statuses.filter((status) => status === 201).length === 1 && statuses.filter((status) => status === 409).length === 9, statuses);

  return first.body;
}

async function testChatbot(ticket) {
  section('Task 4: chatbot');
  const routes = await chat('show routes');
  check('routes come from Task 3', routes.intent === 'ROUTES' && routes.source === 'LIVE' && routes.response.includes('R-100'), routes);
  const fares = await chat('which fare applies');
  check('"which" no longer reads as a greeting', fares.intent === 'PRICING' && fares.response.includes('12.5'), fares);
  const records = await chat('show redundancy records');
  check('records come from Task 1', records.intent === 'REDUNDANCY_STATUS' && records.source === 'LIVE', records);
  check('chatbot repeats no personal data', !records.response.includes('ava@example.com') && records.response.includes('a***@example.com'), records);
  const explain = await chat('how does validation work');
  check('validation is explained from static answers', explain.intent === 'VALIDATION_HELP' && explain.source === 'STATIC', explain);
  const error = await chat('why was my record flagged as a false positive');
  check('duplicate errors are explained', error.intent === 'DUPLICATE_HELP', error);
  const noDetails = await chat('check my booking');
  check('booking check asks for the ticket number and email', noDetails.intent === 'BOOKING_LOOKUP' && noDetails.response.includes('email'), noDetails);
  const found = await chat(`check booking ${ticket.ticketNumber} ${ticket.email}`);
  check('booking status comes from Task 3', found.source === 'LIVE' && found.response.includes('ACTIVE') && found.response.includes('Manchester'), found);
  const refused = await chat(`check booking ${ticket.ticketNumber} stranger@example.com`);
  check('booking status is refused for the wrong email', refused.response.startsWith('No booking matches') && !refused.response.includes('Manchester'), refused);
  const unique = await chat('validate Brand New brand.new@example.com 07222333444');
  check('chatbot can validate a new record', unique.intent === 'VALIDATION_CHECK' && unique.response.startsWith('UNIQUE'), unique);
  const redundant = await chat('validate Ava Thompson ava@example.com 07123456789');
  check('chatbot reports a duplicate', redundant.response.startsWith('REDUNDANT'), redundant);
  check('unknown text gets a helpful fallback', (await chat('purple monkey dishwasher')).intent === 'FALLBACK');
  const empty = await post(CHAT, { message: '   ' });
  check('blank message returns 400 in the shared error shape', empty.status === 400 && isErrorShape(empty.body), empty);
  const tooLong = await post(CHAT, { message: 'x'.repeat(501) });
  check('overlong message returns 400', tooLong.status === 400, tooLong.status);
  const wrongMethod = await request(CHAT);
  check('GET on the chat endpoint returns 405', wrongMethod.status === 405 && isErrorShape(wrongMethod.body), wrongMethod);
  const page = await request(`http://localhost:${PORTS.chatbot}/`);
  check('chatbot still serves its own page', page.status === 200 && String(page.body).includes('Cloud Project Chatbot'));
}

async function testTask1Outage() {
  section('Outage: Task 1 offline');
  await stop('records');
  const pending = await post(`${BUSPASS}/tickets/book`, booking({
    routeCode: 'R-110', passengerName: 'Pax Three', email: 'pax.three@example.com', phone: '07000 100 003',
  }));
  check('booking still succeeds', pending.status === 201, pending);
  check('ticket is visibly PENDING, not silently skipped', pending.body.validationStatus === 'PENDING', pending.body);
  const down = await chat('show redundancy records');
  check('chatbot reports Task 1 as unavailable', down.response.includes('could not read'), down);
  const validate = await chat('validate Some One some.one@example.com 07222333555');
  check('chatbot validation reports the outage', validate.response.includes('could not reach'), validate);
  const lookup = await chat(`check booking ${pending.body.ticketNumber} ${pending.body.email}`);
  check('chatbot explains a pending ticket', lookup.response.includes('pending'), lookup);

  section('Recovery: Task 1 back online');
  await start('records');
  const verified = await waitFor(async () => {
    const ticket = await post(`${BUSPASS}/tickets/lookup`, { ticketNumber: pending.body.ticketNumber, email: pending.body.email });
    return ticket.body.validationStatus === 'VERIFIED';
  });
  check('pending ticket becomes VERIFIED by itself', verified);
  const stored = (await request(RECORDS, { headers: { 'X-Admin-Key': ADMIN_KEY } })).body;
  check('retried passenger reaches Task 1', stored.some((item) => item.email === 'pax.three@example.com'));
}

async function testRestart(ticket) {
  section('Restart: Task 3');
  await stop('buspass');
  const routes = await chat('show routes');
  check('chatbot reports Task 3 as unavailable', routes.response.includes('could not reach'), routes);
  const fares = await chat('what are the fares');
  check('fare rules still answer while Task 3 is down', fares.response.includes('80%') && fares.response.includes('could not reach'), fares);
  const lookup = await chat(`check booking ${ticket.ticketNumber} ${ticket.email}`);
  check('booking status reports the outage', lookup.response.includes('could not reach'), lookup);
  check('static answers still work', (await chat('how does validation work')).intent === 'VALIDATION_HELP');

  await start('buspass');
  const next = await post(`${BUSPASS}/tickets/book`, booking({
    passengerName: 'Pax Four', email: 'pax.four@example.com', phone: '07000 100 004', passengerCategory: 'ADULT',
  }));
  check('seat numbers continue after a restart', next.status === 201 && next.body.seatNumber > ticket.seatNumber + 1, next.body);
  const verify = await request(`${BUSPASS}/tickets/${ticket.ticketNumber}/verify`);
  check('old tickets still verify after a restart', verify.body.valid === true, verify);
}

async function main() {
  buildJars(Object.values(SERVICES), { force: process.argv.includes('--rebuild') });
  console.log('Starting services on spare ports...');
  // One at a time, because three JVMs booting together can starve a small machine.
  for (const key of Object.keys(SERVICES)) {
    await start(key);
  }

  await testRecords();
  const ticket = await testBookings();
  await testChatbot(ticket);
  await testTask1Outage();
  await testRestart(ticket);
}

let crashed = false;
try {
  await main();
} catch (error) {
  crashed = true;
  console.error(error);
} finally {
  await Promise.all(Object.values(running).map(stopProcess));
  log.end();
}

console.log(`\n${passed} passed, ${failures.length} failed`);
if (failures.length > 0 || crashed) {
  console.log(`Service logs kept in ${workDir}`);
  process.exitCode = 1;
} else {
  await new Promise((resolve) => setTimeout(resolve, 500));
  rmSync(workDir, { recursive: true, force: true, maxRetries: 5, retryDelay: 200 });
}
