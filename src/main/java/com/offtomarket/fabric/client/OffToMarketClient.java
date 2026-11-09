package com.offtomarket.fabric.client;

import net.fabricmc.api.ClientModInitializer;

/**
 * Client entrypoint stub. The Forge edition registers its six menu screens
 * from a Dist.CLIENT EventBusSubscriber (FMLClientSetupEvent ->
 * MenuScreens.register); here the equivalent MenuScreens.register calls land
 * in Phase 5 alongside the ported screens (PORT-AUDIT.md SS7).
 */
public class OffToMarketClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        // Phase 5: MenuScreens.register(...) for the six menu types.
    }
}
