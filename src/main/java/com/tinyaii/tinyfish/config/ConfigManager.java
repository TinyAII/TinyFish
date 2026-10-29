package com.tinyaii.tinyfish.config;

import com.tinyaii.tinyfish.TinyFishPlugin;
import com.tinyaii.tinyfish.fish.AttributeMath;
import com.tinyaii.tinyfish.fish.FishCodec;
import com.tinyaii.tinyfish.fish.FishDefinition;
import com.tinyaii.tinyfish.util.ConfigWriter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 读 config.yml + fish.yml。所有字段都在这里缓存成基本类型，
 * 业务代码不直接读 FileConfiguration（避免每个 tick 都查配置）。
 */
public final class ConfigManager {

    /** 版本检查服务的默认地址与 app_key（公开标识，不是密钥）。 */
    private static final String DEFAULT_UPDATE_ENDPOINT = "https://bump.tinyaii.cn";
    private static final String DEFAULT_APP_KEY = "app_dcb01c804910adade6c5812d";

    private final TinyFishPlugin plugin;

    // 投递
    private String delivery = "virtual";
    // 公式
    private String speedFormula = "0";
    private String multiplierFormula = "0";
    private String speedLoreKey = "钓鱼速度";
    private String multiplierLoreKey = "钓鱼倍率";
    private double maxSpeedMultiplier = 3.0;
    private int maxExtraCatches = 5;
    // 属性
    private String attributeSink = "auto";
    private double attributeMaxTotal = 50.0;
    private List<AttributeMath.Tier> tiers = Collections.emptyList();
    // 物品
    private String fallbackMaterial = "COD";
    private boolean itemGlow = false;
    private String weightLoreFormat = "&7克重: &e%weight%g";
    private String priceLoreFormat = "&7售价: &6%price%";
    private String idLoreFormat = "&8种类: %fish_id%";
    // 经济
    private String economyProvider = "auto";
    private String currencyName = "金币";
    private int moneyScale = 2;
    private double startingBalance = 0.0;
    // 收集箱
    private int collectionPages = 1;
    // 排行榜
    private int leaderboardPageSize = 10;
    private String leaderboardMetric = "best";
    // 广播
    private boolean broadcastEnabled = true;
    private int broadcastMinWeight = 3000;
    private String broadcastFormat = "&6[大鱼]&f %player% 钓到了 %fish_name% &f(&e%weight%g&f)";
    // 自动钓鱼
    private boolean autoFishingEnabled = true;
    private boolean requireRod = true;
    private boolean requireWater = true;
    private int waterCheckRadius = 3;
    private int biteTimeoutSeconds = 45;
    private int reelDelayTicks = 12;
    private int recastDelayTicks = 16;
    private String failChanceFormula = "15";
    private String failMessage = "&c鱼脱钩了，这次什么也没钓到";
    private boolean damageRodPerCatch = true;
    private boolean stopOnQuit = true;
    // 在线更新检查
    private boolean updateCheckEnabled = true;
    private String updateEndpoint = "https://bump.tinyaii.cn";
    private String updateAppKey = "";
    private String updateChannel = "stable";
    private int updateTimeoutSeconds = 3;
    private int updateIntervalHours = 6;
    private String updateNotifyPermission = "tinyfish.admin";

    private FishCodec codec;
    private List<FishDefinition> fishDefinitions = Collections.emptyList();
    private Map<String, FishDefinition> fishById = Collections.emptyMap();
    private File fishFile;

