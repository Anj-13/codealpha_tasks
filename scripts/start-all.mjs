#!/usr/bin/env node
// Starts all three services, then the unified web app. Ctrl+C stops everything.
// Pass --rebuild to rebuild the jars first. Set ports with the same variables as each service.
import { spawn, spawnSync } from 'node:child_process';
import { existsSync } from 'node:fs';
import { join } from 'node:path';
import { ROOT, SERVICES, buildJars, startService, stopProcess, waitForHealth } from './lib/services.mjs';

const WEB_DIR = join(ROOT, 'apps', 'web');
const WEB_PORT = process.env.WEB_PORT ?? '5173';
const PORTS = {
  records: Number(process.env.RECORDS_PORT ?? SERVICES.records.port),
  buspass: Number(process.env.BUSPASS_PORT ?? SERVICES.buspass.port),
  chatbot: Number(process.env.CHATBOT_PORT ?? SERVICES.chatbot.port),
};
const RECORDS_URL = `http://localhost:${PORTS.records}`;
const BUSPASS_URL = `http://localhost:${PORTS.buspass}`;
const CHATBOT_URL = `http://localhost:${PORTS.chatbot}`;
const ENV = {
  records: {},
  buspass: { REMOVAL_BASE_URL: `${RECORDS_URL}/api/records` },
  chatbot: { BUSPASS_BASE_URL: `${BUSPASS_URL}/api/buspass`, REMOVAL_BASE_URL: `${RECORDS_URL}/api/records` },
};

const children = [];
let stopping = false;

function prefixed(label) {
  return (data) => {
    for (const line of String(data).split(/\r?\n/).filter(Boolean)) {
      console.log(`[${label}] ${line}`);
    }
  };
}

async function stopAll() {
  if (stopping) {
    return;
  }
  stopping = true;
  console.log('\nStopping everything...');
  await Promise.all(children.map(stopProcess));
  process.exit(0);
}

process.on('SIGINT', stopAll);
process.on('SIGTERM', stopAll);

buildJars(Object.values(SERVICES), { force: process.argv.includes('--rebuild') });

if (!existsSync(join(WEB_DIR, 'node_modules'))) {
  console.log('Installing web app dependencies...');
  spawnSync('npm', ['install'], { cwd: WEB_DIR, stdio: 'inherit', shell: true });
}

for (const [key, service] of Object.entries(SERVICES)) {
  console.log(`Starting ${service.name} on port ${PORTS[key]}...`);
  const child = startService(service, PORTS[key], ENV[key], prefixed(key));
  children.push(child);
  try {
    await waitForHealth(child, PORTS[key]);
    console.log(`${service.name} is up.`);
  } catch (error) {
    // The platform is built to run without any one service, so keep going and say so.
    console.error(`${service.name} did not start: ${error.message}`);
  }
}

const web = spawn('npm', ['run', 'dev'], {
  cwd: WEB_DIR,
  env: { ...process.env, WEB_PORT, RECORDS_URL, BUSPASS_URL, CHATBOT_URL },
  stdio: ['ignore', 'pipe', 'pipe'],
  shell: true,
});
web.stdout.on('data', prefixed('web'));
web.stderr.on('data', prefixed('web'));
children.push(web);

console.log(`\nOpen http://localhost:${WEB_PORT}. Press Ctrl+C to stop everything.`);
