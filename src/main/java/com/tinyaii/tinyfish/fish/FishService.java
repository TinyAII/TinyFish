package com.tinyaii.tinyfish.fish;

import com.tinyaii.tinyfish.config.ConfigManager;
import com.tinyaii.tinyfish.economy.EconomyProvider;
import com.tinyaii.tinyfish.hook.NeigeItemsBridge;
import com.tinyaii.tinyfish.service.PlayerDataStore;
import com.tinyaii.tinyfish.util.Expression;
import com.tinyaii.tinyfish.util.Texts;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 鱼获核心业务：抽取 → 定价 → 交付 → 卖出。
 */
public final class FishService {

    private final ConfigManager config;
    private final PlayerDataStore dataStore;
    private final EconomyProvider economy;
    private final CatchDelivery delivery;
    private final ItemDelivery itemDelivery;
    /** 咬钩加速的跨版本实现（启动时探测一次）。 */
    private final HookSpeed hookSpeed;

    public FishService(ConfigManager config, PlayerDataStore dataStore, EconomyProvider economy,
                       NeigeItemsBridge neige) {
        this.config = config;
        this.dataStore = dataStore;
        this.economy = economy;
        this.itemDelivery = new ItemDelivery(config, neige);
        this.hookSpeed = HookSpeed.detect();
        this.delivery = config.isItemDelivery()
                ? itemDelivery
                : new VirtualDelivery(dataStore);
    }

    public CatchDelivery delivery() {
        return delivery;
    }

    // ────────────────────────── 抽取 ──────────────────────────

    /**
     * 按 chance 累加权重抽一种鱼。
     *
     * <p>用「累加 + nextDouble(总权重)」而不是「nextInt(100)」：
     * 这样 chance 加起来不是 100 也不会出错（服主改配置时很常见）。
     */
    public FishDefinition rollDefinition() {
        List<FishDefinition> list = config.getFishDefinitions();
        if (list.isEmpty()) {
            return null;
        }
        double total = 0.0;
        for (FishDefinition def : list) {
            total += def.getChance();
        }
        if (total <= 0.0) {
            return list.get(ThreadLocalRandom.current().nextInt(list.size()));
        }
        double random = ThreadLocalRandom.current().nextDouble(total);
        double cursor = 0.0;
        for (FishDefinition def : list) {
            cursor += def.getChance();
            if (random < cursor) {
                return def;
            }
        }
        return list.get(list.size() - 1);
    }

    /** 抽一条鱼并算好克重与价格，不发放。 */
    public FishCatch createCatch() {
        FishDefinition definition = rollDefinition();
        if (definition == null) {
            return null;
        }
        int weight = definition.rollWeight();
        double price = definition.calculatePrice(weight);
        return new FishCatch(definition, weight, price, Texts.color(definition.getDisplayName()));
    }

    // ────────────────────────── 发放 ──────────────────────────

    /**
     * 钓到一条鱼：抽取 → 交付 → 提示 → 大鱼广播。
     *
     * <p>「钓鱼倍率」生效时会一次给多条（每个都独立抽取，克重各不相同）。
     *
     * @param quiet 自动钓鱼时为 true，跳过单条聊天提示（不然会刷屏）
     */
    public FishCatch giveCatch(Player player, boolean quiet) {
        int extra = extraCatchCount(player);
        FishCatch first = null;
        int count = 0;
        double totalPrice = 0.0;
        for (int i = 0; i <= extra; i++) {
            FishCatch fishCatch = createCatch();
            if (fishCatch == null) {
                break;
            }
            delivery.deliver(player, fishCatch);
            if (first == null) {
                first = fishCatch;
            }
            count++;
            totalPrice += fishCatch.price;
            broadcastIfBig(player, fishCatch);
        }
        if (!quiet && first != null) {
            if (count == 1) {
                player.sendMessage(Texts.color("&a✦ 钓到了 " + first.displayName
                        + " &7(" + first.weight + "g) &e" + economy.format(first.price)));
            } else {
                player.sendMessage(Texts.color("&a✦ 钓到了 &f" + count + " &a条鱼获"
                        + " &7(首条 " + first.displayName + " " + first.weight + "g)"
                        + " &e" + economy.format(totalPrice)));
            }
        }
        return first;
    }

    // ────────────────────────── 加成 ──────────────────────────

    /**
     * 钓鱼速度倍率（≥1.0）。
     *
     * <p>来源 = 配置公式 + 鱼竿 lore 里写的百分比，两者相加。
     * 结果被 {@code fishing.max-speed-multiplier} 夹住，防止公式配错导致秒上鱼。
     */
    public double speedMultiplier(Player player) {
        double percent = evaluate(config.getSpeedFormula(), player)
                + lorePercent(player, config.getSpeedLoreKey());
        double multiplier = 1.0 + percent / 100.0;
        return Math.max(1.0, Math.min(config.getMaxSpeedMultiplier(), multiplier));
    }

