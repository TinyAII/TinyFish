package com.tinyaii.tinyfish.fish;

/**
 * 一次鱼获的结果（纯数据）。
 */
public final class FishCatch {

    public final FishDefinition definition;
    public final int weight;
    public final double price;
    /** 已上色的显示名。 */
    public final String displayName;

    public FishCatch(FishDefinition definition, int weight, double price, String displayName) {
        this.definition = definition;
        this.weight = weight;
        this.price = price;
        this.displayName = displayName;
    }

    public String fishId() {
        return definition.getId();
    }
}
