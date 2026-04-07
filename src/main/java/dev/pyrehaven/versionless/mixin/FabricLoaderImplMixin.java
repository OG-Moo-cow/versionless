package dev.pyrehaven.versionless.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hooks into FabricLoaderImpl.load() at the very top — BEFORE setup()
 * is called, which means BEFORE dependency resolution and BEFORE any
 * mod metadata is read. We patch old mod JARs in-place so they pass
 * compatibility checks on the same launch.
 */
@Mixin(value = net.fabricmc.loader.impl.FabricLoaderImpl.class, remap = false, priority = -2000)
public abstract class FabricLoaderImplMixin {
    
    @Inject(method = "load", at = @At("HEAD"))
    private void patchOldModJarsBeforeResolution(CallbackInfo ci) {
        dev.pyrehaven.versionless.ModJarPatcher.run();
    }
}
