package com.strict.client;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MainClient implements ClientModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("strict-client");

    @Override
    public void onInitializeClient() {
        LOGGER.info("严格模组 正在客户端t运作");
        ClientNetworking.register();
    }
}