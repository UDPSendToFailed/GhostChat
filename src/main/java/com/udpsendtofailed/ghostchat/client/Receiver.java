package com.udpsendtofailed.ghostchat.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Receiver {

    private final GhostManager manager;
    private final Map<UUID, ChannelState> channels = new ConcurrentHashMap<>();
    
    private int pingWindowTimer = 0;
    private final Set<UUID> pingRespondents = new HashSet<>();
    
    private static final int WATCHDOG_TIMEOUT = 100; 

    public Receiver(GhostManager manager) {
        this.manager = manager;
    }

    public void reset() {
        channels.clear();
        pingRespondents.clear();
        pingWindowTimer = 0;
    }

    public void openPingWindow() {
        this.pingWindowTimer = 100;
        this.pingRespondents.clear();
        GhostManager.logDebug("Listening for PONGs...");
    }

    public void tick(Minecraft client) {
        if (pingWindowTimer > 0) {
            pingWindowTimer--;
            if (pingWindowTimer == 0) {
                GhostManager.log(Component.literal(GhostManager.PREFIX + "§7Scan complete. Found §e" + pingRespondents.size() + " §7ghosts."));
            }
        }

        Iterator<Map.Entry<UUID, ChannelState>> it = channels.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, ChannelState> entry = it.next();
            ChannelState state = entry.getValue();
            state.watchdog--;
            
            if (state.watchdog <= 0) {
                if (state.buffer.size() > 0 && state.mode == Mode.TEXT) {
                    flushMessage(entry.getKey(), state, true);
                }
                it.remove();
            }
        }
    }

    public void onMetadataUpdate(UUID senderUUID, int mask) {
        Minecraft mc = Minecraft.getInstance();
        
        if (mc.player != null && senderUUID.equals(mc.player.getUUID())) {
            return;
        }

        boolean isActive = (mask & 0x40) != 0;
        if (!isActive) {
            ChannelState existing = channels.get(senderUUID);
            if (existing != null) {
                if (existing.buffer.size() > 0) flushMessage(senderUUID, existing, true);
                channels.remove(senderUUID);
            }
            return;
        }

        ChannelState state = channels.computeIfAbsent(senderUUID, k -> new ChannelState());
        state.watchdog = WATCHDOG_TIMEOUT;

        boolean incomingClock = (mask & 0x20) != 0;
        if (incomingClock == state.lastClock) {
            return; 
        }
        state.lastClock = incomingClock;

        int nibble = (mask & 0x1E) >> 1;

        if (!state.hasHighNibble) {
            state.lowNibbleCache = nibble;
            state.hasHighNibble = true;
        } else {
            int byteVal = (nibble << 4) | state.lowNibbleCache;
            state.hasHighNibble = false;
            processByte(senderUUID, state, (byte)byteVal);
        }
    }

    private void processByte(UUID uuid, ChannelState state, byte b) {
        switch (state.mode) {
            case SYNC:
                if (b == GhostManager.HEAD_TEXT) {
                    state.mode = Mode.TEXT;
                } else if (b == GhostManager.HEAD_CMD) {
                    state.mode = Mode.CMD;
                }
                break;

            case TEXT:
                if (b == GhostManager.EOS) {
                    flushMessage(uuid, state, false);
                    state.reset();
                } else {
                    state.buffer.write(b);
                }
                break;

            case CMD:
                if (b == GhostManager.EOS) {
                    state.reset();
                } else {
                    handleCommand(uuid, b);
                }
                break;
        }
    }

    private void handleCommand(UUID uuid, byte cmd) {
        if (cmd == GhostManager.CMD_PING) {
            manager.getTransmitter().queueCommand(GhostManager.CMD_PONG);
        } else if (cmd == GhostManager.CMD_PONG) {
            if (pingWindowTimer > 0 && !pingRespondents.contains(uuid)) {
                pingRespondents.add(uuid);
                printDetection(uuid);
            }
        }
    }

    private void flushMessage(UUID uuid, ChannelState state, boolean isPartial) {
        try {
            String content = state.buffer.toString(StandardCharsets.UTF_8);
            if (content.isEmpty()) return;

            Minecraft.getInstance().execute(() -> {
                Minecraft mc = Minecraft.getInstance();
                if (mc.level == null) return;
                
                Player p = mc.level.getPlayerByUUID(uuid);
                String name = (p != null) ? p.getName().getString() : "Unknown";
                
                String suffix = isPartial ? " §c[SIGNAL LOST]" : "";
                GhostManager.log(Component.literal(GhostManager.PREFIX + "§e" + name + "§8: §7" + content + suffix));
            });
            
        } catch (Exception e) {
            e.printStackTrace();
        }
        state.buffer.reset();
    }

    private void printDetection(UUID uuid) {
        Minecraft.getInstance().execute(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null || mc.player == null) return;
            
            Player p = mc.level.getPlayerByUUID(uuid);
            if (p == null) return;

            Vec3 pos = p.position();
            double dist = pos.distanceTo(mc.player.position());
            
            // Format: [Radar] Detected Name at X, Y, Z (Dist)
            String msg = String.format("§8[§aRadar§8] §7Detected §e%s §7at §b%d, %d, %d §7(§e%.1fm§7)", 
                    p.getName().getString(), 
                    (int)pos.x, (int)pos.y, (int)pos.z, 
                    dist);
            
            GhostManager.log(Component.literal(msg));
        });
    }
    
    private enum Mode { SYNC, TEXT, CMD }

    private static class ChannelState {
        Mode mode = Mode.SYNC;
        boolean lastClock = false;
        boolean hasHighNibble = false;
        int lowNibbleCache = 0;
        int watchdog = WATCHDOG_TIMEOUT;
        final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        void reset() {
            mode = Mode.SYNC;
            hasHighNibble = false;
            buffer.reset();
        }
    }
}