package com.jerome.pleasestore.client;

import com.jerome.pleasestore.PleaseStoreMod;
import com.jerome.pleasestore.registry.ModBlockEntities;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = PleaseStoreMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class PleaseStoreClientEvents {
    private PleaseStoreClientEvents() {
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.PLEASE_STORE_CONTROLLER.get(), PleaseStoreControllerRenderer::new);
    }
}
