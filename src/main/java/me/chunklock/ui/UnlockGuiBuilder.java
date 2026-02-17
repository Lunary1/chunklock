package me.chunklock.ui;

import me.chunklock.config.LanguageKeys;
import me.chunklock.economy.items.ItemRequirement;
import me.chunklock.managers.BiomeUnlockRegistry;
import me.chunklock.managers.ChunkEvaluator;
import me.chunklock.models.Difficulty;
import me.chunklock.util.message.MessageUtil;
import me.chunklock.util.player.EnchantmentUtil;
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
                           BiomeUnlockRegistry.UnlockRequirement requirement,
                           me.chunklock.economy.EconomyManager economyManager,
                           BiomeUnlockRegistry biomeRegistry) {
        // Legacy signature - calculate paymentRequirement here for backward compatibility
        me.chunklock.economy.EconomyManager.PaymentRequirement paymentRequirement = 
            economyManager.calculateRequirement(player, chunk, eval.biome, eval);
        return build(player, chunk, eval, requirement, paymentRequirement, economyManager, biomeRegistry);
    }
    
    public Inventory build(Player player, Chunk chunk, ChunkEvaluator.ChunkValueData eval,
                           BiomeUnlockRegistry.UnlockRequirement requirement,
                           me.chunklock.economy.EconomyManager.PaymentRequirement paymentRequirement,
                           me.chunklock.economy.EconomyManager economyManager,
                           BiomeUnlockRegistry biomeRegistry) {
        String guiTitleBase = MessageUtil.getMessage(LanguageKeys.GUI_UNLOCK_TITLE);
        String guiTitle = guiTitleBase + " (" + chunk.getX() + ", " + chunk.getZ() + ")";
        Inventory inv = Bukkit.createInventory(null, GUI_SIZE,
            Component.text(guiTitle).color(NamedTextColor.GOLD));
        
        // Add decorative borders
        addBorderDecoration(inv);
        
        // Add all informational items
        addChunkInfoItem(inv, chunk, eval);
        
        // Check if we should use Vault economy or materials
        if (economyManager != null && economyManager.getCurrentType() == me.chunklock.economy.EconomyManager.EconomyType.VAULT 
            && economyManager.isVaultAvailable()) {
            // Use money-based UI with unified cost calculation
            addMoneyProgressBar(inv, player, paymentRequirement, economyManager);
            addMoneyRequirementDisplay(inv, player, paymentRequirement, economyManager);
            addMoneyUnlockButton(inv, player, paymentRequirement, economyManager);
        } else {
            // Use material-based UI (default) - use paymentRequirement for consistency
            if (paymentRequirement.getType() == me.chunklock.economy.EconomyManager.EconomyType.MATERIALS) {
                // Update requirement from paymentRequirement to ensure consistency
                requirement = new BiomeUnlockRegistry.UnlockRequirement(
                    paymentRequirement.getMaterial(), 
                    paymentRequirement.getMaterialAmount()
                );
            }
            addProgressBar(inv, player, requirement);
            addRequirementDisplay(inv, player, requirement, paymentRequirement, eval.biome, biomeRegistry);
            addUnlockButton(inv, player, requirement, paymentRequirement);
        }
        
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

        String infoTitle = MessageUtil.getMessage(LanguageKeys.GUI_BUILDER_CHUNK_INFO_TITLE);
        meta.displayName(Component.text(infoTitle)
            .color(NamedTextColor.AQUA)
            .decoration(TextDecoration.ITALIC, false)
            .decoration(TextDecoration.BOLD, true));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        java.util.Map<String, String> placeholders = new java.util.HashMap<>();
        placeholders.put("location", chunk.getX() + ", " + chunk.getZ());
        String locationMsg = MessageUtil.getMessage(LanguageKeys.GUI_BUILDER_CHUNK_INFO_LOCATION, placeholders);
        lore.add(Component.text(locationMsg).color(NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
        
        placeholders.put("biome", BiomeUnlockRegistry.getBiomeDisplayName(eval.biome));
        String biomeMsg = MessageUtil.getMessage(LanguageKeys.GUI_BUILDER_CHUNK_INFO_BIOME, placeholders);
        lore.add(Component.text(biomeMsg).color(NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        
        placeholders.put("difficulty", eval.difficulty.toString());
        String difficultyMsg = MessageUtil.getMessage(LanguageKeys.GUI_BUILDER_CHUNK_INFO_DIFFICULTY, placeholders);
        lore.add(Component.text(difficultyMsg).color(getDifficultyColor(eval.difficulty)).decoration(TextDecoration.ITALIC, false));
        
        placeholders.put("score", String.valueOf(eval.score));
        String scoreMsg = MessageUtil.getMessage(LanguageKeys.GUI_BUILDER_CHUNK_INFO_SCORE, placeholders);
        lore.add(Component.text(scoreMsg).color(NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));

        lore.add(Component.empty());
        String note1 = MessageUtil.getMessage(LanguageKeys.GUI_BUILDER_CHUNK_INFO_DIFFICULTY_NOTE);
        lore.add(Component.text(note1).color(NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, true));
        String note2 = MessageUtil.getMessage(LanguageKeys.GUI_BUILDER_CHUNK_INFO_DIFFICULTY_NOTE_2);
        lore.add(Component.text(note2).color(NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, true));

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
        
        java.util.Map<String, String> placeholders = new java.util.HashMap<>();
        placeholders.put("percentage", String.format("%.1f", percentage));
        String progressTitle = MessageUtil.getMessage(LanguageKeys.GUI_BUILDER_PROGRESS_TITLE, placeholders);
        meta.displayName(Component.text(progressTitle)
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
        placeholders.put("have", String.valueOf(playerHas));
        placeholders.put("required", String.valueOf(required));
        String itemsMsg = MessageUtil.getMessage(LanguageKeys.GUI_BUILDER_PROGRESS_ITEMS, placeholders);
        lore.add(Component.text(itemsMsg).color(NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
            
        if (playerHas < required) {
            placeholders.put("needed", String.valueOf(required - playerHas));
            String needMsg = MessageUtil.getMessage(LanguageKeys.GUI_BUILDER_PROGRESS_NEED, placeholders);
            lore.add(Component.text(needMsg).color(NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        }
        
        meta.lore(lore);
        progressItem.setItemMeta(meta);
        inv.setItem(PROGRESS_SLOT, progressItem);
    }
    
    private void addRequirementDisplay(Inventory inv, Player player, BiomeUnlockRegistry.UnlockRequirement requirement,
                                       me.chunklock.economy.EconomyManager.PaymentRequirement paymentRequirement,
                                       Biome biome, BiomeUnlockRegistry biomeRegistry) {
        // Use payment requirement's items for display/validation consistency with cost calculation
        List<ItemRequirement> allRequirements = (paymentRequirement != null && !paymentRequirement.getItemRequirements().isEmpty()) ?
            paymentRequirement.getItemRequirements() : biomeRegistry.getRequirementsForBiome(biome);
        
        if (allRequirements.isEmpty()) {
            // Fallback to old display if no requirements found
            addRequirementDisplayLegacy(inv, player, requirement);
            return;
        }
        
        // Check ALL requirements from payment requirement
        int requiredAmount = requirement.amount();
        int playerHas = countPlayerItems(player, requirement.material());
        boolean hasEnough = true;
        for (ItemRequirement req : allRequirements) {
            if (!req.hasInInventory(player)) {
                hasEnough = false;
                break;
            }
        }
        
        // Main requirement display in center (shows first vanilla item or first custom item)
        ItemRequirement firstReq = allRequirements.get(0);
        requiredAmount = firstReq.getAmount();
        if (firstReq instanceof me.chunklock.economy.items.VanillaItemRequirement vanillaReq) {
            playerHas = countPlayerItems(player, vanillaReq.getMaterial());
        } else {
            playerHas = firstReq.hasInInventory(player) ? firstReq.getAmount() : 0;
        }
        ItemStack mainDisplay = firstReq.getRepresentativeStack();
        if (mainDisplay == null) {
            mainDisplay = new ItemStack(Material.PAPER);
        }
        mainDisplay.setAmount(Math.min(64, requiredAmount));
        
        ItemMeta meta = mainDisplay.getItemMeta();
        if (meta == null) {
            // Fallback: create a new ItemMeta for this item type
            ItemStack temp = new ItemStack(Material.PAPER);
            meta = temp.getItemMeta();
        }
        
        String itemDisplayName = firstReq.getDisplayName();
        java.util.Map<String, String> placeholders = new java.util.HashMap<>();
        placeholders.put("item", itemDisplayName);
        String requiredTitle = MessageUtil.getMessage(LanguageKeys.GUI_BUILDER_REQUIRED_TITLE, placeholders);
        meta.displayName(Component.text(requiredTitle)
            .color(hasEnough ? NamedTextColor.GREEN : NamedTextColor.RED)
            .decoration(TextDecoration.ITALIC, false)
            .decoration(TextDecoration.BOLD, true));
            
        if (hasEnough) {
            Enchantment unbreaking = EnchantmentUtil.getUnbreaking();
            if (unbreaking != null) {
                meta.addEnchant(unbreaking, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
        }
        
        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        placeholders.clear();
        placeholders.put("amount", String.valueOf(requiredAmount));
        String totalMsg = MessageUtil.getMessage(LanguageKeys.GUI_BUILDER_REQUIRED_TOTAL, placeholders);
        lore.add(Component.text(totalMsg).color(NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
        
        placeholders.put("have", String.valueOf(playerHas));
        String haveMsg = MessageUtil.getMessage(LanguageKeys.GUI_BUILDER_REQUIRED_HAVE, placeholders);
        lore.add(Component.text(haveMsg).color(hasEnough ? NamedTextColor.GREEN : NamedTextColor.RED)
            .decoration(TextDecoration.ITALIC, false));
        
        // Show all requirements if multiple items needed
        if (allRequirements.size() > 1) {
            lore.add(Component.empty());
            String allReqMsg = MessageUtil.getMessage(LanguageKeys.GUI_BUILDER_REQUIRED_ALL);
            lore.add(Component.text(allReqMsg).color(NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
            for (ItemRequirement req : allRequirements) {
                boolean hasReq = req.hasInInventory(player);
                lore.add(Component.text("  • " + req.getDisplayName())
                    .color(hasReq ? NamedTextColor.GREEN : NamedTextColor.RED)
                    .decoration(TextDecoration.ITALIC, false));
            }
        }
            
        if (!hasEnough) {
            lore.add(Component.empty());
            placeholders.put("missing", String.valueOf(Math.max(0, requiredAmount - playerHas)));
            String missingMsg = MessageUtil.getMessage(LanguageKeys.GUI_BUILDER_REQUIRED_MISSING, placeholders);
            lore.add(Component.text(missingMsg).color(NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.empty());
            placeholders.put("item", itemDisplayName);
            String tipMsg = MessageUtil.getMessage(LanguageKeys.GUI_BUILDER_REQUIRED_TIP, placeholders);
            lore.add(Component.text(tipMsg).color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, true));
        } else {
            lore.add(Component.empty());
            String allItemsMsg = MessageUtil.getMessage(LanguageKeys.GUI_BUILDER_REQUIRED_ALL_ITEMS);
            lore.add(Component.text(allItemsMsg).color(NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
        }
        
        meta.lore(lore);
        mainDisplay.setItemMeta(meta);
        inv.setItem(22, mainDisplay);
        
        // Visual representation of stacks if needed
        if (requiredAmount > 64) {
            addStackVisualization(inv, requirement, playerHas);
        }
    }
    
    /**
     * Legacy requirement display for backward compatibility.
     */
    private void addRequirementDisplayLegacy(Inventory inv, Player player, BiomeUnlockRegistry.UnlockRequirement requirement) {
        int requiredAmount = requirement.amount();
        int playerHas = countPlayerItems(player, requirement.material());
        boolean hasEnough = playerHas >= requiredAmount;
        
        // Main requirement display in center
        ItemStack mainDisplay = new ItemStack(requirement.material(), Math.min(64, requiredAmount));
        ItemMeta meta = mainDisplay.getItemMeta();
        
        java.util.Map<String, String> placeholders = new java.util.HashMap<>();
        placeholders.put("item", formatMaterialName(requirement.material()));
        String requiredTitle = MessageUtil.getMessage(LanguageKeys.GUI_BUILDER_REQUIRED_TITLE, placeholders);
        meta.displayName(Component.text(requiredTitle)
            .color(hasEnough ? NamedTextColor.GREEN : NamedTextColor.RED)
            .decoration(TextDecoration.ITALIC, false)
            .decoration(TextDecoration.BOLD, true));
            
        if (hasEnough) {
            Enchantment unbreaking = EnchantmentUtil.getUnbreaking();
            if (unbreaking != null) {
                meta.addEnchant(unbreaking, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
        }
        
        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        placeholders.put("amount", String.valueOf(requiredAmount));
        String totalMsg = MessageUtil.getMessage(LanguageKeys.GUI_BUILDER_REQUIRED_TOTAL, placeholders);
        lore.add(Component.text(totalMsg).color(NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
        
        placeholders.put("have", String.valueOf(playerHas));
        String haveMsg = MessageUtil.getMessage(LanguageKeys.GUI_BUILDER_REQUIRED_HAVE, placeholders);
        lore.add(Component.text(haveMsg).color(hasEnough ? NamedTextColor.GREEN : NamedTextColor.RED)
            .decoration(TextDecoration.ITALIC, false));
            
        if (!hasEnough) {
            lore.add(Component.empty());
            placeholders.put("missing", String.valueOf(requiredAmount - playerHas));
            String missingMsg = MessageUtil.getMessage(LanguageKeys.GUI_BUILDER_REQUIRED_MISSING, placeholders);
            lore.add(Component.text(missingMsg).color(NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.empty());
            String tipMsg = MessageUtil.getMessage(LanguageKeys.GUI_BUILDER_REQUIRED_TIP, placeholders);
            lore.add(Component.text(tipMsg).color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, true));
        } else {
            lore.add(Component.empty());
            String enoughMsg = MessageUtil.getMessage(LanguageKeys.GUI_BUILDER_REQUIRED_ENOUGH);
            lore.add(Component.text(enoughMsg).color(NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
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
                String partMsg = MessageUtil.getMessage(LanguageKeys.GUI_BUILDER_STACK_PART);
                lore.add(Component.text(partMsg).color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            }
            if (fullStacks > 4 && i == 3) {
                java.util.Map<String, String> placeholders = new java.util.HashMap<>();
                placeholders.put("more", String.valueOf(fullStacks - 3));
                String moreMsg = MessageUtil.getMessage(LanguageKeys.GUI_BUILDER_STACK_MORE, placeholders);
                lore.add(Component.text(moreMsg).color(NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, true));
            }
            
            meta.lore(lore);
            stack.setItemMeta(meta);
            inv.setItem(slots[i], stack);
        }
    }

    private void addUnlockButton(Inventory inv, Player player, BiomeUnlockRegistry.UnlockRequirement requirement,
                                  me.chunklock.economy.EconomyManager.PaymentRequirement paymentRequirement) {
        // Check ALL requirements from payment requirement for consistency
        boolean hasEnough;
        if (paymentRequirement != null && !paymentRequirement.getItemRequirements().isEmpty()) {
            hasEnough = true;
            for (ItemRequirement req : paymentRequirement.getItemRequirements()) {
                if (!req.hasInInventory(player)) {
                    hasEnough = false;
                    break;
                }
            }
        } else {
            hasEnough = countPlayerItems(player, requirement.material()) >= requirement.amount();
        }
        
        ItemStack unlockButton;
        ItemMeta meta;
        
        java.util.Map<String, String> placeholders = new java.util.HashMap<>();
        if (hasEnough) {
            unlockButton = new ItemStack(Material.EMERALD_BLOCK);
            meta = unlockButton.getItemMeta();
            
            String readyTitle = MessageUtil.getMessage(LanguageKeys.GUI_BUILDER_UNLOCK_BUTTON_READY);
            meta.displayName(Component.text(readyTitle)
                .color(NamedTextColor.GREEN)
                .decoration(TextDecoration.ITALIC, false)
                .decoration(TextDecoration.BOLD, true));
                
            Enchantment unbreaking = EnchantmentUtil.getUnbreaking();
            if (unbreaking != null) {
                meta.addEnchant(unbreaking, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
            
            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            String metMsg = MessageUtil.getMessage(LanguageKeys.GUI_BUILDER_UNLOCK_BUTTON_REQUIREMENTS_MET);
            lore.add(Component.text(metMsg).color(NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
            
            placeholders.put("amount", String.valueOf(requirement.amount()));
            placeholders.put("material", formatMaterialName(requirement.material()));
            String consumeMsg = MessageUtil.getMessage(LanguageKeys.GUI_BUILDER_UNLOCK_BUTTON_CONSUME, placeholders);
            lore.add(Component.text(consumeMsg).color(NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.empty());
            String clickMsg = MessageUtil.getMessage(LanguageKeys.GUI_BUILDER_UNLOCK_BUTTON_CLICK);
            lore.add(Component.text(clickMsg).color(NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
            
            meta.lore(lore);
        } else {
            unlockButton = new ItemStack(Material.REDSTONE_BLOCK);
            meta = unlockButton.getItemMeta();
            
            String notReadyTitle = MessageUtil.getMessage(LanguageKeys.GUI_BUILDER_UNLOCK_BUTTON_NOT_READY);
            meta.displayName(Component.text(notReadyTitle)
                .color(NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false)
                .decoration(TextDecoration.BOLD, true));
                
            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            String missingMsg = MessageUtil.getMessage(LanguageKeys.GUI_BUILDER_UNLOCK_BUTTON_MISSING);
            lore.add(Component.text(missingMsg).color(NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
            
            placeholders.put("amount", String.valueOf(Math.max(0, requirement.amount() - countPlayerItems(player, requirement.material()))));
            placeholders.put("material", formatMaterialName(requirement.material()));
            String needMsg = MessageUtil.getMessage(LanguageKeys.GUI_BUILDER_UNLOCK_BUTTON_NEED_MORE, placeholders);
            lore.add(Component.text(needMsg).color(NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.empty());
            String gatherMsg = MessageUtil.getMessage(LanguageKeys.GUI_BUILDER_UNLOCK_BUTTON_GATHER);
            lore.add(Component.text(gatherMsg).color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, true));
            
            meta.lore(lore);
        }
        
        unlockButton.setItemMeta(meta);
        inv.setItem(UNLOCK_BUTTON_SLOT, unlockButton);
    }
    
    private void addHelpItem(Inventory inv) {
        ItemStack helpBook = new ItemStack(Material.BOOK);
        ItemMeta meta = helpBook.getItemMeta();
        
        String helpTitle = MessageUtil.getMessage(LanguageKeys.GUI_BUILDER_HELP_TITLE);
        meta.displayName(Component.text(helpTitle)
            .color(NamedTextColor.LIGHT_PURPLE)
            .decoration(TextDecoration.ITALIC, false)
            .decoration(TextDecoration.BOLD, true));
            
        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        String processTitle = MessageUtil.getMessage(LanguageKeys.GUI_BUILDER_HELP_PROCESS_TITLE);
        lore.add(Component.text(processTitle).color(NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        
        String step1 = MessageUtil.getMessage(LanguageKeys.GUI_BUILDER_HELP_STEP_1);
        lore.add(Component.text(step1).color(NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
        String step2 = MessageUtil.getMessage(LanguageKeys.GUI_BUILDER_HELP_STEP_2);
        lore.add(Component.text(step2).color(NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
        String step3 = MessageUtil.getMessage(LanguageKeys.GUI_BUILDER_HELP_STEP_3);
        lore.add(Component.text(step3).color(NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
            
        lore.add(Component.empty());
        String tipsTitle = MessageUtil.getMessage(LanguageKeys.GUI_BUILDER_HELP_TIPS_TITLE);
        lore.add(Component.text(tipsTitle).color(NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
        
        String tip1 = MessageUtil.getMessage(LanguageKeys.GUI_BUILDER_HELP_TIP_1);
        lore.add(Component.text(tip1).color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        String tip2 = MessageUtil.getMessage(LanguageKeys.GUI_BUILDER_HELP_TIP_2);
        lore.add(Component.text(tip2).color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        String tip3 = MessageUtil.getMessage(LanguageKeys.GUI_BUILDER_HELP_TIP_3);
        lore.add(Component.text(tip3).color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            
        meta.lore(lore);
        helpBook.setItemMeta(meta);
        inv.setItem(HELP_SLOT, helpBook);
    }
    
    private void addTeamInfo(Inventory inv, Player player) {
        // This would need TeamManager integration
        ItemStack teamBanner = new ItemStack(Material.WHITE_BANNER);
        ItemMeta meta = teamBanner.getItemMeta();
        
        String teamTitle = MessageUtil.getMessage(LanguageKeys.GUI_BUILDER_TEAM_TITLE);
        meta.displayName(Component.text(teamTitle)
            .color(NamedTextColor.GOLD)
            .decoration(TextDecoration.ITALIC, false)
            .decoration(TextDecoration.BOLD, true));
            
        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        String unlockMsg = MessageUtil.getMessage(LanguageKeys.GUI_BUILDER_TEAM_UNLOCK);
        lore.add(Component.text(unlockMsg).color(NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        String resourcesMsg = MessageUtil.getMessage(LanguageKeys.GUI_BUILDER_TEAM_RESOURCES);
        lore.add(Component.text(resourcesMsg).color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            
        meta.lore(lore);
        teamBanner.setItemMeta(meta);
        inv.setItem(40, teamBanner);
    }

    private String formatMaterialName(Material material) {
        return me.chunklock.util.item.MaterialUtil.formatMaterialName(material);
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
    
    // ============ MONEY-BASED UI METHODS ============
    
    private void addMoneyProgressBar(Inventory inv, Player player, 
                                   me.chunklock.economy.EconomyManager.PaymentRequirement requirement,
                                   me.chunklock.economy.EconomyManager economyManager) {
        ItemStack progressItem = new ItemStack(Material.GOLD_INGOT);
        ItemMeta meta = progressItem.getItemMeta();
        
        double playerBalance = economyManager.getVaultService().getBalance(player);
        double requiredCost = requirement.getVaultCost();
        boolean canAfford = playerBalance >= requiredCost;
        
        double percentage = Math.min(100.0, (playerBalance / requiredCost) * 100.0);
        
        java.util.Map<String, String> placeholders = new java.util.HashMap<>();
        placeholders.put("percentage", String.format("%.1f", percentage));
        String progressTitle = MessageUtil.getMessage(LanguageKeys.GUI_BUILDER_MONEY_PROGRESS_TITLE, placeholders);
        meta.displayName(Component.text(progressTitle)
            .color(canAfford ? NamedTextColor.GREEN : NamedTextColor.RED)
            .decoration(TextDecoration.ITALIC, false)
            .decoration(TextDecoration.BOLD, true));
            
        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        
        // Create visual progress bar
        int filledBars = (int) (percentage / 10);
        StringBuilder progressBar = new StringBuilder("§6");
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
        
        String formattedBalance = economyManager.getVaultService().format(playerBalance);
        String formattedCost = economyManager.getVaultService().format(requiredCost);
        
        placeholders.put("balance", formattedBalance);
        String balanceMsg = MessageUtil.getMessage(LanguageKeys.GUI_BUILDER_MONEY_PROGRESS_BALANCE, placeholders);
        lore.add(Component.text(balanceMsg).color(NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
        
        placeholders.put("required", formattedCost);
        String requiredMsg = MessageUtil.getMessage(LanguageKeys.GUI_BUILDER_MONEY_PROGRESS_REQUIRED, placeholders);
        lore.add(Component.text(requiredMsg).color(NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
            
        if (!canAfford) {
            double needed = requiredCost - playerBalance;
            String formattedNeeded = economyManager.getVaultService().format(needed);
            placeholders.put("needed", formattedNeeded);
            String needMsg = MessageUtil.getMessage(LanguageKeys.GUI_BUILDER_MONEY_PROGRESS_NEED, placeholders);
            lore.add(Component.text(needMsg).color(NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        }
        
        meta.lore(lore);
        progressItem.setItemMeta(meta);
        inv.setItem(PROGRESS_SLOT, progressItem);
    }
    
    private void addMoneyRequirementDisplay(Inventory inv, Player player,
                                          me.chunklock.economy.EconomyManager.PaymentRequirement requirement,
                                          me.chunklock.economy.EconomyManager economyManager) {
        double playerBalance = economyManager.getVaultService().getBalance(player);
        double requiredCost = requirement.getVaultCost();
        boolean canAfford = playerBalance >= requiredCost;
        
        // Main requirement display in center
        ItemStack mainDisplay = new ItemStack(Material.EMERALD);
        ItemMeta meta = mainDisplay.getItemMeta();
        
        String formattedCost = economyManager.getVaultService().format(requiredCost);
        String currencyName = economyManager.getVaultService().getCurrencyName();
        
        java.util.Map<String, String> placeholders = new java.util.HashMap<>();
        placeholders.put("cost", formattedCost);
        String requiredTitle = MessageUtil.getMessage(LanguageKeys.GUI_BUILDER_MONEY_REQUIRED_TITLE, placeholders);
        meta.displayName(Component.text(requiredTitle)
            .color(canAfford ? NamedTextColor.GREEN : NamedTextColor.RED)
            .decoration(TextDecoration.ITALIC, false)
            .decoration(TextDecoration.BOLD, true));
            
        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        String modeMsg = MessageUtil.getMessage(LanguageKeys.GUI_BUILDER_MONEY_REQUIRED_MODE);
        lore.add(Component.text(modeMsg).color(NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
        
        placeholders.put("currency", currencyName);
        String currencyMsg = MessageUtil.getMessage(LanguageKeys.GUI_BUILDER_MONEY_REQUIRED_CURRENCY, placeholders);
        lore.add(Component.text(currencyMsg).color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        
        if (canAfford) {
            String canAffordMsg = MessageUtil.getMessage(LanguageKeys.GUI_BUILDER_MONEY_REQUIRED_CAN_AFFORD);
            lore.add(Component.text(canAffordMsg).color(NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
        } else {
            String cannotAffordMsg = MessageUtil.getMessage(LanguageKeys.GUI_BUILDER_MONEY_REQUIRED_CANNOT_AFFORD);
            lore.add(Component.text(cannotAffordMsg).color(NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
        }
        
        lore.add(Component.empty());
        String clickMsg = MessageUtil.getMessage(LanguageKeys.GUI_BUILDER_MONEY_REQUIRED_CLICK);
        lore.add(Component.text(clickMsg).color(NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
            
        meta.lore(lore);
        mainDisplay.setItemMeta(meta);
        inv.setItem(22, mainDisplay); // Center slot
    }
    
    private void addMoneyUnlockButton(Inventory inv, Player player,
                                    me.chunklock.economy.EconomyManager.PaymentRequirement requirement,
                                    me.chunklock.economy.EconomyManager economyManager) {
        double playerBalance = economyManager.getVaultService().getBalance(player);
        double requiredCost = requirement.getVaultCost();
        boolean canAfford = playerBalance >= requiredCost;
        
        Material buttonMaterial = canAfford ? Material.LIME_CONCRETE : Material.RED_CONCRETE;
        ItemStack unlockButton = new ItemStack(buttonMaterial);
        ItemMeta meta = unlockButton.getItemMeta();
        
        String formattedCost = economyManager.getVaultService().format(requiredCost);
        
        java.util.Map<String, String> placeholders = new java.util.HashMap<>();
        placeholders.put("cost", formattedCost);
        
        if (canAfford) {
            String readyTitle = MessageUtil.getMessage(LanguageKeys.GUI_BUILDER_MONEY_UNLOCK_BUTTON_READY, placeholders);
            meta.displayName(Component.text(readyTitle)
                .color(NamedTextColor.GREEN)
                .decoration(TextDecoration.ITALIC, false)
                .decoration(TextDecoration.BOLD, true));
        } else {
            String notReadyTitle = MessageUtil.getMessage(LanguageKeys.GUI_BUILDER_MONEY_UNLOCK_BUTTON_NOT_READY);
            meta.displayName(Component.text(notReadyTitle)
                .color(NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false)
                .decoration(TextDecoration.BOLD, true));
        }
        
        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        
        if (canAfford) {
            String clickMsg = MessageUtil.getMessage(LanguageKeys.GUI_BUILDER_MONEY_UNLOCK_BUTTON_CLICK, placeholders);
            lore.add(Component.text(clickMsg).color(NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
            String clickMsg2 = MessageUtil.getMessage(LanguageKeys.GUI_BUILDER_MONEY_UNLOCK_BUTTON_CLICK_2);
            lore.add(Component.text(" " + clickMsg2).color(NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
            
            // Add enchant effect if affordable (use UNBREAKING instead of deprecated DURABILITY)
            meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        } else {
            String formattedBalance = economyManager.getVaultService().format(playerBalance);
            placeholders.put("balance", formattedBalance);
            String balanceMsg = MessageUtil.getMessage(LanguageKeys.GUI_BUILDER_MONEY_UNLOCK_BUTTON_BALANCE, placeholders);
            lore.add(Component.text(balanceMsg).color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            double needed = requiredCost - playerBalance;
            String formattedNeeded = economyManager.getVaultService().format(needed);
            placeholders.put("needed", formattedNeeded);
            String needMsg = MessageUtil.getMessage(LanguageKeys.GUI_BUILDER_MONEY_UNLOCK_BUTTON_NEED, placeholders);
            lore.add(Component.text(needMsg).color(NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
        }
        
        meta.lore(lore);
        unlockButton.setItemMeta(meta);
        inv.setItem(UNLOCK_BUTTON_SLOT, unlockButton);
    }
}