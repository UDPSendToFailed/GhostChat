package com.udpsendtofailed.ghostchat.client;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.network.chat.Component;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class GhostCommands {

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandBuildContext registryAccess) {
        dispatcher.register(literal("ghost")
            .then(literal("send")
                .then(argument("message", StringArgumentType.greedyString())
                    .executes(context -> {
                        String msg = StringArgumentType.getString(context, "message");
                        queueGhostTransmission(msg, context.getSource().getPlayer().getName().getString());
                        return 1;
                    })))
            .then(literal("speed")
                .then(argument("ticks", IntegerArgumentType.integer(1))
                    .executes(context -> {
                        int newSpeed = IntegerArgumentType.getInteger(context, "ticks");
                        GhostManager.getInstance().setTicksPerNibble(newSpeed);
                        GhostManager.log("Speed set to §e" + newSpeed + " §7ticks per nibble.");
                        return 1;
                    })))
            .then(literal("debug")
                .executes(context -> {
                    boolean current = GhostManager.getInstance().isDebugEnabled();
                    GhostManager.getInstance().setDebugEnabled(!current);
                    GhostManager.log("Debug logs " + (!current ? "§aenabled" : "§cdisabled") + "§7.");
                    return 1;
                }))
            .then(literal("toggle")
                .executes(context -> {
                    boolean current = GhostManager.getInstance().isInterceptEnabled();
                    GhostManager.getInstance().setInterceptEnabled(!current);
                    GhostManager.log("Chat intercept " + (!current ? "§aenabled" : "§cdisabled") + "§7.");
                    return 1;
                }))
            .then(literal("near")
                .executes(context -> {
                    GhostManager.getInstance().getTransmitter().queueCommand(GhostManager.CMD_PING);
                    GhostManager.log("Pinging for nearby users...");
                    return 1;
                }))
        );
    }

    public static void queueGhostTransmission(String message, String senderName) {
        Component formattedMessage = Component.literal(GhostManager.PREFIX + "§e" + senderName + "§8: §7" + message);
        GhostManager.log(formattedMessage);
        GhostManager.getInstance().getTransmitter().queueMessage(message);
    }
}