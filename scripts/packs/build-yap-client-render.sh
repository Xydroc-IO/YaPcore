#!/usr/bin/env bash
# Build YaP optional Fabric client mods → dist/client-mods/
# Release upload: client_mods.zip (folder with bag + staff + ultrawide + visuals jars).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
OUT="${ROOT}/dist/client-mods"
STAGING="${OUT}/_client_mods_staging/client_mods"
mkdir -p "$OUT"

echo "==> Fetch official Sodium (PolyForm Shield pin)"
"${ROOT}/scripts/packs/fetch-sodium.sh"

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

echo "==> Build YaP 420"
(
  cd "${ROOT}/client/yap-420"
  ./gradlew --no-daemon build
)
HAZE_JAR="$(ls -1t "${ROOT}/client/yap-420/build/libs"/yap-420-*.jar 2>/dev/null | grep -v sources | grep -v javadoc | head -1 || true)"
if [[ -z "${HAZE_JAR}" ]]; then
  echo "ERROR: yap-420 jar not found" >&2
  exit 1
fi
cp -f "$HAZE_JAR" "$OUT/"

echo "==> Build YaP Presence"
(
  cd "${ROOT}/client/yap-presence"
  ./gradlew --no-daemon build
)
PRESENCE_JAR="$(ls -1t "${ROOT}/client/yap-presence/build/libs"/yap-presence-*.jar 2>/dev/null | grep -v sources | grep -v javadoc | head -1 || true)"
if [[ -z "${PRESENCE_JAR}" ]]; then
  echo "ERROR: yap-presence jar not found" >&2
  exit 1
fi
cp -f "$PRESENCE_JAR" "$OUT/"

echo "==> Build YaP Blocks"
(
  cd "${ROOT}/client/yap-blocks"
  ./gradlew --no-daemon build
)
BLOCKS_JAR="$(ls -1t "${ROOT}/client/yap-blocks/build/libs"/yap-blocks-*.jar 2>/dev/null | grep -v sources | grep -v javadoc | head -1 || true)"
if [[ -z "${BLOCKS_JAR}" ]]; then
  echo "ERROR: yap-blocks jar not found" >&2
  exit 1
fi
cp -f "$BLOCKS_JAR" "$OUT/"

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

echo "==> Build YaP Staff"
(
  cd "${ROOT}/client/yap-staff"
  ./gradlew --no-daemon build
)
STAFF_JAR="$(ls -1t "${ROOT}/client/yap-staff/build/libs"/yap-staff-*.jar 2>/dev/null | grep -v sources | grep -v javadoc | head -1 || true)"
if [[ -z "${STAFF_JAR}" ]]; then
  echo "ERROR: yap-staff jar not found" >&2
  exit 1
fi
cp -f "$STAFF_JAR" "$OUT/"

echo "==> Bundle install notes"
cat > "${OUT}/INSTALL.txt" <<'EOF'
YaP optional Fabric client mods (Minecraft 26.2)

Release zip: client_mods.zip
  Unzip → client_mods/ with jars. Copy them into .minecraft/mods/
  (or Prism instance mods/).

  - yap-visuals-*.jar   Sodium + YaP Iris + shaders (one jar — do not also install sodium/iris)
  - yap-bag-*.jar       Bag keybind / inventory tabs (/bag)
  - yap-420-*.jar       YaP420 haze FX (yap:420) — optional with YaP420 plugin
  - yap-presence-*.jar  YaP Tailor menu (Esc / P) — skins, wardrobe, emotes, geo (yap:presence)
  - yap-blocks-*.jar    Bedrock catalog block HELLO (yap:blocks; visuals via server pack)
  - yap-staff-*.jar     Esc pause Staff menu (/yapadmin)
  - yap-ultrawide-*.jar Hor+ FOV for ultrawide monitors

Requirements: Fabric Loader 0.19+ for Minecraft 26.2.

Vanilla Java / Bedrock / no-mods clients still join YaPcore without these files.
EOF

# Release asset: one zip with a client_mods/ folder (upload this, not loose jars).
rm -rf "${OUT}/_client_mods_staging"
mkdir -p "$STAGING"
/bin/cp -f "$VISUALS_JAR" "$BAG_JAR" "$HAZE_JAR" "$PRESENCE_JAR" "$BLOCKS_JAR" "$UW_JAR" "$STAFF_JAR" "$STAGING/"
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
echo "  Contents: $(basename "$VISUALS_JAR") $(basename "$BAG_JAR") $(basename "$HAZE_JAR") $(basename "$PRESENCE_JAR") $(basename "$BLOCKS_JAR") $(basename "$STAFF_JAR") $(basename "$UW_JAR")"
ls -lh "$OUT"
