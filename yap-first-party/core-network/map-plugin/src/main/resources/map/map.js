(function () {
  if (window.YAP_MAP_VIEW === '3d') {
    return;
  }

  var cfg = window.YAP_MAP_CONFIG || {};
  var params = new URLSearchParams(window.location.search);
  var world = params.get('world') || cfg.defaultWorld || 'world';
  var layer = params.get('layer') || cfg.defaultLayer || 'surface';
  var instance = params.get('instance') || '';
  var sampleRadius = cfg.sampleChunkRadius || 8;
  var gridX = cfg.gridChunksX || sampleRadius;
  var gridZ = cfg.gridChunksZ || sampleRadius;
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
  var cover = 1 << maxZoom;
  var map = L.map('map', {
    crs: L.CRS.Simple,
    minZoom: 0,
    maxZoom: maxZoom
  });

  // One latlng unit per overview tile. Zoom 0 fits the grid; zoom max is one tile per chunk.
  var pxPerBlock = 1 / (cover * 16);
  var bounds = [[0, 0], [gridZ / cover, gridX / cover]];

  function withInstance(url) {
    if (!instance) return url;
    return url + (url.indexOf('?') >= 0 ? '&' : '?') + 'instance=' + encodeURIComponent(instance);
  }

  // Leaflet zoom 0 = coarsest pyramid; Leaflet max = chunk tiles.
  // CRS.Simple tile Y is negative — flip it before indexing files.
  L.TileLayer.YaP = L.TileLayer.extend({
    getTileUrl: function (coords) {
      var x = coords.x;
      var y = coords.y < 0 ? (-coords.y - 1) : coords.y;
      var ourZoom = maxZoom - coords.z;
      if (ourZoom < 0) ourZoom = 0;
      if (ourZoom > maxZoom) ourZoom = maxZoom;
      var scale = 1 << ourZoom;
      var tx = Math.floor(x / scale);
      var ty = Math.floor(y / scale);
      if (tx < 0 || ty < 0) {
        return 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==';
      }
      return withInstance('/tiles/' + world + '/' + layer + '/' + ourZoom + '/' + tx + '_' + ty + '.png');
    }
  });
  new L.TileLayer.YaP('', {
    tileSize: 256,
    minZoom: 0,
    maxZoom: maxZoom,
    noWrap: true,
    bounds: bounds,
    errorTileUrl: 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg=='
  }).addTo(map);
  var coordsEl = document.getElementById('coords');
  function zoomToFit(attempt) {
    map.invalidateSize(true);
    var size = map.getSize();
    if ((size.x < 50 || size.y < 50) && attempt < 20) {
      setTimeout(function () { zoomToFit(attempt + 1); }, 50);
      return;
    }
    var z = 0;
    var w = (gridX / cover) * 256;
    var h = (gridZ / cover) * 256;
    while (z < maxZoom && (w * 2) <= (size.x - 24) && (h * 2) <= (size.y - 24)) {
      z++;
      w *= 2;
      h *= 2;
    }
    map.setView([bounds[1][0] / 2, bounds[1][1] / 2], z);
  }
  zoomToFit(0);
  window.addEventListener('resize', function () { zoomToFit(0); });
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
    fetch(withInstance('/map/markers.json'), { cache: 'no-store' })
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
