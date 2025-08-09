package com.ninja.islandwallet;

import com.ninja.islandwallet.api.PlaceholderAPIIntegration;
import com.ninja.islandwallet.commands.IslandWalletCommand;
import com.ninja.islandwallet.config.ConfigManager;
import com.ninja.islandwallet.data.DatabaseManager;
import com.ninja.islandwallet.data.SQLiteManager;
import com.ninja.islandwallet.data.YamlManager;
import com.ninja.islandwallet.gui.GuiManager;
import com.ninja.islandwallet.listeners.PlayerPointsListener;
import com.ninja.islandwallet.listeners.SuperiorSkyblockListener;
import com.ninja.islandwallet.managers.LeaderboardManager;
import com.ninja.islandwallet.managers.PayoutManager;
import com.ninja.islandwallet.managers.WalletManager;
import com.ninja.islandwallet.utils.DependencyChecker;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.logging.Level;

/**
 * CRITICAL: Main plugin class for IslandWallet
 * Manages shared island wallets with COMPLETELY SEPARATED gems and payout points
 *
 * GEMS: Island shared currency (NOT linked to payout system)
 * PAYOUT POINTS: Competition ranking points (purchased with gems at configured rate)
 */
public class IslandWalletPlugin extends JavaPlugin {

    private static IslandWalletPlugin instance;

    // Core managers
    private ConfigManager configManager;
    private DatabaseManager databaseManager;
    private WalletManager walletManager;
    private LeaderboardManager leaderboardManager;
    private PayoutManager payoutManager;
    private GuiManager guiManager;

    // Economy integration
    private Economy economy;

    // PlaceholderAPI integration
    private PlaceholderAPIIntegration placeholderAPI;

    // Cleanup task
    private BukkitRunnable cleanupTask;

    @Override
    public void onEnable() {
        instance = this;

        try {
            // Check dependencies first
            if (!checkDependencies()) {
                getLogger().severe("Required dependencies not found! Disabling plugin.");
                getServer().getPluginManager().disablePlugin(this);
                return;
            }

            // Initialize managers
            if (!initializeManagers()) {
                getLogger().severe("Failed to initialize managers! Disabling plugin.");
                getServer().getPluginManager().disablePlugin(this);
                return;
            }

            // Setup economy
            setupEconomy();

            // Register commands and listeners
            registerCommands();
            registerListeners();

            // Setup PlaceholderAPI
            setupPlaceholderAPI();

            // Start payout scheduler
            startPayoutScheduler();

            // Start cleanup task
            startCleanupTask();

            // Validate configuration
            if (!configManager.validateConfiguration()) {
                getLogger().warning("Configuration validation failed - some features may not work correctly");
            }

            getLogger().info("IslandWallet v" + getDescription().getVersion() + " has been enabled successfully!");
            getLogger().info("CRITICAL: Gems and payout points are completely separated systems");
            getLogger().info("Point cost: " + configManager.getPointCostMoney() + " money per payout point");

        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Failed to enable IslandWallet plugin", e);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        try {
            // Cancel cleanup task
            if (cleanupTask != null) {
                cleanupTask.cancel();
            }

            // Close database connections
            if (databaseManager != null) {
                databaseManager.close();
            }

            // Unregister PlaceholderAPI
            if (placeholderAPI != null) {
                placeholderAPI.unregister();
            }

            getLogger().info("IslandWallet has been disabled successfully!");

        } catch (Exception e) {
            getLogger().log(Level.WARNING, "Error during plugin disable", e);
        }
    }

    /**
     * Check if all required dependencies are present
     */
    private boolean checkDependencies() {
        try {
            DependencyChecker checker = new DependencyChecker(this);
            return checker.checkRequiredDependencies();
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Error checking dependencies", e);
            return false;
        }
    }

    /**
     * Initialize all plugin managers with proper error handling
     */
    private boolean initializeManagers() {
        try {
            // Configuration manager
            configManager = new ConfigManager(this);
            getLogger().info("Configuration manager initialized");

            // Database manager
            String storageType = configManager.getStorageType();
            if ("sqlite".equalsIgnoreCase(storageType)) {
                databaseManager = new SQLiteManager(this);
                getLogger().info("Using SQLite storage");
            } else {
                databaseManager = new YamlManager(this);
                getLogger().info("Using YAML storage");
            }

            // Initialize database
            databaseManager.initialize();
            getLogger().info("Database initialized");

            // Core managers
            walletManager = new WalletManager(this, databaseManager);
            getLogger().info("Wallet manager initialized");

            leaderboardManager = new LeaderboardManager(this, walletManager);
            getLogger().info("Leaderboard manager initialized");

            payoutManager = new PayoutManager(this, walletManager, leaderboardManager);
            getLogger().info("Payout manager initialized");

            guiManager = new GuiManager(this, leaderboardManager);
            getLogger().info("GUI manager initialized");

            return true;

        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Failed to initialize managers", e);
            return false;
        }
    }

    /**
     * Setup Vault economy integration
     */
    private void setupEconomy() {
        try {
            if (getServer().getPluginManager().getPlugin("Vault") == null) {
                getLogger().warning("Vault not found! Economy features will be limited.");
                return;
            }

            RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
            if (rsp == null) {
                getLogger().warning("No economy provider found!");
                return;
            }

            economy = rsp.getProvider();
            getLogger().info("Economy integration enabled with " + economy.getName());

        } catch (Exception e) {
            getLogger().log(Level.WARNING, "Failed to setup economy integration", e);
        }
    }

