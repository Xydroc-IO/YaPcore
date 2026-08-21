#!/usr/bin/env bash
# Optional Meta Infer (RacerD) static race analysis.
# Install: https://fbinfer.com/docs/getting-started
# Usage: ./scripts/infer-racerd.sh

set -eu

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
ROOT="$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)"
cd "$ROOT"

if ! command -v infer >/dev/null 2>&1; then
  echo "Meta Infer not installed."
  echo "Install from https://fbinfer.com/docs/getting-started then re-run."
  echo "SpotBugs (RacerD alternative in-repo): gradle spotbugsMain"
  exit 1
fi

# Capture compile commands then analyze for races / thread safety
gradle clean compileJava
infer run --racerd-only -- gradle compileJava
echo "Infer report: $ROOT/infer-out/report.txt"
