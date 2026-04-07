package dev.pyrehaven.versionless;

import java.io.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/**
 * Scans the mods/ directory and patches old mod JARs in-place
 * so their fabric.mod.json accepts MC 26.1.1.
 * 
 * This runs at the HEAD of FabricLoaderImpl.load() — before ANY
 * mod metadata is read, so by the time ModDiscoverer scans the
 * JARs, they already contain the patched version.
 */
public class ModJarPatcher {
    
    private static final String[] OLD_PATTERNS = {
        "1.20", "1.21", "~1.20", "~1.21", "^1.20", "^1.21",
        "1.20.x", "1.21.x"
    };
    private static final Set<String> SKIP = Set.of(
        "versionless", "fabricloader", "fabric-api", "fabric",
        "minecraft", "java", "mixinextras"
    );
    private static int patchedCount = 0;
    
    public static int getLastPatchCount() { return patchedCount; }
    
    public static void run() {
        try {
            System.out.println("[Versionless] Scanning mods directory for old mods to bridge...");
            Path modsDir = findModsDir();
            if (modsDir == null) {
                System.out.println("[Versionless] No mods directory found — nothing to patch");
                return;
            }
            
            System.out.println("[Versionless] Mods directory: " + modsDir);
            
            try (var stream = Files.list(modsDir)) {
                for (Path jar : stream.toList()) {
                    if (patchJar(jar)) patchedCount++;
                }
            }
            
            if (patchedCount > 0) {
                System.out.println("[Versionless] Patched " + patchedCount + " old mod(s) — they will now load on 26.1!");
            } else {
                System.out.println("[Versionless] No old mods found needing patches");
            }
            
        } catch (Exception e) {
            System.err.println("[Versionless] Failed to patch mods: " + e.getMessage());
        }
    }
    
    private static Path findModsDir() {
        String[] possibleBases = {
            System.getProperty("fabric.modFolder"),
            System.getProperty("fabric.gameDir"),
            System.getProperty("user.dir", "."),
        };
        String[] subPaths = {"mods", "run/mods", ".minecraft/mods"};
        
        for (String base : possibleBases) {
            if (base == null) continue;
            for (String sub : subPaths) {
                try {
                    Path p = Path.of(base, sub);
                    if (Files.exists(p)) return p;
                } catch (Exception ignored) {}
            }
            try {
                Path p = Path.of(base);
                if (Files.exists(p)) {
                    try (var s = Files.list(p)) {
                        if (s.anyMatch(f -> f.toString().endsWith(".jar"))) return p;
                    }
                }
            } catch (Exception ignored) {}
        }
        return null;
    }
    
    private static boolean patchJar(Path jarPath) throws Exception {
        if (!jarPath.toString().endsWith(".jar")) return false;
        
        try (ZipFile zf = new ZipFile(jarPath.toFile())) {
            ZipEntry entry = zf.getEntry("fabric.mod.json");
            if (entry == null) return false;
            
            String json;
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(zf.getInputStream(entry), StandardCharsets.UTF_8))) {
                json = r.lines().reduce("", (a, b) -> a + b);
            }
            
            String modId = extractStr(json, "\"id\"");
            if (modId == null || SKIP.contains(modId)) return false;
            
            String mcDep = findMcDep(json);
            if (mcDep == null) return false;
            if (mcDep.contains("26.")) return false; // Already compatible
            if (!isOld(mcDep)) return false;
            
            String patched = patchMcDep(json);
            if (patched.equals(json)) return false;
            
