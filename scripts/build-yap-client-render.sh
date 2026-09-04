#!/usr/bin/env bash
# Build YaP client render stack → single yap-visuals.jar (Sodium + Iris + shaders).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="${ROOT}/dist/client-mods"
mkdir -p "$OUT"

echo "==> Fetch official Sodium (PolyForm Shield pin)"
"${ROOT}/scripts/fetch-sodium.sh"

echo "==> Build YaP Iris (LGPL fork)"
(
  cd "${ROOT}/client/yap-iris"
  ./gradlew --no-daemon :fabric:build -Pbuild.release
)

IRIS_JAR="$(ls -1t "${ROOT}/client/yap-iris/build/libs"/yap-iris-fabric-*.jar 2>/dev/null | head -1 || true)"
if [[ -z "${IRIS_JAR}" ]]; then
  IRIS_JAR="$(ls -1t "${ROOT}/client/yap-iris/fabric/build/libs"/yap-iris-fabric-*.jar 2>/dev/null | head -1 || true)"
fi
if [[ -z "${IRIS_JAR}" ]]; then
  echo "ERROR: yap-iris fabric jar not found" >&2
  exit 1
fi
cp -f "$IRIS_JAR" "$OUT/"
cp -f "${ROOT}/client/yap-iris/LICENSE" "$OUT/LICENSE-YaP-Iris-LGPLv3.txt"
cp -f "${ROOT}/third-party/iris/NOTICE.txt" "$OUT/NOTICE-YaP-Iris.txt"
cp -f "${ROOT}/client/yap-iris/LICENSE-DEPENDENCIES" "$OUT/LICENSE-DEPENDENCIES-Iris.txt"

echo "==> Pack YaP Shaders"
SHADER_ZIP="${OUT}/yap-shaders.zip"
rm -f "$SHADER_ZIP"
(
  cd "${ROOT}/client/yap-shaders"
  zip -qr "$SHADER_ZIP" pack.mcmeta shaders README.md
)

echo "==> Build YaP Visuals (all-in-one jar)"
(
  cd "${ROOT}/client/yap-visuals"
  ./gradlew --no-daemon clean build
)
VISUALS_JAR="$(ls -1t "${ROOT}/client/yap-visuals/build/libs"/yap-visuals-*.jar 2>/dev/null | grep -v sources | grep -v javadoc | head -1 || true)"
if [[ -z "${VISUALS_JAR}" ]]; then
  echo "ERROR: yap-visuals jar not found" >&2
  ls -la "${ROOT}/client/yap-visuals/build/libs/" >&2 || true
  exit 1
fi
cp -f "$VISUALS_JAR" "$OUT/"

echo "==> Bundle install notes"
cat > "${OUT}/INSTALL.txt" <<'EOF'
YaP client visuals (optional — Fabric Java only)

RECOMMENDED — one jar:
  1. Install Fabric Loader 0.19+ for Minecraft 26.2
  2. Copy ONLY yap-visuals-*.jar into .minecraft/mods/
  3. Launch once (installs yap-shaders + nests Sodium + YaP Iris)
  4. Video Settings → Shader Packs if needed (usually auto-selected)

Do not also install separate sodium / iris / yap-shaders (duplicates).

Advanced — separate pieces still in this folder for debugging.
Vanilla / Bedrock / no-mods clients still join YaPcore without these files.
EOF

BUNDLE="${OUT}/yap-client-visuals.zip"
rm -f "$BUNDLE"
(
  cd "$OUT"
  zip -qr "$BUNDLE" \
    "$(basename "$VISUALS_JAR")" \
    INSTALL.txt \
    LICENSE-Sodium-PolyForm-Shield.txt \
    NOTICE-Sodium.txt \
    LICENSE-YaP-Iris-LGPLv3.txt \
    NOTICE-YaP-Iris.txt \
    LICENSE-DEPENDENCIES-Iris.txt
)

echo "Done. Primary artifact: $OUT/$(basename "$VISUALS_JAR")"
ls -lh "$OUT"
