import { useState, type FormEvent } from 'react';
import { ApiError, ServiceOfflineError } from '../api/client';
import { listRecords, saveRecord, validateRecord } from '../api/records';
import type { RecordInput, RecordResult } from '../api/types';
import { Badge, Field, Loading, Notice, OfflineNotice, PageHeader, ServiceBanner } from '../components/ui';
import { RECORD_OUTCOMES, formatDateTime } from '../format';
import { useLoad } from '../hooks/useLoad';

const EMPTY_FORM: RecordInput = { fullName: '', email: '', phone: '', address: '' };

interface Failure {
  message: string;
  fieldErrors: Record<string, string>;
}

function toFailure(error: unknown): Failure {
  if (error instanceof ApiError) {
    return { message: error.message, fieldErrors: error.fieldErrors };
  }
  if (error instanceof ServiceOfflineError) {
    return { message: 'Data Validation is offline. Your details have not been sent.', fieldErrors: {} };
  }
  return { message: 'Something went wrong. Please try again.', fieldErrors: {} };
}

function Outcome({ result }: { result: RecordResult }) {
  const outcome = RECORD_OUTCOMES[result.status];
  return (
    <div className="card outcome" role="status">
      <div className="card-heading">
        <h3>Result</h3>
        <Badge tone={outcome.tone}>{outcome.label}</Badge>
      </div>
      <p>{outcome.meaning}</p>
      <p className="muted">{result.validationReason}. {result.stored ? 'The record was stored.' : 'Nothing was stored.'}</p>
    </div>
  );
}

function RecordsSection({ version }: { version: number }) {
  const [adminKey, setAdminKey] = useState('');
  const [appliedKey, setAppliedKey] = useState('');
  const { state, reload } = useLoad(() => listRecords(appliedKey), `${appliedKey}:${version}`);

  function applyKey(event: FormEvent) {
    event.preventDefault();
    setAppliedKey(adminKey);
  }

  return (
    <section aria-labelledby="records-heading">
      <div className="section-heading">
        <h2 id="records-heading" className="section-title">Stored records</h2>
        <button type="button" className="button button-secondary" onClick={() => void reload()}>Refresh</button>
      </div>

      {state.phase === 'loading' && <Loading label="Loading records" />}
      {state.phase === 'offline' && <OfflineNotice service="records" onRetry={reload} />}
      {state.phase === 'error' && <Notice tone="danger" title="Records could not be loaded">{state.message}</Notice>}
      {state.phase === 'ready' && state.data.length === 0 && <p className="empty">No records are stored yet.</p>}
      {state.phase === 'ready' && state.data.length > 0 && (
        <>
          <p className="muted">
            {state.data[0].masked
              ? 'Personal details are masked. An admin key shows the full records.'
              : 'Showing full records.'}
          </p>
          <div className="table-wrap" tabIndex={0} role="region" aria-labelledby="records-heading">
            <table>
              <thead>
                <tr><th scope="col">Name</th><th scope="col">Email</th><th scope="col">Phone</th><th scope="col">Status</th><th scope="col">Stored</th></tr>
              </thead>
              <tbody>
                {state.data.map((record) => (
                  <tr key={record.id}>
                    <td>{record.fullName}</td>
                    <td>{record.email}</td>
                    <td>{record.phone}</td>
                    <td><Badge tone={RECORD_OUTCOMES[record.status].tone}>{RECORD_OUTCOMES[record.status].label}</Badge></td>
                    <td>{formatDateTime(record.createdAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </>
      )}

      <form className="inline-form" onSubmit={applyKey}>
        <Field
          label="Admin key"
          name="adminKey"
          type="password"
          autoComplete="off"
          value={adminKey}
          onChange={(event) => setAdminKey(event.target.value)}
          hint="Kept in this page only, and cleared when you leave."
        />
        <button type="submit" className="button button-secondary">Show full records</button>
      </form>
    </section>
  );
}

export function ValidationPage() {
  const [form, setForm] = useState(EMPTY_FORM);
  const [result, setResult] = useState<RecordResult | null>(null);
  const [failure, setFailure] = useState<Failure | null>(null);
  const [busy, setBusy] = useState(false);
  const [recordsVersion, setRecordsVersion] = useState(0);

  const update = (field: keyof RecordInput) => (event: { target: { value: string } }) =>
    setForm({ ...form, [field]: event.target.value });

  async function run(action: (input: RecordInput) => Promise<RecordResult>) {
    setBusy(true);
    setResult(null);
    setFailure(null);
    try {
      const outcome = await action(form);
      setResult(outcome);
      if (outcome.stored) {
        setRecordsVersion((version) => version + 1);
      }
    } catch (error) {
      setFailure(toFailure(error));
    } finally {
      setBusy(false);
    }
  }

  function submit(event: FormEvent) {
    event.preventDefault();
    void run(validateRecord);
  }

  const fieldErrors = failure?.fieldErrors ?? {};

  return (
    <>
      <PageHeader title="Data Validation">
        Check a record against what is already stored. Spacing and capitals are normalised first, so
        formatting cannot hide a duplicate.
      </PageHeader>
      <ServiceBanner service="records" />

      <section className="two-column" aria-label="Check a record">
        <form className="card form" onSubmit={submit} noValidate>
          <h2 className="section-title">Check a record</h2>
          <Field label="Full name" name="fullName" autoComplete="name" required value={form.fullName} onChange={update('fullName')} error={fieldErrors.fullName} />
          <Field label="Email" name="email" type="email" autoComplete="email" required value={form.email} onChange={update('email')} error={fieldErrors.email} />
          <Field label="Phone" name="phone" type="tel" autoComplete="tel" required value={form.phone} onChange={update('phone')} error={fieldErrors.phone} />
          <Field label="Address (optional)" name="address" autoComplete="street-address" value={form.address} onChange={update('address')} error={fieldErrors.address} />
          <div className="form-actions">
            <button type="submit" className="button button-primary" disabled={busy}>Check only</button>
            <button type="button" className="button button-secondary" disabled={busy} onClick={() => void run(saveRecord)}>Check and store</button>
          </div>
        </form>

        <div aria-live="polite">
          {busy && <Loading label="Checking record" />}
          {result && <Outcome result={result} />}
          {failure && <Notice tone="danger" title="The record could not be checked">{failure.message}</Notice>}
          {!busy && !result && !failure && (
            <p className="empty">The result appears here. "Check only" stores nothing.</p>
          )}
        </div>
      </section>

      <RecordsSection version={recordsVersion} />
    </>
  );
}
