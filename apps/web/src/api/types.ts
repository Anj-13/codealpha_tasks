export type RecordStatus = 'UNIQUE' | 'REDUNDANT' | 'FALSE_POSITIVE';

export interface RecordInput {
  fullName: string;
  email: string;
  phone: string;
  address: string;
}

export interface RecordResult {
  id: number | null;
  fullName: string;
  email: string;
  phone: string;
  address: string | null;
  status: RecordStatus;
  validationReason: string;
  stored: boolean;
  masked: boolean;
  createdAt: string;
}

export interface RecordSummary {
  totalRecords: number;
  lastStoredAt: string | null;
}

export type PassengerCategory = 'ADULT' | 'STUDENT' | 'SENIOR';
export type ValidationStatus = 'PENDING' | 'VERIFIED' | 'REVIEW' | 'NOT_CHECKED';

export interface Route {
  code: string;
  source: string;
  destination: string;
  baseFare: number;
}

export interface BookingInput {
  passengerName: string;
  email: string;
  phone: string;
  routeCode: string;
  travelDate: string;
  passengerCategory: PassengerCategory;
}

export interface Ticket {
  ticketNumber: string;
  passengerName: string;
  email: string;
  phone: string | null;
  routeCode: string;
  routeName: string;
  travelDate: string;
  seatNumber: number;
  farePaid: number;
  currency: string;
  status: 'ACTIVE' | 'CANCELLED';
  validationStatus: ValidationStatus;
  createdAt: string;
}

export interface TicketVerification {
  ticketNumber: string;
  valid: boolean;
  reason: 'VALID' | 'TAMPERED' | 'TOKEN_MISMATCH' | 'NOT_ACTIVE' | 'LEGACY_UNSIGNED';
}

export interface ScaleMetrics {
  totalBookings: number;
  recommendedServers: number;
  maxServers: number;
}

export interface ChatReply {
  intent: string;
  response: string;
  source: 'STATIC' | 'LIVE';
}
