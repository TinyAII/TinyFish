package com.tinyaii.tinyfish.economy;

import java.util.UUID;

/**
 * 经济提供者。所有金额操作都走这个接口，
 * 上层（卖鱼、回收）不关心钱是内置的还是 Vault 的。
 */
public interface EconomyProvider {

    /** 给人看的名字，用于日志/提示。 */
    String name();

    /** 当前是否可用（反射探测失败时返回 false）。 */
    boolean available();

    double balance(UUID id);

    boolean has(UUID id, double amount);

    /** 扣钱。余额不足或出错返回 false，调用方不要再扣第二次。 */
    boolean withdraw(UUID id, double amount);

    /** 加钱。 */
    boolean deposit(UUID id, double amount);

    /** 格式化金额，带货币名。 */
    String format(double amount);
}
