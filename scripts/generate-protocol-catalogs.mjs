#!/usr/bin/env node
/**
 * Generate per-ProtocolBand item/block/entity catalogs from Prismarine minecraft-data.
 * Run from repo root: node scripts/generate-protocol-catalogs.mjs
 * Requires: cd scripts/bench/bots && npm i minecraft-data
 */
import fs from 'fs';
import path from 'path';
import { createRequire } from 'module';
import { fileURLToPath } from 'url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(__dirname, '..');
const require = createRequire(path.join(root, 'scripts/bench/bots/package.json'));
const md = require('minecraft-data');
const outDir = path.join(root, 'src/main/resources/protocol/catalogs');
fs.mkdirSync(outDir, { recursive: true });

const BANDS = [
  ['V1_8', '1.8'], ['V1_9', '1.9'], ['V1_12', '1.12.2'], ['V1_13', '1.13.2'],
  ['V1_16', '1.16.5'], ['V1_17', '1.18.2'], ['V1_19', '1.19.4'], ['V1_20_2', '1.20.2'],
  ['V1_21', '1.21.1'], ['V1_21_6', '1.21.4'], ['V1_21_11', '1.21.4'],
  ['V26_1', '1.21.4'], ['V26_2', '1.21.4'],
];

const index = { generatedAt: new Date().toISOString(), source: 'minecraft-data (PrismarineJS)', bands: {} };
for (const [band, ver] of BANDS) {
  const d = md(ver);
  const items = {}, itemsByName = {}, blocks = {}, blocksByName = {}, entities = {}, entitiesByName = {};
  for (const it of d.itemsArray || []) {
    const name = (it.name || '').replace(/^minecraft:/, '').toLowerCase();
    items[String(it.id)] = name;
    if (!(name in itemsByName)) itemsByName[name] = it.id;
  }
  for (const b of d.blocksArray || []) {
    const name = (b.name || '').replace(/^minecraft:/, '').toLowerCase();
    blocks[String(b.id)] = name;
    if (!(name in blocksByName)) blocksByName[name] = b.id;
  }
  // Objects first, then mobs/players overwrite shared legacy type ids (1.8 collision).
  const entityEntries = [...(d.entitiesArray || [])].sort((a, b) => {
    const rank = (e) => (e.type === 'mob' || e.type === 'player' ? 1 : 0);
    return rank(a) - rank(b);
  });
  const NAME_ALIASES = {
    pigzombie: 'zombified_piglin', lavaslime: 'magma_cube', mushroomcow: 'mooshroom',
    snowman: 'snow_golem', ozelot: 'ocelot', villagergolem: 'iron_golem',
    entityhorse: 'horse', witherboss: 'wither', enderdragon: 'ender_dragon',
    cavespider: 'cave_spider', endercrystal: 'end_crystal', primedtnt: 'tnt',
    fallingsand: 'falling_block', thrownegg: 'egg', thrownenderpearl: 'ender_pearl',
    eyeofendersignal: 'eye_of_ender', thrownpotion: 'potion', thrownexpbottle: 'experience_bottle',
    fireworksrocketentity: 'firework_rocket', minecartrideable: 'minecart',
    fishingfloat: 'fishing_bobber',
  };
  for (const e of entityEntries) {
    let name = (e.name || '').replace(/^minecraft:/, '').toLowerCase().replace(/\s+/g, '_');
    name = NAME_ALIASES[name] || name;
    const id = e.id != null ? e.id : e.internalId;
    if (id == null) continue;
    entities[String(id)] = name;
    if (!(name in entitiesByName)) entitiesByName[name] = id;
  }
  const proto = d.version?.version ?? null;
  const sectionFormat = (proto != null && proto < 393) || parseFloat(ver) < 1.13 ? 'legacy' : 'paletted';
  const catalog = {
    band, minecraftVersion: ver, protocol: proto,
    itemCount: Object.keys(items).length, blockCount: Object.keys(blocks).length, entityCount: Object.keys(entities).length,
    chunk: { mcVersion: ver, protocol: proto, sectionFormat },
    items, itemsByName, blocks, blocksByName, entities, entitiesByName,
  };
  fs.writeFileSync(path.join(outDir, band + '.json'), JSON.stringify(catalog));
  index.bands[band] = { file: band + '.json', minecraftVersion: ver, protocol: proto,
    items: catalog.itemCount, blocks: catalog.blockCount, entities: catalog.entityCount, sectionFormat };
  console.log(band, ver, catalog.itemCount, catalog.blockCount, catalog.entityCount);
}
fs.writeFileSync(path.join(outDir, 'index.json'), JSON.stringify(index, null, 2));
console.log('Wrote', outDir);
