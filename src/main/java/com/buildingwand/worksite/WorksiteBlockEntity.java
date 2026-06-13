package com.buildingwand.worksite;

import com.buildingwand.ModItems;
import com.buildingwand.item.BuildingWandItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.BaseTorchBlock;
import net.minecraft.world.level.block.CactusBlock;
import net.minecraft.world.level.block.CeilingHangingSignBlock;
import net.minecraft.world.level.block.CoralPlantBlock;
import net.minecraft.world.level.block.CoralWallFanBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.HangingSignBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.world.level.block.TallFlowerBlock;
import net.minecraft.world.level.block.TallGrassBlock;
import net.minecraft.world.level.block.TorchflowerCropBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.WallHangingSignBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

/**
 * Plain-Java worksite data holder (not a BlockEntity).
 * All rotation/mirror is pre-applied in the constructor so the tick loop is simple.
 * Stored in {@link WorksiteManager} keyed by the post's BlockPos.
 */
public class WorksiteBlockEntity {

    public static final int MAX_GHOST = 2000;

    // Pre-transformed: offsets are in rotated/mirrored space, states are pre-rotated.
    private final List<Map.Entry<BlockPos, BlockState>> buildEntries;
    private final Set<Long> buildOffsets = new HashSet<>();
    private final Set<Long> completedOffsets = new HashSet<>();
    private final List<BlockPos> decorationOffsets = new ArrayList<>();
    private final Map<String, Integer> needed   = new LinkedHashMap<>();
    private final Map<String, Integer> deposited = new LinkedHashMap<>();
    private boolean building   = false;
    private int     buildIndex = 0;
    private final ResourceKey<Level> dimension;
    private final BlockPos buildOriginFromPost;

    public WorksiteBlockEntity(ListTag clipboard, int rot, int mir,
                               Level level, Player creator) {
        this(clipboard, rot, mir, level, creator, BlockPos.ZERO);
    }

    public WorksiteBlockEntity(ListTag clipboard, int rot, int mir,
                               Level level, Player creator, BlockPos buildOriginFromPost) {
        this(clipboard, rot, mir, level, creator, buildOriginFromPost, null);
    }

    public WorksiteBlockEntity(ListTag clipboard, int rot, int mir,
                               Level level, Player creator, BlockPos buildOriginFromPost, BlockPos postPos) {
        this.dimension           = level.dimension();
        this.buildOriginFromPost = buildOriginFromPost;
        this.buildEntries        = computeBuildEntries(clipboard, rot, mir, level);
        for (var entry : buildEntries) buildOffsets.add(entry.getKey().asLong());
        if (level instanceof ServerLevel serverLevel && postPos != null) {
            computeNeeded(serverLevel, postPos);
            depositFromPlayer(creator, creator.isCreative(), serverLevel, postPos);
        } else {
            computeNeeded();
            depositFromPlayer(creator, creator.isCreative());
        }
    }

    private WorksiteBlockEntity(List<Map.Entry<BlockPos, BlockState>> buildEntries,
                                Set<Long> completedOffsets,
                                List<BlockPos> decorationOffsets,
                                Map<String, Integer> needed,
                                Map<String, Integer> deposited,
                                boolean building,
                                int buildIndex,
                                ResourceKey<Level> dimension,
                                BlockPos buildOriginFromPost) {
        this.buildEntries = buildEntries;
        for (var entry : buildEntries) this.buildOffsets.add(entry.getKey().asLong());
        this.completedOffsets.addAll(completedOffsets);
        this.decorationOffsets.addAll(decorationOffsets);
        this.needed.putAll(needed);
        this.deposited.putAll(deposited);
        this.building = building;
        this.buildIndex = buildIndex;
        this.dimension = dimension;
        this.buildOriginFromPost = buildOriginFromPost;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("dimension", dimension.identifier().toString());
        tag.putInt("originX", buildOriginFromPost.getX());
        tag.putInt("originY", buildOriginFromPost.getY());
        tag.putInt("originZ", buildOriginFromPost.getZ());
        tag.putBoolean("building", building);
        tag.putInt("buildIndex", buildIndex);

        ListTag entries = new ListTag();
        for (var entry : buildEntries) {
            CompoundTag e = new CompoundTag();
            BlockPos off = entry.getKey();
            e.putInt("x", off.getX());
            e.putInt("y", off.getY());
            e.putInt("z", off.getZ());
            e.put("state", NbtUtils.writeBlockState(entry.getValue()));
            entries.add(e);
        }
        tag.put("entries", entries);

        long[] completed = completedOffsets.stream().mapToLong(Long::longValue).toArray();
        tag.putLongArray("completed", completed);

        ListTag decor = new ListTag();
        for (BlockPos off : decorationOffsets) {
            CompoundTag e = new CompoundTag();
            e.putInt("x", off.getX());
            e.putInt("y", off.getY());
            e.putInt("z", off.getZ());
            decor.add(e);
        }
        tag.put("decor", decor);
        tag.put("needed", saveIntMap(needed));
        tag.put("deposited", saveIntMap(deposited));
        return tag;
    }

