package com.strict.config;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.util.*;

public class ConfigManager {
    private static final File CONFIG_FILE = new File("config/strict/config.yml");
    private static final Yaml YAML;
    private static Config config;

    static {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        YAML = new Yaml(options);
    }

    public static class Config {
        boolean privateMode = false;
        boolean logEnabled = true; // 临时启用日志，便于调试
        String secretKey = "dwgx1337";
        String secretKeyHash = "";
        Set<String> allowedMods = new HashSet<>(Arrays.asList("minecraft", "strict", "fabricloader", "java", "mixinextras", "org_yaml_snakeyaml"));
        Set<String> excludedMods = new HashSet<>();
        boolean allowFabricMods = true;
        Set<String> blacklistedPlayers = new HashSet<>();

        public boolean isPrivateMode() {
            return privateMode;
        }

        public void setPrivateMode(boolean privateMode) {
            this.privateMode = privateMode;
        }

        public boolean isLogEnabled() {
            return logEnabled;
        }

        public void setLogEnabled(boolean logEnabled) {
            this.logEnabled = logEnabled;
        }

        public String getSecretKey() {
            return secretKey;
        }

        public void setSecretKey(String secretKey) {
            this.secretKey = secretKey;
        }

        public String getSecretKeyHash() {
            return secretKeyHash;
        }

        public void setSecretKeyHash(String secretKeyHash) {
            this.secretKeyHash = secretKeyHash;
        }

        public Set<String> getAllowedMods() {
            return allowedMods;
        }

        public Set<String> getExcludedMods() {
            return excludedMods;
        }

        public boolean isAllowFabricMods() {
            return allowFabricMods;
        }

        public void setAllowFabricMods(boolean allowFabricMods) {
            this.allowFabricMods = allowFabricMods;
        }

        public Set<String> getBlacklistedPlayers() {
            return blacklistedPlayers;
        }
    }

    public static synchronized void loadConfig() {
        try {
            if (!CONFIG_FILE.exists()) {
                CONFIG_FILE.getParentFile().mkdirs();
                config = new Config();
                saveConfig();
                System.out.println("生成默认配置文件: " + CONFIG_FILE.getAbsolutePath());
            } else {
                try (FileReader reader = new FileReader(CONFIG_FILE)) {
                    Map<String, Object> yamlData = YAML.load(reader);
                    if (yamlData == null) {
                        System.err.println("配置文件为空，加载默认配置");
                        config = new Config();
                        saveConfig();
                        return;
                    }
                    Config newConfig = new Config();
                    newConfig.privateMode = (Boolean) yamlData.getOrDefault("privateMode", false);
                    newConfig.logEnabled = (Boolean) yamlData.getOrDefault("logEnabled", true); // 临时启用日志
                    newConfig.secretKey = (String) yamlData.getOrDefault("secretKey", "dwgx1337");
                    newConfig.secretKeyHash = (String) yamlData.getOrDefault("secretKeyHash", "");
                    newConfig.allowedMods = new HashSet<>((List<String>) yamlData.getOrDefault("allowedMods", Arrays.asList("minecraft", "strict", "fabricloader", "java", "mixinextras", "org_yaml_snakeyaml")));
                    newConfig.excludedMods = new HashSet<>((List<String>) yamlData.getOrDefault("excludedMods", Collections.emptyList()));
                    newConfig.allowFabricMods = (Boolean) yamlData.getOrDefault("allowFabricMods", true);
                    newConfig.blacklistedPlayers = new HashSet<>((List<String>) yamlData.getOrDefault("blacklistedPlayers", Collections.emptyList()));
                    config = newConfig;
                    System.out.println("成功加载配置文件: allowedMods=" + config.allowedMods);
                }
            }
        } catch (Exception e) {
            System.err.println("加载配置文件失败: " + e.getMessage());
            e.printStackTrace();
            if (config == null) {
                config = new Config();
            }
        }
    }

    public static synchronized void saveConfig() {
        try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
            Map<String, Object> yamlData = new HashMap<>();
            yamlData.put("privateMode", config.privateMode);
            yamlData.put("logEnabled", config.logEnabled);
            yamlData.put("secretKey", config.secretKey);
            yamlData.put("secretKeyHash", config.secretKeyHash);
            yamlData.put("allowedMods", new ArrayList<>(config.allowedMods));
            yamlData.put("excludedMods", new ArrayList<>(config.excludedMods));
            yamlData.put("allowFabricMods", config.allowFabricMods);
            yamlData.put("blacklistedPlayers", new ArrayList<>(config.blacklistedPlayers));
            YAML.dump(yamlData, writer);
            System.out.println("成功保存配置文件: allowedMods=" + config.allowedMods);
        } catch (Exception e) {
            System.err.println("保存配置文件失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static synchronized Config getConfig() {
        if (config == null) {
            loadConfig();
        }
        return config;
    }
}