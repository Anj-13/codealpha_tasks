import { render } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { vi } from 'vitest';
import { App } from '../App';

type Reply = { status?: number; body?: unknown } | 'offline' | 'network-error' | (() => Promise<Reply>);

export type Routes = Record<string, Reply>;

const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });

/** Stubs fetch by "METHOD /path". Anything unlisted behaves like a proxy with no service behind it. */
export function stubFetch(routes: Routes) {
  const calls: { key: string; body: unknown; headers: Headers }[] = [];

  const resolve = async (reply: Reply): Promise<Response> => {
    if (typeof reply === 'function') {
      return resolve(await reply());
    }
    if (reply === 'offline') {
      return new Response('', { status: 500 });
    }
    if (reply === 'network-error') {
      throw new TypeError('Failed to fetch');
    }
    return json(reply.status ?? 200, reply.body);
  };

  const fetchStub = vi.fn(async (url: string, init: RequestInit = {}) => {
    const key = `${init.method ?? 'GET'} ${url}`;
    calls.push({
      key,
      body: init.body ? JSON.parse(init.body as string) : undefined,
      headers: new Headers(init.headers),
    });
    return resolve(routes[key] ?? 'offline');
  });

  vi.stubGlobal('fetch', fetchStub);
  return calls;
}

export const ALL_UP: Routes = {
  'GET /health/records': { body: { status: 'UP' } },
  'GET /health/buspass': { body: { status: 'UP' } },
  'GET /health/chatbot': { body: { status: 'UP' } },
};

export function renderApp(path = '/') {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <App />
    </MemoryRouter>,
  );
}
