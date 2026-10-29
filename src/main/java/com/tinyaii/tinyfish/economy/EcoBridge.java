package com.tinyaii.tinyfish.economy;

import com.tinyaii.tinyfish.config.ConfigManager;
import com.tinyaii.tinyfish.service.PlayerDataStore;
import com.tinyaii.tinyfish.util.Texts;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * 外部经济桥（全反射，不引编译期依赖）。
 *
 * <p>探测顺序按配置 {@code economy.provider}：
 * <ul>
 *   <li>{@code auto}   ：自家 Economy → Vault → 内置</li>
 *   <li>{@code own}    ：只试自家 Economy</li>
 *   <li>{@code vault}  ：只试 Vault</li>
 *   <li>{@code internal}：直接内置</li>
 * </ul>
 *
 * <p><b>待确认</b>：自家 Economy 的 {@code nl.tinyaii.economy.api.EconomyAPI} 只核实过
 * {@code has / withdraw} 两个方法（TinyClaim 的 EcoBridge 在用）。其余方法名是按常见命名
 * 做**候选探测**，不是官方契约；探测不到就自动降级，不会报错。
 */
public final class EcoBridge implements EconomyProvider {

    private final Kind kind;
    private final Object target;
    private final ConfigManager config;

    private Method mBalance;
    private Method mHas;
    private Method mWithdraw;
    private Method mDeposit;
    private Method mFormat;

    private enum Kind { OWN, VAULT }

    private EcoBridge(Kind kind, Object target, ConfigManager config) {
        this.kind = kind;
        this.target = target;
        this.config = config;
    }

    // ────────────────────────── 探测 ──────────────────────────

