package com.ninja.islandwallet.gui;

import com.ninja.islandwallet.IslandWalletPlugin;
import com.ninja.islandwallet.managers.LeaderboardManager;
import com.ninja.islandwallet.models.IslandData;
import com.ninja.islandwallet.models.PayoutWinner;
import com.ninja.islandwallet.utils.MessageUtil;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * FIXED: GUI manager with proper config reading, player heads in podium, and fixed mechanics
 */
public class GuiManager implements Listener {

    private final IslandWalletPlugin plugin;
    private final LeaderboardManager leaderboardManager;

    private final ConcurrentHashMap<java.util.UUID, Long> guiCooldowns = new ConcurrentHashMap<>();
    private static final long GUI_COOLDOWN_MS = 500;

    public GuiManager(IslandWalletPlugin plugin, LeaderboardManager leaderboardManager) {
        this.plugin = plugin;
        this.leaderboardManager = leaderboardManager;
    }

    /**
     * FIXED: Open main leaderboard GUI with proper config reading
     */
    public void openLeaderboardGui(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }

        try {
            // FIXED: Properly read title from config
            String title = plugin.getConfigManager().getLeaderboardTitle();
            int size = plugin.getConfigManager().getLeaderboardSize();

            Inventory gui = Bukkit.createInventory(null, size, title);

            List<IslandData> leaderboard = leaderboardManager.getTopIslands(
                    plugin.getConfigManager().getLeaderboardDisplayRanks()
            );

            // FIXED: Better validation of leaderboard data
            leaderboard.removeIf(island -> island == null || !plugin.getWalletManager().validateIslandData(island));

            populateLeaderboardGui(gui, leaderboard);

            // FIXED: Add buttons with proper config validation
            addPodiumAccessButton(gui, player);
            addPurchaseAccessButton(gui, player);

            // FIXED: Use configurable filler system
            fillEmptySlots(gui);
            addExitButton(gui, "leaderboard");

            player.openInventory(gui);

            if (plugin.getConfigManager().isDebugEnabled()) {
                plugin.getLogger().info("Opened leaderboard GUI for " + player.getName() + " with " + leaderboard.size() + " islands");
            }

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to open leaderboard GUI for " + player.getName(), e);
            sendErrorMessage(player, "database-error");
        }
    }

    /**
     * FIXED: Open purchase GUI with proper validation
     */
    public void openPurchaseGui(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }

        try {
            // FIXED: Properly read title from config
            String title = plugin.getConfigManager().getPurchaseTitle();
            int size = plugin.getConfigManager().getPurchaseSize();

            Inventory gui = Bukkit.createInventory(null, size, title);

            IslandData islandData = plugin.getWalletManager().getPlayerIslandData(player);
            if (islandData == null) {
                sendErrorMessage(player, "no-island");
                return;
            }

            // FIXED: Enhanced validation
            if (!plugin.getWalletManager().validateIslandData(islandData)) {
                plugin.getLogger().warning("Invalid island data for player: " + player.getName());
                sendErrorMessage(player, "database-error");
                return;
            }

            addPurchaseOptions(gui, player);
            fillEmptySlots(gui);
            addExitButton(gui, "purchase");

            player.openInventory(gui);

            if (plugin.getConfigManager().isDebugEnabled()) {
                plugin.getLogger().info("Opened purchase GUI for " + player.getName());
            }

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to open purchase GUI for " + player.getName(), e);
            sendErrorMessage(player, "database-error");
        }
    }

    /**
     * FIXED: Open podium GUI with player heads and proper config reading
     */
    public void openPodiumGui(Player player, int season) {
        if (player == null || !player.isOnline()) {
            return;
        }

        if (season < 1) {
            plugin.getLogger().warning("Invalid season requested: " + season);
            sendErrorMessage(player, "Invalid season number!");
            return;
        }

        try {
            // FIXED: Properly read and format title from config
            String title = plugin.getConfigManager().getPodiumTitle().replace("{season}", String.valueOf(season));
            int size = plugin.getConfigManager().getPodiumSize();

            Inventory gui = Bukkit.createInventory(null, size, title);

            List<PayoutWinner> winners = plugin.getDatabaseManager().loadPayoutWinners(season);

            // FIXED: Enhanced validation of winner data
            winners.removeIf(winner -> winner == null ||
                    winner.getIslandName() == null || winner.getIslandName().trim().isEmpty() ||
                    winner.getLeader() == null || winner.getLeader().trim().isEmpty() ||
                    winner.getPoints() < 0 ||
                    winner.getRank() < 1);

            populatePodiumGui(gui, winners);


            fillEmptySlots(gui);
            addExitButton(gui, "podium");

            player.openInventory(gui);

            if (plugin.getConfigManager().isDebugEnabled()) {
                plugin.getLogger().info("Opened podium GUI for " + player.getName() + " - Season " + season + " with " + winners.size() + " winners");
            }

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to open podium GUI for " + player.getName(), e);
            sendErrorMessage(player, "database-error");
        }
    }

    /**
     * FIXED: Add podium access button with proper config validation
     */
    private void addPodiumAccessButton(Inventory gui, Player player) {
        if (!player.hasPermission("islandwallet.leaderboard")) {
            return;
        }

        try {
            int podiumSlot = plugin.getConfigManager().getPodiumAccessSlot();

            // FIXED: Validate slot bounds
            if (podiumSlot >= 0 && podiumSlot < gui.getSize()) {
                ItemStack podiumButton = createPodiumAccessItem();
                if (podiumButton != null) {
                    gui.setItem(podiumSlot, podiumButton);
                }
            } else {
                plugin.getLogger().warning("Invalid podium access slot configured: " + podiumSlot);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to add podium access button", e);
        }
    }

    /**
     * FIXED: Create podium access button with proper error handling
     */
    private ItemStack createPodiumAccessItem() {
        try {
            String materialName = plugin.getConfigManager().getItemMaterial("podium-access");
            Material material = Material.valueOf(materialName);
            ItemStack item = new ItemStack(material);
            ItemMeta meta = item.getItemMeta();

            if (meta != null) {
                String name = plugin.getConfigManager().getItemName("podium-access");
                meta.setDisplayName(name);

                List<String> lore = new ArrayList<>();
                for (String loreLine : plugin.getConfigManager().getItemLore("podium-access")) {
                    lore.add(loreLine);
                }
                meta.setLore(lore);
                item.setItemMeta(meta);
            }

            return item;
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid material for podium access item: " + e.getMessage());
            // Fallback to default material
            return new ItemStack(Material.NETHER_STAR);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to create podium access item", e);
            return new ItemStack(Material.NETHER_STAR);
        }
    }

    /**
     * FIXED: Add purchase access button with proper validation
     */
    private void addPurchaseAccessButton(Inventory gui, Player player) {
        if (!player.hasPermission("islandwallet.purchase")) {
            return;
        }

        try {
            int accessSlot = plugin.getConfigManager().getPurchaseAccessSlot();

            // FIXED: Validate slot bounds
            if (accessSlot >= 0 && accessSlot < gui.getSize()) {
                ItemStack accessButton = createPurchaseAccessItem();
                if (accessButton != null) {
                    gui.setItem(accessSlot, accessButton);
                }
            } else {
                plugin.getLogger().warning("Invalid purchase access slot configured: " + accessSlot);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to add purchase access button", e);
        }
    }

    /**
     * FIXED: Create purchase access button with proper placeholder replacement
     */
    private ItemStack createPurchaseAccessItem() {
        try {
            String materialName = plugin.getConfigManager().getItemMaterial("purchase-access");
            Material material = Material.valueOf(materialName);
            ItemStack item = new ItemStack(material);
            ItemMeta meta = item.getItemMeta();

            if (meta != null) {
                String name = plugin.getConfigManager().getItemName("purchase-access");
                meta.setDisplayName(name);

                List<String> lore = new ArrayList<>();
                for (String loreLine : plugin.getConfigManager().getItemLore("purchase-access")) {
                    String processedLine = MessageUtil.replacePlaceholders(loreLine,
                            "{point_cost_money}", String.format("%.2f", plugin.getConfigManager().getPointCostMoney()));
                    lore.add(processedLine);
                }
                meta.setLore(lore);
                item.setItemMeta(meta);
            }

            return item;
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid material for purchase access item: " + e.getMessage());
            return new ItemStack(Material.GOLD_INGOT);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to create purchase access item", e);
            return new ItemStack(Material.GOLD_INGOT);
        }
    }

    /**
     * FIXED: Populate leaderboard GUI with enhanced validation
     */
    private void populateLeaderboardGui(Inventory gui, List<IslandData> leaderboard) {
        int slot = 0;
        int maxDisplaySlots = gui.getSize() - 9; // Leave bottom row for buttons

        for (IslandData island : leaderboard) {
            if (slot >= maxDisplaySlots) {
                break;
            }

            // FIXED: Enhanced validation
            if (!plugin.getWalletManager().validateIslandData(island)) {
                plugin.getLogger().warning("Skipping invalid island data in leaderboard: " + island.getIslandId());
                continue;
            }

            int rank = slot + 1;
            ItemStack item = createLeaderboardItem(island, rank);
            if (item != null) {
                gui.setItem(slot, item);
                slot++;
            }
        }
    }

    /**
     * FIXED: Create leaderboard item with proper player head support
     */
    private ItemStack createLeaderboardItem(IslandData island, int rank) {
        try {
            String materialName = plugin.getConfigManager().getItemMaterial("leaderboard-entry");
            Material material = Material.valueOf(materialName);
            ItemStack item = new ItemStack(material);

            // FIXED: Proper player head setting
            if (material == Material.PLAYER_HEAD && island.getLeader() != null && !island.getLeader().trim().isEmpty()) {
                SkullMeta skullMeta = (SkullMeta) item.getItemMeta();
                if (skullMeta != null) {
                    try {
                        // Try to get OfflinePlayer for better head texture support
                        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(island.getLeader());
                        skullMeta.setOwningPlayer(offlinePlayer);
                    } catch (Exception e) {
                        // Fallback to owner name
                        skullMeta.setOwner(island.getLeader());
                    }
                    item.setItemMeta(skullMeta);
                }
            }

            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                String leaderName = island.getLeader() != null ? island.getLeader() : "Unknown";
                String name = MessageUtil.replacePlaceholders(plugin.getConfigManager().getItemName("leaderboard-entry"),
                        "{rank}", String.valueOf(rank),
                        "{island_name}", MessageUtil.sanitizeString(island.getIslandName()),
                        "{leader}", MessageUtil.sanitizeString(leaderName));

                meta.setDisplayName(name);

                List<String> lore = new ArrayList<>();
                for (String loreLine : plugin.getConfigManager().getItemLore("leaderboard-entry")) {
                    String processedLine = MessageUtil.replacePlaceholders(loreLine,
                            "{rank}", String.valueOf(rank),
                            "{island_name}", MessageUtil.sanitizeString(island.getIslandName()),
                            "{leader}", MessageUtil.sanitizeString(leaderName),
                            "{member_count}", String.valueOf(island.getMemberCount()),
                            "{payout_points_formatted}", MessageUtil.formatNumber(island.getPayoutPoints()),
                            "{currency_formatted}", MessageUtil.formatCurrency(plugin.getWalletManager().getCurrencyValue(island.getPayoutPoints())));

                    lore.add(processedLine);
                }

                meta.setLore(lore);
                item.setItemMeta(meta);
            }

            return item;
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid material for leaderboard entry: " + e.getMessage());
            return new ItemStack(Material.PLAYER_HEAD);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to create leaderboard item for rank " + rank, e);
            return new ItemStack(Material.PLAYER_HEAD);
        }
    }

    /**
     * FIXED: Add purchase options with enhanced validation
     */
    private void addPurchaseOptions(Inventory gui, Player player) {
        try {
            double pointCost = plugin.getConfigManager().getPointCostMoney();
            Economy economy = plugin.getEconomy();
            double playerBalance = economy != null ? economy.getBalance(player) : 0.0;

            // FIXED: Validate slots before adding items
            int buy1Slot = plugin.getConfigManager().getBuy1Slot();
            if (buy1Slot >= 0 && buy1Slot < gui.getSize()) {
                ItemStack buy1 = createPurchaseItem("purchase-1-point", 1, pointCost, playerBalance);
                if (buy1 != null) {
                    gui.setItem(buy1Slot, buy1);
                }
            }

            int buy10Slot = plugin.getConfigManager().getBuy10Slot();
            if (buy10Slot >= 0 && buy10Slot < gui.getSize()) {
                ItemStack buy10 = createPurchaseItem("purchase-10-points", 10, pointCost * 10, playerBalance);
                if (buy10 != null) {
                    gui.setItem(buy10Slot, buy10);
                }
            }

            int buy100Slot = plugin.getConfigManager().getBuy100Slot();
            if (buy100Slot >= 0 && buy100Slot < gui.getSize()) {
                ItemStack buy100 = createPurchaseItem("purchase-100-points", 100, pointCost * 100, playerBalance);
                if (buy100 != null) {
                    gui.setItem(buy100Slot, buy100);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to add purchase options", e);
        }
    }

    /**
     * FIXED: Create purchase item with proper validation
     */
    private ItemStack createPurchaseItem(String itemPath, int points, double cost, double playerBalance) {
        try {
            String materialName = plugin.getConfigManager().getItemMaterial(itemPath);
            Material material = Material.valueOf(materialName);
            ItemStack item = new ItemStack(material);
            ItemMeta meta = item.getItemMeta();

            if (meta != null) {
                String name = plugin.getConfigManager().getItemName(itemPath);
                meta.setDisplayName(name);

                List<String> lore = new ArrayList<>();
                for (String loreLine : plugin.getConfigManager().getItemLore(itemPath)) {
                    String processedLine = MessageUtil.replacePlaceholders(loreLine,
                            "{cost}", String.format("%.2f", cost),
                            "{player_balance}", String.format("%.2f", playerBalance),
                            "{can_afford}", String.valueOf(playerBalance >= cost));
                    lore.add(processedLine);
                }
                meta.setLore(lore);

                // FIXED: Add enchant effect for affordable items
                if (playerBalance >= cost) {
                    meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
                    meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
                }

                item.setItemMeta(meta);
            }

            return item;
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid material for purchase item " + itemPath + ": " + e.getMessage());
            return new ItemStack(Material.EMERALD);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to create purchase item: " + itemPath, e);
            return new ItemStack(Material.EMERALD);
        }
    }

    /**
     * FIXED: Populate podium GUI with PLAYER HEADS using leader textures
     */
    private void populatePodiumGui(Inventory gui, List<PayoutWinner> winners) {
        try {
            for (PayoutWinner winner : winners) {
                if (winner.getRank() <= 3) {
                    int slot = getPodiumSlot(winner.getRank());
                    if (slot >= 0 && slot < gui.getSize()) {
                        ItemStack item = createPodiumItem(winner);
                        if (item != null) {
                            gui.setItem(slot, item);
                        }
                    }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to populate podium GUI", e);
        }
    }

    /**
     * FIXED: Get podium slot with validation
     */
    private int getPodiumSlot(int rank) {
        try {
            return switch (rank) {
                case 1 -> plugin.getConfigManager().getFirstPlaceSlot();
                case 2 -> plugin.getConfigManager().getSecondPlaceSlot();
                case 3 -> plugin.getConfigManager().getThirdPlaceSlot();
                default -> -1;
            };
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to get podium slot for rank " + rank + ": " + e.getMessage());
            return -1;
        }
    }

    /**
     * FIXED: Create podium item with PLAYER HEAD and leader texture
     */
    private ItemStack createPodiumItem(PayoutWinner winner) {
        try {
            String itemPath = switch (winner.getRank()) {
                case 1 -> "podium-first";
                case 2 -> "podium-second";
                case 3 -> "podium-third";
                default -> "podium-first";
            };

            // FIXED: Always use PLAYER_HEAD for podium items to show leader texture
            ItemStack item = new ItemStack(Material.PLAYER_HEAD);

            // FIXED: Set the player head to show the leader's skin texture
            SkullMeta skullMeta = (SkullMeta) item.getItemMeta();
            if (skullMeta != null && winner.getLeader() != null && !winner.getLeader().trim().isEmpty()) {
                try {
                    // Try to get OfflinePlayer for better head texture support
                    OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(winner.getLeader());
                    skullMeta.setOwningPlayer(offlinePlayer);
                } catch (Exception e) {
                    // Fallback to owner name
                    skullMeta.setOwner(winner.getLeader());
                }

                // FIXED: Set display name and lore from config
                String name = MessageUtil.replacePlaceholders(plugin.getConfigManager().getItemName(itemPath),
                        "{island_name}", MessageUtil.sanitizeString(winner.getIslandName()),
                        "{leader}", MessageUtil.sanitizeString(winner.getLeader()));

                skullMeta.setDisplayName(name);

                List<String> lore = new ArrayList<>();
                for (String loreLine : plugin.getConfigManager().getItemLore(itemPath)) {
                    String processedLine = MessageUtil.replacePlaceholders(loreLine,
                            "{island_name}", MessageUtil.sanitizeString(winner.getIslandName()),
                            "{leader}", MessageUtil.sanitizeString(winner.getLeader()),
                            "{payout_points_formatted}", MessageUtil.formatNumber(winner.getPoints()),
                            "{currency_formatted}", MessageUtil.formatCurrency(plugin.getWalletManager().getCurrencyValue(winner.getPoints())));

                    lore.add(processedLine);
                }

                skullMeta.setLore(lore);
                item.setItemMeta(skullMeta);
            }

            return item;
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to create podium item for winner: " + winner.getIslandName(), e);
            return new ItemStack(Material.PLAYER_HEAD);
        }
    }

    /**
     * FIXED: Add navigation buttons with proper validation
     */


    /**
     * FIXED: Fill empty slots with enhanced validation
     */
    private void fillEmptySlots(Inventory gui) {
        if (!plugin.getConfigManager().isFillerEnabled()) {
            return;
        }

        try {
            String materialName = plugin.getConfigManager().getFillerMaterial();
            Material fillerMaterial = Material.valueOf(materialName);
            ItemStack filler = new ItemStack(fillerMaterial);
            ItemMeta fillerMeta = filler.getItemMeta();

            if (fillerMeta != null) {
                fillerMeta.setDisplayName(plugin.getConfigManager().getFillerName());

                List<String> fillerLore = plugin.getConfigManager().getFillerLore();
                if (!fillerLore.isEmpty()) {
                    fillerMeta.setLore(fillerLore);
                }
                filler.setItemMeta(fillerMeta);
            }

            List<Integer> specificSlots = plugin.getConfigManager().getFillerSpecificSlots();
            List<Integer> skipSlots = plugin.getConfigManager().getFillerSkipSlots();

            if (!specificSlots.isEmpty()) {
                // Fill only specific slots
                for (int slot : specificSlots) {
                    if (slot >= 0 && slot < gui.getSize() && gui.getItem(slot) == null) {
                        gui.setItem(slot, filler);
                    }
                }
            } else {
                // Fill all empty slots except skipped ones
                for (int slot = 0; slot < gui.getSize(); slot++) {
                    if (gui.getItem(slot) == null && !skipSlots.contains(slot)) {
                        gui.setItem(slot, filler);
                    }
                }
            }
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid filler material: " + e.getMessage());
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to fill empty slots", e);
        }
    }

    /**
     * FIXED: Add exit button with proper config reading
     */
    private void addExitButton(Inventory gui, String guiType) {
        try {
            if (!plugin.getConfigManager().isExitButtonEnabled(guiType)) {
                return;
            }

            int exitSlot = plugin.getConfigManager().getExitButtonSlot(guiType);
            if (exitSlot >= 0 && exitSlot < gui.getSize()) {
                ItemStack exitButton = createExitButtonItem(guiType);
                if (exitButton != null) {
                    gui.setItem(exitSlot, exitButton);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to add exit button for " + guiType, e);
        }
    }

    /**
     * FIXED: Create exit button with proper validation
     */
    private ItemStack createExitButtonItem(String guiType) {
        try {
            String materialName = plugin.getConfigManager().getExitButtonMaterial(guiType);
            Material material = Material.valueOf(materialName);
            ItemStack item = new ItemStack(material);
            ItemMeta meta = item.getItemMeta();

            if (meta != null) {
                meta.setDisplayName(plugin.getConfigManager().getExitButtonName(guiType));

                List<String> lore = new ArrayList<>();
                for (String loreLine : plugin.getConfigManager().getExitButtonLore(guiType)) {
                    lore.add(loreLine);
                }
                meta.setLore(lore);
                item.setItemMeta(meta);
            }

            return item;
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid exit button material for " + guiType + ": " + e.getMessage());
            return new ItemStack(Material.BARRIER);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to create exit button item for " + guiType, e);
            return new ItemStack(Material.BARRIER);
        }
    }

    /**
     * FIXED: Handle GUI click events with enhanced validation and proper cancellation
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        String title = event.getView().getTitle();

        // FIXED: More robust title checking
        String leaderboardTitle = plugin.getConfigManager().getLeaderboardTitle();
        String purchaseTitle = plugin.getConfigManager().getPurchaseTitle();
        String podiumTitlePrefix = plugin.getConfigManager().getPodiumTitle();

        boolean isOurGui = title.equals(leaderboardTitle) ||
                title.equals(purchaseTitle) ||
                title.startsWith(podiumTitlePrefix.split("\\{")[0].trim());

        if (!isOurGui) {
            return;
        }

        // CRITICAL: Cancel the event to prevent item movement
        event.setCancelled(true);

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) {
            return;
        }

        // FIXED: Check for exit button click first
        if (isExitButtonClick(event)) {
            event.getWhoClicked().closeInventory();
            return;
        }

        if (isOnCooldown(player)) {
            return;
        }

        setCooldown(player);

        try {
            // FIXED: More robust GUI type detection
            if (title.equals(purchaseTitle)) {
                handlePurchaseClick(player, event.getSlot());
            } else if (title.equals(leaderboardTitle)) {
                handleLeaderboardClick(player, event.getSlot());
            } else if (title.startsWith(podiumTitlePrefix.split("\\{")[0].trim())) {
                handlePodiumClick(player, event.getSlot());
            }

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error handling GUI click for " + player.getName(), e);
        }
    }

    /**
     * FIXED: Check for exit button click with better validation
     */
    private boolean isExitButtonClick(InventoryClickEvent event) {
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) {
            return false;
        }

        String title = event.getView().getTitle();
        String guiType = getGuiTypeFromTitle(title);

        if (guiType == null) return false;

        int clickedSlot = event.getSlot();
        int exitSlot = plugin.getConfigManager().getExitButtonSlot(guiType);

        return clickedSlot == exitSlot && plugin.getConfigManager().isExitButtonEnabled(guiType);
    }

    /**
     * FIXED: Get GUI type from title with better matching
     */
    private String getGuiTypeFromTitle(String title) {
        try {
            if (title.equals(plugin.getConfigManager().getLeaderboardTitle())) {
                return "leaderboard";
            } else if (title.equals(plugin.getConfigManager().getPurchaseTitle())) {
                return "purchase";
            } else if (title.startsWith(plugin.getConfigManager().getPodiumTitle().split("\\{")[0].trim())) {
                return "podium";
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error determining GUI type from title: " + title, e);
        }
        return null;
    }

    /**
     * FIXED: Handle clicks in purchase GUI with validation
     */
    private void handlePurchaseClick(Player player, int slot) {
        try {
            int buy1Slot = plugin.getConfigManager().getBuy1Slot();
            int buy10Slot = plugin.getConfigManager().getBuy10Slot();
            int buy100Slot = plugin.getConfigManager().getBuy100Slot();

            int pointsToBuy = 0;

            if (slot == buy1Slot) {
                pointsToBuy = 1;
            } else if (slot == buy10Slot) {
                pointsToBuy = 10;
            } else if (slot == buy100Slot) {
                pointsToBuy = 100;
            }

            if (pointsToBuy > 0) {
                boolean success = plugin.getWalletManager().purchasePayoutPoints(player, pointsToBuy);
                if (success) {
                    // FIXED: Refresh GUI after purchase
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (player.isOnline()) {
                            openPurchaseGui(player);
                        }
                    }, 1L);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error handling purchase click for " + player.getName(), e);
        }
    }

    /**
     * FIXED: Handle clicks in leaderboard GUI with validation
     */
    private void handleLeaderboardClick(Player player, int slot) {
        try {
            int accessSlot = plugin.getConfigManager().getPurchaseAccessSlot();
            int podiumSlot = plugin.getConfigManager().getPodiumAccessSlot();

            if (slot == accessSlot && player.hasPermission("islandwallet.purchase")) {
                openPurchaseGui(player);
            } else if (slot == podiumSlot && player.hasPermission("islandwallet.leaderboard")) {
                int currentSeason = plugin.getDatabaseManager().getCurrentSeason();
                int displaySeason = currentSeason > 1 ? currentSeason - 1 : currentSeason;
                openPodiumGui(player, displaySeason);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error handling leaderboard click for " + player.getName(), e);
        }
    }

    /**
     * FIXED: Handle clicks in podium GUI with validation
     */
    private void handlePodiumClick(Player player, int slot) {
        try {
            int currentSeason = plugin.getDatabaseManager().getCurrentSeason();

            if (slot == plugin.getConfigManager().getPreviousSeasonSlot()) {
                if (currentSeason > 1) {
                    openPodiumGui(player, currentSeason - 1);
                }
                return;
            }

            if (slot == plugin.getConfigManager().getNewCycleSlot()) {
                if (player.hasPermission("islandwallet.admin.reset")) {
                    plugin.getPayoutManager().forceResetCycle();
                    String message = plugin.getConfigManager().getMessage("admin-reset-success");
                    player.sendMessage(plugin.getConfigManager().getPrefix() + message);
                    player.closeInventory();
                } else {
                    String message = plugin.getConfigManager().getMessage("no-permission");
                    player.sendMessage(plugin.getConfigManager().getPrefix() + message);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error handling podium click for " + player.getName(), e);
        }
    }

    // FIXED: Cooldown management with proper validation
    private boolean isOnCooldown(Player player) {
        Long lastClick = guiCooldowns.get(player.getUniqueId());
        if (lastClick == null) {
            return false;
        }
        return (System.currentTimeMillis() - lastClick) < GUI_COOLDOWN_MS;
    }

    private void setCooldown(Player player) {
        guiCooldowns.put(player.getUniqueId(), System.currentTimeMillis());
    }

    private void sendErrorMessage(Player player, String messageKey) {
        try {
            String message = plugin.getConfigManager().getMessage(messageKey);
            player.sendMessage(plugin.getConfigManager().getPrefix() + message);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to send error message to " + player.getName(), e);
        }
    }

    public void reloadConfiguration() {
        guiCooldowns.clear();
        plugin.getLogger().info("GUI configuration reloaded");
    }

    public void cleanupCooldowns() {
        long cutoffTime = System.currentTimeMillis() - (5 * 60 * 1000);
        guiCooldowns.entrySet().removeIf(entry -> entry.getValue() < cutoffTime);
    }
}