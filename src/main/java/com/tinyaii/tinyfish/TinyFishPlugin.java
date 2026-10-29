package com.tinyaii.tinyfish;

import com.tinyaii.tinyfish.attribute.AttributePlusSink;
import com.tinyaii.tinyfish.attribute.AttributeSink;
import com.tinyaii.tinyfish.attribute.LoreSink;
import com.tinyaii.tinyfish.command.TinyFishCommand;
import com.tinyaii.tinyfish.config.ConfigManager;
import com.tinyaii.tinyfish.economy.EcoBridge;
import com.tinyaii.tinyfish.economy.EconomyProvider;
import com.tinyaii.tinyfish.economy.InternalEconomy;
import com.tinyaii.tinyfish.fish.FishCodec;
import com.tinyaii.tinyfish.fish.FishService;
import com.tinyaii.tinyfish.gui.GuiListener;
import com.tinyaii.tinyfish.gui.GuiRender;
import com.tinyaii.tinyfish.hook.NeigeItemsBridge;
import com.tinyaii.tinyfish.listener.FishingListener;
import com.tinyaii.tinyfish.listener.PlayerStateListener;
import com.tinyaii.tinyfish.service.AutoFishingService;
import com.tinyaii.tinyfish.service.CollectionService;
import com.tinyaii.tinyfish.service.LeaderboardService;
import com.tinyaii.tinyfish.service.PlayerDataStore;
import com.tinyaii.tinyfish.service.UpdateChecker;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * TinyFish 2.0.0 —— 自定义钓鱼（原 TinyFish 重构版）。
 *
 * <p>设计要点：
 * <ul>
 *   <li><b>单 jar 支持 1.13 ~ 26.x</b>：只使用 1.13 就存在的 Bukkit API，编译目标 Java 8。</li>
 *   <li><b>默认虚拟鱼箱</b>：鱼获不进背包，直接进服务端数据，零伪造风险。</li>
 *   <li><b>物品模式带 HMAC 签名</b>：1.13 没有 PDC，所以用签名 lore 防篡改。</li>
 *   <li><b>零编译期依赖</b>：AttributePlus / NeigeItems / PlaceholderAPI / Vault 全部反射桥接。</li>
 * </ul>
 */
public final class TinyFishPlugin extends JavaPlugin {

    public static final String VERSION = "2.0.0";

    /** 数据落盘间隔（秒）。 */
    private static final int SAVE_INTERVAL_SECONDS = 60;

    private ConfigManager configManager;
    private PlayerDataStore dataStore;
    private EconomyProvider economy;
    private FishService fishService;
    private AttributeSink attributeSink;
    private CollectionService collectionService;
    private LeaderboardService leaderboardService;
    private GuiListener guiListener;
    private AutoFishingService autoFishingService;
    private UpdateChecker updateChecker;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        // 顺序依赖：ConfigManager 必须先于 PlayerDataStore（后者读 starting-balance）
        configManager = new ConfigManager(this);
        configManager.reload();

        dataStore = new PlayerDataStore(this, SAVE_INTERVAL_SECONDS);
        dataStore.load();

        economy = EcoBridge.detect(this, configManager, dataStore);
        if (economy == null) {
            economy = new InternalEconomy(dataStore, configManager);
        }

        attributeSink = createAttributeSink();
        NeigeItemsBridge neige = NeigeItemsBridge.detect(this);
        fishService = new FishService(configManager, dataStore, economy, neige);
        collectionService = new CollectionService(configManager, dataStore, economy, attributeSink);
        leaderboardService = new LeaderboardService(configManager, dataStore);

        GuiRender guiRender = new GuiRender(configManager, dataStore, fishService,
                collectionService, economy, leaderboardService);
        guiListener = new GuiListener(guiRender, configManager, dataStore,
                fishService, collectionService, economy);

        autoFishingService = new AutoFishingService(this, configManager, fishService);
        autoFishingService.start();

        updateChecker = new UpdateChecker(this, configManager);
        updateChecker.start();

        getServer().getPluginManager().registerEvents(
                new FishingListener(fishService, autoFishingService), this);
        getServer().getPluginManager().registerEvents(
                new PlayerStateListener(this, collectionService, autoFishingService, updateChecker), this);
        getServer().getPluginManager().registerEvents(guiListener, this);

