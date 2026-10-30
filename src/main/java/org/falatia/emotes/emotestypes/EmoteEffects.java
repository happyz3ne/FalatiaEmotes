package org.falatia.emotes.emotestypes;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.phys.Vec3;
import org.falatia.emotes.Emote;
import org.falatia.emotes.interfaces.EmoteEvent;

public final class EmoteEffects {

    private EmoteEffects() {
    }

    public static void play(Avatar entity, Emote emote, EmoteEvent event, float seconds) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!(entity.level() instanceof ClientLevel level) || minecraft.player == null || entity.isInvisibleTo(minecraft.player)) {
            return;
        }
        switch (event) {
            case EmoteEvent.Sound sound -> level.playLocalSound(entity, SoundEvent.createVariableRangeEvent(sound.id()),
                    SoundSource.PLAYERS, Mth.clamp(sound.volume(), 0.0F, 4.0F), Mth.clamp(sound.pitch(), 0.1F, 2.0F));
            case EmoteEvent.Particles particles -> {
                ParticleOptions options = particles.options(level.registryAccess());
                if (options == null) {
                    return;
                }
                Vec3 at = EmoteLocator.locate(entity, emote, seconds, particles.locator, particles.offset);
                RandomSource random = level.getRandom();
                for (int i = 0; i < particles.count; i++) {
                    level.addParticle(options,
                            at.x + random.nextGaussian() * particles.spread[0],
                            at.y + random.nextGaussian() * particles.spread[1],
                            at.z + random.nextGaussian() * particles.spread[2],
                            random.nextGaussian() * particles.speed,
                            random.nextGaussian() * particles.speed,
                            random.nextGaussian() * particles.speed);
                }
            }
        }
    }
}
