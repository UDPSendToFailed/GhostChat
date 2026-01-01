package com.udpsendtofailed.ghostchat.client;

import com.udpsendtofailed.ghostchat.config.GhostConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public class GhostManager {

    private static GhostManager instance;
    private final Transmitter transmitter;
    private final Receiver receiver;
    
    public static final byte HEAD_TEXT = (byte) 0x10; 
    public static final byte HEAD_CMD  = (byte) 0x11;
    public static final byte CMD_PING  = (byte) 0x01;
    public static final byte CMD_PONG  = (byte) 0x02;
    public static final byte EOS       = (byte) 0x03;

    public static final String PREFIX = "§8[§7Ghost§8] ";

    private GhostManager() {
        this.transmitter = new Transmitter(this);
        this.receiver = new Receiver(this);
        // Ensure config is loaded on init
        GhostConfig.getInstance(); 
    }

    public static void init() {
        if (instance == null) instance = new GhostManager();
    }

    public static GhostManager getInstance() { return instance; }
    public Transmitter getTransmitter() { return transmitter; }
    public Receiver getReceiver() { return receiver; }

    public void tick(Minecraft client) {
        transmitter.tick(client);
        receiver.tick(client);
    }
    
    public void reset() {
        transmitter.reset();
        receiver.reset();
    }

    // --- CONFIG DELEGATES ---

    public boolean isDebugEnabled() { 
        return GhostConfig.getInstance().isDebugEnabled; 
    }
    
    public void setDebugEnabled(boolean v) { 
        GhostConfig.getInstance().isDebugEnabled = v;
        GhostConfig.save();
    }

    public boolean isInterceptEnabled() { 
        return GhostConfig.getInstance().isInterceptEnabled; 
    }
    
    public void setInterceptEnabled(boolean v) { 
        GhostConfig.getInstance().isInterceptEnabled = v;
        GhostConfig.save();
    }

    public int getTicksPerNibble() { 
        return GhostConfig.getInstance().ticksPerNibble; 
    }
    
    public void setTicksPerNibble(int v) { 
        GhostConfig.getInstance().ticksPerNibble = v;
        GhostConfig.save();
    }

    // --- LOGGING ---

    public static void log(Component c) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        
        mc.execute(() -> {
            if (mc.player != null) {
                mc.player.displayClientMessage(c, false);
            }
        });
    }

    public static void log(String s) {
        log(Component.literal(PREFIX + "§7" + s));
    }

    public static void logDebug(String s) {
        if (instance != null && instance.isDebugEnabled()) {
            log(Component.literal("§8[§cDBG§8] §7" + s));
        }
    }
}