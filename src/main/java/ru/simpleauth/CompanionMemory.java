package ru.simpleauth;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Долговременная память компаньона — простой список фактов в текстовом
 * файле. В отличие от истории диалога (живёт в памяти и стирается при
 * рестарте), это то, что компаньон решил запомнить надолго: имена, планы,
 * договорённости, где что построено.
 *
 * Компаньон сам добавляет сюда записи, вставляя в свой ответ строку
 * [ЗАПОМНИТЬ: текст] — она вырезается из реплики перед показом в чате.
 */
public class CompanionMemory {

    private static final int MAX_FACTS = 40;

    private final List<String> facts = new ArrayList<>();
    private boolean loaded = false;

    private static Path file() {
        return ServerSettings.dir().resolve("companion_memory.txt");
    }

    public synchronized List<String> all() {
        ensureLoaded();
        return new ArrayList<>(facts);
    }

    public synchronized void add(String fact) {
        ensureLoaded();
        String trimmed = fact.trim();
        if (trimmed.isEmpty() || facts.contains(trimmed)) return;
        facts.add(trimmed);
        // самые старые вытесняются, чтобы файл и запросы не росли бесконечно
        while (facts.size() > MAX_FACTS) {
            facts.remove(0);
        }
        save();
    }

    public synchronized void clear() {
        ensureLoaded();
        facts.clear();
        save();
    }

    private void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        Path path = file();
        if (!Files.exists(path)) return;
        try {
            facts.addAll(Files.readAllLines(path, StandardCharsets.UTF_8).stream()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty())
                    .toList());
        } catch (IOException e) {
            ServerCore.LOGGER.warn("Не удалось прочитать память компаньона: {}", e.getMessage());
        }
    }

    private void save() {
        try {
            Files.createDirectories(ServerSettings.dir());
            Files.write(file(), facts, StandardCharsets.UTF_8);
        } catch (IOException e) {
            ServerCore.LOGGER.warn("Не удалось сохранить память компаньона: {}", e.getMessage());
        }
    }
}
