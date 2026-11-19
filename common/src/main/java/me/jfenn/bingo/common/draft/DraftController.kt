package me.jfenn.bingo.common.draft

import me.jfenn.bingo.api.data.BingoGameStatus
import me.jfenn.bingo.api.state.GameAction
import me.jfenn.bingo.api.state.GameState
import me.jfenn.bingo.common.event.packet.ServerPacketEvents
import me.jfenn.bingo.common.map.CardImageService
import me.jfenn.bingo.common.map.CardTile
import me.jfenn.bingo.common.map.CardViewService
import me.jfenn.bingo.common.map.toCardTileImage
import me.jfenn.bingo.common.MOD_ID_BINGO
import me.jfenn.bingo.common.card.BingoCard
import me.jfenn.bingo.common.card.CardGeneratorState
import me.jfenn.bingo.common.card.CardService
import me.jfenn.bingo.common.card.data.ObjectiveData
import me.jfenn.bingo.common.card.filter.ObjectiveFilterList
import me.jfenn.bingo.common.card.objective.BingoObjectiveManager
import me.jfenn.bingo.common.card.objective.display.ObjectiveDisplay
import me.jfenn.bingo.common.card.objective.display.ObjectiveDisplayService
import me.jfenn.bingo.common.data.ScopedData
import me.jfenn.bingo.common.event.ScopedEvents
import me.jfenn.bingo.common.event.model.OptionsChangedEvent
import me.jfenn.bingo.common.scope.BingoComponent
import me.jfenn.bingo.common.state.BingoState
import me.jfenn.bingo.common.team.BingoTeam
import me.jfenn.bingo.common.team.BingoTeamKey
import me.jfenn.bingo.common.text.TextProvider
import me.jfenn.bingo.generated.StringKey
import me.jfenn.bingo.platform.IPlayerHandle
import me.jfenn.bingo.platform.IPlayerManager
import me.jfenn.bingo.platform.dialog.IDialogAction
import me.jfenn.bingo.platform.dialog.IDialogManager
import me.jfenn.bingo.platform.event.IEventBus
import me.jfenn.bingo.platform.text.Formatting
import me.jfenn.bingo.platform.text.HoverAction
import me.jfenn.bingo.platform.text.IText
import me.jfenn.bingo.platform.text.TextAction
import org.slf4j.Logger
import kotlin.random.Random

/**
 * "Draftout" mode: before the game starts, two teams take turns picking the goals on the card.
 *
 * Each turn offers [OFFER_SIZE] random goals from the "draftout" tier list; the team picks one within
 * [PICK_SECONDS] seconds (or a random one is chosen). Each team may reroll its offer once per draft.
 *
 * The draft runs in the [DRAFTING] game state, inserted between LOADING and COUNTDOWN, so players are
 * already at their spawnpoint (and frozen) and can scout the world while drafting.
 */
