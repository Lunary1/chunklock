// src/main/java/me/chunklock/commands/HelpCommand.java
package me.chunklock.commands;

import me.chunklock.config.LanguageKeys;
import me.chunklock.util.message.MessageUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.CommandSender;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Handles the help command - shows detailed help information.
 */
public class HelpCommand extends SubCommand {
    
    private final ChunklockCommandManager commandManager;
    
    public HelpCommand(ChunklockCommandManager commandManager) {
        super("help", "chunklock.use", false);
        this.commandManager = commandManager;
    }
    
    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length > 0) {
            // Show help for specific command
            return showSpecificCommandHelp(sender, args[0]);
        }
        
        // Show general help
        showGeneralHelp(sender);
        return true;
    }
    
    private boolean showSpecificCommandHelp(CommandSender sender, String commandName) {
        SubCommand subCommand = commandManager.getSubCommands().get(commandName.toLowerCase());
        
        if (subCommand == null) {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("command", commandName);
            String message = MessageUtil.getMessage(LanguageKeys.COMMAND_HELP_UNKNOWN, placeholders);
            sender.sendMessage(Component.text(message).color(NamedTextColor.RED));
            return true;
        }
        
        if (!subCommand.hasPermission(sender)) {
            String message = MessageUtil.getMessage(LanguageKeys.COMMAND_HELP_NO_PERMISSION);
            sender.sendMessage(Component.text(message).color(NamedTextColor.RED));
            return true;
        }
        
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("command", commandName);
        placeholders.put("usage", subCommand.getUsage());
        placeholders.put("description", subCommand.getDescription());
        
        String helpTitle = MessageUtil.getMessage(LanguageKeys.COMMAND_HELP_COMMAND_HELP, placeholders);
        sender.sendMessage(Component.text(helpTitle).color(NamedTextColor.AQUA)
            .decoration(TextDecoration.BOLD, true));
        
        String usageMsg = MessageUtil.getMessage(LanguageKeys.COMMAND_HELP_USAGE, placeholders);
        sender.sendMessage(Component.text(usageMsg).color(NamedTextColor.WHITE));
        
        String descMsg = MessageUtil.getMessage(LanguageKeys.COMMAND_HELP_DESCRIPTION, placeholders);
        sender.sendMessage(Component.text(descMsg).color(NamedTextColor.GRAY));
        
        if (subCommand.getPermission() != null) {
            placeholders.put("permission", subCommand.getPermission());
            String permMsg = MessageUtil.getMessage(LanguageKeys.COMMAND_HELP_PERMISSION, placeholders);
            sender.sendMessage(Component.text(permMsg).color(NamedTextColor.YELLOW));
        }
        
        return true;
    }
    
    private void showGeneralHelp(CommandSender sender) {
        String title = MessageUtil.getMessage(LanguageKeys.COMMAND_HELP_TITLE);
        sender.sendMessage(Component.text(title)
            .color(NamedTextColor.AQUA)
            .decoration(TextDecoration.BOLD, true));
        
        String basicHeader = MessageUtil.getMessage(LanguageKeys.COMMAND_HELP_BASIC_HEADER);
        sender.sendMessage(Component.text(basicHeader).color(NamedTextColor.GRAY));
        
        String statusMsg = MessageUtil.getMessage(LanguageKeys.COMMAND_HELP_STATUS);
        sender.sendMessage(Component.text(statusMsg).color(NamedTextColor.WHITE));
        
        String spawnMsg = MessageUtil.getMessage(LanguageKeys.COMMAND_HELP_SPAWN);
        sender.sendMessage(Component.text(spawnMsg).color(NamedTextColor.WHITE));
        
        String teamMsg = MessageUtil.getMessage(LanguageKeys.COMMAND_HELP_TEAM);
        sender.sendMessage(Component.text(teamMsg).color(NamedTextColor.WHITE));
        
        String tipMsg = MessageUtil.getMessage(LanguageKeys.COMMAND_HELP_TIP);
        sender.sendMessage(Component.text(tipMsg).color(NamedTextColor.GOLD));
        
        if (sender.hasPermission("chunklock.admin")) {
            String adminHeader = MessageUtil.getMessage(LanguageKeys.COMMAND_HELP_ADMIN_HEADER);
            sender.sendMessage(Component.text(adminHeader).color(NamedTextColor.YELLOW));
            
            String unlockMsg = MessageUtil.getMessage(LanguageKeys.COMMAND_HELP_UNLOCK);
            sender.sendMessage(Component.text(unlockMsg).color(NamedTextColor.GRAY));
            
            String resetMsg = MessageUtil.getMessage(LanguageKeys.COMMAND_HELP_RESET);
            sender.sendMessage(Component.text(resetMsg).color(NamedTextColor.GRAY));
            
            String bypassMsg = MessageUtil.getMessage(LanguageKeys.COMMAND_HELP_BYPASS);
            sender.sendMessage(Component.text(bypassMsg).color(NamedTextColor.GRAY));
            
            String reloadMsg = MessageUtil.getMessage(LanguageKeys.COMMAND_HELP_RELOAD);
            sender.sendMessage(Component.text(reloadMsg).color(NamedTextColor.GRAY));
            
            sender.sendMessage(Component.text("").color(NamedTextColor.GRAY));
        }
        
        String detailedMsg = MessageUtil.getMessage(LanguageKeys.COMMAND_HELP_DETAILED);
        sender.sendMessage(Component.text(detailedMsg).color(NamedTextColor.GOLD));
        
        String teamTipMsg = MessageUtil.getMessage(LanguageKeys.COMMAND_HELP_TEAM_TIP);
        sender.sendMessage(Component.text(teamTipMsg).color(NamedTextColor.GREEN));
    }
    
    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        if (args.length == 1) {
            // Complete with available command names
            String prefix = args[0].toLowerCase();
            return commandManager.getSubCommands().keySet().stream()
                .filter(name -> name.startsWith(prefix))
                .filter(name -> {
                    SubCommand cmd = commandManager.getSubCommands().get(name);
                    return cmd.hasPermission(sender) && cmd.isValidSender(sender);
                })
                .sorted()
                .toList();
        }
        
        return Collections.emptyList();
    }
    
    @Override
    public String getUsage() {
        return "/chunklock help [command]";
    }
    
    @Override
    public String getDescription() {
        return "Show help information for commands";
    }
}