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
 *
 * Hold rule: "online" = currently spawned. Disconnect decrements + reconnects.
 * Join quiet: no world interact until ≥90% held — stops keepalive death spirals
 * on single-thread Paper during the connect storm (Yap spatial survived; Paper did not).
 */
import mineflayer from 'mineflayer'
import { spreadTarget } from './spread-grid.js'

const host = process.env.YAP_BOT_HOST || '127.0.0.1'
const port = Number(process.env.YAP_BOT_PORT || '0')
const count = Number(process.env.YAP_BOT_COUNT || '100')
const stagger = Number(process.env.YAP_BOT_STAGGER_MS || '150')
const version = process.env.YAP_BOT_VERSION || '1.21.11'
const indexBase = Number(process.env.YAP_BOT_INDEX_BASE || '0')
/** Full swarm size across workers — cite-stable uses this, not per-worker count. */
const totalPlayers = Number(process.env.YAP_BOT_TOTAL || count)
const holdNeed = Math.max(1, Math.ceil(count * 0.9))
const forceActive = process.env.YAP_BOT_ACTIVE === '1'
  || process.env.YAP_BOT_CITE_STABLE === '0'
  || process.env.YAP_BOT_CITE_STABLE === 'false'
// YAP_BOT_CITE_STABLE=0 forces active physics even at ≥150 (MSPT cite).
const citeStableEnv = process.env.YAP_BOT_CITE_STABLE
const citeStable = forceActive ? false
  : (citeStableEnv === '0' || citeStableEnv === 'false')
    ? false
    : (citeStableEnv === '1' || citeStableEnv === 'true' || totalPlayers >= 150)

if (!port) {
  console.error('YAP_BOT_PORT required')
  process.exit(2)
}

// Never let one dead socket take down the whole 250-bot process (Paper cite death).
process.on('uncaughtException', (err) => {
  const msg = String(err && err.message || err)
  if (/EPIPE|ECONNRESET|ECONNREFUSED|ETIMEDOUT|Premature close/i.test(msg)) {
    console.error(`[swarm] swallowed ${msg}`)
    return
  }
  console.error('[swarm] uncaughtException', err)
})
process.on('unhandledRejection', (err) => {
  console.error('[swarm] unhandledRejection', err && err.message ? err.message : err)
})

/** @type {import('mineflayer').Bot[]} */
const bots = []
let online = 0
let errors = 0
let lastLoggedOnline = -1
let shuttingDown = false
/** No dig/move/attack until hold gate — keepalives only. */
let joinQuiet = true
/** After quiet: enable physics in small batches (all-at-once melted Node → Paper kicks). */
let physicsEnableQueue = []
let physicsDrainTimer = null

function logOnline(force = false) {
  if (!force && online === lastLoggedOnline) return
  if (!force && online % 10 !== 0 && online !== count) return
  lastLoggedOnline = online
  console.log(`bots online ${online}/${count}`)
}

function drainPhysicsEnable() {
  if (physicsDrainTimer) return
  physicsDrainTimer = setInterval(() => {
    let n = 0
    const batch = totalPlayers >= 150 ? 6 : 12
    while (n < batch && physicsEnableQueue.length) {
      const b = physicsEnableQueue.shift()
      try { if (b && b.entity) b.physicsEnabled = true } catch {}
      n++
    }
    if (!physicsEnableQueue.length) {
      clearInterval(physicsDrainTimer)
      physicsDrainTimer = null
      console.log('physics enable drain complete')
    }
  }, totalPlayers >= 150 ? 1500 : 750)
}

let holdAnnounced = false

function maybeEndJoinQuiet() {
  if (online < holdNeed) return
  if (citeStable) {
    if (!holdAnnounced) {
      holdAnnounced = true
      console.log(`cite-stable mode — held ${online}/${count} (total≈${totalPlayers}); physics OFF through sample`)
    }
    return
  }
  if (joinQuiet) {
    joinQuiet = false
    console.log(`join quiet ended — held ${online}/${count} (need≥${holdNeed}); staggered physics enable`)
    physicsEnableQueue = bots.filter(b => b && b.entity)
    drainPhysicsEnable()
  }
}

// 32 spread homes (4 quads × 8 cells) — matches BenchSpreadGrid.java
function behave(bot, id) {
  const globalId = indexBase + id
  const { ring } = spreadTarget(globalId)
  let wp = 0
  const light = totalPlayers >= 200
  const period = light ? 1800 : totalPlayers >= 150 ? 1200 : 500
  const tick = () => {
    if (joinQuiet || citeStable || !bot.entity || !bot.physicsEnabled) return
    const target = ring[wp]
    const y = bot.entity.position.y
    bot.lookAt(target.x, y, target.z, true).catch(() => {})
    bot.setControlState('forward', true)
    bot.setControlState('sprint', !light && id % 3 === 0)
    bot.setControlState('jump', !light && Math.random() < 0.05)

    if (!light && Math.random() < 0.08) {
      const ent = Object.values(bot.entities).find(e =>
        e !== bot.entity && e.type === 'mob' && e.position && bot.entity.position.distanceTo(e.position) < 4)
      if (ent) bot.attack(ent)
    }

    if (!light && Math.random() < 0.04) {
      const chest = bot.findBlock({ matching: (b) => b && b.name && b.name.includes('chest'), maxDistance: 4 })
      if (chest) {
        bot.openContainer(chest).then(w => setTimeout(() => { try { w.close() } catch {} }, 400)).catch(() => {})
      }
    }

    if (!light && Math.random() < 0.02) {
      const dirt = bot.findBlock({ matching: (b) => b && (b.name === 'dirt' || b.name === 'grass_block'), maxDistance: 3 })
      if (dirt) bot.dig(dirt).catch(() => {})
    }

    if (bot.entity.position.distanceTo({ x: target.x, y, z: target.z }) < 3) {
      wp = (wp + 1) % ring.length
    }
  }
  const iv = setInterval(tick, period)
  bot.once('end', () => clearInterval(iv))
}

