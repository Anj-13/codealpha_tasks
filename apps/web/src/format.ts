import type { RecordStatus, Ticket, TicketVerification, ValidationStatus } from './api/types';
import type { Tone } from './components/ui';

export function formatMoney(amount: number, currency = 'GBP'): string {
  return new Intl.NumberFormat('en-GB', { style: 'currency', currency }).format(amount);
}

export function formatDate(value: string): string {
  return new Intl.DateTimeFormat('en-GB', { dateStyle: 'medium' }).format(new Date(value));
}

export function formatDateTime(value: string): string {
  return new Intl.DateTimeFormat('en-GB', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value));
}

export const FARE_MULTIPLIERS = { ADULT: 1, STUDENT: 0.8, SENIOR: 0.7 } as const;

export const RECORD_OUTCOMES: Record<RecordStatus, { label: string; tone: Tone; meaning: string }> = {
  UNIQUE: { label: 'Unique', tone: 'success', meaning: 'No matching record was found.' },
  REDUNDANT: { label: 'Duplicate', tone: 'danger', meaning: 'This record already exists, so it will not be stored again.' },
  FALSE_POSITIVE: {
    label: 'Needs review',
    tone: 'warning',
    meaning: 'The email or phone matches a different record. Check the details, or have it reviewed manually.',
  },
};

export const VALIDATION_LABELS: Record<ValidationStatus, { label: string; tone: Tone; meaning: string }> = {
  VERIFIED: { label: 'Verified', tone: 'success', meaning: 'The passenger is on record with Data Validation.' },
  PENDING: {
    label: 'Pending',
    tone: 'warning',
    meaning: 'Data Validation could not be reached. Your ticket is valid, and the check is retried automatically.',
  },
  REVIEW: {
    label: 'In review',
    tone: 'warning',
    meaning: 'These details partly match another record. Your ticket is valid, and the record needs a manual check.',
  },
  NOT_CHECKED: { label: 'Not checked', tone: 'neutral', meaning: 'This ticket was not checked by Data Validation.' },
};

export const VERIFICATION_LABELS: Record<TicketVerification['reason'], string> = {
  VALID: 'This ticket is valid.',
  TAMPERED: 'This ticket has been altered since it was issued.',
  TOKEN_MISMATCH: 'The QR token does not match this ticket.',
  NOT_ACTIVE: 'This ticket is no longer active.',
  LEGACY_UNSIGNED: 'This ticket was issued before signing was added, so it cannot be verified.',
};

export function ticketRoute(ticket: Ticket): string {
  return ticket.routeName.replace('->', 'to');
}
