package com.udpsendtofailed.ghostchat.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

public class GhostChatClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        GhostManager.init();
        
        ClientCommandRegistrationCallback.EVENT.register(GhostCommands::register);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;
            GhostManager.getInstance().tick(client);
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            GhostManager.getInstance().reset();
        });
    }
}