#!/usr/bin/env bash
exec "$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)/scripts/test-unit.sh" "$@"
