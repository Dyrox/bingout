#!/bin/sh
# The class directories of the development client, for scripts/dev/HotSwap.java: mc26.3's (which the client loads
# classes from), and with "jar:" those of the modules it loads as jars.
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
for module in api platform common mc26.3; do
    prefix=jar:
    [ "$module" = mc26.3 ] && prefix=
    for lang in kotlin java; do
        for set in main client; do echo "$prefix$ROOT/$module/build/classes/$lang/$set"; done
    done
done
