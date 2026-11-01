import react from '@vitejs/plugin-react';
import { loadEnv } from 'vite';
import { defineConfig } from 'vitest/config';

// The browser only ever talks to this server, so the services need no CORS and can stay private.
function serviceProxy(env: Record<string, string>) {
  const records = env.RECORDS_URL ?? 'http://localhost:8081';
  const buspass = env.BUSPASS_URL ?? 'http://localhost:8082';
  const chatbot = env.CHATBOT_URL ?? 'http://localhost:8090';
  const health = (target: string) => ({ target, rewrite: () => '/actuator/health' });

  return {
    '/api/records': records,
    '/api/buspass': buspass,
    '/api/chat': chatbot,
    '/health/records': health(records),
    '/health/buspass': health(buspass),
    '/health/chatbot': health(chatbot),
  };
}

export default defineConfig(({ mode }) => {
  const env = { ...loadEnv(mode, process.cwd(), ''), ...process.env } as Record<string, string>;
  const port = Number(env.WEB_PORT ?? 5173);
  const proxy = serviceProxy(env);

  return {
    plugins: [react()],
    server: { port, strictPort: true, proxy },
    preview: { port, strictPort: true, proxy },
    test: {
      environment: 'jsdom',
      globals: true,
      setupFiles: './src/test/setup.ts',
      css: false,
    },
  };
});
