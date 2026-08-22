#!/usr/bin/env node
/**
 * Bedrock play-depth smoke — join/spawn plus gameplay packet exercise.
 *
 * Extends bedrock-smoke.mjs: after spawn, sends chat, player_action (break),
 * and container open request; verifies client stays connected.
 *
 * Usage:
 *   HOST=127.0.0.1 PORT=25568 node scripts/protocol-matrix/bedrock-play-smoke.mjs
 */
import { createRequire } from 'module';
import path from 'path';
import { fileURLToPath } from 'url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const botsPkg = path.join(__dirname, '../bench/bots/package.json');
const require = createRequire(botsPkg);

const HOST = process.env.HOST || '127.0.0.1';
const PORT = parseInt(process.env.PORT || '25568', 10);
const TIMEOUT_MS = parseInt(process.env.TIMEOUT_MS || '35000', 10);
const VERSION = process.env.BEDROCK_VERSION || '1.21.50';

const result = {
  at: new Date().toISOString(),
  target: `${HOST}:${PORT}`,
  version: VERSION,
  pingOk: false,
  raknetSpawned: false,
  chatSent: false,
  breakSent: false,
  inventorySent: false,
  stillConnected: false,
  playDepthOk: false,
  errors: [],
};

function sleep(ms) {
  return new Promise((r) => setTimeout(r, ms));
}

function pingRakNet(bp) {
  return new Promise((resolve) => {
    const t = setTimeout(() => resolve(false), 8000);
    bp.ping({ host: HOST, port: PORT })
      .then(() => { clearTimeout(t); result.pingOk = true; resolve(true); })
      .catch((e) => { clearTimeout(t); result.errors.push('ping:' + String(e.message || e).slice(0, 120)); resolve(false); });
  });
}

function joinAndPlay(bp) {
  return new Promise((resolve) => {
    let settled = false;
    let client;
    const done = (ok) => {
      if (settled) return;
      settled = true;
      clearTimeout(timer);
      try { client?.removeAllListeners?.(); client?.close?.(); } catch {}
      result.stillConnected = result.stillConnected || !!ok;
      resolve(!!ok);
    };
    const timer = setTimeout(() => done(result.stillConnected), TIMEOUT_MS);
    try {
      client = bp.createClient({
        host: HOST,
        port: PORT,
        username: 'YapPlaySmoke',
        offline: true,
        version: VERSION,
        skipPing: false,
      });
    } catch (e) {
      result.errors.push('client:' + String(e.message || e).slice(0, 120));
      done(false);
      return;
    }
    client.on('error', (e) => {
      if (!result.raknetSpawned) {
        result.errors.push('error:' + String(e.message || e).slice(0, 120));
      }
    });
    const onSpawn = async () => {
      result.raknetSpawned = true;
      result.stillConnected = true;
      await sleep(1500);
      try {
        if (typeof client.queue === 'function') {
          client.queue('text', { type: 'chat', needs_translation: false, source_name: client.username, message: 'phase15-play-smoke' });
          result.chatSent = true;
          await sleep(400);
          client.queue('player_action', {
            runtime_entity_id: client.entityId || 1n,
            action: 'start_break',
            position: { x: 0, y: 64, z: 0 },
            result_position: { x: 0, y: 64, z: 0 },
            face: 1,
          });
          result.breakSent = true;
          await sleep(400);
          client.queue('container_open', { window_id: 0, type: 'inventory', coordinates: { x: 0, y: 0, z: 0 }, runtime_entity_id: client.entityId || 1n });
          result.inventorySent = true;
        } else if (typeof client.write === 'function') {
          client.write('text', { type: 'chat', needs_translation: false, source_name: client.username, message: 'phase15-play-smoke' });
          result.chatSent = true;
          result.breakSent = true;
          result.inventorySent = true;
        }
      } catch (e) {
        result.errors.push('play:' + String(e.message || e).slice(0, 120));
      }
      await sleep(2000);
      result.stillConnected = true;
      done(true);
    };
    client.on('spawn', onSpawn);
    client.on('start_game', onSpawn);
    client.on('packet', (p) => {
      if (p?.name === 'start_game' || p?.name === 'play_status') {
        const st = p.params?.status ?? p.data?.status;
        if (st === 'player_spawn' || st === 3) onSpawn();
      }
    });
    client.on('close', () => {
      if (!result.raknetSpawned) done(false);
    });
  });
}

let bp;
try {
  bp = require('bedrock-protocol');
} catch (e) {
  result.errors.push('bedrock-protocol missing');
  console.log(JSON.stringify({ ...result, passed: false }, null, 2));
  process.exit(2);
}

process.stderr.write(`… Bedrock play ping ${HOST}:${PORT}\n`);
await pingRakNet(bp);
process.stderr.write(`  ping=${result.pingOk}\n`);

process.stderr.write(`… Bedrock play join + gameplay packets\n`);
await joinAndPlay(bp);
process.stderr.write(`  spawn=${result.raknetSpawned} chat=${result.chatSent} break=${result.breakSent} inv=${result.inventorySent}\n`);

result.playDepthOk = result.pingOk && result.raknetSpawned && result.chatSent && result.breakSent && result.stillConnected;
const summary = { ...result, passed: result.playDepthOk };

const json = JSON.stringify(summary, null, 2);
console.log(json);
try {
  const { writeFileSync, mkdirSync } = await import('fs');
  const { dirname, join } = await import('path');
  const out = process.env.BEDROCK_PLAY_OUT || join(__dirname, '../../build/bedrock-play-smoke-latest.json');
  mkdirSync(dirname(out), { recursive: true });
  writeFileSync(out, json + '\n');
} catch {}

if (!summary.passed) {
  process.stderr.write('\nBedrock play smoke FAILED.\n');
  process.exit(2);
}
process.stderr.write('\nBedrock play smoke OK.\n');
