package com.ninja.islandwallet.commands;

import com.ninja.islandwallet.IslandWalletPlugin;
import com.ninja.islandwallet.models.IslandData;
import com.ninja.islandwallet.models.PayoutWinner;
import com.ninja.islandwallet.utils.MessageUtil;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ENHANCED command handler with fixed placeholders and full color support
 */
public class IslandWalletCommand implements CommandExecutor, TabCompleter {

    private final IslandWalletPlugin plugin;
    private final Map<String, Long> commandCooldowns;

    public IslandWalletCommand(IslandWalletPlugin plugin) {
        this.plugin = plugin;
        this.commandCooldowns = new ConcurrentHashMap<>();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // ENHANCED: Handle payout command
        if (command.getName().equalsIgnoreCase("payout")) {
            return handlePayoutCommand(sender);
        }

        // Handle wallet command
        if (args.length == 0) {
            sendHelpMessage(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        return switch (subCommand) {
            case "balance", "bal", "get" -> handleBalanceCommand(sender, args);
            case "purchase", "buy" -> handlePurchaseCommand(sender);
            case "podium", "winners" -> handlePodiumCommand(sender, args);
            case "info", "status" -> handleInfoCommand(sender);
            case "time", "countdown" -> handleTimeCommand(sender);
            case "admin" -> handleAdminCommand(sender, args);
            case "help" -> {
                sendHelpMessage(sender);
                yield true;
            }
            default -> {
                String message = plugin.getConfigManager().getMessage("invalid-command");
                sender.sendMessage(plugin.getConfigManager().getPrefix() + message);
                yield true;
            }
        };
    }

    /**
     * Handle payout command (opens leaderboard with purchase access)
     */
    private boolean handlePayoutCommand(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getConfigManager().getMessage("player-only"));
            return true;
        }

        if (!player.hasPermission("islandwallet.leaderboard")) {
            player.sendMessage(plugin.getConfigManager().getPrefix() +
                    plugin.getConfigManager().getMessage("no-permission"));
            return true;
        }

        if (isOnCooldown(player, "leaderboard")) {
            long remainingTime = getRemainingCooldown(player, "leaderboard");
            String message = MessageUtil.replacePlaceholders(
                    plugin.getConfigManager().getMessage("command-cooldown"),
                    "{seconds}", String.valueOf(remainingTime));
            player.sendMessage(plugin.getConfigManager().getPrefix() + message);
            return true;
        }

        // Open enhanced leaderboard GUI
        plugin.getGuiManager().openLeaderboardGui(player);

        setCooldown(player, "leaderboard");
        return true;
    }

