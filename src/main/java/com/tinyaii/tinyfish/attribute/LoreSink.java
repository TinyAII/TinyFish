package com.tinyaii.tinyfish.attribute;

import org.bukkit.entity.Player;

import java.util.Map;

/**
 * 兜底输出端：不修改任何玩家数据，属性只做展示。
 *
 * <p>没装 AttributePlus 时使用。这样"收集箱 → 属性"这条玩法在零依赖环境下依然可见，
 * 只是不会真的加到战斗力里 —— 总比插件直接罢工好。
 */
public final class LoreSink implements AttributeSink {

    @Override
    public String name() {
        return "lore";
    }

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public void apply(Player player, Map<String, Double> attributes) {
        // 故意留空：lore 模式下属性只显示，不落到实体属性上
    }

    @Override
    public void clear(Player player) {
        // 无需清理
    }
}
