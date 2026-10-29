package com.tinyaii.tinyfish.fish;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 鱼获物品的身份编解码 —— 本插件防伪造的地基。
 *
 * <p>为什么不用 PersistentDataContainer（PDC）：
 * <b>PDC 是 1.14 才有的 API，1.13 上根本不存在</b>。而本插件要支持 1.13。
 *
 * <p>所以数据写在 lore 的一行里，但带 HMAC 签名：
 * <pre>tinyfish|v1|&lt;fishId&gt;|&lt;weight&gt;|&lt;price&gt;|&lt;sig16&gt;</pre>
 * 签名 = HMAC-SHA256(密钥, "v1|fishId|weight|price") 的前 16 位 hex。
 * 密钥只存在服务器本地的 config.yml 里，玩家拿不到 ⇒ 改任何字段都会验签失败。
 *
 * <p>这套方案的边界（不要夸大）：
 * <ul>
 *   <li>能挡：改克重、改售价、改鱼种、用 /editlore 类插件伪造</li>
 *   <li>挡不住：/give 复制一条真鱼 —— 那等同原版复制物品，属管理权限范畴</li>
 * </ul>
 */
public final class FishCodec {

    /** lore 数据行的起始标记。 */
    public static final String LORE_PREFIX = "tinyfish";
    /** 数据格式版本。改格式时递增，旧物品会自然失效而不是被误读。 */
    private static final String VERSION = "v1";
    /** 签名截断长度（hex 字符数）。16 hex = 64 bit，对游戏物品足够。 */
    private static final int SIG_HEX_LEN = 16;

    private final byte[] secret;

    public FishCodec(String secretText) {
        this.secret = decodeSecret(secretText);
    }

    /** 生成一个 32 字节随机密钥（Base64）。 */
    public static String generateSecret() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

    private static byte[] decodeSecret(String text) {
        if (text != null && !text.trim().isEmpty()) {
            String t = text.trim();
            try {
                byte[] decoded = Base64.getDecoder().decode(t);
                if (decoded.length >= 16) {
                    return decoded;
                }
            } catch (IllegalArgumentException ignored) {
                // 不是 Base64 就按原文当密钥用
            }
            return t.getBytes(StandardCharsets.UTF_8);
        }
        // 兜底：配置为空时用一次性随机密钥（本次运行内自洽，重启后旧物品失效）
        return generateSecret().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 生成要写进 lore 的签名行（不含颜色码）。
     *
     * @param priceText 已按 money-scale 格式化好的价格文本，保证同一价格只有一种写法
     */
    public String encode(String fishId, int weight, String priceText) {
        if (fishId == null || fishId.isEmpty() || fishId.indexOf('|') >= 0) {
            throw new IllegalArgumentException("fishId 不能为空且不能包含 '|': " + fishId);
        }
        String payload = VERSION + "|" + fishId + "|" + weight + "|" + priceText;
        return LORE_PREFIX + "|" + payload + "|" + sign(payload);
    }

    /**
     * 从一行 lore 解析并验签。
     *
     * @param rawLore 已去掉颜色码的一行
     * @return 验签通过则返回数据；不是本插件的行、格式不对、或签名不匹配都返回 null
     */
    public Decoded decode(String rawLore) {
        if (rawLore == null) {
            return null;
        }
        String line = rawLore.trim();
        if (!line.startsWith(LORE_PREFIX + "|")) {
            return null;
        }
        String[] parts = line.split("\\|", 6);
        if (parts.length != 6) {
            return null;
        }
        if (!VERSION.equals(parts[1])) {
            return null;
        }
        String fishId = parts[2];
        if (fishId.isEmpty()) {
            return null;
        }
        String payload = parts[1] + "|" + fishId + "|" + parts[3] + "|" + parts[4];
        String expected = sign(payload);
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                parts[5].getBytes(StandardCharsets.UTF_8))) {
            return null;
        }
        int weight;
        double price;
        try {
            weight = Integer.parseInt(parts[3]);
            price = Double.parseDouble(parts[4]);
        } catch (NumberFormatException e) {
            return null;
        }
        if (weight <= 0 || price < 0 || Double.isNaN(price) || Double.isInfinite(price)) {
            return null;
        }
        return new Decoded(fishId, weight, price);
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            byte[] raw = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(SIG_HEX_LEN);
            for (int i = 0; i < SIG_HEX_LEN / 2; i++) {
                sb.append(Character.forDigit((raw[i] >> 4) & 0xF, 16));
                sb.append(Character.forDigit(raw[i] & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            // HmacSHA256 是 JDK 必备算法，走到这里说明运行环境异常
            throw new IllegalStateException("HmacSHA256 不可用", e);
        }
    }

    /** 解析结果。 */
    public static final class Decoded {
        public final String fishId;
        public final int weight;
        public final double price;

        Decoded(String fishId, int weight, double price) {
            this.fishId = fishId;
            this.weight = weight;
            this.price = price;
        }
    }
}
