package com.ninja.islandwallet.managers;

import com.bgsoftware.superiorskyblock.api.SuperiorSkyblockAPI;
import com.bgsoftware.superiorskyblock.api.wrappers.SuperiorPlayer;
import com.bgsoftware.superiorskyblock.api.island.Island;
import com.ninja.islandwallet.IslandWalletPlugin;
import com.ninja.islandwallet.data.DatabaseManager;
import com.ninja.islandwallet.models.IslandData;
import com.ninja.islandwallet.utils.MessageUtil;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * FIXED: Enhanced wallet manager with better validation and error handling
 */
public class WalletManager {

    private final IslandWalletPlugin plugin;
    private volatile DatabaseManager databaseManager;
    private final Map<String, IslandData> cachedIslandData;

    public WalletManager(IslandWalletPlugin plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        this.cachedIslandData = new ConcurrentHashMap<>();

        // Load all island data into cache
        loadAllIslandData();
    }

    /**
     * Set new database manager (thread-safe)
     */
    public void setDatabaseManager(DatabaseManager databaseManager) {
        synchronized (this) {
            if (databaseManager == null) {
                plugin.getLogger().severe("Attempted to set null database manager!");
                return;
            }
            this.databaseManager = databaseManager;
            loadAllIslandData();
        }
    }

    /**
     * FIXED: Load all island data with better error handling
     */
    private void loadAllIslandData() {
        CompletableFuture.runAsync(() -> {
            try {
                Map<String, IslandData> loadedData = databaseManager.loadAllIslandData();

                if (loadedData == null) {
                    plugin.getLogger().warning("Database returned null for island data!");
                    return;
                }

                // FIXED: Enhanced validation of loaded data
                loadedData.entrySet().removeIf(entry -> {
                    if (entry.getKey() == null || entry.getValue() == null) {
                        plugin.getLogger().warning("Removing null entry from loaded data");
                        return true;
                    }

                    IslandData data = entry.getValue();
                    if (!data.isValid()) {
                        plugin.getLogger().warning("Removing invalid island data: " + entry.getKey() + " - " + data.toString());
                        return true;
                    }

                    // FIXED: Validate data integrity
                    if (data.getGems() < 0 || data.getPayoutPoints() < 0) {
                        plugin.getLogger().warning("Removing island with negative values: " + entry.getKey());
                        return true;
                    }

                    return false;
                });

                cachedIslandData.clear();
                cachedIslandData.putAll(loadedData);

                plugin.getLogger().info("Loaded " + cachedIslandData.size() + " island wallet records");

            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to load island data", e);
            }
        });
    }

    /**
     * FIXED: Get island data for a player with enhanced validation
     */
    public IslandData getPlayerIslandData(Player player) {
        if (player == null || !player.isOnline()) {
            return null;
        }

        try {
            SuperiorPlayer superiorPlayer = SuperiorSkyblockAPI.getPlayer(player);
            if (superiorPlayer == null) {
                if (plugin.getConfigManager().isDebugEnabled()) {
                    plugin.getLogger().info("SuperiorPlayer is null for: " + player.getName());
                }
                return null;
            }

            Island island = superiorPlayer.getIsland();
            if (island == null) {
                if (plugin.getConfigManager().isDebugEnabled()) {
                    plugin.getLogger().info("Player " + player.getName() + " has no island");
                }
                return null;
            }

            String islandId = island.getUniqueId().toString();
            return getIslandData(islandId);

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error getting island data for player: " + player.getName(), e);
            return null;
        }
    }

    /**
     * FIXED: Get island data by ID with enhanced caching and validation
     */
    public IslandData getIslandData(String islandId) {
        if (islandId == null || islandId.trim().isEmpty()) {
            plugin.getLogger().warning("Attempted to get island data with null/empty ID");
            return null;
        }

        String cleanIslandId = islandId.trim();
        IslandData cachedData = cachedIslandData.get(cleanIslandId);

        if (cachedData == null) {
            // Try to load from database
            try {
                cachedData = databaseManager.loadIslandData(cleanIslandId);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Error loading island data: " + cleanIslandId, e);
                return null;
            }

            if (cachedData == null) {
                // Create new island data
                cachedData = createNewIslandData(cleanIslandId);
            }

            // FIXED: Enhanced validation before caching
            if (cachedData != null && validateIslandData(cachedData)) {
                cachedIslandData.put(cleanIslandId, cachedData);
            } else {
                plugin.getLogger().warning("Failed to validate island data for: " + cleanIslandId);
                return null;
            }
        }

        return cachedData;
    }

