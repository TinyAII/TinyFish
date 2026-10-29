package com.tinyaii.tinyfish.fish;

import com.tinyaii.tinyfish.config.ConfigManager;
import com.tinyaii.tinyfish.hook.NeigeItemsBridge;
import com.tinyaii.tinyfish.util.Texts;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 物品交付：生成带 HMAC 签名的真实鱼获物品，可交易、可上架商店。
 *
 * <p>外观来源：优先 NeigeItems 的自定义物品（如果服主配了 ni-item-id 且装了 NE），
 * 否则用原版材质。**无论哪种，签名行都会追加到 lore 最后一行**（深灰），
 * 玩家改任何字段都会验签失败，物品会被当成普通物品。
 */
public final class ItemDelivery implements CatchDelivery {

    private final ConfigManager config;
    private final NeigeItemsBridge neige;

    public ItemDelivery(ConfigManager config, NeigeItemsBridge neige) {
        this.config = config;
        this.neige = neige;
    }

    @Override
    public String name() {
        return "item";
    }

    @Override
    public void deliver(Player player, FishCatch fishCatch) {
        ItemStack item = build(fishCatch, player);
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);
        // 背包满就掉在脚下，不能静默吞掉
        for (ItemStack rest : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), rest);
        }
    }

    /** 按鱼定义生成物品。 */
    public ItemStack build(FishCatch fishCatch, Player player) {
        ItemStack stack = null;
        // ① 先试 NeigeItems 自定义外观
        if (neige != null && neige.isAvailable()) {
            stack = neige.createItem(fishCatch.definition.getNiItemId(), player);
        }
        // ② 回落到原版材质
        if (stack == null || stack.getType() == Material.AIR) {
            stack = new ItemStack(resolveMaterial(fishCatch.definition), 1);
        } else {
            stack = stack.clone();
            stack.setAmount(1);
        }

        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }

        String name = fishCatch.displayName == null || fishCatch.displayName.isEmpty()
                ? Texts.color(fishCatch.definition.getDisplayName())
                : fishCatch.displayName;
        if (!name.isEmpty()) {
            meta.setDisplayName(name);
        }

        // 保留物品原有的 lore（NeigeItems 物品可能自带说明），在后面追加我们的
        List<String> lore = meta.hasLore() && meta.getLore() != null
                ? new ArrayList<String>(meta.getLore())
                : new ArrayList<String>();
        for (String line : fishCatch.definition.getLore()) {
            lore.add(Texts.color(line));
        }
        lore.add(Texts.color(config.getWeightLoreFormat().replace("%weight%", String.valueOf(fishCatch.weight))));
        lore.add(Texts.color(config.getPriceLoreFormat().replace("%price%",
                Texts.formatNumber(fishCatch.price, config.getMoneyScale()) + " " + config.getCurrencyName())));
        lore.add(Texts.color(config.getIdLoreFormat().replace("%fish_id%", fishCatch.fishId())));

        // 签名行：价格用固定格式，保证同一价格只有一种文本写法，否则验签会误判
        String priceText = Texts.formatNumber(fishCatch.price, config.getMoneyScale());
        String signature = config.getCodec().encode(fishCatch.fishId(), fishCatch.weight, priceText);
        lore.add(ChatColor.DARK_GRAY + signature);

        meta.setLore(lore);

        if (config.isItemGlow()) {
            meta.addEnchant(Enchantment.LURE, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        stack.setItemMeta(meta);
        return stack;
    }

    /** 材质解析：优先鱼定义 → 配置默认 → COD 兜底。全程用字符串匹配，避免跨版本常量缺失。 */
    private Material resolveMaterial(FishDefinition definition) {
        Material material = Material.matchMaterial(definition.getFallbackMaterial());
        if (material == null || material == Material.AIR) {
            material = Material.matchMaterial(config.getFallbackMaterial());
        }
        if (material == null || material == Material.AIR) {
            material = Material.matchMaterial("COD");
        }
        return material == null ? Material.STONE : material;
    }
}
