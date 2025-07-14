package me.chunklock.ui;

import me.chunklock.managers.BiomeUnlockRegistry;
import me.chunklock.managers.ChunkEvaluator;
import me.chunklock.models.Difficulty;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.block.Biome;


import java.util.ArrayList;
import java.util.List;

public class UnlockGuiBuilder {
    
    // Constants for GUI layout
    private static final int GUI_SIZE = 54; // 6 rows instead of 3
    private static final int INFO_SLOT = 4;
    private static final int PROGRESS_SLOT = 13;
    private static final int UNLOCK_BUTTON_SLOT = 31;
    private static final int HELP_SLOT = 49;
    
    public Inventory build(Player player, Chunk chunk, ChunkEvaluator.ChunkValueData eval,
                           BiomeUnlockRegistry.UnlockRequirement requirement) {
        Inventory inv = Bukkit.createInventory(null, GUI_SIZE, 
            Component.text("🔓 Unlock Chunk (" + chunk.getX() + ", " + chunk.getZ() + ")")
                .color(NamedTextColor.GOLD));
        
        // Add decorative borders
        addBorderDecoration(inv);
        
        // Add all informational items
        addChunkInfoItem(inv, chunk, eval);
        addProgressBar(inv, player, requirement);
        addRequirementDisplay(inv, player, requirement);
        addUnlockButton(inv, player, requirement);
        addHelpItem(inv);
        addTeamInfo(inv, player);
        
        return inv;
    }
    
    private void addBorderDecoration(Inventory inv) {
        ItemStack borderGlass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = borderGlass.getItemMeta();
        meta.displayName(Component.text(" "));
        borderGlass.setItemMeta(meta);
        
        // Top and bottom rows
        for (int i = 0; i < 9; i++) {
            inv.setItem(i, borderGlass);
            inv.setItem(45 + i, borderGlass);
        }
        
        // Side columns
        for (int i = 1; i < 5; i++) {
            inv.setItem(i * 9, borderGlass);
            inv.setItem(i * 9 + 8, borderGlass);
        }
    }

    private void addChunkInfoItem(Inventory inv, Chunk chunk, ChunkEvaluator.ChunkValueData eval) {
        Material infoMaterial;

        // Determine material based on biome
        if (eval.biome == Biome.PLAINS) {
            infoMaterial = Material.GRASS_BLOCK;
        } else if (eval.biome == Biome.FOREST
                || eval.biome == Biome.BIRCH_FOREST
                || eval.biome == Biome.DARK_FOREST) {
            infoMaterial = Material.OAK_LOG;
        } else if (eval.biome == Biome.DESERT) {
            infoMaterial = Material.SAND;
        } else if (eval.biome == Biome.JUNGLE) {
            infoMaterial = Material.JUNGLE_LOG;
        } else if (eval.biome == Biome.SWAMP) {
            infoMaterial = Material.LILY_PAD;
        } else if (eval.biome == Biome.OCEAN
                || eval.biome == Biome.DEEP_OCEAN) {
            infoMaterial = Material.KELP;
        } else if (eval.biome == Biome.TAIGA
                || eval.biome == Biome.SNOWY_TAIGA) {
            infoMaterial = Material.SPRUCE_LOG;
        } else if (eval.biome == Biome.SAVANNA) {
            infoMaterial = Material.ACACIA_LOG;
        } else if (eval.biome == Biome.BADLANDS) {
            infoMaterial = Material.RED_SAND;
        } else {
            infoMaterial = Material.MAP;
        }

        ItemStack chunkInfo = new ItemStack(infoMaterial);
        ItemMeta meta = chunkInfo.getItemMeta();

        if (meta == null) return; // Safety check

        meta.displayName(Component.text("📍 Chunk Information")
            .color(NamedTextColor.AQUA)
            .decoration(TextDecoration.ITALIC, false)
            .decoration(TextDecoration.BOLD, true));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(Component.text("📌 Location: ").color(NamedTextColor.GRAY)
            .append(Component.text(chunk.getX() + ", " + chunk.getZ()).color(NamedTextColor.WHITE))
            .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("🌿 Biome: ").color(NamedTextColor.GRAY)
            .append(Component.text(BiomeUnlockRegistry.getBiomeDisplayName(eval.biome)).color(NamedTextColor.YELLOW))
            .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("⚔ Difficulty: ").color(NamedTextColor.GRAY)
            .append(Component.text(eval.difficulty.toString()).color(getDifficultyColor(eval.difficulty)))
            .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("📊 Score: ").color(NamedTextColor.GRAY)
            .append(Component.text(eval.score + " points").color(NamedTextColor.WHITE))
            .decoration(TextDecoration.ITALIC, false));

        lore.add(Component.empty());
        lore.add(Component.text("ℹ Difficulty affects the amount")
            .color(NamedTextColor.DARK_GRAY)
            .decoration(TextDecoration.ITALIC, true));
        lore.add(Component.text("  of resources required to unlock")
            .color(NamedTextColor.DARK_GRAY)
            .decoration(TextDecoration.ITALIC, true));

        meta.lore(lore);
        chunkInfo.setItemMeta(meta);

        // You must ensure INFO_SLOT is defined somewhere in your class
        inv.setItem(INFO_SLOT, chunkInfo);
    }
    
