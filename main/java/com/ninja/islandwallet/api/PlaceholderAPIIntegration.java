package com.ninja.islandwallet.api;

import com.ninja.islandwallet.IslandWalletPlugin;
import com.ninja.islandwallet.models.IslandData;
import com.ninja.islandwallet.models.PayoutWinner;
import com.ninja.islandwallet.utils.MessageUtil;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * ENHANCED PlaceholderAPI integration with money-based purchasing placeholders and time remaining
 */
public class PlaceholderAPIIntegration extends PlaceholderExpansion {

    private final IslandWalletPlugin plugin;

    public PlaceholderAPIIntegration(IslandWalletPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "islandwallet";
    }

    @Override
    public @NotNull String getAuthor() {
        return plugin.getDescription().getAuthors().toString();
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onPlaceholderRequest(Player player, @NotNull String params) {
        if (player == null) {
            return "";
        }

        // Get player's island data
        IslandData islandData = plugin.getWalletManager().getPlayerIslandData(player);

        if (islandData == null) {
            // Player is not on an island - return appropriate defaults
            return switch (params.toLowerCase()) {
                case "gems", "balance", "gem_balance", "gems_formatted", "balance_formatted" -> "0";
                case "payout_points", "points", "payout_points_formatted", "points_formatted" -> "0";
                case "rank", "leaderboard_rank" -> "N/A";
                case "island_name", "name" -> "None";
                case "leader", "owner" -> "N/A";
                case "member_count", "members" -> "0";
                case "currency_balance", "currency", "currency_formatted" -> "0";
                case "last_payout_rank", "last_payout_points" -> "N/A";
                case "point_cost_money", "cost_per_point_money" -> getPointCostInfo(params);
                case "can_afford_1_point", "can_afford_1", "can_afford_10_points", "can_afford_10", "can_afford_100_points", "can_afford_100" -> "false";
                case "has_island" -> "false";
                case "is_leader", "is_owner", "is_member" -> "false";
                case "total_islands" -> String.valueOf(plugin.getLeaderboardManager().getTotalParticipatingIslands());
                case "current_season" -> String.valueOf(plugin.getDatabaseManager().getCurrentSeason());
                case "player_balance", "money_balance" -> getPlayerBalance(player);
                // ENHANCED: Time remaining placeholders
                case "time_remaining_hours" -> MessageUtil.formatTimeRemainingHours(plugin.getPayoutManager().getTimeUntilNextPayout());
                case "time_remaining_minutes" -> MessageUtil.formatTimeRemainingMinutes(plugin.getPayoutManager().getTimeUntilNextPayout());
                case "time_remaining_formatted" -> MessageUtil.formatTimeRemaining(plugin.getPayoutManager().getTimeUntilNextPayout());
                case "time_remaining_seconds" -> String.valueOf(plugin.getPayoutManager().getTimeUntilNextPayout());
                // ENHANCED: Multiple countdown format placeholders
                case "time_remaining_compact" -> MessageUtil.formatTimeRemainingCompact(plugin.getPayoutManager().getTimeUntilNextPayout());
                case "time_remaining_short" -> MessageUtil.formatTimeRemainingShort(plugin.getPayoutManager().getTimeUntilNextPayout());
                case "time_remaining_days" -> String.valueOf(plugin.getPayoutManager().getTimeUntilNextPayout() / 86400);
                case "time_remaining_hours_only" -> String.valueOf((plugin.getPayoutManager().getTimeUntilNextPayout() % 86400) / 3600);
                case "time_remaining_minutes_only" -> String.valueOf((plugin.getPayoutManager().getTimeUntilNextPayout() % 3600) / 60);
                case "time_remaining_dhm" -> MessageUtil.formatTimeRemainingDHM(plugin.getPayoutManager().getTimeUntilNextPayout());
                // ENHANCED: Multiple countdown format placeholders
                case "time_remaining_compact" -> MessageUtil.formatTimeRemainingCompact(plugin.getPayoutManager().getTimeUntilNextPayout());
                case "time_remaining_short" -> MessageUtil.formatTimeRemainingShort(plugin.getPayoutManager().getTimeUntilNextPayout());
                case "time_remaining_days" -> String.valueOf(plugin.getPayoutManager().getTimeUntilNextPayout() / 86400);
                case "time_remaining_hours_only" -> String.valueOf((plugin.getPayoutManager().getTimeUntilNextPayout() % 86400) / 3600);
                case "time_remaining_minutes_only" -> String.valueOf((plugin.getPayoutManager().getTimeUntilNextPayout() % 3600) / 60);
                case "time_remaining_dhm" -> MessageUtil.formatTimeRemainingDHM(plugin.getPayoutManager().getTimeUntilNextPayout());
                // Legacy compatibility
                case "playerpoints" -> "0";
                default -> "";
            };
        }

        return switch (params.toLowerCase()) {
            // CRITICAL: Separated gems from payout points
            case "gems", "balance", "gem_balance" -> String.valueOf(islandData.getGems());
            case "gems_formatted", "balance_formatted" -> String.format("%,d", islandData.getGems());

            case "payout_points", "points" -> String.valueOf(islandData.getPayoutPoints());
            case "payout_points_formatted", "points_formatted" -> String.format("%,d", islandData.getPayoutPoints());

            // Island information
            case "island_name", "name" -> islandData.getIslandName();
            case "leader", "owner" -> islandData.getLeader() != null ? islandData.getLeader() : "N/A";
            case "member_count", "members" -> String.valueOf(islandData.getMemberCount());

            // Ranking information
            case "rank", "leaderboard_rank" -> {
                int rank = plugin.getLeaderboardManager().getIslandRank(islandData);
                yield rank > 0 ? String.valueOf(rank) : "N/A";
            }

            // Currency conversion (for display only, NOT for payouts)
            case "currency_balance", "currency" -> {
                double currencyValue = plugin.getWalletManager().getCurrencyValue(islandData.getGems());
                yield String.format("%.0f", currencyValue);
            }
            case "currency_formatted" -> {
                double currencyValue = plugin.getWalletManager().getCurrencyValue(islandData.getGems());
                yield String.format("$%,.2f", currencyValue);
            }

            // ENHANCED: Money-based purchase capabilities
            case "point_cost_money", "cost_per_point_money" -> String.format("%.2f", plugin.getConfigManager().getPointCostMoney());
            case "player_balance", "money_balance" -> getPlayerBalance(player);

            case "can_afford_1_point", "can_afford_1" -> String.valueOf(canAffordPoints(player, 1));
            case "can_afford_10_points", "can_afford_10" -> String.valueOf(canAffordPoints(player, 10));
            case "can_afford_100_points", "can_afford_100" -> String.valueOf(canAffordPoints(player, 100));

            // ENHANCED: Time remaining placeholders (3 formats)
            case "time_remaining_hours" -> MessageUtil.formatTimeRemainingHours(plugin.getPayoutManager().getTimeUntilNextPayout());
            case "time_remaining_minutes" -> MessageUtil.formatTimeRemainingMinutes(plugin.getPayoutManager().getTimeUntilNextPayout());
            case "time_remaining_formatted" -> MessageUtil.formatTimeRemaining(plugin.getPayoutManager().getTimeUntilNextPayout());
            case "time_remaining_seconds" -> String.valueOf(plugin.getPayoutManager().getTimeUntilNextPayout());

            // Player status
            case "has_island" -> "true";
            case "is_leader", "is_owner" -> String.valueOf(player.getName().equals(islandData.getLeader()));
            case "is_member" -> String.valueOf(islandData.isMember(player.getName()));

            // Historical data
            case "last_payout_rank" -> getLastPayoutRank(islandData.getIslandId());
            case "last_payout_points" -> getLastPayoutPoints(islandData.getIslandId());

            // Server-wide statistics
            case "total_islands" -> String.valueOf(plugin.getLeaderboardManager().getTotalParticipatingIslands());
            case "total_gems" -> {
                long totalGems = plugin.getWalletManager().getAllIslandData().values().stream()
                        .mapToLong(IslandData::getGems)
                        .sum();
                yield String.valueOf(totalGems);
            }
            case "total_payout_points" -> String.valueOf(plugin.getLeaderboardManager().getTotalPoints());
            case "current_season" -> String.valueOf(plugin.getDatabaseManager().getCurrentSeason());

            // Top islands (placeholders for scoreboards)
            case "top_1_name" -> getTopIslandName(1);
            case "top_1_leader" -> getTopIslandLeader(1);
            case "top_1_points" -> getTopIslandPoints(1);
            case "top_2_name" -> getTopIslandName(2);
            case "top_2_leader" -> getTopIslandLeader(2);
            case "top_2_points" -> getTopIslandPoints(2);
            case "top_3_name" -> getTopIslandName(3);
            case "top_3_leader" -> getTopIslandLeader(3);
            case "top_3_points" -> getTopIslandPoints(3);

            // Economy ratios
            case "gem_to_currency_ratio" -> String.valueOf(plugin.getConfigManager().getGemToCurrencyRatio());

            // Legacy compatibility (maps to gems)
            case "playerpoints" -> String.valueOf(islandData.getGems());

            default -> "";
        };
    }

    /**
     * Get point cost information for non-island players
     */
    private String getPointCostInfo(String params) {
        return switch (params.toLowerCase()) {
            case "point_cost_money", "cost_per_point_money" -> String.format("%.2f", plugin.getConfigManager().getPointCostMoney());
            default -> "0";
        };
    }

    /**
     * Get player's money balance
     */
    private String getPlayerBalance(Player player) {
        Economy economy = plugin.getEconomy();
        if (economy == null) {
            return "0.00";
        }
        return String.format("%.2f", economy.getBalance(player));
    }

    /**
     * Check if player can afford specific number of points
     */
    private boolean canAffordPoints(Player player, int points) {
        Economy economy = plugin.getEconomy();
        if (economy == null) {
            return false;
        }

        double cost = points * plugin.getConfigManager().getPointCostMoney();
        return economy.has(player, cost);
    }

    /**
     * Get island's rank in the last payout cycle
     */
    private String getLastPayoutRank(String islandId) {
        try {
            int currentSeason = plugin.getDatabaseManager().getCurrentSeason();
            if (currentSeason <= 1) {
                return "N/A";
            }

            int lastSeason = currentSeason - 1;
            List<PayoutWinner> lastSeasonWinners = plugin.getDatabaseManager().loadPayoutWinners(lastSeason);

            for (PayoutWinner winner : lastSeasonWinners) {
                if (winner.getIslandId().equals(islandId)) {
                    return String.valueOf(winner.getRank());
                }
            }

            return "N/A";

        } catch (Exception e) {
            plugin.getLogger().warning("Error getting last payout rank: " + e.getMessage());
            return "N/A";
        }
    }

    /**
     * Get island's points in the last payout cycle
     */
    private String getLastPayoutPoints(String islandId) {
        try {
            int currentSeason = plugin.getDatabaseManager().getCurrentSeason();
            if (currentSeason <= 1) {
                return "0";
            }

            int lastSeason = currentSeason - 1;
            List<PayoutWinner> lastSeasonWinners = plugin.getDatabaseManager().loadPayoutWinners(lastSeason);

            for (PayoutWinner winner : lastSeasonWinners) {
                if (winner.getIslandId().equals(islandId)) {
                    return String.valueOf(winner.getPoints());
                }
            }

            return "0";

        } catch (Exception e) {
            plugin.getLogger().warning("Error getting last payout points: " + e.getMessage());
            return "0";
        }
    }

    /**
     * Get top island name by rank
     */
    private String getTopIslandName(int rank) {
        try {
            List<IslandData> topIslands = plugin.getLeaderboardManager().getTopIslands(rank);
            if (topIslands.size() >= rank) {
                return topIslands.get(rank - 1).getIslandName();
            }
            return "N/A";
        } catch (Exception e) {
            return "N/A";
        }
    }

    /**
     * Get top island leader by rank
     */
    private String getTopIslandLeader(int rank) {
        try {
            List<IslandData> topIslands = plugin.getLeaderboardManager().getTopIslands(rank);
            if (topIslands.size() >= rank) {
                String leader = topIslands.get(rank - 1).getLeader();
                return leader != null ? leader : "N/A";
            }
            return "N/A";
        } catch (Exception e) {
            return "N/A";
        }
    }

    /**
     * Get top island points by rank
     */
    private String getTopIslandPoints(int rank) {
        try {
            List<IslandData> topIslands = plugin.getLeaderboardManager().getTopIslands(rank);
            if (topIslands.size() >= rank) {
                return String.valueOf(topIslands.get(rank - 1).getPayoutPoints());
            }
            return "0";
        } catch (Exception e) {
            return "0";
        }
    }
}