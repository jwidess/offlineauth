package cn.alini.offlineauth;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class JsonAuthStorage {
    private static final String DIR = "config/offlineauth";
    private static final String FILE_NAME = "auth.json";
    private static final Path FILE_PATH = Path.of(DIR, FILE_NAME);
    private static final Gson gson = new Gson();

    private Map<String, String> credentials = new HashMap<>();
    private long lastModified = -1;

    public JsonAuthStorage() {
        load();
    }

    // 每次操作前热加载
    private void reloadIfChanged() {
        File file = FILE_PATH.toFile();
        if (!file.exists()) {
            credentials = new HashMap<>();
            lastModified = -1;
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
        if (!file.exists()) {
            save(); // 写一个空文件
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
        credentials.put(name, password);
        save();
    }

    public boolean checkPassword(String name, String password) {
        reloadIfChanged();
        return credentials.containsKey(name) && credentials.get(name).equals(password);
    }

    public void changePassword(String name, String newPassword) {
        reloadIfChanged();
        if (credentials.containsKey(name)) {
            credentials.put(name, newPassword);
            save();
        }
    }
}