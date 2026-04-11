package com.jerome.pleasestore.block.entity;

import com.jerome.pleasestore.PleaseStoreMod;
import com.jerome.pleasestore.config.PleaseStoreConfig;
import com.jerome.pleasestore.registry.ModBlockEntities;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.wrapper.CombinedInvWrapper;
import net.neoforged.neoforge.items.wrapper.InvWrapper;
import net.neoforged.neoforge.items.wrapper.SidedInvWrapper;
import org.jspecify.annotations.Nullable;

public class PleaseStoreControllerBlockEntity extends BlockEntity {
    private static final String RUNNING_KEY = "running";
    private static final String TARGET_POS_KEY = "target_pos";
    private static final double APPROACH_SPEED = 0.5D;
    private static final double ARRIVAL_DISTANCE_SQR = 2.0D * 2.0D;
    private static final int APPROACH_TIMEOUT_TICKS = 20 * 20;
    private static final int DEPOSIT_INTERVAL_TICKS = 20;
    private static final int MIN_OPEN_TICKS = 20;
    private static final int CHEST_LID_ANIMATION_TICKS = 20;
    private static final int MIN_STORAGE_SESSION_TICKS = MIN_OPEN_TICKS + CHEST_LID_ANIMATION_TICKS;
    private static final int MAX_ACTIVE_VILLAGERS = 5;
    private static final Map<ResourceKey<Level>, Map<UUID, BlockPos>> CLAIMED_VILLAGERS = new HashMap<>();
    private static final Map<ResourceKey<Level>, Map<UUID, Deque<BlockPos>>> QUEUED_CONTROLLER_TASKS = new HashMap<>();
    private static final String ITEM_HANDLER_CAPABILITIES_CLASS = "net.neoforged.neoforge.common.capabilities.Capabilities";
    private static final String ITEM_HANDLER_FIELD = "ITEM_HANDLER";

    private final Set<UUID> activeVillagerUuids = new HashSet<>();
    private final Set<UUID> unloadingVillagerUuids = new HashSet<>();
    private final Map<UUID, Long> approachDeadlineGameTimeByVillager = new HashMap<>();
    private final Map<UUID, Long> minimumUnloadEndGameTimeByVillager = new HashMap<>();
    private final Map<UUID, Long> nextDepositGameTimeByVillager = new HashMap<>();
    private final Map<UUID, Long> postCloseReleaseGameTimeByVillager = new HashMap<>();
    private boolean running;
    private @Nullable BlockPos targetPos;