    public static WorksiteBlockEntity load(CompoundTag tag) {
        ResourceKey<Level> dimension = ResourceKey.create(
                Registries.DIMENSION,
                Identifier.parse(tag.getStringOr("dimension", "minecraft:overworld")));
        BlockPos origin = new BlockPos(
                tag.getIntOr("originX", 0),
                tag.getIntOr("originY", 0),
                tag.getIntOr("originZ", 0));

        List<Map.Entry<BlockPos, BlockState>> entries = new ArrayList<>();
        ListTag entryTags = tag.getListOrEmpty("entries");
        for (int i = 0; i < entryTags.size(); i++) {
            CompoundTag e = entryTags.getCompoundOrEmpty(i);
            BlockPos off = new BlockPos(e.getIntOr("x", 0), e.getIntOr("y", 0), e.getIntOr("z", 0));
            BlockState state = NbtUtils.readBlockState(BuiltInRegistries.BLOCK, e.getCompoundOrEmpty("state"));
            entries.add(Map.entry(off, state));
        }

        Set<Long> completed = new HashSet<>();
        for (long v : tag.getLongArray("completed").orElse(new long[0])) completed.add(v);

        List<BlockPos> decor = new ArrayList<>();
        ListTag decorTags = tag.getListOrEmpty("decor");
        for (int i = 0; i < decorTags.size(); i++) {
            CompoundTag e = decorTags.getCompoundOrEmpty(i);
            decor.add(new BlockPos(e.getIntOr("x", 0), e.getIntOr("y", 0), e.getIntOr("z", 0)));
        }

        return new WorksiteBlockEntity(
                entries,
                completed,
                decor,
                loadIntMap(tag.getListOrEmpty("needed")),
                loadIntMap(tag.getListOrEmpty("deposited")),
                tag.getBooleanOr("building", false),
                tag.getIntOr("buildIndex", 0),
                dimension,
                origin);
    }

    private static ListTag saveIntMap(Map<String, Integer> map) {
        ListTag list = new ListTag();
        for (Map.Entry<String, Integer> entry : map.entrySet()) {
            CompoundTag e = new CompoundTag();
            e.putString("id", entry.getKey());
            e.putInt("count", entry.getValue());
            list.add(e);
        }
        return list;
    }

