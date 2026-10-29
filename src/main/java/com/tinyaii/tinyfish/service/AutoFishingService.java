package com.tinyaii.tinyfish.service;

import com.tinyaii.tinyfish.config.ConfigManager;
import com.tinyaii.tinyfish.fish.FishService;
import com.tinyaii.tinyfish.util.Texts;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 自动钓鱼。
 *
 * <p>实现方式：**自己抛竿、自己收杆，不走原版钓鱼结算**。
 * 原版钓鱼的判定（CAUGHT_FISH）需要玩家右击，自动钓鱼没法触发，
 * 所以收杆时直接移除钩子、由 {@link FishService} 发鱼。
 *
 * <p>⚠️ 修正了老 nqFish 的一个错误：抛竿必须用 {@code launchProjectile(FishHook.class)}。
 * 老代码写的是 {@code Fish.class} —— 但 1.13 起 {@code Fish} 是「鱼」这个生物（WaterMob），
 * 不是鱼钩，那次调用必然抛异常，靠 catch 落到第二个分支才跑通。
 */
public final class AutoFishingService {

    /** 巡检间隔（tick）。20 tick = 1 秒。 */
    private static final int TICK_INTERVAL = 10;

    private final Plugin plugin;
    private final ConfigManager config;
    private final FishService fishService;

    private final Set<UUID> enabled = new HashSet<UUID>();
    private final Map<UUID, Session> sessions = new HashMap<UUID, Session>();
    private BukkitTask task;

    public AutoFishingService(Plugin plugin, ConfigManager config, FishService fishService) {
        this.plugin = plugin;
        this.config = config;
        this.fishService = fishService;
    }

    /** 单个玩家的自动钓鱼状态。 */
    private static final class Session {
        FishHook hook;
        long castAt;
        long biteAt;
        long nextActionAt;
    }

    // ────────────────────────── 生命周期 ──────────────────────────

