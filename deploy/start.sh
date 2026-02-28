#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# Outline Playground — startup script
# Usage:
#   ./start.sh            # foreground (logs to console)
#   ./start.sh --daemon   # background (logs to playground.log)
# ─────────────────────────────────────────────────────────────────────────────
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAR="$SCRIPT_DIR/outline-playground.jar"
CONFIG="$SCRIPT_DIR/application.properties"
LOG="$SCRIPT_DIR/playground.log"
PID_FILE="$SCRIPT_DIR/playground.pid"

if [[ ! -f "$JAR" ]]; then
  echo "ERROR: $JAR not found. Run the build first (see DEPLOY.md)." >&2
  exit 1
fi

JVM_OPTS="${JVM_OPTS:--Xmx512m -Xms128m}"

CMD=(java $JVM_OPTS -jar "$JAR" --spring.config.location="file:$CONFIG")

if [[ "${1:-}" == "--daemon" ]]; then
  echo "Starting Outline Playground in background (log → $LOG) …"
  nohup "${CMD[@]}" >> "$LOG" 2>&1 &
  echo $! > "$PID_FILE"
  echo "Started. PID=$(cat "$PID_FILE")"
  echo "Tail log:  tail -f $LOG"
  echo "Stop:      ./stop.sh"
else
  echo "Starting Outline Playground on port $(grep -E '^server\.port' "$CONFIG" | cut -d= -f2 | tr -d ' ') …"
  exec "${CMD[@]}"
fi
