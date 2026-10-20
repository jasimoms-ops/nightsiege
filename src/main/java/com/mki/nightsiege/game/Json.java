package com.mki.nightsiege.game;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mki.nightsiege.NightSiegeMod;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Small Gson helper for the two JSON files the mod keeps in the config folder. */
public final class Json {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private Json() {
    }

    public static Path configFile(String name) {
        return FabricLoader.getInstance().getConfigDir().resolve(name);
    }

    /** Reads a file, or returns null when it is missing or unreadable. */
    public static <T> T read(Path path, Class<T> type) {
        if (!Files.isRegularFile(path)) {
            return null;
        }
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return GSON.fromJson(reader, type);
        } catch (IOException | RuntimeException e) {
            NightSiegeMod.LOGGER.warn("[Night Siege] {} konnte nicht gelesen werden: {}",
                    path.getFileName(), e.toString());
            return null;
        }
    }

    /** Serialises an object for the network, never for a file. */
    public static String toJson(Object value) {
        return GSON.toJson(value);
    }

    public static <T> T fromJson(String text, Class<T> type) {
        return GSON.fromJson(text, type);
    }

    public static void write(Path path, Object value) {
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                GSON.toJson(value, writer);
            }
        } catch (IOException | RuntimeException e) {
            NightSiegeMod.LOGGER.warn("[Night Siege] {} konnte nicht geschrieben werden: {}",
                    path.getFileName(), e.toString());
        }
    }
}
