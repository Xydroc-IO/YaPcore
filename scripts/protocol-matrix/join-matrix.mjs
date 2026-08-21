#!/usr/bin/env node
/**
 * Protocol client matrix: offline JE bots at key protocol versions → YaP/Paper host.
 *
 * Usage:
 *   HOST=127.0.0.1 PORT=25566 node scripts/protocol-matrix/join-matrix.mjs
 */
import { createRequire } from 'module';
import path from 'path';
import { fileURLToPath } from 'url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const botsPkg = path.join(__dirname, '../bench/bots/package.json');
const require = createRequire(botsPkg);
const mc = require('minecraft-protocol');
const createClient = mc.createClient;

const HOST = process.env.HOST || '127.0.0.1';
const PORT = parseInt(process.env.PORT || '25566', 10);
const TIMEOUT_MS = parseInt(process.env.TIMEOUT_MS || '20000', 10);
const RESOURCE_PACK = process.env.RESOURCE_PACK === '1' || process.env.RESOURCE_PACK === 'true';

/**
 * Product DoD: JE 1.20.2+ (config-era) onto Paper 26.2.
 * 1.19.4 stays as an optional canary. Pre-1.19 is best-effort Rewind — only when MATRIX_FULL=1.
 */
const MATRIX_DOD = [
  ['1.19.4 canary', '1.19.4', 762],
  ['1.20.4', '1.20.4', 765],
  ['1.21.1', '1.21.1', 767],
  ['1.21.4', '1.21.4', 769],
];

const MATRIX_LEGACY = [
  ['1.8.9 / Rewind', '1.8.9', 47],
  ['1.12.2', '1.12.2', 340],
  ['1.16.5', '1.16.5', 754],
];

const MATRIX = process.env.MATRIX_FULL === '1'
  ? [...MATRIX_LEGACY, ...MATRIX_DOD]
  : MATRIX_DOD;

function attempt(label, version, protocol) {
  return new Promise((resolve) => {
    const started = Date.now();
    const result = {
      label, version, protocol, host: HOST, port: PORT,
      statusPing: false, loginOk: false, spawned: false,
      resourcePackSeen: false, resourcePackMode: RESOURCE_PACK ? 'on' : 'off',
      error: null, ms: 0,
    };
    let client;
    let settled = false;
    const done = (err) => {
      if (settled) return;
      settled = true;
      result.ms = Date.now() - started;
      if (err && !result.error) result.error = String(err.message || err);
      try { client?.end(); } catch {}
      resolve(result);
    };
    const timer = setTimeout(() => done(new Error('timeout')), TIMEOUT_MS);
    try {
      client = createClient({
        host: HOST,
        port: PORT,
        username: 'YapMatrix_' + protocol + (RESOURCE_PACK ? 'p' : ''),
        version,
        auth: 'offline',
        hideErrors: true,
      });
    } catch (e) {
      clearTimeout(timer);
      done(e);
      return;
    }
    client.on('state', () => { result.statusPing = true; });
    client.on('login', () => { result.loginOk = true; });
    // Pack packets (name varies by version / via remap)
    const notePack = (name) => {
      if (!name) return;
      const n = String(name).toLowerCase();
      if (n.includes('resource_pack') || n.includes('add_resource_pack')) {
        result.resourcePackSeen = true;
        // Do not client.write pack status here — ViaProxyHandler auto-acks toward Paper;
        // naive writes break version-specific serializers (seen on 1.21.4).
      }
    };
    client.on('packet', (data, meta) => {
      if (meta?.name) notePack(meta.name);
    });
    client.on('resource_pack_send', () => { result.resourcePackSeen = true; });
    client.on('resourcePackSend', () => { result.resourcePackSeen = true; });
    client.on('playerJoin', () => {
      result.spawned = true; result.loginOk = true; clearTimeout(timer); done();
    });
    client.on('spawn', () => {
      result.spawned = true; result.loginOk = true; clearTimeout(timer); done();
    });
    client.on('end', (reason) => {
      clearTimeout(timer);
      if (!result.spawned) done(reason || 'ended');
    });
    client.on('error', (err) => {
      clearTimeout(timer);
      done(err);
    });
    mc.ping({ host: HOST, port: PORT, version }, (err, data) => {
      if (!err && data) result.statusPing = true;
    });
  });
}

const rows = [];
for (const [label, version, protocol] of MATRIX) {
  process.stderr.write(`… ${label} (${version} / ${protocol})${RESOURCE_PACK ? ' [pack-on]' : ''}\n`);
  rows.push(await attempt(label, version, protocol));
}

const passedSpawn = rows.filter((r) => r.spawned).length;
const passedLogin = rows.filter((r) => r.loginOk).length;
const passedStatus = rows.filter((r) => r.statusPing).length;
const summary = {
  at: new Date().toISOString(),
  target: `${HOST}:${PORT}`,
  resourcePack: RESOURCE_PACK,
  rows,
  passedSpawn,
  passedLogin,
  passedStatus,
  total: rows.length,
};
console.log(JSON.stringify(summary, null, 2));

const gaps = rows.filter((r) => !r.spawned);
if (gaps.length) {
  process.stderr.write('\nGaps (no spawn) — fill remaps from these failures:\n');
  for (const g of gaps) {
    process.stderr.write(`  - ${g.label}: ${g.error || 'no spawn'}\n`);
  }
  process.exit(2);
}
process.stderr.write(`\nAll matrix clients spawned${RESOURCE_PACK ? ' (resource-pack-on)' : ''}.\n`);
