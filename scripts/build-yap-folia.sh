#!/usr/bin/env bash
# Build YaP-Folia from vendor/folia/work → lib/yap-folia-{ver}.jar
# Keeps lib/folia-{ver}.jar as stock alias/fallback when present.
#
# Env:
#   YAP_FOLIA_SKIP_PATCH=1   skip YaP patch apply (debug)
#   YAP_FOLIA_CLEAN=1        ./gradlew clean before build
set -euo pipefail
ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
# shellcheck disable=SC1091
. "$ROOT/scripts/lib.sh"
yap_require_java

LOCK="$ROOT/vendor/folia/UPSTREAM.lock"
WORK="$ROOT/vendor/folia/work"
# shellcheck disable=SC1090
. <(grep -E '^(BRANCH|COMMIT|MC_VERSION)=' "$LOCK" | sed 's/^/export /')
VER="${MC_VERSION:-26.2}"
OUT_YAP="$ROOT/lib/yap-folia-${VER}.jar"
OUT_STOCK_ALIAS="$ROOT/lib/folia-${VER}.jar"

if [ ! -d "$WORK/.git" ]; then
  "$ROOT/scripts/vendor-folia.sh"
fi

WANT="$(grep -E '^COMMIT=' "$LOCK" | cut -d= -f2-)"
HAVE="$(git -C "$WORK" rev-parse HEAD)"
if [ "$HAVE" != "$WANT" ]; then
  echo "Vendor HEAD $HAVE != pin $WANT — re-running vendor-folia.sh"
  "$ROOT/scripts/vendor-folia.sh"
fi

# Clean leftover YaP patch state so apply is idempotent from pin
git -C "$WORK" reset --hard "$WANT"
git -C "$WORK" clean -fd

cd "$WORK"

# YaP branding modifies Folia's tracked patch files BEFORE paperweight runs.
if [ "${YAP_FOLIA_SKIP_PATCH:-0}" != "1" ]; then
  echo "== YaP patches (pre-applyAllPatches) =="
  "$ROOT/scripts/folia-patch.sh" pre
fi

echo "== Folia applyAllPatches =="
if [ "${YAP_FOLIA_CLEAN:-0}" = "1" ]; then
  ./gradlew --no-daemon clean
fi
./gradlew --no-daemon applyAllPatches

# Teleport / async-save / scoreboard / entity-budget target generated sources.
if [ "${YAP_FOLIA_SKIP_PATCH:-0}" != "1" ]; then
  echo "== YaP patches (post-applyAllPatches) =="
  "$ROOT/scripts/folia-patch.sh" post
fi

echo "== Folia createPaperclipJar / createBundlerJar =="
./gradlew --no-daemon :folia-server:createPaperclipJar :folia-server:createBundlerJar || \
  ./gradlew --no-daemon :folia-server:createPaperclipJar || \
  ./gradlew --no-daemon :folia-server:createBundlerJar

CANDIDATE=""
# Prefer paperclip (matches Fill/stock Main-Class), then bundler, then large server jar
pick_jar() {
  local pattern="$1" min_bytes="$2"
  local f base SZ
  while IFS= read -r f; do
    [ -z "$f" ] && continue
    base="$(basename "$f")"
    case "$base" in
      *-sources.jar|*-javadoc.jar|*api*|*-dev*) continue ;;
    esac
    SZ="$(wc -c <"$f" | tr -d ' ')"
    if [ "$SZ" -gt "$min_bytes" ]; then
      CANDIDATE="$f"
      return 0
    fi
  done < <(find "$WORK" -type f -name "$pattern" ! -path '*/.gradle/*' -printf '%s\t%p\n' 2>/dev/null | sort -nr | cut -f2-)
  return 1
}
pick_jar '*paperclip*.jar' 40000000 || \
  pick_jar '*bundler*.jar' 40000000 || \
  pick_jar '*-mojmap.jar' 40000000 || true

if [ -z "${CANDIDATE:-}" ]; then
  while IFS= read -r f; do
    [ -z "$f" ] && continue
    base="$(basename "$f")"
    case "$base" in
      *-sources.jar|*-javadoc.jar|*api*) continue ;;
    esac
    SZ="$(wc -c <"$f" | tr -d ' ')"
    if [ "$SZ" -gt 20000000 ]; then
      CANDIDATE="$f"
      break
    fi
  done < <(find "$WORK/folia-server/build/libs" -type f -name '*.jar' -printf '%s\t%p\n' 2>/dev/null | sort -nr | cut -f2-)
fi

if [ -z "${CANDIDATE:-}" ] || [ ! -f "$CANDIDATE" ]; then
  echo "FAIL: could not locate Folia server jar after build" >&2
  find "$WORK" -name '*.jar' -size +1M 2>/dev/null | head -40 || true
  exit 1
fi

mkdir -p "$ROOT/lib"
/bin/cp -f "$CANDIDATE" "$OUT_YAP"
if [ ! -f "$OUT_STOCK_ALIAS" ]; then
  /bin/cp -f "$OUT_YAP" "$OUT_STOCK_ALIAS"
  echo "Created stock alias $OUT_STOCK_ALIAS (from yap-folia)"
fi

# Stamp YaP metadata on thin/server jars only. Paperclip outer manifests are
# minimal (Main-Class: paperclip); jar ufm rewrites them and drops attributes.
case "$(basename "$CANDIDATE")" in
  *paperclip*|*bundler*) ;;
  *)
    if command -v jar >/dev/null 2>&1; then
      TMPM="$(mktemp)"
      {
        echo "YaP-Folia-Brand: YaP-Folia"
        echo "YaP-Folia-Version: ${VER}"
        echo "YaP-Folia-Upstream: ${WANT}"
      } >"$TMPM"
      jar ufm "$OUT_YAP" "$TMPM" 2>/dev/null || true
      rm -f "$TMPM"
    fi
    ;;
esac

echo "OK → $OUT_YAP ($(wc -c <"$OUT_YAP" | tr -d ' ') bytes)"
echo "Upstream $(git -C "$WORK" rev-parse --short HEAD)  source=$CANDIDATE"
