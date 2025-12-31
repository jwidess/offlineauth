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
    public String prefix = "§7[§bAuth§7] ";
    public Map<String, String> messages = new HashMap<>();

    public AuthConfig() {
        // 默认消息初始化（不要在这里调用load！）
        messages.put("register_prompt", "§c首次进服，请使用 /register 密码 确认密码 注册账户！");
        messages.put("login_prompt", "§e请使用 /login 密码 登录账户！");
        messages.put("already_registered", "§c您已注册，请用 /login 密码 登录！");
        messages.put("not_registered", "§c您尚未注册，请用 /register 密码 确认密码 注册！");
        messages.put("register_success", "§a注册成功，已自动登入！");
        messages.put("login_success", "§a登录成功！");
        messages.put("auto_login_success", "§a检测到同IP设备，已自动登录。");
        messages.put("password_mismatch", "§c两次输入的密码不一致！");
        messages.put("wrong_password", "§c密码错误！");
        messages.put("timeout", "§c未登录超时已被踢出！");
        messages.put("no_permission_register", "§c正版玩家无需注册！");
        messages.put("no_permission_login", "§c正版玩家无需登录！");
        messages.put("no_permission_changepwd", "§c正版玩家无需修改密码！");
        messages.put("changepwd_success", "§a密码修改成功！");
        messages.put("changepwd_wrong", "§c原密码错误！");
        messages.put("inventory_restored", "§a背包已恢复");
        messages.put("chat_blocked", "§c未登录禁止发言");
        messages.put("break_blocked", "§c未登录禁止破坏方块");
        messages.put("place_blocked", "§c未登录禁止放置方块");
        messages.put("drop_blocked", "§c未登录禁止丢弃物品");
        messages.put("pickup_blocked", "§c未登录禁止拾取物品");
        messages.put("use_blocked", "§c未登录禁止使用物品");
        messages.put("container_blocked", "§c未登录禁止打开背包/容器");
        messages.put("fail_blocked", "§c错误次数过多，请{lock}秒后再试！");
        messages.put("fail_attempts_left", "§c密码错误！剩余尝试次数：{left}");
        messages.put("help_header", "§6离线认证插件指令列表：");
        messages.put("help_register", "§e/register 密码 确认密码 §7- 注册账户（首次进服使用）");
        messages.put("help_login", "§e/login 密码 §7- 登录账户");
        messages.put("help_changepwd", "§e/changepassword 旧密码 新密码 §7- 修改密码");
        messages.put("auto_login_warn", "§e⚠已启用自动登录（同IP设备短时间内无需重复登录）。如在网吧/公共电脑请勿使用此功能，避免账号被盗。");
        messages.put("reload_success", "§a配置已重载！");

        // 正确做法：在构造后，手动调用 load()，不要在构造里调用
        // 由主类 new AuthConfig 后，再调用 config.load()
    }

    // 由主类调用 config.load()，不要循环new！
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
        return prefix + messages.getOrDefault(key, "配置缺失：" + key);
    }

    public String msg(String key, Map<String, String> params) {
        String raw = prefix + messages.getOrDefault(key, "配置缺失：" + key);
        if (params != null) {
            for (Map.Entry<String, String> e : params.entrySet()) {
                raw = raw.replace("{" + e.getKey() + "}", e.getValue());
            }
        }
        return raw;
    }
}