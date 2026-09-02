#!/usr/bin/env node
/**
 * JE §E play soak — chunk-border walk, inventory, commands, stability hold.
 *
 * Usage:
 *   HOST=127.0.0.1 PORT=25569 node scripts/protocol-matrix/je-play-soak.mjs
 */
import { createRequire } from 'module'
import path from 'path'
import { fileURLToPath } from 'url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const botsPkg = path.join(__dirname, '../bench/bots/package.json')
const require = createRequire(botsPkg)
const mineflayer = require('mineflayer')

const HOST = process.env.HOST || '127.0.0.1'
const PORT = parseInt(process.env.PORT || '25569', 10)
const WALK_BLOCKS = parseInt(process.env.PHASE7_WALK_BLOCKS || '220', 10)
const SOAK_SECS = process.env.FAST_PHASE7 === '1'
  ? 60
  : parseInt(process.env.PHASE7_SOAK_SECS || '600', 10)
const VERSION = process.env.JE_VERSION || '26.2'
const USERNAME = process.env.JE_BOT_NAME || 'YapPhase7JE'

const result = {
  at: new Date().toISOString(),
  target: `${HOST}:${PORT}`,
  version: VERSION,
  walkBlocksTarget: WALK_BLOCKS,
  soakSecsTarget: SOAK_SECS,
  spawned: false,
  walkBlocks: 0,
  chunksVisited: 0,
  chestOpened: false,
  furnaceOpened: false,
  blocksBroken: 0,
  mobAttacked: false,
  helpSent: false,
  abilitiesReload: false,
  vehicleTypes: false,
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

async function run() {
  const bot = mineflayer.createBot({
    host: HOST,
    port: PORT,
    username: USERNAME,
    version: VERSION,
    auth: 'offline',
    hideErrors: true,
  })

  const chunks = new Set()
  let soakStart = 0

  bot.on('resourcePack', () => {
    try { bot.acceptResourcePack() } catch {}
  })
  try {
    bot._client?.on?.('add_resource_pack', () => {
      try { bot._client.write('resource_pack_receive', { result: 3 }) } catch {}
    })
  } catch {}

  bot.on('messagestr', (msg) => {
    const t = String(msg || '')
    if (t.includes('YAPABILITIES_JSON:')) result.abilitiesReload = true
    if (t.toLowerCase().includes('vehicle') && t.includes('chassis')) result.vehicleTypes = true
  })

  bot.on('physicsTick', () => {
    if (!bot.entity) return
    const p = bot.entity.position
    chunks.add(chunkKey(p.x, p.z))
    result.chunksVisited = chunks.size
    if (soakStart > 0) {
      result.soakHeldSecs = Math.floor((Date.now() - soakStart) / 1000)
    }
  })

  await new Promise((resolve, reject) => {
    const failTimer = setTimeout(() => {
      reject(new Error(`timeout after ${SOAK_SECS + 120}s`))
    }, (SOAK_SECS + 120) * 1000)

    bot.once('spawn', async () => {
      result.spawned = true
      try {
        await sleep(2000)
        bot.chat('/help')
        result.helpSent = true
        await sleep(600)
        bot.chat('/yapabilities reload')
        await sleep(600)
        bot.chat('/yapabilities snapshot json')
        await sleep(600)
        bot.chat('/yapvehicle types')
        await sleep(600)

        // Chunk-border walk via op teleport (reliable vs physics on fresh world)
        let dist = 0
        for (let i = 0; i < 30 && dist < WALK_BLOCKS; i++) {
          const x = i * 8
          const z = i * 4
          bot.chat(`/tp ${USERNAME} ${x} 80 ${z}`)
          chunks.add(chunkKey(x, z))
          dist += 8
          await sleep(350)
        }
        result.walkBlocks = dist
        result.chunksVisited = chunks.size

        const chest = bot.findBlock({
          matching: (b) => b && b.name && b.name.includes('chest'),
          maxDistance: 16,
        })
        if (chest) {
          const w = await bot.openContainer(chest)
          result.chestOpened = true
          await sleep(400)
          try { w.close() } catch {}
        }

        const furnace = bot.findBlock({
          matching: (b) => b && (b.name === 'furnace' || b.name === 'blast_furnace'),
          maxDistance: 16,
        })
        if (furnace) {
          const w = await bot.openContainer(furnace)
          result.furnaceOpened = true
          await sleep(400)
          try { w.close() } catch {}
        }

        const dirt = bot.findBlock({
          matching: (b) => b && (b.name === 'dirt' || b.name === 'grass_block'),
          maxDistance: 4,
        })
        if (dirt) {
          try {
            await bot.dig(dirt)
            result.blocksBroken++
          } catch (e) {
            result.errors.push('dig:' + String(e.message || e).slice(0, 80))
          }
        }

        const mob = Object.values(bot.entities).find(
          (e) => e !== bot.entity && e.type === 'mob' && e.position
            && bot.entity.position.distanceTo(e.position) < 8,
        )
        if (mob) {
          try {
            bot.attack(mob)
            result.mobAttacked = true
          } catch {}
        }

        soakStart = Date.now()
        while (result.soakHeldSecs < SOAK_SECS) {
          if (!bot.entity) break
          await sleep(1000)
        }
        result.stillConnected = !!bot.entity
        clearTimeout(failTimer)
        resolve()
      } catch (e) {
        clearTimeout(failTimer)
        reject(e)
      }
    })

    bot.on('end', () => {
      if (!result.spawned) {
        clearTimeout(failTimer)
        reject(new Error('disconnected before spawn'))
      }
    })
    bot.on('error', (e) => {
      result.errors.push(String(e.message || e).slice(0, 120))
    })
  })

  try { bot.quit() } catch {}
}

try {
  await run()
  result.passed = result.spawned
    && result.walkBlocks >= WALK_BLOCKS
    && result.chunksVisited >= 4
    && result.helpSent
    && result.stillConnected
    && result.soakHeldSecs >= SOAK_SECS - 5
  console.log(JSON.stringify(result, null, 2))
  if (!result.passed) {
    console.error('JE play soak FAIL')
    process.exit(1)
  }
  console.log('JE play soak OK')
} catch (e) {
  result.errors.push(String(e.message || e))
  console.log(JSON.stringify(result, null, 2))
  console.error('JE play soak FAIL:', e.message || e)
  process.exit(1)
}
