package me.jfenn.bingo.mixin;

import me.jfenn.bingo.common.draft.DraftoutTriggerController;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.NameTagItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** After naming a mob with a name tag: "Name a Sheep 'jeb_'", "Name a Ghast 'Dinnerbone'", ... (draftout goals) */
@Mixin(NameTagItem.class)
public abstract class NameTagItemMixin {
    @Inject(method = "interactLivingEntity", at = @At("RETURN"))
    private void yamb$onNamed(ItemStack stack, Player player, LivingEntity target, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        if (player.level().isClientSide() || !cir.getReturnValue().consumesAction()) return;
        DraftoutTriggerController.onNameTagUsed(player, target);
    }
}
