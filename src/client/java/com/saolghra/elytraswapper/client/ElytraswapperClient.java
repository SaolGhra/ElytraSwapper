package com.saolghra.elytraswapper.client;

import org.lwjgl.glfw.GLFW;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.KeyMapping;

public class ElytraswapperClient implements ClientModInitializer {

    public static SwapKeyBinding keyBinding;

    @Override
    public void onInitializeClient() {

        // Create and log the key binding
        keyBinding = new SwapKeyBinding("Elytra-Chestplate Swapping", GLFW.GLFW_KEY_GRAVE_ACCENT, KeyMapping.Category.MISC); // category = category.ecs translationkey: key.ecs.swap
    }
}