    /**
     * 按配置探测可用的外部经济。
     *
     * @return 探测不到返回 null，调用方应回落到 {@link InternalEconomy}
     */
    public static EcoBridge detect(Plugin plugin, ConfigManager config, PlayerDataStore dataStore) {
        String mode = config.getEconomyProvider();
        if ("internal".equals(mode)) {
            return null;
        }
        if ("own".equals(mode) || "auto".equals(mode)) {
            EcoBridge own = tryOwn(plugin, config);
            if (own != null) {
                return own;
            }
        }
        if ("vault".equals(mode) || "auto".equals(mode)) {
            EcoBridge vault = tryVault(plugin, config);
            if (vault != null) {
                return vault;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static EcoBridge tryOwn(Plugin plugin, ConfigManager config) {
        if (Bukkit.getPluginManager().getPlugin("Economy") == null) {
            return null;
        }
        try {
            Class<?> api = Class.forName("nl.tinyaii.economy.api.EconomyAPI");
            EcoBridge bridge = new EcoBridge(Kind.OWN, null, config);
            bridge.mHas = findStatic(api, new String[]{"has"}, UUID.class, double.class);
            bridge.mWithdraw = findStatic(api, new String[]{"withdraw", "take"}, UUID.class, double.class);
            bridge.mDeposit = findStatic(api, new String[]{"deposit", "add", "give"}, UUID.class, double.class);
            bridge.mBalance = findStatic(api, new String[]{"balance", "getBalance", "get", "balanceOf"}, UUID.class);
            if (bridge.mHas == null || bridge.mWithdraw == null || bridge.mDeposit == null) {
                plugin.getLogger().warning("[经济桥] 找到 Economy 插件，但 API 方法不匹配（has/withdraw/deposit），已跳过");
                return null;
            }
            if (bridge.mBalance == null) {
                plugin.getLogger().warning("[经济桥] 自家 Economy 无余额查询方法，balance 将返回 0");
            }
            plugin.getLogger().info("[经济桥] 已对接自家 Economy");
            return bridge;
        } catch (Throwable t) {
            plugin.getLogger().info("[经济桥] 未找到可用的自家 Economy（" + t.getClass().getSimpleName() + "）");
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static EcoBridge tryVault(Plugin plugin, ConfigManager config) {
        try {
            Class<?> ecoClass = Class.forName("net.milkbowl.vault.economy.Economy");
            RegisteredServiceProvider<?> rsp =
                    Bukkit.getServicesManager().getRegistration((Class) ecoClass);
            if (rsp == null || rsp.getProvider() == null) {
                return null;
            }
            Object provider = rsp.getProvider();
            EcoBridge bridge = new EcoBridge(Kind.VAULT, provider, config);
            bridge.mBalance = ecoClass.getMethod("getBalance", OfflinePlayer.class);
            bridge.mHas = ecoClass.getMethod("has", OfflinePlayer.class, double.class);
            bridge.mWithdraw = ecoClass.getMethod("withdrawPlayer", OfflinePlayer.class, double.class);
            bridge.mDeposit = ecoClass.getMethod("depositPlayer", OfflinePlayer.class, double.class);
            try {
                bridge.mFormat = ecoClass.getMethod("format", double.class);
            } catch (NoSuchMethodException ignored) {
                bridge.mFormat = null;
            }
            plugin.getLogger().info("[经济桥] 已对接 Vault");
            return bridge;
        } catch (Throwable t) {
            return null;
        }
    }

    private static Method findStatic(Class<?> type, String[] names, Class<?>... params) {
        for (String name : names) {
            try {
                Method m = type.getMethod(name, params);
                if (java.lang.reflect.Modifier.isStatic(m.getModifiers())) {
                    m.setAccessible(true);
                    return m;
                }
            } catch (NoSuchMethodException ignored) {
                // 试下一个候选名
            }
        }
        return null;
    }

    // ────────────────────────── 实现 ──────────────────────────

    @Override
    public String name() {
        return kind == Kind.OWN ? "自家 Economy" : "Vault";
    }

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public double balance(UUID id) {
        if (mBalance == null) {
            return 0.0;
        }
        try {
            Object arg = argFor(id);
            Object result = mBalance.invoke(target, arg);
            return result instanceof Number ? ((Number) result).doubleValue() : 0.0;
        } catch (Throwable t) {
            warnOnce("查询余额", t);
            return 0.0;
        }
    }

    @Override
    public boolean has(UUID id, double amount) {
        if (amount <= 0) {
            return true;
        }
        try {
            Object result = mHas.invoke(target, argFor(id), amount);
            return result instanceof Boolean && (Boolean) result;
        } catch (Throwable t) {
            warnOnce("检查余额", t);
            return false;
        }
    }

    @Override
    public boolean withdraw(UUID id, double amount) {
        if (amount <= 0) {
            return true;
        }
        try {
            Object result = mWithdraw.invoke(target, argFor(id), amount);
            return success(result);
        } catch (Throwable t) {
            warnOnce("扣款", t);
            return false;
        }
    }

    @Override
    public boolean deposit(UUID id, double amount) {
        if (amount <= 0) {
            return true;
        }
        try {
            Object result = mDeposit.invoke(target, argFor(id), amount);
            return success(result);
        } catch (Throwable t) {
            warnOnce("入账", t);
            return false;
        }
    }

    @Override
    public String format(double amount) {
        if (mFormat != null) {
            try {
                Object result = mFormat.invoke(target, amount);
                if (result instanceof String) {
                    return (String) result;
                }
            } catch (Throwable ignored) {
                // 回落到本地格式化
            }
        }
        return Texts.formatNumber(amount, config.getMoneyScale()) + " " + config.getCurrencyName();
    }

    /** Own 用 UUID，Vault 用 OfflinePlayer。 */
    private Object argFor(UUID id) {
        if (kind == Kind.OWN) {
            return id;
        }
        OfflinePlayer offline = Bukkit.getOfflinePlayer(id);
        return offline;
    }

    /**
     * 判定调用结果：
     * <ul>
     *   <li>自家 Economy：返回 boolean</li>
     *   <li>Vault：返回 EconomyResponse，读 transactionSuccess()</li>
     * </ul>
     */
    private boolean success(Object result) {
        if (result instanceof Boolean) {
            return (Boolean) result;
        }
        if (result == null) {
            // 无返回值的方法按成功处理（部分经济插件 withdraw 返回 void）
            return true;
        }
        try {
            Method m = result.getClass().getMethod("transactionSuccess");
            Object ok = m.invoke(result);
            return ok instanceof Boolean && (Boolean) ok;
        } catch (Throwable ignored) {
            try {
                java.lang.reflect.Field f = result.getClass().getField("transactionSuccess");
                Object ok = f.get(result);
                return ok instanceof Boolean && (Boolean) ok;
            } catch (Throwable ignored2) {
                return true;
            }
        }
    }

    private volatile boolean warned = false;

    private void warnOnce(String action, Throwable t) {
        if (warned) {
            return;
        }
        warned = true;
        Bukkit.getLogger().warning("[TinyFish][经济桥] " + action + " 失败: " + t);
    }
}
