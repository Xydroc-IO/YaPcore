#!/usr/bin/env bash
# Universal nginx setup for YaPcore (Debian/Ubuntu/Fedora/RHEL/Arch/Manjaro).
# Proxies public TCP/UDP game port → local YaPcore, and HTTP → resource packs.
#
# Usage:
#   ./scripts/nginx-setup.sh              # install configs from server.properties
#   ./scripts/nginx-setup.sh --dry-run    # print rendered configs only
#   ./scripts/nginx-setup.sh --uninstall  # remove YaPcore nginx snippets
#
# Requires root for install (sudo). Does not force-install nginx packages unless
# --install-pkg is passed.

set -eu

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
ROOT="$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)"
# shellcheck disable=SC1091
. "$SCRIPT_DIR/lib.sh"

DRY=0
UNINSTALL=0
INSTALL_PKG=0
for arg in "$@"; do
  case "$arg" in
    --dry-run) DRY=1 ;;
    --uninstall) UNINSTALL=1 ;;
    --install-pkg) INSTALL_PKG=1 ;;
    -h|--help)
      sed -n '2,14p' "$0" | tr -d '#'
      exit 0
      ;;
  esac
done

cd "$ROOT"
yap_load_config

# Extra keys
NGINX_PUBLIC=25565
NGINX_PACK_HTTP=80
NGINX_DOMAIN=""
SERVER_DOMAIN=""
PUBLIC_HOST=""
YAP_JAVA="$PORT"
YAP_BEDROCK="$PORT"
YAP_PACK=8081
ALLOW_LOCAL=true
SHARED=true
if [ -f "$ROOT/config/server.properties" ]; then
  while IFS= read -r line || [ -n "$line" ]; do
    case "$line" in ''|\#*) continue ;; esac
    key="${line%%=*}"; val="${line#*=}"
    key="$(echo "$key" | tr -d '[:space:]')"
    case "$key" in
      nginx-public-port) NGINX_PUBLIC="$val" ;;
      nginx-pack-port) NGINX_PACK_HTTP="$val" ;;
      nginx-domain) NGINX_DOMAIN="$val" ;;
      server-domain) SERVER_DOMAIN="$val" ;;
      public-host) PUBLIC_HOST="$val" ;;
      port) YAP_JAVA="$val" ;;
      bedrock-port) YAP_BEDROCK="$val" ;;
      resource-pack-http-port) YAP_PACK="$val" ;;
      shared-listen-port) SHARED="$val" ;;
      allow-localhost) ALLOW_LOCAL="$val" ;;
    esac
  done <"$ROOT/config/server.properties"
fi
SHARED="$(echo "$SHARED" | tr '[:upper:]' '[:lower:]')"
if [ "$SHARED" = "true" ] || [ "$SHARED" = "1" ] || [ "$SHARED" = "yes" ]; then
  YAP_BEDROCK="$YAP_JAVA"
fi
NGINX_PUBLIC="$(echo "$NGINX_PUBLIC" | tr -cd '0-9')"
NGINX_PACK_HTTP="$(echo "$NGINX_PACK_HTTP" | tr -cd '0-9')"
YAP_JAVA="$(echo "$YAP_JAVA" | tr -cd '0-9')"
YAP_BEDROCK="$(echo "$YAP_BEDROCK" | tr -cd '0-9')"
YAP_PACK="$(echo "$YAP_PACK" | tr -cd '0-9')"
# Prefer nginx-domain, then server-domain / public-host
if [ -z "$NGINX_DOMAIN" ] || [ "$NGINX_DOMAIN" = "_" ]; then
  NGINX_DOMAIN="${SERVER_DOMAIN:-$PUBLIC_HOST}"
fi
[ -n "$NGINX_DOMAIN" ] || NGINX_DOMAIN="_"

render() {
  sed -e "s/__NGINX_PUBLIC_PORT__/${NGINX_PUBLIC}/g" \
      -e "s/__NGINX_PACK_PORT__/${NGINX_PACK_HTTP}/g" \
      -e "s/__NGINX_DOMAIN__/${NGINX_DOMAIN}/g" \
      -e "s/__YAP_JAVA_PORT__/${YAP_JAVA}/g" \
      -e "s/__YAP_BEDROCK_PORT__/${YAP_BEDROCK}/g" \
      -e "s/__YAP_PACK_PORT__/${YAP_PACK}/g" \
      "$1"
}

STREAM_TPL="$ROOT/deploy/nginx/yapcore-stream.conf.template"
HTTP_TPL="$ROOT/deploy/nginx/yapcore-http.conf.template"
OUT_DIR="$ROOT/deploy/nginx/generated"
mkdir -p "$OUT_DIR"
STREAM_OUT="$OUT_DIR/yapcore-stream.conf"
HTTP_OUT="$OUT_DIR/yapcore-http.conf"

