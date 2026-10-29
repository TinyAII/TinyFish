package com.tinyaii.tinyfish.service;

import com.tinyaii.tinyfish.config.ConfigManager;
import com.tinyaii.tinyfish.fish.FishDefinition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 排行榜。
 *
 * <p>两种口径（config 的 {@code leaderboard.metric}）：
 * <ul>
 *   <li>{@code best}  —— 单条最大克重（默认），比较刺激，能让玩家追求"钓一条巨物"</li>
 *   <li>{@code total} —— 各鱼种最大克重之和，奖励全面发展</li>
 * </ul>
 */
public final class LeaderboardService {

    private final ConfigManager config;
    private final PlayerDataStore dataStore;

    public LeaderboardService(ConfigManager config, PlayerDataStore dataStore) {
        this.config = config;
        this.dataStore = dataStore;
    }

    /** 一行排行数据。 */
    public static final class Row {
        public final UUID uuid;
        public final String name;
        public final int weight;
        /** best 口径下是哪一条鱼拿了最高克重；total 口径下为 null。 */
        public final String fishId;

        Row(UUID uuid, String name, int weight, String fishId) {
            this.uuid = uuid;
            this.name = name;
            this.weight = weight;
            this.fishId = fishId;
        }
    }

    /**
     * 取前 limit 名（已排序，从高到低）。
     *
     * <p>每次调用都扫一遍 data.yml 的内存镜像 —— 百人规模下是毫秒级。
     * 如果以后玩家数上千，这里要加缓存。
     */
    public List<Row> top(int limit) {
        boolean byBest = "best".equalsIgnoreCase(config.getLeaderboardMetric());
        List<Row> rows = new ArrayList<Row>();

        for (UUID id : dataStore.getKnownPlayers()) {
            String name = dataStore.getStoredName(id);
            if (name == null || name.isEmpty()) {
                name = id.toString().substring(0, 8);
            }
            if (byBest) {
                int bestWeight = 0;
                String bestFish = null;
                for (FishDefinition def : config.getFishDefinitions()) {
                    int weight = dataStore.getBestWeight(id, def.getId());
                    if (weight > bestWeight) {
                        bestWeight = weight;
                        bestFish = def.getId();
                    }
                }
                if (bestWeight > 0) {
                    rows.add(new Row(id, name, bestWeight, bestFish));
                }
            } else {
                int sum = 0;
                for (FishDefinition def : config.getFishDefinitions()) {
                    sum += dataStore.getBestWeight(id, def.getId());
                }
                if (sum > 0) {
                    rows.add(new Row(id, name, sum, null));
                }
            }
        }

        Collections.sort(rows, new Comparator<Row>() {
            @Override
            public int compare(Row a, Row b) {
                int byWeight = Integer.compare(b.weight, a.weight);
                return byWeight != 0 ? byWeight : a.name.compareToIgnoreCase(b.name);
            }
        });

        if (limit > 0 && rows.size() > limit) {
            return new ArrayList<Row>(rows.subList(0, limit));
        }
        return rows;
    }

    /** 取前 limit 名的总页数（每页 pageSize 条）。 */
    public int pageCount(int pageSize) {
        int size = top(0).size();
        return Math.max(1, (size + pageSize - 1) / pageSize);
    }

    /** 取某一页的数据。 */
    public List<Row> page(int page, int pageSize) {
        List<Row> all = top(0);
        int from = Math.max(0, (page - 1) * pageSize);
        if (from >= all.size()) {
            return Collections.emptyList();
        }
        int to = Math.min(all.size(), from + pageSize);
        return new ArrayList<Row>(all.subList(from, to));
    }

    /** 玩家自己的排名（1 起，0 = 没上榜）。 */
    public int rankOf(UUID id) {
        List<Row> all = top(0);
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).uuid.equals(id)) {
                return i + 1;
            }
        }
        return 0;
    }

    /** 鱼箱里某鱼种的累计克重（用于排行榜 total 口径的展示）。 */
    public long totalBoxWeight(UUID id) {
        Map<String, PlayerDataStore.BoxEntry> box = dataStore.getFishBox(id);
        long sum = 0;
        for (PlayerDataStore.BoxEntry entry : box.values()) {
            sum += entry.total;
        }
        return sum;
    }
}
