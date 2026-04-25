#!/usr/bin/env bash
# Launch outline-aipp on port 8094 (daemon mode).
# Logs → $DIR/server.log (plain append, no rotation).
#
# Flags:
#   -d, --daemon   detach & exit; process survives shell close
#   (none)         foreground, useful for local dev
set -e

DAEMON=false
for arg in "$@"; do
  [[ "$arg" == "-d" ]] && DAEMON=true
  [[ "$arg" == "--daemon" ]] && DAEMON=true
done

DIR="$(cd "$(dirname "$0")" && pwd)"
JAR="$DIR/outline-aipp.jar"
LOG="$DIR/server.log"
PID="$DIR/server.pid"
PORT="${OUTLINE_AIPP_PORT:-8094}"

if [ ! -f "$JAR" ]; then
  echo "jar not found: $JAR  (build with: mvn -q -DskipTests package && cp target/outline-aipp.jar deploy/)"
  exit 1
fi

# stop old instance first
if [ -f "$PID" ] && kill -0 "$(cat "$PID")" 2>/dev/null; then
  kill "$(cat "$PID")" 2>/dev/null || true
  sleep 1
fi

JAVA_ARGS=(--server.port="$PORT")

if $DAEMON; then
  # Same daemonization trick used by world-entitir / world-one:
  # python3 setsid() → new session → closing launcher terminal doesn't kill the JVM.
  python3 -c '
import os, sys
os.setsid()
os.execvp("java", ["java", "-jar"] + sys.argv[1:])
' "$JAR" "${JAVA_ARGS[@]}" </dev/null >> "$LOG" 2>&1 &
  WORLDONE_PID=$!
  disown $WORLDONE_PID 2>/dev/null || true
  echo $WORLDONE_PID > "$PID"
  echo "outline-aipp 已启动（daemon），PID=$WORLDONE_PID port=$PORT"
  echo "日志：tail -f $LOG"
else
  java -jar "$JAR" "${JAVA_ARGS[@]}"
fi
