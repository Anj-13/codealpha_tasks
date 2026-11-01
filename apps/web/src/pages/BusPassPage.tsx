import { useState, type FormEvent } from 'react';
import { bookTicket, listRoutes, lookupTicket, verifyTicket } from '../api/buspass';
import { ApiError, ServiceOfflineError } from '../api/client';
import type { BookingInput, PassengerCategory, Route, Ticket, TicketVerification } from '../api/types';
import { Badge, Field, Loading, Notice, OfflineNotice, PageHeader, SelectField, ServiceBanner } from '../components/ui';
import { FARE_MULTIPLIERS, VALIDATION_LABELS, VERIFICATION_LABELS, formatDate, formatMoney, ticketRoute } from '../format';
import { useLoad } from '../hooks/useLoad';

const CATEGORIES: { value: PassengerCategory; label: string }[] = [
  { value: 'ADULT', label: 'Adult (full fare)' },
  { value: 'STUDENT', label: 'Student (80%)' },
  { value: 'SENIOR', label: 'Senior (70%)' },
];

function today(): string {
  return new Date().toISOString().slice(0, 10);
}

interface Failure {
  message: string;
  fieldErrors: Record<string, string>;
}

function toFailure(error: unknown): Failure {
  if (error instanceof ApiError) {
    return { message: error.message, fieldErrors: error.fieldErrors };
  }
  if (error instanceof ServiceOfflineError) {
    return { message: 'Bus Pass is offline. Nothing was booked or looked up.', fieldErrors: {} };
  }
  return { message: 'Something went wrong. Please try again.', fieldErrors: {} };
}

