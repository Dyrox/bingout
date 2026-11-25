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

## Draft reroll count

`/bingo options draft_reroll_count 2` sets the total rerolls per team for the next draft. The default is 1; 0 disables rerolls. Requires game configuration permission, like the other options, and can be set before a game or during play for the next draft. The setting is saved with the card options; both teams start with the same allowance. Existing `/bingo reroll` behavior is unchanged.

## Shared draft screen

Both teams and spectators receive the current two offers, all selected goals, both teams' remaining rerolls, the picking team, and server time remaining. Only the picking team can act. The server checks team membership, reroll limits, and an opaque offer token that changes after every pick and reroll. UI actions now use the vanilla `bingo draft choose <turn> <option>` command transport; the older C2S packet endpoint remains compatible. Pending clicks unlock for retry after one second without a new turn. Clients with the new packet protocol use the compact translucent screen; older clients keep the chat/dialog fallback.

The layout follows the original Draftout `DraftScreen` structure (status and countdown, two icon choices, rerolls, 5×5 board), with a new renderer using this project's platform interfaces. No original background is used. Original goal-art attribution remains in `scripts/draftout/assets/README.md`.

Goal definitions, icons, and draft translations remain generated through `python3 scripts/draftout/generate.py`.

## Dev test tools

Only active in development launches (`gradle runClient`), never in release builds:

- Quick test of a few goals: `/bingo dev quick <goals...>` (draftout names like `lock_map`, or full ids) starts a game right away with only these goals on the card (the rest are "not drafted yet" placeholders): no draft, no lockout, no countdown, red team, a clear day, survival mode with an empty inventory. In a test script, the next line waits until it's playing.
- Run a test script and get its results with `scripts/dev/test.sh <script>` (or `-` to read the script from stdin). It starts the client if needed and leaves it running, so the next script takes seconds; after changing code, use `--restart`. For example:

  ```sh
  scripts/dev/test.sh - <<'EOF'
  bingo dev quick eat_cookie
  bingo dev grant me eat/cookie
  wait 5
  bingo dev expect achieved red true eat_cookie
  EOF
  ```

- `/bingo dev ...` (op): set up situations that are slow to reach by playing — `abort` (back to the pregame from any state), `team`, `fake` (offline team member), `grant` (also for offline players), `stat`, `resetplayer` (like Bingo's lobby-mode reset), `use` (like a right click), `interact`, `leash`, `pearl`, `lockmap` (cartography table + glass pane), `memento` (a Bingo card map) — and check results with `expect achieved|progress|advancement|score` (`me` for your own player).
- Test scripts: with `mc26.3/run/bingout-dev/autoplay` present, the client opens (or creates) the plain test world `BingoutDev`, publishes it to LAN so it keeps running behind menus, respawns right away after dying, runs `mc26.3/run/bingout-dev/script.txt` line by line (`wait`, `await <state>`, `echo`, `exit`, commands), and quits on `exit`. Results are logged with `[bingout-dev]` to the console. See `scripts/dev/draftout-regression.txt` (counters, offline teammates, drafting) and `scripts/dev/draftout-goals.txt` (falling off vines, locking maps, unique hostile mobs).

## GitHub CI and releases

`.github/workflows/build-release.yml` builds the Minecraft 26.3 target on Java 25. Pushes to `main`, pull requests, and manual Actions runs produce the `bingout-jars` artifact. Each run embeds `ci-<run-number>-<attempt>` in the version. It runs the 407 resource checks plus DraftController regressions; the two known upstream configuration failures are outside this targeted suite.

Push a `v*` tag (for example `v2.16.0`) to build and automatically create a GitHub Release containing the installable 26.3 JAR and `SHA256SUMS.txt`. Publishing a release in the GitHub UI also triggers building and attachment of its JARs. The tag must point to a commit containing this workflow. Builds must all pass before publishing. No external token is required; the publishing job uses the repository's `GITHUB_TOKEN` with `contents: write`.
