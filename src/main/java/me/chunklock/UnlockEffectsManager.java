package me.chunklock;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import net.kyori.adventure.title.Title.Times;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.Duration;

public class UnlockEffectsManager {

    /**
     * Plays unlock effects for a player when they unlock a chunk
     */
    public static void playUnlockEffects(Player player, Chunk unlockedChunk, int totalUnlocked) {
        try {
            // Play sound effects
            playUnlockSounds(player, totalUnlocked);
            
            // Show title/subtitle
            showUnlockTitle(player, unlockedChunk, totalUnlocked);
            
            // Spawn celebration particles
            spawnUnlockParticles(player, unlockedChunk);
            
            // Optional: Send message to nearby players
            broadcastUnlockToNearby(player, unlockedChunk, totalUnlocked);
            
        } catch (Exception e) {
            ChunklockPlugin.getInstance().getLogger().warning("Error playing unlock effects: " + e.getMessage());
        }
    }

    private static void playUnlockSounds(Player player, int totalUnlocked) {
        // Main unlock sound
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.0f);
        
        // Achievement sound for milestones
        if (totalUnlocked % 5 == 0) { // Every 5 chunks
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        }
        
        // Progression sound
        new BukkitRunnable() {
            @Override
            public void run() {
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.5f);
            }
        }.runTaskLater(ChunklockPlugin.getInstance(), 10L); // Delayed sound
    }

    private static void showUnlockTitle(Player player, Chunk chunk, int totalUnlocked) {
        Component title = Component.text()
            .append(Component.text("🔓 ", NamedTextColor.GREEN))
            .append(Component.text("CHUNK UNLOCKED", NamedTextColor.GREEN, TextDecoration.BOLD))
            .build();
        
        Component subtitle = Component.text()
            .append(Component.text("Chunk ", NamedTextColor.GRAY))
            .append(Component.text(chunk.getX() + ", " + chunk.getZ(), NamedTextColor.WHITE))
            .append(Component.text(" • Total: ", NamedTextColor.GRAY))
            .append(Component.text(totalUnlocked, NamedTextColor.YELLOW, TextDecoration.BOLD))
            .build();
        
        Times times = Times.times(
            Duration.ofMillis(500),  // Fade in
            Duration.ofMillis(2000), // Stay
            Duration.ofMillis(500)   // Fade out
        );
        
        Title unlockTitle = Title.title(title, subtitle, times);
        player.showTitle(unlockTitle);
    }

    private static void spawnUnlockParticles(Player player, Chunk chunk) {
        World world = chunk.getWorld();
        Location center = new Location(world, 
            chunk.getX() * 16 + 8.5, 
            world.getHighestBlockYAt(chunk.getX() * 16 + 8, chunk.getZ() * 16 + 8) + 2, 
            chunk.getZ() * 16 + 8.5);

        // Immediate burst effect
        spawnParticleBurst(center);
        
        // Delayed spiral effect
        new BukkitRunnable() {
            int ticks = 0;
            final int maxTicks = 40; // 2 seconds
            
            @Override
            public void run() {
                if (ticks >= maxTicks || !player.isOnline()) {
                    cancel();
                    return;
                }
                
                spawnSpiralParticles(center, ticks);
                ticks++;
            }
        }.runTaskTimer(ChunklockPlugin.getInstance(), 10L, 1L);
    }

    private static void spawnParticleBurst(Location center) {
        // Firework-like burst
        center.getWorld().spawnParticle(Particle.FIREWORK, center, 30, 2, 2, 2, 0.1);
        
        // Golden sparkles
        center.getWorld().spawnParticle(Particle.DUST, center, 20, 1.5, 1.5, 1.5, 0, 
            new Particle.DustOptions(Color.YELLOW, 2.0f));
        
        // Green success particles
        center.getWorld().spawnParticle(Particle.DUST, center, 15, 1, 1, 1, 0,
            new Particle.DustOptions(Color.LIME, 2.0f));
    }

    private static void spawnSpiralParticles(Location center, int tick) {
        double radius = 2.0 + (tick * 0.05); // Expanding spiral
        double height = tick * 0.1;
        
        for (int i = 0; i < 3; i++) { // Multiple spirals
            double angle = (tick * 0.2) + (i * (Math.PI * 2 / 3));
            double x = center.getX() + Math.cos(angle) * radius;
            double z = center.getZ() + Math.sin(angle) * radius;
            double y = center.getY() + height;
            
            Location particleLoc = new Location(center.getWorld(), x, y, z);
            
            // Rotating colored particles
            Color spiralColor = tick % 20 < 10 ? Color.YELLOW : Color.LIME;
            center.getWorld().spawnParticle(Particle.DUST, particleLoc, 1, 0, 0, 0, 0,
                new Particle.DustOptions(spiralColor, 1.5f));
        }
    }

    private static void broadcastUnlockToNearby(Player player, Chunk chunk, int totalUnlocked) {
        String message = "§7[§a+§7] §f" + player.getName() + " §7unlocked chunk §f" + 
                        chunk.getX() + ", " + chunk.getZ() + " §7(§e" + totalUnlocked + " total§7)";
        
        // Send to players within 100 blocks
        for (Player nearby : Bukkit.getOnlinePlayers()) {
            if (nearby != player && 
                nearby.getWorld() == player.getWorld() && 
                nearby.getLocation().distance(player.getLocation()) <= 100) {
                nearby.sendMessage(message);
            }
        }
    }
}