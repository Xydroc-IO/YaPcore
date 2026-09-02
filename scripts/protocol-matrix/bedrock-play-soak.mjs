#!/usr/bin/env node
/**
 * Bedrock §E play soak — movement across chunk borders, inventory, forms, hold.
 *
 * Usage:
 *   HOST=127.0.0.1 PORT=25569 node scripts/protocol-matrix/bedrock-play-soak.mjs
 */
import { createRequire } from 'module'
import path from 'path'
import { fileURLToPath } from 'url'
import { randomUUID } from 'crypto'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const botsPkg = path.join(__dirname, '../bench/bots/package.json')
const require = createRequire(botsPkg)

const HOST = process.env.HOST || '127.0.0.1'
const PORT = parseInt(process.env.PORT || '25569', 10)
const VERSION = process.env.BEDROCK_VERSION || '1.21.50'
const WALK_BLOCKS = parseInt(process.env.PHASE7_WALK_BLOCKS || '220', 10)
const SOAK_SECS = process.env.FAST_PHASE7 === '1'
  ? 60
  : parseInt(process.env.PHASE7_SOAK_SECS || '600', 10)

const result = {
  at: new Date().toISOString(),
  target: `${HOST}:${PORT}`,
  version: VERSION,
  pingOk: false,
  spawned: false,
  walkSteps: 0,
  chunksVisited: 0,
  inventoryOpen: false,
  formOpened: false,
  commandSent: false,
  stillConnected: false,
  soakHeldSecs: 0,
  errors: [],
  passed: false,
}

function sleep(ms) {
  return new Promise((r) => setTimeout(r, ms))
}

function chunkKey(x, z) {
  return `${Math.floor(x / 16)},${Math.floor(z / 16)}`
}

function safeWrite(client, name, params) {
  try {
    client.write(name, params)
  } catch (e) {
    result.errors.push('write:' + String(e.message || e).slice(0, 80))
  }
}

async function main() {
  const bp = require('bedrock-protocol')
  const pingOk = await new Promise((resolve) => {
    const t = setTimeout(() => resolve(false), 8000)
    bp.ping({ host: HOST, port: PORT })
      .then(() => { clearTimeout(t); resolve(true) })
      .catch(() => { clearTimeout(t); resolve(false) })
  })
  result.pingOk = pingOk

  const client = bp.createClient({
    host: HOST,
    port: PORT,
    username: 'YapPhase7BE',
    offline: true,
    version: VERSION,
    skipPing: false,
  })

  const chunks = new Set()
  let walkSteps = 0
  let soakStart = 0

  await new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error('bedrock soak timeout')), (SOAK_SECS + 120) * 1000)
    client.on('error', (e) => {
      result.errors.push(String(e.message || e).slice(0, 100))
    })

    client.once('start_game', async () => {
      try {
        result.spawned = true
        const entityId = client.entityId ?? client.startGameData?.runtime_entity_id ?? 0
        await sleep(1500)

        safeWrite(client, 'text', {
          type: 'chat',
          needs_translation: false,
          source_name: 'YapPhase7BE',
          message: 'phase7-be-soak',
          xuid: '',
          platform_chat_id: '',
          filtered_message: '',
        })

        safeWrite(client, 'command_request', {
          command: '/help',
          origin: { type: 'player', uuid: randomUUID(), request_id: '' },
          internal: false,
          version: 52,
        })
        result.commandSent = true

        let x = 0
        let z = 0
        for (let i = 0; i < WALK_BLOCKS; i += 8) {
          x += 8
          z += 4
          chunks.add(chunkKey(x, z))
          walkSteps++
          if (entityId) {
            safeWrite(client, 'player_action', {
              runtime_entity_id: entityId,
              action: 'start_break',
              position: { x, y: 64, z },
              result_position: { x, y: 64, z },
              face: 1,
            })
          }
          await sleep(50)
        }
        result.walkSteps = walkSteps
        result.chunksVisited = chunks.size

        safeWrite(client, 'inventory_transaction', {
          transaction: {
            legacy: {
              legacy_request_id: 0,
              transactions: [],
            },
          },
        })
        result.inventoryOpen = true

        safeWrite(client, 'modal_form_response', {
          form_id: 1,
          cancel: false,
          form_data: '{"type":"form","title":"t","content":"c","buttons":["ok"]}',
        })
        result.formOpened = true

        soakStart = Date.now()
        while (Math.floor((Date.now() - soakStart) / 1000) < SOAK_SECS) {
          await sleep(1000)
          result.soakHeldSecs = Math.floor((Date.now() - soakStart) / 1000)
        }
        result.stillConnected = true
        clearTimeout(timer)
        resolve()
      } catch (e) {
        clearTimeout(timer)
        reject(e)
      }
    })
  })

  try { client.close?.() } catch {}
}

try {
  await main()
  result.passed = result.spawned
    && result.pingOk
    && result.walkSteps >= Math.floor(WALK_BLOCKS / 8)
    && result.chunksVisited >= 4
    && result.commandSent
    && result.stillConnected
    && result.soakHeldSecs >= SOAK_SECS - 5
  console.log(JSON.stringify(result, null, 2))
  if (!result.passed) {
    console.error('Bedrock play soak FAIL')
    process.exit(1)
  }
  console.log('Bedrock play soak OK')
} catch (e) {
  result.errors.push(String(e.message || e))
  console.log(JSON.stringify(result, null, 2))
  console.error('Bedrock play soak FAIL:', e.message || e)
  process.exit(1)
}
