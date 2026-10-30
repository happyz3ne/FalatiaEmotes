package org.falatia.emotes;

import java.util.function.Supplier;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.FilterMode;

import net.minecraft.client.renderer.texture.DynamicTexture;

public final class SmoothTexture extends DynamicTexture {

    public SmoothTexture(Supplier<String> label, NativeImage image) {
        super(label, image);
        this.sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
    }
}
