package dev.pyrehaven.versionless;

import java.io.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.zip.*;

/**
 * Scans mods/versionless-mods/ at preLaunch, patches each old mod JAR's
 * fabric.mod.json to accept MC 26.1.1, then stages them so they load.
 *
 * Old mods go in mods/versionless-mods/ — FabricLoader never scans this folder,
 * so no dependency conflicts. We patch and report what we found.
 *
 * Phase 4 (test.4): Detects old mods, patches fabric.mod.json, logs compatibility info
 * Phase 5: Full runtime injection and bytecode remapping
 */
public class VersionlessBridge implements Runnable {

    private static final String[] OLD_VERSIONS = {
        "1.20", "1.21", "~1.20", "~1.21", "^1.20", "^1.21",
        "1.20.x", "1.21.x", "[1.20, 1.22)", "~1.20.1"
    };

    private static final Set<String> SKIP = Set.of(
        "versionless", "fabricloader", "fabric-api", "fabric",
        "minecraft", "java"
    );

    private static int bridgeCount = 0;
    private static final List<String> bridged = new ArrayList<>();

    public static int getBridgeCount() { return bridgeCount; }
    public static List<String> getBridgedMods() { return bridged; }
    public static void dumpBridgedMods() {
        for (String m : bridged) System.out.println("[Versionless]   " + m);
    }

    @Override
    public void run() {
        System.out.println("=== Versionless v0.1.0-test.4 ===");
        System.out.println("Scanning mods/versionless-mods/ for old mods...");

        try {
            Path modsDir = findVersionlessModsDir();
            if (modsDir == null) {
                System.out.println("[Versionless] No versionless-mods/ folder found.");
                System.out.println("[Versionless] Create mods/versionless-mods/ and put old Fabric mods there.");
                return;
            }

            System.out.println("[Versionless] Found versionless-mods/ at: " + modsDir);

            // Create output staging dir
            Path stageDir = modsDir.resolve("staged");
            Files.createDirectories(stageDir);
            int patched = 0;

            try (var stream = Files.list(modsDir)) {
                for (Path jar : stream.toList()) {
                    if (!jar.toString().endsWith(".jar")) continue;
                    if (jar.startsWith(stageDir)) continue;
                    if (isOldFabricMod(jar)) {
                        String modId = patchAndStage(jar, stageDir);
                        if (modId != null) {
                            System.out.println("[Versionless] Bridged: " + modId);
                            bridged.add(modId);
                            patched++;
                        }
                    }
                }
            }

            bridgeCount = patched;
            if (bridgeCount > 0) {
                System.out.println("[Versionless] ✓ Patched " + bridgeCount + " mod jar(s) — staged in: " + stageDir);
                System.out.println("[Versionless] Note: These mods are patched but need bytecode remapping to fully run.");
                System.out.println("[Versionless] Phase 5 (in development) will handle runtime class loading.");
            } else {
                System.out.println("[Versionless] No old mods found in versionless-mods/");
            }

        } catch (Exception e) {
            System.err.println("[Versionless] Bridge error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private Path findVersionlessModsDir() {
        String[] bases = {
            System.getProperty("fabric.gameDir"),
            System.getProperty("user.dir", ".")
        };
        String[] subpaths = {
            "mods/versionless-mods",
            "run/mods/versionless-mods"
        };

        for (String base : bases) {
            if (base == null) continue;
            for (String sub : subpaths) {
                try {
                    Path p = Path.of(base, sub);
                    if (Files.exists(p)) return p;
                } catch (Exception ignored) {}
            }
        }
        return null;
    }

    private boolean isOldFabricMod(Path jar) {
        try (ZipFile zf = new ZipFile(jar.toFile())) {
            ZipEntry e = zf.getEntry("fabric.mod.json");
            if (e == null) return false;
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(zf.getInputStream(e), StandardCharsets.UTF_8))) {
                String json = r.lines().reduce("", (a, b) -> a + b);
                String id = extractStr(json, "\"id\"");
                if (id == null || SKIP.contains(id)) return false;
                String mcDep = findMcDepInternal(json);
                return mcDep != null && isOldMcVersion(mcDep);
            }
        } catch (Exception e) {
            return false;
        }
    }

