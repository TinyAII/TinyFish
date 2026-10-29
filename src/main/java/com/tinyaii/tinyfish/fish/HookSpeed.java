package com.tinyaii.tinyfish.fish;

import org.bukkit.entity.FishHook;

import java.lang.reflect.Method;

/**
 * 咬钩速度加成 —— 跨版本实现。
 *
 * <p>为什么不能直接写死一个方法：
 * <ul>
 *   <li><b>1.13 ~ 1.25</b>：只有 {@code getBiteChance/setBiteChance(double)}（每 tick 咬钩概率）</li>
 *   <li><b>26.x</b>：新增更精确的 {@code getTimeUntilBite/setTimeUntilBite(int)}，
 *       而 {@code setBiteChance} 已被标记 <b>removal</b>（将来会删）</li>
 * </ul>
 *
 * <p>所以策略是：**有 timeUntilBite 就用它**（精确且不会被删），
 * 没有就退回 biteChance；两者都失败就静默放弃加成 —— 绝不能因为加速功能把钓鱼搞崩。
 */
public final class HookSpeed {

    private final Method getTimeUntilBite;
    private final Method setTimeUntilBite;
    private final boolean useTimeUntil;

    private HookSpeed(Method get, Method set) {
        this.getTimeUntilBite = get;
        this.setTimeUntilBite = set;
        this.useTimeUntil = get != null && set != null;
    }

    /** 启动时探测一次，之后复用。 */
    public static HookSpeed detect() {
        try {
            Method get = FishHook.class.getMethod("getTimeUntilBite");
            Method set = FishHook.class.getMethod("setTimeUntilBite", int.class);
            return new HookSpeed(get, set);
        } catch (Throwable ignored) {
            return new HookSpeed(null, null);
        }
    }

    /** 走的是哪条路径（写进启动日志，方便排查）。 */
    public String mode() {
        return useTimeUntil ? "timeUntilBite" : "biteChance";
    }

    /**
     * 按倍率加速当前钩子。
     *
     * @param multiplier &gt; 1 才生效；1.0 表示不加成
     */
    public void apply(FishHook hook, double multiplier) {
        if (hook == null || multiplier <= 1.0) {
            return;
        }
        if (useTimeUntil) {
            try {
                Object remaining = getTimeUntilBite.invoke(hook);
                if (remaining instanceof Integer) {
                    int ticks = (Integer) remaining;
                    if (ticks > 0) {
                        int reduced = Math.max(1, (int) Math.round(ticks / multiplier));
                        setTimeUntilBite.invoke(hook, reduced);
                        return;
                    }
                }
                // 还没开始计时（0 或负数）就什么都不做
                return;
            } catch (Throwable ignored) {
                // 落到 biteChance 路径
            }
        }
        try {
            double chance = hook.getBiteChance();
            if (chance > 0.0 && !Double.isNaN(chance) && !Double.isInfinite(chance)) {
                hook.setBiteChance(Math.min(1.0, chance * multiplier));
            }
        } catch (Throwable ignored) {
            // 26.x 之后的版本可能已删除该方法；加速失败不影响钓鱼本身
        }
    }
}
