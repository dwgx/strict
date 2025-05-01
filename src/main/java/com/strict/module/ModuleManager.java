package com.strict.module;

import com.strict.command.CommandHandler;
import com.strict.config.ConfigManager;
import com.strict.module.impl.ModCheckModule;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

public class ModuleManager {
    private static final ModCheckModule modCheckModule = new ModCheckModule();

    public static void init() {
        ConfigManager.loadConfig();
        modCheckModule.registerServer();
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            CommandHandler.register(dispatcher);
        });
    }
}