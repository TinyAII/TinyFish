package com.tinyaii.tinyfish.gui;

import com.tinyaii.tinyfish.config.ConfigManager;
import com.tinyaii.tinyfish.economy.EconomyProvider;
import com.tinyaii.tinyfish.fish.AttributeMath;
import com.tinyaii.tinyfish.fish.FishDefinition;
import com.tinyaii.tinyfish.fish.FishService;
import com.tinyaii.tinyfish.service.CollectionService;
import com.tinyaii.tinyfish.service.LeaderboardService;
import com.tinyaii.tinyfish.service.PlayerDataStore;
import com.tinyaii.tinyfish.util.Texts;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 构建各个 GUI 页面。
 *
 * <p>所有页面都是只读展示 + 点格子触发动作，**不允许玩家往里面放东西**（由 GuiListener 拦截）。
 */
public final class GuiRender {

    public static final int MAIN_SIZE = 27;
    public static final int PAGE_SIZE = 54;
    /** 列表区可用槽位（底部 9 格留给功能按钮）。 */
    public static final int LIST_SLOTS = 45;

    private final ConfigManager config;
    private final PlayerDataStore dataStore;
    private final FishService fishService;
    private final CollectionService collection;
    private final EconomyProvider economy;
    private final LeaderboardService leaderboard;

    public GuiRender(ConfigManager config, PlayerDataStore dataStore, FishService fishService,
                     CollectionService collection, EconomyProvider economy, LeaderboardService leaderboard) {
        this.config = config;
        this.dataStore = dataStore;
        this.fishService = fishService;
        this.collection = collection;
        this.economy = economy;
        this.leaderboard = leaderboard;
    }

    // ────────────────────────── 主菜单 ──────────────────────────

    public Inventory main(Player player) {
        GuiHolder holder = new GuiHolder(GuiType.MAIN, 1);
        Inventory inv = Bukkit.createInventory(holder, MAIN_SIZE, Texts.color("&8TinyFish · 钓鱼"));
        holder.setInventory(inv);

        int fishCount = config.getFishDefinitions().size();
        Map<String, Double> attrs = collection.computeAttributes(player.getUniqueId());
        double balance = economy.balance(player.getUniqueId());

        inv.setItem(11, icon(Material.matchMaterial("COD"), "&b我的鱼箱",
                Arrays.asList(
                        "&7存放钓到的鱼获",
                        "&7卖出可换 &e" + config.getCurrencyName(),
                        "",
                        "&e点击查看")));

        inv.setItem(13, icon(Material.matchMaterial("FISHING_ROD"), "&d鱼获收集箱",
                Arrays.asList(
                        "&7把鱼投入可换取 &a永久属性",
                        "&7当前属性: " + collection.describe(attrs),
                        "",
                        "&e点击查看")));

        inv.setItem(15, icon(Material.matchMaterial("GOLD_INGOT"), "&6排行榜",
                Arrays.asList(
                        "&7口径: " + (("total".equalsIgnoreCase(config.getLeaderboardMetric())) ? "累计克重" : "单条最大克重"),
                        "&7鱼种: &f" + fishCount + " 种",
                        "",
                        "&e点击查看")));

        inv.setItem(22, icon(Material.matchMaterial("BARRIER"), "&c关闭", null));

        fillEmpty(inv, MAIN_SIZE);
        return inv;
    }

    // ────────────────────────── 鱼箱 ──────────────────────────

