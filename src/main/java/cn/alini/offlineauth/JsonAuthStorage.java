package cn.alini.offlineauth;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Base64;
import java.util.Arrays;

public class JsonAuthStorage {
    private static final String DIR = "config/offlineauth";
    private static final String FILE_NAME = "auth_hash.json";
    private static final String OLD_FILE_NAME = "auth.json";
    private static final Path FILE_PATH = Path.of(DIR, FILE_NAME);
    private static final Gson gson = new Gson();

    // PBKDF2 constants for password hashing
    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int ITERATIONS = 10000;
    private static final int KEY_LENGTH = 256;
    private static final String SALT_SEPARATOR = ":";

    private Map<String, String> credentials = new HashMap<>();
    private long lastModified = -1;

    public JsonAuthStorage() {
        load();
    }

    // 每次操作前热加载
    private void reloadIfChanged() {
        File file = FILE_PATH.toFile();
        if (!file.exists()) {
            // File missing, might need migration or it's just empty. Let load() handle it.
            load();
            return;
        }
        long lm = file.lastModified();
        if (lm != lastModified) {
            load();
        }
    }

    private void load() {
        credentials = new HashMap<>();
        File dir = new File(DIR);
        if (!dir.exists()) dir.mkdirs();
        
        File file = FILE_PATH.toFile();
        File oldFile = Path.of(DIR, OLD_FILE_NAME).toFile();

        // Old to New Migration logic: If new file doesn't exist but old one does
        if (!file.exists() && oldFile.exists()) {
            boolean migrationSuccess = false;
            try (Reader reader = new FileReader(oldFile)) {
                Type type = new TypeToken<Map<String, String>>(){}.getType();
                Map<String, String> map = gson.fromJson(reader, type);
                if (map != null) {
                    for (Map.Entry<String, String> entry : map.entrySet()) {
                        // Hash existing plaintext passwords
                        credentials.put(entry.getKey(), hashPassword(entry.getValue()));
                    }
                }
                migrationSuccess = true;
            } catch (Exception e) {
                e.printStackTrace();
            }

            if (migrationSuccess) {
                save(); // Save to new auth_hash.json
                oldFile.renameTo(new File(dir, OLD_FILE_NAME + ".migrated")); // Rename old file
            }
            lastModified = file.exists() ? file.lastModified() : -1;
            return;
        }

        if (!file.exists()) {
            save(); // 写一个空文件 (Write an empty file)
            lastModified = file.exists() ? file.lastModified() : -1;
            return;
        }
        try (Reader reader = new FileReader(file)) {
            Type type = new TypeToken<Map<String, String>>(){}.getType();
            Map<String, String> map = gson.fromJson(reader, type);
            if (map != null) credentials.putAll(map);
        } catch (Exception e) {
            e.printStackTrace();
        }
        lastModified = file.lastModified();
    }

    private void save() {
        File dir = new File(DIR);
        if (!dir.exists()) dir.mkdirs();
        try (Writer writer = new FileWriter(FILE_PATH.toFile())) {
            gson.toJson(credentials, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
        File file = FILE_PATH.toFile();
        lastModified = file.exists() ? file.lastModified() : -1;
    }

    public boolean isRegistered(String name) {
        reloadIfChanged();
        return credentials.containsKey(name);
    }

    public void register(String name, String password) {
        reloadIfChanged();
        credentials.put(name, hashPassword(password));
        save();
    }

    public boolean checkPassword(String name, String password) {
        reloadIfChanged();
        return credentials.containsKey(name) && verifyPassword(password, credentials.get(name));
    }

    public void changePassword(String name, String newPassword) {
        reloadIfChanged();
        if (credentials.containsKey(name)) {
            credentials.put(name, hashPassword(newPassword));
            save();
        }
    }

    private String hashPassword(String password) {
        try {
            // Generate random 16 byte salt
            byte[] salt = new byte[16];
            new SecureRandom().nextBytes(salt);
            
            // Hash using PBKDF2 with the generated salt
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH);
            SecretKeyFactory skf = SecretKeyFactory.getInstance(ALGORITHM);
            byte[] hash = skf.generateSecret(spec).getEncoded();
            
            // Format: salt:hash (both Base64)
            return Base64.getEncoder().encodeToString(salt) + SALT_SEPARATOR + Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            e.printStackTrace();
            return null;
        }
    }

    private boolean verifyPassword(String inputPassword, String storedHash) {
        try {
            // Split value into salt and hash
            String[] parts = storedHash.split(SALT_SEPARATOR);
            if (parts.length != 2) return false;
            byte[] salt = Base64.getDecoder().decode(parts[0]);
            byte[] hash = Base64.getDecoder().decode(parts[1]);

            // Hash the input password using the same salt
            PBEKeySpec spec = new PBEKeySpec(inputPassword.toCharArray(), salt, ITERATIONS, KEY_LENGTH);
            SecretKeyFactory skf = SecretKeyFactory.getInstance(ALGORITHM);
            byte[] inputHash = skf.generateSecret(spec).getEncoded();

            // Compare new hash with stored hash
            return Arrays.equals(hash, inputHash);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}