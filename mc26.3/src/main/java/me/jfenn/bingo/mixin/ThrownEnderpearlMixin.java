package me.jfenn.bingo.mixin;

import me.jfenn.bingo.common.draft.DraftoutTriggerController;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * An ender pearl landing, before it teleports its owner: "Throw an Ender Pearl through a Nether Portal" (draftout goal)
 * is a pearl that landed in another dimension than its owner.
 */
@Mixin(ThrownEnderpearl.class)
public abstract class ThrownEnderpearlMixin {
    @Shadow
    private static boolean isAllowedToTeleportOwner(Entity owner, Level level) {
        throw new AssertionError();
    }

    @Inject(method = "onHit", at = @At("HEAD"))
    private void yamb$onHit(HitResult hitResult, CallbackInfo ci) {
        ThrownEnderpearl pearl = (ThrownEnderpearl) (Object) this;
        Level level = pearl.level();
        if (!(level instanceof ServerLevel) || pearl.isRemoved()) return;
        Entity owner = pearl.getOwner();
        if (owner != null && isAllowedToTeleportOwner(owner, level)) {
            DraftoutTriggerController.onPearlTeleport(owner, level);
        }
    }
}
