package com.offtomarket.fabric.item;

/**
 * Currency denominations. Ported 1:1 from the Forge edition
 * (com.offtomarket.mod.item.CoinType) minus the config-dependent
 * formatValue() helper, which returns in Phase 2 with the config port.
 * 1 GP = 10 SP = 100 CP.
 */
public enum CoinType {
    GOLD("gold", 100),
    SILVER("silver", 10),
    COPPER("copper", 1);

    private final String name;
    private final int value; // value in copper pieces

    CoinType(String name, int value) {
        this.name = name;
        this.value = value;
    }

    public String getName() {
        return name;
    }

    public int getValue() {
        return value;
    }
}
