#!/usr/bin/env node
/**
 * Regenerate Bedrock block catalogs for 1.21.50:
 *   - block_runtime_ids.json     Material → defaultState hash
 *   - block_state_hashes.json    JE BlockData.getAsString() → hashed runtime id
 *
 * Usage: node scripts/generate-bedrock-block-runtime-ids.mjs
 */
import { createRequire } from 'module'
import fs from 'fs'
import path from 'path'
import { fileURLToPath } from 'url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const root = path.join(__dirname, '..')
const require = createRequire(path.join(root, 'scripts/bench/bots/package.json'))
const registry = require('prismarine-registry')('bedrock_1.21.50')

const mdRoot = path.join(root, 'scripts/bench/bots/node_modules/minecraft-data/minecraft-data/data')
const j2bPath = path.join(mdRoot, 'bedrock/1.21.42/blocksJ2B.json')
const j2b = JSON.parse(fs.readFileSync(j2bPath, 'utf8'))

const outMat = {}
const aliases = {
  GRASS: 'grass_block',
  CAVE_AIR: 'air',
  VOID_AIR: 'air',
  SNOW: 'snow_layer',
}

for (const [name, block] of Object.entries(registry.blocksByName)) {
  outMat[name.toUpperCase()] = block.defaultState | 0
}
for (const [from, to] of Object.entries(aliases)) {
  const b = registry.blocksByName[to]
  if (b) outMat[from] = b.defaultState | 0
}

function propValue(v) {
  if (v && typeof v === 'object' && 'value' in v) return v.value
  return v
}

function stateKey(name, props) {
  const n = name.startsWith('minecraft:') ? name : `minecraft:${name}`
  const keys = Object.keys(props).sort()
  if (keys.length === 0) return `${n}[]`
  return `${n}[${keys.map((k) => `${k}=${props[k]}`).join(',')}]`
}

/** Register BE name+props → hash; also bool aliases for 0/1 bit fields. */
function putBe(map, name, props, hash) {
  const plain = {}
  for (const [k, v] of Object.entries(props)) plain[k] = propValue(v)
  const keys = [stateKey(name, plain)]
  const alt = { ...plain }
  let changed = false
  for (const [k, v] of Object.entries(alt)) {
    if (v === 0 || v === 1) {
      alt[k] = v === 1 ? 'true' : 'false'
      changed = true
    } else if (v === true || v === false) {
      alt[k] = v ? '1' : '0'
      changed = true
    }
  }
  if (changed) keys.push(stateKey(name, alt))
  for (const k of keys) map.set(k, hash)
}

const beToHash = new Map()
for (const [name, block] of Object.entries(registry.blocksByName)) {
  const states = registry.blockStates.filter((s) => s.name === name)
  for (let i = 0; i < states.length; i++) {
    const hash = (block.minStateId + i) | 0
    if (hash > block.maxStateId) break
    const props = {}
    for (const [k, v] of Object.entries(states[i].states || {})) props[k] = propValue(v)
    putBe(beToHash, name, props, hash)
  }
}

const outStates = {}
let hit = 0
let miss = 0
for (const [je, be] of Object.entries(j2b)) {
  const h = beToHash.get(be)
  if (h != null) {
    outStates[je] = h
    hit++
  } else {
    miss++
    // Fallback: strip props → defaultState of BE block name
    const m = /^minecraft:([a-z0-9_]+)/i.exec(be)
    if (m) {
      const b = registry.blocksByName[m[1]]
      if (b) outStates[je] = b.defaultState | 0
    }
  }
}

const destDir = path.join(root, 'src/main/resources/protocol/bedrock/1.21.50')
fs.mkdirSync(destDir, { recursive: true })
const matDest = path.join(destDir, 'block_runtime_ids.json')
const stateDest = path.join(destDir, 'block_state_hashes.json')
fs.writeFileSync(matDest, JSON.stringify(outMat))
fs.writeFileSync(stateDest, JSON.stringify(outStates))
console.log('wrote', matDest, 'materials', Object.keys(outMat).length)
console.log('wrote', stateDest, 'jeStates', Object.keys(outStates).length, 'j2bHit', hit, 'j2bMiss', miss)
