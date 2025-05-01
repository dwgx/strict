package com.strict.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.strict.config.ConfigManager;
import com.strict.module.impl.ModCheckModule;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Set;
import java.util.stream.Collectors;
import static net.minecraft.server.command.CommandManager.literal;
import static net.minecraft.server.command.CommandManager.argument;

public class CommandHandler {
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("strict")
                .requires(source -> source.hasPermissionLevel(4))
                .then(literal("mode")
                        .then(literal("public")
                                .executes(context -> {
                                    ConfigManager.getConfig().setPrivateMode(false);
                                    ConfigManager.saveConfig();
                                    context.getSource().sendFeedback(() -> Text.literal("已切换到公开模式").formatted(Formatting.GREEN), true);
                                    return 1;
                                }))
                        .then(literal("private")
                                .executes(context -> {
                                    ConfigManager.getConfig().setPrivateMode(true);
                                    ConfigManager.saveConfig();
                                    context.getSource().sendFeedback(() -> Text.literal("已切换到私密模式").formatted(Formatting.GREEN), true);
                                    return 1;
                                })))
                .then(literal("log")
                        .then(argument("enabled", BoolArgumentType.bool())
                                .executes(context -> {
                                    boolean enabled = BoolArgumentType.getBool(context, "enabled");
                                    ConfigManager.getConfig().setLogEnabled(enabled);
                                    ConfigManager.saveConfig();
                                    context.getSource().sendFeedback(() -> Text.literal("详细日志已" + (enabled ? "开启" : "关闭")).formatted(Formatting.GREEN), true);
                                    return 1;
                                })))
                .then(literal("accept")
                        .then(argument("player", StringArgumentType.string())
                                .executes(context -> {
                                    String playerName = StringArgumentType.getString(context, "player");
                                    if (!ModCheckModule.getPendingPlayers().contains(playerName)) {
                                        context.getSource().sendError(Text.literal("玩家 " + playerName + " 未在等待列表"));
                                        return 0;
                                    }
                                    ModCheckModule.getPendingPlayers().remove(playerName);
                                    ModCheckModule.allowedPlayers.add(playerName);
                                    context.getSource().sendFeedback(() -> Text.literal("已同意玩家 " + playerName + " 加入，下次连接将通过").formatted(Formatting.GREEN), true);
                                    return 1;
                                })))
                .then(literal("reject")
                        .then(argument("player", StringArgumentType.string())
                                .executes(context -> {
                                    String playerName = StringArgumentType.getString(context, "player");
                                    if (!ModCheckModule.getPendingPlayers().contains(playerName)) {
                                        context.getSource().sendError(Text.literal("玩家 " + playerName + " 未在等待列表"));
                                        return 0;
                                    }
                                    ModCheckModule.getPendingPlayers().remove(playerName);
                                    ModCheckModule.getPlayerJoinDataMap().remove(playerName);
                                    context.getSource().sendFeedback(() -> Text.literal("已拒绝玩家 " + playerName + " 加入").formatted(Formatting.RED), true);
                                    return 1;
                                })))
                .then(literal("blacklist")
                        .then(literal("add")
                                .then(argument("player", StringArgumentType.string())
                                        .executes(context -> {
                                            String playerName = StringArgumentType.getString(context, "player");
                                            ModCheckModule.PlayerJoinData joinData = ModCheckModule.getPlayerJoinDataMap().get(playerName);
                                            if (joinData == null) {
                                                context.getSource().sendError(Text.literal("玩家 " + playerName + " 数据丢失"));
                                                return 0;
                                            }
                                            String identifier = joinData.getUuid() != null ? joinData.getUuid() : playerName;
                                            ConfigManager.getConfig().getBlacklistedPlayers().add(identifier);
                                            ConfigManager.getConfig().getBlacklistedPlayers().add(joinData.computerName);
                                            ConfigManager.saveConfig();
                                            ModCheckModule.getPendingPlayers().remove(playerName);
                                            ModCheckModule.getPlayerJoinDataMap().remove(playerName);
                                            context.getSource().sendFeedback(() -> Text.literal("已拉黑玩家 " + playerName + " (标识: " + identifier + ", ComputerName: " + joinData.computerName + ")").formatted(Formatting.DARK_GRAY), true);
                                            return 1;
                                        })))
                        .then(literal("remove")
                                .then(argument("identifier", StringArgumentType.string())
                                        .executes(context -> {
                                            String identifier = StringArgumentType.getString(context, "identifier");
                                            if (ConfigManager.getConfig().getBlacklistedPlayers().remove(identifier)) {
                                                ConfigManager.saveConfig();
                                                context.getSource().sendFeedback(() -> Text.literal("已解除拉黑 " + identifier).formatted(Formatting.GREEN), true);
                                                return 1;
                                            } else {
                                                context.getSource().sendError(Text.literal("标识 " + identifier + " 不在拉黑列表中"));
                                                return 0;
                                            }
                                        })))
                        .then(literal("list")
                                .executes(context -> {
                                    String blacklisted = String.join(", ", ConfigManager.getConfig().getBlacklistedPlayers());
                                    context.getSource().sendFeedback(() -> Text.literal("拉黑列表: " + (blacklisted.isEmpty() ? "空" : blacklisted)).formatted(Formatting.YELLOW), false);
                                    return 1;
                                })))
                .then(literal("info")
                        .then(argument("player", StringArgumentType.string())
                                .executes(context -> {
                                    String playerName = StringArgumentType.getString(context, "player");
                                    ModCheckModule.PlayerJoinData joinData = ModCheckModule.getPlayerJoinDataMap().get(playerName);
                                    if (joinData == null) {
                                        context.getSource().sendError(Text.literal("玩家 " + playerName + " 数据不存在"));
                                        return 0;
                                    }
                                    String identifier = joinData.getUuid() != null ? joinData.getUuid() : playerName;
                                    // 过滤 Fabric 子模块
                                    Set<String> filteredMods = joinData.getMods().stream()
                                            .filter(mod -> !mod.startsWith("fabric-") || mod.equals("fabricloader"))
                                            .collect(Collectors.toSet());
                                    StringBuilder message = new StringBuilder();
                                    message.append("玩家信息: ").append(playerName).append("\n");
                                    message.append("标识: ").append(identifier).append("\n");
                                    message.append("计算机名称: ").append(joinData.computerName).append("\n");
                                    message.append("客户端类型: ").append(joinData.clientType != null ? joinData.clientType : "未知").append("\n");
                                    message.append("模组列表: ").append(filteredMods).append("\n");
                                    message.append("令牌数据: ").append(joinData.getTokenData()).append("\n");
                                    message.append("密钥: ").append(ConfigManager.getConfig().getSecretKey()).append("\n");
                                    context.getSource().sendFeedback(() -> Text.literal(message.toString()).formatted(Formatting.YELLOW), false);
                                    return 1;
                                })))
                .then(literal("mod")
                        .then(literal("allow")
                                .then(argument("modId", StringArgumentType.string())
                                        .executes(context -> {
                                            String modId = StringArgumentType.getString(context, "modId");
                                            ConfigManager.getConfig().getAllowedMods().add(modId);
                                            ConfigManager.saveConfig();
                                            context.getSource().sendFeedback(() -> Text.literal("已添加模组 " + modId + " 到允许列表").formatted(Formatting.GREEN), true);
                                            return 1;
                                        })))
                        .then(literal("exclude")
                                .then(argument("modId", StringArgumentType.string())
                                        .executes(context -> {
                                            String modId = StringArgumentType.getString(context, "modId");
                                            ConfigManager.getConfig().getExcludedMods().add(modId);
                                            ConfigManager.saveConfig();
                                            context.getSource().sendFeedback(() -> Text.literal("已添加模组 " + modId + " 到排除列表").formatted(Formatting.RED), true);
                                            return 1;
                                        })))
                        .then(literal("remove")
                                .then(argument("modId", StringArgumentType.string())
                                        .executes(context -> {
                                            String modId = StringArgumentType.getString(context, "modId");
                                            boolean removed = ConfigManager.getConfig().getAllowedMods().remove(modId) ||
                                                    ConfigManager.getConfig().getExcludedMods().remove(modId);
                                            ConfigManager.saveConfig();
                                            if (removed) {
                                                context.getSource().sendFeedback(() -> Text.literal("已从列表中移除模组 " + modId).formatted(Formatting.GREEN), true);
                                                return 1;
                                            } else {
                                                context.getSource().sendError(Text.literal("模组 " + modId + " 不在任何列表中"));
                                                return 0;
                                            }
                                        })))
                        .then(literal("allowFabric")
                                .then(argument("enabled", BoolArgumentType.bool())
                                        .executes(context -> {
                                            boolean enabled = BoolArgumentType.getBool(context, "enabled");
                                            ConfigManager.getConfig().setAllowFabricMods(enabled);
                                            ConfigManager.saveConfig();
                                            context.getSource().sendFeedback(() -> Text.literal("Fabric 模组自动允许已" + (enabled ? "开启" : "关闭")).formatted(Formatting.GREEN), true);
                                            return 1;
                                        })))
                        .then(literal("list")
                                .executes(context -> {
                                    ConfigManager.Config config = ConfigManager.getConfig();
                                    // 过滤 Fabric 子模块
                                    Set<String> filteredAllowedMods = config.getAllowedMods().stream()
                                            .filter(mod -> !mod.startsWith("fabric-") || mod.equals("fabricloader"))
                                            .collect(Collectors.toSet());
                                    StringBuilder message = new StringBuilder();
                                    message.append("允许的模组: ").append(String.join(", ", filteredAllowedMods)).append("\n");
                                    message.append("排除的模组: ").append(String.join(", ", config.getExcludedMods())).append("\n");
                                    message.append("允许 Fabric 模组: ").append(config.isAllowFabricMods() ? "是" : "否").append("\n");
                                    context.getSource().sendFeedback(() -> Text.literal(message.toString()).formatted(Formatting.YELLOW), false);
                                    return 1;
                                })))
                .then(literal("reload")
                        .executes(context -> {
                            ConfigManager.loadConfig();
                            context.getSource().sendFeedback(() -> Text.literal("已重新加载配置文件").formatted(Formatting.GREEN), true);
                            return 1;
                        })));
    }
}