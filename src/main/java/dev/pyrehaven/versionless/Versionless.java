package dev.pyrehaven.versionless;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Versionless — backward-compatibility bridge for Fabric mods 1.20+ → MC 26.1
 *
 * Architecture:
 * Phase 1: Generates fabric_loader_dependencies.json at preLaunch to let old mods
 *          pass Fabric Loader dependency resolution (requires one restart)
 * Phase 2: Patches old mod bytecode at runtime for API mapping compatibility
 */
public class Versionless implements ModInitializer {
    public static final String MOD_ID = "versionless";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("🔥 Versionless v0.1.0-test.3 initializing");
        LOGGER.info("──────────────────────────────────────");
        LOGGER.info("Supported: 1.20 – 1.21.11 mods → MC 26.1.1");
        LOGGER.info("──────────────────────────────────────");

        EnvType env = FabricLoader.getInstance().getEnvironmentType();
        LOGGER.info("Environment: {}", env);

        // Check which old mods we successfully bridged
        var checker = new BridgedModChecker();
        int count = checker.countBridgedMods();
        if (count > 0) {
            LOGGER.info("✅ Successfully bridged {} mod(s) to run on 26.1.1!", count);
            checker.logBridgedMods();
        } else {
            LOGGER.info("No bridged mods detected — add any 1.20-1.21.11 mods and restart");
        }

        LOGGER.info("Versionless initialized");
    }
}
