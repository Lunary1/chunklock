package me.chunklock.listener;

import me.chunklock.managers.ChunkBorderManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class BorderListener implements Listener {
    private final ChunkBorderManager borderManager;

    public BorderListener(ChunkBorderManager borderManager) {
        this.borderManager = borderManager;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        borderManager.handlePlayerInteract(event);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        borderManager.handlePlayerJoin(event);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        borderManager.handlePlayerQuit(event);
    }
}
