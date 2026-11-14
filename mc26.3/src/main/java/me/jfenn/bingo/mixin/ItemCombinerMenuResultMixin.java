package me.jfenn.bingo.mixin;

import me.jfenn.bingo.common.draft.DraftoutTriggerController;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Taking the result of an anvil or smithing table counts as using it (for the draftout goals) */
@Mixin({AnvilMenu.class, SmithingMenu.class})
public abstract class ItemCombinerMenuResultMixin {
    @Inject(method = "onTake(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;)V", at = @At("HEAD"))
    private void yamb$onTakeResult(Player player, ItemStack stack, CallbackInfo ci) {
        DraftoutTriggerController.onWorkstationResultTaken(player);
    }
}
