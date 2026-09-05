import * as THREE from 'three';
import { OrbitControls } from 'three/addons/controls/OrbitControls.js';

if (window.YAP_MAP_VIEW === '3d') {
  init3d();
}

function init3d() {
  const cfg = window.YAP_MAP_CONFIG || {};
  const params = new URLSearchParams(window.location.search);
  let world = params.get('world') || cfg.defaultWorld || 'world';
  const worlds = Array.isArray(cfg.worlds) && cfg.worlds.length ? cfg.worlds : [world];
  const meshLayers = Array.isArray(cfg.meshLayers) && cfg.meshLayers.length
    ? cfg.meshLayers
    : ['full'];
  let layer = params.get('layer') || cfg.meshDefaultLayer || meshLayers[0];
  if (meshLayers.indexOf(layer) < 0) layer = meshLayers[0];

  let originBlockX = cfg.originBlockX || 0;
  let originBlockZ = cfg.originBlockZ || 0;
  let originChunkX = cfg.originChunkX != null ? cfg.originChunkX : (originBlockX >> 4);
  let originChunkZ = cfg.originChunkZ != null ? cfg.originChunkZ : (originBlockZ >> 4);
  const sampleRadius = cfg.sampleChunkRadius || 8;
  const preferBinary = cfg.meshBinary !== false;
  let maxLod = cfg.meshMaxLod != null ? cfg.meshMaxLod : 2;

  const MAX_CONCURRENT = 4;
  const LOAD_RADIUS_CHUNKS = Math.max(sampleRadius, 8);
  const UNLOAD_RADIUS_CHUNKS = LOAD_RADIUS_CHUNKS + 2;
  /** Camera distance (chunks) thresholds for LOD0 / LOD1 / LOD2. */
  const LOD1_DIST = 4;
  const LOD2_DIST = 8;

  const worldSelect = document.getElementById('world');
  if (worldSelect && worldSelect.options.length === 0) {
    worlds.forEach((w) => {
      const opt = document.createElement('option');
      opt.value = w;
      opt.textContent = w;
      if (w === world) opt.selected = true;
      worldSelect.appendChild(opt);
    });
    worldSelect.addEventListener('change', () => {
      params.set('world', worldSelect.value);
      params.set('view', '3d');
      params.set('layer', layer);
      window.location.search = params.toString();
    });
  }

  const layerSelect = document.getElementById('layer');
  const layerWrap = document.getElementById('layer-wrap');
  if (layerWrap) layerWrap.classList.remove('hidden');
  if (layerSelect) {
    layerSelect.innerHTML = '';
    meshLayers.forEach((l) => {
      const opt = document.createElement('option');
      opt.value = l;
      opt.textContent = l;
      if (l === layer) opt.selected = true;
      layerSelect.appendChild(opt);
    });
    layerSelect.addEventListener('change', () => {
      params.set('layer', layerSelect.value);
      params.set('view', '3d');
      window.location.search = params.toString();
    });
  }

  const container = document.getElementById('view3d');
  const coordsEl = document.getElementById('coords');
  const scene = new THREE.Scene();
  scene.background = new THREE.Color(0x101418);

  const camera = new THREE.PerspectiveCamera(60, 1, 0.1, 4000);
  const renderer = new THREE.WebGLRenderer({ antialias: true });
  renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2));
  container.appendChild(renderer.domElement);

  const controls = new OrbitControls(camera, renderer.domElement);
  controls.enableDamping = true;
  controls.dampingFactor = 0.08;
  controls.maxPolarAngle = Math.PI * 0.49;

  const hemi = new THREE.HemisphereLight(0xddeeff, 0x334455, 0.85);
  scene.add(hemi);
  const sun = new THREE.DirectionalLight(0xffffff, 0.65);
  sun.position.set(40, 80, 20);
  scene.add(sun);

  const terrainGroup = new THREE.Group();
  scene.add(terrainGroup);
  const playerGroup = new THREE.Group();
  scene.add(playerGroup);

  const centerX = originBlockX + (sampleRadius * 16) / 2;
  const centerZ = originBlockZ + (sampleRadius * 16) / 2;
  camera.position.set(centerX + 80, 120, centerZ + 80);
  controls.target.set(centerX, 64, centerZ);
  controls.update();

  function resize() {
    const w = container.clientWidth || window.innerWidth;
    const h = container.clientHeight || (window.innerHeight - 44);
    camera.aspect = w / Math.max(1, h);
    camera.updateProjectionMatrix();
    renderer.setSize(w, h, false);
  }
  window.addEventListener('resize', resize);
  resize();

  let pollMs = 5000;
  let markerTimer = null;
  let legacyFlatMeshes = false;
  /** @type {Map<string, {mesh: THREE.Object3D, cx: number, cz: number, lod: number}>} */
  const loadedChunks = new Map();
  /** @type {Map<string, object>} */
  const manifestByKey = new Map();
  /** @type {Set<string>} */
  const inFlight = new Set();
  let fetchQueue = [];
  let activeFetches = 0;
  let totalBoxes = 0;
  let frustum = new THREE.Frustum();
  let projScreen = new THREE.Matrix4();
  const chunkBox = new THREE.Box3();
  const _v = new THREE.Vector3();

  function setStatus(msg) {
    if (coordsEl) coordsEl.textContent = msg;
  }

  function chunkKey(cx, cz) {
    return cx + ',' + cz;
  }

  function disposeObject(obj) {
    if (!obj) return;
    obj.traverse((child) => {
      if (child.geometry) child.geometry.dispose();
      if (child.material) {
        if (Array.isArray(child.material)) child.material.forEach((m) => m.dispose());
        else child.material.dispose();
      }
    });
  }

  function meshBaseUrl() {
    if (legacyFlatMeshes) {
      return '/meshes/' + encodeURIComponent(world) + '/';
    }
    return '/meshes/' + encodeURIComponent(world) + '/' + encodeURIComponent(layer) + '/';
  }

  function pickLod(distChunks) {
    const cap = Math.max(0, Math.min(2, maxLod | 0));
    if (cap >= 2 && distChunks >= LOD2_DIST) return 2;
    if (cap >= 1 && distChunks >= LOD1_DIST) return 1;
    return 0;
  }

  function flightKey(cx, cz, lod) {
    return chunkKey(cx, cz) + '@' + lod;
  }

  /**
   * Decode YMSH binary (little-endian).
   * @returns {{v:number,u:number,cx:number,cz:number,n:number,d:number[]}|null}
   */
  function decodeYmesh(buffer) {
    if (!buffer || buffer.byteLength < 24) return null;
    const view = new DataView(buffer);
    const magic = String.fromCharCode(
      view.getUint8(0), view.getUint8(1), view.getUint8(2), view.getUint8(3)
    );
    if (magic !== 'YMSH') return null;
    let o = 4;
    const version = view.getUint16(o, true); o += 2;
    o += 2; // flags
    const cx = view.getInt32(o, true); o += 4;
    const cz = view.getInt32(o, true); o += 4;
    const n = view.getUint32(o, true); o += 4;
    const unit = view.getUint32(o, true); o += 4;
    const d = [];
    for (let i = 0; i < n * 7; i++) {
      if (o + 4 > buffer.byteLength) break;
      d.push(view.getInt32(o, true));
      o += 4;
    }
    return { v: version || 2, u: unit || 1000, cx, cz, n, d };
  }

  /**
   * Build one InstancedMesh for a chunk from packed d[].
   * v1: [lx,y,lz,rgb]  v2: [lx,y,lz,sx,sy,sz,rgb] with optional u (milliblocks)
   */
  function buildChunkMesh(data, entry) {
    const version = data.v != null ? data.v : 1;
    const d = Array.isArray(data.d) ? data.d : [];
    const cx = data.cx != null ? data.cx : entry.cx;
    const cz = data.cz != null ? data.cz : entry.cz;
    const baseX = cx * 16;
    const baseZ = cz * 16;
    let unit = data.u != null ? data.u : 1;
    // Heuristic: milliblock packs have sizes >> 16
    if (version >= 2 && unit === 1 && d.length >= 7) {
      let maxSz = 0;
      for (let i = 3; i < d.length; i += 7) {
        maxSz = Math.max(maxSz, d[i] || 0, d[i + 1] || 0, d[i + 2] || 0);
      }
      if (maxSz > 32) unit = 1000;
    }
    const stride = version >= 2 ? 7 : 4;
    const boxes = [];
    for (let i = 0; i + stride - 1 < d.length; i += stride) {
      if (version >= 2) {
        const sx = Math.max(1 / unit, d[i + 3] / unit);
        const sy = Math.max(1 / unit, d[i + 4] / unit);
        const sz = Math.max(1 / unit, d[i + 5] / unit);
        boxes.push({
          x: baseX + d[i] / unit,
          y: d[i + 1] / unit,
          z: baseZ + d[i + 2] / unit,
          sx,
          sy,
          sz,
          rgb: d[i + 6] & 0xffffff
        });
      } else {
        boxes.push({
          x: baseX + d[i],
          y: d[i + 1],
          z: baseZ + d[i + 2],
          sx: 1,
          sy: 1,
          sz: 1,
          rgb: d[i + 3] & 0xffffff
        });
      }
    }
    const count = boxes.length;
    if (count === 0) return null;
    const geo = new THREE.BoxGeometry(1, 1, 1);
    const mat = new THREE.MeshLambertMaterial({ vertexColors: false });
    const mesh = new THREE.InstancedMesh(geo, mat, count);
    mesh.instanceMatrix.setUsage(THREE.StaticDrawUsage);
    const color = new THREE.Color();
    const matrix = new THREE.Matrix4();
    const quat = new THREE.Quaternion();
    const scale = new THREE.Vector3();
    const pos = new THREE.Vector3();
    for (let i = 0; i < count; i++) {
      const b = boxes[i];
      pos.set(b.x + b.sx * 0.5, b.y + b.sy * 0.5, b.z + b.sz * 0.5);
      scale.set(b.sx, b.sy, b.sz);
      matrix.compose(pos, quat, scale);
      mesh.setMatrixAt(i, matrix);
      color.setHex(b.rgb);
      mesh.setColorAt(i, color);
    }
    mesh.instanceMatrix.needsUpdate = true;
    if (mesh.instanceColor) mesh.instanceColor.needsUpdate = true;
    mesh.userData.boxCount = count;
    mesh.userData.cx = cx;
    mesh.userData.cz = cz;
    mesh.frustumCulled = true;
    return mesh;
  }

  function unloadChunk(key) {
    const entry = loadedChunks.get(key);
    if (!entry) return;
    terrainGroup.remove(entry.mesh);
    totalBoxes -= entry.mesh.userData.boxCount || 0;
    disposeObject(entry.mesh);
    loadedChunks.delete(key);
  }

  function chunkUrls(entry, lod) {
    const stem = entry.cx + '_' + entry.cz;
    const lodPrefix = legacyFlatMeshes ? '' : ('lod' + lod + '/');
    const ymeshRel = entry.ymesh
      ? String(entry.ymesh).replace(/lod0\//, 'lod' + lod + '/')
      : (lodPrefix + stem + '.ymesh');
    const jsonRel = entry.file
      ? String(entry.file).replace(/lod0\//, 'lod' + lod + '/')
      : (lodPrefix + stem + '.json');
    // Legacy flat / pre-lod filenames
    if (legacyFlatMeshes) {
      return {
        ymesh: meshBaseUrl() + stem + '.ymesh',
        json: meshBaseUrl() + (entry.file || (stem + '.json'))
      };
    }
    return {
      ymesh: meshBaseUrl() + ymeshRel,
      json: meshBaseUrl() + jsonRel
    };
  }

  function fetchChunkData(entry, lod) {
    const urls = chunkUrls(entry, lod);
    if (preferBinary && !legacyFlatMeshes) {
      return fetch(urls.ymesh, { cache: 'no-store' })
        .then((res) => (res.ok ? res.arrayBuffer() : null))
        .then((buf) => {
          if (buf) {
            const decoded = decodeYmesh(buf);
            if (decoded) return decoded;
          }
          return fetch(urls.json, { cache: 'no-store' })
            .then((r) => (r.ok ? r.json() : null));
        });
    }
    return fetch(urls.json, { cache: 'no-store' })
      .then((res) => (res.ok ? res.json() : null));
  }

  function pumpFetchQueue() {
    while (activeFetches < MAX_CONCURRENT && fetchQueue.length > 0) {
      const job = fetchQueue.shift();
      if (!job) break;
      const key = chunkKey(job.cx, job.cz);
      const fk = flightKey(job.cx, job.cz, job.lod);
      if (inFlight.has(fk)) continue;
      const existing = loadedChunks.get(key);
      if (existing && existing.lod === job.lod) continue;
      inFlight.add(fk);
      activeFetches++;
      fetchChunkData(job, job.lod)
        .then((data) => {
          if (!data) return;
          const prev = loadedChunks.get(key);
          if (prev && prev.lod === job.lod) return;
          const mesh = buildChunkMesh(data, job);
          if (!mesh) return;
          if (prev) unloadChunk(key);
          mesh.userData.lod = job.lod;
          terrainGroup.add(mesh);
          loadedChunks.set(key, { mesh, cx: job.cx, cz: job.cz, lod: job.lod });
          totalBoxes += mesh.userData.boxCount || 0;
        })
        .catch(() => {})
        .finally(() => {
          inFlight.delete(fk);
          activeFetches--;
          pumpFetchQueue();
          updateStatusLine();
        });
    }
  }

  function enqueueChunk(entry, lod) {
    const key = chunkKey(entry.cx, entry.cz);
    const existing = loadedChunks.get(key);
    if (existing && existing.lod === lod) return;
    const fk = flightKey(entry.cx, entry.cz, lod);
    if (inFlight.has(fk)) return;
    if (fetchQueue.some((j) => j.cx === entry.cx && j.cz === entry.cz && j.lod === lod)) return;
    fetchQueue.push({ ...entry, lod });
  }

  function cameraChunk() {
    const t = controls.target;
    return {
      cx: Math.floor(t.x / 16),
      cz: Math.floor(t.z / 16)
    };
  }

  function chunkDist(cx, cz, cam) {
    const dx = cx - cam.cx;
    const dz = cz - cam.cz;
    return Math.sqrt(dx * dx + dz * dz);
  }

  function availableLod(entry, wanted) {
    const lods = Array.isArray(entry.lods) ? entry.lods : [0, 1, 2];
    for (let l = wanted; l >= 0; l--) {
      if (lods.indexOf(l) >= 0 || lods.indexOf(String(l)) >= 0) return l;
    }
    return 0;
  }

  function updateStreaming() {
    if (manifestByKey.size === 0) return;
    const cam = cameraChunk();
    projScreen.multiplyMatrices(camera.projectionMatrix, camera.matrixWorldInverse);
    frustum.setFromProjectionMatrix(projScreen);

    for (const [key, entry] of loadedChunks) {
      const dx = entry.cx - cam.cx;
      const dz = entry.cz - cam.cz;
      if (dx * dx + dz * dz > UNLOAD_RADIUS_CHUNKS * UNLOAD_RADIUS_CHUNKS) {
        unloadChunk(key);
      }
    }

    const candidates = [];
    for (const entry of manifestByKey.values()) {
      const dx = entry.cx - cam.cx;
      const dz = entry.cz - cam.cz;
      const dist2 = dx * dx + dz * dz;
      if (dist2 > LOAD_RADIUS_CHUNKS * LOAD_RADIUS_CHUNKS) continue;
      const dist = Math.sqrt(dist2);
      const wanted = availableLod(entry, pickLod(dist));
      const key = chunkKey(entry.cx, entry.cz);
      const loaded = loadedChunks.get(key);
      if (loaded && loaded.lod === wanted) continue;
      const minX = entry.cx * 16;
      const minZ = entry.cz * 16;
      chunkBox.min.set(minX, -64, minZ);
      chunkBox.max.set(minX + 16, 320, minZ + 16);
      const inFrustum = frustum.intersectsBox(chunkBox);
      candidates.push({ entry, dist2, inFrustum, lod: wanted });
    }
    candidates.sort((a, b) => {
      if (a.inFrustum !== b.inFrustum) return a.inFrustum ? -1 : 1;
      return a.dist2 - b.dist2;
    });
    fetchQueue = [];
    for (const c of candidates) {
      enqueueChunk(c.entry, c.lod);
    }
    pumpFetchQueue();
  }

  function updateStatusLine() {
    let lodCounts = [0, 0, 0];
    for (const e of loadedChunks.values()) {
      const l = e.lod | 0;
      if (l >= 0 && l <= 2) lodCounts[l]++;
    }
    setStatus(
      world + ' / ' + layer + ' / 3D  loaded=' + loadedChunks.size
        + '/' + manifestByKey.size + '  boxes=' + totalBoxes
        + '  lod=' + lodCounts.join('/')
        + '  queue=' + fetchQueue.length
    );
  }

  async function loadManifest() {
    setStatus(world + ' / ' + layer + ' / 3D — loading manifest…');
    manifestByKey.clear();
    fetchQueue = [];
    for (const key of [...loadedChunks.keys()]) {
      unloadChunk(key);
    }
    totalBoxes = 0;
    try {
      const manRes = await fetch(meshBaseUrl() + 'manifest.json', { cache: 'no-store' });
      if (!manRes.ok) {
        const legacy = await fetch(
          '/meshes/' + encodeURIComponent(world) + '/manifest.json',
          { cache: 'no-store' }
        );
        if (!legacy.ok) {
          setStatus(world + ' / ' + layer + ' / 3D — no manifest (run /yapmap render)');
          return;
        }
        await applyManifest(await legacy.json(), true);
        return;
      }
      await applyManifest(await manRes.json(), false);
    } catch (e) {
      setStatus(world + ' / ' + layer + ' / 3D — load failed');
      console.warn('YaPMap 3D manifest failed', e);
    }
  }

  async function applyManifest(manifest, legacyFlat) {
    legacyFlatMeshes = !!legacyFlat;
    if (typeof manifest.originChunkX === 'number') {
      originChunkX = manifest.originChunkX;
      originBlockX = originChunkX * 16;
    }
    if (typeof manifest.originChunkZ === 'number') {
      originChunkZ = manifest.originChunkZ;
      originBlockZ = originChunkZ * 16;
    }
    if (typeof manifest.maxLod === 'number') {
      maxLod = manifest.maxLod;
    }
    const chunks = Array.isArray(manifest.chunks) ? manifest.chunks : [];
    for (const entry of chunks) {
      if (entry == null || entry.cx == null || entry.cz == null) continue;
      manifestByKey.set(chunkKey(entry.cx, entry.cz), entry);
    }
    const midX = originBlockX + (sampleRadius * 16) / 2;
    const midZ = originBlockZ + (sampleRadius * 16) / 2;
    controls.target.set(midX, 64, midZ);
    camera.position.set(midX + 80, 120, midZ + 80);
    controls.update();
    if (legacyFlat) {
      setStatus(world + ' / 3D — legacy v1 mesh path; re-render for layered LOD v2');
    }
    updateStreaming();
    updateStatusLine();
  }

  function clearPlayers() {
    while (playerGroup.children.length) {
      const c = playerGroup.children.pop();
      disposeObject(c);
    }
  }

  /** Marker poll only updates player spheres — never rebuilds terrain. */
  function refreshMarkers() {
    fetch('/map/markers.json', { cache: 'no-store' })
      .then((r) => (r.ok ? r.json() : null))
      .then((data) => {
        if (!data) return;
        if (typeof data.pollSeconds === 'number' && data.pollSeconds >= 2) {
          const next = data.pollSeconds * 1000;
          if (next !== pollMs) {
            pollMs = next;
            if (markerTimer) {
              clearInterval(markerTimer);
              markerTimer = setInterval(refreshMarkers, pollMs);
            }
          }
        }
        clearPlayers();
        if (data.showPlayers === false || !Array.isArray(data.players)) return;
        data.players
          .filter((p) => p && p.world && String(p.world).toLowerCase() === String(world).toLowerCase())
          .forEach((p) => {
            const geo = new THREE.SphereGeometry(0.6, 12, 12);
            const mat = new THREE.MeshBasicMaterial({ color: 0x3d9eff });
            const sphere = new THREE.Mesh(geo, mat);
            sphere.position.set(p.x, (p.y != null ? p.y : 70) + 1.2, p.z);
            sphere.userData.name = p.name || 'player';
            playerGroup.add(sphere);
          });
      })
      .catch(() => {});
  }

  loadManifest();
  refreshMarkers();
  markerTimer = setInterval(refreshMarkers, pollMs);

  let streamAccum = 0;
  function animate() {
    requestAnimationFrame(animate);
    controls.update();
    streamAccum++;
    if (streamAccum % 15 === 0) {
      updateStreaming();
    }
    _v.copy(controls.target);
    if (coordsEl && streamAccum % 30 === 0) {
      updateStatusLine();
    }
    renderer.render(scene, camera);
  }
  animate();
}
