#!/usr/bin/env node
/**
 * 32-cell spread grid — must match BenchSpreadGrid.java (4 quads × 8 cells).
 */
const BASE = 64
const STEP = 16
// Chunks -4..-6 are inside the carved section. Six chunks further west is -10..-12.
const WEST_CLEAR_OF_CUT = 6 * STEP
const QUAD_SIGN = [[1, 1], [-1, 1], [1, -1], [-1, -1]]
const CELLS = [[0, 0], [1, 0], [2, 0], [0, 1], [1, 1], [2, 1], [0, 2], [1, 2]]

/** @returns {{x:number,z:number}[]} */
export function spreadHomes() {
  const out = []
  for (const [sx, sz] of QUAD_SIGN) {
    for (const [cx, cz] of CELLS) {
      let x = sx * (BASE + cx * STEP)
      if (sx < 0) x -= WEST_CLEAR_OF_CUT
      out.push({ x, z: sz * (BASE + cz * STEP) })
    }
  }
  return out
}

/** Home + local patrol ring for one bot id. */
export function spreadTarget(globalId) {
  const homes = spreadHomes()
  const home = homes[globalId % homes.length]
  const ring = [
    { x: home.x, z: home.z },
    { x: home.x + 6, z: home.z },
    { x: home.x, z: home.z + 6 },
    { x: home.x + 6, z: home.z + 6 },
  ]
  return { home, ring }
}