    private void addProgressBar(Inventory inv, Player player, BiomeUnlockRegistry.UnlockRequirement requirement) {
        int playerHas = countPlayerItems(player, requirement.material());
        int required = requirement.amount();
        double percentage = Math.min(100.0, (double) playerHas / required * 100);
        
        ItemStack progressItem = new ItemStack(Material.EXPERIENCE_BOTTLE);
        ItemMeta meta = progressItem.getItemMeta();
        
        meta.displayName(Component.text("📈 Progress: " + String.format("%.1f%%", percentage))
            .color(percentage >= 100 ? NamedTextColor.GREEN : NamedTextColor.YELLOW)
            .decoration(TextDecoration.ITALIC, false)
            .decoration(TextDecoration.BOLD, true));
            
        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        
        // Create visual progress bar
        int filledBars = (int) (percentage / 10);
        StringBuilder progressBar = new StringBuilder("§a");
        for (int i = 0; i < 10; i++) {
            if (i < filledBars) {
                progressBar.append("█");
            } else {
                progressBar.append("§7█");
            }
        }
        
        lore.add(Component.text(progressBar.toString())
            .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        lore.add(Component.text("Items: " + playerHas + " / " + required)
            .color(NamedTextColor.WHITE)
            .decoration(TextDecoration.ITALIC, false));
            
        if (playerHas < required) {
            lore.add(Component.text("Need " + (required - playerHas) + " more")
                .color(NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false));
        }
        
        meta.lore(lore);
        progressItem.setItemMeta(meta);
        inv.setItem(PROGRESS_SLOT, progressItem);
    }
    
    private void addRequirementDisplay(Inventory inv, Player player, BiomeUnlockRegistry.UnlockRequirement requirement) {
        int requiredAmount = requirement.amount();
        int playerHas = countPlayerItems(player, requirement.material());
        boolean hasEnough = playerHas >= requiredAmount;
        
        // Main requirement display in center
        ItemStack mainDisplay = new ItemStack(requirement.material(), Math.min(64, requiredAmount));
        ItemMeta meta = mainDisplay.getItemMeta();
        
        meta.displayName(Component.text("💎 Required: " + formatMaterialName(requirement.material()))
            .color(hasEnough ? NamedTextColor.GREEN : NamedTextColor.RED)
            .decoration(TextDecoration.ITALIC, false)
            .decoration(TextDecoration.BOLD, true));
            
        if (hasEnough) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        
        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(Component.text("📦 Total Required: " + requiredAmount)
            .color(NamedTextColor.WHITE)
            .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("🎒 You Have: " + playerHas)
            .color(hasEnough ? NamedTextColor.GREEN : NamedTextColor.RED)
            .decoration(TextDecoration.ITALIC, false));
            
        if (!hasEnough) {
            lore.add(Component.empty());
            lore.add(Component.text("⚠ Missing: " + (requiredAmount - playerHas))
                .color(NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.empty());
            lore.add(Component.text("💡 Tip: Gather more " + formatMaterialName(requirement.material()))
                .color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, true));
        } else {
            lore.add(Component.empty());
            lore.add(Component.text("✅ You have enough items!")
                .color(NamedTextColor.GREEN)
                .decoration(TextDecoration.ITALIC, false));
        }
        
        meta.lore(lore);
        mainDisplay.setItemMeta(meta);
        inv.setItem(22, mainDisplay);
        
        // Visual representation of stacks if needed
        if (requiredAmount > 64) {
            addStackVisualization(inv, requirement, playerHas);
        }
    }
    
    private void addStackVisualization(Inventory inv, BiomeUnlockRegistry.UnlockRequirement requirement, int playerHas) {
        int requiredAmount = requirement.amount();
        int fullStacks = requiredAmount / 64;
        int remainder = requiredAmount % 64;
        
        // Show up to 3 stacks visually
        int[] slots = {20, 21, 23, 24};
        int stacksToShow = Math.min(fullStacks + (remainder > 0 ? 1 : 0), 4);
        
        for (int i = 0; i < stacksToShow; i++) {
            boolean isLastStack = (i == fullStacks && remainder > 0);
            int stackSize = isLastStack ? remainder : 64;
            
            ItemStack stack = new ItemStack(requirement.material(), stackSize);
            ItemMeta meta = stack.getItemMeta();
            
            String stackName = isLastStack ? 
                "Stack " + (i + 1) + " (" + stackSize + " items)" :
                "Stack " + (i + 1) + " (64 items)";
                
            meta.displayName(Component.text(stackName)
                .color(NamedTextColor.AQUA)
                .decoration(TextDecoration.ITALIC, false));
                
            List<Component> lore = new ArrayList<>();
            if (i < stacksToShow - 1 || fullStacks > 4) {
                lore.add(Component.text("Part of total requirement")
                    .color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
            }
            if (fullStacks > 4 && i == 3) {
                lore.add(Component.text("+" + (fullStacks - 3) + " more stacks...")
                    .color(NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, true));
            }
            
            meta.lore(lore);
            stack.setItemMeta(meta);
            inv.setItem(slots[i], stack);
        }
    }

    private void addUnlockButton(Inventory inv, Player player, BiomeUnlockRegistry.UnlockRequirement requirement) {
        boolean hasEnough = countPlayerItems(player, requirement.material()) >= requirement.amount();
        
        ItemStack unlockButton;
        ItemMeta meta;
        
        if (hasEnough) {
            unlockButton = new ItemStack(Material.EMERALD_BLOCK);
            meta = unlockButton.getItemMeta();
            
            meta.displayName(Component.text("🎯 CLICK TO UNLOCK!")
                .color(NamedTextColor.GREEN)
                .decoration(TextDecoration.ITALIC, false)
                .decoration(TextDecoration.BOLD, true));
                
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            
            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            lore.add(Component.text("✅ All requirements met!")
                .color(NamedTextColor.GREEN)
                .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("📦 " + requirement.amount() + "x " + formatMaterialName(requirement.material()) + " will be consumed")
                .color(NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.empty());
            lore.add(Component.text("➤ Click to unlock this chunk")
                .color(NamedTextColor.AQUA)
                .decoration(TextDecoration.ITALIC, false));
            
            meta.lore(lore);
        } else {
            unlockButton = new ItemStack(Material.REDSTONE_BLOCK);
            meta = unlockButton.getItemMeta();
            
            meta.displayName(Component.text("❌ CANNOT UNLOCK YET")
                .color(NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false)
                .decoration(TextDecoration.BOLD, true));
                
            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            lore.add(Component.text("⚠ Missing required items!")
                .color(NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("Need " + (requirement.amount() - countPlayerItems(player, requirement.material())) + " more " + formatMaterialName(requirement.material()))
                .color(NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.empty());
            lore.add(Component.text("💡 Gather the required items first")
                .color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, true));
            
            meta.lore(lore);
        }
        
        unlockButton.setItemMeta(meta);
        inv.setItem(UNLOCK_BUTTON_SLOT, unlockButton);
    }
    
    private void addHelpItem(Inventory inv) {
        ItemStack helpBook = new ItemStack(Material.BOOK);
        ItemMeta meta = helpBook.getItemMeta();
        
        meta.displayName(Component.text("📖 How to Unlock Chunks")
            .color(NamedTextColor.LIGHT_PURPLE)
            .decoration(TextDecoration.ITALIC, false)
            .decoration(TextDecoration.BOLD, true));
            
        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(Component.text("🔓 Unlocking Process:")
            .color(NamedTextColor.YELLOW)
            .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("  1. Gather required materials")
            .color(NamedTextColor.WHITE)
            .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("  2. Right-click the border glass")
            .color(NamedTextColor.WHITE)
            .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("  3. Click the green unlock button")
            .color(NamedTextColor.WHITE)
            .decoration(TextDecoration.ITALIC, false));
            
        lore.add(Component.empty());
        lore.add(Component.text("💡 Tips:")
            .color(NamedTextColor.AQUA)
            .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("  • Different biomes cost different items")
            .color(NamedTextColor.GRAY)
            .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("  • Harder chunks require more resources")
            .color(NamedTextColor.GRAY)
            .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("  • Team members share unlocked chunks")
            .color(NamedTextColor.GRAY)
            .decoration(TextDecoration.ITALIC, false));
            
        meta.lore(lore);
        helpBook.setItemMeta(meta);
        inv.setItem(HELP_SLOT, helpBook);
    }
    
    private void addTeamInfo(Inventory inv, Player player) {
        // This would need TeamManager integration
        ItemStack teamBanner = new ItemStack(Material.WHITE_BANNER);
        ItemMeta meta = teamBanner.getItemMeta();
        
        meta.displayName(Component.text("👥 Team Status")
            .color(NamedTextColor.GOLD)
            .decoration(TextDecoration.ITALIC, false)
            .decoration(TextDecoration.BOLD, true));
            
        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(Component.text("🏆 Your team can unlock chunks together")
            .color(NamedTextColor.YELLOW)
            .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("📊 Team resources are shared")
            .color(NamedTextColor.GRAY)
            .decoration(TextDecoration.ITALIC, false));
            
        meta.lore(lore);
        teamBanner.setItemMeta(meta);
        inv.setItem(40, teamBanner);
    }

    private String formatMaterialName(Material material) {
        String name = material.name().toLowerCase().replace("_", " ");
        String[] words = name.split(" ");
        StringBuilder formatted = new StringBuilder();
        
        for (String word : words) {
            if (formatted.length() > 0) formatted.append(" ");
            formatted.append(word.substring(0, 1).toUpperCase()).append(word.substring(1));
        }
        
        return formatted.toString();
    }

    private NamedTextColor getDifficultyColor(Difficulty difficulty) {
        return switch (difficulty) {
            case EASY -> NamedTextColor.GREEN;
            case NORMAL -> NamedTextColor.YELLOW;
            case HARD -> NamedTextColor.RED;
            case IMPOSSIBLE -> NamedTextColor.DARK_PURPLE;
        };
    }

    private int countPlayerItems(Player player, Material material) {
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == material) {
                count += item.getAmount();
            }
        }
        ItemStack offHand = player.getInventory().getItemInOffHand();
        if (offHand != null && offHand.getType() == material) {
            count += offHand.getAmount();
        }
        return count;
    }
}