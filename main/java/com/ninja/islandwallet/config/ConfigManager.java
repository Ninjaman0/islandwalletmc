package com.ninja.islandwallet.config;

import com.ninja.islandwallet.IslandWalletPlugin;
import com.ninja.islandwallet.utils.MessageUtil;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;
import java.util.stream.Collectors;

/**
 * FIXED: Enhanced configuration manager with proper validation and error handling
 */
public class ConfigManager {

    private final IslandWalletPlugin plugin;
    private FileConfiguration config;

    public ConfigManager(IslandWalletPlugin plugin) {
        this.plugin = plugin;
        plugin.saveDefaultConfig();
        this.config = plugin.getConfig();
    }

    /**
     * Reload configuration from file
     */
    public void reload() {
        plugin.reloadConfig();
        this.config = plugin.getConfig();
        plugin.getLogger().info("Configuration reloaded successfully");
    }

    // Storage settings
    public String getStorageType() {
        String type = config.getString("storage.type", "sqlite").toLowerCase();
        if (!type.equals("sqlite") && !type.equals("yaml")) {
            plugin.getLogger().warning("Invalid storage type '" + type + "', using sqlite");
            return "sqlite";
        }
        return type;
    }

    public String getDatabaseFile() {
        String filename = config.getString("storage.database-file", "islandwallet.db");
        if (filename == null || filename.trim().isEmpty()) {
            plugin.getLogger().warning("Empty database filename, using default");
            return "islandwallet.db";
        }
        return filename.trim();
    }

    // FIXED: Payout settings with proper validation
    public long getPayoutInterval() {
        long interval = config.getLong("payout.interval", 1209600);
        if (interval < 3600) { // Minimum 1 hour
            plugin.getLogger().warning("Payout interval too short (" + interval + "s), using minimum of 1 hour");
            return 3600;
        }
        return interval;
    }

    public long getPayoutIntervalTicks() {
        return getPayoutInterval() * 20;
    }

    public boolean isAutoStartNewCycle() {
        return config.getBoolean("payout.auto-start-new-cycle", true);
    }

    /**
     * FIXED: Point cost in MONEY with validation
     */
    public double getPointCostMoney() {
        double cost = config.getDouble("payout.point-cost-money", 1000.0);
        if (cost <= 0) {
            plugin.getLogger().warning("Invalid point cost (" + cost + "), using default of 1000.0");
            return 1000.0;
        }
        return cost;
    }

    /**
     * FIXED: Get last payout time with validation
     */
    public long getLastPayoutTime() {
        return config.getLong("payout.last-payout-time", 0);
    }

    /**
     * FIXED: Set last payout time with validation
     */
    public void setLastPayoutTime(long time) {
        if (time < 0) {
            plugin.getLogger().warning("Invalid payout time: " + time);
            return;
        }
        config.set("payout.last-payout-time", time);
        plugin.saveConfig();
    }

    // Economy settings
    public boolean useVault() {
        return config.getBoolean("economy.use-vault", true);
    }

    public boolean useEssentialsX() {
        return config.getBoolean("economy.use-essentialsx", true);
    }

    public double getGemToCurrencyRatio() {
        double ratio = config.getDouble("economy.gem-to-currency-ratio", 1.0);
        if (ratio <= 0) {
            plugin.getLogger().warning("Invalid gem to currency ratio, using 1.0");
            return 1.0;
        }
        return ratio;
    }

    // FIXED: GUI settings with proper validation
    public int getLeaderboardDisplayRanks() {
        int ranks = config.getInt("gui.leaderboard.display-ranks", 10);
        if (ranks < 1) {
            plugin.getLogger().warning("Invalid display ranks (" + ranks + "), using 10");
            return 10;
        }
        return Math.min(ranks, 50); // Cap at 50 for performance
    }

    public String getLeaderboardTitle() {
        String title = config.getString("gui.leaderboard.title", "&6&lPayout Leaderboard");
        if (title == null || title.trim().isEmpty()) {
            plugin.getLogger().warning("Empty leaderboard title, using default");
            title = "&6&lPayout Leaderboard";
        }
        return MessageUtil.translateColors(title.trim());
    }

    public int getLeaderboardSize() {
        int size = config.getInt("gui.leaderboard.size", 54);
        return validateGuiSize(size, "leaderboard");
    }

    // FIXED: Podium GUI settings with validation
    public String getPodiumTitle() {
        String title = config.getString("gui.podium.title", "&6&lSeason {season} Winners");
        if (title == null || title.trim().isEmpty()) {
            plugin.getLogger().warning("Empty podium title, using default");
            title = "&6&lSeason {season} Winners";
        }
        return MessageUtil.translateColors(title.trim());
    }

