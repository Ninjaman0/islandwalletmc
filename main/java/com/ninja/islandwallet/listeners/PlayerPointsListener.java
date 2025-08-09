package com.ninja.islandwallet.listeners;

import com.ninja.islandwallet.IslandWalletPlugin;
import org.black_ixx.playerpoints.event.PlayerPointsChangeEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * FIXED: PlayerPoints listener with enhanced validation and error handling
 */
public class PlayerPointsListener implements Listener {

    private final IslandWalletPlugin plugin;
    private final ConcurrentHashMap<UUID, Long> lastProcessedTime;
    private final ConcurrentHashMap<UUID, Long> pendingRemovals;

    private static final long MIN_PROCESS_INTERVAL = 100; // 100ms minimum between processing

    public PlayerPointsListener(IslandWalletPlugin plugin) {
        this.plugin = plugin;
        this.lastProcessedTime = new ConcurrentHashMap<>();
        this.pendingRemovals = new ConcurrentHashMap<>();
    }

    /**
     * FIXED: Handle PlayerPoints gain events with enhanced validation
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerPointsChange(PlayerPointsChangeEvent event) {
        try {
            // FIXED: Enhanced validation of event data
            if (event == null) {
                plugin.getLogger().warning("Received null PlayerPointsChangeEvent");
                return;
            }

            // Only handle point gains (positive changes)
            if (event.getChange() <= 0) {
                return;
            }

            // FIXED: Validate reasonable amounts to prevent exploitation
            long gemsEarned = event.getChange();
            if (gemsEarned > 1000000000L) { // 1 billion max per transaction
                plugin.getLogger().warning("Blocked excessive gem transfer: " + gemsEarned + " for player " + event.getPlayerId());
                return;
            }

            // FIXED: Enhanced player validation
            UUID playerUUID = event.getPlayerId();
            if (playerUUID == null) {
                plugin.getLogger().warning("PlayerPointsChangeEvent has null player UUID");
                return;
            }

            // FIXED: Prevent spam processing with better tracking
            long currentTime = System.currentTimeMillis();
            Long lastTime = lastProcessedTime.get(playerUUID);
            if (lastTime != null && (currentTime - lastTime) < MIN_PROCESS_INTERVAL) {
                if (plugin.getConfigManager().isDebugEnabled()) {
                    plugin.getLogger().info("Skipping PlayerPoints processing for " + playerUUID + " due to spam protection");
                }
                return;
            }
            lastProcessedTime.put(playerUUID, currentTime);

            Player player = Bukkit.getPlayer(playerUUID);
            if (player == null || !player.isOnline()) {
                if (plugin.getConfigManager().isDebugEnabled()) {
                    plugin.getLogger().info("Player not online for PlayerPoints transfer: " + playerUUID);
                }
                return;
            }

            // FIXED: Enhanced validation of player state
            if (!player.isValid()) {
                plugin.getLogger().warning("Player is not valid for PlayerPoints transfer: " + player.getName());
                return;
            }

            // FIXED: Try to deposit gems to island wallet with better error handling
            boolean success = plugin.getWalletManager().depositGems(player, gemsEarned);

            if (success) {
                // FIXED: Enhanced removal with better error handling
                schedulePointRemoval(player, playerUUID, gemsEarned);
            } else {
                // Player is not on an island, gems remain in personal balance
                if (plugin.getConfigManager().isDebugEnabled()) {
                    plugin.getLogger().info(String.format("Player %s earned %d gems but is not on an island - gems remain in personal balance",
                            player.getName(), gemsEarned));
                }
            }

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error handling PlayerPoints change event", e);
        }
    }

    /**
     * FIXED: Schedule point removal with enhanced error handling
     */
    private void schedulePointRemoval(Player player, UUID playerUUID, long gemsEarned) {
        // Schedule removal for next tick to avoid concurrent modification
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            try {
                // FIXED: Validate player is still online and valid
                if (!player.isOnline() || !player.isValid()) {
                    plugin.getLogger().warning("Player no longer valid for point removal: " + player.getName());
                    return;
                }

                // FIXED: Enhanced PlayerPoints API usage
                org.black_ixx.playerpoints.PlayerPointsAPI playerPointsAPI = getPlayerPointsAPI();
                if (playerPointsAPI == null) {
                    plugin.getLogger().severe("PlayerPoints API is not available!");
                    return;
                }

                // FIXED: Calculate safe amount to remove (PlayerPoints uses int, not long)
                int removeAmount = (int) Math.min(gemsEarned, Integer.MAX_VALUE);
                if (removeAmount <= 0) {
                    plugin.getLogger().warning("Invalid remove amount calculated: " + removeAmount);
                    return;
                }

                // FIXED: Enhanced balance checking
                int currentBalance = playerPointsAPI.look(playerUUID);
                if (currentBalance < 0) {
                    plugin.getLogger().warning("Player has negative balance, cannot remove points: " + player.getName());
                    return;
                }

                if (currentBalance >= removeAmount) {
                    // Remove the points from player's personal balance
                    boolean removed = playerPointsAPI.take(playerUUID, removeAmount);

                    if (removed) {
                        // FIXED: Enhanced transfer notification
                        if (player.isOnline() && player.isValid()) {
                            String transferMessage = plugin.getConfigManager().getMessage("gems-transferred");
                            player.sendMessage(plugin.getConfigManager().getPrefix() + transferMessage);
                        }

                        if (plugin.getConfigManager().isLogTransactions()) {
                            plugin.getLogger().info(String.format("Successfully transferred %d gems from %s's personal balance to island wallet",
                                    removeAmount, player.getName()));
                        }
                    } else {
                        plugin.getLogger().warning("Failed to remove gems from player balance for: " + player.getName() +
                                " (API returned false)");
                    }
                } else {
                    plugin.getLogger().warning("Insufficient PlayerPoints balance to remove for: " + player.getName() +
                            " (Current: " + currentBalance + ", Needed: " + removeAmount + ")");
                }

            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to remove gems from player balance for: " + player.getName(), e);
            }
        }, 1L); // Run on next tick
    }

    /**
     * FIXED: Get PlayerPoints API with validation
     */
    private org.black_ixx.playerpoints.PlayerPointsAPI getPlayerPointsAPI() {
        try {
            org.black_ixx.playerpoints.PlayerPoints playerPointsPlugin =
                    org.black_ixx.playerpoints.PlayerPoints.getInstance();

            if (playerPointsPlugin == null) {
                plugin.getLogger().severe("PlayerPoints plugin instance is null!");
                return null;
            }

            if (!playerPointsPlugin.isEnabled()) {
                plugin.getLogger().warning("PlayerPoints plugin is not enabled!");
                return null;
            }

            return playerPointsPlugin.getAPI();
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error getting PlayerPoints API", e);
            return null;
        }
    }

    /**
     * FIXED: Enhanced cleanup with better validation
     */
    public void cleanupOldEntries() {
        try {
            long cutoffTime = System.currentTimeMillis() - (5 * 60 * 1000); // 5 minutes

            int removedProcessed = 0;
            int removedPending = 0;

            // Clean up last processed times
            removedProcessed = lastProcessedTime.entrySet().removeIf(entry -> {
                if (entry.getKey() == null || entry.getValue() == null) {
                    return true;
                }
                return entry.getValue() < cutoffTime;
            }) ? 1 : 0;

            // Clean up pending removals
            removedPending = pendingRemovals.entrySet().removeIf(entry -> {
                if (entry.getKey() == null || entry.getValue() == null) {
                    return true;
                }
                return entry.getValue() < cutoffTime;
            }) ? 1 : 0;

            if (plugin.getConfigManager().isDebugEnabled()) {
                plugin.getLogger().info(String.format("Cleaned up %d processed entries and %d pending entries from PlayerPointsListener",
                        removedProcessed, removedPending));
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error during PlayerPointsListener cleanup", e);
        }
    }
}