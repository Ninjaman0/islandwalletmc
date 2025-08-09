package com.ninja.islandwallet.managers;

import com.ninja.islandwallet.IslandWalletPlugin;
import com.ninja.islandwallet.models.IslandData;
import com.ninja.islandwallet.models.PayoutWinner;
import com.ninja.islandwallet.utils.MessageUtil;
import org.bukkit.Bukkit;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

/**
 * CRITICAL: Manages payout cycles based ONLY on payout points (separate from gems)
 * NO MONEY DISTRIBUTION - Only tracks winners and rankings
 * Gems are completely preserved during payout cycles
 * ENHANCED: Now tracks payout times for time remaining calculations
 */
public class PayoutManager {

    private final IslandWalletPlugin plugin;
    private final WalletManager walletManager;
    private final LeaderboardManager leaderboardManager;
    private final AtomicBoolean payoutInProgress = new AtomicBoolean(false);

    public PayoutManager(IslandWalletPlugin plugin, WalletManager walletManager, LeaderboardManager leaderboardManager) {
        this.plugin = plugin;
        this.walletManager = walletManager;
        this.leaderboardManager = leaderboardManager;
    }

    /**
     * CRITICAL: Process payout cycle (only tracks winners, NO money distribution)
     * ENHANCED: Now updates last payout time for time remaining calculations
     */
    public void processPayout() {
        if (!payoutInProgress.compareAndSet(false, true)) {
            plugin.getLogger().warning("Payout already in progress, skipping...");
            return;
        }

        try {
            plugin.getLogger().info("Processing payout cycle (tracking winners only)...");

            // Get current leaderboard based on payout points only
            List<IslandData> leaderboard = leaderboardManager.getLeaderboard(true);

            if (leaderboard.isEmpty()) {
                plugin.getLogger().info("No islands with payout points found - skipping payout");
                return;
            }

            // Validate leaderboard data
            leaderboard.removeIf(island -> !walletManager.validateIslandData(island) || island.getPayoutPoints() <= 0);

            if (leaderboard.isEmpty()) {
                plugin.getLogger().info("No valid islands for payout after validation");
                return;
            }

            // Get current season
            int currentSeason = plugin.getDatabaseManager().getCurrentSeason();

            // Create payout winners for history (NO money distribution)
            List<PayoutWinner> winners = createPayoutWinners(leaderboard, currentSeason);

            // Save winners to database asynchronously
            CompletableFuture.runAsync(() -> {
                try {
                    plugin.getDatabaseManager().savePayoutWinners(winners);
                    plugin.getLogger().info("Saved " + winners.size() + " payout winners for season " + currentSeason);
                } catch (Exception e) {
                    plugin.getLogger().log(Level.SEVERE, "Failed to save payout winners", e);
                }
            });

            // ENHANCED: Update last payout time for time remaining calculations
            long currentTime = System.currentTimeMillis() / 1000; // Convert to seconds
            plugin.getConfigManager().setLastPayoutTime(currentTime);

            // Announce winners (NO money amounts)
            announceWinners(winners);

            // Start new cycle if configured
            if (plugin.getConfigManager().isAutoStartNewCycle()) {
                startNewCycle();
            }

            plugin.getLogger().info("Payout cycle completed successfully (winners tracked)");

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error processing payout cycle", e);
        } finally {
            payoutInProgress.set(false);
        }
    }

    /**
     * Force complete payout cycle (admin command)
     */
    public void forceCompletePayout() {
        plugin.getLogger().info("Admin force-completing payout cycle...");

        // Process in separate thread to avoid blocking
        CompletableFuture.runAsync(() -> {
            processPayout();

            // Broadcast admin action
            Bukkit.getScheduler().runTask(plugin, () -> {
                String message = plugin.getConfigManager().getMessage("payout-force-complete");
                Bukkit.broadcastMessage(plugin.getConfigManager().getPrefix() + message);
            });
        });
    }

    /**
     * Force shutdown current payout (admin command)
     */
    public void forceShutdownPayout() {
        if (payoutInProgress.compareAndSet(true, false)) {
            plugin.getLogger().info("Admin force-shutdown of payout cycle");

            // Broadcast admin action
            String message = plugin.getConfigManager().getMessage("payout-force-shutdown");
            Bukkit.broadcastMessage(plugin.getConfigManager().getPrefix() + message);
        } else {
            plugin.getLogger().info("No payout cycle in progress to shutdown");
        }
    }

    /**
     * Create payout winners from leaderboard (based on payout points only)
     */
    private List<PayoutWinner> createPayoutWinners(List<IslandData> leaderboard, int season) {
        List<PayoutWinner> winners = new ArrayList<>();
        LocalDateTime payoutTime = LocalDateTime.now();

        for (int i = 0; i < leaderboard.size(); i++) {
            IslandData island = leaderboard.get(i);
            int rank = i + 1;

            // Validate island data before creating winner
            if (!walletManager.validateIslandData(island)) {
                plugin.getLogger().warning("Skipping invalid island data for winner creation: " + island.getIslandId());
                continue;
            }

            PayoutWinner winner = new PayoutWinner(
                    island.getIslandId(),
                    MessageUtil.sanitizeString(island.getIslandName()),
                    MessageUtil.sanitizeString(island.getLeader() != null ? island.getLeader() : "Unknown"),
                    island.getPayoutPoints(), // Use payout points, NOT gems
                    rank,
                    payoutTime,
                    season
            );

            winners.add(winner);
        }

        return winners;
    }

