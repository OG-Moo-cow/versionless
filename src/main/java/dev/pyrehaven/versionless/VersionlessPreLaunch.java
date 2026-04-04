package dev.pyrehaven.versionless;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/**
 * PreLaunch entrypoint — generates fabric_loader_dependencies.json
 * to allow old-mod dependency resolution BEFORE any mods load.
 * 
 * This runs during Fabric Loader's preLaunch phase, which is the
 * earliest mod code can execute. The file it generates is read by
 * Loader on the NEXT launch to override mod dependency requirements.
 */
public class VersionlessPreLaunch implements Runnable {
    
    public static final String CONFIG_FILENAME = "fabric_loader_dependencies.json";
    private static final String[] SUPPORTED = {
        "1.20", "1.20.1", "1.20.2", "1.20.4", "1.20.6",
        "1.21", "1.21.1", "1.21.4", "1.21.5", "1.21.6",
        "1.21.8", "1.21.9", "1.21.10", "1.21.11"
    };
    
    @Override
    public void run() {
        String loaderVersion = System.getProperty("fabric.loader.version", "unknown");
        String mcVersion = System.getProperty("minecraft.client.version", "unknown");
        
        Versionless.LOGGER.info("🔥 Versionless PreLaunch — generating dependency overrides");
        Versionless.LOGGER.info("  Fabric Loader: {}", loaderVersion);
        Versionless.LOGGER.info("  MC version: {}", mcVersion);
        
        // Scan mods directory for old mods
        List<String> oldModIds = scanModsForOldMods();
        
        if (oldModIds.isEmpty()) {
            Versionless.LOGGER.info("No old mods found that need overrides");
            return;
        }
        
        Versionless.LOGGER.info("Found {} old mod(s) that need dependency overrides:", oldModIds.size());
        for (String id : oldModIds) {
            Versionless.LOGGER.info("  - {}", id);
        }
        
        // Generate fabric_loader_dependencies.json
        generateDependencyOverrides(oldModIds);
        
        Versionless.LOGGER.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        Versionless.LOGGER.info("⚠️  GENERATED dependency overrides file");
        Versionless.LOGGER.info("⚠️  RESTART THE GAME for changes to take effect!");
        Versionless.LOGGER.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }
    
    List<String> scanModsForOldMods() {
        List<String> oldModIds = new ArrayList<>();
        String modsDir = System.getProperty("fabric.mod.folder");
        if (modsDir == null) {
            try {
                // Try default mods folder
                String gameDir = System.getProperty("user.dir");
                modsDir = gameDir + "/mods";
            } catch (Exception e) {
                return oldModIds;
            }
        }
        
        File mods = new File(modsDir);
        if (!mods.exists() || !mods.isDirectory()) {
            return oldModIds;
        }
        
        for (File jarFile : mods.listFiles((d, n) -> n.endsWith(".jar"))) {
            try (ZipFile zf = new ZipFile(jarFile)) {
                ZipEntry entry = zf.getEntry("fabric.mod.json");
                if (entry == null) continue;
                
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(zf.getInputStream(entry), StandardCharsets.UTF_8))) {
                    String json = reader.lines().reduce("", (a, b) -> a + b);
                    String modId = extractField(json, "\"id\"");
                    String mcDep = extractMinecraftDep(json);
                    
                    if (modId != null && !modId.equals("versionless") && 
                        !modId.equals("fabricloader") && !modId.equals("fabric-api") &&
                        !modId.equals("fabric") && !modId.equals("minecraft") &&
                        mcDep != null && isOldVersion(mcDep)) {
                        oldModIds.add(modId);
                    }
                }
            } catch (Exception e) {
                Versionless.LOGGER.debug("Failed to scan {} : {}", jarFile.getName(), e.getMessage());
            }
        }
        
        return oldModIds;
    }
    
    void generateDependencyOverrides(List<String> modIds) {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"version\": 1.0,\n");
        json.append("  \"overrides\": {\n");
        
        for (int i = 0; i < modIds.size(); i++) {
            String id = modIds.get(i);
            json.append("    \"").append(id).append("\": {\n");
            json.append("      \"minecraft\": \"26.1.1\",\n");
            json.append("    }");
            if (i < modIds.size() - 1) json.append(",");
            json.append("\n");
        }
        
        json.append("  }\n");
        json.append("}");
        
        try {
            // Write to config directory
            String configDir = System.getProperty("fabric.loader.config.dir", "");
            if (configDir.isEmpty()) {
                // Fall back to same dir as executable or user.dir
                String userDir = System.getProperty("user.dir", ".");
                configDir = userDir + "/config";
            }
            
            Path cfgPath = Paths.get(configDir);
            Files.createDirectories(cfgPath);
            Path outFile = cfgPath.resolve(CONFIG_FILENAME);
            Files.writeString(outFile, json.toString(), StandardCharsets.UTF_8);
            
            Versionless.LOGGER.info("Wrote dependency overrides to: {}", cfgPath.resolve(CONFIG_FILENAME));
        } catch (Exception e) {
            Versionless.LOGGER.error("Failed to write dependency overrides: {}", e.getMessage());
        }
    }
    
    String extractField(String json, String fieldName) {
        int pos = json.indexOf(fieldName);
        if (pos == -1) return null;
        
        int colon = json.indexOf(":", pos + fieldName.length());
        if (colon == -1) return null;
        
        int start = json.indexOf("\"", colon + 1);
        if (start == -1) return null;
        
        int end = json.indexOf("\"", start + 1);
        if (end == -1) return null;
        
        return json.substring(start + 1, end);
    }
    
    String extractMinecraftDep(String json) {
        // Look for "minecraft" in "depends" section
        // Format: "minecraft": ">=1.21", "~1.21.1", "1.21.x", etc.
        int dependsPos = json.indexOf("\"depends\"");
        if (dependsPos == -1) return null;
        
        // Find minecraft within depends block
        int mcPos = json.indexOf("\"minecraft\"", dependsPos);
        if (mcPos == -1) return null;
        
        // Get value (string or array)
        int colon = json.indexOf(":", mcPos);
        if (colon == -1) return null;
        
        // Find opening quote or bracket
        int start = colon + 1;
        while (start < json.length() && Character.isWhitespace(json.charAt(start))) start++;
        
        if (json.charAt(start) == '"') {
            // Single string value
            int end = json.indexOf("\"", start + 1);
            if (end == -1) return null;
            return json.substring(start + 1, end);
        } else if (json.charAt(start) == '[') {
            // Array value — get first element
            int firstQuote = json.indexOf("\"", start + 1);
            if (firstQuote == -1) return null;
            int endQuote = json.indexOf("\"", firstQuote + 1);
            if (endQuote == -1) return null;
            return json.substring(firstQuote + 1, endQuote);
        }
        
        return null;
    }
    
    boolean isOldVersion(String versionRange) {
        for (String v : SUPPORTED) {
            if (versionRange.contains(v)) return true;
        }
        return false;
    }
}