    public void start() {
        stop();
        if (!config.isAutoFishingEnabled()) {
            plugin.getLogger().info("[自动钓鱼] 配置已关闭，不启动");
            return;
        }
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override
            public void run() {
                tickSessions();
            }
        }, TICK_INTERVAL, TICK_INTERVAL);
        plugin.getLogger().info("[自动钓鱼] 已启动（巡检间隔 " + TICK_INTERVAL + " tick）");
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (Session session : sessions.values()) {
            if (session.hook != null && session.hook.isValid()) {
                session.hook.remove();
            }
        }
        sessions.clear();
        enabled.clear();
    }

    // ────────────────────────── 开关 ──────────────────────────

    public boolean isEnabled(Player player) {
        return enabled.contains(player.getUniqueId());
    }

    /** 切换开关，返回切换后的状态。 */
    public boolean toggle(Player player) {
        UUID id = player.getUniqueId();
        if (enabled.remove(id)) {
            Session session = sessions.remove(id);
            if (session != null && session.hook != null && session.hook.isValid()) {
                session.hook.remove();
            }
            return false;
        }
        enabled.add(id);
        sessions.put(id, new Session());
        return true;
    }

    public void onQuit(Player player) {
        UUID id = player.getUniqueId();
        Session session = sessions.remove(id);
        if (session != null && session.hook != null && session.hook.isValid()) {
            session.hook.remove();
        }
        if (config.isStopOnQuitEnabled()) {
            enabled.remove(id);
        }
    }

    /**
     * 玩家钓到鱼咬钩了（由 FishingListener 通知）。
     * 只对开启了自动钓鱼的玩家生效。
     */
    public void onBite(Player player) {
        if (!isEnabled(player)) {
            return;
        }
        Session session = sessions.get(player.getUniqueId());
        if (session == null || session.hook == null || session.biteAt > 0) {
            return;
        }
        session.biteAt = System.currentTimeMillis();
    }

    // ────────────────────────── 巡检 ──────────────────────────

    private void tickSessions() {
        if (enabled.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, Session>> it = sessions.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Session> entry = it.next();
            Player player = plugin.getServer().getPlayer(entry.getKey());
            if (player == null || !player.isOnline()) {
                it.remove();
                enabled.remove(entry.getKey());
                continue;
            }
            if (!player.hasPermission("tinyfish.auto")) {
                it.remove();
                enabled.remove(entry.getKey());
                continue;
            }
            Session session = entry.getValue();

            // 钩子失效（掉水里没了 / 被清理）就当没有
            if (session.hook != null && !session.hook.isValid()) {
                session.hook = null;
                session.biteAt = 0;
            }

            if (session.hook == null) {
                if (now >= session.nextActionAt && !hasForeignHook(player)) {
                    cast(player, session, now);
                }
                continue;
            }

            long reelDelayMillis = config.getReelDelayTicks() * 50L;
            if (session.biteAt > 0 && now - session.biteAt >= reelDelayMillis) {
                reap(player, session, now);
                continue;
            }
            long timeoutMillis = config.getBiteTimeoutSeconds() * 1000L;
            if (now - session.castAt > timeoutMillis) {
                // 等太久没鱼上钩：收杆重抛
                session.hook.remove();
                session.hook = null;
                session.biteAt = 0;
                session.nextActionAt = now + config.getRecastDelayTicks() * 50L;
            }
        }
    }

    private void cast(Player player, Session session, long now) {
        if (config.isRequireRod() && !isHoldingRod(player)) {
            // 没拿鱼竿就静静等着，不刷屏提醒
            session.nextActionAt = now + 1000L;
            return;
        }
        if (config.isRequireWater() && !hasWaterNearby(player)) {
            session.nextActionAt = now + 1000L;
            return;
        }
        try {
            FishHook hook = player.launchProjectile(FishHook.class);
            if (hook == null) {
                session.nextActionAt = now + 1000L;
                return;
            }
            session.hook = hook;
            session.castAt = now;
            session.biteAt = 0;
            session.nextActionAt = 0;
        } catch (Throwable t) {
            // 极老/极新核心的抛竿 API 差异，吞掉但不刷屏
            session.nextActionAt = now + 2000L;
        }
    }

    private void reap(Player player, Session session, long now) {
        if (session.hook != null && session.hook.isValid()) {
            session.hook.remove();
        }
        session.hook = null;
        session.biteAt = 0;
        session.nextActionAt = now + config.getRecastDelayTicks() * 50L;

        if (rollFailure(player)) {
            player.sendMessage(Texts.color(config.getFailMessage()));
            return;
        }
        fishService.giveCatch(player, true);
    }

    // ────────────────────────── 判定 ──────────────────────────

    private boolean rollFailure(Player player) {
        double chance = fishService.evaluate(config.getFailChanceFormula(), player);
        if (chance <= 0) {
            return false;
        }
        if (chance >= 100) {
            return true;
        }
        return ThreadLocalRandom.current().nextDouble() * 100.0 < chance;
    }

    private boolean isHoldingRod(Player player) {
        Material type = player.getInventory().getItemInMainHand().getType();
        return type == Material.matchMaterial("FISHING_ROD");
    }

    /**
     * 玩家附近是不是已经有他自己的钩子。
     * 有就说明他手动抛了竿，我们不抢，等它结束。
     */
    private boolean hasForeignHook(Player player) {
        try {
            for (Entity entity : player.getNearbyEntities(64.0, 64.0, 64.0)) {
                if (entity instanceof FishHook) {
                    FishHook hook = (FishHook) entity;
                    if (player.equals(hook.getShooter())) {
                        return true;
                    }
                }
            }
        } catch (Throwable ignored) {
            // 某些实现可能不支持 getShooter，保守返回 false 让它自己抛
        }
        return false;
    }

    private boolean hasWaterNearby(Player player) {
        Location location = player.getLocation();
        World world = location.getWorld();
        if (world == null) {
            return false;
        }
        Material water = Material.matchMaterial("WATER");
        if (water == null) {
            return true;
        }
        int radius = config.getWaterCheckRadius();
        int baseX = location.getBlockX();
        int baseY = location.getBlockY();
        int baseZ = location.getBlockZ();
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    Block block = world.getBlockAt(baseX + x, baseY + y, baseZ + z);
                    if (block.getType() == water) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
