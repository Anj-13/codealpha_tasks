import { NavLink, Outlet } from 'react-router-dom';
import { SERVICE_NAMES, useServiceHealth, type ServiceKey } from '../hooks/ServiceHealth';
import { useTheme } from '../hooks/useTheme';
import { StatusDot } from './ui';

const NAV_ITEMS = [
  { to: '/', label: 'Dashboard' },
  { to: '/validation', label: 'Data Validation' },
  { to: '/bus-pass', label: 'Bus Pass' },
  { to: '/assistant', label: 'Assistant' },
  { to: '/architecture', label: 'Architecture' },
];

const SERVICE_ORDER: ServiceKey[] = ['records', 'buspass', 'chatbot'];

export function Layout() {
  const health = useServiceHealth();
  const { theme, toggleTheme } = useTheme();

  return (
    <div className="shell">
      <a className="skip-link" href="#main">Skip to content</a>
      <aside className="sidebar">
        <div className="brand">
          <span className="brand-mark" aria-hidden="true" />
          Cloud Smart Services
        </div>
        <nav aria-label="Main">
          <ul className="nav-list">
            {NAV_ITEMS.map((item) => (
              <li key={item.to}>
                <NavLink to={item.to} end={item.to === '/'} className="nav-link">
                  {item.label}
                </NavLink>
              </li>
            ))}
          </ul>
        </nav>
        <div className="sidebar-footer">
          <dl className="service-list" aria-label="Service status">
            {SERVICE_ORDER.map((service) => (
              <div key={service}>
                <dt>{SERVICE_NAMES[service]}</dt>
                <dd><StatusDot state={health[service]} /></dd>
              </div>
            ))}
          </dl>
          <button type="button" className="button button-secondary theme-toggle" onClick={toggleTheme}>
            {theme === 'dark' ? 'Use light theme' : 'Use dark theme'}
          </button>
        </div>
      </aside>
      <main id="main" className="content" tabIndex={-1}>
        <Outlet />
      </main>
    </div>
  );
}
