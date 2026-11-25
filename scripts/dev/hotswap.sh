#!/bin/sh
# Hot-swaps changed code into the development client started by scripts/dev/test.sh, instead of restarting it:
#   1. compiles (without rebuilding the module jars, which the running client has open),
#   2. redefines the changed classes in the client's JVM through its debug port (scripts/dev/HotSwap.java),
#   3. copies the resources and runs /reload when data files (goals, advancements, ...) changed.
#
#   scripts/dev/hotswap.sh                  # then run a test: scripts/dev/test.sh <script>
#   scripts/dev/test.sh --hotswap <script>  # the same, restarting the client if the code can't be swapped
#
# What can be swapped: changes inside existing methods and lambdas (the JVM's HotSwap). What needs a restart (exit
# code 3, with the reason): added or removed fields, methods or lambdas, other supertypes, mixins, and changed classes
# of common/platform/api that aren't loaded yet (they would load from the module jars, which aren't rebuilt meanwhile).
# Code that already ran isn't run again: init blocks, constructors, registered event handlers keep what they did.
set -eu

ROOT=$(cd "$(dirname "$0")/../.." && pwd)
DEV="$ROOT/mc26.3/run/bingout-dev"
PID_FILE="$DEV/client.pid"
CLASSES="$DEV/hotswap-classes.txt"
RESOURCES_MARKER="$DEV/hotswap-resources-marker"
PORT=${BINGOUT_DEBUG_PORT:-5005}
JDK25=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
if [ -n "${BINGOUT_JAVA_HOME:-}" ]; then JAVA_HOME=$BINGOUT_JAVA_HOME
elif [ -d "$JDK25" ]; then JAVA_HOME=$JDK25
fi
export JAVA_HOME

if ! { [ -f "$PID_FILE" ] && kill -0 "$(cat "$PID_FILE")" 2>/dev/null; } || [ ! -f "$CLASSES" ]; then
    echo "No development client from scripts/dev/test.sh is running" >&2
    exit 1
fi

cd "$ROOT"
before=$(date +%s)
# the same build id as the running client (scripts/dev/test.sh)
if ! ./gradlew --console=plain -q -Pbuild_id=dev :mc26.3:classes :mc26.3:clientClasses \
    -x :common:jar -x :platform:jar -x :api:jar > "$DEV/hotswap-build.log" 2>&1; then
    grep -a '^e: \|error:\|What went wrong' -A3 "$DEV/hotswap-build.log" | head -30 >&2
    echo "Build failed (log: $DEV/hotswap-build.log)" >&2
    exit 1
fi

# shellcheck disable=SC2046
"$JAVA_HOME/bin/java" "$ROOT/scripts/dev/HotSwap.java" swap "$PORT" "$CLASSES" $("$ROOT/scripts/dev/class-dirs.sh") \
    || exit $?

if [ ! -f "$RESOURCES_MARKER" ] \
    || [ -n "$(find "$ROOT/mc26.3/build/resources/main" -type f -newer "$RESOURCES_MARKER" | head -1)" ]; then
    echo "Reloading data packs"
    printf '@console reload\nwait 20\n' | "$ROOT/scripts/dev/test.sh" - > /dev/null
fi
touch "$RESOURCES_MARKER"
echo "Hot swap done in $(( $(date +%s) - before ))s"
