package com.saolghra.elytraswapper.neoforge;

import com.saolghra.elytraswapper.ElytraSwapper;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

//? if <1.20.6 {
/*import net.neoforged.fml.loading.FMLEnvironment;
*///?}

/**
 * NeoForge entrypoint.
 *
 * Everything that touches a client-only class lives in {@link NeoForgeClientSetup}, which is only
 * ever referenced from inside the dist check below. That keeps the client classes off a dedicated
 * server's class-loading path entirely, rather than relying on them merely never being called.
 *
 * @Mod grew its dist attribute in NeoForge 20.6 (Minecraft 1.20.6). Before that the loader has no
 * way to refuse a client-only mod on a server, so the mod has to bow out itself — hence the
 * FMLEnvironment guard, which is compiled in only where the annotation cannot do the job.
 */
//? if >=1.20.6 {
@Mod(value = ElytraSwapper.MOD_ID, dist = Dist.CLIENT)
//?} else {
/*@Mod(ElytraSwapper.MOD_ID)
*///?}
public final class ElytraSwapperNeoForge {

    public ElytraSwapperNeoForge(IEventBus modEventBus) {
        //? if <1.20.6 {
        /*if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }
        *///?}
        NeoForgeClientSetup.register(modEventBus);
    }
}
