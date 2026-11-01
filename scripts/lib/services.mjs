import { spawn, spawnSync } from 'node:child_process';
import { existsSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

export const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..', '..');

export const SERVICES = {
  records: {
    name: 'Task 1: Data Redundancy',
    dir: 'services/data-redundancy',
    jar: 'target/removalsystem-0.0.1-SNAPSHOT.jar',
    port: 8081,
    portVariable: 'SERVER_PORT',
  },
  buspass: {
    name: 'Task 3: Bus Pass',
    dir: 'services/bus-pass',
    jar: 'target/buspass-0.0.1-SNAPSHOT.jar',
    port: 8082,
    portVariable: 'SERVER_PORT',
  },
  chatbot: {
    name: 'Task 4: Chatbot',
    dir: 'services/chatbot',
    jar: 'target/chatbot-1.0.0.jar',
    port: 8090,
    portVariable: 'CHATBOT_PORT',
  },
};

const isWindows = process.platform === 'win32';

export function jarPath(service) {
  return join(ROOT, service.dir, service.jar);
}

export function buildJars(services, { force = false } = {}) {
  for (const service of services) {
    if (!force && existsSync(jarPath(service))) {
      continue;
    }
    console.log(`Building ${service.name}...`);
    const [command, args] = isWindows
      ? ['cmd', ['/c', 'mvnw.cmd', '-q', '-DskipTests', 'package']]
      : ['sh', ['mvnw', '-q', '-DskipTests', 'package']];
    const result = spawnSync(command, args, { cwd: join(ROOT, service.dir), stdio: 'inherit' });
    if (result.status !== 0) {
      throw new Error(`Build failed for ${service.name}`);
    }
  }
}

export function startService(service, port, env, onOutput) {
  const child = spawn('java', ['-jar', jarPath(service)], {
    cwd: join(ROOT, service.dir),
    env: { ...process.env, ...env, [service.portVariable]: String(port) },
    stdio: ['ignore', 'pipe', 'pipe'],
  });
  child.stdout.on('data', onOutput);
  child.stderr.on('data', onOutput);
  return child;
}

export async function waitForHealth(child, port, timeoutMs = 240000) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    if (child.exitCode !== null) {
      throw new Error(`The service for port ${port} exited during startup with code ${child.exitCode}`);
    }
    try {
      const response = await fetch(`http://localhost:${port}/actuator/health`);
      if (response.ok) {
        return;
      }
    } catch {
      // Not listening yet.
    }
    await new Promise((resolve) => setTimeout(resolve, 500));
  }
  throw new Error(`Nothing healthy on port ${port} after ${timeoutMs} ms`);
}

export function stopProcess(child) {
  return new Promise((resolve) => {
    if (child.exitCode !== null || child.signalCode !== null) {
      resolve();
      return;
    }
    child.once('exit', resolve);
    child.kill();
  });
}
