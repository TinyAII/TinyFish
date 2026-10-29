package com.tinyaii.tinyfish.attribute;

import com.tinyaii.tinyfish.fish.AttributeMath;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * AttributePlus 输出端（全反射，不引编译期依赖）。
 *
 * <p>调用方式来自老 TinyFish 的反编译代码，是实际跑通过的：
 * <pre>
 * AttributeAPI.deleteAttribute(player, source)
 * AttributeAPI.addAttribute(player, source, List&lt;String&gt;, true)
 * AttributeAPI.updateAttribute(player)
 * </pre>
 *
 * <p><b>待确认</b>：AttributePlus 有新旧两套包名（{@code org.serverct.ersha.api}
 * 与 {@code org.serverct.ersha.jd}），下面会依次尝试。任一方法签名不匹配就整体降级到 lore 模式。
 */
public final class AttributePlusSink implements AttributeSink {

    private static final String SOURCE = "TinyFish";

    private final Plugin plugin;
    private Method mAdd;
    private Method mDelete;
    private Method mUpdate;
    private boolean ready;

    private AttributePlusSink(Plugin plugin) {
        this.plugin = plugin;
    }

    /**
     * 探测 AttributePlus。装了就返回实例，否则返回 null。
     */
    public static AttributePlusSink detect(Plugin plugin) {
        if (Bukkit.getPluginManager().getPlugin("AttributePlus") == null) {
            return null;
        }
        AttributePlusSink sink = new AttributePlusSink(plugin);
        String[] classNames = {
                "org.serverct.ersha.api.AttributeAPI",
                "org.serverct.ersha.jd.AttributeAPI"
        };
        for (String className : classNames) {
            try {
                Class<?> api = Class.forName(className);
                Method add = find(api, "addAttribute", Player.class, String.class, List.class, boolean.class);
                Method delete = find(api, "deleteAttribute", Player.class, String.class);
                Method update = find(api, "updateAttribute", Player.class);
                if (add == null || delete == null) {
                    continue;
                }
                sink.mAdd = add;
                sink.mDelete = delete;
                sink.mUpdate = update;
                sink.ready = true;
                plugin.getLogger().info("[属性] 已对接 AttributePlus（" + className + "）");
                return sink;
            } catch (Throwable ignored) {
                // 试下一个包名
            }
        }
        plugin.getLogger().warning("[属性] 找到 AttributePlus 但 API 不匹配，已降级为 lore 展示");
        return null;
    }

    private static Method find(Class<?> type, String name, Class<?>... params) {
        try {
            Method m = type.getMethod(name, params);
            m.setAccessible(true);
            return m;
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    @Override
    public String name() {
        return "attributeplus";
    }

    @Override
    public boolean available() {
        return ready;
    }

    @Override
    public void apply(Player player, Map<String, Double> attributes) {
        if (!ready) {
            return;
        }
        try {
            mDelete.invoke(null, player, SOURCE);
            if (attributes != null && !attributes.isEmpty()) {
                List<String> lines = new ArrayList<String>();
                for (Map.Entry<String, Double> entry : attributes.entrySet()) {
                    lines.add(AttributeMath.formatLine(entry.getKey(), entry.getValue()));
                }
                mAdd.invoke(null, player, SOURCE, lines, Boolean.TRUE);
            }
            if (mUpdate != null) {
                mUpdate.invoke(null, player);
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("[属性] 应用 AttributePlus 属性失败: " + t);
        }
    }

    @Override
    public void clear(Player player) {
        if (!ready) {
            return;
        }
        try {
            mDelete.invoke(null, player, SOURCE);
            if (mUpdate != null) {
                mUpdate.invoke(null, player);
            }
        } catch (Throwable ignored) {
            // 清理失败不影响主流程
        }
    }
}
