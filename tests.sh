#!/usr/bin/env bash
# Project-root shortcut — drop into Konsole from Dolphin / file manager.
# Forwards to scripts/tests.sh (interactive menu).
exec "$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)/scripts/tests.sh" "$@"