    public ConfigManager(TinyFishPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        plugin.reloadConfig();
        applyBundledDefaults();
        FileConfiguration c = plugin.getConfig();

        delivery = lower(c.getString("fishing.delivery", "virtual"), "virtual");
        if (!"item".equals(delivery) && !"virtual".equals(delivery)) {
            plugin.getLogger().warning("fishing.delivery=\"" + delivery + "\" 无效，已按 virtual 处理（可选: virtual / item）");
            delivery = "virtual";
        }
        speedFormula = c.getString("fishing.speed-formula", "0");
        multiplierFormula = c.getString("fishing.multiplier-formula", "0");
        speedLoreKey = c.getString("fishing.speed-lore-key", "钓鱼速度");
        multiplierLoreKey = c.getString("fishing.multiplier-lore-key", "钓鱼倍率");
        maxSpeedMultiplier = Math.max(1.0, c.getDouble("fishing.max-speed-multiplier", 3.0));
        maxExtraCatches = Math.max(0, c.getInt("fishing.max-extra-catches", 5));

        attributeSink = lower(c.getString("attributes.sink", "auto"), "auto");
        attributeMaxTotal = c.getDouble("attributes.max-total", 50.0);
        tiers = readTiers(c);

        fallbackMaterial = c.getString("item.fallback-material", "COD");
        itemGlow = c.getBoolean("item.glow", false);
        weightLoreFormat = c.getString("item.weight-lore-format", "&7克重: &e%weight%g");
        priceLoreFormat = c.getString("item.price-lore-format", "&7售价: &6%price%");
        idLoreFormat = c.getString("item.id-lore-format", "&8种类: %fish_id%");

        economyProvider = lower(c.getString("economy.provider", "auto"), "auto");
        currencyName = c.getString("economy.currency-name", "金币");
        moneyScale = Math.max(0, Math.min(6, c.getInt("economy.money-scale", 2)));
        startingBalance = c.getDouble("economy.internal.starting-balance", 0.0);

        collectionPages = Math.max(1, c.getInt("collection.pages", 1));
        leaderboardPageSize = Math.max(1, Math.min(45, c.getInt("leaderboard.page-size", 10)));
        leaderboardMetric = lower(c.getString("leaderboard.metric", "best"), "best");

        broadcastEnabled = c.getBoolean("broadcast.enabled", true);
        broadcastMinWeight = Math.max(0, c.getInt("broadcast.minimum-weight", 3000));
        broadcastFormat = c.getString("broadcast.format", broadcastFormat);

        autoFishingEnabled = c.getBoolean("auto-fishing.enabled", true);
        requireRod = c.getBoolean("auto-fishing.require-fishing-rod", true);
        requireWater = c.getBoolean("auto-fishing.require-water-nearby", true);
        waterCheckRadius = Math.max(1, c.getInt("auto-fishing.water-check-radius", 3));
        biteTimeoutSeconds = Math.max(5, c.getInt("auto-fishing.bite-timeout-seconds", 45));
        reelDelayTicks = Math.max(1, c.getInt("auto-fishing.reel-delay-ticks", 12));
        recastDelayTicks = Math.max(1, c.getInt("auto-fishing.recast-delay-ticks", 16));
        failChanceFormula = c.getString("auto-fishing.fail-chance-formula", "15");
        failMessage = c.getString("auto-fishing.fail-message", failMessage);
        damageRodPerCatch = c.getBoolean("auto-fishing.damage-rod-per-catch", true);
        stopOnQuit = c.getBoolean("auto-fishing.stop-on-quit", true);

        updateCheckEnabled = c.getBoolean("update-check.enabled", true);
        updateEndpoint = c.getString("update-check.endpoint", DEFAULT_UPDATE_ENDPOINT);
        updateAppKey = c.getString("update-check.app-key", "");
        // 老版本升级上来的 config.yml 没有 update-check 段；
        // 实测 Paper 26.3 上 setDefaults 兜不住（reloadConfig 内部行为有差异），
        // 所以这里再做一层硬兜底 —— app_key 不是密钥，写死在代码里没有安全风险。
        if (updateAppKey == null || updateAppKey.trim().isEmpty()) {
            updateAppKey = DEFAULT_APP_KEY;
        }
        updateChannel = c.getString("update-check.channel", "stable");
        updateTimeoutSeconds = Math.max(1, c.getInt("update-check.timeout-seconds", 3));
        updateIntervalHours = Math.max(1, c.getInt("update-check.interval-hours", 6));
        updateNotifyPermission = c.getString("update-check.notify-permission", "tinyfish.admin");

        codec = new FishCodec(ensureSecret(c));

        reloadFish();
    }

