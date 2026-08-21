#!/usr/bin/env node
/** Single-bot join probe with packet/state logging. Env: HOST PORT VERSION */
import { createRequire } from 'module'
import path from 'path'
import { fileURLToPath } from 'url'

const require = createRequire(path.join(path.dirname(fileURLToPath(import.meta.url)), '../bench/bots/package.json'))
const mc = require('minecraft-protocol')

const host = process.env.HOST || '127.0.0.1'
const port = Number(process.env.PORT || '25571')
const version = process.env.VERSION || '1.21.11'
const username = process.env.USER || 'via_probe_01'

const client = mc.createClient({
  host,
  port,
  username,
  auth: 'offline',
  version,
  hideErrors: false,
  checkTimeoutInterval: 60000
})

const t0 = Date.now()
const stamp = () => `+${Date.now() - t0}ms`

client.on('connect', () => console.log(stamp(), 'tcp connect'))
client.on('state', (s) => console.log(stamp(), 'state →', s))
client.on('login', () => console.log(stamp(), 'login event'))
client.on('playerJoin', () => console.log(stamp(), 'playerJoin'))
client.on('spawn', () => console.log(stamp(), 'SPAWN'))
client.on('end', (r) => console.log(stamp(), 'end', r || ''))
client.on('error', (e) => console.log(stamp(), 'error', e.message || e))
client.on('kick_disconnect', (p) => console.log(stamp(), 'kick_disconnect', JSON.stringify(p)))
client.on('disconnect', (p) => console.log(stamp(), 'disconnect', JSON.stringify(p)))
client.on('packet', (data, meta) => {
  if (!meta || meta.name === 'keep_alive' || meta.name === 'bundle_delimiter') return
  const names = new Set([
    'compress', 'success', 'login', 'finish_configuration', 'select_known_packs',
    'registry_data', 'tags', 'declare_commands', 'login_plugin_request',
    'cookie_request', 'store_cookie', 'custom_payload', 'transfer', 'start_configuration'
  ])
  if (names.has(meta.name) || meta.state === 'login' || meta.state === 'configuration') {
    console.log(stamp(), `pkt ${meta.state}/${meta.name}`, meta.state === 'play' ? '' : JSON.stringify(data).slice(0, 120))
  }
})

setTimeout(() => {
  console.log(stamp(), 'timeout — state=', client.state)
  try { client.end() } catch {}
  process.exit(client.state === 'play' ? 0 : 2)
}, 20000)
