package cn.alini.offlineauth;

import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent;
import net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.commands.Commands;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import cn.alini.trueuuid.api.TrueuuidApi;

import java.io.*;
import java.lang.reflect.Type;
import java.util.*;

@Mod.EventBusSubscriber
public class OfflineAuthHandler {
    private static final JsonAuthStorage storage = new JsonAuthStorage();
    private static final AuthConfig config = new AuthConfig();
    private static final Set<String> loggedIn = new HashSet<>();
    private static final Map<String, ItemStack[]> inventoryBackup = new HashMap<>();
    private static final Map<String, Long> joinTimeMap = new HashMap<>();
    private static final Map<String, Integer> notLoggedTick = new HashMap<>();
    private static final Map<String, double[]> notLoggedSpawnPos = new HashMap<>();
    private static final String INVENTORY_DIR = "config/offlineauth/inventory";

    private static final String AUTOLOGIN_FILE = "config/offlineauth/autologin.json";
    private static final String FAIL_FILE = "config/offlineauth/fail.json";
    private static final Gson gson = new Gson();
    private static final Map<String, AutoLoginInfo> autoLoginMap = new HashMap<>();
    private static final Map<String, FailInfo> failMap = new HashMap<>();
    static { config.load(); loadAutoLogin(); loadFail(); }

    private static boolean isOfflinePlayer(ServerPlayer player) {
        return !TrueuuidApi.isPremium(player.getName().getString().toLowerCase(Locale.ROOT));
    }

    private static String getPlayerIp(ServerPlayer player) {
        ServerGamePacketListenerImpl conn = player.connection;
        String raw = conn.getRemoteAddress().toString(); // /ip:port
        if (raw.startsWith("/")) raw = raw.substring(1);
        int idx = raw.indexOf(':');
        if (idx > 0) return raw.substring(0, idx);
        return raw;
    }

    public static class AutoLoginInfo {
        public String ip;
        public long lastLoginTime;
    }
    public static class FailInfo {
        public int failCount;
        public long lastFailTime;
        public long lockUntil;
    }

