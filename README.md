# Strict / 严格模组

> Fabric server-side mod for Minecraft 1.21.x — enforces mod whitelist and encrypted token authentication on player login.
>
> Minecraft 1.21.x Fabric 服务端模组，登录时强制模组白名单验证与加密令牌认证。

---

## Overview / 概述

**Strict** is a server-side Fabric mod that locks down your Minecraft 1.21.3 server. When a player connects, the server initiates a custom handshake over the `strict:check` channel: the client must respond with an AES-GCM encrypted token and its full mod list. The server verifies the token's integrity (SHA-256 hash, nonce uniqueness, timestamp freshness) and checks the mod list against a configurable allow/exclude policy. Players who fail verification get kicked.

**Strict（严格模组）** 是 Minecraft 1.21.3 Fabric 服务端模组，专治服务器裸奔。玩家连接时服务器通过 `strict:check` 通道发起自定义握手，客户端必须回传 AES-GCM 加密令牌和完整模组列表。服务器校验令牌完整性（SHA-256 哈希、nonce 唯一性、时间戳新鲜度），并将模组列表与可配置的允许/排除策略比对。验证失败直接踢人。

---

## Features / 功能

- **Mod verification / 模组验证** — clients report loaded mods; server rejects anything outside the allow/exclude policy. 客户端上报模组列表，不在策略内的直接拒绝。
- **Encrypted player authentication / 加密玩家认证** — AES-GCM encrypted token with player name, nonce, timestamp, and SHA-256 hash. Resists tampering and replay attacks. AES-GCM 加密令牌，防篡改防重放。
- **Access control / 访问控制** — public mode (verified = join) or private mode (new players need admin approval). 公开模式直接进，私密模式等管理员批。
- **Approval flow / 审批流程** — in private mode, ops get clickable `[Accept]` / `[Reject]` / `[Blacklist]` buttons in chat. 私密模式下管理员收到可点击的同意/拒绝/拉黑按钮。
- **Blacklist / 黑名单** — ban by UUID or computer name. 按 UUID 或计算机名封禁。
- **YAML config / YAML 配置** — everything in `config/strict/config.yml`, hot-reloadable. 全部配置集中在一个 YAML 文件，支持运行时重载。
- **Verbose logging / 详细日志** — toggle via command or config. 可通过命令或配置开关。

---

## Tech Stack / 技术栈

| | |
|---|---|
| Language | Java 21 |
| Loader | Fabric Loader `0.16.14` |
| Fabric API | `0.114.0+1.21.3` |
| Minecraft | `1.21.3` (Yarn `1.21.3+build.2`) |
| Build | Gradle + Fabric Loom `1.10-SNAPSHOT` |
| Config | SnakeYAML `2.6` (bundled via `include`) |

---

## Project Structure / 项目结构

```
strict/
├── build.gradle
├── gradle.properties
├── LICENSE.txt                   # MIT
└── src/
    ├── main/java/com/strict/
    │   ├── Main.java                          # ModInitializer
    │   ├── module/
    │   │   ├── Module.java
    │   │   ├── ModuleManager.java
    │   │   └── impl/ModCheckModule.java       # 核心握手/验证/访问控制
    │   ├── command/CommandHandler.java         # /strict 命令树
    │   ├── config/ConfigManager.java          # config.yml 读写
    │   └── utils/CryptoUtils.java             # AES-GCM + SHA-256
    ├── main/resources/
    │   ├── fabric.mod.json
    │   ├── strict.mixins.json
    │   └── assets/config/config.yml           # 默认配置
    └── client/java/com/strict/client/
        ├── MainClient.java
        ├── MainDataGenerator.java
        └── ClientNetworking.java              # 发送令牌+模组列表
```

---

## Getting Started / 快速开始

**Prerequisites / 前置条件:** JDK 21, Fabric-enabled Minecraft `1.21.3` server, Fabric API.

**Build / 构建:**

```bash
./gradlew build      # Linux / macOS
.\gradlew.bat build  # Windows
```

Output: `build/libs/strict-1.0-SNAPSHOT.jar`

**Server install / 服务端安装:** Drop the jar + Fabric API into `mods/`, start once to generate `config/strict/config.yml`, edit config, restart.

**Client install / 客户端安装:** Same jar + Fabric API into client `mods/`. The client side handles the handshake response automatically.

---

## Usage / 使用方法

All commands under `/strict`, require OP level 4:

| Command | Description / 说明 |
|---|---|
| `/strict mode public\|private` | Switch access mode / 切换访问模式 |
| `/strict log <true\|false>` | Toggle verbose logging / 开关详细日志 |
| `/strict accept <player>` | Approve pending player / 批准待审玩家 |
| `/strict reject <player>` | Reject pending player / 拒绝待审玩家 |
| `/strict blacklist add\|remove\|list` | Manage blacklist / 管理黑名单 |
| `/strict info <player>` | Show player's reported info / 查看玩家上报信息 |
| `/strict mod allow\|exclude\|remove <modId>` | Manage mod policy / 管理模组策略 |
| `/strict mod allowFabric <true\|false>` | Auto-allow `fabric-` prefixed mods / 自动放行 fabric- 前缀模组 |
| `/strict mod list` | List mod policy / 列出模组策略 |
| `/strict reload` | Reload config / 重载配置 |

---

## Configuration / 配置

`config/strict/config.yml`:

```yaml
privateMode: false
logEnabled: true
secretKey: "dwgx1337"        # 正式用之前务必改掉
secretKeyHash: ""
allowedMods:
  - minecraft
  - strict
  - fabricloader
  - java
  - mixinextras
  - org_yaml_snakeyaml
excludedMods: []
allowFabricMods: true
blacklistedPlayers: []
```

| Key | Purpose / 用途 |
|---|---|
| `privateMode` | Require admin approval for new players / 新玩家需管理员审批 |
| `logEnabled` | Verbose logging / 详细日志 |
| `secretKey` | AES-GCM key derivation + token hashing. Change before use / 密钥，部署前必须修改 |
| `allowedMods` / `excludedMods` | Explicit allow/deny lists / 显式允许/拒绝列表 |
| `allowFabricMods` | Auto-allow `fabric-*` mod IDs / 自动放行 fabric 系模组 |
| `blacklistedPlayers` | UUIDs or computer names to reject / 封禁的 UUID 或计算机名 |

---

## How It Works / 工作原理

1. Player login triggers `strict:check` query via `ServerLoginConnectionEvents.QUERY_START`.
2. Client (`ClientNetworking`) replies with `CheckPayload`: AES-GCM encrypted token + OS username + mod list.
3. Server (`ModCheckModule`) decrypts token, verifies SHA-256 hash, checks nonce uniqueness (10-min window) and timestamp freshness (5-min window), then validates mod list.
4. Public mode: verified players join (ops bypass). Private mode: unknown players held in `pendingPlayers`, disconnected with "waiting for approval" until an op accepts.

---

## Status / 状态

WIP (`1.0-SNAPSHOT`). Targets Minecraft `1.21.3` specifically.

开发中（`1.0-SNAPSHOT`），绑定 Minecraft `1.21.3`。

---

## License / 许可证

MIT — see [`LICENSE.txt`](LICENSE.txt). Copyright (C) 2025 dwgx.
