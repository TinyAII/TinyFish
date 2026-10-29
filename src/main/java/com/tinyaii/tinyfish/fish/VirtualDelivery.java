package com.tinyaii.tinyfish.fish;

import com.tinyaii.tinyfish.service.PlayerDataStore;
import org.bukkit.entity.Player;

/**
 * 虚拟鱼箱交付（默认模式）：鱼获只写服务端数据，不生成物品。
 *
 * <p>因为物品根本不存在，所以**没有任何伪造可能**，也不占背包。
 * 代价是玩家之间不能交易鱼 —— 想要交易就把 {@code fishing.delivery} 改成 {@code item}。
 */
public final class VirtualDelivery implements CatchDelivery {

    private final PlayerDataStore dataStore;

    public VirtualDelivery(PlayerDataStore dataStore) {
        this.dataStore = dataStore;
    }

    @Override
    public String name() {
        return "virtual";
    }

    @Override
    public void deliver(Player player, FishCatch fishCatch) {
        dataStore.addFishBox(player.getUniqueId(), fishCatch.fishId(), fishCatch.weight);
        dataStore.updateBestWeight(player.getUniqueId(), fishCatch.fishId(), fishCatch.weight);
    }
}
