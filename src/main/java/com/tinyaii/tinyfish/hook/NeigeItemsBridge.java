package com.tinyaii.tinyfish.hook;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * NeigeItems 软依赖桥（全反射）。
 *
 * <p>作用：让服主能用 NeigeItems 的自定义物品作为鱼获外观，而不是只有原版鳕鱼。
 * 没装 NeigeItems 时 {@link #isAvailable()} 返回 false，调用方回落到原版材质。
 *
 * <p>调用依据来自老 nqFish 的反编译代码：
 * <pre>
 * pers.neige.neigeitems.manager.ItemManager.INSTANCE
 *   .hasItem(String)
 *   .getItemStack(String, OfflinePlayer)
 * </pre>
 *
 * <p><b>待确认</b>：NeigeItems 的包名/方法签名在不同版本可能变化。
 * 这里只做一次探测，失败就永久降级，不会每次调用都抛异常刷屏。
 */
public final class NeigeItemsBridge {

    private final Object itemManager;
    private final Method getItemStack;
    private final Method hasItem;

    private NeigeItemsBridge(Object itemManager, Method getItemStack, Method hasItem) {
        this.itemManager = itemManager;
        this.getItemStack = getItemStack;
        this.hasItem = hasItem;
    }

    /**
     * 探测 NeigeItems。
     *
     * @return 不可用时返回 null
     */
    public static NeigeItemsBridge detect(Plugin plugin) {
        Plugin neige = Bukkit.getPluginManager().getPlugin("NeigeItems");
        if (neige == null || !neige.isEnabled()) {
            return null;
        }
        try {
            Class<?> clazz = Class.forName("pers.neige.neigeitems.manager.ItemManager");
            Field instanceField = clazz.getField("INSTANCE");
            Object instance = instanceField.get(null);
            Method getItemStack = clazz.getMethod("getItemStack", String.class, OfflinePlayer.class);
            Method hasItem = clazz.getMethod("hasItem", String.class);
            plugin.getLogger().info("[物品] 已对接 NeigeItems");
            return new NeigeItemsBridge(instance, getItemStack, hasItem);
        } catch (Throwable t) {
            plugin.getLogger().warning("[物品] 找到 NeigeItems 但 API 不匹配（"
                    + t.getClass().getSimpleName() + "），鱼获将使用原版材质");
            return null;
        }
    }

    public boolean isAvailable() {
        return itemManager != null && getItemStack != null && hasItem != null;
    }

    /** 这个 ID 在 NeigeItems 里存在吗。 */
    public boolean hasItem(String itemId) {
        if (!isAvailable() || itemId == null || itemId.trim().isEmpty()) {
            return false;
        }
        try {
            Object result = hasItem.invoke(itemManager, itemId);
            return result instanceof Boolean && (Boolean) result;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * 生成 NeigeItems 物品。
     *
     * @return 失败或 ID 不存在时返回 null（调用方回落原版材质）
     */
    public ItemStack createItem(String itemId, Player player) {
        if (!isAvailable() || itemId == null || itemId.trim().isEmpty()) {
            return null;
        }
        try {
            if (!hasItem(itemId)) {
                return null;
            }
            Object result = getItemStack.invoke(itemManager, itemId, player);
            return result instanceof ItemStack ? (ItemStack) result : null;
        } catch (Throwable t) {
            return null;
        }
    }
}
