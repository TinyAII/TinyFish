package com.tinyaii.tinyfish.util;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

/**
 * 只改 config.yml 里某一行的值，保留注释和排版。
 *
 * <p>为什么不用 {@code plugin.saveConfig()}：Bukkit 的 YamlConfiguration 保存时
 * 会把整份文件的注释全部吃掉。这份配置的注释是写给服主看的，吃掉一次就废了。
 */
public final class ConfigWriter {

    private ConfigWriter() {
    }

    /**
     * 找到以 keyPrefix 开头的行，整行替换成 newLine。
     *
     * @return true = 替换成功；false = 没找到该键（调用方自行兜底）
     */
    public static boolean replaceLine(File file, String keyPrefix, String newLine) throws IOException {
        if (file == null || !file.isFile()) {
            return false;
        }
        List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
        boolean replaced = false;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).trim().startsWith(keyPrefix)) {
                lines.set(i, newLine);
                replaced = true;
                break;
            }
        }
        if (!replaced) {
            return false;
        }
        Files.write(file.toPath(), lines, StandardCharsets.UTF_8);
        return true;
    }
}
