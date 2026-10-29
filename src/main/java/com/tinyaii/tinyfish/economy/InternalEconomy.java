package com.tinyaii.tinyfish.economy;

import com.tinyaii.tinyfish.config.ConfigManager;
import com.tinyaii.tinyfish.service.PlayerDataStore;
import com.tinyaii.tinyfish.util.Texts;

import java.util.UUID;

/**
 * 内置经济：余额存在 data.yml 里。
 *
 * <p>即使服务器没装 Vault / 自家 Economy，插件也能独立工作 ——
 * 这是"零依赖单 jar"的前提。
 */
public final class InternalEconomy implements EconomyProvider {

    private final PlayerDataStore dataStore;
    private final ConfigManager config;

    public InternalEconomy(PlayerDataStore dataStore, ConfigManager config) {
        this.dataStore = dataStore;
        this.config = config;
    }

    @Override
    public String name() {
        return "内置";
    }

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public double balance(UUID id) {
        return dataStore.getBalance(id);
    }

    @Override
    public boolean has(UUID id, double amount) {
        return amount <= 0 || dataStore.getBalance(id) >= amount;
    }

    @Override
    public boolean withdraw(UUID id, double amount) {
        if (amount <= 0) {
            return true;
        }
        double current = dataStore.getBalance(id);
        if (current < amount) {
            return false;
        }
        dataStore.setBalance(id, current - amount);
        return true;
    }

    @Override
    public boolean deposit(UUID id, double amount) {
        if (amount <= 0) {
            return true;
        }
        dataStore.addBalance(id, amount);
        return true;
    }

    @Override
    public String format(double amount) {
        return Texts.formatNumber(amount, config.getMoneyScale()) + " " + config.getCurrencyName();
    }
}
