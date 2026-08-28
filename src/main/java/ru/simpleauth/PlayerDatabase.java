package ru.simpleauth;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Хранилище аккаунтов: config/simpleauth/players.json
 * Пароли хранятся как PBKDF2-WithHmacSHA256 хеш + соль. Открытые пароли нигде не сохраняются.
 */
public class PlayerDatabase {

    public static class Account {
        public String name;
        public String salt;
        public String hash;
        public int iterations;
        public long registeredAt;
        public long lastLogin;
    }

    private static final String ALGO = "PBKDF2WithHmacSHA256";
    private static final int ITERATIONS = 120_000;
    private static final int KEY_LENGTH = 256;
    private static final int SALT_BYTES = 16;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type TYPE = new TypeToken<HashMap<String, Account>>() {
    }.getType();

    private final Map<String, Account> accounts = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    public PlayerDatabase() {
        load();
    }

    private Path file() {
        return Config.dir().resolve("players.json");
    }

    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    public synchronized void load() {
        Path path = file();
        try {
            Files.createDirectories(Config.dir());
            if (!Files.exists(path)) return;
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                Map<String, Account> loaded = GSON.fromJson(reader, TYPE);
                accounts.clear();
                if (loaded != null) accounts.putAll(loaded);
            }
            SimpleAuth.LOGGER.info("[SimpleAuth] Загружено аккаунтов: {}", accounts.size());
        } catch (Exception e) {
            SimpleAuth.LOGGER.error("[SimpleAuth] Не удалось прочитать players.json", e);
        }
    }

    public synchronized void save() {
        Path path = file();
        Path tmp = path.resolveSibling("players.json.tmp");
        try {
            Files.createDirectories(Config.dir());
            try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                GSON.toJson(new HashMap<>(accounts), TYPE, writer);
            }
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            SimpleAuth.LOGGER.error("[SimpleAuth] Не удалось сохранить players.json", e);
        }
    }

    public boolean isRegistered(String name) {
        return accounts.containsKey(key(name));
    }

    public boolean unregister(String name) {
        boolean removed = accounts.remove(key(name)) != null;
        if (removed) save();
        return removed;
    }

    public void register(String name, String password) {
        byte[] salt = new byte[SALT_BYTES];
        random.nextBytes(salt);
        Account account = new Account();
        account.name = name;
        account.salt = Base64.getEncoder().encodeToString(salt);
        account.hash = hash(password, salt, ITERATIONS);
        account.iterations = ITERATIONS;
        account.registeredAt = System.currentTimeMillis();
        account.lastLogin = account.registeredAt;
        accounts.put(key(name), account);
        save();
    }

    public boolean check(String name, String password) {
        Account account = accounts.get(key(name));
        if (account == null) return false;
        byte[] salt = Base64.getDecoder().decode(account.salt);
        int iterations = account.iterations > 0 ? account.iterations : ITERATIONS;
        String candidate = hash(password, salt, iterations);
        return MessageDigest.isEqual(
                candidate.getBytes(StandardCharsets.UTF_8),
                account.hash.getBytes(StandardCharsets.UTF_8));
    }

    public void touchLogin(String name) {
        Account account = accounts.get(key(name));
        if (account != null) {
            account.lastLogin = System.currentTimeMillis();
            save();
        }
    }

    public void changePassword(String name, String newPassword) {
        Account account = accounts.get(key(name));
        if (account == null) return;
        byte[] salt = new byte[SALT_BYTES];
        random.nextBytes(salt);
        account.salt = Base64.getEncoder().encodeToString(salt);
        account.hash = hash(newPassword, salt, ITERATIONS);
        account.iterations = ITERATIONS;
        save();
    }

    public int size() {
        return accounts.size();
    }

    private static String hash(String password, byte[] salt, int iterations) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, KEY_LENGTH);
            SecretKeyFactory factory = SecretKeyFactory.getInstance(ALGO);
            byte[] out = factory.generateSecret(spec).getEncoded();
            spec.clearPassword();
            return Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new IllegalStateException("Не удалось захешировать пароль", e);
        }
    }
}
