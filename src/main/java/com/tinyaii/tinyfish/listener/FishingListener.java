package com.tinyaii.tinyfish.listener;

import com.tinyaii.tinyfish.fish.FishService;
import com.tinyaii.tinyfish.service.AutoFishingService;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;

/**
 * 钓鱼事件。
 *
 * <p>三个状态各自分工：
 * <ul>
 *   <li>{@code FISHING}     —— 钩子刚抛出，应用「钓鱼速度」加成（跨版本实现见 {@link com.tinyaii.tinyfish.fish.HookSpeed}）</li>
 *   <li>{@code BITE}        —— 鱼咬钩，通知自动钓鱼服务（它据此决定何时收杆）</li>
 *   <li>{@code CAUGHT_FISH} —— 玩家真的钓到了，删掉原版掉落换成我们的鱼获</li>
 * </ul>
 */
public final class FishingListener implements Listener {

    private final FishService fishService;
    private final AutoFishingService autoFishing;

    public FishingListener(FishService fishService, AutoFishingService autoFishing) {
        this.fishService = fishService;
        this.autoFishing = autoFishing;
    }

    /** 抛竿完成：给钩子加速度加成。用 MONITOR，不干扰其它插件。 */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCast(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.FISHING) {
            return;
        }
        Player player = event.getPlayer();
        if (!player.hasPermission("tinyfish.use")) {
            return;
        }
        Entity hook = event.getHook();
        if (hook instanceof FishHook) {
            fishService.applyHookSpeed(player, (FishHook) hook);
        }
    }

    /** 鱼咬钩：交给自动钓鱼服务计时。 */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBite(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.BITE) {
            return;
        }
        autoFishing.onBite(event.getPlayer());
    }

    /** 钓到鱼：删原版掉落 + 清经验 + 给自定义鱼获。 */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onCaught(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) {
            return;
        }
        Player player = event.getPlayer();
        if (!player.hasPermission("tinyfish.use")) {
            return;
        }
        // 鱼种没配置时就别动原版了，否则玩家什么都拿不到
        if (fishService.config().getFishDefinitions().isEmpty()) {
            return;
        }

        Entity caught = event.getCaught();
        if (caught != null) {
            caught.remove();
        }
        event.setExpToDrop(0);

        fishService.giveCatch(player, false);
    }
}