    private static void loadAutoLogin() {
        File f = new File(AUTOLOGIN_FILE);
        if (!f.exists()) return;
        try (Reader r = new FileReader(f)) {
            Type t = new TypeToken<Map<String, AutoLoginInfo>>(){}.getType();
            Map<String, AutoLoginInfo> m = gson.fromJson(r, t);
            if (m != null) autoLoginMap.putAll(m);
        } catch (Exception e) { e.printStackTrace(); }
    }
    private static void saveAutoLogin() {
        File f = new File(AUTOLOGIN_FILE);
        try (Writer w = new FileWriter(f)) {
            gson.toJson(autoLoginMap, w);
        } catch (Exception e) { e.printStackTrace(); }
    }
    private static void loadFail() {
        File f = new File(FAIL_FILE);
        if (!f.exists()) return;
        try (Reader r = new FileReader(f)) {
            Type t = new TypeToken<Map<String, FailInfo>>(){}.getType();
            Map<String, FailInfo> m = gson.fromJson(r, t);
            if (m != null) failMap.putAll(m);
        } catch (Exception e) { e.printStackTrace(); }
    }
    private static void saveFail() {
        File f = new File(FAIL_FILE);
        try (Writer w = new FileWriter(f)) {
            gson.toJson(failMap, w);
        } catch (Exception e) { e.printStackTrace(); }
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerLoggedInEvent event) {
        ServerPlayer player = (ServerPlayer) event.getEntity();
        if (isOfflinePlayer(player)) {
            String name = player.getName().getString();
            loggedIn.remove(name);
            joinTimeMap.put(name, System.currentTimeMillis());
            notLoggedTick.put(name, 0);
            notLoggedSpawnPos.put(name, new double[]{player.getX(), player.getY(), player.getZ()}); // 记录上线坐标 (Record login coordinates)

            // --- 自动登录检查 (Auto-login check) ---
            String ip = getPlayerIp(player);
            AutoLoginInfo info = autoLoginMap.get(name);
            if (config.autoLoginEnable && info != null && info.ip != null && info.ip.equals(ip)
                    && System.currentTimeMillis() - info.lastLoginTime < config.autoLoginExpireSeconds * 1000L
                    && storage.isRegistered(name)) {
                loggedIn.add(name);
                joinTimeMap.remove(name);
                notLoggedTick.remove(name);
                notLoggedSpawnPos.remove(name);
                restoreInventoryIfNeeded(player);
                player.setInvulnerable(false); // 自动登录后恢复正常 (Restore to normal after auto-login)
                player.sendSystemMessage(Component.literal(config.msg("auto_login_success")));
                player.sendSystemMessage(Component.literal(config.msg("auto_login_warn")));
                return;
            }

            // 背包暂存，防止未登录时操作物品 (Inventory backup to prevent item manipulation when not logged in)
            if (!inventoryBackup.containsKey(name) && !hasInventoryFile(name)) {
                ItemStack[] inv = new ItemStack[player.getInventory().getContainerSize()];
                for (int i = 0; i < inv.length; i++) {
                    inv[i] = player.getInventory().getItem(i).copy();
                }
                backupInventory(name, inv);
                player.getInventory().clearContent();
            } else if (!inventoryBackup.containsKey(name) && hasInventoryFile(name)) {
                player.getInventory().clearContent();
            }
            // 未登录时无敌 (Invulnerable when not logged in)
            player.setInvulnerable(true);
        } else {
            player.setInvulnerable(false);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (!(event.player instanceof ServerPlayer player)) return;
        String name = player.getName().getString();
        if (isOfflinePlayer(player) && !loggedIn.contains(name)) {
            double[] pos = notLoggedSpawnPos.getOrDefault(name, new double[]{player.getX(), player.getY(), player.getZ()});
            player.teleportTo(pos[0], pos[1], pos[2]);
            Long joinTime = joinTimeMap.get(name);
            if (joinTime != null && System.currentTimeMillis() - joinTime > config.timeoutSeconds * 1000L) {
                player.connection.disconnect(Component.literal(config.msg("timeout")));
                joinTimeMap.remove(name);
                notLoggedTick.remove(name);
                notLoggedSpawnPos.remove(name);
                return;
            }
            int tick = notLoggedTick.getOrDefault(name, 0) + 1;
            if (tick >= 100) { // 5秒=100tick
                if (!storage.isRegistered(name)) {
                    player.sendSystemMessage(Component.literal(config.msg("register_prompt")));
                } else {
                    player.sendSystemMessage(Component.literal(config.msg("login_prompt")));
                }
                tick = 0;
            }
            notLoggedTick.put(name, tick);
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerLoggedOutEvent event) {
        ServerPlayer player = (ServerPlayer) event.getEntity();
        String name = player.getName().getString();
        loggedIn.remove(name);
        joinTimeMap.remove(name);
        notLoggedTick.remove(name);
        notLoggedSpawnPos.remove(name);
    }

    @SubscribeEvent
    public static void onChat(ServerChatEvent event) {
        ServerPlayer player = event.getPlayer();
        if (isOfflinePlayer(player) && !loggedIn.contains(player.getName().getString())) {
            event.setCanceled(true);
            player.sendSystemMessage(Component.literal(config.msg("chat_blocked")));
        }
    }
    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player) {
            if (isOfflinePlayer(player) && !loggedIn.contains(player.getName().getString())) {
                event.setCanceled(true);
                player.sendSystemMessage(Component.literal(config.msg("break_blocked")));
            }
        }
    }
    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (isOfflinePlayer(player) && !loggedIn.contains(player.getName().getString())) {
                event.setCanceled(true);
                player.sendSystemMessage(Component.literal(config.msg("place_blocked")));
            }
        }
    }
    @SubscribeEvent
    public static void onItemToss(ItemTossEvent event) {
        ServerPlayer player = (ServerPlayer) event.getPlayer();
        if (isOfflinePlayer(player) && !loggedIn.contains(player.getName().getString())) {
            event.setCanceled(true);
            player.sendSystemMessage(Component.literal(config.msg("drop_blocked")));
        }
    }
    @SubscribeEvent
    public static void onItemPickup(EntityItemPickupEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (isOfflinePlayer(player) && !loggedIn.contains(player.getName().getString())) {
                event.setCanceled(true);
                player.sendSystemMessage(Component.literal(config.msg("pickup_blocked")));
            }
        }
    }
    @SubscribeEvent
    public static void onRightClick(PlayerInteractEvent.RightClickItem event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (isOfflinePlayer(player) && !loggedIn.contains(player.getName().getString())) {
                event.setCanceled(true);
                player.sendSystemMessage(Component.literal(config.msg("use_blocked")));
            }
        }
    }

    private static void backupInventory(String name, ItemStack[] inv) {
        inventoryBackup.put(name, inv);
        File dir = new File(INVENTORY_DIR);
        if (!dir.exists()) dir.mkdirs();
        File file = new File(dir, name + ".json");
        try (FileOutputStream fos = new FileOutputStream(file)) {
            ListTag list = new ListTag();
            for (ItemStack stack : inv) {
                list.add(stack.save(new CompoundTag()));
            }
            CompoundTag root = new CompoundTag();
            root.put("items", list);
            NbtIo.writeCompressed(root, fos);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    private static boolean hasInventoryFile(String name) {
        File file = new File(INVENTORY_DIR, name + ".json");
        return file.exists();
    }
    private static ItemStack[] loadBackupInventory(String name, int size) {
        if (inventoryBackup.containsKey(name)) return inventoryBackup.get(name);
        File file = new File(INVENTORY_DIR, name + ".json");
        if (!file.exists()) return null;
        try (FileInputStream fis = new FileInputStream(file)) {
            CompoundTag root = NbtIo.readCompressed(fis);
            ListTag list = root.getList("items", 10);
            ItemStack[] inv = new ItemStack[size];
            for (int i = 0; i < inv.length && i < list.size(); i++) {
                inv[i] = ItemStack.of(list.getCompound(i));
            }
            for (int i = list.size(); i < inv.length; i++) inv[i] = ItemStack.EMPTY;
            inventoryBackup.put(name, inv);
            return inv;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
    private static void removeBackup(String name) {
        inventoryBackup.remove(name);
        File file = new File(INVENTORY_DIR, name + ".json");
        if (file.exists()) file.delete();
    }
    private static void restoreInventoryIfNeeded(ServerPlayer player) {
        String name = player.getName().getString();
        ItemStack[] backup = loadBackupInventory(name, player.getInventory().getContainerSize());
        if (backup != null) {
            for (int i = 0; i < backup.length; i++) {
                player.getInventory().setItem(i, backup[i]);
            }
            removeBackup(name);
            player.sendSystemMessage(Component.literal(config.msg("inventory_restored")));
        }
    }

    private static boolean checkFailBlock(ServerPlayer player, String name) {
        if (!config.failBlockEnable) return false;
        FailInfo fi = failMap.get(name);
        if (fi != null && fi.lockUntil > System.currentTimeMillis()) {
            Map<String,String> map = new HashMap<>();
            long left = (fi.lockUntil - System.currentTimeMillis())/1000+1;
            map.put("lock", String.valueOf(left));
            player.sendSystemMessage(Component.literal(config.msg("fail_blocked", map)));
            return true;
        }
        return false;
    }
    private static boolean recordFail(ServerPlayer player, String name) {
        if (!config.failBlockEnable) return false;
        FailInfo fi = failMap.getOrDefault(name, new FailInfo());
        fi.failCount++;
        fi.lastFailTime = System.currentTimeMillis();
        boolean kicked = false;
        if (fi.failCount >= config.maxFailAttempts) {
            fi.lockUntil = System.currentTimeMillis() + config.failLockSeconds * 1000L;
            Map<String,String> map = new HashMap<>();
            map.put("lock", String.valueOf(config.failLockSeconds));
            player.connection.disconnect(Component.literal(config.msg("fail_blocked", map)));
            kicked = true;
        } else {
            Map<String,String> map = new HashMap<>();
            map.put("left", String.valueOf(config.maxFailAttempts-fi.failCount));
            player.sendSystemMessage(Component.literal(config.msg("fail_attempts_left", map)));
        }
        failMap.put(name, fi);
        saveFail();
        return kicked;
    }
    private static void clearFail(String name) {
        if (failMap.remove(name) != null) saveFail();
    }

    @SubscribeEvent
    public static void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("register")
                        .then(Commands.argument("password", StringArgumentType.string())
                                .then(Commands.argument("confirm", StringArgumentType.string())
                                        .executes(ctx -> {
                                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                                            String name = player.getName().getString();
                                            if (checkFailBlock(player, name)) return 1;
                                            String pwd = StringArgumentType.getString(ctx, "password");
                                            String confirm = StringArgumentType.getString(ctx, "confirm");
                                            if (!isOfflinePlayer(player)) {
                                                player.sendSystemMessage(Component.literal(config.msg("no_permission_register")));
                                                return 1;
                                            }
                                            if (!pwd.equals(confirm)) {
                                                recordFail(player, name);
                                                return 1;
                                            }
                                            if (storage.isRegistered(name)) {
                                                player.sendSystemMessage(Component.literal(config.msg("already_registered")));
                                                return 1;
                                            }
                                            storage.register(name, pwd);
                                            loggedIn.add(name);
                                            joinTimeMap.remove(name);
                                            notLoggedTick.remove(name);
                                            notLoggedSpawnPos.remove(name);
                                            // Restore after login
                                            restoreInventoryIfNeeded(player);
                                            player.setInvulnerable(false); // 登录后恢复 (Restore after login)
                                            player.sendSystemMessage(Component.literal(config.msg("register_success")));
                                            clearFail(name);
                                            if (config.autoLoginEnable) {
                                                String ip = getPlayerIp(player);
                                                AutoLoginInfo info = new AutoLoginInfo();
                                                info.ip = ip;
                                                info.lastLoginTime = System.currentTimeMillis();
                                                autoLoginMap.put(name, info);
                                                saveAutoLogin();
                                            }
                                            return 1;
                                        })
                                )
                        )
        );
        event.getDispatcher().register(
                Commands.literal("reg")
                        .then(Commands.argument("password", StringArgumentType.string())
                                .then(Commands.argument("confirm", StringArgumentType.string())
                                        .executes(ctx -> {
                                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                                            String name = player.getName().getString();
                                            if (checkFailBlock(player, name)) return 1;
                                            String pwd = StringArgumentType.getString(ctx, "password");
                                            String confirm = StringArgumentType.getString(ctx, "confirm");
                                            if (!isOfflinePlayer(player)) {
                                                player.sendSystemMessage(Component.literal(config.msg("no_permission_register")));
                                                return 1;
                                            }
                                            if (!pwd.equals(confirm)) {
                                                recordFail(player, name);
                                                return 1;
                                            }
                                            if (storage.isRegistered(name)) {
                                                player.sendSystemMessage(Component.literal(config.msg("already_registered")));
                                                return 1;
                                            }
                                            storage.register(name, pwd);
                                            loggedIn.add(name);
                                            joinTimeMap.remove(name);
                                            notLoggedTick.remove(name);
                                            notLoggedSpawnPos.remove(name);
                                            restoreInventoryIfNeeded(player);
                                            player.setInvulnerable(false);
                                            player.sendSystemMessage(Component.literal(config.msg("register_success")));
                                            clearFail(name);
                                            if (config.autoLoginEnable) {
                                                String ip = getPlayerIp(player);
                                                AutoLoginInfo info = new AutoLoginInfo();
                                                info.ip = ip;
                                                info.lastLoginTime = System.currentTimeMillis();
                                                autoLoginMap.put(name, info);
                                                saveAutoLogin();
                                            }
                                            return 1;
                                        })
                                )
                        )
        );
        event.getDispatcher().register(
                Commands.literal("login")
                        .then(Commands.argument("password", StringArgumentType.string())
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    String name = player.getName().getString();
                                    if (checkFailBlock(player, name)) return 1;
                                    String pwd = StringArgumentType.getString(ctx, "password");
                                    if (!isOfflinePlayer(player)) {
                                        player.sendSystemMessage(Component.literal(config.msg("no_permission_login")));
                                        return 1;
                                    }
                                    if (!storage.isRegistered(name)) {
                                        player.sendSystemMessage(Component.literal(config.msg("not_registered")));
                                        return 1;
                                    }
                                    if (!storage.checkPassword(name, pwd)) {
                                        recordFail(player, name);
                                        return 1;
                                    }
                                    loggedIn.add(name);
                                    joinTimeMap.remove(name);
                                    notLoggedTick.remove(name);
                                    notLoggedSpawnPos.remove(name);
                                    restoreInventoryIfNeeded(player);
                                    player.setInvulnerable(false); // 登录后恢复 (Restore after login)
                                    player.sendSystemMessage(Component.literal(config.msg("login_success")));
                                    clearFail(name);
                                    if (config.autoLoginEnable) {
                                        String ip = getPlayerIp(player);
                                        AutoLoginInfo info = new AutoLoginInfo();
                                        info.ip = ip;
                                        info.lastLoginTime = System.currentTimeMillis();
                                        autoLoginMap.put(name, info);
                                        saveAutoLogin();
                                    }
                                    return 1;
                                })
                        )
        );
        event.getDispatcher().register(
                Commands.literal("l")
                        .then(Commands.argument("password", StringArgumentType.string())
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    String name = player.getName().getString();
                                    if (checkFailBlock(player, name)) return 1;
                                    String pwd = StringArgumentType.getString(ctx, "password");
                                    if (!isOfflinePlayer(player)) {
                                        player.sendSystemMessage(Component.literal(config.msg("no_permission_login")));
                                        return 1;
                                    }
                                    if (!storage.isRegistered(name)) {
                                        player.sendSystemMessage(Component.literal(config.msg("not_registered")));
                                        return 1;
                                    }
                                    if (!storage.checkPassword(name, pwd)) {
                                        recordFail(player, name);
                                        return 1;
                                    }
                                    loggedIn.add(name);
                                    joinTimeMap.remove(name);
                                    notLoggedTick.remove(name);
                                    notLoggedSpawnPos.remove(name);
                                    restoreInventoryIfNeeded(player);
                                    player.setInvulnerable(false); // 登录后恢复 (Restore after login)
                                    player.sendSystemMessage(Component.literal(config.msg("login_success")));
                                    clearFail(name);
                                    if (config.autoLoginEnable) {
                                        String ip = getPlayerIp(player);
                                        AutoLoginInfo info = new AutoLoginInfo();
                                        info.ip = ip;
                                        info.lastLoginTime = System.currentTimeMillis();
                                        autoLoginMap.put(name, info);
                                        saveAutoLogin();
                                    }
                                    return 1;
                                })
                        )
        );
        event.getDispatcher().register(
                Commands.literal("changepassword")
                        .then(Commands.argument("old", StringArgumentType.string())
                                .then(Commands.argument("new", StringArgumentType.string())
                                        .executes(ctx -> {
                                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                                            String name = player.getName().getString();
                                            if (checkFailBlock(player, name)) return 1;
                                            String oldPwd = StringArgumentType.getString(ctx, "old");
                                            String newPwd = StringArgumentType.getString(ctx, "new");
                                            if (!isOfflinePlayer(player)) {
                                                player.sendSystemMessage(Component.literal(config.msg("no_permission_changepwd")));
                                                return 1;
                                            }
                                            if (!storage.isRegistered(name) || !storage.checkPassword(name, oldPwd)) {
                                                recordFail(player, name);
                                                return 1;
                                            }
                                            storage.changePassword(name, newPwd);
                                            player.sendSystemMessage(Component.literal(config.msg("changepwd_success")));
                                            clearFail(name);
                                            if (config.autoLoginEnable) {
                                                String ip = getPlayerIp(player);
                                                AutoLoginInfo info = new AutoLoginInfo();
                                                info.ip = ip;
                                                info.lastLoginTime = System.currentTimeMillis();
                                                autoLoginMap.put(name, info);
                                                saveAutoLogin();
                                            }
                                            return 1;
                                        })
                                )
                        )
        );
        event.getDispatcher().register(
                Commands.literal("auth")
                        .then(Commands.literal("help")
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    player.sendSystemMessage(Component.literal(config.msg("help_header")));
                                    player.sendSystemMessage(Component.literal(config.msg("help_register")));
                                    player.sendSystemMessage(Component.literal(config.msg("help_login")));
                                    player.sendSystemMessage(Component.literal(config.msg("help_changepwd")));
                                    return 1;
                                })
                        )
                        .then(Commands.literal("reload")
                                .requires(source -> source.hasPermission(2))
                                .executes(ctx -> {
                                    config.load();
                                    ctx.getSource().sendSuccess(() -> Component.literal(config.msg("reload_success")), true);
                                    return 1;
                                })
                        )
        );
    }
}