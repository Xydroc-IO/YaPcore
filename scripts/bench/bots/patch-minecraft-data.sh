#!/usr/bin/env bash
# Paper/Folia 26.2 uses protocol 776. minecraft-data indexes 26.2 but data.js and
# pc/26.2/ are missing until upstream ships them — clone 26.1 and patch version.
set -euo pipefail
DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
MD="$DIR/node_modules/minecraft-data"
PC="$MD/minecraft-data/data/pc"
DATA_JS="$MD/data.js"
CHUNK_JS="$DIR/node_modules/prismarine-chunk/src/index.js"

if [[ ! -d "$PC/26.1" ]]; then
  echo "patch-minecraft-data: skip (run npm install in bots/ first)" >&2
  exit 0
fi

if [[ ! -d "$PC/26.2" ]] || [[ -L "$PC/26.2" ]]; then
  rm -rf "$PC/26.2"
  cp -a "$PC/26.1" "$PC/26.2"
fi

cat >"$PC/26.2/version.json" <<'EOF'
{
  "version": 776,
  "minecraftVersion": "26.2",
  "majorVersion": "26.2",
  "releaseType": "release"
}
EOF

BOTS_DIR="$DIR" node --input-type=module <<'NODE'
import fs from 'node:fs'
import path from 'node:path'

const dir = process.env.BOTS_DIR
const dataJs = path.join(dir, 'node_modules/minecraft-data/data.js')
let src = fs.readFileSync(dataJs, 'utf8')
if (!src.includes("    '26.2': {")) {
  const anchor = "    '26.1': {"
  const start = src.indexOf(anchor)
  if (start < 0) {
    console.error('patch-minecraft-data: 26.1 anchor missing in data.js')
    process.exit(1)
  }
  let depth = 0
  let end = -1
  for (let i = start + anchor.length - 1; i < src.length; i++) {
    const ch = src[i]
    if (ch === '{') depth++
    else if (ch === '}') {
      depth--
      if (depth === 0) {
        end = i + 1
        break
      }
    }
  }
  if (end < 0) {
    console.error('patch-minecraft-data: could not find end of 26.1 block')
    process.exit(1)
  }
  const block = src.slice(start, end)
  const block26 = block.replaceAll('/26.1/', '/26.2/').replace("'26.1':", "'26.2':")
  src = src.slice(0, end) + ',\n' + block26 + src.slice(end)
  fs.writeFileSync(dataJs, src)
  console.log('patch-minecraft-data: added data.js pc/26.2 (protocol 776, cloned from 26.1)')
}

const mfVersion = path.join(dir, 'node_modules/mineflayer/lib/version.js')
let vf = fs.readFileSync(mfVersion, 'utf8')
if (!vf.includes("'26.2'")) {
  vf = vf.replace("'26.1']", "'26.1', '26.2']")
  fs.writeFileSync(mfVersion, vf)
  console.log('patch-minecraft-data: mineflayer testedVersions += 26.2')
}

const chunkJs = path.join(dir, 'node_modules/prismarine-chunk/src/index.js')
let cf = fs.readFileSync(chunkJs, 'utf8')
if (!cf.includes('26.2:')) {
  cf = cf.replace(
    '    26.1: require(\'./pc/1.18/chunk\')',
    "    26.1: require('./pc/1.18/chunk'),\n    26.2: require('./pc/1.18/chunk')"
  )
  fs.writeFileSync(chunkJs, cf)
  console.log('patch-minecraft-data: prismarine-chunk += 26.2')
}

const physFeatures = path.join(dir, 'node_modules/prismarine-physics/lib/features.json')
let pf = fs.readFileSync(physFeatures, 'utf8')
if (!pf.includes('"26.2"')) {
  pf = pf.replaceAll('"26.1"', '"26.1", "26.2"')
  fs.writeFileSync(physFeatures, pf)
  console.log('patch-minecraft-data: prismarine-physics features += 26.2')
}
NODE

echo "patch-minecraft-data: pc/26.2 ready"
