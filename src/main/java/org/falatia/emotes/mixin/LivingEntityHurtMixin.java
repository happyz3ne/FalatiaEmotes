package org.falatia.emotes.mixin;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

import org.falatia.emotes.interfaces.HurtTracker;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityHurtMixin implements HurtTracker {

    @Unique
    private long falatia$lastHurtTime = Long.MIN_VALUE;

    @Inject(method = "handleDamageEvent", at = @At("HEAD"))
    private void falatia$rememberHurt(DamageSource source, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        this.falatia$lastHurtTime = self.level().getGameTime();
    }

    @Override
    public long falatia$lastHurtTime() {
        return this.falatia$lastHurtTime;
    }
}
