import type { InputHTMLAttributes, ReactNode, SelectHTMLAttributes } from 'react';
import { SERVICE_NAMES, useServiceHealth, type ServiceKey, type ServiceState } from '../hooks/ServiceHealth';

export type Tone = 'success' | 'warning' | 'danger' | 'neutral';

export function Badge({ tone, children }: { tone: Tone; children: ReactNode }) {
  return <span className={`badge badge-${tone}`}>{children}</span>;
}

const STATE_LABEL: Record<ServiceState, string> = {
  checking: 'Checking',
  online: 'Online',
  offline: 'Offline',
};

export function StatusDot({ state }: { state: ServiceState }) {
  return (
    <span className="status">
      <span className={`status-dot status-${state}`} aria-hidden="true" />
      {STATE_LABEL[state]}
    </span>
  );
}

export function Notice({ tone, title, children }: { tone: Tone; title?: string; children: ReactNode }) {
  return (
    <div className={`notice notice-${tone}`} role={tone === 'danger' ? 'alert' : 'status'}>
      {title && <strong>{title}</strong>}
      <div>{children}</div>
    </div>
  );
}

export function OfflineNotice({ service, onRetry }: { service: ServiceKey; onRetry?: () => void }) {
  return (
    <Notice tone="warning" title={`${SERVICE_NAMES[service]} is offline`}>
      <p>The rest of the platform keeps working. Start this service to use this feature.</p>
      {onRetry && (
        <button type="button" className="button button-secondary" onClick={onRetry}>
          Try again
        </button>
      )}
    </Notice>
  );
}

/** Warns when the health check says a service is down, before the user fills in a form. */
export function ServiceBanner({ service }: { service: ServiceKey }) {
  const health = useServiceHealth();
  return health[service] === 'offline' ? <OfflineNotice service={service} /> : null;
}

export function Loading({ label }: { label: string }) {
  return (
    <p className="loading" role="status">
      <span className="spinner" aria-hidden="true" />
      {label}
    </p>
  );
}

interface FieldProps extends InputHTMLAttributes<HTMLInputElement> {
  label: string;
  name: string;
  error?: string;
  hint?: string;
}

export function Field({ label, name, error, hint, ...input }: FieldProps) {
  const describedBy = error ? `${name}-error` : hint ? `${name}-hint` : undefined;
  return (
    <div className="field">
      <label htmlFor={name}>{label}</label>
      <input id={name} name={name} aria-invalid={Boolean(error)} aria-describedby={describedBy} {...input} />
      {hint && !error && <p id={`${name}-hint`} className="field-hint">{hint}</p>}
      {error && <p id={`${name}-error`} className="field-error">{error}</p>}
    </div>
  );
}

interface SelectFieldProps extends SelectHTMLAttributes<HTMLSelectElement> {
  label: string;
  name: string;
  error?: string;
  children: ReactNode;
}

export function SelectField({ label, name, error, children, ...select }: SelectFieldProps) {
  return (
    <div className="field">
      <label htmlFor={name}>{label}</label>
      <select id={name} name={name} aria-invalid={Boolean(error)} aria-describedby={error ? `${name}-error` : undefined} {...select}>
        {children}
      </select>
      {error && <p id={`${name}-error`} className="field-error">{error}</p>}
    </div>
  );
}

export function PageHeader({ title, children }: { title: string; children: ReactNode }) {
  return (
    <header className="page-header">
      <h1>{title}</h1>
      <p>{children}</p>
    </header>
  );
}
