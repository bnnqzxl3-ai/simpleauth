package ru.simpleauth;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Долгая память NPC о людях: переживает перезапуск сервера. */
public class AiNotes {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type TYPE = new TypeToken<LinkedHashMap<String, List<String>>>() {
    }.getType();

    private static final int MAX_NOTES_PER_PLAYER = 20;
    private static final int MAX_NOTE_LENGTH = 200;

    private final Map<String, List<String>> notes = new LinkedHashMap<>();

    public AiNotes() {
        load();
    }

    private static Path file() {
        return ServerSettings.dir().resolve("ai_notes.json");
    }

    private void load() {
        Path path = file();
        if (!Files.exists(path)) return;
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            Map<String, List<String>> loaded = GSON.fromJson(reader, TYPE);
            if (loaded != null) notes.putAll(loaded);
        } catch (Exception e) {
            ServerCore.LOGGER.warn("[ServerCore] AI: не удалось прочитать память", e);
        }
    }

    private void save() {
        try {
            Files.createDirectories(ServerSettings.dir());
            try (Writer writer = Files.newBufferedWriter(file(), StandardCharsets.UTF_8)) {
                GSON.toJson(notes, writer);
            }
        } catch (Exception e) {
            ServerCore.LOGGER.warn("[ServerCore] AI: не удалось сохранить память", e);
        }
    }

    private static String key(String player) {
        return player == null ? "" : player.trim().toLowerCase(Locale.ROOT);
    }

    public synchronized void add(String player, String note) {
        String trimmed = note.length() > MAX_NOTE_LENGTH ? note.substring(0, MAX_NOTE_LENGTH) : note;
        List<String> list = notes.computeIfAbsent(key(player), name -> new ArrayList<>());
        if (list.contains(trimmed)) return;
        list.add(trimmed);
        while (list.size() > MAX_NOTES_PER_PLAYER) list.remove(0);
        save();
    }

    public synchronized List<String> get(String player) {
        List<String> list = notes.get(key(player));
        return list == null ? List.of() : new ArrayList<>(list);
    }

    public synchronized void forget(String player) {
        if (notes.remove(key(player)) != null) save();
    }

    public synchronized void forgetAll() {
        notes.clear();
        save();
    }

    /** Короткая сводка для системного промпта: с кем NPC уже знаком. */
    public synchronized String summary(List<String> players) {
        StringBuilder text = new StringBuilder();
        for (String player : players) {
            List<String> list = notes.get(key(player));
            if (list == null || list.isEmpty()) continue;
            if (text.length() > 0) text.append("\n");
            text.append(player).append(": ").append(String.join("; ", list));
        }
        return text.toString();
    }
}
