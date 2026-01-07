# OfflineAuth

### English | [简体中文](README_zh-CN.md)
A lightweight authentication mod for offline (cracked) Minecraft servers.  
Supports registration, login, inventory protection, brute-force prevention, and auto-login based on IP.

> **Requires [TrueUUID](https://github.com/YuWan-030/TrueUUID) mod as a dependency!**

## Features

- **Register/Login/Change Password** — Secure commands for offline players.
- **Inventory Protection** — Unauthenticated player's inventory is backed up and restored on login.
- **Brute-force Protection** — Too many failed attempts will lock the account for a configurable period and kick the player.
- **IP Auto-login** — Players can be automatically logged in within a set time window if they join from the same IP.
- **Configurable Messages** — All prompts and behaviors can be customized in `config/offlineauth/config.json`.
- **No interference with online (premium) accounts** — Only offline UUID players are affected.
- **Command `/auth help`** — View all available commands.

## Commands

- `/register <password> <confirm>` — Register a new account.
- `/login <password>` — Login to your account.
- `/changepassword <old> <new>` — Change your password.
- `/auth help` — Show help information.
- `/auth reload` — Reload the configuration file (requires permission level 2).

## Configuration

Edit `config/offlineauth/config.json` to customize:

- `timeoutSeconds` — Kick unauthenticated players after X seconds.
- `maxFailAttempts` — Maximum login/register failures before lock/kick.
- `failLockSeconds` — Lockout duration after too many failures.
- `autoLoginEnable` — Enable/disable auto-login feature.
- `autoLoginExpireSeconds` — Time window for IP-based auto-login.
- `messages` — Customize all prompts and warnings.
- `inventoryOnly` - Only backup player inventory data during auth.
  - **False** (*Default*) | In this mode all player NBT data is backed up, including mod data like Curios, backpacks, etc.
  - **True** | Only the player inventory data is backed up, this is faster and acceptable for Vanilla servers.
- `mergeOnRestore` - Merge items received while unauthenticated (e.g. Starter Kits) with the restored inventory.
  - **True** (*Default*) | Items are merged. If inventory is full, items are dropped. **NOTE**: This should always be enabled if `inventoryOnly` is **True** otherwise dupe glitches are possible.
  - **False** | Items received while unauthenticated are discarded.

## Security Notice

- **Auto-login (same IP)** is convenient, but insecure on public computers/networks. Always warn players to protect their accounts.
- Passwords are stored in plain text (by default), use at your own risk or extend for encryption.

## How to Install

1. **Install [TrueUUID](https://github.com/YuWan-030/TrueUUID) mod on your server. (Required!)**
2. Place the OfflineAuth mod jar in your server's `mods` folder.
3. Start your server once to generate the config files.
4. Edit `config.json` as needed.
5. Restart the server.

## License

LGPLv3

## Credits

YuWan-030  
2025

