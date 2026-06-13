package com.buildingwand;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class BuildingWandConfig {

    private static final int DEFAULT_SMART_SCAN_LIMIT = 80_000;
    private static final int DEFAULT_SMART_BLOCK_LIMIT = 25_000;
    private static final int MIN_SMART_SCAN_LIMIT = 10_000;
    private static final int MAX_SMART_SCAN_LIMIT = 250_000;
    private static final int MIN_SMART_BLOCK_LIMIT = 2_000;
    private static final int MAX_SMART_BLOCK_LIMIT = 100_000;

    private static final Path CONFIG_PATH = FabricLoader.getInstance()
            .getConfigDir()
            .resolve("buildingwand.properties");

    private static int smartScanLimit = DEFAULT_SMART_SCAN_LIMIT;
    private static int smartBlockLimit = DEFAULT_SMART_BLOCK_LIMIT;

    private BuildingWandConfig() {}

    public static synchronized void load() {
        smartScanLimit = DEFAULT_SMART_SCAN_LIMIT;
        smartBlockLimit = DEFAULT_SMART_BLOCK_LIMIT;
        if (!Files.exists(CONFIG_PATH)) {
            save();
            return;
        }

        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(CONFIG_PATH)) {
            props.load(in);
            smartScanLimit = clamp(parseInt(props, "smart_scan_limit", DEFAULT_SMART_SCAN_LIMIT),
                    MIN_SMART_SCAN_LIMIT, MAX_SMART_SCAN_LIMIT);
            smartBlockLimit = clamp(parseInt(props, "smart_block_limit", DEFAULT_SMART_BLOCK_LIMIT),
                    MIN_SMART_BLOCK_LIMIT, MAX_SMART_BLOCK_LIMIT);
        } catch (IOException ignored) {
            save();
        }
    }

    public static synchronized void save() {
        Properties props = new Properties();
        props.setProperty("smart_scan_limit", Integer.toString(smartScanLimit));
        props.setProperty("smart_block_limit", Integer.toString(smartBlockLimit));
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (OutputStream out = Files.newOutputStream(CONFIG_PATH)) {
                props.store(out, "Building Wand");
            }
        } catch (IOException ignored) {
        }
    }

    public static synchronized int smartScanLimit() {
        return smartScanLimit;
    }

    public static synchronized int smartBlockLimit() {
        return smartBlockLimit;
    }

    public static synchronized void setSmartScanLimit(int value) {
        smartScanLimit = clamp(value, MIN_SMART_SCAN_LIMIT, MAX_SMART_SCAN_LIMIT);
    }

    public static synchronized void setSmartBlockLimit(int value) {
        smartBlockLimit = clamp(value, MIN_SMART_BLOCK_LIMIT, MAX_SMART_BLOCK_LIMIT);
    }

    public static int minSmartScanLimit() { return MIN_SMART_SCAN_LIMIT; }
    public static int maxSmartScanLimit() { return MAX_SMART_SCAN_LIMIT; }
    public static int minSmartBlockLimit() { return MIN_SMART_BLOCK_LIMIT; }
    public static int maxSmartBlockLimit() { return MAX_SMART_BLOCK_LIMIT; }

    private static int parseInt(Properties props, String key, int fallback) {
        try {
            return Integer.parseInt(props.getProperty(key, Integer.toString(fallback)).trim());
        } catch (Exception ex) {
            return fallback;
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