    public Inventory box(Player player, int page) {
        GuiHolder holder = new GuiHolder(GuiType.BOX, page);
        Inventory inv = Bukkit.createInventory(holder, PAGE_SIZE,
                Texts.color("&8鱼箱 · 第 " + page + " 页"));
        holder.setInventory(inv);

        if (config.isItemDelivery()) {
            inv.setItem(22, icon(Material.matchMaterial("BOOK"), "&e物品模式",
                    Arrays.asList(
                            "&7当前鱼获以物品形式存在背包里",
                            "&7用 &f/tf sell &7直接卖出",
                            "",
                            "&8想用鱼箱？把 config 的",
                            "&8fishing.delivery 改成 virtual")));
            fillEmpty(inv, PAGE_SIZE);
            return inv;
        }

        Map<String, PlayerDataStore.BoxEntry> box = dataStore.getFishBox(player.getUniqueId());
        int slot = 0;
        double totalMoney = 0.0;
        int totalCount = 0;
        for (Map.Entry<String, PlayerDataStore.BoxEntry> entry : box.entrySet()) {
            if (slot >= LIST_SLOTS) {
                break;
            }
            PlayerDataStore.BoxEntry value = entry.getValue();
            FishDefinition def = config.fishById(entry.getKey());
            String name = def == null ? "&c" + entry.getKey() : def.getDisplayName();
            List<String> lore = new ArrayList<String>();
            lore.add("&7条数: &f" + value.count);
            lore.add("&7最大: &e" + value.best + "g");
            lore.add("&7累计: &7" + value.total + "g");
            if (def != null) {
                double money = FishService.boxValue(value.count, value.total,
                        def.getBasePrice(), def.getPricePerGram());
                lore.add("&7价值: &6" + economy.format(money));
                lore.add("");
                lore.add("&e左键 &7投入收集箱（换属性）");
                lore.add("&7  其余同种鱼会自动卖出");
                totalMoney += money;
                totalCount += value.count;
            } else {
                lore.add("&c该鱼种已从配置移除");
            }
            Material mat = def == null ? Material.matchMaterial("BARRIER")
                    : materialOf(def.getFallbackMaterial());
            holder.bindSlot(slot, entry.getKey());
            inv.setItem(slot, icon(mat, name, lore));
            slot++;
        }

        if (box.isEmpty()) {
            inv.setItem(22, icon(Material.matchMaterial("BARRIER"), "&7鱼箱是空的", null));
        }

        // 底部功能栏
        inv.setItem(45, icon(Material.matchMaterial("GOLD_INGOT"), "&6全部卖出",
                Arrays.asList(
                        "&7共 &f" + totalCount + " &7条",
                        "&7预计 &e" + economy.format(totalMoney),
                        "",
                        "&e点击卖出")));
        inv.setItem(49, icon(Material.matchMaterial("ARROW"), "&f返回主菜单", null));
        inv.setItem(53, icon(Material.matchMaterial("BARRIER"), "&c关闭", null));

        fillEmpty(inv, PAGE_SIZE);
        return inv;
    }

    // ────────────────────────── 收集箱 ──────────────────────────

    public Inventory collection(Player player) {
        GuiHolder holder = new GuiHolder(GuiType.COLLECTION, 1);
        Inventory inv = Bukkit.createInventory(holder, PAGE_SIZE, Texts.color("&8鱼获收集箱"));
        holder.setInventory(inv);

        Map<String, Integer> collected = dataStore.getCollection(player.getUniqueId());
        int slot = 0;
        for (Map.Entry<String, Integer> entry : collected.entrySet()) {
            if (slot >= LIST_SLOTS) {
                break;
            }
            FishDefinition def = config.fishById(entry.getKey());
            String name = def == null ? "&c" + entry.getKey() : def.getDisplayName();
            List<String> lore = new ArrayList<String>();
            lore.add("&7克重: &e" + entry.getValue() + "g");
            if (def != null) {
                Map<String, Double> single = AttributeMath.forCatch(
                        def.getAttributesPerGram(), entry.getValue(), config.getTiers());
                lore.add("&7提供属性:");
                for (Map.Entry<String, Double> attr : single.entrySet()) {
                    lore.add("  &a" + AttributeMath.formatLine(attr.getKey(), attr.getValue()));
                }
            }
            lore.add("");
            lore.add("&c右键 &7取回（属性会相应减少）");
            Material mat = def == null ? Material.matchMaterial("BARRIER")
                    : materialOf(def.getFallbackMaterial());
            holder.bindSlot(slot, entry.getKey());
            inv.setItem(slot, icon(mat, name, lore));
            slot++;
        }

        if (collected.isEmpty()) {
            inv.setItem(22, icon(Material.matchMaterial("BARRIER"), "&7还没有投入任何鱼获",
                    Arrays.asList("&7去鱼箱里点鱼投入吧")));
        }

        Map<String, Double> total = collection.computeAttributes(player.getUniqueId());
        inv.setItem(45, icon(Material.matchMaterial("NETHER_STAR"), "&d当前总属性",
                Arrays.asList(
                        collection.describe(total),
                        "",
                        "&7输出: &f" + collection.sink().name()
                                + (collection.sink().available() ? "" : " &c（仅展示）"),
                        "&7上限: &f" + config.getAttributeMaxTotal())));
        inv.setItem(49, icon(Material.matchMaterial("ARROW"), "&f返回主菜单", null));
        inv.setItem(53, icon(Material.matchMaterial("BARRIER"), "&c关闭", null));

        fillEmpty(inv, PAGE_SIZE);
        return inv;
    }

