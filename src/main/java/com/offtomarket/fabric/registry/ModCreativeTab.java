package com.offtomarket.fabric.registry;

import com.offtomarket.fabric.OffToMarketFabric;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

/**
 * Fabric counterpart of the Forge edition's ModCreativeTab (a CreativeModeTab
 * subclass with the gold coin as icon). In 1.20.1 the tab is a registry
 * entry; FabricItemGroup.builder provides the Fabric-side builder.
 * Phase 1 lists the three coins; Phase 2 adds every ported item.
 */
public class ModCreativeTab {
    public static final CreativeModeTab TAB = Registry.register(
            BuiltInRegistries.CREATIVE_MODE_TAB,
            new ResourceLocation(OffToMarketFabric.MODID, "offtomarket"),
            FabricItemGroup.builder()
                    .title(Component.translatable("itemGroup.offtomarket"))
                    .icon(() -> new ItemStack(ModItems.GOLD_COIN))
                    .displayItems((params, output) -> {
                        output.accept(ModItems.COPPER_COIN);
                        output.accept(ModItems.SILVER_COIN);
                        output.accept(ModItems.GOLD_COIN);
                    })
                    .build());

    public static void register() {
        // Loading this class performs the registration above.
    }
}
