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
 * config/simpleauth/actions.log. В отличие от EntityCleanupTask (который намеренно
 * тихий), это обычный лог для последующего просмотра — не выводится в
 * консоль сервера, но и не прячется, лежит рядом с конфигом.
 */
public class EventLogger {

    private static final DateTimeFormatter TS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final SessionManager manager;

    public EventLogger(SessionManager manager) {
        this.manager = manager;
    }

    private Path logFile() {
        return ServerSettings.dir().resolve("actions.log");
    }

    public void log(String line) {
        if (!manager.config().actionLogEnabled) return;
        try {
            Files.createDirectories(ServerSettings.dir());
            String entry = "[" + LocalDateTime.now().format(TS) + "] " + line + System.lineSeparator();
            Files.writeString(logFile(), entry, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {
            // лог не критичен — сервер не должен падать из-за проблем с диском
        }
    }
}
