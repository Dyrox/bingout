package me.jfenn.bingo.common.ready

import kotlinx.serialization.Serializable

@Serializable
enum class PostgameDefaultVoteType(
    val voteType: VoteType?,
) {
    NEXT_ROUND(VoteType.NEXT_ROUND),
    RETURN_TO_LOBBY(VoteType.RETURN_TO_LOBBY),
    KEEP_PLAYING(VoteType.KEEP_PLAYING),
    AUTO(null);
}