package com.tinyaii.tinyfish.command;

import com.tinyaii.tinyfish.config.ConfigManager;
import com.tinyaii.tinyfish.economy.EconomyProvider;
import com.tinyaii.tinyfish.fish.FishCatch;
import com.tinyaii.tinyfish.fish.FishDefinition;
import com.tinyaii.tinyfish.fish.FishService;
import com.tinyaii.tinyfish.gui.GuiListener;
import com.tinyaii.tinyfish.service.AutoFishingService;
import com.tinyaii.tinyfish.service.CollectionService;
import com.tinyaii.tinyfish.service.PlayerDataStore;
import com.tinyaii.tinyfish.util.Texts;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * /tf 指令（别名 /tinyfish、/钓鱼）。
 *
 * <p>主力交互在 GUI（{@code /tf gui}）；文本子命令保留给脚本、快捷操作和排查用。
 */
public final class TinyFishCommand implements CommandExecutor, TabCompleter {

    private static final String PERM_USE = "tinyfish.use";
    private static final String PERM_SELL = "tinyfish.sell";
    private static final String PERM_ADMIN = "tinyfish.admin";

    private final ConfigManager config;
    private final PlayerDataStore dataStore;
    private final FishService fishService;
    private final EconomyProvider economy;
    private final CollectionService collection;
    private final GuiListener gui;
    private final AutoFishingService autoFishing;

    public TinyFishCommand(ConfigManager config, PlayerDataStore dataStore, FishService fishService,
                           EconomyProvider economy, CollectionService collection, GuiListener gui,
                           AutoFishingService autoFishing) {
        this.config = config;
        this.dataStore = dataStore;
        this.fishService = fishService;
        this.economy = economy;
        this.collection = collection;
        this.gui = gui;
        this.autoFishing = autoFishing;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            help(sender, label);
            return true;
        }
        String sub = args[0].toLowerCase();

