package org.falatia.emotes;

import net.fabricmc.api.ClientModInitializer;

import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

import org.falatia.emotes.compat.EmfCompat;

import org.falatia.emotes.emotemenu.EmoteWheel;
import org.falatia.emotes.emotestypes.EmoteNearby;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class FalatiaEmotes implements ClientModInitializer {

    public static final String MOD_ID = "falatia-emotes";

    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static final KeyMapping.Category KEY_CATEGORY = KeyMapping.Category.register(id("main"));

    @Override
    public void onInitializeClient() {
        Emotes.register();
        EmoteWheel.register();
        EmoteSync.register();
        EmoteRelay.register();
        EmoteNearby.register();
        EmfCompat.register();
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