    /** 额外鱼获条数（0 = 只有 1 条）。小数部分按概率进位。 */
    public int extraCatchCount(Player player) {
        double percent = evaluate(config.getMultiplierFormula(), player)
                + lorePercent(player, config.getMultiplierLoreKey());
        if (percent <= 0) {
            return 0;
        }
        double exact = percent / 100.0;
        int base = (int) Math.floor(exact);
        if (ThreadLocalRandom.current().nextDouble() < (exact - base)) {
            base++;
        }
        return Math.max(0, Math.min(base, config.getMaxExtraCatches()));
    }

    /** 对刚抛出的钩子应用速度加成。 */
    public void applyHookSpeed(Player player, FishHook hook) {
        try {
            hookSpeed.apply(hook, speedMultiplier(player));
        } catch (Throwable ignored) {
            // 加速失败不影响钓鱼本身
        }
    }

    public String hookSpeedMode() {
        return hookSpeed.mode();
    }

    /**
     * 求配置里的公式值。支持四则运算；装了 PlaceholderAPI 时先把 %变量% 替换掉。
     *
     * <p>public 是因为自动钓鱼的「失败概率」也要用同一套求值逻辑。
     */
    public double evaluate(String formula, Player player) {
        if (formula == null || formula.trim().isEmpty()) {
            return 0.0;
        }
        String text = applyPlaceholders(player, formula);
        // PAPI 返回的可能是 "1,000" 这种带千分位的，去掉逗号再算
        text = text.replace(",", "");
        return Expression.eval(text, 0.0);
    }

    private String applyPlaceholders(Player player, String text) {
        if (text == null || text.indexOf('%') < 0) {
            return text;
        }
        try {
            Class<?> papi = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
            Method method = papi.getMethod("setPlaceholders", Player.class, String.class);
            Object result = method.invoke(null, player, text);
            return result instanceof String ? (String) result : text;
        } catch (Throwable ignored) {
            // 没装 PAPI 或版本不匹配：原样返回，交给表达式求值器决定
            return text;
        }
    }

    /**
     * 读鱼竿 lore 里的百分比，例如一行「钓鱼速度：50%」。
     * 找不到 key 或没有数字都算 0。
     */
    private double lorePercent(Player player, String key) {
        if (key == null || key.trim().isEmpty()) {
            return 0.0;
        }
        ItemStack rod = player.getInventory().getItemInMainHand();
        if (rod == null || rod.getType() == Material.AIR || !rod.hasItemMeta()) {
            return 0.0;
        }
        ItemMeta meta = rod.getItemMeta();
        if (meta == null || !meta.hasLore()) {
            return 0.0;
        }
        double total = 0.0;
        for (String line : meta.getLore()) {
            String plain = ChatColor.stripColor(line);
            if (plain == null) {
                continue;
            }
            int index = plain.indexOf(key);
            if (index < 0) {
                continue;
            }
            Double value = firstNumber(plain.substring(index + key.length()));
            if (value != null) {
                total += value;
            }
        }
        return total;
    }