    // ────────────────────────── 排行榜 ──────────────────────────

    public Inventory top(int page) {
        GuiHolder holder = new GuiHolder(GuiType.TOP, page);
        int pageSize = LIST_SLOTS;
        int pages = leaderboard.pageCount(pageSize);
        int current = Math.max(1, Math.min(page, pages));
        Inventory inv = Bukkit.createInventory(holder, PAGE_SIZE,
                Texts.color("&8排行榜 · 第 " + current + "/" + pages + " 页"));
        holder.setInventory(inv);

        List<LeaderboardService.Row> rows = leaderboard.page(current, pageSize);
        if (rows.isEmpty()) {
            inv.setItem(22, icon(Material.matchMaterial("BARRIER"), "&7还没有人上榜", null));
        }
        int index = (current - 1) * pageSize + 1;
        int slot = 0;
        for (LeaderboardService.Row row : rows) {
            if (slot >= pageSize) {
                break;
            }
            List<String> lore = new ArrayList<String>();
            lore.add("&7排名: &e#" + index);
            if (row.fishId != null) {
                FishDefinition def = config.fishById(row.fishId);
                lore.add("&7鱼种: &f" + (def == null ? row.fishId : def.getDisplayName()));
            }
            lore.add("&7成绩: &b" + row.weight + "g");
            inv.setItem(slot++, head(row.uuid, "&e#" + index + " &f" + row.name, lore));
            index++;
        }

        inv.setItem(45, icon(Material.matchMaterial("ARROW"), "&f上一页", null));
        inv.setItem(49, icon(Material.matchMaterial("ARROW"), "&f返回主菜单", null));
        inv.setItem(53, icon(Material.matchMaterial("ARROW"), "&f下一页", null));

        fillEmpty(inv, PAGE_SIZE);
        return inv;
    }

    // ────────────────────────── 工具 ──────────────────────────

    private ItemStack icon(Material material, String name, List<String> lore) {
        ItemStack stack = new ItemStack(material == null ? Material.STONE : material, 1);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        meta.setDisplayName(Texts.color(name));
        if (lore != null && !lore.isEmpty()) {
            List<String> colored = new ArrayList<String>(lore.size());
            for (String line : lore) {
                colored.add(Texts.color(line));
            }
            meta.setLore(colored);
        }
        stack.setItemMeta(meta);
        return stack;
    }

    /** 玩家头颅（离线也能取到）。 */
    private ItemStack head(java.util.UUID uuid, String name, List<String> lore) {
        Material material = Material.matchMaterial("PLAYER_HEAD");
        if (material == null) {
            material = Material.matchMaterial("SKULL_ITEM");
        }
        ItemStack stack = new ItemStack(material == null ? Material.STONE : material, 1);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        meta.setDisplayName(Texts.color(name));
        if (lore != null && !lore.isEmpty()) {
            List<String> colored = new ArrayList<String>(lore.size());
            for (String line : lore) {
                colored.add(Texts.color(line));
            }
            meta.setLore(colored);
        }
        if (meta instanceof SkullMeta) {
            try {
                ((SkullMeta) meta).setOwningPlayer(Bukkit.getOfflinePlayer(uuid));
            } catch (Throwable ignored) {
                // 极老核心可能不支持，退化成普通头颅
            }
        }
        stack.setItemMeta(meta);
        return stack;
    }

    private Material materialOf(String name) {
        Material material = Material.matchMaterial(name);
        if (material == null || material == Material.AIR) {
            material = Material.matchMaterial("COD");
        }
        return material == null ? Material.STONE : material;
    }

    /** 空白槽位填灰色玻璃，避免看起来像能放东西。 */
    private void fillEmpty(Inventory inv, int size) {
        Material glass = Material.matchMaterial("GRAY_STAINED_GLASS_PANE");
        if (glass == null) {
            glass = Material.matchMaterial("STAINED_GLASS_PANE");
        }
        if (glass == null) {
            return;
        }
        ItemStack filler = icon(glass, " ", null);
        for (int i = 0; i < size; i++) {
            if (inv.getItem(i) == null) {
                inv.setItem(i, filler);
            }
        }
    }

    public LeaderboardService leaderboard() {
        return leaderboard;
    }

    public CollectionService collectionService() {
        return collection;
    }
}
