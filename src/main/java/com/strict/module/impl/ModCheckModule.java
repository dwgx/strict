package com.strict.module.impl;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.strict.config.ConfigManager;
import com.strict.module.Module;
import com.strict.utils.CryptoUtils;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerLoginConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerLoginNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import io.netty.buffer.Unpooled;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class ModCheckModule implements Module {
    private static final Identifier CHECK_CHANNEL = Identifier.of("strict", "check");
    private static MinecraftServer server;
    private static final Set<String> pendingPlayers = new HashSet<>();
    public static final Set<String> allowedPlayers = new HashSet<>();
    private static final Map<String, PlayerJoinData> playerJoinDataMap = new HashMap<>();
    private static final Map<String, Long> usedNonces = new ConcurrentHashMap<>();
    private static final Map<String, Long> pendingTimestamps = new ConcurrentHashMap<>();
    private static final CheckPayloadCodec PAYLOAD_CODEC = new CheckPayloadCodec();
    private static final long TIMESTAMP_THRESHOLD = 300_000; // 5 minutes
    private static final long NONCE_EXPIRY = 600_000; // 10 minutes
    private static final long PENDING_TIMEOUT = 3_600_000; // 1 hour

    public record CheckPayload(String token, String computerName, List<String> modList) implements CustomPayload {
        public static final CustomPayload.Id<CheckPayload> ID = new CustomPayload.Id<>(CHECK_CHANNEL);

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public static class CheckPayloadCodec {
        public CheckPayload decode(PacketByteBuf buf) {
            String token = buf.readString();
            String computerName = buf.readString();
            List<String> modList = buf.readList(PacketByteBuf::readString);
            return new CheckPayload(token, computerName, modList);
        }

        public PacketByteBuf encode(CheckPayload payload) {
            PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
            buf.writeString(payload.token());
            buf.writeString(payload.computerName());
            buf.writeCollection(payload.modList(), PacketByteBuf::writeString);
            return buf;
        }
    }

    private static void cleanupExpiredNonces() {
        long currentTime = System.currentTimeMillis();
        usedNonces.entrySet().removeIf(entry -> currentTime - entry.getValue() > NONCE_EXPIRY);
        pendingTimestamps.entrySet().removeIf(entry -> currentTime - entry.getValue() > PENDING_TIMEOUT);
        pendingPlayers.removeIf(player -> !pendingTimestamps.containsKey(player));
    }

    @Override
    public void registerServer() {
        PayloadTypeRegistry.playC2S().register(CheckPayload.ID, new PacketCodec<PacketByteBuf, CheckPayload>() {
            @Override
            public CheckPayload decode(PacketByteBuf buf) {
                return PAYLOAD_CODEC.decode(buf);
            }

            @Override
            public void encode(PacketByteBuf buf, CheckPayload payload) {
                buf.writeString(payload.token());
                buf.writeString(payload.computerName());
                buf.writeCollection(payload.modList(), PacketByteBuf::writeString);
            }
        });

        ServerLoginNetworking.registerGlobalReceiver(CHECK_CHANNEL, (server, handler, understood, buf, synchronizer, responseSender) -> {
            cleanupExpiredNonces();
            if (!understood) {
                handler.disconnect(Text.literal("请安装 Strict 模组").formatted(Formatting.RED));
                return;
            }
            if (buf == null || buf.readableBytes() == 0) {
                handler.disconnect(Text.literal("客户端未发送有效数据包").formatted(Formatting.RED));
                return;
            }

            CheckPayload payload;
            try {
                payload = PAYLOAD_CODEC.decode(buf);
            } catch (Exception e) {
                handler.disconnect(Text.literal("数据包解码失败: " + e.getMessage()).formatted(Formatting.RED));
                return;
            }

            String token = payload.token();
            String computerName = payload.computerName();
            List<String> modList = payload.modList();
            if (ConfigManager.getConfig().isLogEnabled()) {
                System.out.println("客户端模组列表: " + modList);
            }

            String tokenData;
            try {
                SecretKey aesKey = generateAesKey(ConfigManager.getConfig().getSecretKey());
                tokenData = CryptoUtils.decryptAES_GCM(token, aesKey);
            } catch (Exception e) {
                handler.disconnect(Text.literal("令牌解密失败: " + e.getMessage()).formatted(Formatting.RED));
                return;
            }

            JsonObject tokenJson;
            try {
                tokenJson = JsonParser.parseString(tokenData).getAsJsonObject();
            } catch (Exception e) {
                handler.disconnect(Text.literal("令牌格式无效: " + e.getMessage()).formatted(Formatting.RED));
                return;
            }

            String tokenPlayerName = tokenJson.get("playerName").getAsString();
            String nonce = tokenJson.get("nonce").getAsString();
            long timestamp = tokenJson.get("timestamp").getAsLong();
            String receivedHash = tokenJson.get("hash").getAsString();

            JsonObject tokenPlainJson = new JsonObject();
            tokenPlainJson.addProperty("playerName", tokenPlayerName);
            tokenPlainJson.addProperty("nonce", nonce);
            tokenPlainJson.addProperty("timestamp", timestamp);
            String tokenPlain = tokenPlainJson.toString();

            if (!CryptoUtils.verifyToken(receivedHash, tokenPlain, ConfigManager.getConfig().getSecretKey())) {
                handler.disconnect(Text.literal("令牌校验失败").formatted(Formatting.RED));
                return;
            }

            if (usedNonces.containsKey(nonce) || Math.abs(System.currentTimeMillis() - timestamp) > TIMESTAMP_THRESHOLD) {
                handler.disconnect(Text.literal("令牌已过期或重复使用").formatted(Formatting.RED));
                return;
            }
            usedNonces.put(nonce, System.currentTimeMillis());

            String clientType = detectClientType(modList);
            PlayerJoinData tempData = new PlayerJoinData(clientType, computerName, modList.stream().collect(Collectors.toSet()), null);
            tempData.setTokenData(tokenData, tokenPlayerName, nonce, timestamp);
            playerJoinDataMap.put(tokenPlayerName, tempData);
        });

        ServerLoginConnectionEvents.QUERY_START.register((handler, server, sender, synchronizer) -> {
            sender.sendPacket(CHECK_CHANNEL, PAYLOAD_CODEC.encode(new CheckPayload("", "", List.of())));
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            String playerName = handler.getPlayer().getGameProfile().getName();
            PlayerJoinData tempData = playerJoinDataMap.get(playerName);

            // Check if validation data exists
            if (tempData == null) {
                handler.disconnect(Text.literal("未收到验证数据").formatted(Formatting.RED));
                return;
            }

            String uuid = handler.getPlayer().getGameProfile().getId().toString();
            tempData.setUuid(uuid);

            // Check blacklist
            if (ConfigManager.getConfig().getBlacklistedPlayers().contains(uuid) ||
                    ConfigManager.getConfig().getBlacklistedPlayers().contains(tempData.computerName)) {
                handler.disconnect(Text.literal("你已被服务器拉黑").formatted(Formatting.RED));
                return;
            }

            // Check for illegal mods
            Set<String> mods = tempData.mods;
            Set<String> illegalMods = getIllegalMods(mods);
            if (!illegalMods.isEmpty()) {
                String illegalModsList = String.join(", ", illegalMods);
                usedNonces.remove(tempData.getNonce());
                handler.disconnect(Text.literal("检测到非法模组: " + illegalModsList + "。请移除后重试。").formatted(Formatting.RED));
                return;
            }

            // Handle private mode
            if (ConfigManager.getConfig().isPrivateMode() &&
                    !handler.getPlayer().hasPermissionLevel(4) &&
                    !allowedPlayers.contains(playerName)) {
                // Add to pending players if not already pending
                if (!pendingPlayers.contains(playerName)) {
                    pendingPlayers.add(playerName);
                    pendingTimestamps.put(playerName, System.currentTimeMillis());
                }

                // Create approval broadcast
                Text acceptText = Text.literal("[同意]")
                        .setStyle(Style.EMPTY.withColor(Formatting.GREEN)
                                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/strict accept " + playerName)));
                Text rejectText = Text.literal("[拒绝]")
                        .setStyle(Style.EMPTY.withColor(Formatting.RED)
                                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/strict reject " + playerName)));
                Text blacklistText = Text.literal("[拉黑]")
                        .setStyle(Style.EMPTY.withColor(Formatting.DARK_GRAY)
                                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/strict blacklist add " + playerName)));
                Text message = Text.literal("玩家 " + playerName + " 请求加入，计算机名称: " + tempData.computerName + ", UUID: " + uuid + " ")
                        .append(acceptText).append(Text.literal(" "))
                        .append(rejectText).append(Text.literal(" "))
                        .append(blacklistText);

                // Broadcast to admins only
                server.getPlayerManager().getPlayerList().stream()
                        .filter(p -> p.hasPermissionLevel(4))
                        .forEach(p -> p.sendMessage(message, false));

                if (ConfigManager.getConfig().isLogEnabled()) {
                    System.out.println("玩家 " + playerName + " 等待审批，UUID: " + uuid + ", 计算机名称: " + tempData.computerName);
                    System.out.println("广播审批消息: " + message.getString());
                }

                // Disconnect player with clear message
                handler.disconnect(Text.literal("你的加入请求已提交，请等待管理员审批。").formatted(Formatting.YELLOW));
                return;
            }

            // Allow player to join
            allowPlayer(server, handler.getPlayer(), tempData.clientType, tempData.computerName);
            allowedPlayers.remove(playerName); // Clean up if previously approved
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            String playerName = handler.getPlayer().getGameProfile().getName();
            PlayerJoinData tempData = playerJoinDataMap.get(playerName);
            if (tempData != null) {
                usedNonces.remove(tempData.getNonce());
            }
            // Do not remove from pendingPlayers here to allow approval after disconnect
            playerJoinDataMap.remove(playerName);
        });

        ServerLifecycleEvents.SERVER_STARTED.register(s -> {
            server = s;
            ConfigManager.loadConfig();
            ServerLifecycleEvents.SERVER_STOPPING.register(srv -> {
                pendingPlayers.clear();
                pendingTimestamps.clear();
                playerJoinDataMap.clear();
                usedNonces.clear();
                ConfigManager.saveConfig();
            });
        });
    }

    @Override
    public void registerClient() {
    }

    private static SecretKey generateAesKey(String secret) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] keyBytes = digest.digest(secret.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(keyBytes, "AES");
        } catch (Exception e) {
            throw new RuntimeException("AES 密钥生成失败", e);
        }
    }

    private static Set<String> getIllegalMods(Set<String> mods) {
        ConfigManager.Config config = ConfigManager.getConfig();
        Set<String> illegalMods = mods.stream()
                .filter(mod -> !isAllowedMod(mod, config))
                .collect(Collectors.toSet());
        if (!illegalMods.isEmpty() && config.isLogEnabled()) {
            System.out.println("检测到非法模组: " + String.join(", ", illegalMods));
        }
        return illegalMods;
    }

    private static boolean isAllowedMod(String modId, ConfigManager.Config config) {
        if (config.getAllowedMods().contains(modId)) {
            return true;
        }
        if (config.isAllowFabricMods() && modId.startsWith("fabric-")) {
            return true;
        }
        if (config.getExcludedMods().contains(modId)) {
            return false;
        }
        return false;
    }

    private static String detectClientType(List<String> modList) {
        if (modList.contains("forge")) return "Forge";
        if (modList.contains("fabricloader")) return "Fabric";
        return "Vanilla";
    }

    public void allowPlayer(MinecraftServer server, ServerPlayerEntity player, String clientType, String computerName) {
        if (server == null || player == null) {
            System.err.println("无法广播玩家加入消息：服务器或玩家对象为 null");
            return;
        }
        Formatting[] colors = {Formatting.AQUA, Formatting.GREEN, Formatting.YELLOW, Formatting.LIGHT_PURPLE};
        Formatting randomColor = colors[new Random().nextInt(colors.length)];
        String message = String.format("玩家 %s 使用 %s 客户端加入，计算机名称: %s", player.getName().getString(), clientType, computerName);
        Text text = Text.literal(message).formatted(randomColor);
        server.getPlayerManager().broadcast(text, false);
        if (ConfigManager.getConfig().isLogEnabled()) {
            System.out.println("广播玩家加入消息: " + message);
        }
    }

    public static boolean isPrivateMode() {
        return ConfigManager.getConfig().isPrivateMode();
    }

    public static void setPrivateMode(boolean mode) {
        ConfigManager.getConfig().setPrivateMode(mode);
        ConfigManager.saveConfig();
    }

    public static boolean isLogEnabled() {
        return ConfigManager.getConfig().isLogEnabled();
    }

    public static void setLogEnabled(boolean enabled) {
        ConfigManager.getConfig().setLogEnabled(enabled);
        ConfigManager.saveConfig();
    }

    public static Set<String> getPendingPlayers() {
        return pendingPlayers;
    }

    public static Map<String, PlayerJoinData> getPlayerJoinDataMap() {
        return playerJoinDataMap;
    }

    public static Set<String> getBlacklistedPlayers() {
        return ConfigManager.getConfig().getBlacklistedPlayers();
    }

    public static MinecraftServer getServer() {
        return server;
    }

    public static class PlayerJoinData {
        public final String clientType;
        public final String computerName;
        final Set<String> mods;
        final List<String> modList;
        private String uuid;
        private String tokenData;
        private String tokenPlayerName;
        private String nonce;
        private long timestamp;

        PlayerJoinData(String clientType, String computerName, Set<String> mods, String uuid) {
            this.clientType = clientType;
            this.computerName = computerName;
            this.mods = mods;
            this.modList = mods != null ? new ArrayList<>(mods) : null;
            this.uuid = uuid;
        }

        void setTokenData(String tokenData, String tokenPlayerName, String nonce, long timestamp) {
            this.tokenData = tokenData;
            this.tokenPlayerName = tokenPlayerName;
            this.nonce = nonce;
            this.timestamp = timestamp;
        }

        void setUuid(String uuid) {
            this.uuid = uuid;
        }

        public String getTokenData() {
            return tokenData;
        }

        String getTokenPlayerName() {
            return tokenPlayerName;
        }

        String getNonce() {
            return nonce;
        }

        long getTimestamp() {
            return timestamp;
        }

        public String getUuid() {
            return uuid;
        }

        public Set<String> getMods() {
            return mods;
        }
    }
}