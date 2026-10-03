package me.chunklock.border;

import io.papermc.paper.event.packet.PlayerChunkLoadEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side borders: the glass only exists on the owning player's client.
 *
 * Placing real blocks costs block updates, lighting and chunk saves, and a
 * full-height wall is about 384 blocks per column. Sending fake blocks costs
 * one packet per chunk section and leaves the world untouched.
 *
 * Only the difference between what the client currently shows and the wanted
 * border set is sent, so an update that changes nothing sends nothing. Fake
 * blocks are lost when the client reloads a chunk or digs one, so those cases
 * resend them.
 */
public final class ClientBorderView implements Listener {

    private final Plugin plugin;
    private final BlockData glass;
    /** Locations currently shown as glass on each player's client. */
    private final Map<UUID, Set<Location>> shown = new ConcurrentHashMap<>();
    /** Same locations, indexed by chunk, to resend on chunk load. */
    private final Map<UUID, Map<Long, List<Location>>> shownByChunk = new ConcurrentHashMap<>();
    /** Border sides the shown set was built from, see ChunkBorderManager. */
    private final Map<UUID, Set<Long>> signatures = new ConcurrentHashMap<>();

    public ClientBorderView(Plugin plugin, Material borderMaterial) {
        this.plugin = plugin;
        this.glass = borderMaterial.createBlockData();
    }

    /** Shared placeholder stored as "original data": nothing is replaced in the world. */
    public BlockData placeholder() {
        return glass;
    }

    /**
     * Brings the player's client in line with the border set: shows new glass,
     * restores the real blocks where borders went away.
     */
    public void sync(Player player, Map<Location, BlockData> wanted) {
        UUID id = player.getUniqueId();
        Set<Location> target = wanted == null ? Set.of() : wanted.keySet();
        Set<Location> before = shown.getOrDefault(id, Set.of());

        Map<Location, BlockData> changes = new HashMap<>();
        for (Location loc : before) {
            if (!target.contains(loc)) {
                changes.put(loc, loc.getBlock().getBlockData());
            }
        }
        for (Location loc : target) {
            if (!before.contains(loc)) {
                changes.put(loc, glass);
            }
        }

        if (target.isEmpty()) {
            forget(id);
        } else {
            Set<Location> now = new HashSet<>(target);
            Map<Long, List<Location>> index = new HashMap<>();
            for (Location loc : now) {
                index.computeIfAbsent(chunkKey(loc), k -> new ArrayList<>()).add(loc);
            }
            shown.put(id, now);
            shownByChunk.put(id, index);
        }

        if (!changes.isEmpty() && player.isOnline()) {
            player.sendMultiBlockChange(changes);
        }
    }

    public void forget(UUID id) {
        shown.remove(id);
        shownByChunk.remove(id);
        signatures.remove(id);
    }

    public boolean hasSignature(UUID id, Set<Long> signature) {
        return signature != null && signature.equals(signatures.get(id));
    }

    public void setSignature(UUID id, Set<Long> signature) {
        if (signature == null || !shown.containsKey(id)) {
            signatures.remove(id);
        } else {
            signatures.put(id, signature);
        }
    }

    public boolean isShown(UUID id, Location location) {
        Set<Location> set = shown.get(id);
        return set != null && set.contains(location);
    }

    /** The client drops fake blocks when it (re)loads a chunk. */
    @EventHandler
    public void onChunkSent(PlayerChunkLoadEvent event) {
        Map<Long, List<Location>> index = shownByChunk.get(event.getPlayer().getUniqueId());
        if (index == null) {
            return;
        }
        List<Location> inChunk = index.get(chunkKey(event.getChunk().getX(), event.getChunk().getZ()));
        if (inChunk == null || inChunk.isEmpty()) {
            return;
        }
        Map<Location, BlockData> changes = new HashMap<>();
        for (Location loc : inChunk) {
            changes.put(loc, glass);
        }
        event.getPlayer().sendMultiBlockChange(changes);
    }

    /**
     * Digging fake glass removes it on the client, and a cancelled break makes
     * the server resend the real block. Put the glass back a few times, since
     * the dig can finish after the first resend.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onDig(PlayerInteractEvent event) {
        if (event.getAction() != Action.LEFT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        Player player = event.getPlayer();
        Block block = event.getClickedBlock();
        Location loc = block.getLocation();
        if (!isShown(player.getUniqueId(), loc)) {
            return;
        }
        for (long delay : new long[] {1L, 5L, 20L}) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline() && isShown(player.getUniqueId(), loc)) {
                    player.sendBlockChange(loc, glass);
                }
            }, delay);
        }
    }

    /**
     * An explosion near a border sends block updates that overwrite the fake
     * glass on the client. Put it back once the explosion has been applied.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityExplode(EntityExplodeEvent event) {
        resendNear(event.getLocation(), EXPLOSION_RESEND_RADIUS);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBlockExplode(BlockExplodeEvent event) {
        resendNear(event.getBlock().getLocation(), EXPLOSION_RESEND_RADIUS);
    }

    private static final int EXPLOSION_RESEND_RADIUS = 8;

    private void resendNear(Location center, int radius) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            int r2 = radius * radius;
            for (Player player : center.getWorld().getPlayers()) {
                Set<Location> set = shown.get(player.getUniqueId());
                if (set == null) {
                    continue;
                }
                Map<Location, BlockData> changes = new HashMap<>();
                for (Location loc : set) {
                    int dx = loc.getBlockX() - center.getBlockX();
                    int dy = loc.getBlockY() - center.getBlockY();
                    int dz = loc.getBlockZ() - center.getBlockZ();
                    if (dx * dx + dy * dy + dz * dz <= r2) {
                        changes.put(loc, glass);
                    }
                }
                if (!changes.isEmpty()) {
                    player.sendMultiBlockChange(changes);
                }
            }
        }, 2L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        forget(event.getPlayer().getUniqueId());
    }

    private static long chunkKey(Location loc) {
        return chunkKey(loc.getBlockX() >> 4, loc.getBlockZ() >> 4);
    }

    private static long chunkKey(int x, int z) {
        return ((long) x << 32) | (z & 0xffffffffL);
    }
}
