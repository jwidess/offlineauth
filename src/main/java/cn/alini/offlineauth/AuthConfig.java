package cn.alini.offlineauth;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.*;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class AuthConfig {
    private static final String DIR = "config/offlineauth";
    private static final String FILE_NAME = "config.json";
    private static final Path FILE_PATH = Path.of(DIR, FILE_NAME);
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public int timeoutSeconds = 60;
    public int autoLoginExpireSeconds = 600;
    public int maxFailAttempts = 5;
    public int failLockSeconds = 60;
    public boolean autoLoginEnable = true;
    public boolean failBlockEnable = true;
    public boolean inventoryOnly = false; // true = Only backup inventory | false = Backup full player NBT
    public boolean mergeOnRestore = true; // true = Merge items received while unauthenticated | false = Overwrite
    public String prefix = "§7[§bAuth§7] ";
    public Map<String, String> messages = new HashMap<>();

    public AuthConfig() {
        // 默认消息初始化（不要在这里调用load！）
        // Default message initialization (Do not call load! here)
        messages.put("register_prompt", "§cFirst time joining! Please use /register <password> <confirm> to register your account!");
        messages.put("login_prompt", "§ePlease use /login <password> to login!");
        messages.put("already_registered", "§cYou are already registered! Please use /login <password> to login!");
        messages.put("not_registered", "§cYou are not registered! Please use /register <password> <confirm> to register!");
        messages.put("register_success", "§aRegistration successful! You are now logged in.");
        messages.put("login_success", "§aLogin successful!");
        messages.put("auto_login_success", "§aDetected same IP address, auto-login successful.");
        messages.put("password_mismatch", "§cPasswords do not match!");
        messages.put("wrong_password", "§cIncorrect password!");
        messages.put("timeout", "§cYou have been kicked for not logging in within the time limit!");
        messages.put("no_permission_register", "§cPremium players do not need to register!");
        messages.put("no_permission_login", "§cPremium players do not need to login!");
        messages.put("no_permission_changepwd", "§cPremium players do not need to change password!");
        messages.put("changepwd_success", "§aPassword changed successfully!");
        messages.put("changepwd_wrong", "§cOld password is incorrect!");
        messages.put("inventory_restored", "§aInventory restored");
        messages.put("chat_blocked", "§cYou must login to chat");
        messages.put("break_blocked", "§cYou must login to break blocks");
        messages.put("place_blocked", "§cYou must login to place blocks");
        messages.put("drop_blocked", "§cYou must login to drop items");
        messages.put("pickup_blocked", "§cYou must login to pickup items");
        messages.put("use_blocked", "§cYou must login to use items");
        messages.put("container_blocked", "§cYou must login to open inventory/containers");
        messages.put("fail_blocked", "§cToo many failed attempts! Please try again in {lock} seconds.");
        messages.put("fail_attempts_left", "§cIncorrect password! Attempts remaining: {left}");
        messages.put("help_header", "§6Offline Auth Command List:");
        messages.put("help_register", "§e/register <password> <confirm> §7- Register account (first time joining)");
        messages.put("help_login", "§e/login <password> §7- Login to account");
        messages.put("help_changepwd", "§e/changepassword <old> <new> §7- Change password");
        messages.put("auto_login_warn", "§e⚠Auto-login is enabled (same IP address will not need to login again for a short time). Do not use this feature on public computers to avoid account theft.");
        messages.put("reload_success", "§aConfiguration reloaded!");

        // 正确做法：在构造后，手动调用 load()，不要在构造里调用
        // Correct approach: Manually call load() after construction; do not call it within the constructor.
        // 由主类 new AuthConfig 后，再调用 config.load()
        // After the main class news AuthConfig, call config.load()
    }

    // 由主类调用 config.load()，不要循环new！
    // Called by the main class config.load(), do not loop new!
    public void load() {
        File dir = new File(DIR);
        if (!dir.exists()) dir.mkdirs();
        File file = FILE_PATH.toFile();
        if (!file.exists()) {
            save();
            return;
        }
        try (Reader reader = new FileReader(file)) {
            AuthConfig loaded = gson.fromJson(reader, AuthConfig.class);
            if (loaded != null) {
                this.timeoutSeconds = loaded.timeoutSeconds;
                this.autoLoginExpireSeconds = loaded.autoLoginExpireSeconds;
                this.maxFailAttempts = loaded.maxFailAttempts;
                this.failLockSeconds = loaded.failLockSeconds;
                this.autoLoginEnable = loaded.autoLoginEnable;
                this.inventoryOnly = loaded.inventoryOnly;
                this.mergeOnRestore = loaded.mergeOnRestore;
                this.failBlockEnable = loaded.failBlockEnable;
                this.prefix = loaded.prefix;
                if (loaded.messages != null) this.messages.putAll(loaded.messages);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void save() {
        File dir = new File(DIR);
        if (!dir.exists()) dir.mkdirs();
        try (Writer writer = new FileWriter(FILE_PATH.toFile())) {
            gson.toJson(this, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String msg(String key) {
        return prefix + messages.getOrDefault(key, "Missing config: " + key);
    }

    public String msg(String key, Map<String, String> params) {
        String raw = prefix + messages.getOrDefault(key, "Missing config: " + key);
        if (params != null) {
            for (Map.Entry<String, String> e : params.entrySet()) {
                raw = raw.replace("{" + e.getKey() + "}", e.getValue());
            }
        }
        return raw;
    }
}