    private static Map<String, Integer> loadIntMap(ListTag list) {
        Map<String, Integer> map = new LinkedHashMap<>();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag e = list.getCompoundOrEmpty(i);
            String id = e.getStringOr("id", "");
            int count = e.getIntOr("count", 0);
            if (!id.isEmpty() && count > 0) map.put(id, count);
        }
        return map;
    }

    // 鈹€鈹€ Initialisation 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€

    private static List<Map.Entry<BlockPos, BlockState>> computeBuildEntries(
            ListTag clip, int rot, int mir, Level level) {
        Rotation rotation = BuildingWandItem.ROTATIONS[Math.floorMod(rot, 4)];
        Mirror   mirror   = BuildingWandItem.MC_MIRRORS[Math.floorMod(mir, 4)];
        var blockLookup   = level.registryAccess().lookupOrThrow(Registries.BLOCK);

        List<Map.Entry<BlockPos, BlockState>> entries = new ArrayList<>(clip.size());
        for (int i = 0; i < clip.size(); i++) {
            CompoundTag e  = clip.getCompoundOrEmpty(i);
            int dx = e.getShortOr("dx", (short) 0);
            int dy = e.getShortOr("dy", (short) 0);
            int dz = e.getShortOr("dz", (short) 0);
            BlockState bs  = NbtUtils.readBlockState(blockLookup, e.getCompoundOrEmpty("s"));
            BlockPos transformedOff = BuildingWandItem.transformOffset(dx, dy, dz, rotation, mir);
            entries.add(Map.entry(transformedOff, bs.mirror(mirror).rotate(rotation)));
        }
        // Normal blocks build bottom-up; only strongly dependent blocks are delayed.
        entries.sort(Comparator.comparingInt((Map.Entry<BlockPos, BlockState> en) ->
            buildOrderPriority(en.getValue())
        ).thenComparingInt(en -> en.getKey().getY()));
        return entries;
    }

    private static int buildOrderPriority(BlockState state) {
        if (state.is(Blocks.TNT)) return 3;
        if (isSourceFluid(state)) return 2;
        if (isDependentBlock(state)) return 1;
        return 0;
    }

    private void sortBuildEntries() {
        buildEntries.sort(Comparator.comparingInt((Map.Entry<BlockPos, BlockState> en) ->
                buildOrderPriority(en.getValue())
        ).thenComparingInt(en -> en.getKey().getY()));
    }

    private static boolean isDependentBlock(BlockState state) {
        Block block = state.getBlock();
        return block instanceof BaseTorchBlock
                || block instanceof WallTorchBlock
                || block instanceof LadderBlock
                || block instanceof WallSignBlock
                || block instanceof HangingSignBlock
                || block instanceof CeilingHangingSignBlock
                || block instanceof WallHangingSignBlock
                || block instanceof SugarCaneBlock
                || block instanceof CactusBlock
                || block instanceof CoralPlantBlock
                || block instanceof CoralWallFanBlock
                || block instanceof TorchflowerCropBlock
                || block instanceof FlowerPotBlock;
    }

    private void computeNeeded() {
        needed.clear();
        for (var e : buildEntries) {
            BlockState bs = e.getValue();
            // Skip flowing water/lava; they appear naturally from placed source blocks.
            if (isFlowingFluid(bs)) continue;
            for (String key : depositKeys(bs)) needed.merge(key, 1, Integer::sum);
        }
    }

    private void computeNeeded(ServerLevel level, BlockPos postPos) {
        needed.clear();
        BlockPos origin = buildOrigin(postPos);
        for (var e : buildEntries) {
            BlockState bs = e.getValue();
            if (isFlowingFluid(bs)) continue;
            BlockPos target = origin.offset(e.getKey());
            if (!canPlaceAt(bs, level.getBlockState(target))) continue;
            for (String key : depositKeys(bs)) needed.merge(key, 1, Integer::sum);
        }
    }

    // 鈹€鈹€ Material management 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€

    public void depositFromPlayer(Player player, boolean creative) {
        for (Map.Entry<String, Integer> req : needed.entrySet()) {
            String id       = req.getKey();
            int totalNeeded = req.getValue();
            int alreadyHave = deposited.getOrDefault(id, 0);
            int moreNeeded  = totalNeeded - alreadyHave;
            if (moreNeeded <= 0) continue;
            if (creative) { deposited.put(id, totalNeeded); continue; }

            // Water/lava require buckets; the empty bucket is returned immediately on deposit.
            if ("minecraft:water_bucket".equals(id) || "minecraft:lava_bucket".equals(id)) {
                int taken = 0;
                while (taken < moreNeeded && consumeBucketFromPlayer(player, id)) taken++;
                if (taken > 0) deposited.merge(id, taken, Integer::sum);
                continue;
            }

            BlockState ref = stateForId(id);
            if (ref == null) continue;
            int taken = 0;
            while (taken < moreNeeded && BuildingWandItem.consumeBlock(player, ref)) taken++;
            if (taken > 0) deposited.merge(id, taken, Integer::sum);
        }
    }

    public void depositFromPlayer(Player player, boolean creative, ServerLevel level, BlockPos postPos) {
        computeNeeded(level, postPos);
        depositFromPlayer(player, creative);
        resetBuildCursor();
    }

    /**
     * @return true if building started, false if materials are insufficient.
     */
    public boolean startBuilding(Player player, ServerLevel level, BlockPos postPos) {
        if (building) {
            resetBuildCursor();
            return true;
        }
        computeNeeded(level, postPos);
        if (player.isCreative()) depositFromPlayer(player, true);
        cleanupDecoration(level, postPos);
        building   = true;
        resetBuildCursor();
        return true;
    }

    private void resetBuildCursor() {
        buildIndex = 0;
    }

    public void refreshDecoration(ServerLevel level, BlockPos postPos) {
        cleanupDecoration(level, postPos);
        if (buildEntries.isEmpty()) return;

        BlockPos origin = buildOrigin(postPos);
        Bounds bounds = bounds();
        addFrame(level, postPos, origin, bounds);
        addMaterialPiles(level, postPos, origin, bounds);
    }

    public void cleanup(ServerLevel level, BlockPos postPos) {
        cleanupDecoration(level, postPos);
    }

    public void cancel(ServerLevel level, BlockPos postPos) {
        cleanupDecoration(level, postPos);
        dropDeposited(level, postPos);
        deposited.clear();
    }

    public boolean replaceMaterial(ServerLevel level, BlockPos postPos, String sourceId, BlockState replacement) {
        if (building || sourceId == null || sourceId.isBlank() || replacement.isAir()) return false;

        boolean changed = false;
        for (int i = 0; i < buildEntries.size(); i++) {
            Map.Entry<BlockPos, BlockState> entry = buildEntries.get(i);
            BlockState oldState = entry.getValue();
            if (!usesDepositId(oldState, sourceId)) continue;
            BlockState newState = oldState.getBlock() == replacement.getBlock()
                    ? inheritSharedProperties(oldState, replacement)
                    : replacement;
            buildEntries.set(i, Map.entry(entry.getKey(), newState));
            changed = true;
        }

        if (!changed) return false;

        sortBuildEntries();
        refundDepositId(level, postPos, sourceId);
        computeNeeded(level, postPos);
        resetBuildCursor();
        return true;
    }

    private void dropDeposited(ServerLevel level, BlockPos postPos) {
        for (Map.Entry<String, Integer> entry : new ArrayList<>(deposited.entrySet())) {
            int count = entry.getValue();
            if (count <= 0) continue;
            Item item = itemForDepositId(entry.getKey());
            if (item == null || item == Items.AIR) continue;
            while (count > 0) {
                int n = Math.min(count, item.getDefaultMaxStackSize());
                Block.popResource(level, postPos, new ItemStack(item, n));
                count -= n;
            }
        }
    }

    private void refundDepositId(ServerLevel level, BlockPos postPos, String id) {
        int count = deposited.getOrDefault(id, 0);
        if (count <= 0) return;
        Item item = itemForDepositId(id);
        if (item != null && item != Items.AIR) {
            int left = count;
            while (left > 0) {
                int n = Math.min(left, item.getDefaultMaxStackSize());
                Block.popResource(level, postPos, new ItemStack(item, n));
                left -= n;
            }
        }
        deposited.remove(id);
    }

    private void cleanupDecoration(ServerLevel level, BlockPos postPos) {
        for (BlockPos off : decorationOffsets) {
            BlockPos pos = postPos.offset(off);
            BlockState state = level.getBlockState(pos);
            if (isDecoration(state)) {
                level.removeBlock(pos, false);
            }
        }
        decorationOffsets.clear();
    }

    // 鈹€鈹€ Tick: place one block per call 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€

    /**
     * @param origin The world position the clipboard offsets are relative to.
     * @return true when all blocks have been placed.
     */
    public boolean placeNextBlock(ServerLevel level, BlockPos origin) {
        origin = buildOrigin(origin);
        if (buildEntries.isEmpty() || remainingNeeded() <= 0) return true;

        int checked = 0;
        while (checked < buildEntries.size()) {
            int index = buildIndex;
            buildIndex = (buildIndex + 1) % buildEntries.size();
            checked++;

            var entry = buildEntries.get(index);
            BlockPos off = entry.getKey();
            if (completedOffsets.contains(off.asLong())) continue;

            BlockPos target = origin.offset(off);
            BlockState bs = entry.getValue();

            if (isFlowingFluid(bs)) {
                completedOffsets.add(off.asLong());
                continue;
            }

            List<String> depositIds = depositKeys(bs);
            if (depositIds.isEmpty()) {
                completedOffsets.add(off.asLong());
                continue;
            }
            if (!hasDeposited(depositIds)) continue;

            if (!canPlaceAt(bs, level.getBlockState(target))) {
                decrementNeeded(depositIds);
                completedOffsets.add(off.asLong());
                continue;
            }

            consumeDeposited(depositIds);
            decrementNeeded(depositIds);
            completedOffsets.add(off.asLong());
            level.setBlock(target, bs, BuildingWandItem.PLACE_FLAGS);
            level.sendParticles(ParticleTypes.CRIT,
                    target.getX() + 0.5, target.getY() + 1.0, target.getZ() + 0.5,
                    4, 0.3, 0.3, 0.3, 0.05);
            return remainingNeeded() <= 0;
        }
        return remainingNeeded() <= 0;
    }
    public int blocksPerSecond() {
        return Math.max(5, (int)Math.ceil(buildEntries.size() / 60.0));
    }

    /**
     * Run a single physics pass over every placed block once the build is complete. Blocks were
     * laid down silently (no neighbor updates), so this is where water flows, fences/redstone
     * connect and supported blocks settle — without anything having been washed away mid-build.
     */
    public void settle(ServerLevel level, BlockPos postPos) {
        BlockPos origin = buildOrigin(postPos);
        List<BlockPos> positions = new ArrayList<>(buildEntries.size());
        for (var e : buildEntries) positions.add(origin.offset(e.getKey()));
        BuildingWandItem.settlePlacedBlocks(level, positions);
    }

    private int remainingNeeded() {
        return needed.values().stream().mapToInt(Integer::intValue).sum();
    }

    private void decrementNeeded(String id) {
        int n = needed.getOrDefault(id, 0);
        if (n <= 1) needed.remove(id);
        else needed.put(id, n - 1);
    }

    private void decrementNeeded(List<String> ids) {
        for (String id : ids) decrementNeeded(id);
    }

    private boolean hasDeposited(List<String> ids) {
        for (String id : ids) {
            if (deposited.getOrDefault(id, 0) <= 0) return false;
        }
        return true;
    }

    private void consumeDeposited(List<String> ids) {
        for (String id : ids) {
            int n = deposited.getOrDefault(id, 0);
            if (n <= 1) deposited.remove(id);
            else deposited.put(id, n - 1);
        }
    }

    // 鈹€鈹€ Ghost entries for client rendering 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€

    /** World-space ghost block entries, capped to MAX_GHOST. */
    public List<Map.Entry<BlockPos, BlockState>> getGhostEntries(BlockPos origin) {
        origin = buildOrigin(origin);
        int limit = Math.min(buildEntries.size(), MAX_GHOST);
        List<Map.Entry<BlockPos, BlockState>> out = new ArrayList<>(limit);
        for (int i = 0; i < limit; i++) {
            var e = buildEntries.get(i);
            out.add(Map.entry(origin.offset(e.getKey()), e.getValue()));
        }
        return out;
    }

    // 鈹€鈹€ Helpers 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€

    /** True only for source water or source lava block states (not waterlogged solids). */
    private static boolean isSourceFluid(BlockState bs) {
        if (!bs.getFluidState().isSource()) return false;
        String id = blockId(bs);
        return "minecraft:water".equals(id) || "minecraft:lava".equals(id);
    }

    /** True for flowing (non-source) water or lava. */
    private static boolean isFlowingFluid(BlockState bs) {
        if (bs.getFluidState().isEmpty() || bs.getFluidState().isSource()) return false;
        String id = blockId(bs);
        return "minecraft:water".equals(id) || "minecraft:lava".equals(id);
    }

    /**
     * Returns the deposited-map key for a block state.
     * Source water 鈫?"minecraft:water_bucket", source lava 鈫?"minecraft:lava_bucket".
     */
    private static String depositKey(BlockState bs) {
        if (isSourceFluid(bs)) {
            String id = blockId(bs);
            return "minecraft:water".equals(id) ? "minecraft:water_bucket" : "minecraft:lava_bucket";
        }
        return blockId(bs);
    }

    private static List<String> depositKeys(BlockState bs) {
        if (bs.getBlock() instanceof FlowerPotBlock pot && pot.getPotted() != Blocks.AIR) {
            String potId = itemId(Blocks.FLOWER_POT.asItem());
            String plantId = itemId(pot.getPotted().asItem());
            if (potId == null || plantId == null || "minecraft:air".equals(plantId)) return List.of();
            return List.of(potId, plantId);
        }
        String key = depositKey(bs);
        return key == null ? List.of() : List.of(key);
    }

    private static boolean usesDepositId(BlockState state, String id) {
        for (String key : depositKeys(state)) {
            if (id.equals(key)) return true;
        }
        return false;
    }

    private static BlockState inheritSharedProperties(BlockState oldState, BlockState newState) {
        BlockState result = newState;
        for (var oldProp : oldState.getProperties()) {
            var newProp = result.getBlock().getStateDefinition().getProperty(oldProp.getName());
            if (newProp != null) result = copyPropertyIfCompatible(oldState, result, oldProp, newProp);
        }
        return result;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static BlockState copyPropertyIfCompatible(BlockState oldState, BlockState newState,
                                                       net.minecraft.world.level.block.state.properties.Property oldProp,
                                                       net.minecraft.world.level.block.state.properties.Property newProp) {
        Comparable value = oldState.getValue(oldProp);
        if (!newProp.getPossibleValues().contains(value)) return newState;
        return newState.setValue(newProp, value);
    }

    /**
     * Removes one bucket item from the player's inventory and returns an empty bucket.
     */
    private static boolean consumeBucketFromPlayer(Player player, String bucketId) {
        Item target;
        try {
            target = BuiltInRegistries.ITEM.get(Identifier.parse(bucketId))
                    .map(ref -> ref.value()).orElse(null);
        } catch (Exception ex) { return false; }
        if (target == null || target == Items.AIR) return false;

        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty() && stack.getItem() == target) {
                stack.shrink(1);
                ItemStack emptyBucket = new ItemStack(Items.BUCKET);
                if (!inv.add(emptyBucket)) player.drop(emptyBucket, false);
                return true;
            }
        }
        return false;
    }

    private static String blockId(BlockState state) {
        var key = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return (key != null) ? key.toString() : null;
    }

    private static String itemId(Item item) {
        var key = BuiltInRegistries.ITEM.getKey(item);
        return (key != null) ? key.toString() : null;
    }

    private static BlockState stateForId(String id) {
        try {
            return BuiltInRegistries.BLOCK.get(Identifier.parse(id))
                    .map(ref -> ref.value().defaultBlockState())
                    .orElse(null);
        } catch (Exception ex) {
            return null;
        }
    }

    private static Item itemForDepositId(String id) {
        try {
            return BuiltInRegistries.ITEM.get(Identifier.parse(id))
                    .map(ref -> ref.value()).orElse(null);
        } catch (Exception ex) {
            return null;
        }
    }

    public ResourceKey<Level>   getDimension() { return dimension; }
    public Map<String, Integer> getNeeded()    { return Collections.unmodifiableMap(needed); }
    public Map<String, Integer> getDeposited() { return Collections.unmodifiableMap(deposited); }
    public boolean              isBuilding()   { return building; }

    private BlockPos buildOrigin(BlockPos postPos) {
        return postPos.offset(buildOriginFromPost);
    }

    private Bounds bounds() {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (var entry : buildEntries) {
            BlockPos p = entry.getKey();
            minX = Math.min(minX, p.getX()); minY = Math.min(minY, p.getY()); minZ = Math.min(minZ, p.getZ());
            maxX = Math.max(maxX, p.getX()); maxY = Math.max(maxY, p.getY()); maxZ = Math.max(maxZ, p.getZ());
        }
        return new Bounds(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private void addFrame(ServerLevel level, BlockPos postPos, BlockPos origin, Bounds b) {
        BlockState fence = ModItems.WORKSITE_FRAME.defaultBlockState();
        int bottom = b.minY();
        int top = b.maxY() + 1;
        for (int x = b.minX(); x <= b.maxX(); x++) {
            tryDecor(level, postPos, origin, new BlockPos(x, bottom, b.minZ()), fence);
            tryDecor(level, postPos, origin, new BlockPos(x, bottom, b.maxZ()), fence);
            tryDecor(level, postPos, origin, new BlockPos(x, top, b.minZ()), fence);
            tryDecor(level, postPos, origin, new BlockPos(x, top, b.maxZ()), fence);
        }
        for (int z = b.minZ(); z <= b.maxZ(); z++) {
            tryDecor(level, postPos, origin, new BlockPos(b.minX(), bottom, z), fence);
            tryDecor(level, postPos, origin, new BlockPos(b.maxX(), bottom, z), fence);
            tryDecor(level, postPos, origin, new BlockPos(b.minX(), top, z), fence);
            tryDecor(level, postPos, origin, new BlockPos(b.maxX(), top, z), fence);
        }
        for (int y = bottom; y <= top; y++) {
            tryDecor(level, postPos, origin, new BlockPos(b.minX(), y, b.minZ()), fence);
            tryDecor(level, postPos, origin, new BlockPos(b.minX(), y, b.maxZ()), fence);
            tryDecor(level, postPos, origin, new BlockPos(b.maxX(), y, b.minZ()), fence);
            tryDecor(level, postPos, origin, new BlockPos(b.maxX(), y, b.maxZ()), fence);
        }
    }

    private void addMaterialPiles(ServerLevel level, BlockPos postPos, BlockPos origin, Bounds b) {
        int depositedCount = deposited.values().stream().mapToInt(Integer::intValue).sum();
        if (depositedCount <= 0) return;

        int cx = (b.minX() + b.maxX()) / 2;
        int cz = (b.minZ() + b.maxZ()) / 2;
        int y = b.minY();
        BlockState pile = ModItems.WORKSITE_MATERIAL_PILE.defaultBlockState();
        tryDecor(level, postPos, origin, new BlockPos(cx, y, cz), pile);
        tryDecor(level, postPos, origin, new BlockPos(cx + 1, y, cz), pile);
        tryDecor(level, postPos, origin, new BlockPos(cx - 1, y, cz), pile);
        tryDecor(level, postPos, origin, new BlockPos(cx, y, cz + 1), pile);
        tryDecor(level, postPos, origin, new BlockPos(cx, y, cz - 1), pile);
        if (depositedCount >= 16) tryDecor(level, postPos, origin, new BlockPos(cx, y + 1, cz), pile);
    }

    private boolean tryDecor(ServerLevel level, BlockPos postPos, BlockPos origin, BlockPos buildOffset, BlockState state) {
        BlockPos worldPos = origin.offset(buildOffset);
        if (worldPos.equals(postPos)) return false;
        if (!level.getBlockState(worldPos).isAir()) return false;
        level.setBlock(worldPos, state, 3);
        decorationOffsets.add(worldPos.subtract(postPos));
        return true;
    }

    private boolean isDecoration(BlockState state) {
        return state.is(ModItems.WORKSITE_FRAME) || state.is(ModItems.WORKSITE_MATERIAL_PILE);
    }

    private boolean canPlaceAt(BlockState planned, BlockState existing) {
        if (existing.isAir() || existing.getBlock() instanceof WorksiteBlock || isDecoration(existing)) return true;
        return isSourceFluid(planned) && !existing.getFluidState().isEmpty();
    }

    private record Bounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {}
}

