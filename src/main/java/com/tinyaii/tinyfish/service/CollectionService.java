package com.tinyaii.tinyfish.service;

import com.tinyaii.tinyfish.attribute.AttributeSink;
import com.tinyaii.tinyfish.config.ConfigManager;
import com.tinyaii.tinyfish.economy.EconomyProvider;
import com.tinyaii.tinyfish.fish.AttributeMath;
import com.tinyaii.tinyfish.fish.FishDefinition;
import com.tinyaii.tinyfish.fish.FishService;
import com.tinyaii.tinyfish.util.Texts;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 鱼获收集箱：把鱼"投入"换成永久属性。
 *
 * <p>规则（虚拟鱼箱模式）：
 * <ul>
 *   <li>每种鱼只能占一个收集位，记录它的克重</li>
 *   <li>投入时取该鱼种**最大克重的一条**，其余同种鱼**自动按市价卖出**（钱进账，不白丢）</li>
 *   <li>如果收集箱里已有更大的，拒绝投入（否则等于白扔）</li>
 * </ul>
 */
public final class CollectionService {

    private final ConfigManager config;
    private final PlayerDataStore dataStore;
    private final EconomyProvider economy;
    private final AttributeSink sink;

    public CollectionService(ConfigManager config, PlayerDataStore dataStore,
                             EconomyProvider economy, AttributeSink sink) {
        this.config = config;
        this.dataStore = dataStore;
        this.economy = economy;
        this.sink = sink;
    }

    // ────────────────────────── 属性计算 ──────────────────────────

    /**
     * 按收集箱算玩家应有的全部属性（已分档、已封顶）。
     */
    public Map<String, Double> computeAttributes(UUID id) {
        Map<String, Integer> collection = dataStore.getCollection(id);
        if (collection.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Double> total = new LinkedHashMap<String, Double>();
        for (Map.Entry<String, Integer> entry : collection.entrySet()) {
            FishDefinition def = config.fishById(entry.getKey());
            if (def == null) {
                // 鱼种从配置删了：它带来的属性自然消失，这是预期行为
                continue;
            }
            Map<String, Double> single = AttributeMath.forCatch(
                    def.getAttributesPerGram(), entry.getValue(), config.getTiers());
            AttributeMath.accumulate(total, single);
        }
        return AttributeMath.clampTotal(total, config.getAttributeMaxTotal());
    }

    /** 重算并应用到玩家身上。 */
    public void refresh(Player player) {
        sink.apply(player, computeAttributes(player.getUniqueId()));
    }

    public void clear(Player player) {
        sink.clear(player);
    }

    // ────────────────────────── 投入 / 取出 ──────────────────────────

    /** 投入结果。 */
    public static final class DepositResult {
        public final boolean ok;
        public final String message;

        private DepositResult(boolean ok, String message) {
            this.ok = ok;
            this.message = message;
        }

        public static DepositResult ok(String message) { return new DepositResult(true, message); }
        public static DepositResult fail(String message) { return new DepositResult(false, message); }
    }

    /**
     * 把某种鱼里最好的一条投入收集箱，其余自动卖出。
     */
    public DepositResult deposit(Player player, String fishId) {
        FishDefinition def = config.fishById(fishId);
        if (def == null) {
            return DepositResult.fail("找不到鱼种: " + fishId);
        }
        if (config.isItemDelivery()) {
            return DepositResult.fail("物品模式下请先卖出物品，或联系服主切换到虚拟鱼箱模式");
        }
        PlayerDataStore.BoxEntry box = dataStore.getFishBoxEntry(player.getUniqueId(), def.getId());
        if (box == null || box.count <= 0) {
            return DepositResult.fail("你的鱼箱里没有 " + def.getDisplayName());
        }
        int existing = dataStore.getCollectionWeight(player.getUniqueId(), def.getId());
        if (existing >= box.best) {
            return DepositResult.fail("收集箱里已有更好的 " + def.getDisplayName()
                    + "（" + existing + "g），这条 " + box.best + "g 投入没有意义");
        }

        // 其余的鱼自动卖钱，避免玩家为了"只留最好的"而白白损失
        int restCount = box.count - 1;
        long restWeight = box.total - box.best;
        double restMoney = restCount > 0
                ? FishService.boxValue(restCount, restWeight, def.getBasePrice(), def.getPricePerGram())
                : 0.0;
        restMoney = round(restMoney);

        // 先给钱（失败就不动数据），再改收集箱
        if (restMoney > 0 && !economy.deposit(player.getUniqueId(), restMoney)) {
            return DepositResult.fail("经济插件入账失败，本次投入已取消");
        }

        dataStore.setCollectionWeight(player.getUniqueId(), def.getId(), box.best);
        dataStore.clearFishBox(player.getUniqueId(), def.getId());
        refresh(player);

        StringBuilder msg = new StringBuilder();
        msg.append("已投入 ").append(def.getDisplayName()).append(" ").append(box.best).append("g");
        if (restCount > 0) {
            msg.append("，其余 ").append(restCount).append(" 条自动卖出，获得 ")
                    .append(economy.format(restMoney));
        }
        return DepositResult.ok(msg.toString());
    }

    /**
     * 把收集箱里的鱼取回来（属性随之减少）。
     */
    public DepositResult withdraw(Player player, String fishId) {
        FishDefinition def = config.fishById(fishId);
        if (def == null) {
            return DepositResult.fail("找不到鱼种: " + fishId);
        }
        if (config.isItemDelivery()) {
            return DepositResult.fail("物品模式下不支持从收集箱取回");
        }
        int weight = dataStore.getCollectionWeight(player.getUniqueId(), def.getId());
        if (weight <= 0) {
            return DepositResult.fail("收集箱里没有 " + def.getDisplayName());
        }
        dataStore.setCollectionWeight(player.getUniqueId(), def.getId(), 0);
        dataStore.addFishBox(player.getUniqueId(), def.getId(), weight);
        refresh(player);
        return DepositResult.ok("已取回 " + def.getDisplayName() + " " + weight + "g（属性已相应减少）");
    }

    // ────────────────────────── 展示 ──────────────────────────

    /** 把属性表转成可读的多行文本。 */
    public String describe(Map<String, Double> attributes) {
        if (attributes == null || attributes.isEmpty()) {
            return Texts.color("&7（暂无属性加成）");
        }
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, Double> entry : attributes.entrySet()) {
            if (!first) {
                sb.append("&7, ");
            }
            sb.append("&a").append(AttributeMath.formatLine(entry.getKey(), entry.getValue()));
            first = false;
        }
        return Texts.color(sb.toString());
    }

    public AttributeSink sink() {
        return sink;
    }

    private double round(double value) {
        double factor = Math.pow(10, config.getMoneyScale());
        return Math.round(value * factor) / factor;
    }
}
