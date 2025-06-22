package me.chunklock.border;

import me.chunklock.ChunklockPlugin;
import me.chunklock.managers.ChunkBorderManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;

public class BorderRefreshService {
    private final ChunkBorderManager borderManager;
    private static final long BORDER_UPDATE_COOLDOWN_MS = 5000L;

    public BorderRefreshService(ChunkBorderManager borderManager) {
        this.borderManager = borderManager;
    }

    public void refreshBordersOnMove(Player player, Map<UUID, Long> lastUpdateMap) {
        UUID playerId = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long last = lastUpdateMap.get(playerId);
        if (last != null && (now - last) < BORDER_UPDATE_COOLDOWN_MS) {
            return;
        }
        if (borderManager.isAutoUpdateOnMovementEnabled()) {
            Bukkit.getScheduler().runTaskAsynchronously(ChunklockPlugin.getInstance(), () ->
                Bukkit.getScheduler().runTask(ChunklockPlugin.getInstance(), () -> {
                    if (player.isOnline()) {
                        borderManager.scheduleBorderUpdate(player);
                        lastUpdateMap.put(playerId, System.currentTimeMillis());
                    }
                })
            );
        }
    }
}
