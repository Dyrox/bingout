# Bingout builds

Bingout is the external mod/JAR name; in-game mode, filter, and drafting UI are named Draftout. Existing mod, data, configuration, and packet namespaces remain compatible with the Bingo/Draftout port. Use `/bingo mode draftout`; `/bingo mode bingout` remains an alias. Install matching builds on both clients and the server for the shared drafting screen.

## Build

Use JDK 25:

```sh
JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home ./gradlew :mc26.3:assemble
```

Each Gradle invocation generates a UTC build ID (`yyyyMMddHHmmssSSS`). All targets in that invocation share the ID. Installable artifacts are:

```
mc26.x/build/libs/bingout-2.16.0-build.<build-id>+mc26.x.jar
```

The same version is embedded in `fabric.mod.json` and the generated version constant. `-sources.jar` is not installable. To repeat a build with an intentional fixed version, pass `-Pbuild_id=<previous-id>`; that overwrites that particular version's files. Ordinary builds get a fresh ID.

## Shared draft screen

Both teams and spectators receive the current two offers, all selected goals, both teams' remaining rerolls, the picking team, and server time remaining. Only the picking team can act. The server checks team membership, reroll limits, and an opaque offer token that changes after every pick and reroll. UI actions now use the vanilla `bingo draft choose <turn> <option>` command transport; the older C2S packet endpoint remains compatible. Pending clicks unlock for retry after one second without a new turn. Clients with the new packet protocol use the compact translucent screen; older clients keep the chat/dialog fallback.

The layout follows the original Draftout `DraftScreen` structure (status and countdown, two icon choices, rerolls, 5×5 board), with a new renderer using this project's platform interfaces. No original background is used. Original goal-art attribution remains in `scripts/draftout/assets/README.md`.

Goal definitions, icons, and draft translations remain generated through `python3 scripts/draftout/generate.py`.

## GitHub CI and releases

`.github/workflows/build-release.yml` builds the Minecraft 26.3 target on Java 25. Pushes to `main`, pull requests, and manual Actions runs produce the `bingout-jars` artifact. Each run embeds `ci-<run-number>-<attempt>` in the version. It runs the 407 resource checks plus DraftController regressions; the two known upstream configuration failures are outside this targeted suite.

Push a `v*` tag (for example `v2.16.0`) to build and automatically create a GitHub Release containing the installable 26.3 JAR and `SHA256SUMS.txt`. Publishing a release in the GitHub UI also triggers building and attachment of its JARs. The tag must point to a commit containing this workflow. Builds must all pass before publishing. No external token is required; the publishing job uses the repository's `GITHUB_TOKEN` with `contents: write`.
