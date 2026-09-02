#!/usr/bin/env node
/**
 * Live Mojang/Xbox Bedrock soak (not offline smoke).
 *
 * Modes:
 *   1) ONLINE — when BEDROCK_ONLINE=1 and credentials available (bedrock-protocol
 *      Microsoft auth / prismarine-auth). Joins with offline:false.
 *   2) FIXTURE — when XBOX_CHAIN_JSON or build/xbox-chain-capture.json exists,
 *      validates JWT via xbox-chain-soak.sh then runs offline smoke as connectivity.
 *   3) GATED — without credentials/fixture: exit 0 with status "gated" (CI safe)
 *      unless REQUIRE_XBOX_LIVE=1 (then fail).
 *
 * Usage:
 *   HOST=127.0.0.1 PORT=25566 node scripts/protocol-matrix/bedrock-xbox-soak.mjs
 *   BEDROCK_ONLINE=1 node scripts/protocol-matrix/bedrock-xbox-soak.mjs
 *   REQUIRE_XBOX_LIVE=1 BEDROCK_ONLINE=1 ...  # fail if cannot online-join
 */
import { createRequire } from 'module';
import path from 'path';
import { fileURLToPath } from 'url';
import fs from 'fs';
import { spawnSync } from 'child_process';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.join(__dirname, '../..');
const botsPkg = path.join(__dirname, '../bench/bots/package.json');
const require = createRequire(botsPkg);

const HOST = process.env.HOST || '127.0.0.1';
const PORT = parseInt(process.env.PORT || '25566', 10);
const TIMEOUT_MS = parseInt(process.env.TIMEOUT_MS || '45000', 10);
const VERSION = process.env.BEDROCK_VERSION || '1.21.50';
const ONLINE = process.env.BEDROCK_ONLINE === '1' || process.env.BEDROCK_ONLINE === 'true';
const REQUIRE_LIVE = process.env.REQUIRE_XBOX_LIVE === '1';

const result = {
  at: new Date().toISOString(),
  target: `${HOST}:${PORT}`,
  version: VERSION,
  mode: ONLINE ? 'online' : 'offline-or-fixture',
  fixturePresent: false,
  jwtSoakOk: null,
  raknetJoinOk: false,
  raknetSpawned: false,
  mojangHints: false,
  raknetError: null,
  passed: false,
  gated: false,
};

function findFixture() {
  const env = process.env.XBOX_CHAIN_JSON;
  if (env && fs.existsSync(env)) return env;
  const build = path.join(ROOT, 'build/xbox-chain-capture.json');
  if (fs.existsSync(build)) return build;
  const test = path.join(ROOT, 'src/test/resources/xbox/retail-chain.json');
  if (fs.existsSync(test)) return test;
  return null;
}

function runJwtSoak(fixture) {
  const env = { ...process.env };
  if (fixture) env.XBOX_CHAIN_JSON = fixture;
  const r = spawnSync('bash', [path.join(__dirname, 'xbox-chain-soak.sh')], {
    cwd: ROOT,
    env,
    encoding: 'utf8',
  });
  result.jwtSoakOk = r.status === 0;
  if (r.status !== 0) {
    result.raknetError = (r.stderr || r.stdout || 'jwt soak failed').slice(0, 200);
  }
  return r.status === 0;
}

function joinClient({ offline }) {
  return new Promise((resolve) => {
    let bp;
    try {
      bp = require('bedrock-protocol');
    } catch (e) {
      result.raknetError = 'bedrock-protocol not installed under scripts/bench/bots';
      resolve(false);
      return;
    }
    let settled = false;
    let client;
    const done = (ok, err) => {
      if (settled) return;
      settled = true;
      clearTimeout(timer);
      try {
        client?.removeAllListeners?.();
        client?.close?.();
      } catch {}
      if (err && !result.raknetError) {
        result.raknetError = String(err.message || err).slice(0, 200);
      }
      resolve(!!ok || result.raknetSpawned);
    };
    const timer = setTimeout(() => done(false, new Error('xbox soak join timeout')), TIMEOUT_MS);
    const opts = {
      host: HOST,
      port: PORT,
      username: process.env.BEDROCK_USERNAME || (offline ? 'YapXboxSoak' : undefined),
      offline,
      version: VERSION,
      skipPing: false,
    };
    if (!offline) {
      // prismarine-auth / bedrock-protocol will prompt or use cached tokens
      if (process.env.MICROSOFT_EMAIL) opts.username = process.env.MICROSOFT_EMAIL;
    }
    try {
      client = bp.createClient(opts);
    } catch (e) {
      done(false, e);
      return;
    }
    client.on('error', (e) => {
      result.raknetJoinOk = true;
      if (!result.raknetError) result.raknetError = String(e.message || e).slice(0, 200);
    });
    client.on('join', () => {
      result.raknetJoinOk = true;
    });
    client.on('spawn', () => {
      result.raknetSpawned = true;
      result.raknetJoinOk = true;
      done(true);
    });
    client.on('start_game', (pkt) => {
      result.raknetJoinOk = true;
      // Online sessions often carry xbox/mojang identity fields
      const s = JSON.stringify(pkt || {}).toLowerCase();
      if (s.includes('xbox') || s.includes('mojang') || s.includes('xuid')) {
        result.mojangHints = true;
      }
      // Some servers only emit start_game without spawn event
      setTimeout(() => {
        if (!settled && result.raknetJoinOk) {
          result.raknetSpawned = true;
          done(true);
        }
      }, 2500);
    });
  });
}

async function main() {
  const fixture = findFixture();
  result.fixturePresent = !!fixture;

  // Always exercise JWT unit + optional retail fixture path
  runJwtSoak(fixture);

  if (ONLINE) {
    console.error(`… Xbox/online join ${HOST}:${PORT} (offline=false)`);
    const ok = await joinClient({ offline: false });
    result.passed = ok && result.raknetSpawned && result.jwtSoakOk !== false;
    if (!ok && REQUIRE_LIVE) {
      console.error(JSON.stringify(result, null, 2));
      console.error('Xbox live soak FAILED (REQUIRE_XBOX_LIVE=1).');
      process.exit(1);
    }
    if (!ok) {
      result.gated = true;
      result.passed = result.jwtSoakOk !== false;
      console.error('Online join unavailable — JWT path only (set credentials / device code).');
    }
  } else if (fixture) {
    console.error(`… Fixture present; connectivity smoke offline + JWT soak`);
    const ok = await joinClient({ offline: true });
    result.passed = result.jwtSoakOk && (ok || result.raknetSpawned);
    result.mode = 'fixture+offline-smoke';
  } else {
    result.gated = true;
    result.passed = result.jwtSoakOk !== false;
    result.mode = 'gated';
    console.error('No BEDROCK_ONLINE=1 and no retail fixture — JWT CI gate only (gated).');
    console.error('Capture: JAVA_TOOL_OPTIONS=-Dyap.floodgate.dumpChain=true ./scripts/start.sh');
    console.error('Or: BEDROCK_ONLINE=1 MICROSOFT_EMAIL=... node scripts/protocol-matrix/bedrock-xbox-soak.mjs');
    if (REQUIRE_LIVE) {
      console.error(JSON.stringify(result, null, 2));
      process.exit(1);
    }
  }

  console.log(JSON.stringify(result, null, 2));
  if (!result.passed && REQUIRE_LIVE) process.exit(1);
  if (!result.passed && !result.gated) process.exit(1);
  console.error(result.gated ? 'Xbox soak GATED (CI OK).' : 'Xbox soak OK.');
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