render "$STREAM_TPL" >"$STREAM_OUT"
render "$HTTP_TPL" >"$HTTP_OUT"

echo "Rendered:"
echo "  $STREAM_OUT"
echo "  $HTTP_OUT"
echo "Domain / server_name:            ${NGINX_DOMAIN}"
echo "Same-PC join (no nginx needed): 127.0.0.1:${YAP_JAVA}"
echo "Public via nginx stream:         ${NGINX_DOMAIN}:${NGINX_PUBLIC}  (or <server-ip>:${NGINX_PUBLIC})"
echo "Packs via nginx HTTP:            http://${NGINX_DOMAIN}:${NGINX_PACK_HTTP}/pack/  (Cloudflare HTTPS → origin :${NGINX_PACK_HTTP})"
echo "Cloudflare DNS checklist:        deploy/cloudflare/dns-records.example"
echo "Docs:                            docs/CLOUDFLARE_AND_NGINX.md"

if [ "$DRY" -eq 1 ]; then
  echo "---- stream ----"
  cat "$STREAM_OUT"
  echo "---- http ----"
  cat "$HTTP_OUT"
  exit 0
fi

if [ "$UNINSTALL" -eq 1 ]; then
  if [ "$(id -u)" -ne 0 ]; then
    echo "Re-run with sudo for uninstall" >&2
    exit 1
  fi
  rm -f /etc/nginx/yapcore-stream.conf \
        /etc/nginx/conf.d/yapcore-http.conf \
        /etc/nginx/sites-enabled/yapcore-http \
        /etc/nginx/sites-available/yapcore-http
  nginx -t && (systemctl reload nginx 2>/dev/null || nginx -s reload || true)
  echo "YaPcore nginx snippets removed."
  exit 0
fi

if [ "$INSTALL_PKG" -eq 1 ]; then
  if command -v pacman >/dev/null 2>&1; then
    pacman -S --needed --noconfirm nginx
  elif command -v apt-get >/dev/null 2>&1; then
    apt-get update -y
    apt-get install -y nginx libnginx-mod-stream || apt-get install -y nginx
  elif command -v dnf >/dev/null 2>&1; then
    dnf install -y nginx
  elif command -v yum >/dev/null 2>&1; then
    yum install -y nginx
  else
    echo "No known package manager — install nginx manually." >&2
  fi
fi

if ! command -v nginx >/dev/null 2>&1; then
  echo "nginx not found. Install it, or re-run with: sudo $0 --install-pkg" >&2
  echo "Generated configs are ready under deploy/nginx/generated/"
  exit 2
fi

if [ "$(id -u)" -ne 0 ]; then
  echo "Configs generated. To install system-wide:"
  echo "  sudo $0"
  exit 0
fi

# Ensure stream module / include
MAIN=/etc/nginx/nginx.conf
if [ -f "$MAIN" ] && ! grep -q 'yapcore-stream.conf' "$MAIN" 2>/dev/null; then
  if grep -qE '^\s*stream\s*\{' "$MAIN"; then
    echo "Note: nginx.conf already has a stream{} block — merge $STREAM_OUT manually if needed."
  else
    # Prefer top-level include (works when stream module loaded)
    if ! grep -q 'include /etc/nginx/yapcore-stream.conf' "$MAIN"; then
      cp -a "$MAIN" "$MAIN.yapcore.bak.$(date +%s)"
      printf '\n# YaPcore Minecraft TCP/UDP proxy\ninclude /etc/nginx/yapcore-stream.conf;\n' >>"$MAIN"
    fi
  fi
fi

cp -f "$STREAM_OUT" /etc/nginx/yapcore-stream.conf
if [ -d /etc/nginx/sites-available ]; then
  cp -f "$HTTP_OUT" /etc/nginx/sites-available/yapcore-http
  ln -sfn /etc/nginx/sites-available/yapcore-http /etc/nginx/sites-enabled/yapcore-http
elif [ -d /etc/nginx/conf.d ]; then
  cp -f "$HTTP_OUT" /etc/nginx/conf.d/yapcore-http.conf
else
  cp -f "$HTTP_OUT" /etc/nginx/yapcore-http.conf
  grep -q 'yapcore-http.conf' "$MAIN" || echo 'include /etc/nginx/yapcore-http.conf;' >>"$MAIN"
fi

if nginx -t; then
  systemctl enable nginx 2>/dev/null || true
  systemctl reload nginx 2>/dev/null || nginx -s reload || systemctl restart nginx
  echo "nginx reloaded — game :${NGINX_PUBLIC} → 127.0.0.1:${YAP_JAVA}, packs HTTP :${NGINX_PACK_HTTP}"
else
  echo "nginx -t failed — fix config before reload" >&2
  exit 1
fi
