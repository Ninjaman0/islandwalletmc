package com.ninja.islandwallet.data;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.ninja.islandwallet.IslandWalletPlugin;
import com.ninja.islandwallet.models.IslandData;
import com.ninja.islandwallet.models.PayoutWinner;

import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * SQLite implementation with separated gems and payout points
 */
public class SQLiteManager implements DatabaseManager {

    private final IslandWalletPlugin plugin;
    private final Gson gson;
    private Connection connection;

    public SQLiteManager(IslandWalletPlugin plugin) {
        this.plugin = plugin;
        this.gson = new Gson();
    }

    @Override
    public void initialize() {
        try {
            File dataFolder = plugin.getDataFolder();
            if (!dataFolder.exists()) {
                dataFolder.mkdirs();
            }

            String dbPath = new File(dataFolder, plugin.getConfigManager().getDatabaseFile()).getAbsolutePath();
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);

            createTables();
            plugin.getLogger().info("SQLite database initialized successfully");

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to initialize SQLite database", e);
        }
    }

    /**
     * Create database tables with separated gems and payout points
     */
    private void createTables() throws SQLException {
        String islandDataTable = """
            CREATE TABLE IF NOT EXISTS island_data (
                island_id TEXT PRIMARY KEY,
                island_name TEXT NOT NULL,
                leader TEXT,
                admin TEXT,
                leader_uuid TEXT,
                members TEXT,
                gems INTEGER DEFAULT 0,
                payout_points INTEGER DEFAULT 0,
                last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
        """;

        String payoutWinnersTable = """
            CREATE TABLE IF NOT EXISTS payout_winners (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                island_id TEXT NOT NULL,
                island_name TEXT NOT NULL,
                leader TEXT NOT NULL,
                points INTEGER NOT NULL,
                rank INTEGER NOT NULL,
                season INTEGER NOT NULL,
                payout_date TIMESTAMP NOT NULL
            )
        """;

        String metadataTable = """
            CREATE TABLE IF NOT EXISTS metadata (
                key TEXT PRIMARY KEY,
                value TEXT NOT NULL
            )
        """;

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(islandDataTable);
            stmt.execute(payoutWinnersTable);
            stmt.execute(metadataTable);

            // Check if we need to migrate from old schema
            migrateFromOldSchema();

            // Initialize current season if not exists
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT OR IGNORE INTO metadata (key, value) VALUES (?, ?)"
            );
            ps.setString(1, "current_season");
            ps.setString(2, "1");
            ps.executeUpdate();
        }
    }

    /**
     * Migrate from old schema if player_points column exists
     */
    private void migrateFromOldSchema() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            // Check if old player_points column exists
            ResultSet rs = stmt.executeQuery("PRAGMA table_info(island_data)");
            boolean hasPlayerPoints = false;
            boolean hasGems = false;

            while (rs.next()) {
                String columnName = rs.getString("name");
                if ("player_points".equals(columnName)) {
                    hasPlayerPoints = true;
                } else if ("gems".equals(columnName)) {
                    hasGems = true;
                }
            }

            // If we have player_points but no gems column, migrate
            if (hasPlayerPoints && !hasGems) {
                plugin.getLogger().info("Migrating from old schema: player_points -> gems");

                // Add gems column
                stmt.execute("ALTER TABLE island_data ADD COLUMN gems INTEGER DEFAULT 0");

                // Copy player_points to gems
                stmt.execute("UPDATE island_data SET gems = player_points WHERE gems = 0");

                plugin.getLogger().info("Migration completed: player_points data copied to gems");
            }
        }
    }

    @Override
    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Error closing database connection", e);
        }
    }

    @Override
    public void saveIslandData(IslandData islandData) {
        String sql = """
            INSERT OR REPLACE INTO island_data 
            (island_id, island_name, leader, admin, leader_uuid, members, gems, payout_points, last_updated)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
        """;

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, islandData.getIslandId());
            ps.setString(2, islandData.getIslandName());
            ps.setString(3, islandData.getLeader());
            ps.setString(4, islandData.getAdmin());
            ps.setString(5, islandData.getLeaderUUID() != null ? islandData.getLeaderUUID().toString() : null);
            ps.setString(6, gson.toJson(islandData.getMembers()));
            ps.setLong(7, islandData.getGems());
            ps.setLong(8, islandData.getPayoutPoints());

            ps.executeUpdate();

            if (plugin.getConfigManager().isLogTransactions()) {
                plugin.getLogger().info("Saved island data: " + islandData.getIslandName() +
                        " (Gems: " + islandData.getGems() + ", Payout Points: " + islandData.getPayoutPoints() + ")");
            }

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save island data", e);
        }
    }

    @Override
    public IslandData loadIslandData(String islandId) {
        String sql = "SELECT * FROM island_data WHERE island_id = ?";

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, islandId);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return createIslandDataFromResultSet(rs);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load island data", e);
        }

        return null;
    }

    @Override
    public Map<String, IslandData> loadAllIslandData() {
        Map<String, IslandData> islandDataMap = new HashMap<>();
        String sql = "SELECT * FROM island_data";

        try (PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                IslandData islandData = createIslandDataFromResultSet(rs);
                islandDataMap.put(islandData.getIslandId(), islandData);
            }

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load all island data", e);
        }

        return islandDataMap;
    }

    /**
     * Create IslandData object from ResultSet with separated gems and payout points
     */
    private IslandData createIslandDataFromResultSet(ResultSet rs) throws SQLException {
        IslandData islandData = new IslandData(
                rs.getString("island_id"),
                rs.getString("island_name")
        );

        islandData.setLeader(rs.getString("leader"));
        islandData.setAdmin(rs.getString("admin"));

        String leaderUuidString = rs.getString("leader_uuid");
        if (leaderUuidString != null) {
            islandData.setLeaderUUID(UUID.fromString(leaderUuidString));
        }

        String membersJson = rs.getString("members");
        if (membersJson != null && !membersJson.isEmpty()) {
            List<String> members = gson.fromJson(membersJson, new TypeToken<List<String>>(){}.getType());
            islandData.setMembers(members);
        }

        // Load separated gems and payout points
        islandData.setGems(rs.getLong("gems"));
        islandData.setPayoutPoints(rs.getLong("payout_points"));

        return islandData;
    }

    @Override
    public void deleteIslandData(String islandId) {
        String sql = "DELETE FROM island_data WHERE island_id = ?";

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, islandId);
            ps.executeUpdate();

            plugin.getLogger().info("Deleted island data for: " + islandId);

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to delete island data", e);
        }
    }

    @Override
    public boolean islandExists(String islandId) {
        String sql = "SELECT 1 FROM island_data WHERE island_id = ?";

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, islandId);

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to check island existence", e);
        }

        return false;
    }

    @Override
    public void savePayoutWinners(List<PayoutWinner> winners) {
        String sql = """
            INSERT INTO payout_winners 
            (island_id, island_name, leader, points, rank, season, payout_date)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        """;

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            for (PayoutWinner winner : winners) {
                ps.setString(1, winner.getIslandId());
                ps.setString(2, winner.getIslandName());
                ps.setString(3, winner.getLeader());
                ps.setLong(4, winner.getPoints());
                ps.setInt(5, winner.getRank());
                ps.setInt(6, winner.getSeason());
                ps.setTimestamp(7, Timestamp.valueOf(winner.getPayoutDate()));
                ps.addBatch();
            }

            ps.executeBatch();
            plugin.getLogger().info("Saved " + winners.size() + " payout winners");

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save payout winners", e);
        }
    }

    @Override
    public List<PayoutWinner> loadPayoutWinners(int season) {
        List<PayoutWinner> winners = new ArrayList<>();
        String sql = "SELECT * FROM payout_winners WHERE season = ? ORDER BY rank ASC";

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, season);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    winners.add(createPayoutWinnerFromResultSet(rs));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load payout winners", e);
        }

        return winners;
    }

    @Override
    public List<PayoutWinner> loadAllPayoutWinners() {
        List<PayoutWinner> winners = new ArrayList<>();
        String sql = "SELECT * FROM payout_winners ORDER BY season DESC, rank ASC";

        try (PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                winners.add(createPayoutWinnerFromResultSet(rs));
            }

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load all payout winners", e);
        }

        return winners;
    }

    /**
     * Create PayoutWinner object from ResultSet
     */
    private PayoutWinner createPayoutWinnerFromResultSet(ResultSet rs) throws SQLException {
        return new PayoutWinner(
                rs.getString("island_id"),
                rs.getString("island_name"),
                rs.getString("leader"),
                rs.getLong("points"),
                rs.getInt("rank"),
                rs.getTimestamp("payout_date").toLocalDateTime(),
                rs.getInt("season")
        );
    }

    @Override
    public int getCurrentSeason() {
        String sql = "SELECT value FROM metadata WHERE key = ?";

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, "current_season");

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Integer.parseInt(rs.getString("value"));
                }
            }
        } catch (SQLException | NumberFormatException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to get current season", e);
        }

        return 1; // Default to season 1
    }

    @Override
    public int getNextSeason() {
        int currentSeason = getCurrentSeason();
        int nextSeason = currentSeason + 1;

        String sql = "UPDATE metadata SET value = ? WHERE key = ?";

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, String.valueOf(nextSeason));
            ps.setString(2, "current_season");
            ps.executeUpdate();

            return nextSeason;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to increment season", e);
        }

        return currentSeason;
    }

    @Override
    public void resetAllPayoutPoints() {
        String sql = "UPDATE island_data SET payout_points = 0";

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.executeUpdate();
            plugin.getLogger().info("Reset all payout points to zero (gems preserved)");

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to reset payout points", e);
        }
    }
}