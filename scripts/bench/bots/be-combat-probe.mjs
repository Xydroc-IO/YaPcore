import { createClient } from 'bedrock-protocol'
import http from 'http'
import fs from 'fs'

const TOKEN = process.env.TOKEN || fs.readFileSync('/home/xydroc/Desktop/YaPcore/config/server.properties','utf8')
  .split('\n').find(l => l.startsWith('web-dashboard-token=')).split('=')[1].trim()

function apiCommand(cmd) {
  return new Promise((resolve, reject) => {
    const body = JSON.stringify({ command: cmd })
    const req = http.request({
      host: '127.0.0.1', port: 8080, path: '/api/command', method: 'POST',
      headers: {
        Authorization: `Bearer ${TOKEN}`,
        'Content-Type': 'application/json',
        'Content-Length': Buffer.byteLength(body)
      }
    }, res => {
      let d = ''
      res.on('data', c => { d += c })
      res.on('end', () => resolve({ status: res.statusCode, body: d }))
    })
    req.on('error', reject)
    req.write(body)
    req.end()
  })
}

const entities = new Map()
let playerPos = { x: 7.5, y: 63, z: 2.5 }
let spawned = false
let attacked = false
let selfRuntime = null

const client = createClient({
  host: '127.0.0.1',
  port: 19132,
  username: 'BeCombatBot',
  offline: true,
  version: '1.26.40',
})

function emptyItem() {
  return {
    network_id: 0,
    count: 0,
    metadata: 0,
    has_stack_id: false,
    block_runtime_id: 0,
    extra: { has_nbt: 0, can_place_on: [], can_destroy: [] }
  }
}

function tryAttack() {
  if (attacked || !spawned) return
  let best = null, bestD = 1e9
  for (const [id, e] of entities) {
    if (selfRuntime != null && Number(id) === Number(selfRuntime)) continue
    const dx = e.x - playerPos.x, dy = e.y - playerPos.y, dz = e.z - playerPos.z
    const d = Math.sqrt(dx * dx + dy * dy + dz * dz)
    if (d < bestD) { bestD = d; best = { id, ...e, d } }
  }
  if (!best || best.d > 8) {
    console.log('wait entity tracked=', entities.size, 'pos', playerPos, 'bestD', best ? best.d : null)
    return
  }
  attacked = true
  console.log('ATTACK runtime=', best.id, 'type=', best.type, 'dist=', best.d.toFixed(2), best)
  const pkt = {
    transaction: {
      legacy: { legacy_request_id: 0 },
      transaction_type: 'item_use_on_entity',
      actions: [],
      transaction_data: {
        entity_runtime_id: best.id,
        action_type: 'attack',
        hotbar_slot: 0,
        held_item: emptyItem(),
        player_pos: { x: playerPos.x, y: playerPos.y, z: playerPos.z },
        click_pos: { x: best.x, y: best.y + 0.9, z: best.z }
      }
    }
  }
  try {
    client.queue('inventory_transaction', pkt)
    console.log('queued inventory_transaction')
  } catch (e) {
    console.log('queue fail', e.message)
    try { client.write('inventory_transaction', pkt); console.log('write ok') } catch (e2) {
      console.log('write fail', e2.message)
    }
  }
  try {
    client.queue('animate', { action_id: 'swing_arm', runtime_entity_id: selfRuntime || 0 })
  } catch (e) {
    console.log('animate fail', e.message)
  }
}

client.on('error', e => console.log('ERR', e.message || e))
client.on('kick', p => console.log('KICK', JSON.stringify(p)))
client.on('close', () => console.log('CLOSE'))
client.on('join', () => console.log('JOIN'))

client.on('start_game', p => {
  console.log('START_GAME pos', p.player_position, 'runtime', p.runtime_entity_id)
  selfRuntime = p.runtime_entity_id
  if (p.player_position) {
    playerPos = { x: p.player_position.x, y: p.player_position.y, z: p.player_position.z }
  }
})

client.on('spawn', async () => {
  console.log('SPAWN client.entityId=', client.entityId)
  spawned = true
  selfRuntime = selfRuntime ?? client.entityId ?? client.runtimeEntityId
  try {
    client.queue('set_local_player_as_initialized', {
      runtime_entity_id: selfRuntime || 0
    })
    console.log('sent set_local_player_as_initialized runtime=', selfRuntime)
  } catch (e) {
    console.log('init fail', e.message)
  }

  await new Promise(r => setTimeout(r, 3000))
  const px = Math.floor(playerPos.x)
  const py = Math.floor(playerPos.y)
  const pz = Math.floor(playerPos.z)
  console.log('summon at', px + 1, py, pz)
  console.log(await apiCommand(`summon cow ${px + 1} ${py} ${pz} {NoAI:1b,Silent:1b}`))
  console.log(await apiCommand('execute as BeCombatBot at @s run summon cow ~1.5 ~ ~ {NoAI:1b,Silent:1b}'))

  for (let i = 0; i < 24 && !attacked; i++) {
    await new Promise(r => setTimeout(r, 500))
    tryAttack()
  }
  if (!attacked) {
    console.log('FAILED: no entity to attack. entities=', [...entities.entries()].slice(0, 10))
  }
  await new Promise(r => setTimeout(r, 2500))
  console.log('combat:', await apiCommand('yapfloodgate combat'))
  try { client.close() } catch {}
  process.exit(attacked ? 0 : 2)
})

client.on('move_player', p => {
  const rid = p.runtime_id ?? p.runtime_entity_id
  if (selfRuntime != null && Number(rid) === Number(selfRuntime) && p.position) {
    playerPos = { x: p.position.x, y: p.position.y, z: p.position.z }
  }
})

function track(name, p) {
  const id = p.runtime_id ?? p.runtime_entity_id ?? p.entity_id_self ?? p.entity_runtime_id
  if (id == null) return
  const pos = p.position || p.pos || {}
  const type = p.entity_type ?? p.identifier ?? p.type ?? name
  entities.set(Number(id), {
    type,
    x: Number(pos.x ?? 0),
    y: Number(pos.y ?? 0),
    z: Number(pos.z ?? 0)
  })
  console.log('ENT', name, 'id=', id, 'type=', type, 'pos=', pos.x, pos.y, pos.z, 'n=', entities.size)
  tryAttack()
}

for (const n of ['add_entity', 'add_player', 'add_item_entity', 'add_painting', 'add_mob', 'spawn_entity', 'add_actor']) {
  client.on(n, p => track(n, p))
}

client.on('packet', ({ name, params }) => {
  if (name === 'add_entity' || name === 'add_player' || name === 'add_item_entity' || name === 'add_actor') {
    track(name, params)
  }
  if (name === 'play_status' || name === 'disconnect') {
    console.log('PKT', name, params?.status || params?.message || '')
  }
})

setTimeout(() => {
  console.log('GLOBAL TIMEOUT')
  try { client.close() } catch {}
  process.exit(1)
}, 50000)