function RouteTable({ routes }: { routes: Route[] }) {
  return (
    <div className="table-wrap" tabIndex={0} role="region" aria-label="Routes and fares">
      <table>
        <thead>
          <tr><th scope="col">Route</th><th scope="col">Journey</th><th scope="col">Adult</th><th scope="col">Student</th><th scope="col">Senior</th></tr>
        </thead>
        <tbody>
          {routes.map((route) => (
            <tr key={route.code}>
              <td>{route.code}</td>
              <td>{route.source} to {route.destination}</td>
              <td>{formatMoney(route.baseFare * FARE_MULTIPLIERS.ADULT)}</td>
              <td>{formatMoney(route.baseFare * FARE_MULTIPLIERS.STUDENT)}</td>
              <td>{formatMoney(route.baseFare * FARE_MULTIPLIERS.SENIOR)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function TicketCard({ ticket, heading }: { ticket: Ticket; heading: string }) {
  const [verification, setVerification] = useState<TicketVerification | null>(null);
  const [failure, setFailure] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const validation = VALIDATION_LABELS[ticket.validationStatus];

  async function verify() {
    setBusy(true);
    setFailure(null);
    setVerification(null);
    try {
      setVerification(await verifyTicket(ticket.ticketNumber));
    } catch (error) {
      setFailure(toFailure(error).message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <article className="card ticket">
      <div className="card-heading">
        <h3>{heading}</h3>
        <Badge tone={ticket.status === 'ACTIVE' ? 'success' : 'neutral'}>{ticket.status === 'ACTIVE' ? 'Active' : 'Cancelled'}</Badge>
      </div>
      <dl className="detail-list">
        <div><dt>Passenger</dt><dd>{ticket.passengerName}</dd></div>
        <div><dt>Journey</dt><dd>{ticketRoute(ticket)}</dd></div>
        <div><dt>Date</dt><dd>{formatDate(ticket.travelDate)}</dd></div>
        <div><dt>Seat</dt><dd>{ticket.seatNumber}</dd></div>
        <div><dt>Fare paid</dt><dd>{formatMoney(ticket.farePaid, ticket.currency)}</dd></div>
        <div><dt>Ticket number</dt><dd className="ticket-number">{ticket.ticketNumber}</dd></div>
      </dl>
      <div className="validation-line">
        <Badge tone={validation.tone}>{validation.label}</Badge>
        <p>{validation.meaning}</p>
      </div>
      <button type="button" className="button button-secondary" disabled={busy} onClick={() => void verify()}>Verify ticket</button>
      {busy && <Loading label="Verifying ticket" />}
      {verification && (
        <Notice tone={verification.valid ? 'success' : 'danger'}>{VERIFICATION_LABELS[verification.reason]}</Notice>
      )}
      {failure && <Notice tone="danger">{failure}</Notice>}
    </article>
  );
}

function BookingForm({ routes }: { routes: Route[] }) {
  const [form, setForm] = useState<BookingInput>({
    passengerName: '',
    email: '',
    phone: '',
    routeCode: routes[0]?.code ?? '',
    travelDate: today(),
    passengerCategory: 'ADULT',
  });
  const [ticket, setTicket] = useState<Ticket | null>(null);
  const [failure, setFailure] = useState<Failure | null>(null);
  const [busy, setBusy] = useState(false);

  const update = (field: keyof BookingInput) => (event: { target: { value: string } }) =>
    setForm({ ...form, [field]: event.target.value });

  const selectedRoute = routes.find((route) => route.code === form.routeCode);
  const fare = selectedRoute ? selectedRoute.baseFare * FARE_MULTIPLIERS[form.passengerCategory] : null;
  const fieldErrors = failure?.fieldErrors ?? {};

  async function submit(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setTicket(null);
    setFailure(null);
    try {
      setTicket(await bookTicket(form));
    } catch (error) {
      setFailure(toFailure(error));
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="two-column" aria-label="Book a ticket">
      <form className="card form" onSubmit={(event) => void submit(event)} noValidate>
        <h2 className="section-title">Book a ticket</h2>
        <Field label="Passenger name" name="passengerName" autoComplete="name" required value={form.passengerName} onChange={update('passengerName')} error={fieldErrors.passengerName} />
        <Field label="Email" name="bookingEmail" type="email" autoComplete="email" required value={form.email} onChange={update('email')} error={fieldErrors.email} />
        <Field label="Phone" name="phone" type="tel" autoComplete="tel" required value={form.phone} onChange={update('phone')} error={fieldErrors.phone} hint="Used to match you in Data Validation." />
        <SelectField label="Route" name="routeCode" value={form.routeCode} onChange={update('routeCode')} error={fieldErrors.routeCode}>
          {routes.map((route) => (
            <option key={route.code} value={route.code}>{route.code}: {route.source} to {route.destination}</option>
          ))}
        </SelectField>
        <Field label="Travel date" name="travelDate" type="date" min={today()} required value={form.travelDate} onChange={update('travelDate')} error={fieldErrors.travelDate} />
        <SelectField label="Passenger category" name="passengerCategory" value={form.passengerCategory} onChange={update('passengerCategory')} error={fieldErrors.passengerCategory}>
          {CATEGORIES.map((category) => <option key={category.value} value={category.value}>{category.label}</option>)}
        </SelectField>
        {fare !== null && <p className="fare-preview">Estimated fare <strong>{formatMoney(fare)}</strong></p>}
        <div className="form-actions">
          <button type="submit" className="button button-primary" disabled={busy}>Book ticket</button>
        </div>
      </form>

      <div aria-live="polite">
        {busy && <Loading label="Booking your ticket" />}
        {ticket && <TicketCard ticket={ticket} heading="Booking confirmed" />}
        {failure && <Notice tone="danger" title="The ticket could not be booked">{failure.message}</Notice>}
        {!busy && !ticket && !failure && <p className="empty">Your confirmed ticket appears here.</p>}
      </div>
    </section>
  );
}

function LookupForm() {
  const [ticketNumber, setTicketNumber] = useState('');
  const [email, setEmail] = useState('');
  const [ticket, setTicket] = useState<Ticket | null>(null);
  const [failure, setFailure] = useState<Failure | null>(null);
  const [busy, setBusy] = useState(false);
  const fieldErrors = failure?.fieldErrors ?? {};

  async function submit(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setTicket(null);
    setFailure(null);
    try {
      setTicket(await lookupTicket(ticketNumber, email));
    } catch (error) {
      setFailure(toFailure(error));
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="two-column" aria-label="Find a booking">
      <form className="card form" onSubmit={(event) => void submit(event)} noValidate>
        <h2 className="section-title">Find a booking</h2>
        <p className="muted">Both details are needed, so only the passenger can see a booking.</p>
        <Field label="Ticket number" name="ticketNumber" autoComplete="off" required value={ticketNumber} onChange={(event) => setTicketNumber(event.target.value)} error={fieldErrors.ticketNumber} />
        <Field label="Booking email" name="lookupEmail" type="email" autoComplete="email" required value={email} onChange={(event) => setEmail(event.target.value)} error={fieldErrors.email} />
        <div className="form-actions">
          <button type="submit" className="button button-primary" disabled={busy}>Find booking</button>
        </div>
      </form>

      <div aria-live="polite">
        {busy && <Loading label="Finding your booking" />}
        {ticket && <TicketCard ticket={ticket} heading="Your booking" />}
        {failure && <Notice tone="danger" title="No booking found">{failure.message}</Notice>}
        {!busy && !ticket && !failure && <p className="empty">Booking details appear here.</p>}
      </div>
    </section>
  );
}

export function BusPassPage() {
  const { state, reload } = useLoad(listRoutes);

  return (
    <>
      <PageHeader title="Bus Pass">
        Book a signed ticket with an assigned seat. Each passenger can hold one booking per route and date.
      </PageHeader>
      <ServiceBanner service="buspass" />

      <section aria-labelledby="routes-heading">
        <h2 id="routes-heading" className="section-title">Routes and fares</h2>
        {state.phase === 'loading' && <Loading label="Loading routes" />}
        {state.phase === 'offline' && <OfflineNotice service="buspass" onRetry={reload} />}
        {state.phase === 'error' && <Notice tone="danger" title="Routes could not be loaded">{state.message}</Notice>}
        {state.phase === 'ready' && state.data.length === 0 && <p className="empty">No routes are available yet.</p>}
        {state.phase === 'ready' && state.data.length > 0 && <RouteTable routes={state.data} />}
      </section>

      {state.phase === 'ready' && state.data.length > 0 && <BookingForm routes={state.data} />}
      <LookupForm />
    </>
  );
}
