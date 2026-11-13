package me.jfenn.bingo.common.draft

import me.jfenn.bingo.common.MOD_ID_BINGO
import me.jfenn.bingo.common.map.CardTile
import me.jfenn.bingo.platform.IPacketBuf
import me.jfenn.bingo.platform.Identifier
import me.jfenn.bingo.platform.packet.PacketConverter
import me.jfenn.bingo.platform.text.IText

/** A complete snapshot, including offers, so waiting teams and reconnecting clients see the same draft. */
data class DraftPacket(
    val turn: String = "",
    val canPick: Boolean = false,
    val ticksRemaining: Int = 0,
    val currentTeam: Int = 0,
    val teams: List<IText> = emptyList(),
    val rerolls: List<Int> = emptyList(),
    val offers: List<CardTile> = emptyList(),
    val picks: List<CardTile> = emptyList(),
) {
    val active get() = turn.isNotEmpty()

    object V1 : PacketConverter<DraftPacket> {
        override val id = Identifier.of(MOD_ID_BINGO, "draft_state")
        override fun fromPacketBuf(buf: IPacketBuf) = DraftPacket(
            turn = buf.readString(),
            canPick = buf.readBoolean(),
            ticksRemaining = buf.readInt(),
            currentTeam = buf.readInt(),
            teams = buf.readList { buf.readText() },
            rerolls = buf.readList { buf.readInt() },
            offers = buf.readList { CardTile.V1.fromPacketBuf(buf) },
            picks = buf.readList { CardTile.V1.fromPacketBuf(buf) },
        )

        override fun toPacketBuf(source: DraftPacket, dest: IPacketBuf) {
            dest.writeString(source.turn)
            dest.writeBoolean(source.canPick)
            dest.writeInt(source.ticksRemaining)
            dest.writeInt(source.currentTeam)
            dest.writeList(source.teams) { dest.writeText(it) }
            dest.writeList(source.rerolls) { dest.writeInt(it) }
            dest.writeList(source.offers) { CardTile.V1.toPacketBuf(it, dest) }
            dest.writeList(source.picks) { CardTile.V1.toPacketBuf(it, dest) }
        }
    }
}

/** The opaque turn token changes on every pick AND reroll; stale clicks cannot affect a new offer. */
data class DraftActionPacket(val turn: String, val option: Int) {
    companion object { const val REROLL = -1 }
    object V1 : PacketConverter<DraftActionPacket> {
        override val id = Identifier.of(MOD_ID_BINGO, "draft_action")
        override fun fromPacketBuf(buf: IPacketBuf) = DraftActionPacket(buf.readString(), buf.readInt())
        override fun toPacketBuf(source: DraftActionPacket, dest: IPacketBuf) {
            dest.writeString(source.turn)
            dest.writeInt(source.option)
        }
    }
}
