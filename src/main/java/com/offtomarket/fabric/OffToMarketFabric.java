package com.offtomarket.fabric;

import com.offtomarket.fabric.registry.ModCreativeTab;
import com.offtomarket.fabric.registry.ModItems;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fabric-edition entrypoint. Counterpart of the Forge main class
 * (com.offtomarket.mod.OffToMarket): same MODID by design (PORT-AUDIT.md
 * SS10). Phase 1 wires only the coin proof; later phases add blocks, block
 * entities, menus, networking and config in the order laid out in the audit.
 */
public class OffToMarketFabric implements ModInitializer {
    public static final String MODID = "offtomarket";
    public static final Logger LOGGER = LoggerFactory.getLogger(MODID);

    @Override
    public void onInitialize() {
        ModItems.register();
        ModCreativeTab.register();
        LOGGER.info("Off to Market - Trading Deluxe (Fabric) initializing!");
    }
}
