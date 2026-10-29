package com.tinyaii.tinyfish.gui;

import com.tinyaii.tinyfish.config.ConfigManager;
import com.tinyaii.tinyfish.economy.EconomyProvider;
import com.tinyaii.tinyfish.fish.FishService;
import com.tinyaii.tinyfish.service.CollectionService;
import com.tinyaii.tinyfish.service.PlayerDataStore;
import com.tinyaii.tinyfish.util.Texts;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * GUI 事件处理。
 *
 * <p>安全原则：**所有点击一律取消**。界面是只读的，
 * 玩家不能把东西放进我们的格子，也不能用 shift 把自己的物品推进来。
 * 判断"是不是我们的界面"只看 {@link GuiHolder}，不看标题。
 */
public final class GuiListener implements Listener {

    private final GuiRender render;
    private final ConfigManager config;
    private final PlayerDataStore dataStore;
    private final FishService fishService;
    private final CollectionService collection;
    private final EconomyProvider economy;

    public GuiListener(GuiRender render, ConfigManager config, PlayerDataStore dataStore,
                       FishService fishService, CollectionService collection, EconomyProvider economy) {
        this.render = render;
        this.config = config;
        this.dataStore = dataStore;
        this.fishService = fishService;
        this.collection = collection;
        this.economy = economy;
    }

    // ────────────────────────── 打开页面 ──────────────────────────

    public void openMain(Player player) {
        player.openInventory(render.main(player));
    }

    public void openBox(Player player, int page) {
        player.openInventory(render.box(player, page));
    }

    public void openCollection(Player player) {
        player.openInventory(render.collection(player));
    }

    public void openTop(Player player, int page) {
        player.openInventory(render.top(page));
    }

    // ────────────────────────── 事件 ──────────────────────────

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        InventoryHolder holder = top.getHolder();
        if (!(holder instanceof GuiHolder)) {
            return;
        }
        // 先取消，再做任何判断 —— 避免任何"漏网"的操作改到界面内容
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getWhoClicked();
        int raw = event.getRawSlot();
        // 点到玩家自己的背包（raw >= 界面大小）：已取消，什么都不做
        if (raw < 0 || raw >= top.getSize()) {
            return;
        }
        GuiHolder guiHolder = (GuiHolder) holder;
        switch (guiHolder.getType()) {
            case MAIN:
                handleMain(player, raw);
                break;
            case BOX:
                handleBox(player, guiHolder, raw);
                break;
            case COLLECTION:
                handleCollection(player, guiHolder, raw, event.getClick());
                break;
            case TOP:
                handleTop(player, guiHolder, raw);
                break;
            default:
                break;
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof GuiHolder) {
            event.setCancelled(true);
        }
    }

    // ────────────────────────── 各页面逻辑 ──────────────────────────

    private void handleMain(Player player, int slot) {
        if (slot == 11) {
            openBox(player, 1);
        } else if (slot == 13) {
            openCollection(player);
        } else if (slot == 15) {
            openTop(player, 1);
        } else if (slot == 22) {
            player.closeInventory();
        }
    }

    private void handleBox(Player player, GuiHolder holder, int slot) {
        if (slot < GuiRender.LIST_SLOTS) {
            String fishId = holder.fishIdAt(slot);
            if (fishId == null) {
                return;
            }
            CollectionService.DepositResult result = collection.deposit(player, fishId);
            player.sendMessage(Texts.color((result.ok ? "&a✔ " : "&c✘ ") + result.message));
            if (result.ok) {
                openBox(player, holder.getPage());
            }
            return;
        }
        if (slot == 45) {
            FishService.SellResult result = fishService.sellAll(player);
            if (result.error != null) {
                player.sendMessage(ChatColor.RED + result.error);
            } else if (result.isEmpty()) {
                player.sendMessage(ChatColor.GRAY + "没有可卖出的鱼获。");
            } else {
                player.sendMessage(Texts.color("&a✔ 卖出了 &f" + result.count + " &a条，获得 &e"
                        + economy.format(result.amount)));
                openBox(player, 1);
            }
            return;
        }
        if (slot == 49) {
            openMain(player);
            return;
        }
        if (slot == 53) {
            player.closeInventory();
        }
    }

    private void handleCollection(Player player, GuiHolder holder, int slot, ClickType click) {
        if (slot < GuiRender.LIST_SLOTS) {
            String fishId = holder.fishIdAt(slot);
            if (fishId == null) {
                return;
            }
            if (click != ClickType.RIGHT && click != ClickType.SHIFT_RIGHT) {
                player.sendMessage(Texts.color("&7右键点击可取回该鱼获（属性会相应减少）"));
                return;
            }
            CollectionService.DepositResult result = collection.withdraw(player, fishId);
            player.sendMessage(Texts.color((result.ok ? "&a✔ " : "&c✘ ") + result.message));
            if (result.ok) {
                openCollection(player);
            }
            return;
        }
        if (slot == 49) {
            openMain(player);
            return;
        }
        if (slot == 53) {
            player.closeInventory();
        }
    }

    private void handleTop(Player player, GuiHolder holder, int slot) {
        if (slot == 45) {
            openTop(player, holder.getPage() - 1);
            return;
        }
        if (slot == 49) {
            openMain(player);
            return;
        }
        if (slot == 53) {
            openTop(player, holder.getPage() + 1);
        }
    }
}
