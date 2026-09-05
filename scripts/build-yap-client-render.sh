#!/usr/bin/env bash
# Build YaP optional Fabric client mods → dist/client-mods/
# Release upload: client_mods.zip (folder with bag + ultrawide + visuals jars).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="${ROOT}/dist/client-mods"
STAGING="${OUT}/_client_mods_staging/client_mods"
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

echo "==> Build YaP Bag"
(
  cd "${ROOT}/client/yap-bag"
  ./gradlew --no-daemon build
)
BAG_JAR="$(ls -1t "${ROOT}/client/yap-bag/build/libs"/yap-bag-*.jar 2>/dev/null | grep -v sources | grep -v javadoc | head -1 || true)"
if [[ -z "${BAG_JAR}" ]]; then
  echo "ERROR: yap-bag jar not found" >&2
  exit 1
fi
cp -f "$BAG_JAR" "$OUT/"

echo "==> Build YaP Ultrawide"
(
  cd "${ROOT}/client/yap-ultrawide"
  ./gradlew --no-daemon build
)
UW_JAR="$(ls -1t "${ROOT}/client/yap-ultrawide/build/libs"/yap-ultrawide-*.jar 2>/dev/null | grep -v sources | grep -v javadoc | head -1 || true)"
if [[ -z "${UW_JAR}" ]]; then
  echo "ERROR: yap-ultrawide jar not found" >&2
  exit 1
fi
cp -f "$UW_JAR" "$OUT/"

echo "==> Bundle install notes"
cat > "${OUT}/INSTALL.txt" <<'EOF'
YaP optional Fabric client mods (Minecraft 26.2)

Release zip: client_mods.zip
  Unzip → client_mods/ with three jars. Copy all three into .minecraft/mods/
  (or Prism instance mods/).

  - yap-visuals-*.jar   Sodium + YaP Iris + shaders (one jar — do not also install sodium/iris)
  - yap-bag-*.jar       Bag keybind / inventory tabs (/bag)
  - yap-ultrawide-*.jar Hor+ FOV for ultrawide monitors

Requirements: Fabric Loader 0.19+ for Minecraft 26.2.

Vanilla Java / Bedrock / no-mods clients still join YaPcore without these files.
EOF

# Release asset: one zip with a client_mods/ folder (upload this, not three jars).
rm -rf "${OUT}/_client_mods_staging"
mkdir -p "$STAGING"
/bin/cp -f "$VISUALS_JAR" "$BAG_JAR" "$UW_JAR" "$STAGING/"
/bin/cp -f "${OUT}/INSTALL.txt" "$STAGING/"
CLIENT_MODS_ZIP="${OUT}/client_mods.zip"
rm -f "$CLIENT_MODS_ZIP"
(
  cd "${OUT}/_client_mods_staging"
  zip -qr "$CLIENT_MODS_ZIP" client_mods
)
rm -rf "${OUT}/_client_mods_staging"

# Optional Discord/site visuals-only bundle (licenses + visuals jar).
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

echo "Done."
echo "  Release upload: $CLIENT_MODS_ZIP"
echo "  Contents: $(basename "$VISUALS_JAR") $(basename "$BAG_JAR") $(basename "$UW_JAR")"
ls -lh "$OUT"
