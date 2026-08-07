package com.saolghra.elytraswapper.neoforge;

import com.saolghra.elytraswapper.ElytraSwapper;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * NeoForge entrypoint.
 *
 * RegisterKeyMappingsEvent is NeoForge's equivalent of Fabric's KeyMappingHelper: it registers the
 * mapping at the point the game expects, before the saved keybinds are applied. That ordering is the
 * whole reason this mod no longer hand-rolls registration.
 *
 * The two listeners go on different buses on purpose — registration is a mod-lifecycle event and
 * belongs on the mod bus, while the client tick is a game event on the global bus.
 */
@Mod(value = ElytraSwapper.MOD_ID, dist = Dist.CLIENT)
public final class ElytraSwapperNeoForge {

    public ElytraSwapperNeoForge(IEventBus modEventBus) {
        modEventBus.addListener(this::registerKeyMappings);
        NeoForge.EVENT_BUS.addListener(this::onClientTick);
    }

    private void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(ElytraSwapper.SWAP_KEY);
    }

    private void onClientTick(ClientTickEvent.Post event) {
        ElytraSwapper.handleClientTick(Minecraft.getInstance());
    }
}
