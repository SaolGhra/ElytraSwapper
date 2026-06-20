package com.saolghra.elytraswapper.client;

import org.lwjgl.glfw.GLFW;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.KeyMapping;

public class ElytraswapperClient implements ClientModInitializer {

    // Eager init avoids load-order races where Options loads before onInitializeClient runs.
    public static final SwapKeyBinding keyBinding = new SwapKeyBinding(
            "key.elytraswapper.swap",
            GLFW.GLFW_KEY_GRAVE_ACCENT,
            KeyMapping.Category.MISC
    );

    @Override
    public void onInitializeClient() {
        // Key mapping is created eagerly in the static field above.
    }
}