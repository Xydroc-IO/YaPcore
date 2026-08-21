#!/usr/bin/env bash
# Interactive menu — drop into Konsole when you want to pick a suite.
set -eu
SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
# shellcheck disable=SC1091
. "$SCRIPT_DIR/lib.sh"
yap_test_bootstrap

yap_banner "Test menu"
cat <<'EOF'
  1) Unit tests          (test-unit.sh)
  2) Fray concurrency    (test-fray.sh)
  3) JCStress            (test-jcstress.sh)
  4) SpotBugs            (test-spotbugs.sh)
  5) All CI verify       (test-all.sh)
  6) Boundary stress     (test-stress.sh)
  7) Soak + JFR          (soak-jfr.sh)
  8) Endurance report    (test-endurance.sh)  ← months-long readiness
  9) Open Test Lab GUI   (test-gui.sh)
  q) Quit
EOF
echo ""
printf "Choose [1-9/q]: "
read -r CHOICE || CHOICE=q

run() {
  YAP_NO_PAUSE=1 "$SCRIPT_DIR/$1" "${@:2}"
}

case "$CHOICE" in
  1) run test-unit.sh ;;
  2) run test-fray.sh ;;
  3) run test-jcstress.sh ;;
  4) run test-spotbugs.sh ;;
  5) run test-all.sh ;;
  6)
    printf "Bots [32]: "; read -r B || true
    printf "Seconds [30]: "; read -r S || true
    run test-stress.sh "${B:-32}" "${S:-30}"
    ;;
  7)
    printf "Bots [32]: "; read -r B || true
    printf "Seconds [60]: "; read -r S || true
    YAP_NO_PAUSE=1 "$SCRIPT_DIR/soak-jfr.sh" "--bots=${B:-32}" "--seconds=${S:-60}"
    ;;
  8)
    printf "Seconds [120]: "; read -r S || true
    printf "Bots [64]: "; read -r B || true
    run test-endurance.sh "${S:-120}" "${B:-64}"
    ;;
  9) YAP_NO_PAUSE=1 "$SCRIPT_DIR/test-gui.sh" ;;
  q|Q|"") echo "Bye."; exit 0 ;;
  *) echo "Unknown choice: $CHOICE"; yap_pause_end 1; exit 1 ;;
esac
CODE=$?
yap_pause_end "$CODE"
exit "$CODE"
