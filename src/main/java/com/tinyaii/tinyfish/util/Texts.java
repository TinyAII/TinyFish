package com.tinyaii.tinyfish.util;

import org.bukkit.ChatColor;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 文本工具。故意只依赖 Java 8 + Bukkit ChatColor ——
 * 因为要支持 1.13（Java 8），不能用 Adventure。
 */
public final class Texts {

    private Texts() {
    }

    /** 把 &a 这类颜色码转成 §a。 */
    public static String color(String text) {
        return text == null ? "" : ChatColor.translateAlternateColorCodes('&', text);
    }

    /** 去掉所有颜色码。用于读取 lore 里的数据行。 */
    public static String stripColor(String text) {
        return text == null ? "" : ChatColor.stripColor(text);
    }

    /** 按位数格式化金额（四舍五入，不用 BigDecimal(double) 那个精度陷阱）。 */
    public static String formatNumber(double value, int scale) {
        int s = Math.max(0, Math.min(6, scale));
        return BigDecimal.valueOf(value).setScale(s, RoundingMode.HALF_UP).toPlainString();
    }
}
