package dev.pyrehaven.versionless;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModDependency;
import net.fabricmc.loader.api.metadata.ModMetadata;

import java.util.*;

/**
 * Checks which old mods have been successfully bridged.
 */
public class BridgedModChecker {
    
    private static final List<String> SUPPORTED_OLD = Arrays.asList(
        "1.20", "1.20.1", "1.20.2", "1.20.4", "1.20.6",
        "1.21", "1.21.1", "1.21.4", "1.21.5", "1.21.6",
        "1.21.8", "1.21.9", "1.21.10", "1.21.11"
    );
    
    public int countBridgedMods() {
        int count = 0;
        for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
            if (isOldMod(mod)) count++;
        }
        return count;
    }
    
    public void logBridgedMods() {
        for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
            if (isOldMod(mod)) {
                Versionless.LOGGER.info("  Bridged: {} {}", mod.getMetadata().getId(), mod.getMetadata().getVersion());
            }
        }
    }
    
    private boolean isOldMod(ModContainer mod) {
        String id = mod.getMetadata().getId();
        if (id.equals("versionless") || id.equals("fabricloader") || 
            id.equals("fabric-api") || id.equals("fabric") ||
            id.equals("minecraft") || id.equals("java")) {
            return false;
        }
        
        for (ModDependency dep : mod.getMetadata().getDependencies()) {
            if (!"minecraft".equals(dep.getModId())) continue;
            
            String range = dep.getVersionIntervals().toString();
            for (String v : SUPPORTED_OLD) {
                if (range.contains(v)) return true;
            }
        }
        return false;
    }
}
