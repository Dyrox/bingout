package me.jfenn.bingo.mixin;

import me.jfenn.bingo.common.draft.DraftoutTriggerController;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.PotentSulfurBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Entities above an erupting geyser: "Ride a Geyser" (draftout goal).
 *
 * The launch ticker (LAUNCH_ENTITY_TICKER, compiled to this lambda) pushes every entity in the geyser's column up,
 * but skips players on the server: their client moves them. Checking for that is where the server sees the player
 * in an active geyser.
 */
@Mixin(PotentSulfurBlockEntity.class)
public abstract class PotentSulfurBlockEntityMixin {
    @Redirect(
        method = "lambda$static$5",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;canSimulateMovement()Z")
    )
    private static boolean yamb$onGeyserLaunch(Entity entity) {
        boolean canSimulateMovement = entity.canSimulateMovement();
        DraftoutTriggerController.onGeyserLaunch(entity);
        return canSimulateMovement;
    }
}
