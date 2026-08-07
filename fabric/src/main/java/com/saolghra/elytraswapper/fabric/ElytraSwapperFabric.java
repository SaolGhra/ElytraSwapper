package com.saolghra.elytraswapper.fabric;

import com.saolghra.elytraswapper.ElytraSwapper;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;

/**
 * Fabric entrypoint.
 *
 * KeyBindingHelper is the whole point of this class: it registers the mapping before Options reads
 * the saved keybinds, which is the ordering the old hand-rolled Options mixin got wrong. See
 * .claude/docs/keybind-persistence-bug.md.
 */
public final class ElytraSwapperFabric implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        KeyBindingHelper.registerKeyBinding(ElytraSwapper.SWAP_KEY);
        ClientTickEvents.END_CLIENT_TICK.register(ElytraSwapper::handleClientTick);
    }
}