    public int getPodiumSize() {
        int size = config.getInt("gui.podium.size", 54);
        return validateGuiSize(size, "podium");
    }

    public int getFirstPlaceSlot() {
        return validateSlot(config.getInt("gui.podium.first-place-slot", 13), "first-place");
    }

    public int getSecondPlaceSlot() {
        return validateSlot(config.getInt("gui.podium.second-place-slot", 12), "second-place");
    }

    public int getThirdPlaceSlot() {
        return validateSlot(config.getInt("gui.podium.third-place-slot", 14), "third-place");
    }

    public int getPreviousSeasonSlot() {
        return validateSlot(config.getInt("gui.podium.previous-season-slot", 45), "previous-season");
    }

    public int getNewCycleSlot() {
        return validateSlot(config.getInt("gui.podium.new-cycle-slot", 53), "new-cycle");
    }

    public int getPodiumAccessSlot() {
        return validateSlot(config.getInt("gui.podium.access-slot", 45), "podium-access");
    }

    // Purchase GUI settings
    public String getPurchaseTitle() {
        String title = config.getString("gui.purchase.title", "&a&lPurchase Payout Points");
        if (title == null || title.trim().isEmpty()) {
            plugin.getLogger().warning("Empty purchase title, using default");
            title = "&a&lPurchase Payout Points";
        }
        return MessageUtil.translateColors(title.trim());
    }

    public int getPurchaseSize() {
        int size = config.getInt("gui.purchase.size", 27);
        return validateGuiSize(size, "purchase");
    }

    public int getBuy1Slot() {
        return validateSlot(config.getInt("gui.purchase.buy-1-slot", 11), "buy-1");
    }

    public int getBuy10Slot() {
        return validateSlot(config.getInt("gui.purchase.buy-10-slot", 13), "buy-10");
    }

    public int getBuy100Slot() {
        return validateSlot(config.getInt("gui.purchase.buy-100-slot", 15), "buy-100");
    }

    public int getPurchaseAccessSlot() {
        return validateSlot(config.getInt("gui.purchase.access-slot", 53), "purchase-access");
    }

    // FIXED: Filler settings with validation
    public boolean isFillerEnabled() {
        return config.getBoolean("gui.filler.enabled", true);
    }

    public String getFillerMaterial() {
        String material = config.getString("gui.filler.material", "GRAY_STAINED_GLASS_PANE");
        return validateMaterial(material, "GRAY_STAINED_GLASS_PANE");
    }

    public String getFillerName() {
        String name = config.getString("gui.filler.name", " ");
        if (name == null) name = " ";
        return MessageUtil.translateColors(name);
    }

    public List<String> getFillerLore() {
        List<String> lore = config.getStringList("gui.filler.lore");
        return MessageUtil.translateColors(lore);
    }

    public List<Integer> getFillerSpecificSlots() {
        return config.getIntegerList("gui.filler.specific-slots");
    }

    public List<Integer> getFillerSkipSlots() {
        return config.getIntegerList("gui.filler.skip-slots");
    }

    // FIXED: Exit button settings with validation
    public boolean isExitButtonEnabled(String guiType) {
        return config.getBoolean("gui." + guiType + ".exit-button.enabled", true);
    }

    public int getExitButtonSlot(String guiType) {
        int slot = config.getInt("gui." + guiType + ".exit-button.slot", 49);
        return validateSlot(slot, guiType + "-exit");
    }

    public String getExitButtonMaterial(String guiType) {
        String material = config.getString("gui." + guiType + ".exit-button.material", "BARRIER");
        return validateMaterial(material, "BARRIER");
    }

    public String getExitButtonName(String guiType) {
        String name = config.getString("gui." + guiType + ".exit-button.name", "&c&lExit");
        if (name == null || name.trim().isEmpty()) {
            name = "&c&lExit";
        }
        return MessageUtil.translateColors(MessageUtil.sanitizeString(name.trim()));
    }

    public List<String> getExitButtonLore(String guiType) {
        return config.getStringList("gui." + guiType + ".exit-button.lore").stream()
                .map(MessageUtil::translateColors)
                .collect(Collectors.toList());
    }

    // FIXED: Item configurations with validation
    public String getItemMaterial(String path) {
        String material = config.getString("items." + path + ".material", "STONE");
        return validateMaterial(material, "STONE");
    }

    public String getItemName(String path) {
        String name = config.getString("items." + path + ".name", "");
        if (name == null) name = "";
        return MessageUtil.translateColors(MessageUtil.sanitizeString(name));
    }

    public List<String> getItemLore(String path) {
        List<String> lore = config.getStringList("items." + path + ".lore");
        return MessageUtil.translateColors(lore);
    }

