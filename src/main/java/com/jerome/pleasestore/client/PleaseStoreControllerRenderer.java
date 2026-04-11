package com.jerome.pleasestore.client;

import com.jerome.pleasestore.block.PleaseStoreControllerBlock;
import com.jerome.pleasestore.block.entity.PleaseStoreControllerBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.ChestRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.blockentity.state.ChestRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public final class PleaseStoreControllerRenderer implements BlockEntityRenderer<PleaseStoreControllerBlockEntity, PleaseStoreControllerRenderer.State> {
    private static final float CHEST_SCALE = 0.5F;
    private static final float CHEST_Y_OFFSET = 0.5F;

    private final ChestRenderer<ChestBlockEntity> chestRenderer;

    public PleaseStoreControllerRenderer(BlockEntityRendererProvider.Context context) {
        this.chestRenderer = new ChestRenderer<>(context);
    }

    @Override
    public State createRenderState() {
        return new State(chestRenderer.createRenderState());
    }

    @Override
    public void extractRenderState(PleaseStoreControllerBlockEntity controller, State state, float partialTick, Vec3 cameraPos, ModelFeatureRenderer.CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(controller, state, partialTick, cameraPos, breakProgress);

        BlockState controllerState = controller.getBlockState();
        state.facing = controllerState.hasProperty(PleaseStoreControllerBlock.FACING)
                ? controllerState.getValue(PleaseStoreControllerBlock.FACING)
                : Direction.NORTH;

        ChestBlockEntity chest = createChest(controller.getBlockPos(), controller.getLevel());
        chestRenderer.extractRenderState(chest, state.chest, partialTick, cameraPos, breakProgress);
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState cameraRenderState) {
        poseStack.pushPose();
        poseStack.translate(0.5F, CHEST_Y_OFFSET, 0.5F);
        poseStack.mulPose(Axis.YP.rotationDegrees(-state.facing.toYRot()));
        poseStack.scale(CHEST_SCALE, CHEST_SCALE, CHEST_SCALE);
        poseStack.translate(-0.5F, 0.0F, -0.5F);
        chestRenderer.submit(state.chest, poseStack, submitNodeCollector, cameraRenderState);
        poseStack.popPose();
    }

    private static ChestBlockEntity createChest(BlockPos pos, Level level) {
        BlockState chestState = Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.SOUTH);
        ChestBlockEntity chest = new ChestBlockEntity(pos, chestState);
        chest.setLevel(level);
        return chest;
    }

    public static final class State extends BlockEntityRenderState {
        private final ChestRenderState chest;
        private Direction facing = Direction.NORTH;

        private State(ChestRenderState chest) {
            this.chest = chest;
        }
    }
}
