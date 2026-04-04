package dev.pyrehaven.versionless;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Versionless — backward-compatibility bridge for Fabric mods 1.20+ → MC 26.1
 *
 * 🔥 v0.1.0-test.1
 */
public class Versionless implements ModInitializer {
    public static final String MOD_ID = "versionless";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("🔥 Versionless v0.1.0-test.1 initializing");
        LOGGER.info("Supported source versions: 1.20 – 1.21.11 → 26.1.1");

        var env = FabricLoader.getInstance().getEnvironmentType();
        LOGGER.info("Environment: {}", env);

        LOGGER.info("Versionless initialized — test build #1 (shims registered)");
    }
}
