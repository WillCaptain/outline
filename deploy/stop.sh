#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# Outline Playground — stop script (daemon mode only)
# ─────────────────────────────────────────────────────────────────────────────
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PID_FILE="$SCRIPT_DIR/playground.pid"

if [[ ! -f "$PID_FILE" ]]; then
  echo "No PID file found at $PID_FILE — is the playground running?"
  exit 0
fi

PID=$(cat "$PID_FILE")
if kill -0 "$PID" 2>/dev/null; then
  echo "Stopping Outline Playground (PID=$PID) …"
  kill "$PID"
  rm -f "$PID_FILE"
  echo "Stopped."
else
  echo "Process $PID is not running (stale PID file removed)."
  rm -f "$PID_FILE"
fi
