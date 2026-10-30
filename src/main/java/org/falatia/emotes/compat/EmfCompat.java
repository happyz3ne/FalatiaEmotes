package org.falatia.emotes.compat;

import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.world.entity.Entity;

import org.falatia.emotes.Emotes;
import org.falatia.emotes.FalatiaEmotes;

import traben.entity_model_features.EMFAnimationApi;

public final class EmfCompat {

    private EmfCompat() {
    }

    public static void register() {
        if (!FabricLoader.getInstance().isModLoaded("entity_model_features")) {
            return;
        }
        Hook.register();
    }

    private static final class Hook {

        static void register() {
            try {
                EMFAnimationApi.registerPauseCondition(entity -> (Object) entity instanceof Entity e && Emotes.emoting(e.getUUID()));
            } catch (Exception | LinkageError e) {
                FalatiaEmotes.LOGGER.warn("Emotes: can't hook into Entity Model Features: {}", e.toString());
            }
        }
    }
}
