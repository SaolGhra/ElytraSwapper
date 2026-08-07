package com.saolghra.elytraswapper.neoforge;

import com.saolghra.elytraswapper.ElytraSwapper;

import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;

//? if >=1.20.5 {
import net.neoforged.neoforge.client.event.ClientTickEvent;
//?} else {
/*import net.neoforged.neoforge.event.TickEvent;
*///?}

/**
 * The client half of the NeoForge entrypoint. Loaded only on the client — see
 * {@link ElytraSwapperNeoForge} for why that matters.
 *
 * RegisterKeyMappingsEvent is NeoForge's equivalent of Fabric's KeyMappingHelper: it registers the
 * mapping at the point the game expects, before the saved keybinds are applied. That ordering is the
 * whole reason this mod no longer hand-rolls registration.
 *
 * The two listeners go on different buses on purpose — registration is a mod-lifecycle event and
 * belongs on the mod bus, while the client tick is a game event on the global bus.
 *
 * NeoForge 20.5 (Minecraft 1.20.5) replaced the single phase-carrying TickEvent.ClientTickEvent
 * with a split Pre/Post pair. Post is the direct equivalent of the old Phase.END.
 */
final class NeoForgeClientSetup {

    private NeoForgeClientSetup() {
    }

    static void register(IEventBus modEventBus) {
        modEventBus.addListener(NeoForgeClientSetup::registerKeyMappings);

        //? if >=1.20.5 {
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) ->
                ElytraSwapper.handleClientTick(Minecraft.getInstance()));
        //?} else {
        /*NeoForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent event) -> {
            if (event.phase == TickEvent.Phase.END) {
                ElytraSwapper.handleClientTick(Minecraft.getInstance());
            }
        });
        *///?}
    }

    private static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(ElytraSwapper.SWAP_KEY);
    }
}
