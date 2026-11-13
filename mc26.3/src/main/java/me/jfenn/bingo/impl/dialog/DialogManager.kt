package me.jfenn.bingo.impl.dialog

import me.jfenn.bingo.platform.IPlayerHandle
import me.jfenn.bingo.platform.dialog.*
import net.minecraft.core.Holder
import net.minecraft.network.protocol.common.ClientboundClearDialogPacket
import net.minecraft.server.dialog.Dialog
import net.minecraft.server.level.ServerPlayer

class DialogManager : IDialogManager {
    override fun noticeBuilder(): INoticeDialogBuilder? = NoticeDialogBuilder()
    override fun confirmationBuilder(): IConfirmationDialogBuilder? = ConfirmationDialogBuilder()
    override fun multiActionBuilder(): IMultiActionDialogBuilder? = MultiActionDialogBuilder()

    override fun showDialog(player: IPlayerHandle, dialog: IDialogHandle) {
        require(dialog is DialogHandle)
        val player: ServerPlayer = player.player
        player.openDialog(Holder.direct(dialog.dialog))
    }

    override fun closeDialog(player: IPlayerHandle) {
        val player: ServerPlayer = player.player
        player.connection.send(ClientboundClearDialogPacket.INSTANCE)
    }
}

class DialogHandle(
    val dialog: Dialog
) : IDialogHandle
