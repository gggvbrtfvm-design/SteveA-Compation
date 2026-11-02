package dev.ayran.steveai;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

final class GeminiConfig {
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("steve-ai-companion.properties");
    private static final Properties VALUES = new Properties();

    static void load() {
        try {
            Files.createDirectories(FILE.getParent());
            if (!Files.exists(FILE)) {
                VALUES.setProperty("api_key", "PASTE_YOUR_GEMINI_API_KEY_HERE");
                VALUES.setProperty("model", "gemini-4-argon");
                try (OutputStream out = Files.newOutputStream(FILE)) {
                    VALUES.store(out, "Steve AI Companion - keep this file private");
                }
            }
            try (InputStream in = Files.newInputStream(FILE)) {
                VALUES.load(in);
            }
        } catch (IOException exception) {
            SteveAICompanion.LOGGER.error("Could not load Steve AI config", exception);
        }
    }

    static String apiKey() {
        String key = VALUES.getProperty("api_key", "").trim();
        return key.equals("PASTE_YOUR_GEMINI_API_KEY_HERE") ? "" : key;
    }

    static String model() {
        String model = VALUES.getProperty("model", "gemini-4-argon").trim();
        return model.matches("[a-zA-Z0-9._-]+") ? model : "gemini-4-argon";
    }
}
