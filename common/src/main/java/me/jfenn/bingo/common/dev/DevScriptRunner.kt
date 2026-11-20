package me.jfenn.bingo.common.dev

import me.jfenn.bingo.common.event.ScopedEvents
import me.jfenn.bingo.common.scope.BingoComponent
import me.jfenn.bingo.common.state.BingoState
import me.jfenn.bingo.platform.IModEnvironment
import me.jfenn.bingo.platform.IPlayerManager
import net.minecraft.server.MinecraftServer
import org.slf4j.Logger
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.readLines

/**
 * Development launches only: runs the test script `<game dir>/bingout-dev/script.txt` as soon as it exists and a
 * player is online, one line per tick, then deletes it. Commands run as the first online player (so that commands
 * needing a player work); their results and the `/bingo dev expect ...` checks are logged with "[bingout-dev]",
 * ending with a "RESULT" line.
 *
 * Script lines:
 * - `<command>`: run as the player (`@console <command>` runs it as the server)
 * - `wait <ticks>`
 * - `await <state> [seconds]`: wait for a game state (e.g. `await playing`), failing the script after a timeout
 * - `echo <text>`
 * - `exit`: quit the game once the script is done (handled by the client's DevAutoplay)
 * - `# comment`
 */
internal class DevScriptRunner(
    events: ScopedEvents,
    environment: IModEnvironment,
    private val log: Logger,
    private val server: MinecraftServer,
    private val state: BingoState,
    private val playerManager: IPlayerManager,
) : BingoComponent() {

    companion object {
        private const val PREFIX = "[bingout-dev]"
        private const val DEFAULT_AWAIT_SECONDS = 120
    }

    private val scriptFile = environment.gameDir.resolve("bingout-dev").resolve("script.txt")

    private val queue = ArrayDeque<String>()
    private var running = false
    private var exitWhenDone = false
    private var waitTicks = 0
    private var awaitState: String? = null
    private var awaitTicksLeft = 0
    private var passed = 0
    private var failed = 0
    private var ticks = 0

    fun record(pass: Boolean, message: String) {
        if (pass) {
            passed++
            log.info("$PREFIX PASS {}", message)
        } else {
            failed++
            log.warn("$PREFIX FAIL {}", message)
        }
    }

    private fun load() {
        val lines = scriptFile.readLines()
        scriptFile.deleteIfExists()
        queue.clear()
        queue.addAll(lines.map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") })
        running = true
        // also when the script stops early (a timed out "await"), so that a test run never hangs
        exitWhenDone = "exit" in queue
        passed = 0
        failed = 0
        log.info("$PREFIX Running {} ({} lines)", scriptFile, queue.size)
    }

    private fun finish() {
        running = false
        log.info("$PREFIX RESULT passed={} failed={}", passed, failed)
        if (exitWhenDone) {
            log.info("$PREFIX Exiting")
            DevSignals.exitRequested = true
            if (server.isDedicatedServer) server.halt(false)
        }
    }

    private fun isState(name: String): Boolean {
        val id = state.state.identifier
        return id.equals(name, ignoreCase = true) || id.endsWith(":$name", ignoreCase = true)
    }

    private fun runCommand(line: String) {
        val asConsole = line.startsWith("@console ")
        val command = line.removePrefix("@console ").removePrefix("/")
        val player = playerManager.getPlayers().firstOrNull()?.player
        val source = (if (asConsole || player == null) server.createCommandSourceStack() else player.createCommandSourceStack())
            .withCallback { success, result ->
                // a check that couldn't run (e.g. the goal isn't on the card) is a failed check
                if (!success && command.startsWith("bingo dev expect")) record(false, "could not run /$command")
                else if (!success) log.warn("$PREFIX command failed: /{}", command)
                else log.debug("$PREFIX command result {}: /{}", result, command)
            }
        log.info("$PREFIX > /{}", command)
        server.commands.performPrefixedCommand(source, command)
    }

    private fun step() {
        if (waitTicks > 0) {
            waitTicks--
            return
        }

        awaitState?.let { name ->
            when {
                isState(name) -> awaitState = null
                awaitTicksLeft-- <= 0 -> {
                    record(false, "timed out waiting for state '$name' (state is ${state.state.identifier})")
                    awaitState = null
                    queue.clear()
                }
                else -> return
            }
        }

        val line = queue.removeFirstOrNull()
        if (line == null) {
            finish()
            return
        }

        val (word, rest) = line.split(' ', limit = 2).let { it[0] to it.getOrElse(1) { "" }.trim() }
        try {
            when (word) {
                "wait" -> waitTicks = rest.toInt()
                "await" -> {
                    val parts = rest.split(' ')
                    awaitState = parts[0]
                    awaitTicksLeft = (parts.getOrNull(1)?.toIntOrNull() ?: DEFAULT_AWAIT_SECONDS) * 20
                }
                "echo" -> log.info("$PREFIX {}", rest)
                "exit" -> exitWhenDone = true
                else -> runCommand(line)
            }
        } catch (e: Exception) {
            record(false, "error on line '$line': $e")
            log.error("$PREFIX Error on line '{}'", line, e)
        }
    }

    init {
        if (environment.isDevelopment) {
            log.info("$PREFIX Development launch: test scripts are read from {}", scriptFile)
            events.onGameTick {
                ticks++
                when {
                    running -> step()
                    // checked once a second, with a player online for a few seconds (joined and loaded in)
                    ticks % 20 == 0 && scriptFile.exists() && playerManager.getPlayers().any { it.player.tickCount > 60 } -> load()
                }
            }
        }
    }
}