    /**
     * Register plugin commands with validation
     */
    private void registerCommands() {
        try {
            IslandWalletCommand commandExecutor = new IslandWalletCommand(this);

            registerCommand("wallet", commandExecutor);
            registerCommand("islandwallet", commandExecutor);
            registerCommand("iw", commandExecutor);
            registerCommand("payout", commandExecutor);

            getLogger().info("Commands registered successfully");

        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Failed to register commands", e);
        }
    }

    private void registerCommand(String commandName, CommandExecutor executor) {
        PluginCommand command = getCommand(commandName);
        if (command != null) {
            command.setExecutor(executor);
            if (executor instanceof TabCompleter) {
                command.setTabCompleter((TabCompleter) executor);
            }
        } else {
            getLogger().warning("Failed to register command: " + commandName + " - check plugin.yml");
        }
    }

    /**
     * Register event listeners with validation
     */
    private void registerListeners() {
        try {
            getServer().getPluginManager().registerEvents(new PlayerPointsListener(this), this);
            getServer().getPluginManager().registerEvents(new SuperiorSkyblockListener(this), this);
            getServer().getPluginManager().registerEvents(guiManager, this);

            getLogger().info("Event listeners registered successfully");

        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Failed to register event listeners", e);
        }
    }

    /**
     * Setup PlaceholderAPI integration if available
     */
    private void setupPlaceholderAPI() {
        try {
            if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
                placeholderAPI = new PlaceholderAPIIntegration(this);
                if (placeholderAPI.register()) {
                    getLogger().info("PlaceholderAPI integration enabled with comprehensive placeholders!");
                } else {
                    getLogger().warning("Failed to register PlaceholderAPI integration!");
                }
            } else {
                getLogger().info("PlaceholderAPI not found - placeholder features disabled.");
            }
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "Error setting up PlaceholderAPI integration", e);
        }
    }

    /**
     * Start the payout cycle scheduler
     */
    private void startPayoutScheduler() {
        try {
            long interval = configManager.getPayoutIntervalTicks();

            // Validate interval
            if (interval < 72000) { // Minimum 1 hour
                getLogger().warning("Payout interval too short, using minimum of 1 hour");
                interval = 72000;
            }

            new BukkitRunnable() {
                @Override
                public void run() {
                    try {
                        if (payoutManager.validatePayoutSystem()) {
                            payoutManager.processPayout();
                        } else {
                            getLogger().warning("Payout system validation failed, skipping cycle");
                        }
                    } catch (Exception e) {
                        getLogger().log(Level.SEVERE, "Error processing payout cycle", e);
                    }
                }
            }.runTaskTimer(this, interval, interval);

            getLogger().info("Payout scheduler started with interval: " + (interval / 20) + " seconds");

        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Failed to start payout scheduler", e);
        }
    }

    /**
     * Start cleanup task for memory management
     */
    private void startCleanupTask() {
        try {
            cleanupTask = new BukkitRunnable() {
                @Override
                public void run() {
                    try {
                        // Clean up GUI cooldowns
                        if (guiManager != null) {
                            guiManager.cleanupCooldowns();
                        }

                        // Clean up PlayerPointsListener entries
                        PlayerPointsListener listener = new PlayerPointsListener(IslandWalletPlugin.this);
                        listener.cleanupOldEntries();

                        if (configManager.isDebugEnabled()) {
                            getLogger().info("Performed cleanup of old entries");
                        }

                    } catch (Exception e) {
                        getLogger().log(Level.WARNING, "Error during cleanup task", e);
                    }
                }
            };

            // Run cleanup every 5 minutes
            cleanupTask.runTaskTimer(this, 6000L, 6000L);

        } catch (Exception e) {
            getLogger().log(Level.WARNING, "Failed to start cleanup task", e);
        }
    }

    /**
     * Reload the plugin configuration and managers
     */
    public void reloadPlugin() {
        try {
            getLogger().info("Reloading IslandWallet plugin...");

            configManager.reload();

            // Validate configuration after reload
            if (!configManager.validateConfiguration()) {
                getLogger().warning("Configuration validation failed after reload");
            }

            // Reload database manager if storage type changed
            String newStorageType = configManager.getStorageType();
            if ((databaseManager instanceof SQLiteManager && !"sqlite".equalsIgnoreCase(newStorageType)) ||
                    (databaseManager instanceof YamlManager && !"yaml".equalsIgnoreCase(newStorageType))) {

                databaseManager.close();

                if ("sqlite".equalsIgnoreCase(newStorageType)) {
                    databaseManager = new SQLiteManager(this);
                } else {
                    databaseManager = new YamlManager(this);
                }

                databaseManager.initialize();
                walletManager.setDatabaseManager(databaseManager);
                getLogger().info("Database manager reloaded with new storage type: " + newStorageType);
            }

            guiManager.reloadConfiguration();

            getLogger().info("Plugin reloaded successfully");

        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Error reloading plugin", e);
        }
    }

    // Getters for managers and utilities
    public static IslandWalletPlugin getInstance() {
        return instance;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public WalletManager getWalletManager() {
        return walletManager;
    }

    public LeaderboardManager getLeaderboardManager() {
        return leaderboardManager;
    }

    public PayoutManager getPayoutManager() {
        return payoutManager;
    }

    public GuiManager getGuiManager() {
        return guiManager;
    }

    public Economy getEconomy() {
        return economy;
    }

    public PlaceholderAPIIntegration getPlaceholderAPI() {
        return placeholderAPI;
    }
}