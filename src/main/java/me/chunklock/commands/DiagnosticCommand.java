package me.chunklock.commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;

import java.util.Collections;
import java.util.List;

/**
 * Diagnostic command to verify the new command system is working.
 */
public class DiagnosticCommand extends SubCommand {
    
    public DiagnosticCommand() {
        super("diagnostic", "chunklock.admin", false);
    }
    
    @Override
    public boolean execute(CommandSender sender, String[] args) {
        sender.sendMessage(Component.text("=== Chunklock Command System Diagnostic ===")
            .color(NamedTextColor.AQUA));
        sender.sendMessage(Component.text("✓ New modular command system is active!")
            .color(NamedTextColor.GREEN));
        sender.sendMessage(Component.text("Command class: " + this.getClass().getName())
            .color(NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("Timestamp: " + System.currentTimeMillis())
            .color(NamedTextColor.GRAY));
        
        return true;
    }
    
    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        return Collections.emptyList();
    }
    
    @Override
    public String getUsage() {
        return "/chunklock diagnostic";
    }
    
    @Override
    public String getDescription() {
        return "Test if new command system is working";
    }
}