package com.saolghra.elytraswapper.fabric;

import com.saolghra.elytraswapper.ElytraSwapper;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

//? if >=26.1 {
/*import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
*///?} else {
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
//?}

/**
 * Fabric entrypoint.
 *
 * Registering through Fabric API is the whole point of this class: it puts the mapping in place
 * before Options reads the saved keybinds. Getting that order wrong is what made the keybind reset
 * to its default on every restart back when this mod injected into Options.keyMappings by hand.
 *
 * Fabric renamed this module at the 26.x boundary to match Minecraft's unobfuscated naming —
 * fabric-key-binding-api-v1 / KeyBindingHelper.registerKeyBinding became
 * fabric-key-mapping-api-v1 / KeyMappingHelper.registerKeyMapping. Same behaviour, different name.
 */
public final class ElytraSwapperFabric implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        //? if >=26.1 {
        /*KeyMappingHelper.registerKeyMapping(ElytraSwapper.SWAP_KEY);
        *///?} else {
        KeyBindingHelper.registerKeyBinding(ElytraSwapper.SWAP_KEY);
        //?}

        ClientTickEvents.END_CLIENT_TICK.register(ElytraSwapper::handleClientTick);
    }
}