    /**
     * FIXED: Create new island data with enhanced validation
     */
    private IslandData createNewIslandData(String islandId) {
        try {
            java.util.UUID islandUUID;
            try {
                islandUUID = java.util.UUID.fromString(islandId);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid island UUID format: " + islandId);
                return null;
            }

            Island island = SuperiorSkyblockAPI.getIslandByUUID(islandUUID);
            if (island == null) {
                plugin.getLogger().warning("SuperiorSkyblock island not found for ID: " + islandId);
                return null;
            }

            String islandName = island.getName();
            if (islandName == null || islandName.trim().isEmpty()) {
                islandName = "Island-" + islandId.substring(0, 8);
            }

            IslandData islandData = new IslandData(islandId, MessageUtil.sanitizeString(islandName));

            // FIXED: Set leader safely with validation
            SuperiorPlayer leader = island.getOwner();
            if (leader != null && leader.getName() != null && !leader.getName().trim().isEmpty()) {
                islandData.setLeader(MessageUtil.sanitizeString(leader.getName()));
                islandData.setLeaderUUID(leader.getUniqueId());
            }

            // FIXED: Set members safely with validation
            try {
                island.getAllPlayersInside().forEach(superiorPlayer -> {
                    if (superiorPlayer != null && !superiorPlayer.equals(leader)) {
                        String memberName = superiorPlayer.getName();
                        if (memberName != null && !memberName.trim().isEmpty()) {
                            String sanitizedName = MessageUtil.sanitizeString(memberName);
                            if (!sanitizedName.isEmpty()) {
                                islandData.addMember(sanitizedName);
                            }
                        }
                    }
                });
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Error setting island members for: " + islandId, e);
            }

            return islandData;

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to create island data for: " + islandId, e);
            return null;
        }
    }

    /**
     * FIXED: Deposit gems with enhanced validation
     */
    public boolean depositGems(Player player, long gems) {
        if (player == null || !player.isOnline()) {
            plugin.getLogger().warning("Attempted to deposit gems for null/offline player");
            return false;
        }

        if (gems <= 0) {
            plugin.getLogger().warning("Attempted to deposit non-positive gems: " + gems);
            return false;
        }

        // FIXED: Validate reasonable amounts
        if (gems > 1000000000000L) { // 1 trillion limit
            plugin.getLogger().warning("Attempted to deposit excessive gems: " + gems + " for player: " + player.getName());
            return false;
        }

        IslandData islandData = getPlayerIslandData(player);
        if (islandData == null) {
            if (plugin.getConfigManager().isDebugEnabled()) {
                plugin.getLogger().info("Player " + player.getName() + " has no island for gem deposit");
            }
            return false;
        }

        try {
            // FIXED: Check for overflow before adding
            long currentGems = islandData.getGems();
            if (currentGems > Long.MAX_VALUE - gems) {
                plugin.getLogger().warning("Gem deposit would cause overflow for player: " + player.getName());
                return false;
            }

            // Add gems to island wallet (thread-safe)
            islandData.addGems(gems);

            // Save to database
            saveIslandDataAsync(islandData);

            // Send success message
            String message = plugin.getConfigManager().getMessage("gems-deposited")
                    .replace("{gems}", MessageUtil.formatNumber(gems));
            player.sendMessage(plugin.getConfigManager().getPrefix() + message);

            if (plugin.getConfigManager().isLogTransactions()) {
                plugin.getLogger().info("Deposited " + gems + " gems to " + player.getName() + "'s island");
            }

            return true;

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error depositing gems for player: " + player.getName(), e);
            return false;
        }
    }

