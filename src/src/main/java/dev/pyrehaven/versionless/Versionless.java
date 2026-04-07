package dev.pyrehaven.versionless;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Versionless — backward-compatibility bridge for Fabric mods 1.20+ to MC 26.1
 * 
 * Architecture: Old mods go in mods/versionless-mods/ — FabricLoader never scans
 * this folder, so no dependency conflicts. We patch and stage them at preLaunch.
 */
public class Versionless implements ModInitializer {
    public static final String MOD_ID = "versionless";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Versionless v0.1.0-test.4 initializing");
        int bridged = VersionlessBridge.getBridgeCount();
        if (bridged > 0) {
            LOGGER.info("Successfully bridged {} mod(s) from versionless-mods/", bridged);
            VersionlessBridge.dumpBridgedMods();
        } else {
            LOGGER.info("No mods in versionless-mods/ — place 1.20-1.21.11 mods there");
        }
    }
}
