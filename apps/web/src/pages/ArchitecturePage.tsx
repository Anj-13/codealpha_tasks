import { PageHeader } from '../components/ui';

const MODULES = [
  {
    name: 'Data Validation',
    port: '8081',
    stack: 'Spring Boot, SQLite',
    detail: 'Normalises each record, fingerprints it with SHA-256, and stores only unique records.',
  },
  {
    name: 'Bus Pass',
    port: '8082',
    stack: 'Spring Boot, H2',
    detail: 'Prices and issues HMAC-signed tickets, and assigns seats under a database lock.',
  },
  {
    name: 'Assistant',
    port: '8090',
    stack: 'JDK HttpServer',
    detail: 'Matches messages to predefined intents, then answers from fixed text or a live lookup.',
  },
];

const OUTAGES = [
  {
    failure: 'Data Validation offline',
    effect: 'Tickets are still issued, marked Pending, and checked automatically once it returns.',
  },
  {
    failure: 'Bus Pass offline',
    effect: 'Booking pages show an offline notice. The assistant still gives its fixed answers.',
  },
  {
    failure: 'Assistant offline',
    effect: 'Only the Assistant page is affected.',
  },
];

function ArchitectureDiagram() {
  return (
    <svg className="diagram" viewBox="0 0 720 300" role="img" aria-labelledby="diagram-title diagram-description">
      <title id="diagram-title">How the services connect</title>
      <desc id="diagram-description">
        The browser talks to the web app, which proxies to all three services. The assistant calls Bus Pass and
        Data Validation, and Bus Pass calls Data Validation. Bus Pass stores data in H2, and Data Validation in SQLite.
      </desc>
      <defs>
        <marker id="arrow" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse">
          <path d="M 0 0 L 10 5 L 0 10 z" className="diagram-arrow" />
        </marker>
      </defs>

      <g className="diagram-node">
        <rect x="20" y="124" width="112" height="52" rx="8" />
        <text x="76" y="155">Web app</text>
        <rect x="270" y="20" width="150" height="52" rx="8" />
        <text x="345" y="51">Assistant</text>
        <rect x="270" y="124" width="150" height="52" rx="8" />
        <text x="345" y="155">Bus Pass</text>
        <rect x="270" y="228" width="150" height="52" rx="8" />
        <text x="345" y="259">Data Validation</text>
      </g>
      <g className="diagram-node diagram-store">
        <rect x="570" y="124" width="130" height="52" rx="8" />
        <text x="635" y="155">H2</text>
        <rect x="570" y="228" width="130" height="52" rx="8" />
        <text x="635" y="259">SQLite</text>
      </g>

      <g className="diagram-line" markerEnd="url(#arrow)">
        <path d="M132 136 L268 52" />
        <path d="M132 150 L268 150" />
        <path d="M132 164 L268 248" />
        <path d="M345 72 L345 122" />
        <path d="M345 176 L345 226" />
        <path d="M420 150 L568 150" />
        <path d="M420 254 L568 254" />
        <path d="M420 46 C 530 46, 530 240, 422 240" />
      </g>

      <g className="diagram-label">
        <text x="182" y="141">Proxy</text>
        <text x="353" y="102">Routes, bookings</text>
        <text x="353" y="206">Validate and store</text>
        <text x="512" y="104">Records</text>
      </g>
    </svg>
  );
}

export function ArchitecturePage() {
  return (
    <>
      <PageHeader title="Architecture">
        Three services that each run on their own, plus one frontend. Each service owns its data and shares
        it only through its API.
      </PageHeader>

      <section aria-labelledby="flow-heading">
        <h2 id="flow-heading" className="section-title">How the services connect</h2>
        <div className="card"><ArchitectureDiagram /></div>
      </section>

      <section aria-labelledby="modules-heading">
        <h2 id="modules-heading" className="section-title">Modules</h2>
        <div className="card-grid">
          {MODULES.map((module) => (
            <article key={module.name} className="card">
              <h3>{module.name}</h3>
              <p>{module.detail}</p>
              <p className="muted">{module.stack}, port {module.port}</p>
            </article>
          ))}
        </div>
      </section>

      <section aria-labelledby="outage-heading">
        <h2 id="outage-heading" className="section-title">When a service is offline</h2>
        <div className="table-wrap" tabIndex={0} role="region" aria-labelledby="outage-heading">
          <table>
            <thead><tr><th scope="col">Failure</th><th scope="col">What happens</th></tr></thead>
            <tbody>
              {OUTAGES.map((outage) => (
                <tr key={outage.failure}><td>{outage.failure}</td><td>{outage.effect}</td></tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>

      <section aria-labelledby="scope-heading">
        <h2 id="scope-heading" className="section-title">What this is not</h2>
        <ul className="plain-list">
          <li>It runs locally, and is not deployed to a cloud provider.</li>
          <li>Scaling figures are advice. No servers are provisioned.</li>
          <li>The assistant uses fixed rules, not a machine learning model.</li>
        </ul>
      </section>
    </>
  );
}
