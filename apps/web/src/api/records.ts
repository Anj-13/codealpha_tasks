import { getJson, postJson, postRaw, toError } from './client';
import type { RecordInput, RecordResult, RecordSummary } from './types';

const BASE = '/api/records';
const OUTCOME_STATUSES = [201, 409, 422];

function isOutcome(body: unknown): body is RecordResult {
  return typeof body === 'object' && body !== null && 'validationReason' in body;
}

export function validateRecord(input: RecordInput): Promise<RecordResult> {
  return postJson<RecordResult>(`${BASE}/validate`, input);
}

/** A duplicate or partial match is a normal outcome here, not a failed request. */
export async function saveRecord(input: RecordInput): Promise<RecordResult> {
  const response = await postRaw(BASE, input);
  if (OUTCOME_STATUSES.includes(response.status) && isOutcome(response.body)) {
    return response.body;
  }
  throw toError(response);
}

export function listRecords(adminKey?: string): Promise<RecordResult[]> {
  return getJson<RecordResult[]>(BASE, adminKey ? { 'X-Admin-Key': adminKey } : {});
}

export function getRecordSummary(): Promise<RecordSummary> {
  return getJson<RecordSummary>(`${BASE}/summary`);
}
