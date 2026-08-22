(function () {
  var map = L.map('map', {
    crs: L.CRS.Simple,
    minZoom: 0,
    maxZoom: 0
  });

  var world = new URLSearchParams(window.location.search).get('world') || 'world';
  var tileSize = 256;
  var sampleRadius = 8;
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
})();
