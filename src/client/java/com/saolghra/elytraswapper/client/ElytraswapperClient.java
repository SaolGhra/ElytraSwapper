package com.saolghra.elytraswapper.client;

import org.lwjgl.glfw.GLFW;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;

public class ElytraswapperClient implements ClientModInitializer {

    public static SwapKeyBinding keyBinding;

    @Override
    public void onInitializeClient() {

        // Create and log the key binding
        keyBinding = new SwapKeyBinding("Elytra-Chestplate Swapping", GLFW.GLFW_KEY_GRAVE_ACCENT, "Elytra Swapper"); // category = category.ecs translationkey: key.ecs.swap
        KeyBindingHelper.registerKeyBinding(keyBinding);
    }
}