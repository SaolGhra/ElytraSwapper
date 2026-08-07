package com.saolghra.elytraswapper.mixins.client;

import java.util.Arrays;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.saolghra.elytraswapper.client.ElytraswapperClient;
import com.saolghra.elytraswapper.client.SwapKeyBinding;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Options;

@Mixin(Options.class)
public abstract class OptionsKeyMappingMixin {

    @Mutable
    @Shadow(remap = false)
    @Final
    public KeyMapping[] keyMappings;

    // HEAD, not TAIL. load() applies the saved keybinds by handing this.keyMappings to
    // processOptions(), so a mapping appended afterwards never receives its stored key and silently
    // falls back to the default on every launch — save() still wrote it, which is why it looked like
    // it had stuck until the next restart. HEAD also runs before load()'s early return for a missing
    // options.txt, which TAIL skips entirely on a fresh profile.
    @SuppressWarnings("unused")
    @Inject(method = "load", at = @At("HEAD"), remap = false)
    private void elytraswapper$registerCustomKeyMapping(CallbackInfo ci) {
        SwapKeyBinding custom = ElytraswapperClient.keyBinding;
        if (custom == null) {
            return;
        }

        for (KeyMapping mapping : this.keyMappings) {
            if (mapping == custom || mapping.getName().equals(custom.getName())) {
                return;
            }
        }

        KeyMapping[] expanded = Arrays.copyOf(this.keyMappings, this.keyMappings.length + 1);
        expanded[this.keyMappings.length] = custom;
        this.keyMappings = expanded;

        // Rebuild the key -> mapping index so input dispatch includes the injected key.
        KeyMapping.resetMapping();
    }
}
