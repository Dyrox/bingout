package me.jfenn.bingo.common.ready

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface PersistedVoteType {
    val voteType: VoteType?

    @Serializable
    @SerialName("none")
    data object None : PersistedVoteType {
        override val voteType: VoteType? = null
    }

    @Serializable
    @SerialName("vote")
    data class Vote(
        override val voteType: VoteType,
    ) : PersistedVoteType
}