#!/bin/sh
# Runs a test script (see DevScriptRunner) in the development client and prints its results.
#
#   scripts/dev/test.sh scripts/dev/draftout-goals.txt    # a script file
#   scripts/dev/test.sh - <<'EOF'                          # or a script from stdin
#   bingo dev quick lock_map
#   bingo dev expect achieved red false lock_map
#   EOF
#   scripts/dev/test.sh --restart <script>                 # restart the client first (after changing code)
#
# The client (gradle runClient, opening the test world "BingoutDev") is started when it isn't running, and keeps
# running for the next script, so that running one takes seconds. A script ending with "exit" quits it.
# The client's log is mc26.3/run/bingout-dev/client.log. Exits with 1 if a check failed.
set -eu

ROOT=$(cd "$(dirname "$0")/../.." && pwd)
DEV="$ROOT/mc26.3/run/bingout-dev"
LOG="$DEV/client.log"
PID_FILE="$DEV/client.pid"
TIMEOUT=${BINGOUT_TEST_TIMEOUT:-600}
# the build needs JDK 25 (BINGOUT_JAVA_HOME, else Homebrew's openjdk@25 if installed, else JAVA_HOME)
JDK25=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
if [ -n "${BINGOUT_JAVA_HOME:-}" ]; then JAVA_HOME=$BINGOUT_JAVA_HOME
elif [ -d "$JDK25" ]; then JAVA_HOME=$JDK25
fi
export JAVA_HOME

restart=0
if [ "${1:-}" = "--restart" ]; then restart=1; shift; fi
if [ $# -ne 1 ]; then
    echo "usage: $0 [--restart] <script.txt | ->" >&2
    exit 2
fi

mkdir -p "$DEV"
touch "$DEV/autoplay"
if [ "$1" = "-" ]; then cat > "$DEV/pending.txt"; else cp "$1" "$DEV/pending.txt"; fi

running() { [ -f "$PID_FILE" ] && kill -0 "$(cat "$PID_FILE")" 2>/dev/null; }
log_lines() { if [ -f "$LOG" ]; then wc -l < "$LOG" | tr -d ' '; else echo 0; fi; }

if running && [ $restart -eq 1 ]; then
    echo "Restarting the client..."
    echo exit > "$DEV/script.txt"
    while running; do sleep 1; done
fi

if ! running; then
    echo "Starting the client (build + launch)..."
    rm -f "$DEV/script.txt"
    cd "$ROOT"
    nohup ./gradlew --console=plain :mc26.3:runClient -x test -x :common:test > "$LOG" 2>&1 &
    echo $! > "$PID_FILE"
fi

start=$(log_lines)
mv "$DEV/pending.txt" "$DEV/script.txt"

waited=0
until tail -n "+$((start + 1))" "$LOG" | grep -aq '\[bingout-dev\] RESULT'; do
    if ! running; then
        echo "The client stopped before the script finished:" >&2
        grep -a '^e: \|error:\|FAILED\|Exception' "$LOG" | tail -20 >&2 || true
        exit 1
    fi
    if [ "$waited" -ge "$TIMEOUT" ]; then
        echo "Timed out after ${TIMEOUT}s (log: $LOG)" >&2
        exit 1
    fi
    sleep 1
    waited=$((waited + 1))
done

tail -n "+$((start + 1))" "$LOG" | grep -a '\[bingout-dev\]' | grep -av '\] > /\|command result' \
    | sed 's/^.*\[bingout-dev\] //'
tail -n "+$((start + 1))" "$LOG" | grep -aq '\[bingout-dev\] RESULT passed=[0-9]* failed=0'
