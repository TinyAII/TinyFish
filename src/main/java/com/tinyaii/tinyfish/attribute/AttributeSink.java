package com.tinyaii.tinyfish.attribute;

import org.bukkit.entity.Player;

import java.util.Map;

/**
 * 属性输出端 —— 把算好的属性挂到玩家身上。
 *
 * <p>为什么要这一层：这是给第三方服主用的插件，**不能假设他装了 AttributePlus**。
 * 没装就只把属性显示在 GUI/lore 上，不影响实际数值，插件照常工作。
 */
public interface AttributeSink {

    String name();

    boolean available();

    /**
     * 用一组「完整属性」覆盖本插件之前给这个玩家的加成。
     *
     * @param attributes 属性名 -&gt; 数值（百分比属性名以 % 结尾）
     */
    void apply(Player player, Map<String, Double> attributes);

    /** 玩家退出/插件关闭时清理。 */
    void clear(Player player);
}
