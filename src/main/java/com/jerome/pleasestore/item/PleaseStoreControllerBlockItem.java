package com.jerome.pleasestore.item;

import com.jerome.pleasestore.block.entity.PleaseStoreControllerBlockEntity;
import com.jerome.pleasestore.registry.ModBlockEntities;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.component.TypedEntityData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

public class PleaseStoreControllerBlockItem extends BlockItem {
    private static final String TARGET_POS_KEY = "target_pos";

    public PleaseStoreControllerBlockItem(Block block, Item.Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        ItemStack stack = context.getItemInHand();
        Player player = context.getPlayer();
        Level level = context.getLevel();
        BlockPos clickedPos = context.getClickedPos();

        if (player == null) {
            return super.useOn(context);
        }

        if (player.isShiftKeyDown() || !hasStoredTarget(stack)) {
            BlockPos canonicalTarget = PleaseStoreControllerBlockEntity.canonicalizeStorageTarget(level, clickedPos);
            if (canonicalTarget == null) {
                player.displayClientMessage(Component.translatable("item.pleasestore.please_store_controller.invalid_target"), true);
                return InteractionResult.SUCCESS;
            }

            storeTarget(stack, canonicalTarget);
            player.displayClientMessage(Component.translatable("item.pleasestore.please_store_controller.target_set"), true);
            return InteractionResult.SUCCESS;
        }

        return super.useOn(context);
    }

    @Override
    protected boolean updateCustomBlockEntityTag(BlockPos pos, Level level, @Nullable Player player, ItemStack stack, BlockState state) {
        boolean updated = super.updateCustomBlockEntityTag(pos, level, player, stack, state);
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof PleaseStoreControllerBlockEntity controller) {
            controller.applyPlacementData(getStoredTarget(stack));
            updated = true;
        }
        if (updated) {
            stack.remove(DataComponents.BLOCK_ENTITY_DATA);
        }
        return updated;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay tooltipDisplay, Consumer<Component> tooltipComponents, TooltipFlag isAdvanced) {
        super.appendHoverText(stack, context, tooltipDisplay, tooltipComponents, isAdvanced);

        BlockPos targetPos = getStoredTarget(stack);
        if (targetPos == null) {
            tooltipComponents.accept(Component.translatable("item.pleasestore.please_store_controller.target_missing")
                    .withStyle(ChatFormatting.GRAY));
            return;
        }

        tooltipComponents.accept(Component.translatable("item.pleasestore.please_store_controller.target_ready", formatBlockPos(targetPos))
                .withStyle(ChatFormatting.GRAY));
        tooltipComponents.accept(Component.translatable("item.pleasestore.please_store_controller.target_reset_hint")
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    public static @Nullable BlockPos getStoredTarget(ItemStack stack) {
        CompoundTag tag = getStoredBlockEntityTag(stack);
        if (!tag.contains(TARGET_POS_KEY)) {
            return null;
        }

        return tag.getLong(TARGET_POS_KEY).map(BlockPos::of).orElse(null);
    }

    private static boolean hasStoredTarget(ItemStack stack) {
        return getStoredTarget(stack) != null;
    }

    private static void storeTarget(ItemStack stack, BlockPos targetPos) {
        CompoundTag tag = getStoredBlockEntityTag(stack);
        tag.putLong(TARGET_POS_KEY, targetPos.asLong());
        setBlockEntityData(stack, tag);
    }

    private static BlockEntityType<?> getBlockEntityType() {
        return ModBlockEntities.PLEASE_STORE_CONTROLLER.get();
    }

    private static void setBlockEntityData(ItemStack stack, CompoundTag tag) {
        if (tag.isEmpty()) {
            stack.remove(DataComponents.BLOCK_ENTITY_DATA);
            return;
        }

        stack.set(DataComponents.BLOCK_ENTITY_DATA, TypedEntityData.of(getBlockEntityType(), tag));
    }

    private static CompoundTag getStoredBlockEntityTag(ItemStack stack) {
        TypedEntityData<BlockEntityType<?>> typedData = stack.get(DataComponents.BLOCK_ENTITY_DATA);
        return typedData != null && typedData.type() == getBlockEntityType()
                ? typedData.copyTagWithoutId()
                : new CompoundTag();
    }

    private static Component formatBlockPos(BlockPos pos) {
        return Component.literal(pos.getX() + ", " + pos.getY() + ", " + pos.getZ());
    }
}
