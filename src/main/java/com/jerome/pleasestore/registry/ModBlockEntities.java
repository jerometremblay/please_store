package com.jerome.pleasestore.registry;

import com.jerome.pleasestore.PleaseStoreMod;
import com.jerome.pleasestore.block.entity.PleaseStoreControllerBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, PleaseStoreMod.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PleaseStoreControllerBlockEntity>> PLEASE_STORE_CONTROLLER =
            BLOCK_ENTITY_TYPES.register("please_store_controller",
                    () -> BlockEntityType.Builder.of(PleaseStoreControllerBlockEntity::new, ModBlocks.PLEASE_STORE_CONTROLLER.get()).build(null));

    private ModBlockEntities() {
    }

    public static void register(IEventBus modEventBus) {
        BLOCK_ENTITY_TYPES.register(modEventBus);
    }
}
