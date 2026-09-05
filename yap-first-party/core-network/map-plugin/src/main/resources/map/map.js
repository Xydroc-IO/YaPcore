(function () {
  if (window.YAP_MAP_VIEW === '3d') {
    return;
  }

  var cfg = window.YAP_MAP_CONFIG || {};
  var params = new URLSearchParams(window.location.search);
  var world = params.get('world') || cfg.defaultWorld || 'world';
  var layer = params.get('layer') || cfg.defaultLayer || 'surface';
  var sampleRadius = cfg.sampleChunkRadius || 8;
  var originBlockX = cfg.originBlockX || 0;
  var originBlockZ = cfg.originBlockZ || 0;
  var worlds = Array.isArray(cfg.worlds) && cfg.worlds.length ? cfg.worlds : [world];
  var layers = Array.isArray(cfg.layers) && cfg.layers.length ? cfg.layers : ['surface'];
  if (layers.indexOf(layer) < 0) layer = layers[0];

  var worldSelect = document.getElementById('world');
  worlds.forEach(function (w) {
    var opt = document.createElement('option');
    opt.value = w;
    opt.textContent = w;
    if (w === world) opt.selected = true;
    worldSelect.appendChild(opt);
  });
  worldSelect.addEventListener('change', function () {
    params.set('world', worldSelect.value);
    window.location.search = params.toString();
  });

  var layerSelect = document.getElementById('layer');
  layers.forEach(function (l) {
    var opt = document.createElement('option');
    opt.value = l;
    opt.textContent = l;
    if (l === layer) opt.selected = true;
    layerSelect.appendChild(opt);
  });
  layerSelect.addEventListener('change', function () {
    params.set('layer', layerSelect.value);
    window.location.search = params.toString();
  });

  var maxZoom = (cfg.maxZoom != null ? cfg.maxZoom : 3);
  var map = L.map('map', {
    crs: L.CRS.Simple,
    minZoom: 0,
    maxZoom: maxZoom
  });

  var tileSize = 256;
  var pxPerBlock = tileSize / 16;
  var bounds = [[0, 0], [sampleRadius * tileSize, sampleRadius * tileSize]];
  map.setMaxBounds(bounds);
  map.fitBounds(bounds);

  // Leaflet zoom 0 = coarsest pyramid (our MAX_ZOOM); Leaflet max = detail (our zoom 0).
  // Path: /tiles/{world}/{layer}/{ourZoom}/{tx}_{ty}.png
  L.TileLayer.YaP = L.TileLayer.extend({
    getTileUrl: function (coords) {
      var ourZoom = maxZoom - coords.z;
      var scale = 1 << ourZoom;
      var tx = Math.floor(coords.x / scale);
      var ty = Math.floor(coords.y / scale);
      return '/tiles/' + world + '/' + layer + '/' + ourZoom + '/' + tx + '_' + ty + '.png';
    }
  });
  new L.TileLayer.YaP('', {
    tileSize: tileSize,
    minZoom: 0,
    maxZoom: maxZoom,
    noWrap: true,
    bounds: bounds,
    errorTileUrl: 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg=='
  }).addTo(map);

  map.setView([sampleRadius * tileSize / 2, sampleRadius * tileSize / 2], 0);

  var coordsEl = document.getElementById('coords');
  map.on('mousemove', function (e) {
    var blockX = Math.floor(e.latlng.lng / pxPerBlock) + originBlockX;
    var blockZ = Math.floor(e.latlng.lat / pxPerBlock) + originBlockZ;
    coordsEl.textContent = world + ' / ' + layer + '  x=' + blockX + '  z=' + blockZ;
  });

  var playerLayer = L.layerGroup().addTo(map);
  var npcLayer = L.layerGroup().addTo(map);
  var regionLayer = L.layerGroup().addTo(map);
  var claimLayer = L.layerGroup().addTo(map);
  var poiLayer = L.layerGroup().addTo(map);
  var pollMs = 5000;

  function toLatLng(x, z) {
    return [(z - originBlockZ) * pxPerBlock, (x - originBlockX) * pxPerBlock];
  }

  function inWorld(row) {
    return row && row.world && String(row.world).toLowerCase() === String(world).toLowerCase();
  }

  function poiIcon(icon) {
    var label = (icon === 'star') ? '★' : '●';
    return L.divIcon({
      className: 'yap-poi',
      html: '<span style="color:#e8c040;font-size:16px;text-shadow:0 0 2px #000">' + label + '</span>',
      iconSize: [16, 16],
      iconAnchor: [8, 8]
    });
  }

  function refreshMarkers() {
    fetch('/map/markers.json', { cache: 'no-store' })
      .then(function (r) { return r.ok ? r.json() : null; })
      .then(function (data) {
        if (!data) return;
        if (typeof data.originBlockX === 'number') originBlockX = data.originBlockX;
        if (typeof data.originBlockZ === 'number') originBlockZ = data.originBlockZ;
        if (typeof data.pollSeconds === 'number' && data.pollSeconds >= 2) {
          pollMs = data.pollSeconds * 1000;
        }
        playerLayer.clearLayers();
        npcLayer.clearLayers();
        regionLayer.clearLayers();
        claimLayer.clearLayers();
        poiLayer.clearLayers();
        if (data.showPlayers !== false && Array.isArray(data.players)) {
          data.players.filter(inWorld).forEach(function (p) {
            var m = L.circleMarker(toLatLng(p.x, p.z), {
              radius: 6,
              color: '#1a1a1a',
              weight: 1,
              fillColor: '#3d9eff',
              fillOpacity: 0.95
            });
            m.bindTooltip(p.name || 'player', { permanent: false, direction: 'top' });
            playerLayer.addLayer(m);
          });
        }
        if (data.showNpcs && Array.isArray(data.npcs)) {
          data.npcs.filter(inWorld).forEach(function (n) {
            var m = L.circleMarker(toLatLng(n.x, n.z), {
              radius: 5,
              color: '#1a1a1a',
              weight: 1,
              fillColor: '#f0a030',
              fillOpacity: 0.9
            });
            m.bindTooltip(n.name || n.id || 'npc', { permanent: false, direction: 'top' });
            npcLayer.addLayer(m);
          });
        }
        if (data.showRegions && Array.isArray(data.regions)) {
          data.regions.filter(inWorld).forEach(function (r) {
            var boundsRect = [
              toLatLng(r.minX, r.minZ),
              toLatLng(r.maxX, r.maxZ)
            ];
            var rect = L.rectangle(boundsRect, {
              color: '#40c070',
              weight: 1,
              fillColor: '#40c070',
              fillOpacity: 0.15
            });
            rect.bindTooltip(r.name || 'region', { permanent: false });
            regionLayer.addLayer(rect);
          });
        }
        if (data.showClaims && Array.isArray(data.claims)) {
          data.claims.filter(inWorld).forEach(function (c) {
            var boundsRect = [
              toLatLng(c.minX, c.minZ),
              toLatLng(c.maxX, c.maxZ)
            ];
            var col = c.color || '#c060e0';
            var rect = L.rectangle(boundsRect, {
              color: col,
              weight: 1,
              fillColor: col,
              fillOpacity: 0.18
            });
            rect.bindTooltip(c.name || 'claim', { permanent: false });
            claimLayer.addLayer(rect);
          });
        }
        if (data.showPois !== false && Array.isArray(data.pois)) {
          data.pois.filter(inWorld).forEach(function (p) {
            var m = L.marker(toLatLng(p.x, p.z), { icon: poiIcon(p.icon) });
            m.bindTooltip(p.name || 'poi', { permanent: false, direction: 'top' });
            poiLayer.addLayer(m);
          });
        }
      })
      .catch(function () { /* markers optional */ });
  }

  refreshMarkers();
  setInterval(refreshMarkers, pollMs);
})();
