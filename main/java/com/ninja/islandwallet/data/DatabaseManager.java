package com.ninja.islandwallet.data;

import com.ninja.islandwallet.models.IslandData;
import com.ninja.islandwallet.models.PayoutWinner;

import java.util.List;
import java.util.Map;

/**
 * Interface for database operations
 */
public interface DatabaseManager {
    
    /**
     * Initialize the database
     */
    void initialize();
    
    /**
     * Close database connections
     */
    void close();
    
    /**
     * Save island data
     */
    void saveIslandData(IslandData islandData);
    
    /**
     * Load island data by ID
     */
    IslandData loadIslandData(String islandId);
    
    /**
     * Load all island data
     */
    Map<String, IslandData> loadAllIslandData();
    
    /**
     * Delete island data
     */
    void deleteIslandData(String islandId);
    
    /**
     * Check if island exists in database
     */
    boolean islandExists(String islandId);
    
    /**
     * Save payout winners
     */
    void savePayoutWinners(List<PayoutWinner> winners);
    
    /**
     * Load payout winners by season
     */
    List<PayoutWinner> loadPayoutWinners(int season);
    
    /**
     * Load all payout winners
     */
    List<PayoutWinner> loadAllPayoutWinners();
    
    /**
     * Get the current season number
     */
    int getCurrentSeason();
    
    /**
     * Increment and get next season number
     */
    int getNextSeason();
    
    /**
     * Reset all payout points to zero
     */
    void resetAllPayoutPoints();
}