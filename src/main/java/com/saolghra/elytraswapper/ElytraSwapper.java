package com.saolghra.elytraswapper;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

/**
 * Loader-agnostic core: owns the keybind and the tick handler.
 *
 * Registration itself is deliberately NOT done here — each loader has a supported API for it
 * (Fabric's KeyBindingHelper, NeoForge's RegisterKeyMappingsEvent) which gets the ordering right by
 * construction. An earlier version of this mod hand-rolled registration by injecting into
 * Options.keyMappings, which silently broke keybind persistence: load() applies the stored
 * keybinds before a late-registered mapping exists, so the saved key was discarded on every launch
 * even though save() still wrote it. Do not reintroduce that.
 */
public final class ElytraSwapper {

    public static final String MOD_ID = "elytraswapper";
    public static final String KEY_TRANSLATION = "key.elytraswapper.swap";
    public static final int DEFAULT_KEY = GLFW.GLFW_KEY_GRAVE_ACCENT;

    private ElytraSwapper() {
    }

    /**
     * The swap keybind.
     *
     * 1.21.9 replaced the String category argument with the KeyMapping.Category record — a hard
     * cutover, not an added overload, so both arms are required.
     */
    public static final KeyMapping SWAP_KEY =
            //? if >=1.21.9 {
            /*new KeyMapping(KEY_TRANSLATION, DEFAULT_KEY, KeyMapping.Category.MISC);
            *///?} else {
            new KeyMapping(KEY_TRANSLATION, DEFAULT_KEY, "key.categories.misc");
            //?}

    /**
     * Drains any queued presses and performs the swap.
     *
     * consumeClick() rather than reacting to the key-down event directly: it runs the swap on the
     * client tick thread, which is where inventory packets belong.
     */
    public static void handleClientTick(Minecraft client) {
        if (client.player == null) {
            return;
        }
        while (SWAP_KEY.consumeClick()) {
            InventoryUtils.swapChestplate(client);
        }
    }
}
