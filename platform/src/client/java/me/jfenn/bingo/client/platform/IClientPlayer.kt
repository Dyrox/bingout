package me.jfenn.bingo.client.platform

import me.jfenn.bingo.platform.text.IText

interface IClientPlayer {
    fun sendHotbarMessage(text: IText)
    /** Shows a message in the player's chat (only visible to them) */
    fun sendChatMessage(text: IText) = sendHotbarMessage(text)
    fun sendCommand(command: String)
}