        PluginCommand command = getCommand("tf");
        if (command != null) {
            TinyFishCommand handler = new TinyFishCommand(
                    configManager, dataStore, fishService, economy,
                    collectionService, guiListener, autoFishingService);
            command.setExecutor(handler);
            command.setTabCompleter(handler);
        } else {
            getLogger().severe("plugin.yml 里找不到 tinyfish 命令，指令功能不可用");
        }

        selfTestCodec();
        printBanner();
    }

    @Override
    public void onDisable() {
        if (updateChecker != null) {
            updateChecker.stop();
        }
        if (autoFishingService != null) {
            autoFishingService.stop();
        }
        if (collectionService != null) {
            for (org.bukkit.entity.Player player : getServer().getOnlinePlayers()) {
                collectionService.clear(player);
            }
        }
        if (dataStore != null) {
            dataStore.shutdown();
        }
    }

    /**
     * 选属性输出端：
     * attributeplus 配置 → 必须成功；auto → 有就用；lore → 强制只展示。
     */
    private AttributeSink createAttributeSink() {
        String mode = configManager.getAttributeSink();
        if ("lore".equals(mode)) {
            return new LoreSink();
        }
        AttributePlusSink plus = AttributePlusSink.detect(this);
        if (plus != null) {
            return plus;
        }
        if ("attributeplus".equals(mode)) {
            getLogger().warning("config 要求 attributes.sink=attributeplus，但 AttributePlus 不可用，已降级为 lore");
        }
        return new LoreSink();
    }

    /**
     * 启动自检：用一条假数据跑一遍 encode→decode，再故意改一个字节确认验签会失败。
     * 这能提前发现 JDK 的 HmacSHA256 不可用、或密钥读取异常 —— 而不是等玩家发现物品失效。
     */
    private void selfTestCodec() {
        FishCodec codec = configManager.getCodec();
        try {
            String line = codec.encode("__selftest__", 1234, "5.00");
            FishCodec.Decoded decoded = codec.decode(line);
            if (decoded == null || !"__selftest__".equals(decoded.fishId) || decoded.weight != 1234) {
                getLogger().severe("签名自检失败：合法数据无法解析，物品模式将不可用");
                return;
            }
            FishCodec.Decoded tampered = codec.decode(line.replace("|1234|", "|9999|"));
            if (tampered != null) {
                getLogger().severe("签名自检失败：篡改后的数据仍能通过验签，请立即检查 security.secret");
                return;
            }
            getLogger().info("签名自检通过（合法数据可解析，篡改数据被拒绝）");
        } catch (Throwable t) {
            getLogger().severe("签名自检异常: " + t);
        }
    }

    /**
     * 当前生效的插件版本。
     *
     * <p>故意读 plugin.yml 而不是用常量：常量容易和实际发布的版本号不同步，
     * 而版本检查拿的就是这个值 —— 报错了会误导服主。
     */
    public String currentVersion() {
        try {
            String v = getDescription().getVersion();
            if (v != null && !v.trim().isEmpty()) {
                return v.trim();
            }
        } catch (Throwable ignored) {
            // 极新核心可能移除该方法，回落到常量
        }
        return VERSION;
    }

    private void printBanner() {
        getLogger().info("========================================");
        getLogger().info(" TinyFish v" + currentVersion() + " —— 自定义钓鱼");
        getLogger().info(" 支持范围: 1.13 ~ 26.x（Java 8 字节码, api-version 1.13）");
        getLogger().info(" 投递模式: " + configManager.getDelivery()
                + ("virtual".equals(configManager.getDelivery())
                    ? "（鱼获进虚拟鱼箱，不可交易）"
                    : "（生成签名物品，可交易）"));
        getLogger().info(" 鱼种数量: " + configManager.getFishDefinitions().size());
        getLogger().info(" 属性输出: " + configManager.getAttributeSink()
                + " → 实际 " + attributeSink.name()
                + " | 每属性上限: " + configManager.getAttributeMaxTotal());
        getLogger().info(" 经济来源: " + economy.name());
        getLogger().info(" 自动钓鱼: " + (configManager.isAutoFishingEnabled() ? "已启用" : "已关闭")
                + " | 咬钩加速: " + fishService.hookSpeedMode());
        getLogger().info("========================================");
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public PlayerDataStore getDataStore() {
        return dataStore;
    }

    public EconomyProvider getEconomy() {
        return economy;
    }

    public FishService getFishService() {
        return fishService;
    }

    public AttributeSink getAttributeSink() {
        return attributeSink;
    }

    public CollectionService getCollectionService() {
        return collectionService;
    }
}