    public PleaseStoreControllerBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.PLEASE_STORE_CONTROLLER.get(), pos, blockState);
    }

    public void applyPlacementData(@Nullable BlockPos targetPos) {
        this.targetPos = targetPos == null || level == null ? targetPos : canonicalStoragePos(level, targetPos);
        setChanged();
        syncToClient();
    }

    public @Nullable BlockPos getConfiguredTargetPos() {
        return targetPos;
    }

    public void activate(ServerLevel level) {
        BlockPos storagePos = getTargetPos(level);
        if (storagePos == null) {
            PleaseStoreMod.LOGGER.info("[PleaseStore Debug] Controller {} ignored activation without a valid target", worldPosition);
            return;
        }

        PleaseStoreMod.LOGGER.info("[PleaseStore Debug] Controller {} activated for target {}", worldPosition, storagePos);
        scheduleEligibleVillagers(level, storagePos);
        running = !activeVillagerUuids.isEmpty();
        setChanged();
    }

    public static void tick(Level level, BlockPos pos, BlockState state, PleaseStoreControllerBlockEntity controller) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        if (!controller.running && controller.unloadingVillagerUuids.isEmpty()) {
            return;
        }

        BlockPos storagePos = controller.getTargetPos(serverLevel);
        if (storagePos == null) {
            controller.releaseVillagers();
            return;
        }

        controller.tickActiveVillagers(serverLevel, storagePos);
        if (controller.activeVillagerUuids.isEmpty()) {
            controller.running = false;
            controller.setChanged();
        }
    }

    public static boolean isValidStorageTarget(Level level, BlockPos pos) {
        return canonicalStoragePos(level, pos) != null;
    }

    public static @Nullable BlockPos canonicalizeStorageTarget(Level level, BlockPos pos) {
        return canonicalStoragePos(level, pos);
    }

    public static List<BlockPos> getHighlightPositions(Level level, BlockPos targetPos) {
        BlockPos canonicalPos = canonicalStoragePos(level, targetPos);
        if (canonicalPos == null) {
            return List.of();
        }

        return getRelatedStoragePositions(level, canonicalPos);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        running = input.getBooleanOr(RUNNING_KEY, false);
        targetPos = input.getLong(TARGET_POS_KEY).map(BlockPos::of).orElse(null);
        clearRuntimeState();
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putBoolean(RUNNING_KEY, running);
        if (targetPos != null) {
            output.putLong(TARGET_POS_KEY, targetPos.asLong());
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        if (targetPos != null) {
            tag.putLong(TARGET_POS_KEY, targetPos.asLong());
        }
        tag.putBoolean(RUNNING_KEY, running);
        return tag;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void setRemoved() {
        if (level instanceof ServerLevel) {
            releaseVillagers();
        }
        super.setRemoved();
    }

    public void releaseVillagers() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        for (UUID villagerUuid : activeVillagerUuids) {
            Entity entity = serverLevel.getEntity(villagerUuid);
            if (entity instanceof Villager villager) {
                villager.getNavigation().stop();
            }
            stopUnloading(serverLevel, villagerUuid);
            releaseVillagerClaim(serverLevel, villagerUuid);
        }

        clearRuntimeState();
        running = false;
        setChanged();
    }

    private void clearRuntimeState() {
        activeVillagerUuids.clear();
        unloadingVillagerUuids.clear();
        approachDeadlineGameTimeByVillager.clear();
        minimumUnloadEndGameTimeByVillager.clear();
        nextDepositGameTimeByVillager.clear();
        postCloseReleaseGameTimeByVillager.clear();
    }

    private void tickActiveVillagers(ServerLevel level, BlockPos storagePos) {
        IItemHandler targetInventory = getStorageInventory(level, storagePos);
        if (targetInventory == null) {
            releaseVillagers();
            return;
        }

        List<ItemStack> filters = collectFilterItems(level, getRelatedStoragePositions(level, storagePos));
        Iterator<UUID> iterator = activeVillagerUuids.iterator();
        while (iterator.hasNext()) {
            UUID villagerUuid = iterator.next();
            Entity entity = level.getEntity(villagerUuid);
            if (!(entity instanceof Villager villager) || !villager.isAlive()) {
                completeVillagerRelease(level, iterator, villagerUuid, false);
                continue;
            }

            Long postCloseReleaseGameTime = postCloseReleaseGameTimeByVillager.get(villagerUuid);
            if (postCloseReleaseGameTime != null) {
                holdVillagerStill(villager);
                if (level.getGameTime() >= postCloseReleaseGameTime) {
                    completeVillagerRelease(level, iterator, villagerUuid, false);
                }
                continue;
            }

            boolean unloading = unloadingVillagerUuids.contains(villagerUuid);
            if (!villagerHasItems(villager)) {
                if (!unloading || hasSatisfiedMinimumOpenTime(villagerUuid, level.getGameTime())) {
                    beginFinishingVillager(level, iterator, villagerUuid, false);
                }
                continue;
            }

            if (!villagerCanUseTarget(villager, targetInventory, filters)) {
                if (!unloading || hasSatisfiedMinimumOpenTime(villagerUuid, level.getGameTime())) {
                    beginFinishingVillager(level, iterator, villagerUuid, false);
                }
                continue;
            }

            if (level.getGameTime() >= approachDeadlineGameTimeByVillager.getOrDefault(villagerUuid, Long.MAX_VALUE)) {
                beginFinishingVillager(level, iterator, villagerUuid, false);
                continue;
            }

            double targetX = storagePos.getX() + 0.5D;
            double targetY = storagePos.getY() + 0.5D;
            double targetZ = storagePos.getZ() + 0.5D;
            villager.getNavigation().moveTo(targetX, targetY, targetZ, APPROACH_SPEED);
            if (villager.distanceToSqr(targetX, targetY, targetZ) > ARRIVAL_DISTANCE_SQR) {
                continue;
            }

            villager.getNavigation().stop();
            approachDeadlineGameTimeByVillager.remove(villagerUuid);
            startUnloading(level, villagerUuid, storagePos);

            long gameTime = level.getGameTime();
            minimumUnloadEndGameTimeByVillager.computeIfAbsent(villagerUuid, ignored -> gameTime + MIN_STORAGE_SESSION_TICKS);
            long nextDepositGameTime = nextDepositGameTimeByVillager.computeIfAbsent(villagerUuid, ignored -> gameTime);
            if (gameTime < nextDepositGameTime) {
                continue;
            }

            boolean movedAnyItems = depositInventory(villager, targetInventory, filters);
            if (!movedAnyItems) {
                if (!hasSatisfiedMinimumOpenTime(villagerUuid, gameTime)) {
                    continue;
                }
                beginFinishingVillager(level, iterator, villagerUuid, false);
                continue;
            }

            nextDepositGameTimeByVillager.put(villagerUuid, gameTime + DEPOSIT_INTERVAL_TICKS);
            minimumUnloadEndGameTimeByVillager.put(villagerUuid, gameTime + MIN_STORAGE_SESSION_TICKS);
            if ((!villagerHasItems(villager) || !villagerCanUseTarget(villager, targetInventory, filters))
                    && hasSatisfiedMinimumOpenTime(villagerUuid, gameTime)) {
                beginFinishingVillager(level, iterator, villagerUuid, true);
            }
        }
    }

    private boolean hasSatisfiedMinimumOpenTime(UUID villagerUuid, long gameTime) {
        return gameTime >= minimumUnloadEndGameTimeByVillager.getOrDefault(villagerUuid, Long.MAX_VALUE);
    }

    private void scheduleEligibleVillagers(ServerLevel level, BlockPos storagePos) {
        IItemHandler targetInventory = getStorageInventory(level, storagePos);
        if (targetInventory == null) {
            return;
        }

        List<ItemStack> filters = collectFilterItems(level, getRelatedStoragePositions(level, storagePos));
        AABB searchBox = new AABB(storagePos).inflate(getSearchRadius());
        level.getEntitiesOfClass(Villager.class, searchBox).stream()
                .filter(Entity::isAlive)
                .filter(villager -> !villager.isPassenger())
                .filter(this::villagerHasItems)
                .filter(villager -> !activeVillagerUuids.contains(villager.getUUID()))
                .filter(villager -> villagerCanUseTarget(villager, targetInventory, filters))
                .sorted(Comparator.comparingDouble(villager -> villager.distanceToSqr(storagePos.getX() + 0.5D, storagePos.getY() + 0.5D, storagePos.getZ() + 0.5D)))
                .forEach(villager -> scheduleOrRecruitVillager(level, villager, storagePos));
    }

    private void scheduleOrRecruitVillager(ServerLevel level, Villager villager, BlockPos storagePos) {
        if (isVillagerClaimedByAnotherController(level, villager.getUUID())) {
            enqueueVillagerTask(level, villager.getUUID());
            return;
        }

        if (activeVillagerUuids.size() >= MAX_ACTIVE_VILLAGERS) {
            return;
        }

        recruitVillager(level, villager, storagePos);
    }

    private boolean recruitVillager(ServerLevel level, Villager villager, BlockPos storagePos) {
        enableDoorNavigation(villager);
        claimVillager(level, villager.getUUID());
        activeVillagerUuids.add(villager.getUUID());
        approachDeadlineGameTimeByVillager.put(villager.getUUID(), level.getGameTime() + APPROACH_TIMEOUT_TICKS);

        villager.getNavigation().moveTo(storagePos.getX() + 0.5D, storagePos.getY() + 0.5D, storagePos.getZ() + 0.5D, APPROACH_SPEED);
        setChanged();
        return true;
    }

    private boolean villagerCanUseTarget(Villager villager, IItemHandler targetInventory, List<ItemStack> filters) {
        SimpleContainer villagerInventory = villager.getInventory();
        for (int slot = 0; slot < villagerInventory.getContainerSize(); slot++) {
            ItemStack stack = villagerInventory.getItem(slot);
            if (stack.isEmpty() || !matchesFilters(stack, filters)) {
                continue;
            }

            ItemStack remainder = ItemHandlerHelper.insertItemStacked(targetInventory, stack.copy(), true);
            if (remainder.getCount() != stack.getCount()) {
                return true;
            }
        }

        return false;
    }

    private boolean villagerHasItems(Villager villager) {
        SimpleContainer inventory = villager.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (!inventory.getItem(slot).isEmpty()) {
                return true;
            }
        }

        return false;
    }

    private boolean depositInventory(Villager villager, IItemHandler targetInventory, List<ItemStack> filters) {
        SimpleContainer inventory = villager.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty() || !matchesFilters(stack, filters)) {
                continue;
            }

            ItemStack remainder = ItemHandlerHelper.insertItemStacked(targetInventory, stack.copy(), false);
            if (remainder.getCount() == stack.getCount()) {
                continue;
            }

            inventory.setItem(slot, remainder);
            return true;
        }

        return false;
    }

    private double getSearchRadius() {
        return PleaseStoreConfig.COMMON.villagerSearchRadius.get();
    }

    private void startUnloading(ServerLevel level, UUID villagerUuid, BlockPos storagePos) {
        if (!unloadingVillagerUuids.add(villagerUuid)) {
            return;
        }

        if (unloadingVillagerUuids.size() == 1) {
            toggleStorage(level, storagePos, true);
        }
    }

    private void stopUnloading(ServerLevel level, UUID villagerUuid) {
        if (!unloadingVillagerUuids.remove(villagerUuid)) {
            return;
        }

        BlockPos storagePos = getTargetPos(level);
        if (storagePos != null && unloadingVillagerUuids.isEmpty()) {
            toggleStorage(level, storagePos, false);
        }
    }

    private void toggleStorage(ServerLevel level, BlockPos storagePos, boolean open) {
        BlockEntity targetEntity = level.getBlockEntity(storagePos);
        if (targetEntity instanceof ChestBlockEntity chest) {
            BlockState state = chest.getBlockState();
            toggleChest(level, storagePos, state, open);
            if (state.getBlock() instanceof ChestBlock && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
                BlockPos connectedPos = storagePos.relative(ChestBlock.getConnectedDirection(state));
                BlockEntity connectedEntity = level.getBlockEntity(connectedPos);
                if (connectedEntity instanceof ChestBlockEntity connectedChest) {
                    toggleChest(level, connectedPos, connectedChest.getBlockState(), open);
                }
            }
            return;
        }

        if (targetEntity instanceof BarrelBlockEntity barrel) {
            toggleBarrel(level, storagePos, barrel.getBlockState(), open);
        }
    }

    private void toggleChest(ServerLevel level, BlockPos pos, BlockState state, boolean open) {
        if (!(state.getBlock() instanceof ChestBlock block)) {
            return;
        }

        level.blockEvent(pos, block, 1, open ? 1 : 0);
        playChestSound(level, pos, state, open);
    }

    private void toggleBarrel(ServerLevel level, BlockPos pos, BlockState state, boolean open) {
        level.setBlock(pos, state.setValue(BarrelBlock.OPEN, open), 3);
        playBarrelSound(level, pos, state, open);
    }

    private void playChestSound(Level level, BlockPos pos, BlockState state, boolean open) {
        ChestType chestType = state.getValue(ChestBlock.TYPE);
        if (chestType == ChestType.LEFT) {
            return;
        }

        double x = pos.getX() + 0.5D;
        double y = pos.getY() + 0.5D;
        double z = pos.getZ() + 0.5D;
        if (chestType == ChestType.RIGHT) {
            Direction direction = ChestBlock.getConnectedDirection(state);
            x += direction.getStepX() * 0.5D;
            z += direction.getStepZ() * 0.5D;
        }

        level.playSound(null, x, y, z, open ? SoundEvents.CHEST_OPEN : SoundEvents.CHEST_CLOSE, SoundSource.BLOCKS, 0.5F, level.random.nextFloat() * 0.1F + 0.9F);
    }

    private void playBarrelSound(Level level, BlockPos pos, BlockState state, boolean open) {
        Direction direction = state.getValue(BarrelBlock.FACING);
        double x = pos.getX() + 0.5D + direction.getStepX() / 2.0D;
        double y = pos.getY() + 0.5D + direction.getStepY() / 2.0D;
        double z = pos.getZ() + 0.5D + direction.getStepZ() / 2.0D;
        level.playSound(null, x, y, z, open ? SoundEvents.BARREL_OPEN : SoundEvents.BARREL_CLOSE, SoundSource.BLOCKS, 0.5F, level.random.nextFloat() * 0.1F + 0.9F);
    }

    private void enableDoorNavigation(Villager villager) {
        if (villager.getNavigation() instanceof GroundPathNavigation navigation) {
            navigation.setCanOpenDoors(true);
        }
    }

    private void resetVillagerBehavior(ServerLevel level, Villager villager) {
        holdVillagerStill(villager);
    }

    private void holdVillagerStill(Villager villager) {
        villager.getNavigation().stop();
        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        villager.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
        villager.getBrain().eraseMemory(MemoryModuleType.PATH);
        villager.getBrain().eraseMemory(MemoryModuleType.INTERACTION_TARGET);
    }

    private void beginFinishingVillager(ServerLevel level, Iterator<UUID> iterator, UUID villagerUuid, boolean playSuccessSound) {
        if (postCloseReleaseGameTimeByVillager.containsKey(villagerUuid)) {
            return;
        }

        boolean wasUnloading = unloadingVillagerUuids.contains(villagerUuid);
        stopUnloading(level, villagerUuid);
        approachDeadlineGameTimeByVillager.remove(villagerUuid);
        minimumUnloadEndGameTimeByVillager.remove(villagerUuid);
        nextDepositGameTimeByVillager.remove(villagerUuid);

        if (wasUnloading) {
            Entity entity = level.getEntity(villagerUuid);
            if (entity instanceof Villager villager) {
                holdVillagerStill(villager);
            }
            postCloseReleaseGameTimeByVillager.put(villagerUuid, level.getGameTime() + CHEST_LID_ANIMATION_TICKS);
            if (playSuccessSound) {
                if (entity instanceof Villager villager) {
                    villager.playSound(SoundEvents.VILLAGER_YES, 0.6F, 1.0F);
                }
            }
            return;
        }

        completeVillagerRelease(level, iterator, villagerUuid, playSuccessSound);
    }

    private void completeVillagerRelease(ServerLevel level, Iterator<UUID> iterator, UUID villagerUuid, boolean playSuccessSound) {
        Entity entity = level.getEntity(villagerUuid);
        if (entity instanceof Villager villager) {
            resetVillagerBehavior(level, villager);
            if (playSuccessSound) {
                villager.playSound(SoundEvents.VILLAGER_YES, 0.6F, 1.0F);
            }
        }

        stopUnloading(level, villagerUuid);
        approachDeadlineGameTimeByVillager.remove(villagerUuid);
        minimumUnloadEndGameTimeByVillager.remove(villagerUuid);
        nextDepositGameTimeByVillager.remove(villagerUuid);
        postCloseReleaseGameTimeByVillager.remove(villagerUuid);
        iterator.remove();
        handoffOrReleaseVillagerClaim(level, villagerUuid);
    }

    private void claimVillager(ServerLevel level, UUID villagerUuid) {
        CLAIMED_VILLAGERS
                .computeIfAbsent(level.dimension(), ignored -> new HashMap<>())
                .put(villagerUuid, worldPosition);
    }

    private boolean enqueueVillagerTask(ServerLevel level, UUID villagerUuid) {
        Map<UUID, BlockPos> claims = CLAIMED_VILLAGERS.get(level.dimension());
        if (claims != null && worldPosition.equals(claims.get(villagerUuid))) {
            return false;
        }

        Deque<BlockPos> queue = QUEUED_CONTROLLER_TASKS
                .computeIfAbsent(level.dimension(), ignored -> new HashMap<>())
                .computeIfAbsent(villagerUuid, ignored -> new ArrayDeque<>());
        if (queue.contains(worldPosition)) {
            return false;
        }

        queue.addLast(worldPosition);
        PleaseStoreMod.LOGGER.info("[PleaseStore Debug] Queued controller {} for villager {} behind {}", worldPosition, shortId(villagerUuid), claims == null ? "nobody" : claims.get(villagerUuid));
        return true;
    }

    private void handoffOrReleaseVillagerClaim(ServerLevel level, UUID villagerUuid) {
        Entity entity = level.getEntity(villagerUuid);
        Villager villager = entity instanceof Villager livingVillager && livingVillager.isAlive() ? livingVillager : null;

        Map<UUID, Deque<BlockPos>> queues = QUEUED_CONTROLLER_TASKS.get(level.dimension());
        Deque<BlockPos> queue = queues == null ? null : queues.get(villagerUuid);
        if (queue != null && !queue.isEmpty()) {
            PleaseStoreMod.LOGGER.info("[PleaseStore Debug] Handoff for villager {} from controller {} with queued controllers {}", shortId(villagerUuid), worldPosition, queue);
        }
        while (villager != null && queue != null && !queue.isEmpty()) {
            BlockPos controllerPos = pollNearestQueuedController(level, villager, queue);
            if (controllerPos == null) {
                break;
            }
            BlockEntity blockEntity = level.getBlockEntity(controllerPos);
            if (!(blockEntity instanceof PleaseStoreControllerBlockEntity controller)) {
                continue;
            }

            if (controller.acceptQueuedVillager(level, villager)) {
                PleaseStoreMod.LOGGER.info("[PleaseStore Debug] Handoff accepted: villager {} -> controller {}", shortId(villagerUuid), controllerPos);
                cleanupQueuedTasks(level, villagerUuid, queue);
                return;
            }

            PleaseStoreMod.LOGGER.info("[PleaseStore Debug] Handoff rejected: villager {} -> controller {}", shortId(villagerUuid), controllerPos);
        }

        cleanupQueuedTasks(level, villagerUuid, queue);
        PleaseStoreMod.LOGGER.info("[PleaseStore Debug] Releasing villager {} after controller {} finished", shortId(villagerUuid), worldPosition);
        releaseVillagerClaim(level, villagerUuid);
    }

    private @Nullable BlockPos pollNearestQueuedController(ServerLevel level, Villager villager, Deque<BlockPos> queue) {
        BlockPos bestControllerPos = null;
        double bestDistance = Double.MAX_VALUE;

        for (BlockPos controllerPos : queue) {
            BlockEntity blockEntity = level.getBlockEntity(controllerPos);
            if (!(blockEntity instanceof PleaseStoreControllerBlockEntity controller)) {
                continue;
            }

            BlockPos storagePos = controller.getTargetPos(level);
            if (storagePos == null) {
                continue;
            }

            double distance = villager.distanceToSqr(storagePos.getX() + 0.5D, storagePos.getY() + 0.5D, storagePos.getZ() + 0.5D);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestControllerPos = controllerPos;
            }
        }

        if (bestControllerPos != null) {
            queue.remove(bestControllerPos);
        }
        return bestControllerPos;
    }

    private boolean acceptQueuedVillager(ServerLevel level, Villager villager) {
        BlockPos storagePos = getTargetPos(level);
        if (storagePos == null || activeVillagerUuids.size() >= MAX_ACTIVE_VILLAGERS) {
            PleaseStoreMod.LOGGER.info("[PleaseStore Debug] Controller {} rejected villager {}: target={}, active={}/{}, chest access is serialized", worldPosition, shortId(villager.getUUID()), storagePos, activeVillagerUuids.size(), MAX_ACTIVE_VILLAGERS);
            return false;
        }

        IItemHandler targetInventory = getStorageInventory(level, storagePos);
        if (targetInventory == null) {
            PleaseStoreMod.LOGGER.info("[PleaseStore Debug] Controller {} rejected villager {}: no inventory at target {}", worldPosition, shortId(villager.getUUID()), storagePos);
            return false;
        }

        List<ItemStack> filters = collectFilterItems(level, getRelatedStoragePositions(level, storagePos));
        if (!villagerHasItems(villager) || !villagerCanUseTarget(villager, targetInventory, filters)) {
            PleaseStoreMod.LOGGER.info("[PleaseStore Debug] Controller {} rejected villager {}: no relevant items for target {}", worldPosition, shortId(villager.getUUID()), storagePos);
            return false;
        }

        if (!recruitVillager(level, villager, storagePos)) {
            PleaseStoreMod.LOGGER.info("[PleaseStore Debug] Controller {} rejected villager {}: no free approach spot for target {}", worldPosition, shortId(villager.getUUID()), storagePos);
            return false;
        }
        running = true;
        return true;
    }

    private void cleanupQueuedTasks(ServerLevel level, UUID villagerUuid, @Nullable Deque<BlockPos> queue) {
        if (queue != null && !queue.isEmpty()) {
            return;
        }

        Map<UUID, Deque<BlockPos>> queues = QUEUED_CONTROLLER_TASKS.get(level.dimension());
        if (queues == null) {
            return;
        }

        queues.remove(villagerUuid);
        if (queues.isEmpty()) {
            QUEUED_CONTROLLER_TASKS.remove(level.dimension());
        }
    }

    private void releaseVillagerClaim(ServerLevel level, UUID villagerUuid) {
        Map<UUID, BlockPos> claims = CLAIMED_VILLAGERS.get(level.dimension());
        if (claims == null) {
            return;
        }

        BlockPos claimedBy = claims.get(villagerUuid);
        if (worldPosition.equals(claimedBy)) {
            claims.remove(villagerUuid);
        }

        if (claims.isEmpty()) {
            CLAIMED_VILLAGERS.remove(level.dimension());
        }
    }

    private static String shortId(UUID uuid) {
        return uuid.toString().substring(0, 8);
    }

    private boolean isVillagerClaimedByAnotherController(ServerLevel level, UUID villagerUuid) {
        Map<UUID, BlockPos> claims = CLAIMED_VILLAGERS.get(level.dimension());
        if (claims == null) {
            return false;
        }

        BlockPos claimedBy = claims.get(villagerUuid);
        return claimedBy != null && !worldPosition.equals(claimedBy);
    }

    private @Nullable BlockPos getTargetPos(Level level) {
        if (targetPos == null) {
            return null;
        }

        BlockPos canonicalPos = canonicalStoragePos(level, targetPos);
        if (canonicalPos == null) {
            return null;
        }

        if (!canonicalPos.equals(targetPos)) {
            targetPos = canonicalPos;
            setChanged();
            syncToClient();
        }
        return canonicalPos;
    }

    private void syncToClient() {
        if (level == null) {
            return;
        }

        BlockState state = getBlockState();
        level.sendBlockUpdated(worldPosition, state, state, 3);
    }

    private static boolean matchesFilters(ItemStack stack, List<ItemStack> filters) {
        if (filters.isEmpty()) {
            return true;
        }

        for (ItemStack filter : filters) {
            if (ItemStack.isSameItemSameComponents(filter, stack)) {
                return true;
            }
        }

        return false;
    }

    private static @Nullable BlockPos canonicalStoragePos(Level level, BlockPos pos) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity == null || blockEntity instanceof PleaseStoreControllerBlockEntity) {
            return null;
        }

        if (blockEntity instanceof ChestBlockEntity chest) {
            BlockState state = chest.getBlockState();
            if (!(state.getBlock() instanceof ChestBlock) || state.getValue(ChestBlock.TYPE) == ChestType.SINGLE) {
                return pos;
            }

            BlockPos connectedPos = pos.relative(ChestBlock.getConnectedDirection(state));
            return pos.asLong() <= connectedPos.asLong() ? pos : connectedPos;
        }

        return getStorageInventory(level, pos) != null ? pos : null;
    }

    private static List<BlockPos> getRelatedStoragePositions(Level level, BlockPos pos) {
        List<BlockPos> positions = new ArrayList<>();
        positions.add(pos);

        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof ChestBlockEntity chest) {
            BlockState state = chest.getBlockState();
            if (state.getBlock() instanceof ChestBlock && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
                positions.add(pos.relative(ChestBlock.getConnectedDirection(state)));
            }
        }

        return positions;
    }

    private static List<ItemStack> collectFilterItems(Level level, List<BlockPos> storagePositions) {
        Set<Long> storagePosKeys = new HashSet<>();
        for (BlockPos storagePos : storagePositions) {
            storagePosKeys.add(storagePos.asLong());
        }

        BlockPos min = storagePositions.get(0);
        BlockPos max = storagePositions.get(0);
        for (BlockPos storagePos : storagePositions) {
            min = new BlockPos(Math.min(min.getX(), storagePos.getX()), Math.min(min.getY(), storagePos.getY()), Math.min(min.getZ(), storagePos.getZ()));
            max = new BlockPos(Math.max(max.getX(), storagePos.getX()), Math.max(max.getY(), storagePos.getY()), Math.max(max.getZ(), storagePos.getZ()));
        }

        AABB frameSearchBox = new AABB(min).minmax(new AABB(max)).inflate(1.5D);
        List<ItemStack> filterItems = new ArrayList<>();
        for (ItemFrame itemFrame : level.getEntitiesOfClass(ItemFrame.class, frameSearchBox)) {
            ItemStack framedItem = itemFrame.getItem();
            if (framedItem.isEmpty()) {
                continue;
            }

            BlockPos attachedPos = itemFrame.getPos().relative(itemFrame.getDirection().getOpposite());
            if (storagePosKeys.contains(attachedPos.asLong())) {
                filterItems.add(framedItem.copyWithCount(1));
            }
        }

        return filterItems;
    }

    private static @Nullable IItemHandler getStorageInventory(Level level, BlockPos pos) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity == null) {
            return null;
        }

        if (blockEntity instanceof ChestBlockEntity chest) {
            BlockState state = chest.getBlockState();
            if (state.getBlock() instanceof ChestBlock && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
                BlockPos connectedPos = pos.relative(ChestBlock.getConnectedDirection(state));
                BlockEntity connectedEntity = level.getBlockEntity(connectedPos);
                if (connectedEntity instanceof ChestBlockEntity connectedChest) {
                    return new CombinedInvWrapper(new InvWrapper(chest), new InvWrapper(connectedChest));
                }
            }
        }

        if (blockEntity instanceof WorldlyContainer worldlyContainer) {
            for (Direction direction : Direction.values()) {
                IItemHandler sidedHandler = new SidedInvWrapper(worldlyContainer, direction);
                if (sidedHandler.getSlots() > 0) {
                    return sidedHandler;
                }
            }
        }

        if (blockEntity instanceof Container container) {
            return new InvWrapper(container);
        }

        return getReflectiveItemHandler(blockEntity);
    }

    private static @Nullable IItemHandler getReflectiveItemHandler(BlockEntity blockEntity) {
        Object capability = getItemHandlerCapabilityObject();
        if (capability == null) {
            return null;
        }

        for (java.lang.reflect.Method method : blockEntity.getClass().getMethods()) {
            if (!method.getName().equals("getCapability")) {
                continue;
            }

            try {
                if (method.getParameterCount() == 1) {
                    Object result = method.invoke(blockEntity, capability);
                    if (result instanceof IItemHandler itemHandler) {
                        return itemHandler;
                    }
                }

                if (method.getParameterCount() == 2) {
                    for (Direction direction : Direction.values()) {
                        Object result = method.invoke(blockEntity, capability, direction);
                        if (result instanceof IItemHandler itemHandler) {
                            return itemHandler;
                        }
                    }
                }
            } catch (ReflectiveOperationException ignored) {
                return null;
            }
        }

        return null;
    }

    private static @Nullable Object getItemHandlerCapabilityObject() {
        try {
            Class<?> capabilitiesClass = Class.forName(ITEM_HANDLER_CAPABILITIES_CLASS);
            return capabilitiesClass.getField(ITEM_HANDLER_FIELD).get(null);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }
}
