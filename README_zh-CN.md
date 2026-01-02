# OfflineAuth 离线认证

### [English](README.md) | 简体中文
专为离线（非正版）Minecraft 服务器设计的轻量级认证 MOD。  
支持注册、登录、背包保护、防暴力破解、IP自动登录等功能。

> **需要 [TrueUUID](https://github.com/YuWan-030/TrueUUID) 模组作为前置！**

## 功能特色

- **注册/登录/改密指令** — 离线玩家专用，安全便捷
- **背包保护** — 未认证玩家背包自动备份，登录后恢复，防止丢失/刷物品
- **暴力破解防护** — 连续失败达到设定次数自动锁定账号并踢出，保护安全
- **同IP自动登录** — 支持短时间内同IP自动登录，提升体验（可关闭）
- **定制消息/行为** — 所有提示和参数均可在 `config/offlineauth/config.json` 配置
- **正版不受影响** — 仅对离线UUID玩家生效，正版玩家无需认证
- **指令 `/auth help`** — 查看所有可用命令及帮助

## 指令列表

- `/register <密码> <确认密码>` — 注册新账户
- `/login <密码>` — 登录账户
- `/changepassword <旧密码> <新密码>` — 修改密码
- `/auth help` — 查看帮助信息

## 配置说明

可在 `config/offlineauth/config.json` 自定义：

- `timeoutSeconds` — 未登录超时踢出时间（秒）
- `maxFailAttempts` — 连续失败最大次数（达到后锁定并踢出）
- `failLockSeconds` — 锁定时长（秒）
- `autoLoginEnable` — 是否开启自动登录功能
- `autoLoginExpireSeconds` — 自动登录时间窗口（秒）
- `messages` — 所有提示/警告内容均可自定义

## 安全提醒

- **自动登录（同IP）功能** 虽然便捷，但在网吧/公共电脑环境下存在被盗号风险。务必提醒玩家注意账号安全。
- 密码现已使用 PBKDF2 算法和随机盐进行安全哈希，并存储于服务器的 `config/offlineauth/auth_hash.json` 中。

## 安装方法

1. **请先安装 [TrueUUID](https://github.com/YuWan-030/TrueUUID) 模组！（必需）**
2. 将 OfflineAuth mod jar 文件放入服务器 `mods` 文件夹
3. 首次启动服务器自动生成配置文件
4. 编辑 `config.json` 按需调整参数
5. 重启服务器生效

## 许可

LGPLv3

## 作者

YuWan-030  
2025
