package me.jfenn.bingo.mixin;

import me.jfenn.bingo.common.draft.DraftoutTriggerController;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A firework rocket shot from a crossbow: "Shoot a Firework from a Crossbow" (draftout goal) */
@Mixin(CrossbowItem.class)
public abstract class CrossbowItemMixin {
    @Inject(method = "createProjectile", at = @At("RETURN"))
    private void yamb$onCreateProjectile(Level level, LivingEntity shooter, ItemStack weapon, ItemStack ammo, boolean isCrit, CallbackInfoReturnable<Projectile> cir) {
        if (!level.isClientSide() && cir.getReturnValue() instanceof FireworkRocketEntity) {
            DraftoutTriggerController.onCrossbowFirework(shooter);
        }
    }
}
