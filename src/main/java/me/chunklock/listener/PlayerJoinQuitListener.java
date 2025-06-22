package me.chunklock.listener;

import me.chunklock.listeners.PlayerListener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerJoinQuitListener implements Listener {
    private final PlayerListener delegate;

    public PlayerJoinQuitListener(PlayerListener delegate) {
        this.delegate = delegate;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerJoin(PlayerJoinEvent event) {
        delegate.handlePlayerJoin(event);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerQuit(PlayerQuitEvent event) {
        delegate.handlePlayerQuit(event);
    }
}
