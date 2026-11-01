import { Link } from 'react-router-dom';
import { getScaleMetrics, listRoutes } from '../api/buspass';
import { getRecordSummary } from '../api/records';
import { Loading, OfflineNotice, PageHeader, StatusDot } from '../components/ui';
import { formatDateTime } from '../format';
import { useServiceHealth, type ServiceKey } from '../hooks/ServiceHealth';
import { useLoad, type LoadState } from '../hooks/useLoad';

const MODULES: { service: ServiceKey; to: string; title: string; summary: string; action: string }[] = [
  {
    service: 'records',
    to: '/validation',
    title: 'Data Validation',
    summary: 'Classify records as unique, duplicate or in need of review, and store only unique ones.',
    action: 'Validate a record',
  },
  {
    service: 'buspass',
    to: '/bus-pass',
    title: 'Bus Pass',
    summary: 'Browse routes and fares, book a signed ticket, and check a booking.',
    action: 'Book a ticket',
  },
  {
    service: 'chatbot',
    to: '/assistant',
    title: 'Assistant',
    summary: 'Ask about routes, fares and validation. Answers come from fixed rules and live lookups.',
    action: 'Ask a question',
  },
];

function Metric<T>({ label, state, value, note }: {
  label: string;
  state: LoadState<T>;
  value: (data: T) => string;
  note?: (data: T) => string;
}) {
  return (
    <div className="metric">
      <dt>{label}</dt>
      <dd className="metric-value">
        {state.phase === 'ready' ? value(state.data) : state.phase === 'loading' ? '…' : 'Unavailable'}
      </dd>
      {state.phase === 'ready' && note && <dd className="metric-note">{note(state.data)}</dd>}
    </div>
  );
}

export function DashboardPage() {
  const health = useServiceHealth();
  const summary = useLoad(getRecordSummary);
  const routes = useLoad(listRoutes);
  const scale = useLoad(getScaleMetrics);

  return (
    <>
      <PageHeader title="Dashboard">
        Three independent services, one place to use them. Each runs on its own, so one being offline
        does not stop the others.
      </PageHeader>

      <section aria-labelledby="modules-heading">
        <h2 id="modules-heading" className="section-title">Services</h2>
        <div className="card-grid">
          {MODULES.map((module) => (
            <article key={module.service} className="card module-card">
              <div className="card-heading">
                <h3>{module.title}</h3>
                <StatusDot state={health[module.service]} />
              </div>
              <p>{module.summary}</p>
              <Link to={module.to} className="button button-primary">{module.action}</Link>
            </article>
          ))}
        </div>
      </section>

      <section aria-labelledby="live-heading">
        <h2 id="live-heading" className="section-title">Live figures</h2>
        <dl className="metric-grid">
          <Metric
            label="Stored records"
            state={summary.state}
            value={(data) => String(data.totalRecords)}
            note={(data) => (data.lastStoredAt ? `Last stored ${formatDateTime(data.lastStoredAt)}` : 'Nothing stored yet')}
          />
          <Metric label="Bus routes" state={routes.state} value={(data) => String(data.length)} />
          <Metric label="Total bookings" state={scale.state} value={(data) => String(data.totalBookings)} />
          <Metric
            label="Recommended servers"
            state={scale.state}
            value={(data) => `${data.recommendedServers} of ${data.maxServers}`}
            note={() => 'Advisory only. No servers are provisioned.'}
          />
        </dl>
        {summary.state.phase === 'loading' && scale.state.phase === 'loading' && <Loading label="Loading live figures" />}
        {summary.state.phase === 'offline' && <OfflineNotice service="records" onRetry={summary.reload} />}
        {scale.state.phase === 'offline' && <OfflineNotice service="buspass" onRetry={() => { void routes.reload(); void scale.reload(); }} />}
      </section>
    </>
  );
}
