#!/usr/bin/env node
/**
 * P4.10 — Generate a vanilla packet-ID dump for Via mid/forward remaps.
 *
 * Usage (from repo root):
 *   node scripts/generate-protocol-dump.mjs 1.21.4
 *   node scripts/generate-protocol-dump.mjs --protocol 769
 *   node scripts/generate-protocol-dump.mjs latest   # newest pc release in minecraft-data
 *
 * Writes:
 *   src/main/resources/protocol/vanilla/<label>/packets.json
 *   updates src/main/resources/protocol/vanilla/index.json
 *
 * Requires: cd scripts/bench/bots && npm i minecraft-data
 *
 * When Mojang ships a new protocol ahead of Paper's pin (776), run this, commit
 * the dump + index entry, then ForwardTransformer dump-backs newer clients → 776.
 */
import fs from 'fs';
import path from 'path';
import { createRequire } from 'module';
import { fileURLToPath } from 'url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(__dirname, '..');
const require = createRequire(path.join(root, 'scripts/bench/bots/package.json'));
const md = require('minecraft-data');

const outRoot = path.join(root, 'src/main/resources/protocol/vanilla');
const indexPath = path.join(outRoot, 'index.json');

function usage() {
  console.error('Usage: node scripts/generate-protocol-dump.mjs <mcVersion|latest|--protocol N>');
  process.exit(2);
}

function parseArgs(argv) {
  if (argv.length < 1) usage();
  if (argv[0] === '--protocol') {
    const n = parseInt(argv[1], 10);
    if (!Number.isFinite(n)) usage();
    return { protocol: n };
  }
  return { version: argv[0] };
}

function resolveData(spec) {
  if (spec.protocol != null) {
    const versions = md.versions.filter((v) => v.type === 'pc' && v.version === spec.protocol);
    if (!versions.length) {
      throw new Error('No minecraft-data pc version for protocol ' + spec.protocol);
    }
    return md(versions[versions.length - 1].minecraftVersion);
  }
  let ver = spec.version;
  if (ver === 'latest') {
    const pcs = md.versions.filter((v) => v.type === 'pc' && v.releaseType === 'release');
    ver = pcs[pcs.length - 1].minecraftVersion;
  }
  return md(ver);
}

function mapperToEntries(mapperNode) {
  // ["mapper", { type: "varint", mappings: { "0x01": "spawn_entity", ... } }]
  if (!Array.isArray(mapperNode) || mapperNode[0] !== 'mapper') {
    return null;
  }
  const mappings = mapperNode[1]?.mappings || {};
  const out = {};
  for (const [idHex, name] of Object.entries(mappings)) {
    const id = parseInt(String(idHex), 16);
    if (!Number.isFinite(id) || !name) continue;
    const key = name.startsWith('minecraft:') ? name : 'minecraft:' + name;
    out[key] = { protocol_id: id };
  }
  return out;
}

function extractState(stateObj, direction) {
  if (!stateObj) return {};
  const side = direction === 'clientbound' ? stateObj.toClient : stateObj.toServer;
  if (!side?.types?.packet) return {};
  // packet: ["container", [{name:"name", type:["mapper", ...]}, {name:"params", ...}]]
  const container = side.types.packet;
  if (!Array.isArray(container) || container[0] !== 'container') return {};
  for (const field of container[1] || []) {
    if (field?.name === 'name' && Array.isArray(field.type)) {
      const entries = mapperToEntries(field.type);
      if (entries) return entries;
    }
  }
  return {};
}

function buildDump(data) {
  const proto = data.version?.version;
  const mc = data.version?.minecraftVersion || 'unknown';
  const p = data.protocol || {};
  return {
    protocol: proto,
    minecraftVersion: mc,
    generatedAt: new Date().toISOString(),
    source: 'minecraft-data',
    handshake: {
      clientbound: extractState(p.handshaking, 'clientbound'),
      serverbound: extractState(p.handshaking, 'serverbound'),
    },
    status: {
      clientbound: extractState(p.status, 'clientbound'),
      serverbound: extractState(p.status, 'serverbound'),
    },
    login: {
      clientbound: extractState(p.login, 'clientbound'),
      serverbound: extractState(p.login, 'serverbound'),
    },
    configuration: {
      clientbound: extractState(p.configuration, 'clientbound'),
      serverbound: extractState(p.configuration, 'serverbound'),
    },
    play: {
      clientbound: extractState(p.play, 'clientbound'),
      serverbound: extractState(p.play, 'serverbound'),
    },
  };
}

function labelFor(dump) {
  // Prefer version folder names matching existing layout
  const mc = dump.minecraftVersion || '';
  if (mc.startsWith('26.')) return mc;
  return mc || String(dump.protocol);
}

function updateIndex(label, dump, resource) {
  let index = { paperPinProtocol: 776, dumps: {}, generatedAt: null, note: null };
  if (fs.existsSync(indexPath)) {
    index = JSON.parse(fs.readFileSync(indexPath, 'utf8'));
  }
  index.generatedAt = new Date().toISOString();
  index.note = 'P4.10 — drop new Mojang/PC dumps here; PacketIdDump prefers this index over hard-coded switch.';
  index.paperPinProtocol = 776;
  index.dumps[String(dump.protocol)] = {
    label,
    resource,
    minecraftVersion: dump.minecraftVersion,
    playS2c: Object.keys(dump.play.clientbound || {}).length,
    playC2s: Object.keys(dump.play.serverbound || {}).length,
  };
  fs.writeFileSync(indexPath, JSON.stringify(index, null, 2) + '\n');
}

const spec = parseArgs(process.argv.slice(2));
const data = resolveData(spec);
const dump = buildDump(data);
if (!dump.protocol) {
  console.error('No protocol version in minecraft-data for', spec);
  process.exit(1);
}
const playCount = Object.keys(dump.play.clientbound || {}).length;
if (playCount < 10) {
  console.error('Dump looks empty (play S2C=' + playCount + '). Check minecraft-data version.');
  process.exit(1);
}

const label = labelFor(dump);
const dir = path.join(outRoot, label);
fs.mkdirSync(dir, { recursive: true });
const outFile = path.join(dir, 'packets.json');
fs.writeFileSync(outFile, JSON.stringify(dump, null, 2) + '\n');
const resource = 'protocol/vanilla/' + label + '/packets.json';
updateIndex(label, dump, resource);
console.log('Wrote', outFile);
console.log('protocol', dump.protocol, 'mc', dump.minecraftVersion,
  'playS2C', playCount, 'playC2S', Object.keys(dump.play.serverbound || {}).length);
console.log('Updated', indexPath);