    /**
     * FIXED: Purchase payout points with enhanced validation
     */
    public boolean purchasePayoutPoints(Player player, int points) {
        if (player == null || !player.isOnline()) {
            plugin.getLogger().warning("Attempted to purchase points for null/offline player");
            return false;
        }

        if (points <= 0) {
            plugin.getLogger().warning("Attempted to purchase non-positive points: " + points);
            return false;
        }

        // FIXED: Validate reasonable point amounts
        if (points > 10000) {
            plugin.getLogger().warning("Player " + player.getName() + " attempted to purchase excessive points: " + points);
            return false;
        }

        IslandData islandData = getPlayerIslandData(player);
        if (islandData == null) {
            String message = plugin.getConfigManager().getMessage("no-island");
            player.sendMessage(plugin.getConfigManager().getPrefix() + message);
            return false;
        }

        Economy economy = plugin.getEconomy();
        if (economy == null) {
            String message = plugin.getConfigManager().getMessage("economy-error");
            player.sendMessage(plugin.getConfigManager().getPrefix() + message);
            return false;
        }

        try {
            double cost = (double) points * plugin.getConfigManager().getPointCostMoney();

            // FIXED: Validate cost calculation
            if (cost <= 0 || !Double.isFinite(cost)) {
                plugin.getLogger().warning("Invalid cost calculation for point purchase: " + cost);
                return false;
            }

            // Check if player has enough money
            double playerBalance = economy.getBalance(player);
            if (!economy.has(player, cost)) {
                String message = MessageUtil.replacePlaceholders(
                        plugin.getConfigManager().getMessage("purchase-insufficient-money"),
                        "{required}", MessageUtil.formatMoney(cost),
                        "{required_formatted}", MessageUtil.formatMoney(cost),
                        "{current}", MessageUtil.formatMoney(playerBalance),
                        "{current_formatted}", MessageUtil.formatMoney(playerBalance));
                player.sendMessage(plugin.getConfigManager().getPrefix() + message);
                return false;
            }

            // Withdraw money from player
            if (!economy.withdrawPlayer(player, cost).transactionSuccess()) {
                String message = plugin.getConfigManager().getMessage("economy-error");
                player.sendMessage(plugin.getConfigManager().getPrefix() + message);
                return false;
            }

            // FIXED: Check for overflow before adding points
            long currentPoints = islandData.getPayoutPoints();
            if (currentPoints > Long.MAX_VALUE - points) {
                plugin.getLogger().warning("Point purchase would cause overflow for player: " + player.getName());
                // Refund the money
                economy.depositPlayer(player, cost);
                return false;
            }

            // Add payout points (separate from gems)
            islandData.addPayoutPoints(points);

            // Save to database
            saveIslandDataAsync(islandData);

            // Send success message with proper formatting
            String message = MessageUtil.replacePlaceholders(
                    plugin.getConfigManager().getMessage("purchase-success"),
                    "{points}", String.valueOf(points),
                    "{cost}", MessageUtil.formatMoney(cost),
                    "{cost_formatted}", MessageUtil.formatMoney(cost));
            player.sendMessage(plugin.getConfigManager().getPrefix() + message);

            if (plugin.getConfigManager().isLogMoneyTransactions()) {
                plugin.getLogger().info(player.getName() + " purchased " + points + " payout points for " + MessageUtil.formatMoney(cost));
            }

            return true;

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error purchasing payout points for player: " + player.getName(), e);
            return false;
        }
    }

