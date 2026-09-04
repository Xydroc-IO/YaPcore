(function () {
  var map = L.map('map', {
    crs: L.CRS.Simple,
    minZoom: 0,
    maxZoom: 0
  });

  var world = new URLSearchParams(window.location.search).get('world') || 'world';
  var tileSize = 256;
  var sampleRadius = (window.YAP_MAP_CONFIG && window.YAP_MAP_CONFIG.sampleChunkRadius) || 8;
  var pxPerBlock = tileSize / 16;
  var bounds = [[0, 0], [sampleRadius * tileSize, sampleRadius * tileSize]];
  map.setMaxBounds(bounds);
  map.fitBounds(bounds);

  L.tileLayer('/tiles/' + world + '/0/{x}_{y}.png', {
    tileSize: tileSize,
    noWrap: true,
    bounds: bounds,
    errorTileUrl: 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg=='
  }).addTo(map);

  map.setView([sampleRadius * tileSize / 2, sampleRadius * tileSize / 2], 0);

  var playerLayer = L.layerGroup().addTo(map);
  var npcLayer = L.layerGroup().addTo(map);
  var regionLayer = L.layerGroup().addTo(map);
  var pollMs = 5000;

  function toLatLng(x, z) {
    return [z * pxPerBlock, x * pxPerBlock];
  }

  function inWorld(row) {
    return row && row.world && String(row.world).toLowerCase() === String(world).toLowerCase();
  }

  function refreshMarkers() {
    fetch('/map/markers.json', { cache: 'no-store' })
      .then(function (r) { return r.ok ? r.json() : null; })
      .then(function (data) {
        if (!data) return;
        if (typeof data.pollSeconds === 'number' && data.pollSeconds >= 2) {
          pollMs = data.pollSeconds * 1000;
        }
        playerLayer.clearLayers();
        npcLayer.clearLayers();
        regionLayer.clearLayers();
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
      })
      .catch(function () { /* markers optional */ });
  }

  refreshMarkers();
  setInterval(refreshMarkers, pollMs);
})();
