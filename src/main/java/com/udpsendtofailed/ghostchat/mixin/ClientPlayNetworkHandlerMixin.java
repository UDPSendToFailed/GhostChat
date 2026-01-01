package com.udpsendtofailed.ghostchat.mixin;

import com.udpsendtofailed.ghostchat.client.GhostManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(ClientPacketListener.class)
public class ClientPlayNetworkHandlerMixin {

    @Inject(method = "handleSetEntityData", at = @At("HEAD"))
    public void onSkinUpdate(ClientboundSetEntityDataPacket packet, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null) return;

        List<SynchedEntityData.DataValue<?>> trackedValues = packet.packedItems();
        if (trackedValues == null) return;

        var entity = client.level.getEntity(packet.id());
        if (entity == null) return;
        
        // Metadata ID 16 is usually Skin Parts/Displayed Skin Parts for Players
        for (SynchedEntityData.DataValue<?> value : trackedValues) {
            if (value.id() == 16) {
                // Determine if value is Byte or Integer depending on MC version mapping, safe cast
                int mask = 0;
                if (value.value() instanceof Byte b) mask = b.intValue();
                else if (value.value() instanceof Integer i) mask = i;
                
                GhostManager.getInstance().getReceiver().onMetadataUpdate(entity.getUUID(), mask);
            }
        }
    }
}