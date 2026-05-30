package com.micr.oldf3.client;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import net.fabricmc.loader.api.FabricLoader;

public final class OldF3Config {
    private static final int DEFAULT_DEBUG_GUI_SCALE = 0;
    private static final int MAX_DEBUG_GUI_SCALE = 8;
    private static final String DEBUG_GUI_SCALE_KEY = "debug_gui_scale";

    private static int debugGuiScale = DEFAULT_DEBUG_GUI_SCALE;

    private OldF3Config() {
    }

    public static void load() {
        Path configPath = FabricLoader.getInstance().getConfigDir().resolve("oldf3.properties");
        if (Files.notExists(configPath)) {
            writeDefaultConfig(configPath);
            return;
        }

        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(configPath)) {
            properties.load(reader);
        }
        catch (IOException e) {
            debugGuiScale = DEFAULT_DEBUG_GUI_SCALE;
            return;
        }

        debugGuiScale = parseDebugGuiScale(properties.getProperty(DEBUG_GUI_SCALE_KEY));
    }

    public static int getDebugGuiScale() {
        return debugGuiScale;
    }

    private static void writeDefaultConfig(Path configPath) {
        debugGuiScale = DEFAULT_DEBUG_GUI_SCALE;
        try {
            Files.createDirectories(configPath.getParent());
            try (Writer writer = Files.newBufferedWriter(configPath)) {
                writer.write("# old f3 config\n");
                writer.write("# debug_gui_scale: 0 follows Minecraft's GUI scale. 1-8 forces only the F3 text scale.\n");
                writer.write(DEBUG_GUI_SCALE_KEY + "=" + DEFAULT_DEBUG_GUI_SCALE + "\n");
            }
        }
        catch (IOException e) {
            // The mod still works without a writable config directory; it just uses vanilla GUI scaling.
        }
    }

    private static int parseDebugGuiScale(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return DEFAULT_DEBUG_GUI_SCALE;
        }

        try {
            int parsedValue = Integer.parseInt(rawValue.trim());
            if (parsedValue < DEFAULT_DEBUG_GUI_SCALE) {
                return DEFAULT_DEBUG_GUI_SCALE;
            }
            return Math.min(parsedValue, MAX_DEBUG_GUI_SCALE);
        }
        catch (NumberFormatException e) {
            return DEFAULT_DEBUG_GUI_SCALE;
        }
    }
}
