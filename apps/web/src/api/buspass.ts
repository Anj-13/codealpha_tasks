import { getJson, postJson } from './client';
import type { BookingInput, Route, ScaleMetrics, Ticket, TicketVerification } from './types';

const BASE = '/api/buspass';

export function listRoutes(): Promise<Route[]> {
  return getJson<Route[]>(`${BASE}/routes`);
}

export function bookTicket(input: BookingInput): Promise<Ticket> {
  return postJson<Ticket>(`${BASE}/tickets/book`, input);
}

export function lookupTicket(ticketNumber: string, email: string): Promise<Ticket> {
  return postJson<Ticket>(`${BASE}/tickets/lookup`, { ticketNumber, email });
}

export function verifyTicket(ticketNumber: string): Promise<TicketVerification> {
  return getJson<TicketVerification>(`${BASE}/tickets/${encodeURIComponent(ticketNumber)}/verify`);
}

export function getScaleMetrics(): Promise<ScaleMetrics> {
  return getJson<ScaleMetrics>(`${BASE}/metrics/scale`);
}