    // FIXED: Messages with validation
    public String getMessage(String key) {
        String message = config.getString("messages." + key, "Message not found: " + key);
        if (message == null || message.trim().isEmpty()) {
            plugin.getLogger().warning("Empty message for key: " + key);
            return "Message not found: " + key;
        }
        return MessageUtil.translateColors(MessageUtil.sanitizeString(message.trim()));
    }

    public String getPrefix() {
        return getMessage("prefix");
    }

    // FIXED: Cooldowns with validation
    public double getCooldown(String type) {
        double cooldown = config.getDouble("cooldowns." + type, 0.5);
        if (cooldown < 0.1) {
            plugin.getLogger().warning("Cooldown too short for " + type + ", using 0.1");
            return 0.1;
        }
        if (cooldown > 60.0) {
            plugin.getLogger().warning("Cooldown too long for " + type + ", using 60.0");
            return 60.0;
        }
        return cooldown;
    }

    // Debug settings
    public boolean isDebugEnabled() {
        return config.getBoolean("debug.enabled", false);
    }

    public boolean isLogTransactions() {
        return config.getBoolean("debug.log-transactions", true);
    }

    public boolean isLogGuiInteractions() {
        return config.getBoolean("debug.log-gui-interactions", false);
    }

    public boolean isLogMoneyTransactions() {
        return config.getBoolean("debug.log-money-transactions", true);
    }

    // PlaceholderAPI settings
    public boolean isPlaceholdersEnabled() {
        return config.getBoolean("placeholders.enabled", true);
    }

    public boolean registerAllPlaceholders() {
        return config.getBoolean("placeholders.register-all", true);
    }

    // FIXED: Helper methods for validation
    private int validateGuiSize(int size, String guiType) {
        if (size < 9) {
            plugin.getLogger().warning("GUI size too small for " + guiType + " (" + size + "), using 9");
            size = 9;
        }
        if (size > 54) {
            plugin.getLogger().warning("GUI size too large for " + guiType + " (" + size + "), using 54");
            size = 54;
        }
        if (size % 9 != 0) {
            size = ((size / 9) + 1) * 9;
            plugin.getLogger().warning("GUI size for " + guiType + " must be multiple of 9, adjusted to " + size);
        }
        return Math.min(size, 54);
    }

    private int validateSlot(int slot, String slotType) {
        if (slot < 0) {
            plugin.getLogger().warning("Invalid slot for " + slotType + " (" + slot + "), using 0");
            return 0;
        }
        if (slot > 53) {
            plugin.getLogger().warning("Invalid slot for " + slotType + " (" + slot + "), using 53");
            return 53;
        }
        return slot;
    }

    private String validateMaterial(String materialName, String fallback) {
        if (materialName == null || materialName.trim().isEmpty()) {
            plugin.getLogger().warning("Empty material name, using " + fallback);
            return fallback;
        }

        try {
            Material.valueOf(materialName.trim().toUpperCase());
            return materialName.trim().toUpperCase();
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid material '" + materialName + "', using " + fallback);
            return fallback;
        }
    }

    /**
     * FIXED: Enhanced configuration validation
     */
    public boolean validateConfiguration() {
        boolean valid = true;

        // Validate point cost
        if (getPointCostMoney() <= 0) {
            plugin.getLogger().warning("Point cost must be positive!");
            valid = false;
        }

        // Validate payout interval
        if (getPayoutInterval() < 3600) {
            plugin.getLogger().warning("Payout interval must be at least 1 hour!");
            valid = false;
        }

        // Validate GUI sizes
        if (getLeaderboardSize() % 9 != 0) {
            plugin.getLogger().warning("Leaderboard GUI size must be multiple of 9!");
            valid = false;
        }

        if (getPurchaseSize() % 9 != 0) {
            plugin.getLogger().warning("Purchase GUI size must be multiple of 9!");
            valid = false;
        }

        if (getPodiumSize() % 9 != 0) {
            plugin.getLogger().warning("Podium GUI size must be multiple of 9!");
            valid = false;
        }

        // Validate materials
        try {
            Material.valueOf(getFillerMaterial());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid filler material in configuration!");
            valid = false;
        }

        // Validate display ranks
        if (getLeaderboardDisplayRanks() < 1) {
            plugin.getLogger().warning("Leaderboard display ranks must be at least 1!");
            valid = false;
        }

        // Validate gem to currency ratio
        if (getGemToCurrencyRatio() <= 0) {
            plugin.getLogger().warning("Gem to currency ratio must be positive!");
            valid = false;
        }

        if (!valid) {
            plugin.getLogger().warning("Configuration validation failed - some settings have been corrected");
        }

        return valid;
    }
}