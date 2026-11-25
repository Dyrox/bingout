package me.jfenn.bingo.mixin;

import me.jfenn.bingo.common.draft.DraftoutTriggerController;
import me.jfenn.bingo.impl.TextImpl;
import me.jfenn.bingo.mixinhandler.PlayerEntityMixinHandler;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin {

    @Inject(at = @At(value = "HEAD"), method = "getTabListDisplayName", cancellable = true)
    public void getPlayerListName(CallbackInfoReturnable<Component> ci) {
        ServerPlayer player = (ServerPlayer) (Object) this;
        var originalName = player.getDisplayName();
        var name = PlayerEntityMixinHandler.Companion.getPlayerListName(
                player.getUUID(),
                new TextImpl(originalName != null ? originalName.copy() : Component.literal(player.getScoreboardName()))
        );
        if (name != null) {
            ci.setReturnValue(name.getValue());
            ci.cancel();
        }
    }

    /** Draftout's death goals, while the combat log still tells how the player died (it's cleared by the end of die) */
    @Inject(at = @At(value = "HEAD"), method = "die")
    private void yamb$onDie(DamageSource source, CallbackInfo ci) {
        DraftoutTriggerController.onPlayerDie((ServerPlayer) (Object) this, source);
    }

}