    /**
     * ENHANCED: Handle balance command with fixed placeholders and money display
     */
    private boolean handleBalanceCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getConfigManager().getMessage("player-only"));
            return true;
        }

        // Handle /wallet get <player> command for checking other players
        if (args.length >= 2 && args[0].equalsIgnoreCase("get")) {
            if (!player.hasPermission("islandwallet.admin.view")) {
                player.sendMessage(plugin.getConfigManager().getPrefix() +
                        plugin.getConfigManager().getMessage("no-permission"));
                return true;
            }

            Player targetPlayer = Bukkit.getPlayer(args[1]);
            if (targetPlayer == null) {
                String message = MessageUtil.replacePlaceholders(
                        plugin.getConfigManager().getMessage("player-not-found"),
                        "{player}", args[1]);
                player.sendMessage(plugin.getConfigManager().getPrefix() + message);
                return true;
            }

            IslandData targetIslandData = plugin.getWalletManager().getPlayerIslandData(targetPlayer);
            if (targetIslandData == null) {
                player.sendMessage(plugin.getConfigManager().getPrefix() +
                        plugin.getConfigManager().getMessage("no-island"));
                return true;
            }

            String message = MessageUtil.replacePlaceholders(
                    plugin.getConfigManager().getMessage("admin-balance-check"),
                    .replace("{points}", String.valueOf(points))
                    .replace("{cost}", MessageUtil.formatMoney(cost))
                    .replace("{cost_formatted}", MessageUtil.formatMoney(cost));
            player.sendMessage(plugin.getConfigManager().getPrefix() + message);

            return true;
        }

        if (!player.hasPermission("islandwallet.balance")) {
            player.sendMessage(plugin.getConfigManager().getPrefix() +
                    plugin.getConfigManager().getMessage("no-permission"));
            return true;
        }

        if (isOnCooldown(player, "balance")) {
            long remainingTime = getRemainingCooldown(player, "balance");
            String message = MessageUtil.replacePlaceholders(
                    plugin.getConfigManager().getMessage("command-cooldown"),
                    "{seconds}", String.valueOf(remainingTime));
            player.sendMessage(plugin.getConfigManager().getPrefix() + message);
            return true;
        }

        IslandData islandData = plugin.getWalletManager().getPlayerIslandData(player);

        if (islandData == null) {
            player.sendMessage(plugin.getConfigManager().getPrefix() +
                    plugin.getConfigManager().getMessage("no-island"));
            return true;
        }

        // Display enhanced balance information with money info
        sendEnhancedBalanceInfo(player, islandData);

        setCooldown(player, "balance");
        return true;
    }

    /**
     * Handle purchase command
     */
    private boolean handlePurchaseCommand(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getConfigManager().getMessage("player-only"));
            return true;
        }

        if (!player.hasPermission("islandwallet.purchase")) {
            player.sendMessage(plugin.getConfigManager().getPrefix() +
                    plugin.getConfigManager().getMessage("no-permission"));
            return true;
        }

        if (isOnCooldown(player, "purchase")) {
            long remainingTime = getRemainingCooldown(player, "purchase");
            String message = MessageUtil.replacePlaceholders(
                    plugin.getConfigManager().getMessage("command-cooldown"),
                    "{seconds}", String.valueOf(remainingTime));
            player.sendMessage(plugin.getConfigManager().getPrefix() + message);
            return true;
        }

        IslandData islandData = plugin.getWalletManager().getPlayerIslandData(player);
        if (islandData == null) {
            player.sendMessage(plugin.getConfigManager().getPrefix() +
                    plugin.getConfigManager().getMessage("no-island"));
            return true;
        }

        plugin.getGuiManager().openPurchaseGui(player);

        setCooldown(player, "purchase");
        return true;
    }

    /**
     * ENHANCED: Handle podium command (NEW)
     */
    private boolean handlePodiumCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getConfigManager().getMessage("player-only"));
            return true;
        }

        if (!player.hasPermission("islandwallet.leaderboard")) {
            player.sendMessage(plugin.getConfigManager().getPrefix() +
                    plugin.getConfigManager().getMessage("no-permission"));
            return true;
        }

        // Determine season to display
        int season;
        if (args.length >= 2) {
            try {
                season = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                player.sendMessage(plugin.getConfigManager().getPrefix() + "&cInvalid season number!");
                return true;
            }
        } else {
            // Default to current or last season
            int currentSeason = plugin.getDatabaseManager().getCurrentSeason();
            season = currentSeason > 1 ? currentSeason - 1 : currentSeason;
        }

        plugin.getGuiManager().openPodiumGui(player, season);
        return true;
    }

    /**
     * ENHANCED: Handle info command (shows plugin status and statistics)
     */
    private boolean handleInfoCommand(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getConfigManager().getMessage("player-only"));
            return true;
        }

        if (!player.hasPermission("islandwallet.info")) {
            player.sendMessage(plugin.getConfigManager().getPrefix() +
                    plugin.getConfigManager().getMessage("no-permission"));
            return true;
        }

        sendPluginInfo(player);
        return true;
    }

    /**
     * ENHANCED: Handle time command (shows countdown to next payout)
     */
    private boolean handleTimeCommand(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getConfigManager().getMessage("player-only"));
            return true;
        }

        if (!player.hasPermission("islandwallet.time")) {
            player.sendMessage(plugin.getConfigManager().getPrefix() +
                    plugin.getConfigManager().getMessage("no-permission"));
            return true;
        }

        sendTimeInfo(player);
        return true;
    }

    /**
     * Handle admin commands
     */
    private boolean handleAdminCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("islandwallet.admin.*")) {
            sender.sendMessage(plugin.getConfigManager().getPrefix() +
                    plugin.getConfigManager().getMessage("no-permission"));
            return true;
        }

        if (args.length < 2) {
            sendAdminHelpMessage(sender);
            return true;
        }

        String adminSubCommand = args[1].toLowerCase();

        return switch (adminSubCommand) {
            case "reset" -> handleAdminReset(sender);
            case "force" -> handleAdminForce(sender, args);
            case "view" -> handleAdminView(sender, args);
            case "history" -> handleAdminHistory(sender, args);
            case "reload" -> handleAdminReload(sender);
            case "deposit" -> handleAdminDeposit(sender, args);
            case "withdraw" -> handleAdminWithdraw(sender, args);
            case "stats" -> handleAdminStats(sender);
            case "backup" -> handleAdminBackup(sender);
            case "migrate" -> handleAdminMigrate(sender, args);
            case "cleanup" -> handleAdminCleanup(sender);
            case "test" -> handleAdminTest(sender, args);
            default -> {
                sendAdminHelpMessage(sender);
                yield true;
            }
        };
    }

    /**
     * Handle admin force command
     */
    private boolean handleAdminForce(CommandSender sender, String[] args) {
        if (!sender.hasPermission("islandwallet.admin.force")) {
            sender.sendMessage(plugin.getConfigManager().getPrefix() +
                    plugin.getConfigManager().getMessage("no-permission"));
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage(plugin.getConfigManager().getPrefix() +
                    "&cUsage: /wallet admin force <complete|shutdown>");
            return true;
        }

        String forceType = args[2].toLowerCase();

        switch (forceType) {
            case "complete" -> {
                plugin.getPayoutManager().forceCompletePayout();
                sender.sendMessage(plugin.getConfigManager().getPrefix() +
                        plugin.getConfigManager().getMessage("payout-force-complete"));
            }
            case "shutdown" -> {
                plugin.getPayoutManager().forceShutdownPayout();
                sender.sendMessage(plugin.getConfigManager().getPrefix() +
                        plugin.getConfigManager().getMessage("payout-force-shutdown"));
            }
            default -> {
                sender.sendMessage(plugin.getConfigManager().getPrefix() +
                        "&cUsage: /wallet admin force <complete|shutdown>");
            }
        }

        return true;
    }

    /**
     * ENHANCED: Handle admin stats command
     */
    private boolean handleAdminStats(CommandSender sender) {
        if (!sender.hasPermission("islandwallet.admin.stats")) {
            sender.sendMessage(plugin.getConfigManager().getPrefix() +
                    plugin.getConfigManager().getMessage("no-permission"));
            return true;
        }

        sendServerStats(sender);
        return true;
    }

    /**
     * ENHANCED: Handle admin backup command
     */
    private boolean handleAdminBackup(CommandSender sender) {
        if (!sender.hasPermission("islandwallet.admin.backup")) {
            sender.sendMessage(plugin.getConfigManager().getPrefix() +
                    plugin.getConfigManager().getMessage("no-permission"));
            return true;
        }

        // Trigger backup operation
        plugin.getWalletManager().createBackup();
        String message = plugin.getConfigManager().getMessage("admin-backup-success");
        sender.sendMessage(plugin.getConfigManager().getPrefix() + message);
        return true;
    }

    /**
     * ENHANCED: Handle admin migrate command
     */
    private boolean handleAdminMigrate(CommandSender sender, String[] args) {
        if (!sender.hasPermission("islandwallet.admin.migrate")) {
            sender.sendMessage(plugin.getConfigManager().getPrefix() +
                    plugin.getConfigManager().getMessage("no-permission"));
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage(plugin.getConfigManager().getPrefix() +
                    "&cUsage: /wallet admin migrate <yaml|sqlite>");
            return true;
        }

        String targetType = args[2].toLowerCase();
        if (!targetType.equals("yaml") && !targetType.equals("sqlite")) {
            sender.sendMessage(plugin.getConfigManager().getPrefix() +
                    "&cInvalid storage type! Use 'yaml' or 'sqlite'");
            return true;
        }

        // Trigger migration
        boolean success = plugin.getWalletManager().migrateStorage(targetType);
        if (success) {
            String message = plugin.getConfigManager().getMessage("admin-migrate-success")
                    .replace("{type}", targetType);
            sender.sendMessage(plugin.getConfigManager().getPrefix() + message);
        } else {
            String message = plugin.getConfigManager().getMessage("admin-migrate-failed");
            sender.sendMessage(plugin.getConfigManager().getPrefix() + message);
        }
        return true;
    }

    /**
     * ENHANCED: Handle admin cleanup command
     */
    private boolean handleAdminCleanup(CommandSender sender) {
        if (!sender.hasPermission("islandwallet.admin.cleanup")) {
            sender.sendMessage(plugin.getConfigManager().getPrefix() +
                    plugin.getConfigManager().getMessage("no-permission"));
            return true;
        }

        // Trigger cleanup operation
        plugin.getWalletManager().cleanupInvalidData();
        String message = plugin.getConfigManager().getMessage("admin-cleanup-success");
        sender.sendMessage(plugin.getConfigManager().getPrefix() + message);
        return true;
    }

    /**
     * ENHANCED: Handle admin test command
     */
    private boolean handleAdminTest(CommandSender sender, String[] args) {
        if (!sender.hasPermission("islandwallet.admin.test")) {
            sender.sendMessage(plugin.getConfigManager().getPrefix() +
                    plugin.getConfigManager().getMessage("no-permission"));
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage(plugin.getConfigManager().getPrefix() +
                    "&cUsage: /wallet admin test <database|economy|placeholders>");
            return true;
        }

        String testType = args[2].toLowerCase();
        switch (testType) {
            case "database" -> {
                boolean dbTest = plugin.getWalletManager().testDatabaseConnection();
                String message = dbTest ? 
                    plugin.getConfigManager().getMessage("admin-test-database-success") :
                    plugin.getConfigManager().getMessage("admin-test-database-failed");
                sender.sendMessage(plugin.getConfigManager().getPrefix() + message);
            }
            case "economy" -> {
                boolean ecoTest = plugin.getEconomy() != null;
                String message = ecoTest ? 
                    plugin.getConfigManager().getMessage("admin-test-economy-success") :
                    plugin.getConfigManager().getMessage("admin-test-economy-failed");
                sender.sendMessage(plugin.getConfigManager().getPrefix() + message);
            }
            case "placeholders" -> {
                boolean placeholderTest = plugin.getPlaceholderAPI() != null;
                String message = placeholderTest ? 
                    plugin.getConfigManager().getMessage("admin-test-placeholders-success") :
                    plugin.getConfigManager().getMessage("admin-test-placeholders-failed");
                sender.sendMessage(plugin.getConfigManager().getPrefix() + message);
            }
            default -> {
                sender.sendMessage(plugin.getConfigManager().getPrefix() +
                        "&cInvalid test type! Use: database, economy, or placeholders");
            }
        }
        return true;
    }

    /**
     * ENHANCED: Handle admin deposit command with fixed placeholders
     */
    private boolean handleAdminDeposit(CommandSender sender, String[] args) {
        if (!sender.hasPermission("islandwallet.admin.deposit")) {
            sender.sendMessage(plugin.getConfigManager().getPrefix() +
                    plugin.getConfigManager().getMessage("no-permission"));
            return true;
        }

        if (args.length < 4) {
            sender.sendMessage(plugin.getConfigManager().getPrefix() +
                    "&cUsage: /wallet admin deposit <player> <amount>");
            return true;
        }

        Player targetPlayer = Bukkit.getPlayer(args[2]);
        if (targetPlayer == null) {
            String message = MessageUtil.replacePlaceholders(
                    plugin.getConfigManager().getMessage("player-not-found"),
                    "{player}", args[2]);
            sender.sendMessage(plugin.getConfigManager().getPrefix() + message);
            return true;
        }

        long amount;
        try {
            amount = Long.parseLong(args[3]);
        } catch (NumberFormatException e) {
            sender.sendMessage(plugin.getConfigManager().getPrefix() +
                    "&cInvalid amount! Please enter a valid number.");
            return true;
        }

        if (amount <= 0) {
            sender.sendMessage(plugin.getConfigManager().getPrefix() +
                    "&cAmount must be greater than 0!");
            return true;
        }

        boolean success = plugin.getWalletManager().adminDepositGems(targetPlayer, amount);
        if (success) {
            String message = MessageUtil.replacePlaceholders(
                    plugin.getConfigManager().getMessage("admin-deposit-success"),
                    "{amount}", MessageUtil.formatNumber(amount),
                    "{player}", targetPlayer.getName());
            sender.sendMessage(plugin.getConfigManager().getPrefix() + message);
        } else {
            sender.sendMessage(plugin.getConfigManager().getPrefix() +
                    plugin.getConfigManager().getMessage("no-island"));
        }

        return true;
    }

    /**
     * ENHANCED: Handle admin withdraw command with fixed placeholders
     */
    private boolean handleAdminWithdraw(CommandSender sender, String[] args) {
        if (!sender.hasPermission("islandwallet.admin.withdraw")) {
            sender.sendMessage(plugin.getConfigManager().getPrefix() +
                    plugin.getConfigManager().getMessage("no-permission"));
            return true;
        }

        if (args.length < 4) {
            sender.sendMessage(plugin.getConfigManager().getPrefix() +
                    "&cUsage: /wallet admin withdraw <player> <amount>");
            return true;
        }

        Player targetPlayer = Bukkit.getPlayer(args[2]);
        if (targetPlayer == null) {
            String message = MessageUtil.replacePlaceholders(
                    plugin.getConfigManager().getMessage("player-not-found"),
                    "{player}", args[2]);
            sender.sendMessage(plugin.getConfigManager().getPrefix() + message);
            return true;
        }

        long amount;
        try {
            amount = Long.parseLong(args[3]);
        } catch (NumberFormatException e) {
            sender.sendMessage(plugin.getConfigManager().getPrefix() +
                    "&cInvalid amount! Please enter a valid number.");
            return true;
        }

        if (amount <= 0) {
            sender.sendMessage(plugin.getConfigManager().getPrefix() +
                    "&cAmount must be greater than 0!");
            return true;
        }

        boolean success = plugin.getWalletManager().adminWithdrawGems(targetPlayer, amount);
        if (success) {
            String message = MessageUtil.replacePlaceholders(
                    plugin.getConfigManager().getMessage("admin-withdraw-success"),
                    "{amount}", MessageUtil.formatNumber(amount),
                    "{player}", targetPlayer.getName());
            sender.sendMessage(plugin.getConfigManager().getPrefix() + message);
        } else {
            sender.sendMessage(plugin.getConfigManager().getPrefix() +
                    plugin.getConfigManager().getMessage("insufficient-gems"));
        }

        return true;
    }

    /**
     * Handle admin reset command
     */
    private boolean handleAdminReset(CommandSender sender) {
        if (!sender.hasPermission("islandwallet.admin.reset")) {
            sender.sendMessage(plugin.getConfigManager().getPrefix() +
                    plugin.getConfigManager().getMessage("no-permission"));
            return true;
        }

        plugin.getPayoutManager().forceResetCycle();
        sender.sendMessage(plugin.getConfigManager().getPrefix() +
                plugin.getConfigManager().getMessage("admin-reset-success"));

        return true;
    }

    /**
     * ENHANCED: Handle admin view command with fixed placeholders
     */
    private boolean handleAdminView(CommandSender sender, String[] args) {
        if (!sender.hasPermission("islandwallet.admin.view")) {
            sender.sendMessage(plugin.getConfigManager().getPrefix() +
                    plugin.getConfigManager().getMessage("no-permission"));
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage(plugin.getConfigManager().getPrefix() +
                    "&cUsage: /wallet admin view <island-id>");
            return true;
        }

        String islandId = args[2];
        IslandData islandData = plugin.getWalletManager().getIslandData(islandId);

        if (islandData == null) {
            String message = MessageUtil.replacePlaceholders(
                    plugin.getConfigManager().getMessage("island-not-found"),
                    "{island}", islandId);
            sender.sendMessage(plugin.getConfigManager().getPrefix() + message);
            return true;
        }

        sendIslandInfo(sender, islandData);
        return true;
    }

    /**
     * Handle admin history command
     */
    private boolean handleAdminHistory(CommandSender sender, String[] args) {
        if (!sender.hasPermission("islandwallet.admin.history")) {
            sender.sendMessage(plugin.getConfigManager().getPrefix() +
                    plugin.getConfigManager().getMessage("no-permission"));
            return true;
        }

        int season = -1;
        if (args.length >= 3) {
            try {
                season = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                sender.sendMessage(plugin.getConfigManager().getPrefix() +
                        "&cInvalid season number!");
                return true;
            }
        }

        List<PayoutWinner> winners;
        if (season > 0) {
            winners = plugin.getDatabaseManager().loadPayoutWinners(season);
        } else {
            winners = plugin.getDatabaseManager().loadAllPayoutWinners();
        }

        if (winners.isEmpty()) {
            sender.sendMessage(plugin.getConfigManager().getPrefix() +
                    "&cNo historical payout data found!");
            return true;
        }

        sendHistoryInfo(sender, winners, season);
        return true;
    }

    /**
     * Handle admin reload command
     */
    private boolean handleAdminReload(CommandSender sender) {
        if (!sender.hasPermission("islandwallet.admin.reload")) {
            sender.sendMessage(plugin.getConfigManager().getPrefix() +
                    plugin.getConfigManager().getMessage("no-permission"));
            return true;
        }

        plugin.reloadPlugin();
        sender.sendMessage(plugin.getConfigManager().getPrefix() +
                plugin.getConfigManager().getMessage("admin-reload-success"));

        return true;
    }

    /**
     * ENHANCED: Send balance information with fixed placeholders and money info
     */
    private void sendEnhancedBalanceInfo(Player player, IslandData islandData) {
        String prefix = plugin.getConfigManager().getPrefix();

        player.sendMessage(prefix + plugin.getConfigManager().getMessage("balance-display"));

        String gemsMessage = MessageUtil.replacePlaceholders(
                plugin.getConfigManager().getMessage("balance-gems"),
                "{gems_formatted}", MessageUtil.formatNumber(islandData.getGems()));
        player.sendMessage(prefix + gemsMessage);

        String payoutMessage = MessageUtil.replacePlaceholders(
                plugin.getConfigManager().getMessage("balance-payout-points"),
                "{payout_points_formatted}", MessageUtil.formatNumber(islandData.getPayoutPoints()));
        player.sendMessage(prefix + payoutMessage);

        // Show rank if available
        int rank = plugin.getLeaderboardManager().getIslandRank(islandData);
        if (rank > 0) {
            player.sendMessage(prefix + "&7Current Leaderboard Rank: &e#" + rank);
        }

        // Show point purchase cost in money
        double pointCost = plugin.getConfigManager().getPointCostMoney();
        String costMessage = plugin.getConfigManager().getMessage("balance-point-cost")
                .replace("{cost}", MessageUtil.formatMoney(pointCost))
                .replace("{cost_formatted}", MessageUtil.formatMoney(pointCost));
        player.sendMessage(prefix + costMessage);

        // Show player's money balance
        Economy economy = plugin.getEconomy();
        if (economy != null) {
            double balance = economy.getBalance(player);
            String balanceMessage = plugin.getConfigManager().getMessage("balance-money")
                    .replace("{balance}", MessageUtil.formatMoney(balance))
                    .replace("{balance_formatted}", MessageUtil.formatMoney(balance));
            player.sendMessage(prefix + balanceMessage);
        }
    }

    /**
     * ENHANCED: Send plugin information
     */
    private void sendPluginInfo(Player player) {
        String prefix = plugin.getConfigManager().getPrefix();
        
        player.sendMessage(prefix + plugin.getConfigManager().getMessage("info-header"));
        
        // Plugin version
        String versionMessage = plugin.getConfigManager().getMessage("info-version")
                .replace("{version}", plugin.getDescription().getVersion());
        player.sendMessage(prefix + versionMessage);
        
        // Current season
        String seasonMessage = plugin.getConfigManager().getMessage("info-season")
                .replace("{season}", String.valueOf(plugin.getDatabaseManager().getCurrentSeason()));
        player.sendMessage(prefix + seasonMessage);
        
        // Total islands
        String islandsMessage = plugin.getConfigManager().getMessage("info-total-islands")
                .replace("{count}", String.valueOf(plugin.getLeaderboardManager().getTotalParticipatingIslands()));
        player.sendMessage(prefix + islandsMessage);
        
        // Storage type
        String storageMessage = plugin.getConfigManager().getMessage("info-storage")
                .replace("{type}", plugin.getConfigManager().getStorageType().toUpperCase());
        player.sendMessage(prefix + storageMessage);
    }

    /**
     * ENHANCED: Send time information with multiple formats
     */
    private void sendTimeInfo(Player player) {
        String prefix = plugin.getConfigManager().getPrefix();
        long timeRemaining = plugin.getPayoutManager().getTimeUntilNextPayout();
        
        player.sendMessage(prefix + plugin.getConfigManager().getMessage("time-header"));
        
        // Multiple time formats
        String compactMessage = plugin.getConfigManager().getMessage("time-compact")
                .replace("{time}", MessageUtil.formatTimeRemainingCompact(timeRemaining));
        player.sendMessage(prefix + compactMessage);
        
        String shortMessage = plugin.getConfigManager().getMessage("time-short")
                .replace("{time}", MessageUtil.formatTimeRemainingShort(timeRemaining));
        player.sendMessage(prefix + shortMessage);
        
        String dhmMessage = plugin.getConfigManager().getMessage("time-dhm")
                .replace("{time}", MessageUtil.formatTimeRemainingDHM(timeRemaining));
        player.sendMessage(prefix + dhmMessage);
    }

    /**
     * ENHANCED: Send server statistics
     */
    private void sendServerStats(CommandSender sender) {
        String prefix = plugin.getConfigManager().getPrefix();
        
        sender.sendMessage(prefix + plugin.getConfigManager().getMessage("stats-header"));
        
        // Total gems across all islands
        long totalGems = plugin.getWalletManager().getAllIslandData().values().stream()
                .mapToLong(IslandData::getGems)
                .sum();
        String gemsMessage = plugin.getConfigManager().getMessage("stats-total-gems")
                .replace("{gems}", MessageUtil.formatNumber(totalGems));
        sender.sendMessage(prefix + gemsMessage);
        
        // Total payout points
        long totalPoints = plugin.getLeaderboardManager().getTotalPoints();
        String pointsMessage = plugin.getConfigManager().getMessage("stats-total-points")
                .replace("{points}", MessageUtil.formatNumber(totalPoints));
        sender.sendMessage(prefix + pointsMessage);
        
        // Database type
        String dbMessage = plugin.getConfigManager().getMessage("stats-database")
                .replace("{type}", plugin.getConfigManager().getStorageType().toUpperCase());
        sender.sendMessage(prefix + dbMessage);
        
        // Current season
        String seasonMessage = plugin.getConfigManager().getMessage("stats-season")
                .replace("{season}", String.valueOf(plugin.getDatabaseManager().getCurrentSeason()));
        sender.sendMessage(prefix + seasonMessage);
    }

    /**
     * ENHANCED: Send island information with fixed placeholders
     */
    private void sendIslandInfo(CommandSender sender, IslandData islandData) {
        String prefix = plugin.getConfigManager().getPrefix();

        sender.sendMessage(prefix + "&aIsland Information:");
        sender.sendMessage(prefix + "&7Island ID: &f" + islandData.getIslandId());
        sender.sendMessage(prefix + "&7Island Name: &f" + islandData.getIslandName());
        sender.sendMessage(prefix + "&7Leader: &f" + (islandData.getLeader() != null ? islandData.getLeader() : "None"));
        sender.sendMessage(prefix + "&7Admin: &f" + (islandData.getAdmin() != null ? islandData.getAdmin() : "None"));
        sender.sendMessage(prefix + "&7Members: &f" + islandData.getMemberCount());
        sender.sendMessage(prefix + "&7Gems: &e" + MessageUtil.formatNumber(islandData.getGems()));
        sender.sendMessage(prefix + "&7Payout Points: &b" + MessageUtil.formatNumber(islandData.getPayoutPoints()));

        int rank = plugin.getLeaderboardManager().getIslandRank(islandData);
        sender.sendMessage(prefix + "&7Current Rank: &e" + (rank > 0 ? "#" + rank : "Unranked"));
    }

    /**
     * Send historical payout information with enhanced formatting
     */
    private void sendHistoryInfo(CommandSender sender, List<PayoutWinner> winners, int season) {
        String prefix = plugin.getConfigManager().getPrefix();

        if (season > 0) {
            sender.sendMessage(prefix + "&aSeason " + season + " Winners:");
        } else {
            sender.sendMessage(prefix + "&aAll Historical Winners:");
        }

        int currentSeason = -1;
        for (PayoutWinner winner : winners) {
            if (season <= 0 && winner.getSeason() != currentSeason) {
                currentSeason = winner.getSeason();
                plugin.getLogger().info(player.getName() + " purchased " + points + " payout points for " + MessageUtil.formatMoney(cost));
            }

            String winnerInfo = String.format("&7#%d &e%s &7(Leader: %s) &b%s points",
                    winner.getRank(),
                    winner.getIslandName(),
                    winner.getLeader(),
                    MessageUtil.formatNumber(winner.getPoints())
            );

            sender.sendMessage(prefix + MessageUtil.translateColors(winnerInfo));
        }
    }

    /**
     * ENHANCED: Send help message with new commands and full color support
     */
    private void sendHelpMessage(CommandSender sender) {
        String prefix = plugin.getConfigManager().getPrefix();

        sender.sendMessage(prefix + "&6&lIsland Wallet Commands:");
        sender.sendMessage(prefix + "&e/wallet balance &7- View island gem balance and rank");
        sender.sendMessage(prefix + "&e/wallet purchase &7- Open point purchase menu (uses money)");
        sender.sendMessage(prefix + "&e/wallet podium [season] &7- View winners podium");
        sender.sendMessage(prefix + "&e/wallet info &7- View plugin information and statistics");
        sender.sendMessage(prefix + "&e/wallet time &7- View countdown to next payout");
        sender.sendMessage(prefix + "&e/wallet get <player> &7- Check player's gems (admin)");
        sender.sendMessage(prefix + "&e/payout &7- View payout leaderboard");

        if (sender.hasPermission("islandwallet.admin.*")) {
            sender.sendMessage(prefix + "&c&lAdmin Commands:");
            sender.sendMessage(prefix + "&c/wallet admin reset &7- Force reset payout cycle");
            sender.sendMessage(prefix + "&c/wallet admin force <complete|shutdown> &7- Force payout control");
            sender.sendMessage(prefix + "&c/wallet admin deposit <player> <amount> &7- Deposit gems");
            sender.sendMessage(prefix + "&c/wallet admin withdraw <player> <amount> &7- Withdraw gems");
            sender.sendMessage(prefix + "&c/wallet admin view <island> &7- View island data");
            sender.sendMessage(prefix + "&c/wallet admin history [season] &7- View payout history");
            sender.sendMessage(prefix + "&c/wallet admin stats &7- View server statistics");
            sender.sendMessage(prefix + "&c/wallet admin backup &7- Create data backup");
            sender.sendMessage(prefix + "&c/wallet admin migrate <type> &7- Migrate storage type");
            sender.sendMessage(prefix + "&c/wallet admin cleanup &7- Clean invalid data");
            sender.sendMessage(prefix + "&c/wallet admin test <type> &7- Test system components");
            sender.sendMessage(prefix + "&c/wallet admin reload &7- Reload plugin configuration");
        }
    }

    /**
     * Send admin help message with enhanced colors
     */
    private void sendAdminHelpMessage(CommandSender sender) {
        String prefix = plugin.getConfigManager().getPrefix();

        sender.sendMessage(prefix + "&c&lAdmin Commands:");
        sender.sendMessage(prefix + "&c/wallet admin reset &7- Force reset payout cycle");
        sender.sendMessage(prefix + "&c/wallet admin force <complete|shutdown> &7- Force payout control");
        sender.sendMessage(prefix + "&c/wallet admin deposit <player> <amount> &7- Deposit gems");
        sender.sendMessage(prefix + "&c/wallet admin withdraw <player> <amount> &7- Withdraw gems");
        sender.sendMessage(prefix + "&c/wallet admin view <island-id> &7- View island data");
        sender.sendMessage(prefix + "&c/wallet admin history [season] &7- View payout history");
        sender.sendMessage(prefix + "&c/wallet admin stats &7- View server statistics");
        sender.sendMessage(prefix + "&c/wallet admin backup &7- Create data backup");
        sender.sendMessage(prefix + "&c/wallet admin migrate <yaml|sqlite> &7- Migrate storage");
        sender.sendMessage(prefix + "&c/wallet admin cleanup &7- Clean invalid data");
        sender.sendMessage(prefix + "&c/wallet admin test <type> &7- Test components");
        sender.sendMessage(prefix + "&c/wallet admin reload &7- Reload plugin configuration");
    }

    // Cooldown management
    private boolean isOnCooldown(Player player, String commandType) {
        String key = player.getUniqueId() + ":" + commandType;
        Long lastUsed = commandCooldowns.get(key);

        if (lastUsed == null) {
            return false;
        }

        long cooldownDuration = (long) (plugin.getConfigManager().getCooldown(commandType + "-command") * 1000L);
        return (System.currentTimeMillis() - lastUsed) < cooldownDuration;
    }

    private long getRemainingCooldown(Player player, String commandType) {
        String key = player.getUniqueId() + ":" + commandType;
        Long lastUsed = commandCooldowns.get(key);

        if (lastUsed == null) {
            return 0;
        }

        long cooldownDuration = (long) (plugin.getConfigManager().getCooldown(commandType + "-command") * 1000L);
        long elapsed = System.currentTimeMillis() - lastUsed;
        return Math.max(0, (cooldownDuration - elapsed) / 1000);
    }

    private void setCooldown(Player player, String commandType) {
        String key = player.getUniqueId() + ":" + commandType;
        commandCooldowns.put(key, System.currentTimeMillis());
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (command.getName().equalsIgnoreCase("payout")) {
            return completions;
        }

        if (args.length == 1) {
            completions.addAll(Arrays.asList("balance", "purchase", "podium", "get", "help"));

            completions.addAll(Arrays.asList("info", "time"));

            if (sender.hasPermission("islandwallet.admin.*")) {
                completions.add("admin");
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("admin")) {
            if (sender.hasPermission("islandwallet.admin.*")) {
                completions.addAll(Arrays.asList("reset", "force", "deposit", "withdraw", "view", "history", "stats", "backup", "migrate", "cleanup", "test", "reload"));
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("force")) {
            completions.addAll(Arrays.asList("complete", "shutdown"));
        } else if (args.length == 3 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("migrate")) {
            completions.addAll(Arrays.asList("yaml", "sqlite"));
        } else if (args.length == 3 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("test")) {
            completions.addAll(Arrays.asList("database", "economy", "placeholders"));
        } else if (args.length == 3 && args[0].equalsIgnoreCase("get")) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                completions.add(player.getName());
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("podium")) {
            // Add season numbers
            int currentSeason = plugin.getDatabaseManager().getCurrentSeason();
            for (int i = 1; i <= currentSeason; i++) {
                completions.add(String.valueOf(i));
            }
        }

        return completions;
    }
}