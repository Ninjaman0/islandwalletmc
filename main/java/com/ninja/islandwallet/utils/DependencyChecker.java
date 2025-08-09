package com.ninja.islandwallet.utils;

import com.ninja.islandwallet.IslandWalletPlugin;
import org.bukkit.plugin.Plugin;

/**
 * Utility class to check plugin dependencies
 */
public class DependencyChecker {
    
    private final IslandWalletPlugin plugin;
    
    public DependencyChecker(IslandWalletPlugin plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Check all required dependencies
     */
    public boolean checkRequiredDependencies() {
        boolean allPresent = true;
        
        // Check SuperiorSkyblock2
        if (!checkDependency("SuperiorSkyblock2", true)) {
            allPresent = false;
        }
        
        // Check PlayerPoints
        if (!checkDependency("PlayerPoints", true)) {
            allPresent = false;
        }
        
        // Check Vault
        if (!checkDependency("Vault", true)) {
            allPresent = false;
        }
        
        // Check Essentials
        if (!checkDependency("Essentials", true)) {
            allPresent = false;
        }
        
        // Check optional dependencies
        checkDependency("PlaceholderAPI", false);
        
        return allPresent;
    }
    
    /**
     * Check individual dependency
     */
    private boolean checkDependency(String pluginName, boolean required) {
        Plugin dependencyPlugin = plugin.getServer().getPluginManager().getPlugin(pluginName);
        
        if (dependencyPlugin == null) {
            if (required) {
                plugin.getLogger().severe("Required dependency not found: " + pluginName);
                return false;
            } else {
                plugin.getLogger().info("Optional dependency not found: " + pluginName);
                return true;
            }
        }
        
        if (!dependencyPlugin.isEnabled()) {
            if (required) {
                plugin.getLogger().severe("Required dependency is disabled: " + pluginName);
                return false;
            } else {
                plugin.getLogger().warning("Optional dependency is disabled: " + pluginName);
                return true;
            }
        }
        
        plugin.getLogger().info("Dependency found and enabled: " + pluginName + " v" + 
            dependencyPlugin.getDescription().getVersion());
        return true;
    }
    
    /**
     * Check if PlaceholderAPI is available
     */
    public boolean isPlaceholderAPIAvailable() {
        Plugin placeholderAPI = plugin.getServer().getPluginManager().getPlugin("PlaceholderAPI");
        return placeholderAPI != null && placeholderAPI.isEnabled();
    }
    
    /**
     * Get dependency version
     */
    public String getDependencyVersion(String pluginName) {
        Plugin dependencyPlugin = plugin.getServer().getPluginManager().getPlugin(pluginName);
        
        if (dependencyPlugin != null) {
            return dependencyPlugin.getDescription().getVersion();
        }
        
        return "Not Found";
    }
}