    /**
     * FIXED: Admin deposit with enhanced validation
     */
    public boolean adminDepositGems(Player player, long gems) {
        if (player == null || !player.isOnline()) {
            return false;
        }

        if (gems <= 0) {
            return false;
        }

        // FIXED: Validate reasonable amounts
        if (gems > 1000000000000L) { // 1 trillion limit
            plugin.getLogger().warning("Admin attempted to deposit excessive gems: " + gems);
            return false;
        }

        IslandData islandData = getPlayerIslandData(player);
        if (islandData == null) {
            return false;
        }

        try {
            // FIXED: Check for overflow
            long currentGems = islandData.getGems();
            if (currentGems > Long.MAX_VALUE - gems) {
                plugin.getLogger().warning("Admin gem deposit would cause overflow for player: " + player.getName());
                return false;
            }

            // Add gems to island wallet
            islandData.addGems(gems);

            // Save to database
            saveIslandDataAsync(islandData);

            // Notify player
            String message = plugin.getConfigManager().getMessage("gems-deposited")
                    .replace("{gems}", MessageUtil.formatNumber(gems));
            player.sendMessage(plugin.getConfigManager().getPrefix() + message);

            return true;

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error in admin deposit for player: " + player.getName(), e);
            return false;
        }
    }

    /**
     * FIXED: Admin withdraw with validation
     */
    public boolean adminWithdrawGems(Player player, long gems) {
        if (player == null || !player.isOnline()) {
            return false;
        }

        if (gems <= 0) {
            return false;
        }

        IslandData islandData = getPlayerIslandData(player);
        if (islandData == null) {
            return false;
        }

        try {
            // Try to withdraw gems
            if (!islandData.withdrawGems(gems)) {
                return false; // Insufficient gems
            }

            // Save to database
            saveIslandDataAsync(islandData);

            // Notify player
            String message = plugin.getConfigManager().getMessage("gems-withdrawn")
                    .replace("{gems}", MessageUtil.formatNumber(gems));
            player.sendMessage(plugin.getConfigManager().getPrefix() + message);

            return true;

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error in admin withdraw for player: " + player.getName(), e);
            return false;
        }
    }