    /**
     * Announce winners to the server (NO money amounts)
     */
    private void announceWinners(List<PayoutWinner> winners) {
        // Announce cycle end
        String cycleEndMessage = plugin.getConfigManager().getMessage("payout-cycle-ended");
        Bukkit.broadcastMessage(plugin.getConfigManager().getPrefix() + cycleEndMessage);

        // Announce top 3 winners
        for (int i = 0; i < Math.min(3, winners.size()); i++) {
            PayoutWinner winner = winners.get(i);
            String position = getPositionString(winner.getRank());
            String medal = getMedalEmoji(winner.getRank());

            String winnerMessage = String.format("%s &6%s Place: &e%s &7(Leader: %s) &b%s points",
                    medal,
                    position,
                    winner.getIslandName(),
                    winner.getLeader(),
                    MessageUtil.formatNumber(winner.getPoints())
            );

            Bukkit.broadcastMessage(plugin.getConfigManager().getPrefix() +
                    MessageUtil.translateColors(winnerMessage));
        }

        // Show total participants
        String totalMessage = String.format("&7Total participating islands: &e%d", winners.size());
        Bukkit.broadcastMessage(plugin.getConfigManager().getPrefix() +
                MessageUtil.translateColors(totalMessage));
    }

    /**
     * Get position string for rank
     */
    private String getPositionString(int rank) {
        return switch (rank) {
            case 1 -> "1st";
            case 2 -> "2nd";
            case 3 -> "3rd";
            default -> rank + "th";
        };
    }

    /**
     * Get medal emoji for rank
     */
    private String getMedalEmoji(int rank) {
        return switch (rank) {
            case 1 -> "🥇";
            case 2 -> "🥈";
            case 3 -> "🥉";
            default -> "🏆";
        };
    }

    /**
     * CRITICAL: Start new payout cycle (reset payout points only, preserve gems)
     * ENHANCED: Updates last payout time
     */
    public void startNewCycle() {
        try {
            plugin.getLogger().info("Starting new payout cycle...");

            // Reset only payout points, keep gems intact
            walletManager.resetAllPayoutPoints();

            // Increment season
            int newSeason = plugin.getDatabaseManager().getNextSeason();

            // ENHANCED: Update last payout time
            long currentTime = System.currentTimeMillis() / 1000; // Convert to seconds
            plugin.getConfigManager().setLastPayoutTime(currentTime);

            // Refresh leaderboard
            leaderboardManager.refreshLeaderboard();

            // Announce new cycle
            String newCycleMessage = plugin.getConfigManager().getMessage("payout-cycle-started");
            Bukkit.broadcastMessage(plugin.getConfigManager().getPrefix() + newCycleMessage);

            plugin.getLogger().info("Started new payout cycle - Season " + newSeason + " (gems preserved)");

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to start new payout cycle", e);
        }
    }

    /**
     * Force reset payout cycle (admin command)
     */
    public void forceResetCycle() {
        plugin.getLogger().info("Admin force-resetting payout cycle...");

        // Force complete current cycle then start new one
        CompletableFuture.runAsync(() -> {
            forceCompletePayout();

            // Wait a moment for completion
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            startNewCycle();
        });
    }

    /**
     * ENHANCED: Get time until next payout (in seconds) based on last payout time
     */
    public long getTimeUntilNextPayout() {
        long lastPayoutTime = plugin.getConfigManager().getLastPayoutTime();
        long payoutInterval = plugin.getConfigManager().getPayoutInterval();

        if (lastPayoutTime == 0) {
            // No previous payout recorded, return full interval
            return payoutInterval;
        }

        long currentTime = System.currentTimeMillis() / 1000; // Convert to seconds
        long timeSinceLastPayout = currentTime - lastPayoutTime;
        long timeRemaining = payoutInterval - timeSinceLastPayout;

        return Math.max(0, timeRemaining);
    }

    /**
     * Check if payout is currently in progress
     */
    public boolean isPayoutInProgress() {
        return payoutInProgress.get();
    }

    /**
     * Get current season information
     */
    public int getCurrentSeason() {
        return plugin.getDatabaseManager().getCurrentSeason();
    }

    /**
     * Validate payout system integrity
     */
    public boolean validatePayoutSystem() {
        try {
            // Check if leaderboard is accessible
            List<IslandData> leaderboard = leaderboardManager.getLeaderboard();

            // Check if database is accessible
            int currentSeason = plugin.getDatabaseManager().getCurrentSeason();

            // Check configuration
            boolean configValid = plugin.getConfigManager().validateConfiguration();

            return configValid && currentSeason > 0;

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Payout system validation failed", e);
            return false;
        }
    }
}