    /**
     * 用 jar 内置的 config.yml 当默认值兜底。
     *
     * <p>为什么需要：老版本升级上来时，磁盘上的 config.yml 不会有新增的配置段
     * （Bukkit 的 saveDefaultConfig 只在文件不存在时才写）。
     * 没有这层兜底，新加的 update-check 段就会「读不到」。
     *
     * <p>用 {@code setDefaults} 而不是 {@code options().copyDefaults(true) + saveConfig()}：
     * 后者会把整份文件的注释全部吃掉，这份配置的注释是写给服主看的。
     */
    private void applyBundledDefaults() {
        InputStream resource = null;
        try {
            resource = plugin.getResource("config.yml");
            if (resource == null) {
                return;
            }
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(resource, StandardCharsets.UTF_8));
            plugin.getConfig().setDefaults(defaults);
        } catch (Throwable t) {
            plugin.getLogger().warning("读取内置默认配置失败: " + t.getMessage());
        } finally {
            if (resource != null) {
                try {
                    resource.close();
                } catch (Exception ignored) {
                    // 关闭失败无所谓
                }
            }
        }
    }

    /** 密钥为空时生成一个并写回 config.yml（只改那一行，保留注释）。 */
    private String ensureSecret(FileConfiguration c) {
        String secret = c.getString("security.secret", "");
        if (secret != null && !secret.trim().isEmpty()) {
            return secret.trim();
        }
        String generated = FishCodec.generateSecret();
        File configFile = new File(plugin.getDataFolder(), "config.yml");
        try {
            boolean ok = ConfigWriter.replaceLine(configFile, "secret:",
                    "  secret: \"" + generated + "\"");
            if (ok) {
                plugin.getLogger().info("已生成物品签名密钥并写入 config.yml 的 security.secret");
            } else {
                plugin.getLogger().warning("未能在 config.yml 中找到 security.secret，请手动填入: " + generated);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("写入签名密钥失败（" + e.getMessage() + "），请手动填入 security.secret: " + generated);
        }
        c.set("security.secret", generated);
        return generated;
    }

    private List<AttributeMath.Tier> readTiers(FileConfiguration c) {
        List<AttributeMath.Tier> out = new ArrayList<AttributeMath.Tier>();
        List<Map<?, ?>> raw = c.getMapList("attributes.tiers");
        if (raw != null) {
            for (Map<?, ?> entry : raw) {
                Object maxObj = entry.get("max-weight");
                Object factorObj = entry.get("factor");
                if (maxObj == null || factorObj == null) {
                    continue;
                }
                try {
                    int maxWeight = Integer.parseInt(String.valueOf(maxObj).trim());
                    double factor = Double.parseDouble(String.valueOf(factorObj).trim());
                    out.add(new AttributeMath.Tier(maxWeight, factor));
                } catch (NumberFormatException ignored) {
                    // 单行配错不打断加载
                }
            }
        }
        if (out.isEmpty()) {
            plugin.getLogger().warning("attributes.tiers 未配置或全部无效，属性系数将统一按 1.0 处理");
            out.add(new AttributeMath.Tier(Integer.MAX_VALUE, 1.0));
        } else {
            // 服主可能写乱序，按 max-weight 升序排好，否则 factorFor 会取错档
            Collections.sort(out, new Comparator<AttributeMath.Tier>() {
                @Override
                public int compare(AttributeMath.Tier a, AttributeMath.Tier b) {
                    return Integer.compare(a.maxWeight, b.maxWeight);
                }
            });
        }
        return out;
    }

    private void reloadFish() {
        if (fishFile == null) {
            fishFile = new File(plugin.getDataFolder(), "fish.yml");
        }
        if (!fishFile.isFile()) {
            try {
                plugin.saveResource("fish.yml", false);
            } catch (Exception e) {
                plugin.getLogger().warning("无法生成 fish.yml: " + e.getMessage());
            }
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(fishFile);
        ConfigurationSection root = yaml.getConfigurationSection("fish");
        List<FishDefinition> list = new ArrayList<FishDefinition>();
        Map<String, FishDefinition> map = new LinkedHashMap<String, FishDefinition>();
        if (root != null) {
            for (String id : root.getKeys(false)) {
                ConfigurationSection s = root.getConfigurationSection(id);
                if (s == null) {
                    continue;
                }
                FishDefinition def = new FishDefinition(
                        id,
                        s.getString("ni-item-id", ""),
                        s.getString("display-name", id),
                        s.getDouble("chance", 0.0),
                        s.getInt("min-weight", 1),
                        s.getInt("max-weight", 1),
                        s.getDouble("base-price", 0.0),
                        s.getDouble("price-per-gram", 0.0),
                        s.getString("fallback-material", fallbackMaterial),
                        s.getStringList("lore"),
                        s.getStringList("collect-attribute"),
                        s.getStringList("command")
                );
                list.add(def);
                map.put(id.toLowerCase(), def);
            }
        }
        if (list.isEmpty()) {
            plugin.getLogger().warning("fish.yml 里没有任何有效鱼种，插件将无法产出鱼获");
        }
        fishDefinitions = Collections.unmodifiableList(list);
        fishById = Collections.unmodifiableMap(map);
    }

    private static String lower(String value, String fallback) {
        return value == null ? fallback : value.trim().toLowerCase();
    }

    public FishDefinition fishById(String id) {
        return id == null ? null : fishById.get(id.toLowerCase());
    }

    // ── getters ──────────────────────────────────────────
    public String getDelivery() { return delivery; }
    public boolean isItemDelivery() { return "item".equals(delivery); }
    public String getSpeedFormula() { return speedFormula; }
    public String getMultiplierFormula() { return multiplierFormula; }
    public String getSpeedLoreKey() { return speedLoreKey; }
    public String getMultiplierLoreKey() { return multiplierLoreKey; }
    public double getMaxSpeedMultiplier() { return maxSpeedMultiplier; }
    public int getMaxExtraCatches() { return maxExtraCatches; }
    public String getAttributeSink() { return attributeSink; }
    public double getAttributeMaxTotal() { return attributeMaxTotal; }
    public List<AttributeMath.Tier> getTiers() { return tiers; }
    public String getFallbackMaterial() { return fallbackMaterial; }
    public boolean isItemGlow() { return itemGlow; }
    public String getWeightLoreFormat() { return weightLoreFormat; }
    public String getPriceLoreFormat() { return priceLoreFormat; }
    public String getIdLoreFormat() { return idLoreFormat; }
    public String getEconomyProvider() { return economyProvider; }
    public String getCurrencyName() { return currencyName; }
    public int getMoneyScale() { return moneyScale; }
    public double getStartingBalance() { return startingBalance; }
    public int getCollectionPages() { return collectionPages; }
    public int getLeaderboardPageSize() { return leaderboardPageSize; }
    public String getLeaderboardMetric() { return leaderboardMetric; }
    public boolean isBroadcastEnabled() { return broadcastEnabled; }
    public int getBroadcastMinWeight() { return broadcastMinWeight; }
    public String getBroadcastFormat() { return broadcastFormat; }
    public boolean isAutoFishingEnabled() { return autoFishingEnabled; }
    public boolean isRequireRod() { return requireRod; }
    public boolean isRequireWater() { return requireWater; }
    public int getWaterCheckRadius() { return waterCheckRadius; }
    public int getBiteTimeoutSeconds() { return biteTimeoutSeconds; }
    public int getReelDelayTicks() { return reelDelayTicks; }
    public int getRecastDelayTicks() { return recastDelayTicks; }
    public String getFailChanceFormula() { return failChanceFormula; }
    public String getFailMessage() { return failMessage; }
    public boolean isDamageRodPerCatch() { return damageRodPerCatch; }
    public boolean isStopOnQuitEnabled() { return stopOnQuit; }
    public boolean isUpdateCheckEnabled() { return updateCheckEnabled; }
    public String getUpdateEndpoint() { return updateEndpoint; }
    public String getUpdateAppKey() { return updateAppKey; }
    public String getUpdateChannel() { return updateChannel; }
    public int getUpdateTimeoutSeconds() { return updateTimeoutSeconds; }
    public int getUpdateIntervalHours() { return updateIntervalHours; }
    public String getUpdateNotifyPermission() { return updateNotifyPermission; }
    public FishCodec getCodec() { return codec; }
    public List<FishDefinition> getFishDefinitions() { return fishDefinitions; }
}
