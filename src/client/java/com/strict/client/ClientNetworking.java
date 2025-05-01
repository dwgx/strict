package com.strict.client;

import com.google.gson.JsonObject;
import com.strict.utils.CryptoUtils;
import net.fabricmc.fabric.api.client.networking.v1.ClientLoginNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class ClientNetworking {
    private static final Logger LOGGER = LoggerFactory.getLogger("strict-client");
    private static final String SECRET = "dwgx1337";
    private static final Identifier CHECK_CHANNEL = Identifier.of("strict", "check");

    public static void register() {
        ClientLoginNetworking.registerGlobalReceiver(CHECK_CHANNEL, (client, handler, buf, listenerAdder) -> {
            PacketByteBuf responseBuf = PacketByteBufs.create();
            String playerName = client.getSession().getUsername();
            String computerName = System.getProperty("user.name", "unknown");

            List<String> modList = FabricLoader.getInstance().getAllMods().stream()
                    .map(mod -> mod.getMetadata().getId())
                    .filter(id -> !id.equals("org.yaml.snakeyaml"))//排除org.yaml.snakeyaml
                    .collect(Collectors.toList());
            JsonObject tokenJson = new JsonObject();
            tokenJson.addProperty("playerName", playerName);
            String nonce = UUID.randomUUID().toString();
            tokenJson.addProperty("nonce", nonce);
            long timestamp = System.currentTimeMillis();
            tokenJson.addProperty("timestamp", timestamp);

            JsonObject tokenPlainJson = new JsonObject();
            tokenPlainJson.addProperty("playerName", playerName);
            tokenPlainJson.addProperty("nonce", nonce);
            tokenPlainJson.addProperty("timestamp", timestamp);
            String tokenPlain = tokenPlainJson.toString();

            String hash = CryptoUtils.generateToken(tokenPlain, SECRET);
            tokenJson.addProperty("hash", hash);

            String tokenData = tokenJson.toString();
            try {
                SecretKey aesKey = generateAesKey(SECRET);
                String encryptedToken = CryptoUtils.encryptAES_GCM(tokenData, aesKey);
                responseBuf.writeString(encryptedToken);
                responseBuf.writeString(computerName);
                responseBuf.writeCollection(modList, PacketByteBuf::writeString);
                LOGGER.info("Sending encrypted token: {}, computerName: {}, modList: {}", encryptedToken, computerName, modList);
            } catch (Exception e) {
                LOGGER.error("Failed to encrypt token: {}", e.getMessage());
                return null;
            }

            return CompletableFuture.completedFuture(responseBuf);
        });
    }

    private static SecretKey generateAesKey(String secret) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] keyBytes = digest.digest(secret.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(keyBytes, "AES");
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate AES key", e);
        }
    }
}