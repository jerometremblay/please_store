package com.jerome.pleasestore.client;

import com.jerome.pleasestore.PleaseStoreMod;
import com.jerome.pleasestore.block.entity.PleaseStoreControllerBlockEntity;
import com.jerome.pleasestore.item.PleaseStoreControllerBlockItem;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderHighlightEvent;

@EventBusSubscriber(modid = PleaseStoreMod.MOD_ID, value = Dist.CLIENT)
public final class PleaseStoreTargetHandler {
    private static final float TARGET_RED = 1.0F;
    private static final float TARGET_GREEN = 0.7843F;
    private static final float TARGET_BLUE = 0.3294F;
    private static final float TARGET_ALPHA = 1.0F;

    private PleaseStoreTargetHandler() {
    }

    @SubscribeEvent
    public static void onRenderHighlight(RenderHighlightEvent.Block event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) {
            return;
        }

        BlockPos targetPos = null;
        ItemStack stack = player.getMainHandItem();
        if (stack.getItem() instanceof PleaseStoreControllerBlockItem) {
            targetPos = PleaseStoreControllerBlockItem.getStoredTarget(stack);
        } else if (minecraft.level.getBlockEntity(event.getTarget().getBlockPos()) instanceof PleaseStoreControllerBlockEntity controller) {
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
        if (primaryState.getBlock() instanceof ChestBlock && primaryState.getValue(ChestBlock.TYPE) != ChestType.SINGLE && mergedBox != null) {
            renderTargetBox(event, mergedBox);
            return;
        }

        for (BlockPos highlightPos : highlightPositions) {
            renderTargetBox(event, getWorldBox(minecraft, highlightPos, minecraft.level.getBlockState(highlightPos)));
        }
    }

    private static AABB getWorldBox(Minecraft minecraft, BlockPos pos, BlockState state) {
        VoxelShape shape = state.getShape(minecraft.level, pos);
        AABB box = shape.isEmpty() ? new AABB(BlockPos.ZERO) : shape.bounds();
        return box.move(pos);
    }

    private static void renderTargetBox(RenderHighlightEvent.Block event, AABB worldBox) {
        AABB renderBox = worldBox.move(
                -event.getCamera().getPosition().x,
                -event.getCamera().getPosition().y,
                -event.getCamera().getPosition().z
        );

        LevelRenderer.renderLineBox(
                event.getPoseStack(),
                event.getMultiBufferSource().getBuffer(RenderType.lines()),
                renderBox,
                TARGET_RED,
                TARGET_GREEN,
                TARGET_BLUE,
                TARGET_ALPHA
        );
    }
}
