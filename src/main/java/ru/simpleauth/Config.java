package ru.simpleauth;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Настройки мода. Файл: config/simpleauth/config.json
 */
public class Config {

    public int loginTimeoutSeconds = 60;
    public int minPasswordLength = 4;
    public int maxPasswordLength = 32;
    public int maxLoginAttempts = 3;

    /** Слепота, пока игрок не вошёл (чтобы не осматривался). */
    public boolean blindnessUntilLogin = true;
    /** Неуязвимость, пока игрок не вошёл. */
    public boolean invulnerableUntilLogin = true;
    /** Блокировать чат до входа. */
    public boolean muteChatUntilLogin = true;
    /** Как часто (в секундах) напоминать о входе. */
    public int reminderIntervalSeconds = 5;

    public Messages messages = new Messages();

    public static class Messages {
        public String needRegister = "Добро пожаловать! Зарегистрируйтесь: /register <пароль> <пароль>";
        public String needLogin = "Войдите: /login <пароль>";
        public String registered = "Вы успешно зарегистрированы и вошли в игру.";
        public String loggedIn = "Вход выполнен. Приятной игры!";
        public String alreadyRegistered = "Вы уже зарегистрированы. Используйте /login <пароль>";
        public String notRegistered = "Вы не зарегистрированы. Используйте /register <пароль> <пароль>";
        public String alreadyLoggedIn = "Вы уже вошли.";
        public String passwordsDoNotMatch = "Пароли не совпадают.";
        public String wrongPassword = "Неверный пароль.";
        public String passwordTooShort = "Пароль слишком короткий (минимум %d символов).";
        public String passwordTooLong = "Пароль слишком длинный (максимум %d символов).";
        public String kickTimeout = "Вы не успели войти. Зайдите снова.";
        public String kickTooManyAttempts = "Слишком много неверных попыток входа.";
        public String mustLoginFirst = "Сначала войдите в аккаунт.";
        public String passwordChanged = "Пароль изменён.";
        public String playerUnregistered = "Игрок %s удалён из базы.";
        public String playerNotFound = "Игрок %s не найден в базе.";
        public String configReloaded = "Конфиг SimpleAuth перезагружен.";
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static Path dir() {
        return FabricLoader.getInstance().getConfigDir().resolve("simpleauth");
    }

    public static Path file() {
        return dir().resolve("config.json");
    }

    public static Config load() {
        Path path = file();
        try {
            Files.createDirectories(dir());
            if (Files.exists(path)) {
                try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                    Config loaded = GSON.fromJson(reader, Config.class);
                    if (loaded != null) {
                        if (loaded.messages == null) loaded.messages = new Messages();
                        loaded.save();
                        return loaded;
                    }
                }
            }
        } catch (Exception e) {
            SimpleAuth.LOGGER.error("[SimpleAuth] Не удалось прочитать конфиг, использую значения по умолчанию", e);
        }
        Config fresh = new Config();
        fresh.save();
        return fresh;
    }

    public void save() {
        try {
            Files.createDirectories(dir());
            try (Writer writer = Files.newBufferedWriter(file(), StandardCharsets.UTF_8)) {
                GSON.toJson(this, writer);
            }
        } catch (IOException e) {
            SimpleAuth.LOGGER.error("[SimpleAuth] Не удалось сохранить конфиг", e);
        }
    }
}
