package com.jerome.pleasestore.registry;

import com.jerome.pleasestore.PleaseStoreMod;
import com.jerome.pleasestore.block.PleaseStoreControllerBlock;
import com.jerome.pleasestore.item.PleaseStoreControllerBlockItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(PleaseStoreMod.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(PleaseStoreMod.MOD_ID);

    public static final DeferredBlock<PleaseStoreControllerBlock> PLEASE_STORE_CONTROLLER = BLOCKS.registerBlock("please_store_controller",
            PleaseStoreControllerBlock::new,
            properties -> properties
                    .mapColor(MapColor.COLOR_BROWN)
                    .strength(2.0F)
                    .sound(SoundType.WOOD));

    public static final DeferredItem<BlockItem> PLEASE_STORE_CONTROLLER_ITEM = ITEMS.registerItem("please_store_controller",
            properties -> new PleaseStoreControllerBlockItem(PLEASE_STORE_CONTROLLER.get(), properties));

    private ModBlocks() {
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
    }
}
