package com.ninja.islandwallet.managers;

import com.ninja.islandwallet.IslandWalletPlugin;
import com.ninja.islandwallet.models.IslandData;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Manages leaderboard calculations and rankings
 */
public class LeaderboardManager {
    
    private final IslandWalletPlugin plugin;
    private final WalletManager walletManager;
    
    // Cached leaderboard data
    private List<IslandData> cachedLeaderboard;
    private long lastUpdateTime;
    private static final long CACHE_DURATION = 30000; // 30 seconds
    
    public LeaderboardManager(IslandWalletPlugin plugin, WalletManager walletManager) {
        this.plugin = plugin;
        this.walletManager = walletManager;
        this.cachedLeaderboard = new ArrayList<>();
        this.lastUpdateTime = 0;
    }
    
    /**
     * Get current leaderboard sorted by payout points
     */
    public List<IslandData> getLeaderboard() {
        return getLeaderboard(false);
    }
    
    /**
     * Get leaderboard with option to force update
     */
    public List<IslandData> getLeaderboard(boolean forceUpdate) {
        long currentTime = System.currentTimeMillis();
        
        if (forceUpdate || cachedLeaderboard.isEmpty() || (currentTime - lastUpdateTime) > CACHE_DURATION) {
            updateLeaderboard();
            lastUpdateTime = currentTime;
        }
        
        return new ArrayList<>(cachedLeaderboard);
    }
    
    /**
     * Update leaderboard from current island data
     */
    private void updateLeaderboard() {
        try {
            Map<String, IslandData> allIslandData = walletManager.getAllIslandData();
            
            cachedLeaderboard = allIslandData.values().stream()
                .filter(island -> island.getPayoutPoints() > 0)
                .sorted((island1, island2) -> Long.compare(island2.getPayoutPoints(), island1.getPayoutPoints()))
                .collect(Collectors.toList());
            
            if (plugin.getConfigManager().isDebugEnabled()) {
                plugin.getLogger().info("Updated leaderboard with " + cachedLeaderboard.size() + " islands");
            }
            
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to update leaderboard: " + e.getMessage());
        }
    }
    
    /**
     * Get top N islands from leaderboard
     */
    public List<IslandData> getTopIslands(int count) {
        List<IslandData> leaderboard = getLeaderboard();
        return leaderboard.stream()
            .limit(count)
            .collect(Collectors.toList());
    }
    
    /**
     * Get island rank by island ID
     */
    public int getIslandRank(String islandId) {
        List<IslandData> leaderboard = getLeaderboard();
        
        for (int i = 0; i < leaderboard.size(); i++) {
            if (leaderboard.get(i).getIslandId().equals(islandId)) {
                return i + 1; // Rank starts from 1
            }
        }
        
        return -1; // Not found in leaderboard
    }
    
    /**
     * Get island rank by island data
     */
    public int getIslandRank(IslandData islandData) {
        return getIslandRank(islandData.getIslandId());
    }
    
    /**
     * Check if island is in top N
     */
    public boolean isInTopN(String islandId, int n) {
        int rank = getIslandRank(islandId);
        return rank > 0 && rank <= n;
    }
    
    /**
     * Get total number of islands with points
     */
    public int getTotalParticipatingIslands() {
        return getLeaderboard().size();
    }
    
    /**
     * Get total points across all islands
     */
    public long getTotalPoints() {
        return getLeaderboard().stream()
            .mapToLong(IslandData::getPayoutPoints)
            .sum();
    }
    
    /**
     * Get leaderboard statistics
     */
    public LeaderboardStats getLeaderboardStats() {
        List<IslandData> leaderboard = getLeaderboard();
        
        if (leaderboard.isEmpty()) {
            return new LeaderboardStats(0, 0, 0, 0);
        }
        
        long totalPoints = leaderboard.stream().mapToLong(IslandData::getPayoutPoints).sum();
        long maxPoints = leaderboard.get(0).getPayoutPoints();
        long minPoints = leaderboard.get(leaderboard.size() - 1).getPayoutPoints();
        long averagePoints = totalPoints / leaderboard.size();
        
        return new LeaderboardStats(totalPoints, maxPoints, minPoints, averagePoints);
    }
    
    /**
     * Force refresh leaderboard cache
     */
    public void refreshLeaderboard() {
        updateLeaderboard();
        lastUpdateTime = System.currentTimeMillis();
    }
    
    /**
     * Statistics for leaderboard
     */
    public static class LeaderboardStats {
        private final long totalPoints;
        private final long maxPoints;
        private final long minPoints;
        private final long averagePoints;
        
        public LeaderboardStats(long totalPoints, long maxPoints, long minPoints, long averagePoints) {
            this.totalPoints = totalPoints;
            this.maxPoints = maxPoints;
            this.minPoints = minPoints;
            this.averagePoints = averagePoints;
        }
        
        public long getTotalPoints() {
            return totalPoints;
        }
        
        public long getMaxPoints() {
            return maxPoints;
        }
        
        public long getMinPoints() {
            return minPoints;
        }
        
        public long getAveragePoints() {
            return averagePoints;
        }
    }
}