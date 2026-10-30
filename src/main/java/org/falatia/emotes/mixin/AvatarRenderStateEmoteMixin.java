package org.falatia.emotes.mixin;

import net.minecraft.client.renderer.entity.state.AvatarRenderState;

import org.falatia.emotes.emotestypes.EmoteNearby;
import org.falatia.emotes.EmotePose;
import org.falatia.emotes.interfaces.EmoteRenderStateHolder;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(AvatarRenderState.class)
public abstract class AvatarRenderStateEmoteMixin implements EmoteRenderStateHolder {

    @Unique
    private final EmotePose falatia$emotePose = new EmotePose();

    @Unique
    private final EmoteNearby.Label falatia$emoteLabel = new EmoteNearby.Label();

    @Override
    public EmotePose falatia$emotePose() {
        return this.falatia$emotePose;
    }

    @Override
    public EmoteNearby.Label falatia$emoteLabel() {
        return this.falatia$emoteLabel;
    }
}
