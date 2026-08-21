#!/usr/bin/env node
/**
 * High-pop Mineflayer swarm — movement, look, combat, inventory, border crossing.
 *
 * Env:
 *   YAP_BOT_HOST (default 127.0.0.1)
 *   YAP_BOT_PORT (required)
 *   YAP_BOT_COUNT (default 100)
 *   YAP_BOT_VERSION (default 1.21.11)
 *   YAP_BOT_STAGGER_MS (default 150)
 */
import mineflayer from 'mineflayer'

const host = process.env.YAP_BOT_HOST || '127.0.0.1'
const port = Number(process.env.YAP_BOT_PORT || '0')
const count = Number(process.env.YAP_BOT_COUNT || '100')
const stagger = Number(process.env.YAP_BOT_STAGGER_MS || '150')
const version = process.env.YAP_BOT_VERSION || '1.21.11'

if (!port) {
  console.error('YAP_BOT_PORT required')
  process.exit(2)
}

const WAYPOINTS = [
  { x: 40, z: 40 },
  { x: -40, z: 40 },
  { x: 40, z: -40 },
  { x: -40, z: -40 },
  { x: 8, z: 40 },
  { x: 40, z: 8 },
  { x: 8, z: -40 },
  { x: -40, z: 8 },
  { x: 0, z: 0 }
]

const bots = []
let online = 0
let errors = 0

function behave(bot, id) {
  let wp = id % WAYPOINTS.length
  const tick = () => {
    if (!bot.entity) return
    const target = WAYPOINTS[wp]
    const y = bot.entity.position.y
    bot.lookAt(target.x, y, target.z, true).catch(() => {})
    bot.setControlState('forward', true)
    bot.setControlState('sprint', id % 3 === 0)
    bot.setControlState('jump', Math.random() < 0.05)

    if (Math.random() < 0.08) {
      const ent = Object.values(bot.entities).find(e =>
        e !== bot.entity && e.type === 'mob' && e.position && bot.entity.position.distanceTo(e.position) < 4)
      if (ent) bot.attack(ent)
    }

    if (Math.random() < 0.04) {
      const chest = bot.findBlock({ matching: (b) => b && b.name && b.name.includes('chest'), maxDistance: 4 })
      if (chest) {
        bot.openContainer(chest).then(w => setTimeout(() => { try { w.close() } catch {} }, 400)).catch(() => {})
      }
    }

    if (Math.random() < 0.02) {
      const dirt = bot.findBlock({ matching: (b) => b && (b.name === 'dirt' || b.name === 'grass_block'), maxDistance: 3 })
      if (dirt) bot.dig(dirt).catch(() => {})
    }

    if (bot.entity.position.distanceTo({ x: target.x, y, z: target.z }) < 3) {
      wp = (wp + 1) % WAYPOINTS.length
    }
  }
  const iv = setInterval(tick, 500)
  bot.once('end', () => clearInterval(iv))
}

function spawnOne(i) {
  const username = `yapbot_${String(i).padStart(3, '0')}`
  let attempts = 0
  let counted = false
  let current = null
  let finished = false

  const destroy = () => {
    if (!current) return
    const b = current
    current = null
    try { b.removeAllListeners() } catch {}
    try { b.quit() } catch {}
  }

  const tryConnect = () => {
    if (finished) return
    attempts++
    destroy()
    const opts = {
      host,
      port,
      username,
      auth: 'offline',
      hideErrors: true,
      checkTimeoutInterval: 120000 // ms — keepalives under heavy join pressure
    }
    if (version) opts.version = version

    let bot
    try {
      bot = mineflayer.createBot(opts)
    } catch (e) {
      errors++
      if (attempts < 40) {
        setTimeout(tryConnect, 2500)
        return
      }
      console.error(`[${username}] create failed`, e.message || e)
      return
    }
    current = bot
    bots.push(bot)
    // Highpop must not stall on forced packs (YaP product default).
    bot.on('resourcePack', (url) => {
      try { bot.acceptResourcePack() } catch {}
    })
    try {
      bot._client?.on?.('add_resource_pack', () => {
        try { bot._client.write('resource_pack_receive', { result: 3 }) } catch {}
      })
    } catch {}

    bot.once('spawn', () => {
      if (!counted) {
        counted = true
        online++
        if (online % 10 === 0 || online === count) {
          console.log(`bots online ${online}/${count}`)
        }
      }
      // Kill anti-fly kicks on uneven terrain during chunk load
      try { bot.creative.stopFlying() } catch {}
      bot.setControlState('sneak', true)
      setTimeout(() => { try { bot.setControlState('sneak', false) } catch {} }, 2000)
      behave(bot, i)
    })

    bot.on('error', (err) => {
      const msg = String(err.message || err)
      // Only retry pre-spawn connect failures — never stack duplicate sessions.
      if (!counted && /ECONNREFUSED|ECONNRESET|timed out/i.test(msg) && attempts < 60) {
        setTimeout(tryConnect, 3000)
        return
      }
      errors++
      if (errors <= 12) console.error(`[${username}]`, msg)
    })

    bot.on('kicked', (reason) => {
      const text = typeof reason === 'string' ? reason : JSON.stringify(reason)
      if (errors <= 8) console.error(`[${username}] kicked`, text.slice(0, 120))
      // duplicate_login = another session with same name; do NOT reconnect immediately
      if (/duplicate_login/i.test(text)) {
        finished = true
        return
      }
      // One delayed retry only if we never counted a stable spawn
      if (!counted && attempts < 20) {
        setTimeout(tryConnect, 10000)
      } else {
        finished = true
      }
    })

    bot.on('end', () => {
      if (!counted && !finished && attempts < 40) {
        setTimeout(tryConnect, 5000)
      }
    })
  }

  tryConnect()
}

console.log(`Spawning ${count} bots → ${host}:${port} stagger=${stagger}ms version=${version || 'auto'}`)
for (let i = 0; i < count; i++) {
  setTimeout(() => spawnOne(i), i * stagger)
}

process.on('SIGINT', () => {
  for (const b of bots) try { b.quit() } catch {}
  process.exit(0)
})
