package com.simpleauth;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Аккаунты хранятся в config/simpleauth/players.json.
 * Пароль не сохраняется — только соль и PBKDF2-хеш.
 */
public class AuthManager {

    public static class Account {
        public String salt;
        public String hash;
        public long lastLogin;
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type TYPE = new TypeToken<Map<String, Account>>() {
    }.getType();

    private static final Path DIR = Paths.get("config", "simpleauth");
    private static final Path FILE = DIR.resolve("players.json");

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int ITERATIONS = 120_000;
    private static final int KEY_LENGTH = 256;

    private final Map<String, Account> accounts = new HashMap<>();

    public void load() {
        try {
            Files.createDirectories(DIR);
            if (Files.notExists(FILE)) {
                save();
                return;
            }
            String json = Files.readString(FILE, StandardCharsets.UTF_8);
            Map<String, Account> loaded = GSON.fromJson(json, TYPE);
            accounts.clear();
            if (loaded != null) {
                for (Map.Entry<String, Account> entry : loaded.entrySet()) {
                    Account account = entry.getValue();
                    if (account != null && account.hash != null && account.salt != null) {
                        accounts.put(key(entry.getKey()), account);
                    }
                }
            }
            SimpleAuth.LOGGER.info("Загружено аккаунтов: {}", accounts.size());
        } catch (Exception e) {
            SimpleAuth.LOGGER.error("Не удалось прочитать players.json, старый файл сохранён как players.json.bak", e);
            try {
                Files.move(FILE, DIR.resolve("players.json.bak"), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) {
                // старый файл не трогаем, если переименовать не вышло
            }
            accounts.clear();
        }
    }

    public void save() {
        try {
            Files.createDirectories(DIR);
            Files.writeString(FILE, GSON.toJson(accounts, TYPE), StandardCharsets.UTF_8);
        } catch (IOException e) {
            SimpleAuth.LOGGER.error("Не удалось сохранить players.json", e);
        }
    }

    public boolean isRegistered(String name) {
        return accounts.containsKey(key(name));
    }

    public void register(String name, String password) {
        Account account = new Account();
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        account.salt = Base64.getEncoder().encodeToString(salt);
        account.hash = hash(password, salt);
        account.lastLogin = System.currentTimeMillis();
        accounts.put(key(name), account);
        save();
    }

    public boolean checkPassword(String name, String password) {
        Account account = accounts.get(key(name));
        if (account == null) {
            return false;
        }
        byte[] salt = Base64.getDecoder().decode(account.salt);
        boolean ok = constantTimeEquals(account.hash, hash(password, salt));
        if (ok) {
            account.lastLogin = System.currentTimeMillis();
            save();
        }
        return ok;
    }

    public void setPassword(String name, String password) {
        register(name, password);
    }

    public boolean unregister(String name) {
        boolean removed = accounts.remove(key(name)) != null;
        if (removed) {
            save();
        }
        return removed;
    }

    public int size() {
        return accounts.size();
    }

    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    private static String hash(String password, byte[] salt) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH);
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            return Base64.getEncoder().encodeToString(factory.generateSecret(spec).getEncoded());
        } catch (Exception e) {
            throw new IllegalStateException("Не удалось захешировать пароль", e);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) {
            return false;
        }
        int diff = 0;
        for (int i = 0; i < a.length(); i++) {
            diff |= a.charAt(i) ^ b.charAt(i);
        }
        return diff == 0;
    }
}
