package com.tinyaii.tinyfish.listener;

import com.tinyaii.tinyfish.service.AutoFishingService;
import com.tinyaii.tinyfish.service.CollectionService;
import com.tinyaii.tinyfish.service.UpdateChecker;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

/**
 * 玩家上下线时同步属性、清理自动钓鱼状态、提示新版本。
 *
 * <p>上线延迟 20 tick 再应用属性：AttributePlus 之类的属性插件通常也在 join 时初始化，
 * 立刻应用有可能被它们覆盖掉。
 */
public final class PlayerStateListener implements Listener {

    /** 上线多久后检查「要不要提示新版本」（tick）。要等异步的版本检查跑完。 */
    private static final long UPDATE_NOTIFY_DELAY_TICKS = 200L;

    private final Plugin plugin;
    private final CollectionService collection;
    private final AutoFishingService autoFishing;
    private final UpdateChecker updateChecker;

    public PlayerStateListener(Plugin plugin, CollectionService collection,
                               AutoFishingService autoFishing, UpdateChecker updateChecker) {
        this.plugin = plugin;
        this.collection = collection;
        this.autoFishing = autoFishing;
        this.updateChecker = updateChecker;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        final Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
            @Override
            public void run() {
                if (player.isOnline()) {
                    collection.refresh(player);
                }
            }
        }, 20L);

        // 延迟 10 秒再提示版本：版本检查是异步的，刚上线时结果可能还没回来
        Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
            @Override
            public void run() {
                if (player.isOnline() && updateChecker != null) {
                    updateChecker.notifyPlayer(player);
                }
            }
        }, UPDATE_NOTIFY_DELAY_TICKS);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        autoFishing.onQuit(event.getPlayer());
        collection.clear(event.getPlayer());
    }
}
