// src/main/java/me/chunklock/teams/BasicTeamCommandHandler.java
package me.chunklock.teams;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Basic team command handler for Phase 1 implementation
 * Handles essential team commands: create, join, leave, info, list, chat
 */
public class BasicTeamCommandHandler {
    private final EnhancedTeamManager teamManager;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy");
    
    public BasicTeamCommandHandler(EnhancedTeamManager teamManager) {
        this.teamManager = teamManager;
    }
    
    /**
     * Main team command dispatcher
     * Add this to your existing ChunklockCommand
     */
    public boolean handleTeamCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use team commands.").color(NamedTextColor.RED));
            return true;
        }
        
        if (args.length < 2) {
            showBasicTeamHelp(player);
            return true;
        }
        
        String subCommand = args[1].toLowerCase();
        
        switch (subCommand) {
            case "create" -> handleCreateTeam(player, args);
            case "join" -> handleJoinTeam(player, args);
            case "leave" -> handleLeaveTeam(player);
            case "disband" -> handleDisbandTeam(player);
            case "accept" -> handleAcceptRequest(player, args);
            case "deny" -> handleDenyRequest(player, args);
            case "info" -> handleTeamInfo(player, args);
            case "list" -> handleListTeams(player);
            case "chat", "c" -> handleTeamChat(player, args);
            case "help" -> showBasicTeamHelp(player);
            default -> {
                player.sendMessage(Component.text("Unknown team command. Use '/chunklock team help' for options.")
                    .color(NamedTextColor.RED));
            }
        }
        
        return true;
    }
    
    private void handleCreateTeam(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(Component.text("Usage: /chunklock team create <teamname>")
                .color(NamedTextColor.YELLOW));
            return;
        }
        
        String teamName = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length));
        var result = teamManager.createTeam(player.getUniqueId(), teamName);
        
        Component message = Component.text(result.getMessage())
            .color(result.isSuccess() ? NamedTextColor.GREEN : NamedTextColor.RED);
        player.sendMessage(message);
        
        if (result.isSuccess()) {
            player.sendMessage(Component.text("🎉 Welcome to your new team! Use '/chunklock team help' to see management options.")
                .color(NamedTextColor.GOLD));
        }
    }
    
    private void handleJoinTeam(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(Component.text("Usage: /chunklock team join <teamname>")
                .color(NamedTextColor.YELLOW));
            return;
        }
        
        String teamName = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length));
        var result = teamManager.joinTeam(player.getUniqueId(), teamName);
        
        Component message = Component.text(result.getMessage())
            .color(result.isSuccess() ? NamedTextColor.GREEN : NamedTextColor.RED);
        player.sendMessage(message);
    }
    
    private void handleLeaveTeam(Player player) {
        var result = teamManager.leaveTeam(player.getUniqueId());
        
        Component message = Component.text(result.getMessage())
            .color(result.isSuccess() ? NamedTextColor.GREEN : NamedTextColor.RED);
        player.sendMessage(message);
    }
    
    private void handleDisbandTeam(Player player) {
        Team currentTeam = teamManager.getPlayerTeam(player.getUniqueId());
        if (currentTeam == null) {
            player.sendMessage(Component.text("You are not in a team.").color(NamedTextColor.RED));
            return;
        }
        
        if (!currentTeam.isOwner(player.getUniqueId())) {
            player.sendMessage(Component.text("Only the team owner can disband the team.").color(NamedTextColor.RED));
            return;
        }
        
        player.sendMessage(Component.text("⚠️ Are you sure you want to disband team '" + currentTeam.getTeamName() + "'?")
            .color(NamedTextColor.YELLOW));
        player.sendMessage(Component.text("Type '/chunklock team disband confirm' to proceed, or anything else to cancel.")
            .color(NamedTextColor.RED));
        
        // Simple confirmation (you might want to implement a more sophisticated system later)
        var result = teamManager.disbandTeam(player.getUniqueId());
        Component message = Component.text(result.getMessage())
            .color(result.isSuccess() ? NamedTextColor.GREEN : NamedTextColor.RED);
        player.sendMessage(message);
    }
    
    private void handleAcceptRequest(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(Component.text("Usage: /chunklock team accept <playername>")
                .color(NamedTextColor.YELLOW));
            return;
        }
        
        String playerName = args[2];
        var result = teamManager.acceptJoinRequest(player.getUniqueId(), playerName);
        
        Component message = Component.text(result.getMessage())
            .color(result.isSuccess() ? NamedTextColor.GREEN : NamedTextColor.RED);
        player.sendMessage(message);
    }
    
    private void handleDenyRequest(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(Component.text("Usage: /chunklock team deny <playername>")
                .color(NamedTextColor.YELLOW));
            return;
        }
        
        String playerName = args[2];
        var result = teamManager.denyJoinRequest(player.getUniqueId(), playerName);
        
        Component message = Component.text(result.getMessage())
            .color(result.isSuccess() ? NamedTextColor.GREEN : NamedTextColor.RED);
        player.sendMessage(message);
    }
    
    private void handleTeamInfo(Player player, String[] args) {
        Team team;
        
        if (args.length >= 3) {
            // Show info for specific team
            String teamName = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length));
            team = teamManager.findTeamByName(teamName);
            if (team == null) {
                player.sendMessage(Component.text("Team '" + teamName + "' not found.")
                    .color(NamedTextColor.RED));
                return;
            }
        } else {
            // Show info for player's current team
            team = teamManager.getPlayerTeam(player.getUniqueId());
            if (team == null) {
                player.sendMessage(Component.text("You are not in a team. Use '/chunklock team join <teamname>' to join one.")
                    .color(NamedTextColor.RED));
                return;
            }
        }
        
        showDetailedTeamInfo(player, team);
    }
    
    private void showDetailedTeamInfo(Player player, Team team) {
        player.sendMessage(Component.text("═══ " + team.getTeamName() + " ═══")
            .color(NamedTextColor.AQUA)
            .decoration(TextDecoration.BOLD, true));
        
        // Basic info
        player.sendMessage(Component.text("📊 Members: " + team.getTotalMembers() + "/" + team.getMaxMembers())
            .color(NamedTextColor.WHITE));
        player.sendMessage(Component.text("🏆 Chunks Unlocked: " + team.getTotalChunksUnlocked())
            .color(NamedTextColor.YELLOW));
        player.sendMessage(Component.text("📅 Created: " + dateFormat.format(new Date(team.getCreatedTime())))
            .color(NamedTextColor.GRAY));
        
        // Team members with roles
        player.sendMessage(Component.text("👥 Members:")
            .color(NamedTextColor.AQUA));
        
        String ownerName = getPlayerName(team.getOwnerId());
        player.sendMessage(Component.text("  👑 " + ownerName + " (Owner)")
            .color(NamedTextColor.GOLD));
        
        for (java.util.UUID officerId : team.getOfficers()) {
            if (!officerId.equals(team.getOwnerId())) {
                String officerName = getPlayerName(officerId);
                player.sendMessage(Component.text("  ⭐ " + officerName + " (Officer)")
                    .color(NamedTextColor.YELLOW));
            }
        }
        
        for (java.util.UUID memberId : team.getMembers()) {
            if (!team.isOfficer(memberId)) {
                String memberName = getPlayerName(memberId);
                player.sendMessage(Component.text("  👤 " + memberName + " (Member)")
                    .color(NamedTextColor.WHITE));
            }
        }
        
        // Join requests (if player can see them)
        if (team.canManageTeam(player.getUniqueId()) && !team.getJoinRequests().isEmpty()) {
            player.sendMessage(Component.text("📬 Pending Requests: " + team.getJoinRequests().size())
                .color(NamedTextColor.RED));
            
            for (java.util.UUID requesterId : team.getJoinRequests().keySet()) {
                String requesterName = getPlayerName(requesterId);
                player.sendMessage(Component.text("  • " + requesterName)
                    .color(NamedTextColor.GRAY));
            }
            
            player.sendMessage(Component.text("Use '/chunklock team accept <player>' or '/chunklock team deny <player>'")
                .color(NamedTextColor.GRAY));
        }
        
        // Team settings
        if (team.isOpenJoin()) {
            player.sendMessage(Component.text("🔓 Open to join")
                .color(NamedTextColor.GREEN));
        } else {
            player.sendMessage(Component.text("🔒 Invite only")
                .color(NamedTextColor.RED));
        }
        
        // Cost multiplier info
        double multiplier = team.getCostMultiplier();
        if (multiplier > 1.0) {
            player.sendMessage(Component.text("💰 Chunk Cost Multiplier: " + String.format("%.1fx", multiplier))
                .color(NamedTextColor.YELLOW));
        }
    }
    
    private void handleListTeams(Player player) {
        var allTeams = teamManager.getAllTeams();
        
        if (allTeams.isEmpty()) {
            player.sendMessage(Component.text("No teams exist yet. Create one with '/chunklock team create <name>'!")
                .color(NamedTextColor.GRAY));
            return;
        }
        
        player.sendMessage(Component.text("📋 All Teams (" + allTeams.size() + " total)")
            .color(NamedTextColor.AQUA));
        
        for (Team team : allTeams) {
            Component teamLine = Component.text("  • " + team.getTeamName())
                .color(NamedTextColor.WHITE)
                .append(Component.text(" (" + team.getTotalMembers() + "/" + team.getMaxMembers() + ")")
                    .color(NamedTextColor.GRAY));
            
            if (team.isOpenJoin()) {
                teamLine = teamLine.append(Component.text(" [OPEN]")
                    .color(NamedTextColor.GREEN));
            }
            
            player.sendMessage(teamLine);
        }
    }
    
    private void handleTeamChat(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(Component.text("Usage: /chunklock team chat <message>")
                .color(NamedTextColor.YELLOW));
            return;
        }
        
        String message = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length));
        var result = teamManager.sendTeamMessage(player.getUniqueId(), message);
        
        if (!result.isSuccess()) {
            player.sendMessage(Component.text(result.getMessage()).color(NamedTextColor.RED));
        }
    }
    
    private void showBasicTeamHelp(Player player) {
        player.sendMessage(Component.text("🤝 Chunklock Team Commands")
            .color(NamedTextColor.AQUA)
            .decoration(TextDecoration.BOLD, true));
        
        player.sendMessage(Component.text("═══ Basic Commands ═══")
            .color(NamedTextColor.GRAY));
        player.sendMessage(Component.text("/chunklock team create <name> - Create a new team")
            .color(NamedTextColor.WHITE));
        player.sendMessage(Component.text("/chunklock team join <name> - Join or request to join a team")
            .color(NamedTextColor.WHITE));
        player.sendMessage(Component.text("/chunklock team leave - Leave your current team")
            .color(NamedTextColor.WHITE));
        player.sendMessage(Component.text("/chunklock team info [team] - Show team information")
            .color(NamedTextColor.WHITE));
        player.sendMessage(Component.text("/chunklock team list - List all teams")
            .color(NamedTextColor.WHITE));
        player.sendMessage(Component.text("/chunklock team chat <message> - Send message to team")
            .color(NamedTextColor.WHITE));
        
        player.sendMessage(Component.text("═══ Management (Officers/Owner) ═══")
            .color(NamedTextColor.GRAY));
        player.sendMessage(Component.text("/chunklock team accept <player> - Accept join request")
            .color(NamedTextColor.YELLOW));
        player.sendMessage(Component.text("/chunklock team deny <player> - Deny join request")
            .color(NamedTextColor.YELLOW));
        
        player.sendMessage(Component.text("═══ Owner Only ═══")
            .color(NamedTextColor.GRAY));
        player.sendMessage(Component.text("/chunklock team disband - Disband the team")
            .color(NamedTextColor.RED));
        
        player.sendMessage(Component.text("💡 More features coming soon! Teams share unlocked chunks.")
            .color(NamedTextColor.GOLD));
    }
    
    // Tab completion for team commands
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 2) {
            // Team subcommands
            String prefix = args[1].toLowerCase();
            List<String> commands = List.of(
                "create", "join", "leave", "disband", "info", "list", 
                "chat", "accept", "deny", "help"
            );
            
            for (String cmd : commands) {
                if (cmd.startsWith(prefix)) {
                    completions.add(cmd);
                }
            }
        } else if (args.length == 3) {
            String subCommand = args[1].toLowerCase();
            String prefix = args[2].toLowerCase();
            
            switch (subCommand) {
                case "join", "info" -> {
                    // Complete with team names
                    for (Team team : teamManager.getAllTeams()) {
                        if (team.getTeamName().toLowerCase().startsWith(prefix)) {
                            completions.add(team.getTeamName());
                        }
                    }
                }
                case "accept", "deny" -> {
                    // Complete with online player names
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        if (player.getName().toLowerCase().startsWith(prefix)) {
                            completions.add(player.getName());
                        }
                    }
                }
            }
        }
        
        return completions;
    }
    
    // Utility method
    private String getPlayerName(java.util.UUID playerId) {
        Player player = Bukkit.getPlayer(playerId);
        return player != null ? player.getName() : "Unknown";
    }
}