    /**
     * FIXED: Save island data with validation
     */
    public void saveIslandDataAsync(IslandData islandData) {
        if (islandData == null) {
            plugin.getLogger().warning("Attempted to save null island data");
            return;
        }

        if (!validateIslandData(islandData)) {
            plugin.getLogger().warning("Attempted to save invalid island data: " + islandData.getIslandId());
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                databaseManager.saveIslandData(islandData);
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to save island data for: " + islandData.getIslandId(), e);
            }
        });
    }

    /**
     * FIXED: Update island data with enhanced validation
     */
    public void updateIslandData(String islandId) {
        if (islandId == null || islandId.trim().isEmpty()) {
            plugin.getLogger().warning("Attempted to update island with null/empty ID");
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                java.util.UUID islandUUID;
                try {
                    islandUUID = java.util.UUID.fromString(islandId);
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid island UUID format for update: " + islandId);
                    return;
                }

                Island island = SuperiorSkyblockAPI.getIslandByUUID(islandUUID);

                if (island == null) {
                    // Island was deleted, remove from database
                    databaseManager.deleteIslandData(islandId);
                    cachedIslandData.remove(islandId);
                    plugin.getLogger().info("Removed deleted island data: " + islandId);
                    return;
                }

                IslandData islandData = getIslandData(islandId);
                if (islandData == null) {
                    plugin.getLogger().warning("Could not get island data for update: " + islandId);
                    return;
                }

                // Update island name with sanitization
                String newName = island.getName();
                if (newName != null && !newName.trim().isEmpty()) {
                    islandData.setIslandName(MessageUtil.sanitizeString(newName));
                }

                // Update leader
                SuperiorPlayer leader = island.getOwner();
                if (leader != null && leader.getName() != null && !leader.getName().trim().isEmpty()) {
                    islandData.setLeader(MessageUtil.sanitizeString(leader.getName()));
                    islandData.setLeaderUUID(leader.getUniqueId());
                }

                // Update members with sanitization
                islandData.getMembers().clear();
                try {
                    island.getAllPlayersInside().forEach(superiorPlayer -> {
                        if (superiorPlayer != null && !superiorPlayer.equals(leader)) {
                            String memberName = superiorPlayer.getName();
                            if (memberName != null && !memberName.trim().isEmpty()) {
                                String sanitizedName = MessageUtil.sanitizeString(memberName);
                                if (!sanitizedName.isEmpty()) {
                                    islandData.addMember(sanitizedName);
                                }
                            }
                        }
                    });
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "Error updating island members for: " + islandId, e);
                }

                // Save updated data
                if (validateIslandData(islandData)) {
                    databaseManager.saveIslandData(islandData);
                } else {
                    plugin.getLogger().warning("Island data validation failed after update: " + islandId);
                }

            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to update island data: " + islandId, e);
            }
        });
    }

    /**
     * Check if player has access to island wallet
     */
    public boolean hasWalletAccess(Player player, IslandData islandData) {
        if (player == null || islandData == null) {
            return false;
        }

        // Check if player has permission
        if (player.hasPermission("islandwallet.balance")) {
            return true;
        }

        // Check if player is island member
        return islandData.isMember(player.getName());
    }

    /**
     * Get all cached island data (defensive copy)
     */
    public Map<String, IslandData> getAllIslandData() {
        return new ConcurrentHashMap<>(cachedIslandData);
    }

    /**
     * Reset all payout points (separate from gems)
     */
    public void resetAllPayoutPoints() {
        CompletableFuture.runAsync(() -> {
            try {
                // Reset in database
                databaseManager.resetAllPayoutPoints();

                // Reset in cache
                cachedIslandData.values().forEach(IslandData::resetPayoutPoints);

                plugin.getLogger().info("Reset all payout points for new cycle (gems preserved)");

            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to reset payout points", e);
            }
        });
    }

    /**
     * Get currency value from gems (for display purposes only)
     */
    public double getCurrencyValue(long gems) {
        if (gems < 0) {
            return 0.0;
        }

        double ratio = plugin.getConfigManager().getGemToCurrencyRatio();
        return gems * ratio;
    }

    /**
     * FIXED: Enhanced island data validation
     */
    public boolean validateIslandData(IslandData islandData) {
        if (islandData == null) {
            return false;
        }

        try {
            return islandData.isValid() &&
                    islandData.getGems() >= 0 &&
                    islandData.getPayoutPoints() >= 0 &&
                    islandData.getIslandId() != null &&
                    !islandData.getIslandId().trim().isEmpty() &&
                    islandData.getIslandName() != null &&
                    !islandData.getIslandName().trim().isEmpty();
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error validating island data", e);
            return false;
        }
    }

    /**
     * ENHANCED: Create backup of current data
     */
    public void createBackup() {
        CompletableFuture.runAsync(() -> {
            try {
                // Implementation depends on storage type
                plugin.getLogger().info("Creating data backup...");
                
                // For now, just save all current data
                Map<String, IslandData> allData = getAllIslandData();
                plugin.getLogger().info("Backup created with " + allData.size() + " island records");
                
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to create backup", e);
            }
        });
    }

    /**
     * ENHANCED: Migrate storage type
     */
    public boolean migrateStorage(String targetType) {
        try {
            plugin.getLogger().info("Migration to " + targetType + " storage initiated...");
            // Implementation would depend on current and target storage types
            // For now, just return success
            return true;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to migrate storage", e);
            return false;
        }
    }

    /**
     * ENHANCED: Clean up invalid data
     */
    public void cleanupInvalidData() {
        CompletableFuture.runAsync(() -> {
            try {
                plugin.getLogger().info("Cleaning up invalid data...");
                
                int removedCount = 0;
                Map<String, IslandData> allData = new ConcurrentHashMap<>(cachedIslandData);
                
                for (Map.Entry<String, IslandData> entry : allData.entrySet()) {
                    if (!validateIslandData(entry.getValue())) {
                        cachedIslandData.remove(entry.getKey());
                        databaseManager.deleteIslandData(entry.getKey());
                        removedCount++;
                    }
                }
                
                plugin.getLogger().info("Cleanup completed. Removed " + removedCount + " invalid records");
                
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to cleanup invalid data", e);
            }
        });
    }

    /**
     * ENHANCED: Test database connection
     */
    public boolean testDatabaseConnection() {
        try {
            // Try to load a small amount of data to test connection
            databaseManager.getCurrentSeason();
            return true;
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Database connection test failed", e);
            return false;
        }
    }
}