    private String patchAndStage(Path jar, Path stageDir) throws Exception {
        try (ZipFile zf = new ZipFile(jar.toFile())) {
            ZipEntry e = zf.getEntry("fabric.mod.json");
            if (e == null) return null;

            String json;
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(zf.getInputStream(e), StandardCharsets.UTF_8))) {
                json = r.lines().reduce("", (a, b) -> a + b);
            }

            String modId = extractStr(json, "\"id\"");
            if (modId == null || SKIP.contains(modId)) return null;

            String mcDep = findMcDepInternal(json);
            if (mcDep == null || mcDep.contains("26.")) return null;

            String patched = replaceMcVersion(json, mcDep);
            if (patched == null) return null;

            // Write patched JAR
            Path outJar = stageDir.resolve(jar.getFileName().toString());
            writePatchedJar(jar, outJar, patched);

            return modId;
        }
    }

    private boolean isOldMcVersion(String range) {
        for (String v : OLD_VERSIONS) {
            if (range.contains(v)) return true;
        }
        return false;
    }

    private String findMcDepInternal(String json) {
        int dp = json.indexOf("\"depends\"");
        if (dp == -1) return null;

        int mc = json.indexOf("\"minecraft\"", dp);
        if (mc == -1) return null;

        int colon = json.indexOf(":", mc + 11);
        if (colon == -1) return null;

        int s = colon + 1;
        while (s < json.length() && Character.isWhitespace(json.charAt(s))) s++;
        if (s >= json.length()) return null;

        if (json.charAt(s) == '"') {
            int end = json.indexOf("\"", s + 1);
            return end == -1 ? null : json.substring(s + 1, end);
        }
        if (json.charAt(s) == '[') {
            int fq = json.indexOf("\"", s + 1);
            int lq = fq == -1 ? -1 : json.indexOf("\"", fq + 1);
            return (fq == -1 || lq == -1) ? null : json.substring(fq + 1, lq);
        }
        return null;
    }

    private String replaceMcVersion(String json, String oldRange) {
        int dp = json.indexOf("\"depends\"");
        if (dp == -1) return json;

        int mc = json.indexOf("\"minecraft\"", dp);
        if (mc == -1) return json;

        int colon = json.indexOf(":", mc + 11);
        if (colon == -1) return json;

        int s = colon + 1;
        while (s < json.length() && Character.isWhitespace(json.charAt(s))) s++;
        if (s >= json.length()) return json;

        if (json.charAt(s) == '"') {
            int end = json.indexOf("\"", s + 1);
            if (end == -1) return json;
            return json.substring(0, s + 1) + "26.1.1" + json.substring(end);
        }
        if (json.charAt(s) == '[') {
            int bracket = json.indexOf("]", s);
            if (bracket == -1) return json;
            return json.substring(0, s + 1) + "\"26.1.1\"" + json.substring(bracket);
        }
        return json;
    }

    private void writePatchedJar(Path src, Path dst, String newJson) throws Exception {
        byte[] data = newJson.getBytes(StandardCharsets.UTF_8);

        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(dst));
             ZipFile old = new ZipFile(src.toFile())) {

            for (ZipEntry e : Collections.list(old.entries())) {
                if (e.getName().equals("fabric.mod.json")) {
                    ZipEntry ne = new ZipEntry("fabric.mod.json");
                    ne.setMethod(ZipEntry.STORED);
                    ne.setSize(data.length);
                    CRC32 crc = new CRC32();
                    crc.update(data);
                    ne.setCrc(crc.getValue());
                    out.putNextEntry(ne);
                    out.write(data);
                    out.closeEntry();
                } else if (!e.isDirectory()) {
                    ZipEntry ne = new ZipEntry(e.getName());
                    ne.setMethod(e.getMethod());
                    if (e.getMethod() == ZipEntry.STORED) {
                        ne.setSize(e.getSize());
                        ne.setCrc(e.getCrc());
                    }
                    out.putNextEntry(ne);
                    old.getInputStream(e).transferTo(out);
                    out.closeEntry();
                }
            }
        }
    }

    private String extractStr(String json, String key) {
        int p = json.indexOf(key + ":");
        if (p == -1) return null;
        int q = json.indexOf("\"", p + key.length() + 1);
        if (q == -1) return null;
        int e = json.indexOf("\"", q + 1);
        return e == -1 ? null : json.substring(q + 1, e);
    }
}
