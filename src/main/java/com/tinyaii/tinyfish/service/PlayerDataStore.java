package com.tinyaii.tinyfish.service;

import com.tinyaii.tinyfish.TinyFishPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 玩家数据（data.yml）。
 *
 * <p>两条关键改动（相对老的 TinyFish）：
 * <ol>
 *   <li><b>收集箱按 fishId 存，不再按格位索引</b> —— 老代码用
 *       {@code definitions.get(i)} 对位，往 fish.yml 中间插一种鱼，所有人的收集箱立刻错位。</li>
 *   <li><b>脏标记 + 定时批量落盘</b> —— 老代码每次加钱都 {@code save()} 整个文件。</li>
 * </ol>
 */
public final class PlayerDataStore {

    private final TinyFishPlugin plugin;
    private final File file;
    private FileConfiguration config;
    /** 有未落盘的改动。 */
    private volatile boolean dirty;
    private int saveTaskId = -1;
    /** 批量落盘间隔（秒）。 */
    private final int saveIntervalSeconds;

    public PlayerDataStore(TinyFishPlugin plugin, int saveIntervalSeconds) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data.yml");
        this.saveIntervalSeconds = Math.max(10, saveIntervalSeconds);
    }

    // ────────────────────────── 生命周期 ──────────────────────────

    public void load() {
        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            plugin.getLogger().warning("无法创建插件数据目录: " + plugin.getDataFolder());
        }
        if (!file.isFile()) {
            try {
                if (!file.createNewFile()) {
                    plugin.getLogger().warning("无法创建 data.yml");
                }
            } catch (IOException e) {
                plugin.getLogger().warning("创建 data.yml 失败: " + e.getMessage());
            }
        }
        config = YamlConfiguration.loadConfiguration(file);
        dirty = false;
        startAutoSave();
    }

    private void startAutoSave() {
        stopAutoSave();
        // 用同步任务：YamlConfiguration 不是线程安全的，异步 save 会和主线程读写打架。
        // data.yml 通常只有几十 KB，60 秒一次的开销可忽略。
        saveTaskId = plugin.getServer().getScheduler()
                .runTaskTimer(plugin, new Runnable() {
                    @Override
                    public void run() {
                        if (dirty) {
                            save();
                        }
                    }
                }, saveIntervalSeconds * 20L, saveIntervalSeconds * 20L).getTaskId();
    }

    private void stopAutoSave() {
        if (saveTaskId != -1) {
            plugin.getServer().getScheduler().cancelTask(saveTaskId);
            saveTaskId = -1;
        }
    }

    /** 立即落盘。关服 / 重载时调用。 */
    public synchronized void save() {
        if (config == null || file == null) {
            return;
        }
        try {
            config.save(file);
            dirty = false;
        } catch (IOException e) {
            plugin.getLogger().warning("保存 data.yml 失败: " + e.getMessage());
        }
    }

    public void shutdown() {
        stopAutoSave();
        save();
    }

    private void markDirty() {
        dirty = true;
    }

    // ────────────────────────── 余额 ──────────────────────────

    public synchronized double getBalance(UUID id) {
        return config.getDouble(path(id) + ".balance", plugin.getConfigManager().getStartingBalance());
    }

    public synchronized void setBalance(UUID id, double value) {
        config.set(path(id) + ".balance", value);
        markDirty();
    }

    public synchronized void addBalance(UUID id, double delta) {
        if (delta == 0.0) {
            return;
        }
        setBalance(id, getBalance(id) + delta);
    }

    public synchronized void rememberName(UUID id, String name) {
        if (name == null || name.isEmpty()) {
            return;
        }
        String p = path(id) + ".name";
        if (!name.equals(config.getString(p))) {
            config.set(p, name);
            markDirty();
        }
    }

    // ────────────────────────── 虚拟鱼箱 ──────────────────────────

    /**
     * 记一条鱼进虚拟鱼箱：更新最大克重、累计条数、累计克重。
     */
    public synchronized void addFishBox(UUID id, String fishId, int weight) {
        String base = path(id) + ".box." + fishId;
        int best = config.getInt(base + ".best", 0);
        if (weight > best) {
            config.set(base + ".best", weight);
        }
        config.set(base + ".count", config.getInt(base + ".count", 0) + 1);
        config.set(base + ".total", config.getLong(base + ".total", 0L) + weight);
        markDirty();
    }

    /** 取整个鱼箱的快照（fishId -> 条目）。 */
    public synchronized Map<String, BoxEntry> getFishBox(UUID id) {
        ConfigurationSection section = config.getConfigurationSection(path(id) + ".box");
        if (section == null) {
            return Collections.emptyMap();
        }
        Map<String, BoxEntry> out = new LinkedHashMap<String, BoxEntry>();
        for (String fishId : section.getKeys(false)) {
            BoxEntry entry = new BoxEntry();
            entry.best = section.getInt(fishId + ".best", 0);
            entry.count = section.getInt(fishId + ".count", 0);
            entry.total = section.getLong(fishId + ".total", 0L);
            if (entry.count > 0 || entry.best > 0) {
                out.put(fishId, entry);
            }
        }
        return out;
    }

    public synchronized BoxEntry getFishBoxEntry(UUID id, String fishId) {
        String base = path(id) + ".box." + fishId;
        if (!config.isConfigurationSection(base)) {
            return null;
        }
        BoxEntry entry = new BoxEntry();
        entry.best = config.getInt(base + ".best", 0);
        entry.count = config.getInt(base + ".count", 0);
        entry.total = config.getLong(base + ".total", 0L);
        return entry;
    }

    /** 清空某个鱼种在虚拟鱼箱里的记录（取出 / 卖出后调用）。 */
    public synchronized void clearFishBox(UUID id, String fishId) {
        config.set(path(id) + ".box." + fishId, null);
        markDirty();
    }

    public synchronized void clearAllFishBox(UUID id) {
        config.set(path(id) + ".box", null);
        markDirty();
    }

    // ────────────────────────── 收集箱 ──────────────────────────

    /** 收集箱：每个鱼种只保留一条，记录它提供的克重。 */
    public synchronized Map<String, Integer> getCollection(UUID id) {
        ConfigurationSection section = config.getConfigurationSection(path(id) + ".collection");
        if (section == null) {
            return Collections.emptyMap();
        }
        Map<String, Integer> out = new LinkedHashMap<String, Integer>();
        for (String fishId : section.getKeys(false)) {
            int weight = section.getInt(fishId, 0);
            if (weight > 0) {
                out.put(fishId, weight);
            }
        }
        return out;
    }

    public synchronized int getCollectionWeight(UUID id, String fishId) {
        return config.getInt(path(id) + ".collection." + fishId, 0);
    }

    public synchronized void setCollectionWeight(UUID id, String fishId, int weight) {
        if (weight <= 0) {
            config.set(path(id) + ".collection." + fishId, null);
        } else {
            config.set(path(id) + ".collection." + fishId, weight);
        }
        markDirty();
    }

    // ────────────────────────── 最佳记录 ──────────────────────────

    public synchronized int getBestWeight(UUID id, String fishId) {
        return config.getInt(path(id) + ".best." + fishId, 0);
    }

    public synchronized void updateBestWeight(UUID id, String fishId, int weight) {
        if (weight <= getBestWeight(id, fishId)) {
            return;
        }
        config.set(path(id) + ".best." + fishId, weight);
        markDirty();
    }

    // ────────────────────────── 排行榜数据 ──────────────────────────

    /**
     * data.yml 里所有有记录的玩家 UUID。
     *
     * <p>排行榜需要遍历全部玩家，所以必须有这个入口。
     * 数量级是「几十到几百」，遍历开销可忽略；真有几千人再改成缓存。
     */
    public synchronized java.util.List<UUID> getKnownPlayers() {
        ConfigurationSection players = config.getConfigurationSection("players");
        if (players == null) {
            return Collections.emptyList();
        }
        java.util.List<UUID> out = new java.util.ArrayList<UUID>();
        for (String key : players.getKeys(false)) {
            try {
                out.add(UUID.fromString(key));
            } catch (IllegalArgumentException ignored) {
                // 手工改坏了 data.yml 里的键，跳过
            }
        }
        return out;
    }

    /** 记录下来的玩家名（玩家离线时也能显示）。 */
    public synchronized String getStoredName(UUID id) {
        return config.getString(path(id) + ".name", "");
    }

    // ────────────────────────── 工具 ──────────────────────────

    private static String path(UUID id) {
        return "players." + id.toString();
    }

    /** 虚拟鱼箱里某个鱼种的统计。 */
    public static final class BoxEntry {
        /** 单条最大克重。 */
        public int best;
        /** 累计条数。 */
        public int count;
        /** 累计克重。 */
        public long total;
    }
}
