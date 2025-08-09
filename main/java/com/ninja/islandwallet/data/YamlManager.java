package com.ninja.islandwallet.data;

import com.ninja.islandwallet.IslandWalletPlugin;
import com.ninja.islandwallet.models.IslandData;
import com.ninja.islandwallet.models.PayoutWinner;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * YAML implementation with separated gems and payout points
 */
public class YamlManager implements DatabaseManager {

    private final IslandWalletPlugin plugin;
    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private File islandDataFile;
    private File payoutDataFile;
    private File metadataFile;

    private FileConfiguration islandConfig;
    private FileConfiguration payoutConfig;
    private FileConfiguration metadataConfig;

    public YamlManager(IslandWalletPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void initialize() {
        try {
            File dataFolder = plugin.getDataFolder();
            if (!dataFolder.exists()) {
                dataFolder.mkdirs();
            }

            // Initialize files
            islandDataFile = new File(dataFolder, "islands.yml");
            payoutDataFile = new File(dataFolder, "payouts.yml");
            metadataFile = new File(dataFolder, "metadata.yml");

            // Create files if they don't exist
            if (!islandDataFile.exists()) {
                islandDataFile.createNewFile();
            }
            if (!payoutDataFile.exists()) {
                payoutDataFile.createNewFile();
            }
            if (!metadataFile.exists()) {
                metadataFile.createNewFile();
            }

            // Load configurations
            islandConfig = YamlConfiguration.loadConfiguration(islandDataFile);
            payoutConfig = YamlConfiguration.loadConfiguration(payoutDataFile);
            metadataConfig = YamlConfiguration.loadConfiguration(metadataFile);

            // Initialize metadata if needed
            if (!metadataConfig.contains("current-season")) {
                metadataConfig.set("current-season", 1);
                saveMetadata();
            }

            // Migrate from old schema if needed
            migrateFromOldSchema();

            plugin.getLogger().info("YAML database initialized successfully");

        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to initialize YAML database", e);
        }
    }

    /**
     * Migrate from old schema if player-points exists but gems doesn't
     */
    private void migrateFromOldSchema() {
        ConfigurationSection islandsSection = islandConfig.getConfigurationSection("islands");
        if (islandsSection == null) {
            return;
        }

        boolean needsMigration = false;

        // Check if any island has player-points but no gems
        for (String islandId : islandsSection.getKeys(false)) {
            String basePath = "islands." + islandId;
            if (islandConfig.contains(basePath + ".player-points") && !islandConfig.contains(basePath + ".gems")) {
                needsMigration = true;
                break;
            }
        }

        if (needsMigration) {
            plugin.getLogger().info("Migrating YAML data from old schema: player-points -> gems");

            for (String islandId : islandsSection.getKeys(false)) {
                String basePath = "islands." + islandId;

                if (islandConfig.contains(basePath + ".player-points")) {
                    long playerPoints = islandConfig.getLong(basePath + ".player-points", 0);

                    // Copy to gems field
                    islandConfig.set(basePath + ".gems", playerPoints);

                    // Initialize payout-points if it doesn't exist
                    if (!islandConfig.contains(basePath + ".payout-points")) {
                        islandConfig.set(basePath + ".payout-points", 0);
                    }
                }
            }

            saveIslandConfig();
            plugin.getLogger().info("YAML migration completed");
        }
    }

    @Override
    public void close() {
        // YAML doesn't need explicit closing
    }

    @Override
    public void saveIslandData(IslandData islandData) {
        try {
            String path = "islands." + islandData.getIslandId();

            islandConfig.set(path + ".island-name", islandData.getIslandName());
            islandConfig.set(path + ".leader", islandData.getLeader());
            islandConfig.set(path + ".admin", islandData.getAdmin());

            if (islandData.getLeaderUUID() != null) {
                islandConfig.set(path + ".leader-uuid", islandData.getLeaderUUID().toString());
            }

            islandConfig.set(path + ".members", islandData.getMembers());
            islandConfig.set(path + ".gems", islandData.getGems());
            islandConfig.set(path + ".payout-points", islandData.getPayoutPoints());

            saveIslandConfig();

            if (plugin.getConfigManager().isLogTransactions()) {
                plugin.getLogger().info("Saved island data: " + islandData.getIslandName() +
                        " (Gems: " + islandData.getGems() + ", Payout Points: " + islandData.getPayoutPoints() + ")");
            }

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save island data", e);
        }
    }

    @Override
    public IslandData loadIslandData(String islandId) {
        try {
            String path = "islands." + islandId;

            if (!islandConfig.contains(path)) {
                return null;
            }

            ConfigurationSection section = islandConfig.getConfigurationSection(path);
            if (section == null) {
                return null;
            }

            IslandData islandData = new IslandData(
                    islandId,
                    section.getString("island-name", "Unknown")
            );

            islandData.setLeader(section.getString("leader"));
            islandData.setAdmin(section.getString("admin"));

            String leaderUuidString = section.getString("leader-uuid");
            if (leaderUuidString != null) {
                islandData.setLeaderUUID(UUID.fromString(leaderUuidString));
            }

            List<String> members = section.getStringList("members");
            islandData.setMembers(members);

            // Load separated gems and payout points
            islandData.setGems(section.getLong("gems", 0));
            islandData.setPayoutPoints(section.getLong("payout-points", 0));

            return islandData;

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load island data", e);
        }

        return null;
    }

    @Override
    public Map<String, IslandData> loadAllIslandData() {
        Map<String, IslandData> islandDataMap = new HashMap<>();

        try {
            ConfigurationSection islandsSection = islandConfig.getConfigurationSection("islands");
            if (islandsSection == null) {
                return islandDataMap;
            }

            for (String islandId : islandsSection.getKeys(false)) {
                IslandData islandData = loadIslandData(islandId);
                if (islandData != null) {
                    islandDataMap.put(islandId, islandData);
                }
            }

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load all island data", e);
        }

        return islandDataMap;
    }

    @Override
    public void deleteIslandData(String islandId) {
        try {
            islandConfig.set("islands." + islandId, null);
            saveIslandConfig();

            plugin.getLogger().info("Deleted island data for: " + islandId);

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to delete island data", e);
        }
    }

    @Override
    public boolean islandExists(String islandId) {
        return islandConfig.contains("islands." + islandId);
    }

    @Override
    public void savePayoutWinners(List<PayoutWinner> winners) {
        try {
            int season = getCurrentSeason();
            String path = "seasons." + season;

            for (PayoutWinner winner : winners) {
                String winnerPath = path + ".winners." + winner.getRank();

                payoutConfig.set(winnerPath + ".island-id", winner.getIslandId());
                payoutConfig.set(winnerPath + ".island-name", winner.getIslandName());
                payoutConfig.set(winnerPath + ".leader", winner.getLeader());
                payoutConfig.set(winnerPath + ".points", winner.getPoints());
                payoutConfig.set(winnerPath + ".payout-date", winner.getPayoutDate().format(dateFormatter));
            }

            payoutConfig.set(path + ".payout-date", LocalDateTime.now().format(dateFormatter));
            savePayoutConfig();

            plugin.getLogger().info("Saved " + winners.size() + " payout winners for season " + season);

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save payout winners", e);
        }
    }

    @Override
    public List<PayoutWinner> loadPayoutWinners(int season) {
        List<PayoutWinner> winners = new ArrayList<>();

        try {
            String path = "seasons." + season + ".winners";
            ConfigurationSection winnersSection = payoutConfig.getConfigurationSection(path);

            if (winnersSection == null) {
                return winners;
            }

            for (String rankString : winnersSection.getKeys(false)) {
                int rank = Integer.parseInt(rankString);
                ConfigurationSection winnerSection = winnersSection.getConfigurationSection(rankString);

                if (winnerSection != null) {
                    PayoutWinner winner = new PayoutWinner(
                            winnerSection.getString("island-id"),
                            winnerSection.getString("island-name"),
                            winnerSection.getString("leader"),
                            winnerSection.getLong("points"),
                            rank,
                            LocalDateTime.parse(winnerSection.getString("payout-date"), dateFormatter),
                            season
                    );

                    winners.add(winner);
                }
            }

            // Sort by rank
            winners.sort((w1, w2) -> Integer.compare(w1.getRank(), w2.getRank()));

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load payout winners", e);
        }

        return winners;
    }

    @Override
    public List<PayoutWinner> loadAllPayoutWinners() {
        List<PayoutWinner> allWinners = new ArrayList<>();

        try {
            ConfigurationSection seasonsSection = payoutConfig.getConfigurationSection("seasons");
            if (seasonsSection == null) {
                return allWinners;
            }

            for (String seasonString : seasonsSection.getKeys(false)) {
                int season = Integer.parseInt(seasonString);
                List<PayoutWinner> seasonWinners = loadPayoutWinners(season);
                allWinners.addAll(seasonWinners);
            }

            // Sort by season descending, then by rank ascending
            allWinners.sort((w1, w2) -> {
                int seasonCompare = Integer.compare(w2.getSeason(), w1.getSeason());
                if (seasonCompare != 0) {
                    return seasonCompare;
                }
                return Integer.compare(w1.getRank(), w2.getRank());
            });

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load all payout winners", e);
        }

        return allWinners;
    }

    @Override
    public int getCurrentSeason() {
        return metadataConfig.getInt("current-season", 1);
    }

    @Override
    public int getNextSeason() {
        int nextSeason = getCurrentSeason() + 1;
        metadataConfig.set("current-season", nextSeason);
        saveMetadata();
        return nextSeason;
    }

    @Override
    public void resetAllPayoutPoints() {
        try {
            ConfigurationSection islandsSection = islandConfig.getConfigurationSection("islands");
            if (islandsSection == null) {
                return;
            }

            for (String islandId : islandsSection.getKeys(false)) {
                islandConfig.set("islands." + islandId + ".payout-points", 0);
            }

            saveIslandConfig();
            plugin.getLogger().info("Reset all payout points to zero (gems preserved)");

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to reset payout points", e);
        }
    }

    /**
     * Save island configuration file
     */
    private void saveIslandConfig() {
        try {
            islandConfig.save(islandDataFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save island configuration", e);
        }
    }

    /**
     * Save payout configuration file
     */
    private void savePayoutConfig() {
        try {
            payoutConfig.save(payoutDataFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save payout configuration", e);
        }
    }

    /**
     * Save metadata configuration file
     */
    private void saveMetadata() {
        try {
            metadataConfig.save(metadataFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save metadata", e);
        }
    }
}