internal class DraftController(
    events: ScopedEvents,
    private val log: Logger,
    private val state: BingoState,
    private val eventBus: IEventBus,
    private val data: ScopedData,
    private val cardService: CardService,
    private val objectiveManager: BingoObjectiveManager,
    private val objectiveDisplayService: ObjectiveDisplayService,
    private val playerManager: IPlayerManager,
    private val dialogManager: IDialogManager,
    private val text: TextProvider,
    private val packets: ServerPacketEvents,
    private val images: CardImageService,
    private val cardViewService: CardViewService,
) : BingoComponent() {

    companion object {
        val DRAFTING = GameState("$MOD_ID_BINGO:drafting", BingoGameStatus.STARTING)

        const val TIER_LIST = "draftout"
        const val PLACEHOLDER_ID = "$MOD_ID_BINGO:draftout/placeholder"
        const val ITEMS_TO_WIN = 13
        val DRAFTOUT_FILTER = ObjectiveFilterList.fromString("+draftout -unobtainable")

        const val BOARD_SIZE = 25
        const val OFFER_SIZE = 2
        const val PICK_SECONDS = 10
        const val REROLLS_PER_TEAM = 1

        const val COMMAND_PICK = "bingo draft pick"
        const val COMMAND_REROLL = "bingo draft reroll"

        init {
            // Insert the drafting state into the game start sequence (only once per JVM)
            if (GameAction.START.getNext(GameState.LOADING) != DRAFTING) {
                GameAction.START.insertAfter(GameState.LOADING, DRAFTING)
            }
        }
    }

    sealed class Result {
        object Ok : Result()
        object NotDrafting : Result()
        object NotYourTurn : Result()
        object NoRerolls : Result()
    }

    private class Session(
        val cardId: java.util.UUID,
        val teams: List<BingoTeamKey>,
        val pool: MutableList<String>,
        val random: Random,
    ) {
        val picks = mutableListOf<String>()
        var offer = listOf<String>()
        val rerollsUsed = mutableMapOf<BingoTeamKey, Int>()
        var ticksRemaining = 0
        var turn = ""

        val currentTeam get() = teams[picks.size % teams.size]
    }

    private var session: Session? = null

    val isDrafting get() = session != null

    fun isDraftEnabled(card: BingoCard = state.getActiveCard()) = card.options.isDraftMode

    // ---------- pool ----------

    private fun buildPool(random: Random): MutableList<String> {
        val tierList = data.tierLists[TIER_LIST]
        if (tierList == null) {
            log.error("[DraftController] Tier list '$TIER_LIST' does not exist!")
            return mutableListOf()
        }

        // like Bingo's own cards: leave out advancements that a player has already made (they would be completed
        // right away when players aren't reset between games, e.g. without a lobby)
        val excluded = objectiveManager.listExcludedIds().toSet()
        fun isExcluded(id: String) = id in excluded ||
            (data.objectives[id] as? ObjectiveData.Advancement)?.advancement?.let { it in excluded } == true

        val all = (tierList.values + tierList.s + tierList.a + tierList.b + tierList.c + tierList.d)
            .map { it.item }
            .filter { it != PLACEHOLDER_ID && !isExcluded(it) }
            .toMutableSet()

        // only allow one (random) variant of each group, e.g. one "Obtain 64 <color> Wool"
        for (group in tierList.groups) {
            val members = group.filter { all.contains(it) }
            if (members.size <= 1) continue
            val keep = members.random(random)
            all.removeAll(members.toSet() - keep)
        }

        return all.shuffled(random).toMutableList()
    }

    private fun isSupported(id: String, random: Random): Boolean {
        return objectiveManager.find(id, CardGeneratorState(random)) != null
    }

    private fun drawOffer(session: Session): List<String> {
        val offer = mutableListOf<String>()
        while (offer.size < OFFER_SIZE && session.pool.isNotEmpty()) {
            val id = session.pool.removeAt(session.random.nextInt(session.pool.size))
            if (session.picks.contains(id)) continue
            if (!isSupported(id, session.random)) {
                log.warn("[DraftController] Skipping unsupported draftout goal $id")
                continue
            }
            offer += id
        }
        return offer
    }

    // ---------- display ----------

    private fun resolveDisplay(id: String): ObjectiveDisplay.Resolved {
        val objective = objectiveManager.find(id, CardGeneratorState.DEFAULT)
        val fallback = objective?.display ?: ObjectiveDisplay.Resolved.EMPTY
        return objectiveDisplayService.resolve(id, data.objectives[id]?.display, fallback)
    }

    private fun goalName(id: String): IText {
        return resolveDisplay(id).name?.copy() ?: text.literal(id)
    }

    private fun getTeam(key: BingoTeamKey): BingoTeam? = state.getRegisteredTeams().find { it.key == key }

    private fun teamName(key: BingoTeamKey): IText {
        return getTeam(key)?.getName(text) ?: text.literal(key.label)
    }

    private fun teamPlayers(key: BingoTeamKey): List<IPlayerHandle> {
        return getTeam(key)?.players?.mapNotNull { playerManager.getPlayer(it.uuid) } ?: emptyList()
    }

    private fun allPlayers(): List<IPlayerHandle> = playerManager.getPlayers()

    private fun broadcast(message: IText) {
        log.info("[DraftController] {}", message.value.string)
        for (player in allPlayers()) player.sendMessage(message.copy())
    }

    /** Tiles are built exactly like the board builds them (multi-item icons etc.), cached per goal */
    private val tileCache = mutableMapOf<String, CardTile>()

    private fun tile(id: String): CardTile = tileCache.getOrPut(id) {
        cardService.createPreviewCard(id, state.getActiveCard().options)
            ?.let { cardViewService.getPreviewTile(it) }
            ?: resolveDisplay(id).let { display ->
                CardTile(id = id, image = display.toCardTileImage(), name = display.name, lore = display.lore.orEmpty())
            }
    }

    private fun sync(session: Session, players: List<IPlayerHandle> = allPlayers()) {
        val offers = session.offer.map(::tile)
        val picks = session.picks.map(::tile)
        images.draftImages = (offers + picks)
            .flatMap { tile -> listOf(tile.image) + tile.imageList }
            .mapNotNull { it.texture }
            .toSet()
        images.sendNecessaryImages()
        for (player in players) {
            packets.draftStateV1.send(player, DraftPacket(
                turn = session.turn,
                canPick = getTeam(session.currentTeam)?.includesPlayer(player.profile) == true,
                ticksRemaining = session.ticksRemaining,
                currentTeam = session.teams.indexOf(session.currentTeam),
                teams = session.teams.map(::teamName),
                rerolls = session.teams.map { REROLLS_PER_TEAM - (session.rerollsUsed[it] ?: 0) },
                offers = offers,
                picks = picks,
            ))
        }
    }

    private fun closeScreens() {
        images.draftImages = emptySet()
        for (player in allPlayers()) {
            packets.draftStateV1.send(player, DraftPacket())
            if (!packets.draftStateV1.isSupported(player)) dialogManager.closeDialog(player)
        }
        images.sendNecessaryImages()
    }

    private fun showOffer(session: Session) {
        sync(session)
        val team = session.currentTeam
        val rerollsLeft = REROLLS_PER_TEAM - (session.rerollsUsed[team] ?: 0)
        val title = text.string(StringKey.DraftTitle, session.picks.size + 1, BOARD_SIZE)

        for (player in allPlayers().filter { !packets.draftStateV1.isSupported(it) }) {
            val canPick = getTeam(team)?.includesPlayer(player.profile) == true
            // Chat message with clickable options (works without dialog support)
            val message = text.empty()
                .append(title.copy().formatted(Formatting.GOLD, Formatting.BOLD))
                .append("\n")
                .append(text.string(StringKey.DraftOffer).formatted(Formatting.GRAY))
            session.offer.forEachIndexed { i, id ->
                val option = text.literal("\n [${i + 1}] ").formatted(Formatting.GREEN, Formatting.BOLD)
                    .append(goalName(id).formatted(Formatting.WHITE))
                option.setClickEvent(TextAction.RunCommand("/$COMMAND_PICK ${i + 1}"))
                option.setHoverEvent(HoverAction.ShowText(text.string(StringKey.DraftPick)))
                message.append(option)
            }
            if (rerollsLeft > 0) {
                val reroll = text.literal("\n ").append(
                    text.string(StringKey.DraftReroll, rerollsLeft).bracketed().formatted(Formatting.YELLOW)
                )
                reroll.setClickEvent(TextAction.RunCommand("/$COMMAND_REROLL"))
                message.append(reroll)
            }
            player.sendMessage(message)
            if (!canPick) continue

            // Dialog with the goal items
            val builder = dialogManager.multiActionBuilder() ?: continue
            builder.title = title
            builder.columns = OFFER_SIZE
            builder.addText(text.string(StringKey.DraftOffer))
            session.offer.forEachIndexed { i, id ->
                val display = resolveDisplay(id)
                val name = display.name?.copy() ?: text.literal(id)
                display.item?.copy()?.let { stack ->
                    stack.setDisplay(name = name.copy(), lore = display.lore.orEmpty())
                    builder.addItem(stack)
                }
                builder.addAction(
                    text.literal("${i + 1}. ").append(name),
                    IDialogAction.RunCommand("$COMMAND_PICK ${i + 1}"),
                )
            }
            if (rerollsLeft > 0) {
                builder.addAction(
                    text.string(StringKey.DraftReroll, rerollsLeft),
                    IDialogAction.RunCommand(COMMAND_REROLL),
                )
            }
            dialogManager.showDialog(player, builder.build())
        }

        // close the dialog for the team that is now waiting
        for (otherTeam in session.teams.filter { it != team }) {
            teamPlayers(otherTeam).filter { !packets.draftStateV1.isSupported(it) }.forEach { dialogManager.closeDialog(it) }
        }
    }

    private fun updateBoard(session: Session) {
        val card = state.getCard(session.cardId) ?: return
        cardService.setEntries(card, session.picks, PLACEHOLDER_ID, TIER_LIST)
        eventBus.emit(OptionsChangedEvent)
    }

    // ---------- lifecycle ----------

    private fun start() {
        val card = state.getActiveCard()
        if (!isDraftEnabled(card)) {
            state.changeState(eventBus, GameAction.START)
            return
        }

        val teams = state.getRegisteredTeams().filter { it.players.isNotEmpty() }
        if (teams.size != 2) {
            broadcast(text.string(StringKey.DraftSkipped).formatted(Formatting.RED))
            state.changeState(eventBus, GameAction.START)
            return
        }

        val random = Random(card.seed)
        val session = Session(
            cardId = card.id,
            teams = teams.map { it.key }.shuffled(random),
            pool = buildPool(random),
            random = random,
        )
        this.session = session

        updateBoard(session)
        broadcast(text.string(StringKey.DraftStarted, teamName(session.currentTeam)).formatted(Formatting.GOLD))
        nextTurn(session)
    }

    private fun nextTurn(session: Session) {
        if (session.picks.size >= BOARD_SIZE) {
            finish()
            return
        }

        session.offer = drawOffer(session)
        if (session.offer.isEmpty()) {
            log.error("[DraftController] Ran out of draftout goals after ${session.picks.size} picks!")
            finish()
            return
        }

        session.turn = java.util.UUID.randomUUID().toString()
        session.ticksRemaining = PICK_SECONDS * 20
        showOffer(session)
    }

    private fun finish() {
        val session = this.session ?: return
        this.session = null

        closeScreens()

        // fill any remaining slots randomly (only if the pool ran out)
        updateBoard(session)
        broadcast(text.string(StringKey.DraftComplete).formatted(Formatting.GREEN, Formatting.BOLD))

        if (state.state == DRAFTING) {
            state.changeState(eventBus, GameAction.START)
        }
    }

    private fun pickInternal(session: Session, index: Int, isRandom: Boolean) {
        val team = session.currentTeam
        val id = session.offer.getOrNull(index) ?: return

        session.picks += id
        // offered goals that weren't picked are returned to the pool
        session.pool += session.offer.filter { it != id }
        session.offer = emptyList()

        val key = if (isRandom) StringKey.DraftRandomPick else StringKey.DraftPicked
        broadcast(text.string(key, teamName(team), goalName(id).formatted(Formatting.YELLOW)))

        updateBoard(session)
        nextTurn(session)
    }

    fun choose(player: IPlayerHandle, turn: String, option: Int): Result {
        val current = session ?: return Result.NotDrafting
        if (current.turn != turn) return Result.NotDrafting
        return if (option == DraftActionPacket.REROLL) reroll(player) else pick(player, option)
    }

    fun pick(player: IPlayerHandle, index: Int): Result {
        val session = this.session ?: return Result.NotDrafting
        val team = getTeam(session.currentTeam)
        if (team == null || !team.includesPlayer(player.profile)) return Result.NotYourTurn
        if (index !in session.offer.indices) return Result.NotDrafting

        pickInternal(session, index, isRandom = false)
        return Result.Ok
    }

    fun reroll(player: IPlayerHandle): Result {
        val session = this.session ?: return Result.NotDrafting
        val teamKey = session.currentTeam
        val team = getTeam(teamKey)
        if (team == null || !team.includesPlayer(player.profile)) return Result.NotYourTurn

        val used = session.rerollsUsed[teamKey] ?: 0
        if (used >= REROLLS_PER_TEAM) return Result.NoRerolls
        session.rerollsUsed[teamKey] = used + 1

        // rerolled goals are discarded for the rest of the draft
        broadcast(text.string(StringKey.DraftRerolled, teamName(teamKey)).formatted(Formatting.GRAY))
        nextTurn(session)
        return Result.Ok
    }

    private fun tick() {
        val session = this.session ?: return
        if (state.state != DRAFTING) {
            this.session = null
            return
        }

        session.ticksRemaining--

        if (session.ticksRemaining % 20 == 0) {
            sync(session)
            val seconds = (session.ticksRemaining / 20).coerceAtLeast(0)
            for (teamKey in session.teams) {
                val message = when (teamKey) {
                    session.currentTeam -> text.string(StringKey.DraftYourTurn, seconds).formatted(Formatting.GREEN)
                    else -> text.string(StringKey.DraftWaiting, teamName(session.currentTeam), seconds).formatted(Formatting.GRAY)
                }
                teamPlayers(teamKey).forEach { it.sendHotbarMessage(message.copy()) }
            }
            // spectators / players without a team
            allPlayers()
                .filter { p -> session.teams.none { getTeam(it)?.includesPlayer(p.profile) == true } }
                .forEach { it.sendHotbarMessage(text.string(StringKey.DraftWaiting, teamName(session.currentTeam), seconds)) }
        }

        if (session.ticksRemaining <= 0) {
            pickInternal(session, session.random.nextInt(session.offer.size), isRandom = true)
        }
    }

    init {
        events.onPacket(packets.draftActionV1) { (player, action) ->
            choose(player, action.turn, action.option)
        }
        events.onPlayerChannelRegister { event ->
            session?.takeIf { it.turn.isNotEmpty() }?.let { sync(it, listOf(event.player)) }
        }
        events.onClose { closeScreens() }
        events.onEnter(DRAFTING) { start() }
        events.onGameTick { tick() }
        events.onStateChange { (_, to) ->
            if (to != DRAFTING && session != null) {
                session = null
                closeScreens()
            }
        }
    }
}
