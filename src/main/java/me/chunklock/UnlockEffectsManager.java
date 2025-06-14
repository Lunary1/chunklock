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
     * Plays unlock effects for a player when they unlock a chunk normally
     */
    public static void playUnlockEffects(Player player, Chunk unlockedChunk, int totalUnlocked) {
        try {
            // Play sound effects
            playUnlockSounds(player, totalUnlocked);
            
            // Show title/subtitle
            showUnlockTitle(player, unlockedChunk, totalUnlocked, false);
            
            // Spawn celebration particles
            spawnUnlockParticles(player, unlockedChunk);
            
            // Optional: Send message to nearby players
            broadcastUnlockToNearby(player, unlockedChunk, totalUnlocked, false);
            
        } catch (Exception e) {
            ChunklockPlugin.getInstance().getLogger().warning("Error playing unlock effects: " + e.getMessage());
        }
    }

    /**
     * Plays special overclaim effects for when a player takes a chunk from another player
     */
    public static void playOverclaimEffects(Player player, Chunk overclaimedChunk, int totalUnlocked) {
        try {
            // Play dramatic overclaim sounds
            playOverclaimSounds(player);
            
            // Show overclaim title/subtitle
            showUnlockTitle(player, overclaimedChunk, totalUnlocked, true);
            
            // Spawn dramatic overclaim particles
            spawnOverclaimParticles(player, overclaimedChunk);
            
            // Broadcast overclaim to nearby players
            broadcastUnlockToNearby(player, overclaimedChunk, totalUnlocked, true);
            
        } catch (Exception e) {
            ChunklockPlugin.getInstance().getLogger().warning("Error playing overclaim effects: " + e.getMessage());
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

    private static void playOverclaimSounds(Player player) {
        // Dramatic overclaim sounds
        player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.7f, 1.2f);
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 1.0f, 0.8f);
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 0.7f);
        
        // Delayed threatening sound
        new BukkitRunnable() {
            @Override
            public void run() {
                player.playSound(player.getLocation(), Sound.ENTITY_WITHER_AMBIENT, 0.5f, 1.5f);
            }
        }.runTaskLater(ChunklockPlugin.getInstance(), 15L);
        
        // Victory sound
        new BukkitRunnable() {
            @Override
            public void run() {
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.0f);
            }
        }.runTaskLater(ChunklockPlugin.getInstance(), 30L);
    }

    private static void showUnlockTitle(Player player, Chunk chunk, int totalUnlocked, boolean isOverclaim) {
        Component title;
        Component subtitle;
        
        if (isOverclaim) {
            title = Component.text()
                .append(Component.text("⚔ ", NamedTextColor.RED))
                .append(Component.text("CHUNK OVERCLAIMED", NamedTextColor.RED, TextDecoration.BOLD))
                .append(Component.text(" ⚔", NamedTextColor.RED))
                .build();
            
            subtitle = Component.text()
                .append(Component.text("Taken chunk ", NamedTextColor.GRAY))
                .append(Component.text(chunk.getX() + ", " + chunk.getZ(), NamedTextColor.WHITE))
                .append(Component.text(" by force!", NamedTextColor.RED))
                .build();
        } else {
            title = Component.text()
                .append(Component.text("🔓 ", NamedTextColor.GREEN))
                .append(Component.text("CHUNK UNLOCKED", NamedTextColor.GREEN, TextDecoration.BOLD))
                .build();
            
            subtitle = Component.text()
                .append(Component.text("Chunk ", NamedTextColor.GRAY))
                .append(Component.text(chunk.getX() + ", " + chunk.getZ(), NamedTextColor.WHITE))
                .append(Component.text(" • Total: ", NamedTextColor.GRAY))
                .append(Component.text(totalUnlocked, NamedTextColor.YELLOW, TextDecoration.BOLD))
                .build();
        }
        
        Times times = Times.times(
            Duration.ofMillis(500),  // Fade in
            Duration.ofMillis(isOverclaim ? 3000 : 2000), // Stay longer for overclaim
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
        spawnParticleBurst(center, false);
        
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
                
                spawnSpiralParticles(center, ticks, false);
                ticks++;
            }
        }.runTaskTimer(ChunklockPlugin.getInstance(), 10L, 1L);
    }

    private static void spawnOverclaimParticles(Player player, Chunk chunk) {
        World world = chunk.getWorld();
        Location center = new Location(world, 
            chunk.getX() * 16 + 8.5, 
            world.getHighestBlockYAt(chunk.getX() * 16 + 8, chunk.getZ() * 16 + 8) + 2, 
            chunk.getZ() * 16 + 8.5);

        // Dramatic overclaim burst
        spawnParticleBurst(center, true);
        
        // Extended dramatic effects
        new BukkitRunnable() {
            int ticks = 0;
            final int maxTicks = 60; // 3 seconds - longer for overclaim
            
            @Override
            public void run() {
                if (ticks >= maxTicks || !player.isOnline()) {
                    cancel();
                    return;
                }
                
                // Alternate between threatening and victory effects
                if (ticks < 40) {
                    spawnSpiralParticles(center, ticks, true); // Dramatic spirals
                    
                    if (ticks % 10 == 0) {
                        spawnOverclaimRings(center, ticks); // Expanding rings
                    }
                } else {
                    spawnSpiralParticles(center, ticks, false); // Victory spirals
                }
                
                ticks++;
            }
        }.runTaskTimer(ChunklockPlugin.getInstance(), 10L, 1L);
    }

    private static void spawnParticleBurst(Location center, boolean isOverclaim) {
        if (isOverclaim) {
            // Dramatic overclaim burst - red and black
            center.getWorld().spawnParticle(Particle.EXPLOSION, center, 5, 1, 1, 1, 0);
            center.getWorld().spawnParticle(Particle.LARGE_SMOKE, center, 20, 2, 2, 2, 0.05);
            
            // Red dust burst
            center.getWorld().spawnParticle(Particle.DUST, center, 40, 2, 2, 2, 0, 
                new Particle.DustOptions(Color.RED, 2.5f));
            
            // Dark red dust
            center.getWorld().spawnParticle(Particle.DUST, center, 30, 1.5, 1.5, 1.5, 0,
                new Particle.DustOptions(Color.MAROON, 2.0f));
            
            // Lava particles for threatening effect
            center.getWorld().spawnParticle(Particle.LAVA, center, 15, 1, 1, 1, 0);
            
        } else {
            // Regular unlock burst - firework-like
            center.getWorld().spawnParticle(Particle.FIREWORK, center, 30, 2, 2, 2, 0.1);
            
            // Golden sparkles
            center.getWorld().spawnParticle(Particle.DUST, center, 20, 1.5, 1.5, 1.5, 0, 
                new Particle.DustOptions(Color.YELLOW, 2.0f));
            
            // Green success particles
            center.getWorld().spawnParticle(Particle.DUST, center, 15, 1, 1, 1, 0,
                new Particle.DustOptions(Color.LIME, 2.0f));
        }
    }

    private static void spawnSpiralParticles(Location center, int tick, boolean isOverclaim) {
        double radius = 2.0 + (tick * 0.05); // Expanding spiral
        double height = tick * 0.1;
        
        int spiralCount = isOverclaim ? 4 : 3; // More spirals for overclaim
        
        for (int i = 0; i < spiralCount; i++) {
            double angle = (tick * 0.2) + (i * (Math.PI * 2 / spiralCount));
            double x = center.getX() + Math.cos(angle) * radius;
            double z = center.getZ() + Math.sin(angle) * radius;
            double y = center.getY() + height;
            
            Location particleLoc = new Location(center.getWorld(), x, y, z);
            
            if (isOverclaim) {
                // Menacing red and black spirals
                Color spiralColor = tick % 15 < 8 ? Color.RED : Color.MAROON;
                center.getWorld().spawnParticle(Particle.DUST, particleLoc, 1, 0, 0, 0, 0,
                    new Particle.DustOptions(spiralColor, 1.8f));
                
                // Add some flame particles
                if (tick % 3 == 0) {
                    center.getWorld().spawnParticle(Particle.FLAME, particleLoc, 1, 0.1, 0.1, 0.1, 0.01);
                }
            } else {
                // Regular colored spirals
                Color spiralColor = tick % 20 < 10 ? Color.YELLOW : Color.LIME;
                center.getWorld().spawnParticle(Particle.DUST, particleLoc, 1, 0, 0, 0, 0,
                    new Particle.DustOptions(spiralColor, 1.5f));
            }
        }
    }

    private static void spawnOverclaimRings(Location center, int tick) {
        double radius = 1.0 + (tick * 0.15); // Expanding rings
        int particles = 16;
        
        for (int i = 0; i < particles; i++) {
            double angle = (i * 2 * Math.PI) / particles;
            double x = center.getX() + Math.cos(angle) * radius;
            double z = center.getZ() + Math.sin(angle) * radius;
            double y = center.getY() + 1;
            
            Location ringLoc = new Location(center.getWorld(), x, y, z);
            
            // Alternating red and dark red ring
            Color ringColor = i % 2 == 0 ? Color.RED : Color.MAROON;
            center.getWorld().spawnParticle(Particle.DUST, ringLoc, 1, 0, 0, 0, 0,
                new Particle.DustOptions(ringColor, 2.0f));
        }
    }

    private static void broadcastUnlockToNearby(Player player, Chunk chunk, int totalUnlocked, boolean isOverclaim) {
        String message;
        
        if (isOverclaim) {
            message = "§7[§c⚔§7] §f" + player.getName() + " §coverclaimed §fchunk §f" + 
                     chunk.getX() + ", " + chunk.getZ() + " §7(§e" + totalUnlocked + " total§7)";
        } else {
            message = "§7[§a+§7] §f" + player.getName() + " §7unlocked chunk §f" + 
                     chunk.getX() + ", " + chunk.getZ() + " §7(§e" + totalUnlocked + " total§7)";
        }
        
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