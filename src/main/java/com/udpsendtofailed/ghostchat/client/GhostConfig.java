package com.udpsendtofailed.ghostchat.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public class GhostConfig {

    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("ghostchat.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static GhostConfig instance;

    // --- SETTINGS ---
    public boolean isInterceptEnabled = false; 
    public boolean isDebugEnabled = false;
    public int ticksPerNibble = 2;

    public static GhostConfig getInstance() {
        if (instance == null) {
            load();
        }
        return instance;
    }

    public static void load() {
        if (Files.exists(CONFIG_PATH)) {
            try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
                instance = GSON.fromJson(reader, GhostConfig.class);
            } catch (Exception e) {
                // If corrupted, reset to defaults
                instance = new GhostConfig();
                save();
            }
        } else {
            instance = new GhostConfig();
            save();
        }
    }

    public static void save() {
        if (instance == null) return;
        try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
            GSON.toJson(instance, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}