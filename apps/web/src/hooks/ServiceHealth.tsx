import { createContext, useContext, useEffect, useState, type ReactNode } from 'react';

export type ServiceKey = 'records' | 'buspass' | 'chatbot';
export type ServiceState = 'checking' | 'online' | 'offline';
type HealthMap = Record<ServiceKey, ServiceState>;

const POLL_INTERVAL_MS = 15000;
const CHECK_TIMEOUT_MS = 5000;
const SERVICE_KEYS: ServiceKey[] = ['records', 'buspass', 'chatbot'];

export const SERVICE_NAMES: Record<ServiceKey, string> = {
  records: 'Data Validation',
  buspass: 'Bus Pass',
  chatbot: 'Assistant',
};

const initialHealth: HealthMap = { records: 'checking', buspass: 'checking', chatbot: 'checking' };
const ServiceHealthContext = createContext<HealthMap>(initialHealth);

async function isOnline(service: ServiceKey): Promise<boolean> {
  try {
    const response = await fetch(`/health/${service}`, { signal: AbortSignal.timeout(CHECK_TIMEOUT_MS) });
    const body = await response.json();
    return response.ok && body.status === 'UP';
  } catch {
    return false;
  }
}

export function ServiceHealthProvider({ children }: { children: ReactNode }) {
  const [health, setHealth] = useState<HealthMap>(initialHealth);

  useEffect(() => {
    let active = true;

    async function check() {
      const results = await Promise.all(SERVICE_KEYS.map(isOnline));
      if (active) {
        setHealth({
          records: results[0] ? 'online' : 'offline',
          buspass: results[1] ? 'online' : 'offline',
          chatbot: results[2] ? 'online' : 'offline',
        });
      }
    }

    void check();
    const timer = window.setInterval(check, POLL_INTERVAL_MS);
    return () => {
      active = false;
      window.clearInterval(timer);
    };
  }, []);

  return <ServiceHealthContext.Provider value={health}>{children}</ServiceHealthContext.Provider>;
}

export function useServiceHealth(): HealthMap {
  return useContext(ServiceHealthContext);
}
