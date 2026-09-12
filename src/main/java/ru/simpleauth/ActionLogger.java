package ru.simpleauth;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Простой файловый лог для админ-контроля: пишет строки с меткой времени в
 * config/simpleauth/actions.log. В отличие от ItemCleaner (который намеренно
 * тихий), это обычный лог для последующего просмотра — не выводится в
 * консоль сервера, но и не прячется, лежит рядом с конфигом.
 */
public class ActionLogger {

    private static final DateTimeFormatter TS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final AuthManager manager;

    public ActionLogger(AuthManager manager) {
        this.manager = manager;
    }

    private Path logFile() {
        return Config.dir().resolve("actions.log");
    }

    public void log(String line) {
        if (!manager.config().actionLogEnabled) return;
        try {
            Files.createDirectories(Config.dir());
            String entry = "[" + LocalDateTime.now().format(TS) + "] " + line + System.lineSeparator();
            Files.writeString(logFile(), entry, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {
            // лог не критичен — сервер не должен падать из-за проблем с диском
        }
    }
}
