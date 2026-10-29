package com.tinyaii.tinyfish.fish;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 属性数值计算 —— 纯函数，不碰 Bukkit，方便单测。
 *
 * <p>为什么要分档：老版本是「单克属性 × 克重」的线性乘，
 * 一条 8000g 的金枪鱼（单克 +6）会给出 +48000 攻击力，直接把数值体系打穿。
 * 分档后同样的鱼是 +9。
 */
public final class AttributeMath {

    private AttributeMath() {
    }

    /** 一个克重档位。 */
    public static final class Tier {
        public final int maxWeight;
        public final double factor;

        public Tier(int maxWeight, double factor) {
            this.maxWeight = maxWeight;
            this.factor = Math.max(0.0, factor);
        }
    }

    /** 查克重对应的档位系数；找不到就取最后一档。 */
    public static double factorFor(int weight, List<Tier> tiers) {
        if (tiers == null || tiers.isEmpty()) {
            return 1.0;
        }
        for (Tier tier : tiers) {
            if (weight <= tier.maxWeight) {
                return tier.factor;
            }
        }
        return tiers.get(tiers.size() - 1).factor;
    }

    /**
     * 单条鱼提供的属性 = 单克属性 × 档位系数。
     * 注意：这里不封顶，封顶在 {@link #clampTotal} 里对「累加后的总值」做。
     */
    public static Map<String, Double> forCatch(Map<String, Double> perGram, int weight, List<Tier> tiers) {
        Map<String, Double> out = new LinkedHashMap<String, Double>();
        if (perGram == null || perGram.isEmpty()) {
            return out;
        }
        double factor = factorFor(weight, tiers);
        for (Map.Entry<String, Double> entry : perGram.entrySet()) {
            double value = entry.getValue() * factor;
            if (value != 0.0) {
                out.put(entry.getKey(), value);
            }
        }
        return out;
    }

    /** 把多条鱼的属性累加。 */
    public static void accumulate(Map<String, Double> target, Map<String, Double> addition) {
        if (target == null || addition == null) {
            return;
        }
        for (Map.Entry<String, Double> entry : addition.entrySet()) {
            Double current = target.get(entry.getKey());
            target.put(entry.getKey(), (current == null ? 0.0 : current) + entry.getValue());
        }
    }

    /**
     * 对累加后的总值做封顶。
     *
     * @param maxTotal 每属性上限，&lt;= 0 表示不限制
     */
    public static Map<String, Double> clampTotal(Map<String, Double> total, double maxTotal) {
        Map<String, Double> out = new LinkedHashMap<String, Double>();
        if (total == null) {
            return out;
        }
        boolean limited = maxTotal > 0;
        for (Map.Entry<String, Double> entry : total.entrySet()) {
            double value = entry.getValue();
            if (value == 0.0) {
                continue;
            }
            if (limited) {
                value = Math.min(value, maxTotal);
                value = Math.max(value, -maxTotal);
            }
            out.put(entry.getKey(), value);
        }
        return out;
    }

    /** 格式化成 AttributePlus 能吃的行，如 "攻击力+9" / "攻击加成+1.5%"（去掉多余小数）。 */
    public static String formatLine(String attributeName, double value) {
        boolean percent = attributeName != null && attributeName.endsWith("%");
        String name = percent ? attributeName.substring(0, attributeName.length() - 1) : attributeName;
        String number = trimNumber(value);
        String sign = value >= 0 ? "+" : "";
        return name + sign + number + (percent ? "%" : "");
    }

    /** 去掉浮点尾巴：9.0 -&gt; "9"，1.50 -&gt; "1.5"。 */
    static String trimNumber(double value) {
        if (value == Math.rint(value) && !Double.isInfinite(value)) {
            return String.valueOf((long) value);
        }
        String text = String.format(java.util.Locale.ROOT, "%.4f", value);
        while (text.endsWith("0")) {
            text = text.substring(0, text.length() - 1);
        }
        if (text.endsWith(".")) {
            text = text.substring(0, text.length() - 1);
        }
        return text;
    }
}
