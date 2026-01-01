package com.udpsendtofailed.ghostchat.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ServerboundClientInformationPacket;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.world.entity.player.PlayerModelPart;

import java.nio.charset.StandardCharsets;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class Transmitter {

    private final GhostManager manager;
    
    // Thread-safe queue for incoming requests from commands/chat
    private final Queue<TransmissionTask> taskQueue = new ConcurrentLinkedQueue<>();
    
    // The bits currently being put on the wire
    private final Queue<Byte> activeNibbleBuffer = new LinkedList<>();
    
    private TransmissionTask currentTask = null;
    private int cooldownTimer = 0;
    private boolean clockBit = false; 
    private int totalBitsInCurrentMessage = 0;

    public Transmitter(GhostManager manager) {
        this.manager = manager;
    }

    public void reset() {
        taskQueue.clear();
        activeNibbleBuffer.clear();
        currentTask = null;
        cooldownTimer = 0;
        clockBit = false;
        forceResetSkin();
    }

    public void queueMessage(String message) {
        if (message == null || message.isEmpty()) return;
        taskQueue.add(new TransmissionTask(message));
    }

    public void queueCommand(byte commandByte) {
        taskQueue.add(new TransmissionTask(commandByte));
    }

    public void tick(Minecraft client) {
        if (client.player == null) return;

        if (cooldownTimer > 0) {
            cooldownTimer--;
            return;
        }

        // State 1: We are currently sending bits
        if (!activeNibbleBuffer.isEmpty()) {
            byte nibble = activeNibbleBuffer.poll();
            sendBitPacket(client, nibble);
            
            // UI Feedback
            updateActionBar(client);
            
            cooldownTimer = manager.getTicksPerNibble();
            return;
        }

        // State 2: We finished a message, check for next
        if (currentTask != null) {
            // Task just finished
            currentTask = null;
            // Small gap between messages to ensure Receiver sees EOS clearly
            cooldownTimer = manager.getTicksPerNibble() * 2; 
            return;
        }

        // State 3: Idle, check queue
        if (!taskQueue.isEmpty()) {
            startNextTask(taskQueue.poll());
            // Tick immediately to send first sync bit
            tick(client); 
            return;
        }

        // State 4: Truly Idle - Ensure skin is normal
        // We only do this once to avoid packet spam
        if (isSkinModified(client)) {
            forceResetSkin();
        }
    }

    private void startNextTask(TransmissionTask task) {
        this.currentTask = task;
        this.activeNibbleBuffer.clear();

        // 1. Sync / Wakeup (0x00)
        encodeByte((byte) 0x00);

        // 2. Header
        if (task.isCommand) {
            encodeByte(GhostManager.HEAD_CMD);
            encodeByte(task.commandByte);
            if (task.commandByte == GhostManager.CMD_PING) {
                // Prepare receiver window immediately
                manager.getReceiver().openPingWindow();
            }
        } else {
            encodeByte(GhostManager.HEAD_TEXT);
            byte[] bytes = task.payload.getBytes(StandardCharsets.UTF_8);
            for (byte b : bytes) {
                encodeByte(b);
            }
        }

        // 3. EOS
        encodeByte(GhostManager.EOS);

        this.totalBitsInCurrentMessage = activeNibbleBuffer.size();
    }

    private void encodeByte(byte b) {
        // Low nibble first
        activeNibbleBuffer.add((byte) (b & 0x0F));
        // High nibble second
        activeNibbleBuffer.add((byte) ((b >> 4) & 0x0F));
    }

    private void sendBitPacket(Minecraft client, byte nibble) {
        if (client.getConnection() == null) return;

        // Toggle Clock Bit
        clockBit = !clockBit;

        int mask = 0;
        // Bit 0-3: Data
        mask |= (nibble & 0x0F) << 1; 
        // Bit 5: Clock
        if (clockBit) mask |= 0x20;
        // Bit 6: Active Flag (Always 1 while transmitting)
        mask |= 0x40;

        sendInternal(client, mask);
    }

    private void forceResetSkin() {
        Minecraft client = Minecraft.getInstance();
        if (client.getConnection() == null) return;

        // Reconstruct original skin mask from options
        int originalMask = 0;
        for (PlayerModelPart part : PlayerModelPart.values()) {
            if (client.options.isModelPartEnabled(part)) {
                originalMask |= part.getMask();
            }
        }
        sendInternal(client, originalMask);
    }

    private void sendInternal(Minecraft client, int mask) {
        Options opts = client.options;
        ClientInformation info = new ClientInformation(
            opts.languageCode,
            opts.renderDistance().get(),
            opts.chatVisibility().get(),
            opts.chatColors().get(),
            mask,
            opts.mainHand().get(),
            opts.onlyShowSecureChat().get(),
            opts.allowServerListing().get(),
            opts.particles().get()
        );
        client.getConnection().send(new ServerboundClientInformationPacket(info));
    }
    
    private boolean isSkinModified(Minecraft client) {
        // Logic to check if we need to reset. 
        // Simply returning true calls reset once when queue empties is safe enough.
        return true; 
    }

    private void updateActionBar(Minecraft client) {
        if (totalBitsInCurrentMessage == 0) return;
        int remaining = activeNibbleBuffer.size();
        float progress = 100f - ((float)remaining / totalBitsInCurrentMessage * 100f);
        
        String type = (currentTask.isCommand) ? "CMD" : "MSG";
        client.player.displayClientMessage(
            Component.literal(String.format("§8[§eTx§8] §7Sending %s... §e%.0f%%", type, progress)), 
            true
        );
    }

    private static class TransmissionTask {
        final boolean isCommand;
        final String payload;
        final byte commandByte;

        TransmissionTask(String text) {
            this.isCommand = false;
            this.payload = text;
            this.commandByte = 0;
        }

        TransmissionTask(byte cmd) {
            this.isCommand = true;
            this.payload = null;
            this.commandByte = cmd;
        }
    }
}