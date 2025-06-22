package me.chunklock.ui;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;

public class UnlockGuiListener implements Listener {
    private final UnlockGui gui;

    public UnlockGuiListener(UnlockGui gui) {
        this.gui = gui;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        gui.handleInventoryClick(event);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        gui.handleInventoryClose(event);
    }
}
