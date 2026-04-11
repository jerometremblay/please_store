package com.jerome.pleasestore;

import com.jerome.pleasestore.config.PleaseStoreConfig;
import com.jerome.pleasestore.registry.ModBlockEntities;
import com.jerome.pleasestore.registry.ModBlocks;
import com.mojang.logging.LogUtils;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import org.slf4j.Logger;

@Mod(PleaseStoreMod.MOD_ID)
public final class PleaseStoreMod {
    public static final String MOD_ID = "pleasestore";
    public static final Logger LOGGER = LogUtils.getLogger();

    public PleaseStoreMod(IEventBus modEventBus, ModContainer modContainer) {
        ModBlocks.register(modEventBus);
        ModBlockEntities.register(modEventBus);
        modEventBus.addListener(this::addCreativeTabEntries);
        modContainer.registerConfig(ModConfig.Type.COMMON, PleaseStoreConfig.COMMON_SPEC);
        if (FMLEnvironment.getDist().isClient()) {
            modContainer.registerExtensionPoint(IConfigScreenFactory.class, (container, parent) -> new ConfigurationScreen(modContainer, parent));
        }
        LOGGER.info("Loading {}", MOD_ID);
    }

    @SubscribeEvent
    private void addCreativeTabEntries(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(ModBlocks.PLEASE_STORE_CONTROLLER_ITEM);
        }
    }
}
