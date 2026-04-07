package dev.pyrehaven.versionless;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.api.EnvType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Versionless implements ModInitializer {
    public static final String MOD_ID = "versionless";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Versionless v0.1.0-test.3 initializing");
        int bridged = ModJarPatcher.getLastPatchCount();
        if (bridged > 0) {
            LOGGER.info("Bridging {} old mod(s) to MC 26.1.1!", bridged);
        }
        LOGGER.info("Supports: 1.20-1.21.11 mods on MC 26.1+");
    }
}
