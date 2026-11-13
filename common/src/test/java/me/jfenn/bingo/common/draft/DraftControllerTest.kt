package me.jfenn.bingo.common.draft

import io.mockk.*
import me.jfenn.bingo.api.state.GameState
import me.jfenn.bingo.common.card.BingoCard
import me.jfenn.bingo.common.card.objective.BingoObjectiveManager
import me.jfenn.bingo.common.card.objective.display.ObjectiveDisplay
import me.jfenn.bingo.common.card.objective.display.ObjectiveDisplayService
import me.jfenn.bingo.common.card.tierlist.TierListConfig
import me.jfenn.bingo.common.card.tierlist.TierListEntry
import me.jfenn.bingo.common.data.ScopedData
import me.jfenn.bingo.common.event.ScopedEvents
import me.jfenn.bingo.common.event.packet.ServerPacketEvents
import me.jfenn.bingo.common.state.BingoState
import me.jfenn.bingo.common.team.BingoTeam
import me.jfenn.bingo.common.team.BingoTeamKey
import me.jfenn.bingo.platform.IPlayerHandle
import me.jfenn.bingo.platform.IPlayerManager
import me.jfenn.bingo.platform.event.model.TickEvent
import me.jfenn.bingo.platform.packet.ServerPacket
import me.jfenn.bingo.platform.player.PlayerProfile
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.UUID

class DraftControllerTest {
    private val events = mockk<ScopedEvents>(relaxed = true)
    private val packets = mockk<ServerPacketEvents>(relaxed = true)
    private val state = mockk<BingoState>(relaxed = true)
    private val playerManager = mockk<IPlayerManager>()
    private val snapshots = mutableMapOf<UUID, DraftPacket>()
    private val start = slot<(GameState) -> Unit>()
    private val tick = slot<(TickEvent) -> Unit>()
    private val action = slot<(ServerPacket<DraftActionPacket>) -> Unit>()
    private val players = List(3) {
        mockk<IPlayerHandle>(relaxed = true).also { player ->
            val profile = PlayerProfile(UUID.randomUUID(), "player$it")
            every { player.uuid } returns profile.uuid
            every { player.profile } returns profile
        }
    }
    private val teams = players.take(2).mapIndexed { i, player ->
        mockk<BingoTeam>(relaxed = true).also { team ->
            every { team.key } returns BingoTeamKey("team$i")
            every { team.players } returns listOf(BingoTeam.Player(player.profile))
            every { team.includesPlayer(any<PlayerProfile>()) } answers { firstArg<PlayerProfile>() == player.profile }
        }
    }
    private val controller: DraftController

    init {
        every { events.onEnter(DraftController.DRAFTING, any(), capture(start)) } just Runs
        every { events.onGameTick(capture(tick)) } just Runs
        every { events.onPacket(packets.draftActionV1, capture(action)) } just Runs
        every { packets.draftStateV1.isSupported(any<IPlayerHandle>()) } returns true
        every { packets.draftStateV1.send(any<IPlayerHandle>(), any()) } answers {
            snapshots[firstArg<IPlayerHandle>().uuid] = secondArg()
            true
        }
        every { playerManager.getPlayers() } returns players
        every { playerManager.getPlayer(any()) } answers { players.find { it.uuid == firstArg<UUID>() } }
        every { state.getRegisteredTeams() } returns teams
        every { state.state } returns DraftController.DRAFTING
        val card = mockk<BingoCard>(relaxed = true)
        every { card.id } returns UUID.randomUUID()
        every { card.seed } returns 1234L
        every { card.options.isDraftMode } returns true
        every { state.getActiveCard() } returns card
        every { state.getCard(any<UUID>()) } returns card
        val manager = mockk<BingoObjectiveManager>(relaxed = true)
        every { manager.find(any(), any()) } returns mockk(relaxed = true)
        val display = mockk<ObjectiveDisplayService>()
        every { display.resolve(any(), any(), any()) } answers {
            ObjectiveDisplay.Resolved(image = "test:${firstArg<String>()}")
        }
        controller = DraftController(
            events, mockk(relaxed = true), state, mockk(relaxed = true),
            ScopedData().apply { tierLists = mapOf("draftout" to TierListConfig(
                values = (0 until 80).map { TierListEntry(null, "goal$it") }.toSet()
            )) },
            mockk(relaxed = true), manager, display, playerManager,
            mockk(relaxed = true), mockk(relaxed = true), packets, mockk(relaxed = true),
        )
        start.captured(DraftController.DRAFTING)
    }

    private fun picker() = players.first { snapshots[it.uuid]?.canPick == true }
    private fun snapshot() = snapshots.getValue(players[0].uuid)
    private fun send(player: IPlayerHandle, turn: String, option: Int) =
        controller.choose(player, turn, option)

    @Test fun `both teams and spectators receive identical offers while only current team can act`() {
        assertEquals(3, snapshots.size)
        assertEquals(1, snapshots.values.count { it.canPick })
        assertEquals(1, snapshots.values.map { it.offers }.distinct().size)
        assertEquals(2, snapshot().offers.size)
        val turn = snapshot().turn
        players.filter { it != picker() }.forEach { send(it, turn, 0) }
        assertEquals(turn, snapshot().turn)
        assertEquals(0, snapshot().picks.size)
    }

    @Test fun `reroll invalidates old clicks and remains limited to one per team`() {
        val player = picker()
        val old = snapshot()
        send(player, old.turn, DraftActionPacket.REROLL)
        val next = snapshot()
        assertNotEquals(old.turn, next.turn)
        assertNotEquals(old.offers, next.offers)
        assertEquals(0, next.rerolls[next.currentTeam])
        send(player, old.turn, 0)
        send(player, next.turn, DraftActionPacket.REROLL)
        assertEquals(next.turn, snapshot().turn)
        assertTrue(snapshot().picks.isEmpty())
    }

    @Test fun `complete draft alternates teams updates everyone and closes all screens`() {
        repeat(25) { index ->
            val before = snapshot()
            val player = picker()
            send(player, before.turn, index % 2)
            if (index < 24) {
                assertEquals(index + 1, snapshot().picks.size)
                assertNotEquals(player.uuid, picker().uuid)
                assertEquals(1, snapshots.values.map { it.picks }.distinct().size)
                send(player, before.turn, 0)
                assertEquals(index + 1, snapshot().picks.size)
            }
        }
        assertFalse(controller.isDrafting)
        assertTrue(snapshots.values.none { it.active })
    }

    @Test fun `timeout picks once at ten seconds and gives both teams the next offer`() {
        repeat(199) { tick.captured(TickEvent(it)) }
        assertEquals(0, snapshot().picks.size)
        tick.captured(TickEvent(199))
        assertEquals(1, snapshot().picks.size)
        assertEquals(200, snapshot().ticksRemaining)
        assertEquals(1, snapshots.values.map { it.turn }.distinct().size)
    }
}
