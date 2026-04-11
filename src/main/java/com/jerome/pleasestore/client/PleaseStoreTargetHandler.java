package com.jerome.pleasestore.client;

import com.jerome.pleasestore.PleaseStoreMod;
import com.jerome.pleasestore.block.entity.PleaseStoreControllerBlockEntity;
import com.jerome.pleasestore.item.PleaseStoreControllerBlockItem;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

@EventBusSubscriber(modid = PleaseStoreMod.MOD_ID, value = Dist.CLIENT)
public final class PleaseStoreTargetHandler {
    private static final float TARGET_RED = 1.0F;
    private static final float TARGET_GREEN = 0.7843F;
    private static final float TARGET_BLUE = 0.3294F;
    private static final float TARGET_ALPHA = 1.0F;
    private static final int TARGET_COLOR = 0xFFFFC854;

    private PleaseStoreTargetHandler() {
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent.AfterEntities event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) {
            return;
        }

        BlockPos targetPos = null;
        ItemStack stack = player.getMainHandItem();
        if (stack.getItem() instanceof PleaseStoreControllerBlockItem) {
            targetPos = PleaseStoreControllerBlockItem.getStoredTarget(stack);
        } else if (minecraft.hitResult instanceof BlockHitResult hitResult
                && hitResult.getType() == HitResult.Type.BLOCK
                && minecraft.level.getBlockEntity(hitResult.getBlockPos()) instanceof PleaseStoreControllerBlockEntity controller) {
            targetPos = controller.getConfiguredTargetPos();
        }

        if (targetPos == null) {
            return;
        }

        List<BlockPos> highlightPositions = PleaseStoreControllerBlockEntity.getHighlightPositions(minecraft.level, targetPos);
        if (highlightPositions.isEmpty()) {
            return;
        }

        AABB mergedBox = null;
        for (BlockPos highlightPos : highlightPositions) {
            BlockState state = minecraft.level.getBlockState(highlightPos);
            AABB worldBox = getWorldBox(minecraft, highlightPos, state);
            mergedBox = mergedBox == null ? worldBox : mergedBox.minmax(worldBox);
        }

        BlockState primaryState = minecraft.level.getBlockState(highlightPositions.get(0));
        MultiBufferSource.BufferSource bufferSource = bufferSource(minecraft);
        Vec3 cameraPosition = cameraPosition(minecraft);
        if (primaryState.getBlock() instanceof ChestBlock && primaryState.getValue(ChestBlock.TYPE) != ChestType.SINGLE && mergedBox != null) {
            renderTargetBox(event, bufferSource, cameraPosition, mergedBox);
            bufferSource.endBatch(RenderTypes.lines());
            return;
        }

        for (BlockPos highlightPos : highlightPositions) {
            renderTargetBox(event, bufferSource, cameraPosition, getWorldBox(minecraft, highlightPos, minecraft.level.getBlockState(highlightPos)));
        }
        bufferSource.endBatch(RenderTypes.lines());
    }

    private static AABB getWorldBox(Minecraft minecraft, BlockPos pos, BlockState state) {
        VoxelShape shape = state.getShape(minecraft.level, pos);
        AABB box = shape.isEmpty() ? Shapes.block().bounds() : shape.bounds();
        return box.move(pos);
    }

    private static MultiBufferSource.BufferSource bufferSource(Minecraft minecraft) {
        return minecraft.renderBuffers().bufferSource();
    }

    private static Vec3 cameraPosition(Minecraft minecraft) {
        return minecraft.gameRenderer.getMainCamera().position();
    }

    private static void renderTargetBox(RenderLevelStageEvent.AfterEntities event, MultiBufferSource.BufferSource bufferSource, Vec3 cameraPosition, AABB worldBox) {
        ShapeRenderer.renderShape(
                event.getPoseStack(),
                bufferSource.getBuffer(RenderTypes.lines()),
                Shapes.create(worldBox),
                -cameraPosition.x,
                -cameraPosition.y,
                -cameraPosition.z,
                TARGET_COLOR,
                TARGET_ALPHA
        );
    }
}
