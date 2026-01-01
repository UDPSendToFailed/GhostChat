package com.udpsendtofailed.ghostchat.mixin;

import com.udpsendtofailed.ghostchat.client.GhostCommands;
import com.udpsendtofailed.ghostchat.client.GhostManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin {

    @Inject(method = "sendChat", at = @At("HEAD"), cancellable = true)
    private void onSendChat(String message, CallbackInfo ci) {
        if (message.startsWith("/")) {
            return;
        }

        if (GhostManager.getInstance().isInterceptEnabled()) {
            Minecraft client = Minecraft.getInstance();
            if (client.player != null) {
                GhostCommands.queueGhostTransmission(message, client.player.getName().getString());
                client.gui.getChat().addRecentChat(message);
                ci.cancel();
            }
        }
    }
}