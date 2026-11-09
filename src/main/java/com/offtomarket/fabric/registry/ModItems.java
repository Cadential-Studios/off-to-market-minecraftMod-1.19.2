package com.offtomarket.fabric.registry;

import com.offtomarket.fabric.OffToMarketFabric;
import com.offtomarket.fabric.item.CoinItem;
import com.offtomarket.fabric.item.CoinType;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

/**
 * Fabric counterpart of the Forge edition's DeferredRegister-based ModItems.
 * Phase 1 ports ONLY the three coins, as the pipeline proof; registry names,
 * stack sizes and rarities match the Forge source 1:1:
 *   copper_coin (common, 99), silver_coin (UNCOMMON, 99), gold_coin (RARE, 99).
 * The remaining 10 items land in Phase 2 (see PORT-AUDIT.md SS11).
 */
public class ModItems {
    public static final Item GOLD_COIN = register("gold_coin",
            new CoinItem(new Item.Properties().rarity(Rarity.RARE).stacksTo(99), CoinType.GOLD));
    public static final Item SILVER_COIN = register("silver_coin",
            new CoinItem(new Item.Properties().rarity(Rarity.UNCOMMON).stacksTo(99), CoinType.SILVER));
    public static final Item COPPER_COIN = register("copper_coin",
            new CoinItem(new Item.Properties().stacksTo(99), CoinType.COPPER));

    private static Item register(String name, Item item) {
        return Registry.register(BuiltInRegistries.ITEM, new ResourceLocation(OffToMarketFabric.MODID, name), item);
    }

    /** Called from the mod entrypoint so the class is loaded (and registers run). */
    public static void register() {
        OffToMarketFabric.LOGGER.info("Off to Market (Fabric): registering items");
    }
}
