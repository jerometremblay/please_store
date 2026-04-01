package com.jerome.pleasestore.client;

import com.jerome.pleasestore.block.PleaseStoreControllerBlock;
import com.jerome.pleasestore.block.entity.PleaseStoreControllerBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.ChestRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class PleaseStoreControllerRenderer implements BlockEntityRenderer<PleaseStoreControllerBlockEntity> {
    private static final float CHEST_SCALE = 0.5F;
    private static final float CHEST_Y_OFFSET = 0.5F;

    private final ChestRenderer<ChestBlockEntity> chestRenderer;

    public PleaseStoreControllerRenderer(BlockEntityRendererProvider.Context context) {
        this.chestRenderer = new ChestRenderer<>(context);
    }

    @Override
    public void render(PleaseStoreControllerBlockEntity controller, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight, int packedOverlay) {
        BlockState controllerState = controller.getBlockState();
        if (!controllerState.hasProperty(PleaseStoreControllerBlock.FACING)) {
            return;
        }

        Direction facing = controllerState.getValue(PleaseStoreControllerBlock.FACING);
        BlockState chestState = Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.SOUTH);
        ChestBlockEntity chest = new ChestBlockEntity(controller.getBlockPos(), chestState);
        chest.setLevel(controller.getLevel());

        poseStack.pushPose();
        poseStack.translate(0.5F, CHEST_Y_OFFSET, 0.5F);
        poseStack.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        poseStack.scale(CHEST_SCALE, CHEST_SCALE, CHEST_SCALE);
        poseStack.translate(-0.5F, 0.0F, -0.5F);
        this.chestRenderer.render(chest, partialTick, poseStack, buffer, packedLight, packedOverlay);
        poseStack.popPose();
    }
}
