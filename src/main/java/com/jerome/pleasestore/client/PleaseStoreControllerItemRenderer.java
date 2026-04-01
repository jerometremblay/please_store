package com.jerome.pleasestore.client;

import com.jerome.pleasestore.block.PleaseStoreControllerBlock;
import com.jerome.pleasestore.block.entity.PleaseStoreControllerBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public final class PleaseStoreControllerItemRenderer extends BlockEntityWithoutLevelRenderer {
    private final PleaseStoreControllerBlockEntity renderedBlockEntity;

    public PleaseStoreControllerItemRenderer(BlockEntityRenderDispatcher blockEntityRenderDispatcher, EntityModelSet entityModelSet) {
        super(blockEntityRenderDispatcher, entityModelSet);
        this.renderedBlockEntity = new PleaseStoreControllerBlockEntity(
                net.minecraft.core.BlockPos.ZERO,
                com.jerome.pleasestore.registry.ModBlocks.PLEASE_STORE_CONTROLLER.get()
                        .defaultBlockState()
                        .setValue(PleaseStoreControllerBlock.FACING, net.minecraft.core.Direction.SOUTH)
                        .setValue(PleaseStoreControllerBlock.POWERED, false)
        );
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext displayContext, PoseStack poseStack, MultiBufferSource buffer, int packedLight, int packedOverlay) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }

        renderedBlockEntity.setLevel(minecraft.level);
        minecraft.getBlockRenderer().renderSingleBlock(
                renderedBlockEntity.getBlockState(),
                poseStack,
                buffer,
                packedLight,
                packedOverlay
        );
        minecraft.getBlockEntityRenderDispatcher().renderItem(renderedBlockEntity, poseStack, buffer, packedLight, packedOverlay);
    }
}
