#!/usr/bin/env node
/**
 * Bedrock smoke against live YaPcore dual-stack (UDP).
 *
 * Checks:
 *   1) Unconnected ping / MOTD (RakNet)
 *   2) Offline bedrock-protocol login → start_game/spawn (Geyser parity path)
 *   3) Fallback text JOIN protocol (dev harness)
 *
 * Usage:
 *   HOST=127.0.0.1 PORT=25566 node scripts/protocol-matrix/bedrock-smoke.mjs
 */
import { createRequire } from 'module';
import path from 'path';
import { fileURLToPath } from 'url';
import dgram from 'dgram';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const botsPkg = path.join(__dirname, '../bench/bots/package.json');
const require = createRequire(botsPkg);

const HOST = process.env.HOST || '127.0.0.1';
const PORT = parseInt(process.env.PORT || '25566', 10);
const TIMEOUT_MS = parseInt(process.env.TIMEOUT_MS || '25000', 10);
const VERSION = process.env.BEDROCK_VERSION || '1.21.50';

const result = {
  at: new Date().toISOString(),
  target: `${HOST}:${PORT}`,
  version: VERSION,
  pingOk: false,
  pingMotd: null,
  pingProtocol: null,
  raknetJoinOk: false,
  raknetSpawned: false,
  raknetError: null,
  textJoinOk: false,
  textJoinReply: null,
  textJoinError: null,
};

function pingRakNet() {
  return new Promise((resolve) => {
    let bp;
    try {
      bp = require('bedrock-protocol');
    } catch (e) {
      result.raknetError = 'bedrock-protocol not installed';
      resolve(false);
      return;
    }
    const t = setTimeout(() => {
      result.raknetError = result.raknetError || 'ping timeout';
      resolve(false);
    }, Math.min(8000, TIMEOUT_MS));
    bp.ping({ host: HOST, port: PORT })
      .then((ad) => {
        clearTimeout(t);
        result.pingOk = true;
        result.pingMotd = ad?.motd || ad?.levelName || JSON.stringify(ad)?.slice(0, 120);
        result.pingProtocol = ad?.protocol || ad?.version;
        resolve(true);
      })
      .catch((e) => {
        clearTimeout(t);
        result.raknetError = String(e.message || e);
        resolve(false);
      });
  });
}

function joinRakNet() {
  return new Promise((resolve) => {
    let bp;
    try {
      bp = require('bedrock-protocol');
    } catch (e) {
      result.raknetError = 'bedrock-protocol not installed';
      resolve(false);
      return;
    }
    let settled = false;
    let client;
    const done = (ok, err) => {
      if (settled) return;
      settled = true;
      clearTimeout(timer);
      try { client?.removeAllListeners?.(); client?.close?.(); } catch {}
      if (err && !result.raknetError) result.raknetError = String(err.message || err).slice(0, 200);
      result.raknetJoinOk = result.raknetJoinOk || !!ok || result.raknetSpawned;
      resolve(result.raknetSpawned || !!ok);
    };
    const timer = setTimeout(() => done(false, new Error('raknet join timeout')), TIMEOUT_MS);
    try {
      client = bp.createClient({
        host: HOST,
        port: PORT,
        username: 'YapBeSmoke',
        offline: true,
        version: VERSION,
        skipPing: false,
      });
    } catch (e) {
      done(false, e);
      return;
    }
    // Must register before any packets to avoid Node "Unhandled error" crash
    client.on('error', (e) => {
      result.raknetJoinOk = true; // we got far enough to decode server packets
      if (!result.raknetError) result.raknetError = String(e.message || e).slice(0, 200);
    });
    client.on('join', () => { result.raknetJoinOk = true; });
    client.on('spawn', () => {
      result.raknetSpawned = true;
      result.raknetJoinOk = true;
      done(true);
    });
    client.on('start_game', () => {
      result.raknetJoinOk = true;
      result.raknetSpawned = true;
      done(true);
    });
    client.on('packet', (p) => {
      if (!p?.name) return;
      if (['network_settings', 'play_status', 'resource_packs_info', 'resource_pack_stack', 'start_game'].includes(p.name)) {
        result.raknetJoinOk = true;
      }
      if (p.name === 'start_game') {
        result.raknetSpawned = true;
        done(true);
      }
      if (p.name === 'play_status') {
        const st = p.params?.status ?? p.data?.status;
        if (st === 'player_spawn' || st === 3 || st === 'login_success' || st === 0) {
          result.raknetJoinOk = true;
        }
        if (st === 'player_spawn' || st === 3) {
          result.raknetSpawned = true;
          done(true);
        }
      }
    });
    client.on('close', () => {
      if (!result.raknetSpawned) done(false, new Error(result.raknetError || 'closed before spawn'));
    });
  });
}

function textJoin() {
  return new Promise((resolve) => {
    const sock = dgram.createSocket('udp4');
    const timer = setTimeout(() => {
      result.textJoinError = 'text JOIN timeout';
      try { sock.close(); } catch {}
      resolve(false);
    }, 5000);
    sock.on('message', (msg) => {
      const s = msg.toString('utf8');
      result.textJoinReply = s.slice(0, 200);
      if (s.startsWith('OK|BEDROCK')) {
        result.textJoinOk = true;
        clearTimeout(timer);
        try { sock.close(); } catch {}
        resolve(true);
      }
    });
    sock.on('error', (e) => {
      result.textJoinError = String(e.message || e);
      clearTimeout(timer);
      try { sock.close(); } catch {}
      resolve(false);
    });
    const payload = Buffer.from('JOIN|YapBeText|766', 'utf8');
    sock.send(payload, PORT, HOST);
  });
}

process.stderr.write(`… Bedrock ping ${HOST}:${PORT}\n`);
await pingRakNet();
process.stderr.write(`  ping=${result.pingOk} motd=${result.pingMotd || '-'}\n`);

process.stderr.write(`… Bedrock raknet join (${VERSION})\n`);
await joinRakNet();
process.stderr.write(`  join=${result.raknetJoinOk} spawn=${result.raknetSpawned} err=${result.raknetError || '-'}\n`);

process.stderr.write(`… Bedrock text JOIN fallback\n`);
await textJoin();
process.stderr.write(`  text=${result.textJoinOk} reply=${result.textJoinReply || result.textJoinError || '-'}\n`);

const summary = {
  ...result,
  passed: result.pingOk && (result.raknetSpawned || result.textJoinOk),
  geyserParitySmoke: result.pingOk && result.raknetSpawned,
};

const json = JSON.stringify(summary, null, 2);
console.log(json);
try {
  const { writeFileSync, mkdirSync } = await import('fs');
  const { dirname, join } = await import('path');
  const out = process.env.BEDROCK_OUT || join(__dirname, '../../build/bedrock-smoke-latest.json');
  mkdirSync(dirname(out), { recursive: true });
  writeFileSync(out, json + '\n');
} catch {}

if (!summary.geyserParitySmoke) {
  process.stderr.write('\nBedrock Geyser-parity smoke FAILED (need ping + raknet spawn/start_game).\n');
  process.exit(2);
}
process.stderr.write('\nBedrock Geyser-parity smoke OK.\n');
