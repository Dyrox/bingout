#!/bin/sh
# Runs a test script (see DevScriptRunner) in the development client and prints its results.
#
#   scripts/dev/test.sh scripts/dev/draftout-goals.txt    # a script file
#   scripts/dev/test.sh - <<'EOF'                          # or a script from stdin
#   bingo dev quick lock_map
#   bingo dev expect achieved red false lock_map
#   EOF
#   scripts/dev/test.sh --restart <script>                 # restart the client first (after changing code)
#   scripts/dev/test.sh --hotswap <script>                 # hot-swap changed code first (scripts/dev/hotswap.sh),
#                                                          # restarting only if it can't be swapped
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
PORT=${BINGOUT_DEBUG_PORT:-5005}
# the build needs JDK 25 (BINGOUT_JAVA_HOME, else Homebrew's openjdk@25 if installed, else JAVA_HOME)
JDK25=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
if [ -n "${BINGOUT_JAVA_HOME:-}" ]; then JAVA_HOME=$BINGOUT_JAVA_HOME
elif [ -d "$JDK25" ]; then JAVA_HOME=$JDK25
fi
export JAVA_HOME

restart=0
hotswap=0
case "${1:-}" in
    --restart) restart=1; shift ;;
    --hotswap) hotswap=1; shift ;;
esac
if [ $# -ne 1 ]; then
    echo "usage: $0 [--restart | --hotswap] <script.txt | ->" >&2
    exit 2
fi

mkdir -p "$DEV"
touch "$DEV/autoplay"
PENDING="$DEV/pending.$$.txt"
if [ "$1" = "-" ]; then cat > "$PENDING"; else cp "$1" "$PENDING"; fi

running() { [ -f "$PID_FILE" ] && kill -0 "$(cat "$PID_FILE")" 2>/dev/null; }
log_lines() { if [ -f "$LOG" ]; then wc -l < "$LOG" | tr -d ' '; else echo 0; fi; }

if running && [ $hotswap -eq 1 ]; then
    set +e
    "$ROOT/scripts/dev/hotswap.sh"
    status=$?
    set -e
    case $status in
        0) ;;
        3) echo "Restarting the client instead..."; restart=1 ;;
        *) exit $status ;;
    esac
fi

if running && [ $restart -eq 1 ]; then
    echo "Restarting the client..."
    echo exit > "$DEV/script.txt"
    while running; do sleep 1; done
fi

if ! running; then
    echo "Starting the client (build + launch)..."
    rm -f "$DEV/script.txt" "$DEV/hotswap-classes.txt"
    cd "$ROOT"
    # a fixed build id: otherwise every build has a new version, and scripts/dev/hotswap.sh would see changes everywhere
    nohup ./gradlew --console=plain :mc26.3:runClient -x test -x :common:test -Pbuild_id=dev "-PbingoutDebugPort=$PORT" \
        > "$LOG" 2>&1 &
    echo $! > "$PID_FILE"

    # once it's built and started: record the classes it runs, for scripts/dev/hotswap.sh
    waited=0
    until grep -aq '\[DevAutoplay\] Opening the test world' "$LOG" 2>/dev/null || ! running || [ "$waited" -ge "$TIMEOUT" ]; do
        sleep 1
        waited=$((waited + 1))
    done
    if running; then
        # shellcheck disable=SC2046
        "$JAVA_HOME/bin/java" "$ROOT/scripts/dev/HotSwap.java" snapshot "$DEV/hotswap-classes.txt" \
            $("$ROOT/scripts/dev/class-dirs.sh")
    fi
fi

start=$(log_lines)
mv "$PENDING" "$DEV/script.txt"

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
