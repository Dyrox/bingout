package me.jfenn.bingo.common.dev

import me.jfenn.bingo.api.state.GameAction
import me.jfenn.bingo.api.state.GameState
import me.jfenn.bingo.common.draft.DraftController
import me.jfenn.bingo.common.event.ScopedEvents
import me.jfenn.bingo.common.scope.BingoComponent
import me.jfenn.bingo.common.state.BingoState
import me.jfenn.bingo.common.timer.CountdownPacket
import me.jfenn.bingo.common.timer.CountdownService
import me.jfenn.bingo.platform.IModEnvironment
import me.jfenn.bingo.platform.IPlayerManager
import me.jfenn.bingo.platform.event.IEventBus
import net.minecraft.server.MinecraftServer
import org.slf4j.Logger
import java.util.UUID

/**
 * Development launches only: `/bingo dev quick <goals...>` starts a game right away with just these goals, to test
 * them (see [DevCommand]). Settings are made with the usual commands, one per tick, like a test script would:
 * - back to the pregame from any state (a running game or draft),
 * - no draft, no lockout, a full card to win; the player joins the red team,
 * - the card has the goals, the rest of it "not drafted yet" placeholders (never completed),
 * - no countdown; once playing, it's a clear day, and the player is in survival mode with an empty inventory.
 *
 * [DevScriptRunner] waits while this [isRunning], so a test script can go on right after the quick command.
 */
internal class DevQuickStart(
    events: ScopedEvents,
    environment: IModEnvironment,
    private val log: Logger,
    private val server: MinecraftServer,
    private val state: BingoState,
    private val playerManager: IPlayerManager,
    private val eventBus: IEventBus,
    private val countdownService: CountdownService,
) : BingoComponent() {

    companion object {
        private const val PREFIX = "[bingout-dev]"
        private val TILES = (1..5).flatMap { row -> "bingo".map { col -> "$col$row" } }
        private const val TIMEOUT_TICKS = 60 * 20
    }

    private var playerId: UUID? = null
    private val commands = ArrayDeque<String>()
    private var goals = listOf<String>()
    private var skipCountdown = false
    private var ticksLeft = 0

    val isRunning get() = playerId != null

    fun start(player: UUID, goals: List<String>) {
        require(goals.size <= TILES.size) { "A card has ${TILES.size} tiles" }
        playerId = player
        this.goals = goals
        ticksLeft = TIMEOUT_TICKS
        commands.clear()
        commands += listOf("bingo mode draftout false", "bingo mode lockout false", "bingo goal full_card", "bingo dev team red")
        TILES.forEachIndexed { i, tile -> commands += "bingo card set $tile ${goals.getOrElse(i) { DraftController.PLACEHOLDER_ID }}" }
        commands += "bingo start ignore_warnings"
        skipCountdown = true
        log.info("$PREFIX quick: starting a game with {}", goals)

        if (state.state != GameState.PREGAME) {
            state.reset()
            state.changeState(eventBus, GameState.PREGAME)
        }
    }

    private fun run(command: String) {
        val player = playerManager.getPlayer(playerId ?: return)?.player
        val source = player?.createCommandSourceStack() ?: server.createCommandSourceStack()
        server.commands.performPrefixedCommand(source.withSuppressedOutput(), command)
    }

    private fun finish(message: String) {
        log.info("$PREFIX quick: {}", message)
        playerId = null
        skipCountdown = false
        commands.clear()
    }

    private fun tick() {
        if (ticksLeft-- <= 0) {
            log.warn("$PREFIX FAIL quick: timed out (state is {})", state.state.identifier)
            finish("gave up")
            return
        }

        commands.removeFirstOrNull()?.let {
            run(it)
            return
        }

        when (state.state) {
            // as with a countdown of 0 seconds (CountdownController)
            GameState.COUNTDOWN -> if (skipCountdown) {
                skipCountdown = false
                countdownService.sendCountdownPacket(CountdownPacket(0))
                state.changeState(eventBus, GameAction.START)
            }
            GameState.PLAYING -> {
                for (command in listOf("gamemode survival @s", "clear @s", "effect clear @s", "time set day", "weather clear")) {
                    run(command)
                }
                playerManager.getPlayer(playerId ?: return)?.player?.let {
                    it.health = it.maxHealth
                    it.foodData.foodLevel = 20
                    it.foodData.setSaturation(5f)
                }
                finish("playing with $goals")
            }
            else -> Unit
        }
    }

    init {
        if (environment.isDevelopment) {
            events.onGameTick {
                if (isRunning) tick()
            }
        }
    }
}
