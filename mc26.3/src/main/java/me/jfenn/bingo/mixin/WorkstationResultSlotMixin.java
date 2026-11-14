package me.jfenn.bingo.mixin;

import me.jfenn.bingo.common.draft.DraftoutTriggerController;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The output slots of the stonecutter, loom, cartography table and grindstone: taking their result counts
 * as using the workstation (for the "Use Stonecutter", ... draftout goals).
 */
@Mixin(targets = {
    "net.minecraft.world.inventory.StonecutterMenu$2",
    "net.minecraft.world.inventory.LoomMenu$6",
    "net.minecraft.world.inventory.CartographyTableMenu$5",
    "net.minecraft.world.inventory.GrindstoneMenu$4",
})
public abstract class WorkstationResultSlotMixin {
    @Inject(method = "onTake(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;)V", at = @At("HEAD"))
    private void yamb$onTakeResult(Player player, ItemStack stack, CallbackInfo ci) {
        DraftoutTriggerController.onWorkstationResultTaken(player);
    }
}