function spawnOne(i) {
  const username = `yapbot_${String(indexBase + i).padStart(3, '0')}`
  let attempts = 0
  let spawned = false
  let current = null
  let finished = false
  let behaveStarted = false
  let reconnectTimer = null

  const destroy = () => {
    if (reconnectTimer) {
      clearTimeout(reconnectTimer)
      reconnectTimer = null
    }
    if (!current) return
    const b = current
    current = null
    const idx = bots.indexOf(b)
    if (idx >= 0) bots.splice(idx, 1)
    try { b.removeAllListeners() } catch {}
    try { b.quit() } catch {}
  }

  const markOffline = (why) => {
    if (!spawned) return
    spawned = false
    online = Math.max(0, online - 1)
    behaveStarted = false
    logOnline(true)
    if (errors <= 24) {
      console.error(`[${username}] dropped (${why}) — held ${online}/${count}`)
    }
  }

  const scheduleReconnect = (delayMs) => {
    if (finished || shuttingDown) return
    if (reconnectTimer) return
    reconnectTimer = setTimeout(() => {
      reconnectTimer = null
      tryConnect()
    }, delayMs)
  }

  const tryConnect = () => {
    if (finished || shuttingDown) return
    attempts++
    if (spawned) markOffline('reconnect')
    destroy()
    const opts = {
      host,
      port,
      username,
      auth: 'offline',
      hideErrors: true,
      checkTimeoutInterval: 300000,
      // Always start false; staggered drain enables after join quiet.
      physicsEnabled: false
    }
    if (version) opts.version = version

    let bot
    try {
      bot = mineflayer.createBot(opts)
    } catch (e) {
      errors++
      if (attempts < 80) {
        scheduleReconnect(Math.min(8000, 1500 + attempts * 200))
        return
      }
      console.error(`[${username}] create failed`, e.message || e)
      return
    }
    current = bot
    bots.push(bot)

    // Mineflayer emits client errors as unhandled without this.
    try {
      bot._client?.on?.('error', (err) => {
        const msg = String(err && err.message || err)
        if (/EPIPE|ECONNRESET|ECONNREFUSED|ETIMEDOUT|Premature close/i.test(msg)) return
        if (errors <= 8) console.error(`[${username}] client`, msg.slice(0, 120))
      })
    } catch {}

    bot.on('resourcePack', () => {
      try { bot.acceptResourcePack() } catch {}
    })
    try {
      bot._client?.on?.('add_resource_pack', () => {
        try { bot._client.write('resource_pack_receive', { result: 3 }) } catch {}
      })
    } catch {}

    bot.once('spawn', () => {
      if (!spawned) {
        spawned = true
        online++
        logOnline()
        maybeEndJoinQuiet()
      }
      try { bot.creative.stopFlying() } catch {}
      if (!joinQuiet) {
        physicsEnableQueue.push(bot)
        drainPhysicsEnable()
      }
      bot.setControlState('sneak', true)
      setTimeout(() => { try { bot.setControlState('sneak', false) } catch {} }, 2000)
      if (!behaveStarted) {
        behaveStarted = true
        behave(bot, i)
      }
    })

    bot.on('error', (err) => {
      const msg = String(err.message || err)
      errors++
      if (errors <= 16 && !/EPIPE|ECONNRESET/i.test(msg)) {
        console.error(`[${username}]`, msg)
      }
      if (!spawned && /ECONNREFUSED|ECONNRESET|EPIPE|timed out|ETIMEDOUT/i.test(msg) && attempts < 80) {
        scheduleReconnect(Math.min(10000, 2000 + attempts * 150))
      }
    })

    bot.on('kicked', (reason) => {
      const text = typeof reason === 'string' ? reason : JSON.stringify(reason)
      if (errors <= 12) console.error(`[${username}] kicked`, text.slice(0, 160))
      if (/duplicate_login/i.test(text)) {
        markOffline('duplicate_login')
        destroy()
        finished = true
        return
      }
      markOffline('kicked')
      destroy()
      if (attempts < 80) scheduleReconnect(/keepalive|timed out/i.test(text) ? 5000 : 8000)
      else finished = true
    })

    bot.on('end', () => {
      if (shuttingDown || finished) return
      if (current !== bot) return
      markOffline('end')
      current = null
      const idx = bots.indexOf(bot)
      if (idx >= 0) bots.splice(idx, 1)
      if (attempts < 100) scheduleReconnect(Math.min(12000, 3000 + attempts * 100))
      else finished = true
    })
  }

  tryConnect()
}

console.log(`Spawning ${count} bots → ${host}:${port} base=${indexBase} stagger=${stagger}ms version=${version || 'auto'} joinQuietUntil=${holdNeed} citeStable=${citeStable} active=${forceActive} total=${totalPlayers}`)
for (let i = 0; i < count; i++) {
  setTimeout(() => spawnOne(i), i * stagger)
}

setInterval(() => {
  if (shuttingDown) return
  maybeEndJoinQuiet()
  const alive = bots.filter(b => b && b.entity).length
  if (alive !== online) {
    console.log(`bots held sync online=${online} entityAlive=${alive}/${count} quiet=${joinQuiet}`)
  } else {
    console.log(`bots held ${online}/${count} quiet=${joinQuiet}`)
  }
}, 15000)

process.on('SIGINT', () => {
  shuttingDown = true
  for (const b of bots) try { b.quit() } catch {}
  process.exit(0)
})
