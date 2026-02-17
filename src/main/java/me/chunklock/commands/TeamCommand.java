// src/main/java/me/chunklock/commands/TeamCommand.java
package me.chunklock.commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Team command that wraps the BasicTeamCommandHandler
 */
public class TeamCommand extends SubCommand {
    
    private final BasicTeamCommandHandler teamCommandHandler;
    
    public TeamCommand(BasicTeamCommandHandler teamCommandHandler) {
        super("team", "chunklock.team", true);
        this.teamCommandHandler = teamCommandHandler;
    }
    
    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(Component.text("Only players can use team commands.").color(NamedTextColor.RED));
            return true;
        }
        
        // Pass the command to the team handler
        // We need to reconstruct the args array to include "team" as args[0]
        String[] teamArgs = new String[args.length + 1];
        teamArgs[0] = "team";
        System.arraycopy(args, 0, teamArgs, 1, args.length);
        
        return teamCommandHandler.handleTeamCommand(sender, teamArgs);
    }
    
    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 0) {
            // Show all team subcommands
            completions.add("create");
            completions.add("join");
            completions.add("leave");
            completions.add("disband");
            completions.add("accept");
            completions.add("deny");
            completions.add("info");
            completions.add("list");
            completions.add("chat");
            completions.add("help");
        } else if (args.length == 1) {
            // Filter subcommands based on input
            String prefix = args[0].toLowerCase();
            List<String> allSubCommands = List.of("create", "join", "leave", "disband", 
                                                 "accept", "deny", "info", "list", "chat", "help");
            
            for (String subCommand : allSubCommands) {
                if (subCommand.startsWith(prefix)) {
                    completions.add(subCommand);
                }
            }
        }
        // TODO: Add more specific completions for team names, player names, etc.
        
        return completions;
    }
    
    @Override
    public String getUsage() {
        return "/chunklock team <create|join|leave|disband|accept|deny|info|list|chat|help>";
    }
    
    @Override
    public String getDescription() {
        return "Manage teams for shared chunk progression";
    }
}