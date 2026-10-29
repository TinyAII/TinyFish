package com.tinyaii.tinyfish.fish;

import org.bukkit.entity.Player;

/**
 * 鱼获交付方式 —— 本插件"投递模式"的抽象。
 *
 * <p>两种实现的差别只有「鱼去哪」，抽取/克重/定价/属性全部共用。
 */
public interface CatchDelivery {

    /** 给日志看的名字。 */
    String name();

    void deliver(Player player, FishCatch fishCatch);
}
