package com.ninja.islandwallet.listeners;

import com.bgsoftware.superiorskyblock.api.events.IslandDisbandEvent;
import com.bgsoftware.superiorskyblock.api.events.IslandCreateEvent;
import com.bgsoftware.superiorskyblock.api.events.IslandJoinEvent;
import com.bgsoftware.superiorskyblock.api.events.IslandQuitEvent;
import com.ninja.islandwallet.IslandWalletPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.logging.Level;

/**
 * Listens for SuperiorSkyblock2 events to handle island changes
 */
public class SuperiorSkyblockListener implements Listener {
    
    private final IslandWalletPlugin plugin;
    
    public SuperiorSkyblockListener(IslandWalletPlugin plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Handle island creation
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onIslandCreate(IslandCreateEvent event) {
        try {
            String islandId = event.getIsland().getUniqueId().toString();
            
            // Update island data to ensure it's tracked
            plugin.getWalletManager().updateIslandData(islandId);
            

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error handling island creation", e);
        }
    }
    
    /**
     * Handle island disbandment
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onIslandDisband(IslandDisbandEvent event) {
        try {
            String islandId = event.getIsland().getUniqueId().toString();
            
            // Update island data (this will remove it from database if island is deleted)
            plugin.getWalletManager().updateIslandData(islandId);
            
            plugin.getLogger().info("Island disbanded: " + islandId);
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error handling island disbandment", e);
        }
    }
    
    /**
     * Handle player joining island
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onIslandJoin(IslandJoinEvent event) {
        try {
            String islandId = event.getIsland().getUniqueId().toString();
            
            // Update island data to reflect new member
            plugin.getWalletManager().updateIslandData(islandId);
            
            if (plugin.getConfigManager().isDebugEnabled()) {
                plugin.getLogger().info("Player " + event.getPlayer().getName() + 
                    " joined island: " + islandId);
            }
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error handling island join", e);
        }
    }
    
    /**
     * Handle player leaving island
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onIslandQuit(IslandQuitEvent event) {
        try {
            String islandId = event.getIsland().getUniqueId().toString();
            
            // Update island data to reflect member leaving
            plugin.getWalletManager().updateIslandData(islandId);
            
            if (plugin.getConfigManager().isDebugEnabled()) {
                plugin.getLogger().info("Player " + event.getPlayer().getName() + 
                    " left island: " + islandId);
            }
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error handling island quit", e);
        }
    }
}