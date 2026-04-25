#!/usr/bin/env bash
# Stop the daemon started by ./start.sh -d.
set -e

DIR="$(cd "$(dirname "$0")" && pwd)"
PID="$DIR/server.pid"

if [ -f "$PID" ] && kill -0 "$(cat "$PID")" 2>/dev/null; then
  kill "$(cat "$PID")"
  sleep 1
  echo "outline-aipp stopped (PID $(cat "$PID"))"
  rm -f "$PID"
else
  echo "outline-aipp not running"
fi
