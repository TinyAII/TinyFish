package com.tinyaii.tinyfish.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 我自己的 GUI 标记。
 *
 * <p>靠它区分「这是插件的界面」还是「玩家自己的箱子」——
 * 判断依据必须用 holder，不能用标题字符串（标题能被玩家改）。
 *
 * <p>另外它记着「格子 → 鱼种 ID」的映射：1.13 没有 PersistentDataContainer，
 * 不能把 fishId 塞进物品里，所以点击时只能靠这张表反查。
 */
public final class GuiHolder implements InventoryHolder {

    private final GuiType type;
    private final int page;
    private final Map<Integer, String> slotRefs = new LinkedHashMap<Integer, String>();
    private Inventory inventory;

    public GuiHolder(GuiType type, int page) {
        this.type = type;
        this.page = Math.max(1, page);
    }

    public GuiType getType() {
        return type;
    }

    public int getPage() {
        return page;
    }

    /** 渲染时登记：这个格子代表哪个鱼种。 */
    public void bindSlot(int slot, String fishId) {
        slotRefs.put(slot, fishId);
    }

    /** 点击时反查。 */
    public String fishIdAt(int slot) {
        return slotRefs.get(slot);
    }

    public Map<Integer, String> slotRefs() {
        return Collections.unmodifiableMap(slotRefs);
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        // 必须是创建时给 Bukkit 的那个实例：部分服务端会用它做校验
        return inventory;
    }
}