        if (sub.equals("reload")) {
            return reload(sender);
        }
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "该子命令只能由玩家执行。");
            return true;
        }
        Player player = (Player) sender;

        if (sub.equals("auto") || sub.equals("自动")) {
            return toggleAuto(player);
        }
        if (sub.equals("gui") || sub.equals("菜单") || sub.equals("面板")) {
            return openGui(player);
        }
        if (sub.equals("box") || sub.equals("鱼箱")) {
            return showBox(player);
        }
        if (sub.equals("sell") || sub.equals("卖") || sub.equals("出售")) {
            return sell(player);
        }
        if (sub.equals("collect") || sub.equals("收集箱")) {
            return showCollection(player);
        }
        if (sub.equals("deposit") || sub.equals("投入")) {
            return deposit(player, args);
        }
        if (sub.equals("withdraw") || sub.equals("取回")) {
            return withdraw(player, args);
        }
        if (sub.equals("info") || sub.equals("信息")) {
            return info(player);
        }
        if (sub.equals("give") || sub.equals("发放")) {
            return give(player, args);
        }
        help(sender, label);
        return true;
    }

    private void help(CommandSender sender, String label) {
        sender.sendMessage(ChatColor.GOLD + "===== TinyFish 钓鱼 =====");
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " gui" + ChatColor.WHITE + "  打开图形界面（推荐）");
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " auto" + ChatColor.WHITE + "  开关自动钓鱼");
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " box" + ChatColor.WHITE + "  文本查看鱼箱");
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " sell" + ChatColor.WHITE + "  卖出全部鱼获");
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " collect" + ChatColor.WHITE + "  文本查看收集箱与属性");
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " deposit <鱼种>" + ChatColor.WHITE
                + "  投入收集箱（取最好的一条，其余自动卖出）");
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " withdraw <鱼种>" + ChatColor.WHITE + "  从收集箱取回");
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " info" + ChatColor.WHITE + "  当前配置概览");
        if (sender.hasPermission(PERM_ADMIN)) {
            sender.sendMessage(ChatColor.YELLOW + "/" + label + " reload" + ChatColor.WHITE + "  重载配置");
            sender.sendMessage(ChatColor.YELLOW + "/" + label + " give <鱼种> [克重]" + ChatColor.WHITE
                    + "  给自己发一条鱼（测试用）");
        }
        sender.sendMessage(ChatColor.GRAY + "投递模式: " + config.getDelivery()
                + " | 鱼种: " + config.getFishDefinitions().size()
                + " | 属性: " + config.getAttributeSink());
    }

    private boolean openGui(Player player) {
        if (!player.hasPermission(PERM_USE)) {
            player.sendMessage(ChatColor.RED + "你没有权限。");
            return true;
        }
        gui.openMain(player);
        return true;
    }

    private boolean toggleAuto(Player player) {
        if (!player.hasPermission("tinyfish.auto")) {
            player.sendMessage(ChatColor.RED + "你没有自动钓鱼权限。");
            return true;
        }
        if (!config.isAutoFishingEnabled()) {
            player.sendMessage(ChatColor.RED + "服务器已关闭自动钓鱼功能。");
            return true;
        }
        boolean on = autoFishing.toggle(player);
        player.sendMessage(Texts.color(on
                ? "&a✔ 自动钓鱼已开启（再输入一次 /tf auto 关闭）"
                : "&7⏸ 自动钓鱼已关闭"));
        return true;
    }

    // ────────────────────────── 鱼箱 ──────────────────────────

    private boolean showBox(Player player) {
        if (!player.hasPermission(PERM_USE)) {
            player.sendMessage(ChatColor.RED + "你没有权限。");
            return true;
        }
        if (config.isItemDelivery()) {
            player.sendMessage(Texts.color("&e当前是物品模式 —— 鱼获在背包里，直接 /tf sell 卖出。"));
            return true;
        }
        Map<String, PlayerDataStore.BoxEntry> box = dataStore.getFishBox(player.getUniqueId());
        player.sendMessage(ChatColor.GOLD + "===== 我的鱼箱 =====");
        if (box.isEmpty()) {
            player.sendMessage(ChatColor.GRAY + "还没有鱼获，去钓鱼吧。");
            return true;
        }
        double totalMoney = 0.0;
        int totalCount = 0;
        for (Map.Entry<String, PlayerDataStore.BoxEntry> entry : box.entrySet()) {
            FishDefinition def = config.fishById(entry.getKey());
            PlayerDataStore.BoxEntry value = entry.getValue();
            String name = def == null
                    ? "&c" + entry.getKey() + "（已从配置移除）"
                    : def.getDisplayName();
            player.sendMessage(Texts.color(" &7- &f" + name
                    + " &7×" + value.count
                    + " &8| 最大 &e" + value.best + "g"
                    + " &8| 累计 &7" + value.total + "g"));
            if (def != null) {
                totalMoney += FishService.boxValue(value.count, value.total,
                        def.getBasePrice(), def.getPricePerGram());
                totalCount += value.count;
            }
        }
        player.sendMessage(Texts.color("&7共 &f" + totalCount + " &7条，预计可卖 &e"
                + economy.format(totalMoney)));
        player.sendMessage(Texts.color("&8想把鱼换成永久属性？用 /tf deposit <鱼种>"));
        return true;
    }

    // ────────────────────────── 卖出 ──────────────────────────

    private boolean sell(Player player) {
        if (!player.hasPermission(PERM_SELL)) {
            player.sendMessage(ChatColor.RED + "你没有出售权限。");
            return true;
        }
        FishService.SellResult result = fishService.sellAll(player);
        if (result.error != null) {
            player.sendMessage(ChatColor.RED + result.error);
            return true;
        }
        if (result.isEmpty()) {
            player.sendMessage(ChatColor.GRAY + "没有可卖出的鱼获。");
            return true;
        }
        player.sendMessage(Texts.color("&a✔ 卖出了 &f" + result.count + " &a条鱼获，获得 &e"
                + economy.format(result.amount)));
        return true;
    }

    // ────────────────────────── 收集箱 ──────────────────────────

    private boolean showCollection(Player player) {
        if (!player.hasPermission("tinyfish.collect")) {
            player.sendMessage(ChatColor.RED + "你没有权限。");
            return true;
        }
        Map<String, Integer> collected = dataStore.getCollection(player.getUniqueId());
        player.sendMessage(ChatColor.GOLD + "===== 鱼获收集箱 =====");
        if (collected.isEmpty()) {
            player.sendMessage(ChatColor.GRAY + "还没有投入任何鱼获。");
        } else {
            for (Map.Entry<String, Integer> entry : collected.entrySet()) {
                FishDefinition def = config.fishById(entry.getKey());
                String name = def == null ? "&c" + entry.getKey() : def.getDisplayName();
                String attrs = "";
                if (def != null) {
                    Map<String, Double> single = com.tinyaii.tinyfish.fish.AttributeMath.forCatch(
                            def.getAttributesPerGram(), entry.getValue(), config.getTiers());
                    attrs = " &8→ " + collection.describe(single);
                }
                player.sendMessage(Texts.color(" &7- &f" + name + " &e" + entry.getValue() + "g" + attrs));
            }
        }
        Map<String, Double> total = collection.computeAttributes(player.getUniqueId());
        player.sendMessage(Texts.color("&7当前总属性: " + collection.describe(total)));
        player.sendMessage(Texts.color("&8属性输出: " + collection.sink().name()
                + (collection.sink().available() ? "" : "（不可用，仅展示）")));
        return true;
    }

    private boolean deposit(Player player, String[] args) {
        if (!player.hasPermission("tinyfish.collect")) {
            player.sendMessage(ChatColor.RED + "你没有权限。");
            return true;
        }
        String fishId = args.length >= 2 ? args[1] : singleCandidate(player);
        if (fishId == null) {
            player.sendMessage(Texts.color("&c用法: /tf deposit <鱼种>"));
            return true;
        }
        CollectionService.DepositResult result = collection.deposit(player, fishId);
        player.sendMessage(Texts.color((result.ok ? "&a✔ " : "&c✘ ") + result.message));
        return true;
    }

    private boolean withdraw(Player player, String[] args) {
        if (!player.hasPermission("tinyfish.collect")) {
            player.sendMessage(ChatColor.RED + "你没有权限。");
            return true;
        }
        String fishId = args.length >= 2 ? args[1] : null;
        if (fishId == null) {
            player.sendMessage(Texts.color("&c用法: /tf withdraw <鱼种>"));
            return true;
        }
        CollectionService.DepositResult result = collection.withdraw(player, fishId);
        player.sendMessage(Texts.color((result.ok ? "&a✔ " : "&c✘ ") + result.message));
        return true;
    }

    /** 只带一个鱼种时允许省略参数。 */
    private String singleCandidate(Player player) {
        Map<String, PlayerDataStore.BoxEntry> box = dataStore.getFishBox(player.getUniqueId());
        if (box.size() == 1) {
            return box.keySet().iterator().next();
        }
        return null;
    }

    // ────────────────────────── 其它 ──────────────────────────

    private boolean info(Player player) {
        player.sendMessage(ChatColor.GOLD + "===== TinyFish 当前配置 =====");
        player.sendMessage(Texts.color("&7投递模式: &f" + config.getDelivery()));
        player.sendMessage(Texts.color("&7属性输出: &f" + config.getAttributeSink()
                + " &7| 实际: &f" + collection.sink().name()
                + " &7| 每属性上限: &f" + config.getAttributeMaxTotal()));
        player.sendMessage(Texts.color("&7经济来源: &f" + economy.name()));
        player.sendMessage(Texts.color("&7余额: &e" + economy.format(economy.balance(player.getUniqueId()))));
        player.sendMessage(Texts.color("&7鱼种: &f" + config.getFishDefinitions().size() + " 种"));
        for (FishDefinition def : config.getFishDefinitions()) {
            player.sendMessage(Texts.color(" &8- &f" + def.getDisplayName()
                    + " &7权重 " + (int) def.getChance()
                    + " &8| " + def.getMinWeight() + "~" + def.getMaxWeight() + "g"));
        }
        return true;
    }

    private boolean give(Player player, String[] args) {
        if (!player.hasPermission(PERM_ADMIN)) {
            player.sendMessage(ChatColor.RED + "你没有权限。");
            return true;
        }
        if (args.length < 2) {
            player.sendMessage(Texts.color("&c用法: /tf give <鱼种ID> [克重]"));
            return true;
        }
        FishDefinition def = config.fishById(args[1]);
        if (def == null) {
            player.sendMessage(Texts.color("&c找不到鱼种: " + args[1]));
            return true;
        }
        int weight = def.rollWeight();
        if (args.length >= 3) {
            try {
                weight = Math.max(1, Integer.parseInt(args[2]));
            } catch (NumberFormatException e) {
                player.sendMessage(Texts.color("&c克重必须是数字"));
                return true;
            }
        }
        FishCatch fishCatch = new FishCatch(def, weight, def.calculatePrice(weight),
                Texts.color(def.getDisplayName()));
        fishService.delivery().deliver(player, fishCatch);
        player.sendMessage(Texts.color("&a已发放 &f" + fishCatch.displayName
                + " &7(" + weight + "g) &7via " + fishService.delivery().name()));
        return true;
    }

    private boolean reload(CommandSender sender) {
        if (!sender.hasPermission(PERM_ADMIN)) {
            sender.sendMessage(ChatColor.RED + "你没有权限。");
            return true;
        }
        config.reload();
        sender.sendMessage(ChatColor.GREEN + "✔ 配置已重载（鱼种 " + config.getFishDefinitions().size()
                + " 种，投递模式 " + config.getDelivery() + "）。");
        if (!config.getDelivery().equals(fishService.delivery().name())) {
            sender.sendMessage(ChatColor.YELLOW + "⚠ 投递模式改动需要重启服务器才生效。");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> subs = new ArrayList<String>(
                    Arrays.asList("gui", "auto", "box", "sell", "collect", "deposit", "withdraw", "info"));
            if (sender.hasPermission(PERM_ADMIN)) {
                subs.add("reload");
                subs.add("give");
            }
            return filter(subs, args[0]);
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (sub.equals("give") || sub.equals("deposit") || sub.equals("withdraw")) {
                return filter(fishIds(), args[1]);
            }
        }
        return Collections.emptyList();
    }

    private List<String> fishIds() {
        List<String> ids = new ArrayList<String>();
        for (FishDefinition def : config.getFishDefinitions()) {
            ids.add(def.getId());
        }
        return ids;
    }

    private static List<String> filter(List<String> candidates, String prefix) {
        List<String> out = new ArrayList<String>();
        String p = prefix == null ? "" : prefix.toLowerCase();
        for (String c : candidates) {
            if (c.toLowerCase().startsWith(p)) {
                out.add(c);
            }
        }
        return out;
    }
}
