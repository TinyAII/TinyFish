package com.tinyaii.tinyfish.fish;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 一种鱼的定义（对应 fish.yml 里一个节点）。
 *
 * <p>只存数据与纯计算，不碰 Bukkit —— 这样可以在没有服务端的环境里单测。
 */
public final class FishDefinition {

    private final String id;
    private final String niItemId;
    private final String displayName;
    private final double chance;
    private final int minWeight;
    private final int maxWeight;
    private final double basePrice;
    private final double pricePerGram;
    private final String fallbackMaterial;
    private final List<String> lore;
    /** 单克属性：属性名 -&gt; 每克数值。百分比属性名以 % 结尾。 */
    private final Map<String, Double> attributesPerGram;
    private final List<CommandRule> commands;

    public FishDefinition(String id, String niItemId, String displayName, double chance,
                          int minWeight, int maxWeight, double basePrice, double pricePerGram,
                          String fallbackMaterial, List<String> lore,
                          List<String> rawAttributes, List<String> rawCommands) {
        this.id = id;
        this.niItemId = niItemId == null ? "" : niItemId;
        this.displayName = displayName == null ? "" : displayName;
        this.chance = Math.max(0.0, chance);
        this.minWeight = Math.max(1, Math.min(minWeight, maxWeight));
        this.maxWeight = Math.max(this.minWeight, maxWeight);
        this.basePrice = Math.max(0.0, basePrice);
        this.pricePerGram = Math.max(0.0, pricePerGram);
        this.fallbackMaterial = fallbackMaterial == null || fallbackMaterial.trim().isEmpty()
                ? "COD" : fallbackMaterial.trim();
        this.lore = lore == null ? Collections.<String>emptyList() : new ArrayList<String>(lore);
        this.attributesPerGram = parseAttributes(rawAttributes);
        this.commands = CommandRule.parseAll(rawCommands);
    }

    /** 按 min~max 均匀随机一个克重（两端都含）。 */
    public int rollWeight() {
        if (maxWeight <= minWeight) {
            return minWeight;
        }
        return ThreadLocalRandom.current().nextInt(minWeight, maxWeight + 1);
    }

    /** 售价 = 基础价 + 每克价 × 克重。 */
    public double calculatePrice(int weight) {
        return basePrice + pricePerGram * (double) weight;
    }

    /**
     * 解析 "攻击力+3" / "攻击加成+1%" 这类配置行。
     * 从右往左找最后一个 + 或 -，左边是属性名，右边是数值。
     *
     * <p>public 是给测试用的（纯函数，没有副作用）。
     */
    public static Map<String, Double> parseAttributes(List<String> raw) {
        Map<String, Double> out = new LinkedHashMap<String, Double>();
        if (raw == null) {
            return out;
        }
        for (String line : raw) {
            if (line == null) {
                continue;
            }
            String s = line.trim();
            int idx = -1;
            for (int i = s.length() - 1; i > 0; i--) {
                char c = s.charAt(i);
                if (c == '+' || c == '-') {
                    idx = i;
                    break;
                }
            }
            if (idx <= 0) {
                continue;
            }
            String name = s.substring(0, idx).trim();
            String number = s.substring(idx);
            boolean percent = number.endsWith("%");
            if (percent) {
                number = number.substring(0, number.length() - 1);
            }
            if (name.isEmpty()) {
                continue;
            }
            try {
                double value = Double.parseDouble(number);
                if (value == 0.0) {
                    continue;
                }
                out.put(percent ? name + "%" : name, value);
            } catch (NumberFormatException ignored) {
                // 配置写错就当没写，不抛异常打断整个加载
            }
        }
        return out;
    }

    public String getId() { return id; }
    public String getNiItemId() { return niItemId; }
    public String getDisplayName() { return displayName; }
    public double getChance() { return chance; }
    public int getMinWeight() { return minWeight; }
    public int getMaxWeight() { return maxWeight; }
    public double getBasePrice() { return basePrice; }
    public double getPricePerGram() { return pricePerGram; }
    public String getFallbackMaterial() { return fallbackMaterial; }
    public List<String> getLore() { return Collections.unmodifiableList(lore); }
    public Map<String, Double> getAttributesPerGram() { return Collections.unmodifiableMap(attributesPerGram); }
    public List<CommandRule> getCommands() { return Collections.unmodifiableList(commands); }

    /**
     * 一条指令规则。两种用法共用同一条记录：
     * <ul>
     *   <li>command 列表：value = 概率 0~100，每条独立判定</li>
     *   <li>weight 列表：value = 权重，只抽中一条执行</li>
     * </ul>
     */
    public static final class CommandRule {
        public final String command;
        public final double value;

        public CommandRule(String command, double value) {
            this.command = command;
            this.value = value;
        }

        /** 解析 "'控制台指令' 数字" 形式的列表。 */
        static List<CommandRule> parseAll(List<String> raw) {
            List<CommandRule> out = new ArrayList<CommandRule>();
            if (raw == null) {
                return out;
            }
            for (String line : raw) {
                CommandRule rule = parseOne(line);
                if (rule != null) {
                    out.add(rule);
                }
            }
            return out;
        }

        static CommandRule parseOne(String line) {
            if (line == null) {
                return null;
            }
            String s = line.trim();
            if (s.isEmpty()) {
                return null;
            }
            String command;
            String number;
            if (s.startsWith("'") || s.startsWith("\"")) {
                char quote = s.charAt(0);
                int end = s.indexOf(quote, 1);
                if (end < 0) {
                    return null;
                }
                command = s.substring(1, end);
                number = s.substring(end + 1).trim();
            } else {
                int space = s.lastIndexOf(' ');
                if (space <= 0) {
                    return null;
                }
                command = s.substring(0, space).trim();
                number = s.substring(space + 1).trim();
            }
            try {
                double value = Double.parseDouble(number);
                return command.isEmpty() ? null : new CommandRule(command, value);
            } catch (NumberFormatException e) {
                return null;
            }
        }
    }
}
