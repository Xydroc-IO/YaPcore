#!/usr/bin/env node
/**
 * Generate Bedrock start_game itemstates JSON from minecraft-data.
 * Usage: node scripts/generate-bedrock-itemstates.mjs [version]
 * Default version: 1.21.50
 */
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';
import { createRequire } from 'module';

const require = createRequire(import.meta.url);
const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(__dirname, '..');
const version = process.argv[2] || '1.21.50';

const mdRoot = path.join(
  root,
  'scripts/bench/bots/node_modules/minecraft-data/minecraft-data/data'
);
const dataPaths = JSON.parse(fs.readFileSync(path.join(mdRoot, 'dataPaths.json'), 'utf8'));
const be = dataPaths.bedrock?.[version];
if (!be?.items) {
  console.error('No bedrock items path for', version);
  process.exit(1);
}
const itemsPath = path.join(mdRoot, be.items, 'items.json');
const items = JSON.parse(fs.readFileSync(itemsPath, 'utf8'));
const out = items.map((it) => ({
  name: it.name.startsWith('minecraft:') ? it.name : `minecraft:${it.name}`,
  runtime_id: it.id,
  component_based: false,
}));
const destDir = path.join(root, 'src/main/resources/protocol/bedrock', version);
fs.mkdirSync(destDir, { recursive: true });
const dest = path.join(destDir, 'itemstates.json');
fs.writeFileSync(dest, JSON.stringify(out) + '\n');
console.log('Wrote', out.length, 'itemstates →', dest);