            writePatchedJar(jarPath, patched);
            System.out.println("[Versionless]   ✓ " + modId + " (" + mcDep + " → 26.1.1)");
            return true;
            
        } catch (Exception e) {
            return false;
        }
    }
    
    private static String extractStr(String json, String key) {
        int p = json.indexOf(key + ":");
        if (p == -1) return null;
        int q = json.indexOf("\"", p + key.length() + 1);
        if (q == -1) return null;
        int e = json.indexOf("\"", q + 1);
        return (e == -1) ? null : json.substring(q + 1, e);
    }
    
    private static String findMcDep(String json) {
        // Only look in the "depends" section
        int dependsPos = json.indexOf("\"depends\"");
        if (dependsPos == -1) return null;
        
        int pos = dependsPos;
        while (true) {
            int mc = json.indexOf("\"minecraft\"", pos);
            if (mc == -1) return null;
            
            int colon = json.indexOf(":", mc + 11);
            if (colon == -1) { pos = mc + 11; continue; }
            
            int s = colon + 1;
            while (s < json.length() && json.charAt(s) <= ' ') s++;
            if (s >= json.length()) { pos = mc + 11; continue; }
            
            if (json.charAt(s) == '"') {
                int e = json.indexOf("\"", s + 1);
                return (e == -1) ? null : json.substring(s + 1, e);
            }
            if (json.charAt(s) == '[') {
                int fq = json.indexOf("\"", s + 1);
                int lq = (fq == -1) ? -1 : json.indexOf("\"", fq + 1);
                return (fq == -1 || lq == -1) ? null : json.substring(fq + 1, lq);
            }
            pos = mc + 11;
        }
    }
    
    private static boolean isOld(String range) {
        for (String v : OLD_PATTERNS) if (range.contains(v)) return true;
        return false;
    }
    
    private static String patchMcDep(String json) {
        // Replace all occurrences of old mc version constraints with "26.1.1"
        // We do a broad find-and-replace for known version strings
        for (String v : OLD_PATTERNS) {
            json = json.replace("\"" + v + "\"", "\"26.1.1\"");
            json = json.replace("\"~" + v + "\"", "\"26.1.1\"");
            json = json.replace("\"^" + v + "\"", "\"26.1.1\"");
        }
        // Also replace ranges like ">=1.20 <=1.21" etc.
        json = json.replaceAll("\"[<>=!~^]{0,2}1\\.2[01](\\.[\\d]+)?([-~][<>=!~^]{0,2}[\\d.]+)?\"", "\"26.1.1\"");
        json = json.replaceAll("\"[<>=!~^]{0,2}1\\.2[01](\\.x)?\"", "\"26.1.1\"");
        return json;
    }
    
    private static void writePatchedJar(Path src, String newJson) throws Exception {
        Path tmp = src.resolveSibling(".versionless_patched_" + src.getFileName());
        
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(tmp));
             ZipFile old = new ZipFile(src.toFile())) {
            
            // Use ZipEntry.STORED for proper uncompressed access
            List<ZipEntry> entriesToStore = new ArrayList<>();
            byte[] newJsonBytes = newJson.getBytes(StandardCharsets.UTF_8);
            for (ZipEntry e : Collections.list(old.entries())) {
                if (e.getName().equals("fabric.mod.json")) {
                    ZipEntry ne = new ZipEntry("fabric.mod.json");
                    ne.setMethod(ZipEntry.STORED);
                    ne.setSize(newJsonBytes.length);
                    CRC32 crc = new CRC32();
                    crc.update(newJsonBytes);
                    ne.setCrc(crc.getValue());
                    out.putNextEntry(ne);
                    out.write(newJsonBytes);
                    out.closeEntry();
                } else {
                    if (e.getMethod() == ZipEntry.STORED) {
                        // Preserve STORED entries
                        ZipEntry ne = new ZipEntry(e.getName());
                        ne.setMethod(ZipEntry.STORED);
                        ne.setSize(e.getSize());
                        ne.setCrc(e.getCrc());
                        out.putNextEntry(ne);
                        old.getInputStream(e).transferTo(out);
                        out.closeEntry();
                    } else {
                        // DEFLATED entries
                        ZipEntry ne = new ZipEntry(e.getName());
                        ne.setMethod(ZipEntry.DEFLATED);
                        out.putNextEntry(ne);
                        old.getInputStream(e).transferTo(out);
                        out.closeEntry();
                    }
                }
            }
        }
        
        // Atomic replace
        Files.delete(src);
        Files.move(tmp, src);
    }
}
