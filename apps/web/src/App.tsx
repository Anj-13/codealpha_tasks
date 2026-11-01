import { Link, Route, Routes } from 'react-router-dom';
import { Layout } from './components/Layout';
import { ServiceHealthProvider } from './hooks/ServiceHealth';
import { ArchitecturePage } from './pages/ArchitecturePage';
import { AssistantPage } from './pages/AssistantPage';
import { BusPassPage } from './pages/BusPassPage';
import { DashboardPage } from './pages/DashboardPage';
import { ValidationPage } from './pages/ValidationPage';

function NotFoundPage() {
  return (
    <section>
      <h1>Page not found</h1>
      <p><Link to="/">Back to the dashboard</Link></p>
    </section>
  );
}

export function App() {
  return (
    <ServiceHealthProvider>
      <Routes>
        <Route element={<Layout />}>
          <Route index element={<DashboardPage />} />
          <Route path="validation" element={<ValidationPage />} />
          <Route path="bus-pass" element={<BusPassPage />} />
          <Route path="assistant" element={<AssistantPage />} />
          <Route path="architecture" element={<ArchitecturePage />} />
          <Route path="*" element={<NotFoundPage />} />
        </Route>
      </Routes>
    </ServiceHealthProvider>
  );
}