    /** 从文本里取第一个数字（支持负号与小数点）。 */
    private static Double firstNumber(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        int start = -1;
        int end = -1;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            boolean numeric = (c >= '0' && c <= '9') || c == '.' || c == '-';
            if (numeric) {
                if (start < 0) {
                    start = i;
                }
                end = i + 1;
            } else if (start >= 0) {
                break;
            }
        }
        if (start < 0 || end <= start) {
            return null;
        }
        try {
            return Double.parseDouble(text.substring(start, end));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void broadcastIfBig(Player player, FishCatch fishCatch) {
        if (!config.isBroadcastEnabled() || fishCatch.weight < config.getBroadcastMinWeight()) {
            return;
        }
        String message = config.getBroadcastFormat()
                .replace("%player%", player.getName())
                .replace("%fish_name%", fishCatch.displayName)
                .replace("%weight%", String.valueOf(fishCatch.weight))
                .replace("%price%", economy.format(fishCatch.price));
        Bukkit.broadcastMessage(Texts.color(message));
    }

    // ────────────────────────── 卖出 ──────────────────────────

    /** 卖出结果。 */
    public static final class SellResult {
        public final int count;
        public final double amount;
        public final String error;

        private SellResult(int count, double amount, String error) {
            this.count = count;
            this.amount = amount;
            this.error = error;
        }

        public static SellResult ok(int count, double amount) { return new SellResult(count, amount, null); }
        public static SellResult error(String message) { return new SellResult(0, 0.0, message); }
        public static SellResult empty() { return new SellResult(0, 0.0, null); }

        public boolean isEmpty() { return count == 0 && error == null; }
    }

    /**
     * 卖出玩家所有鱼获。
     *
     * <p>虚拟模式卖鱼箱；物品模式扫背包里的签名物品。
     *
     * <p>⚠️ 顺序很重要：**先给钱成功，再清数据/移除物品**。
     * 反过来的话，钱给失败（比如 Vault 出问题）玩家就白丢鱼了。
     */
    public SellResult sellAll(Player player) {
        if (!config.isItemDelivery()) {
            return sellVirtualBox(player);
        }
        return sellItemsInInventory(player);
    }

    /**
     * 虚拟鱼箱里某一鱼种的价值。
     *
     * <p>这里利用了一个代数恒等式，所以**不需要存每条鱼的克重**：
     * <pre>Σ(base + perGram × w_i) = count × base + perGram × Σw_i</pre>
     * 只需要条数 count 和累计克重 total 就能精确算出总价。
     *
     * <p>public static 是为了单测（纯函数，没有副作用）。
     */
    public static double boxValue(int count, long totalWeight, double basePrice, double pricePerGram) {
        return count * basePrice + totalWeight * pricePerGram;
    }

    /** 虚拟鱼箱卖出。 */
    private SellResult sellVirtualBox(Player player) {
        Map<String, PlayerDataStore.BoxEntry> box = dataStore.getFishBox(player.getUniqueId());
        if (box.isEmpty()) {
            return SellResult.empty();
        }
        double totalMoney = 0.0;
        int totalCount = 0;
        List<String> unknown = new ArrayList<String>();
        for (Map.Entry<String, PlayerDataStore.BoxEntry> entry : box.entrySet()) {
            FishDefinition def = config.fishById(entry.getKey());
            if (def == null) {
                // 服主把这种鱼从 fish.yml 删了：跳过并保留数据，不能按 0 价清掉玩家的鱼
                unknown.add(entry.getKey());
                continue;
            }
            PlayerDataStore.BoxEntry value = entry.getValue();
            totalMoney += boxValue(value.count, value.total, def.getBasePrice(), def.getPricePerGram());
            totalCount += value.count;
        }
        if (totalCount == 0) {
            return SellResult.error(unknown.isEmpty()
                    ? "鱼箱是空的"
                    : "鱼箱里的鱼种已从 fish.yml 移除，无法结算: " + unknown);
        }
        double rounded = round(totalMoney);
        if (!economy.deposit(player.getUniqueId(), rounded)) {
            return SellResult.error("经济插件入账失败，请稍后再试（鱼没有扣除）");
        }
        for (Map.Entry<String, PlayerDataStore.BoxEntry> entry : box.entrySet()) {
            if (config.fishById(entry.getKey()) != null) {
                dataStore.clearFishBox(player.getUniqueId(), entry.getKey());
            }
        }
        return SellResult.ok(totalCount, rounded);
    }

    /** 物品模式：扫背包里的签名鱼获。 */
    private SellResult sellItemsInInventory(Player player) {
        ItemStack[] contents = player.getInventory().getStorageContents();
        double totalMoney = 0.0;
        int totalCount = 0;
        List<Integer> slots = new ArrayList<Integer>();
        for (int i = 0; i < contents.length; i++) {
            FishCatch fishCatch = readCatch(contents[i]);
            if (fishCatch == null) {
                continue;
            }
            totalMoney += fishCatch.price * contents[i].getAmount();
            totalCount += contents[i].getAmount();
            slots.add(i);
        }
        if (totalCount == 0) {
            return SellResult.empty();
        }
        double rounded = round(totalMoney);
        if (!economy.deposit(player.getUniqueId(), rounded)) {
            return SellResult.error("经济插件入账失败，请稍后再试（鱼没有扣除）");
        }
        for (int slot : slots) {
            player.getInventory().setItem(slot, null);
        }
        return SellResult.ok(totalCount, rounded);
    }

    // ────────────────────────── 读取物品 ──────────────────────────

    /**
     * 从物品读回鱼获。签名不合法、鱼种已不存在都返回 null。
     */
    public FishCatch readCatch(ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR || !stack.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null || !meta.hasLore()) {
            return null;
        }
        for (String line : meta.getLore()) {
            FishCodec.Decoded decoded = config.getCodec().decode(Texts.stripColor(line));
            if (decoded == null) {
                continue;
            }
            FishDefinition def = config.fishById(decoded.fishId);
            if (def == null) {
                return null;
            }
            String name = meta.hasDisplayName() ? meta.getDisplayName() : Texts.color(def.getDisplayName());
            return new FishCatch(def, decoded.weight, decoded.price, name);
        }
        return null;
    }

    /** 这个物品是不是有效的鱼获（给 GUI / 指令判断用）。 */
    public boolean isFishItem(ItemStack stack) {
        return readCatch(stack) != null;
    }

    private double round(double value) {
        double factor = Math.pow(10, config.getMoneyScale());
        return Math.round(value * factor) / factor;
    }

    public ConfigManager config() {
        return config;
    }

    public PlayerDataStore dataStore() {
        return dataStore;
    }

    public EconomyProvider economy() {
        return economy;
    }

    public ItemDelivery itemDelivery() {
        return itemDelivery;
    }

    /** 供内部把颜色码去掉用（GUI 里显示原始名）。 */
    public static String plain(String colored) {
        return ChatColor.stripColor(colored);
    }
}
