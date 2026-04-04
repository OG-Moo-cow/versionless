package dev.pyrehaven.versionless;

/**
 * PreLaunch entrypoint — hooks into mod loading before main entrypoints.
 * STUB for test build #1 — will handle mod detection, remapping, and transformation in later versions.
 */
public class VersionlessPreLaunch implements Runnable {
    @Override
    public void run() {
        Versionless.LOGGER.info("Versionless pre-launch — scanning mods for compatibility (stub)");
        // TODO: Phase 1-5 pipeline implementation
    }
}
