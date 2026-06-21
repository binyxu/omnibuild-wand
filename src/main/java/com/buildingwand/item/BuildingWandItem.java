package com.buildingwand.item;

import com.buildingwand.BuildingWandConfig;
import com.buildingwand.ModItems;
import com.buildingwand.network.WandClipPayload;
import com.buildingwand.worksite.WorksiteBlock;
import com.buildingwand.worksite.WorksiteBlockEntity;
import com.buildingwand.worksite.WorksiteManager;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

public class BuildingWandItem extends Item {

    public static final String K_MODE = "m";
    public static final String K_STEP = "st";
    public static final String K_P1X = "x1";
    public static final String K_P1Y = "y1";
    public static final String K_P1Z = "z1";
    public static final String K_P2X = "x2";
    public static final String K_P2Y = "y2";
    public static final String K_P2Z = "z2";
    public static final String K_CLIP = "cl";
    public static final String K_ROT = "r";
    public static final String K_MIR = "mi";
    public static final String K_SEL = "sel";
    public static final String K_SMART = "smart";
    public static final String K_SCHEMATIC = "sch";
    public static final String K_OX = "ox";
    public static final String K_OY = "oy";
    public static final String K_OZ = "oz";

    public static final int MAX_BLOCKS = 10_000;
    // Supply-link region scan limits (one-time scan at bind time).
    private static final int MAX_SUPPLY_SCAN = 200_000;
    private static final int MAX_SUPPLY_CONTAINERS = 512;
    private static final int MAX_SMART_SCAN = 40_000;
    private static final int MAX_PERSISTED_CLIP_BLOCKS = 1500;
    private static final int MAX_PERSISTED_SMART_BLOCKS = 512;
    // Used when carving space during paste/move. Suppress drops and skip neighbor side-effects
    // so attached/multiblock structures don't duplicate items while being relocated.
    private static final int CLEAR_FLAGS =
            Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS | Block.UPDATE_SKIP_ALL_SIDEEFFECTS;
    // Silent placement: sync to client but fire NO neighbor/physics updates. Water won't flow,
    // sand won't fall, attached blocks won't pop off and TNT won't arm while the structure is
    // still being laid down. updateNeighborsAt/physics is run once at the end via settlePlacedBlocks.
    public static final int PLACE_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    private static final Map<UUID, ListTag> SERVER_CLIPBOARDS = new HashMap<>();
    private static final Map<UUID, List<BlockPos>> SERVER_SMART_SELECTIONS = new HashMap<>();
    private static final Set<Block> IMMOVABLE_BLOCKS = Set.of(
            Blocks.BEDROCK,
            Blocks.END_PORTAL_FRAME,
            Blocks.END_PORTAL,
            Blocks.NETHER_PORTAL
    );

    public static final Rotation[] ROTATIONS = {
            Rotation.NONE, Rotation.CLOCKWISE_90, Rotation.CLOCKWISE_180, Rotation.COUNTERCLOCKWISE_90
    };
    public static final String[] ROT_NAMES = {"0°", "90°", "180°", "270°"};
    public static final String[] MIR_NAMES = {"mirror.buildingwand.none", "mirror.buildingwand.lr", "mirror.buildingwand.fb", "mirror.buildingwand.ud"};
    public static final Mirror[] MC_MIRRORS = {Mirror.NONE, Mirror.FRONT_BACK, Mirror.LEFT_RIGHT, Mirror.NONE};

    public BuildingWandItem(Properties props) {
        super(props);
    }

    public static CompoundTag getTag(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }

    public static void saveTag(ItemStack stack, CompoundTag tag) {
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    public static int getMode(ItemStack stack) {
        return getTag(stack).getIntOr(K_MODE, 0);
    }

    public static int getStep(ItemStack stack) {
        return getTag(stack).getIntOr(K_STEP, 0);
    }

    public static int getRot(ItemStack stack) {
        return getTag(stack).getIntOr(K_ROT, 0);
    }

    public static int getMir(ItemStack stack) {
        return getTag(stack).getIntOr(K_MIR, 0);
    }

    public static int getSel(ItemStack stack) {
        return getTag(stack).getIntOr(K_SEL, 0);
    }

    private static ListTag getSmartSelection(ItemStack stack) {
        return getTag(stack).getListOrEmpty(K_SMART);
    }

    private static BlockPos getPos1(ItemStack stack) {
        CompoundTag tag = getTag(stack);
        return new BlockPos(tag.getIntOr(K_P1X, 0), tag.getIntOr(K_P1Y, 0), tag.getIntOr(K_P1Z, 0));
    }

    private static void setPos1(ItemStack stack, BlockPos pos) {
        CompoundTag tag = getTag(stack);
        tag.putInt(K_P1X, pos.getX());
        tag.putInt(K_P1Y, pos.getY());
        tag.putInt(K_P1Z, pos.getZ());
        saveTag(stack, tag);
    }

    private static BlockPos getPos2(ItemStack stack) {
        CompoundTag tag = getTag(stack);
        return new BlockPos(tag.getIntOr(K_P2X, 0), tag.getIntOr(K_P2Y, 0), tag.getIntOr(K_P2Z, 0));
    }

    private static void setPos2(ItemStack stack, BlockPos pos) {
        CompoundTag tag = getTag(stack);
        tag.putInt(K_P2X, pos.getX());
        tag.putInt(K_P2Y, pos.getY());
        tag.putInt(K_P2Z, pos.getZ());
        saveTag(stack, tag);
    }

    public static void setField(ItemStack stack, String key, int value) {
        CompoundTag tag = getTag(stack);
        tag.putInt(key, value);
        saveTag(stack, tag);
    }

    private static BlockPos getCopyOrigin(ItemStack stack) {
        CompoundTag tag = getTag(stack);
        return new BlockPos(tag.getIntOr(K_OX, 0), tag.getIntOr(K_OY, 0), tag.getIntOr(K_OZ, 0));
    }

    private static void setCopyOrigin(ItemStack stack, BlockPos pos) {
        CompoundTag tag = getTag(stack);
        tag.putInt(K_OX, pos.getX());
        tag.putInt(K_OY, pos.getY());
        tag.putInt(K_OZ, pos.getZ());
        saveTag(stack, tag);
    }

    public static void setServerClipboard(Player player, ListTag clip) {
        SERVER_CLIPBOARDS.put(player.getUUID(), clip.copy());
    }

    /**
     * Large clips aren't persisted to the wand NBT (that would blow the 2 MB
     * inventory-sync limit), so the client has no clip data for the ghost
     * preview. Push it once via a dedicated payload so copy/move previews work
     * for big builds just like small ones do from NBT.
     */
    private static void maybeSyncClipToClient(Player player, ListTag list) {
        if (player instanceof ServerPlayer sp && list.size() > MAX_PERSISTED_CLIP_BLOCKS) {
            ServerPlayNetworking.send(sp, new WandClipPayload(list.copy()));
        }
    }

    public static ListTag getClipboard(Player player, ItemStack wand) {
        ListTag cached = SERVER_CLIPBOARDS.get(player.getUUID());
        if (cached != null && !cached.isEmpty()) return cached;
        return getTag(wand).getListOrEmpty(K_CLIP);
    }

    private static void setServerSmartSelection(Player player, List<BlockPos> positions) {
        SERVER_SMART_SELECTIONS.put(player.getUUID(), List.copyOf(positions));
    }

    private static void clearServerSmartSelection(Player player) {
        SERVER_SMART_SELECTIONS.remove(player.getUUID());
    }

    private static void resetStep(ItemStack stack) {
        CompoundTag tag = getTag(stack);
        int mode = tag.getIntOr(K_MODE, 0);
        int sel = tag.getIntOr(K_SEL, 0);
        CompoundTag fresh = new CompoundTag();
        fresh.putInt(K_MODE, mode);
        fresh.putInt(K_SEL, sel);
        saveTag(stack, fresh);
    }

    private static void storeSmartSelection(Player player, ItemStack stack, SelectionData data) {
        CompoundTag tag = getTag(stack);
        setServerSmartSelection(player, data.positions());
        if (data.positions().size() <= MAX_PERSISTED_SMART_BLOCKS) {
            ListTag list = new ListTag();
            for (BlockPos pos : data.positions()) {
                CompoundTag e = new CompoundTag();
                e.putInt("x", pos.getX());
                e.putInt("y", pos.getY());
                e.putInt("z", pos.getZ());
                list.add(e);
            }
            tag.put(K_SMART, list);
        } else {
            tag.remove(K_SMART);
        }
        tag.putInt(K_P1X, data.min().getX());
        tag.putInt(K_P1Y, data.min().getY());
        tag.putInt(K_P1Z, data.min().getZ());
        tag.putInt(K_P2X, data.max().getX());
        tag.putInt(K_P2Y, data.max().getY());
        tag.putInt(K_P2Z, data.max().getZ());
        saveTag(stack, tag);
    }

    private static List<BlockPos> readSmartPositions(Player player, ItemStack stack) {
        List<BlockPos> cached = SERVER_SMART_SELECTIONS.get(player.getUUID());
        if (cached != null && !cached.isEmpty()) return cached;
        List<BlockPos> positions = new ArrayList<>();
        ListTag list = getSmartSelection(stack);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag e = list.getCompoundOrEmpty(i);
            positions.add(new BlockPos(e.getIntOr("x", 0), e.getIntOr("y", 0), e.getIntOr("z", 0)));
        }
        return positions;
    }

    public static ItemStack getHeldWand(Player player) {
        ItemStack main = player.getMainHandItem();
        if (main.getItem() instanceof BuildingWandItem) return main;
        ItemStack off = player.getOffhandItem();
        if (off.getItem() instanceof BuildingWandItem) return off;
        return null;
    }

    public static BlockPos transformOffset(int dx, int dy, int dz, Rotation rot, int mirInt) {
        int mx = dx;
        int my = dy;
        int mz = dz;
        if (mirInt == 1) mx = -dx;
        else if (mirInt == 2) mz = -dz;
        else if (mirInt == 3) my = -dy;
        return switch (rot) {
            case NONE -> new BlockPos(mx, my, mz);
            case CLOCKWISE_90 -> new BlockPos(-mz, my, mx);
            case CLOCKWISE_180 -> new BlockPos(-mx, my, -mz);
            case COUNTERCLOCKWISE_90 -> new BlockPos(mz, my, -mx);
        };
    }

    public static BlockPos placementTarget(BlockPos clicked, Direction face) {
        return clicked.relative(face);
    }

    public static void serverRotate(ItemStack wand, Player player) {
        int mode = getMode(wand);
        if ((mode != 1 && mode != 3) || getStep(wand) != 2) return;
        int newRot = (getRot(wand) + 1) % 4;
        setField(wand, K_ROT, newRot);
        player.sendSystemMessage(Component.translatable("message.buildingwand.rotate", ROT_NAMES[newRot]).withStyle(ChatFormatting.AQUA));
    }

    public static void serverMirror(ItemStack wand, Player player) {
        int mode = getMode(wand);
        if ((mode != 1 && mode != 3) || getStep(wand) != 2) return;
        int newMir = (getMir(wand) + 1) % 4;
        setField(wand, K_MIR, newMir);
        player.sendSystemMessage(Component.translatable("message.buildingwand.mirror", Component.translatable(MIR_NAMES[newMir])).withStyle(ChatFormatting.AQUA));
    }

    public static void serverToggleMode(ItemStack wand, Player player) {
        int nm = (getMode(wand) + 1) % 6;
        CompoundTag fresh = new CompoundTag();
        fresh.putInt(K_MODE, nm);
        fresh.putInt(K_SEL, getSel(wand));
        saveTag(wand, fresh);
        clearServerSmartSelection(player);
        player.sendSystemMessage((switch (nm) {
            case 0 -> Component.translatable("message.buildingwand.mode.fill").withStyle(ChatFormatting.GREEN);
            case 1 -> Component.translatable("message.buildingwand.mode.copy").withStyle(ChatFormatting.AQUA);
            case 2 -> Component.translatable("message.buildingwand.mode.replace").withStyle(ChatFormatting.LIGHT_PURPLE);
            case 3 -> Component.translatable("message.buildingwand.mode.move").withStyle(ChatFormatting.YELLOW);
            case 4 -> Component.translatable("message.buildingwand.mode.supply").withStyle(ChatFormatting.GOLD);
            default -> Component.translatable("message.buildingwand.mode.harvest").withStyle(ChatFormatting.RED);
        }));
    }

    public static void serverToggleSelectionMode(ItemStack wand, Player player) {
        int mode = getMode(wand);
        // Smart/box selection applies to fill, copy, replace, move and harvest — but not supply (4).
        if (mode < 0 || mode > 5 || mode == 4) return;
        CompoundTag tag = getTag(wand);
        int sel = (tag.getIntOr(K_SEL, 0) + 1) % 3; // 0=box -> 1=smart -> 2=chain -> 0
        CompoundTag fresh = new CompoundTag();
        fresh.putInt(K_MODE, mode);
        fresh.putInt(K_SEL, sel);
        saveTag(wand, fresh);
        clearServerSmartSelection(player);
        player.sendSystemMessage((switch (sel) {
            case 0 -> Component.translatable("message.buildingwand.selection.box").withStyle(ChatFormatting.GRAY);
            case 1 -> Component.translatable("message.buildingwand.selection.smart").withStyle(ChatFormatting.GOLD);
            default -> Component.translatable("message.buildingwand.selection.chain").withStyle(ChatFormatting.GREEN);
        }));
    }

    public static void serverCancelSelection(ItemStack wand, Player player) {
        int mode = getMode(wand);
        CompoundTag fresh = new CompoundTag();
        fresh.putInt(K_MODE, mode);
        fresh.putInt(K_SEL, 0);
        saveTag(wand, fresh);
        player.sendSystemMessage(Component.translatable("message.buildingwand.selection.cancelled").withStyle(ChatFormatting.GRAY));
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        if (level.isClientSide()) return InteractionResult.SUCCESS;

        Player player = ctx.getPlayer();
        if (player == null) return InteractionResult.PASS;

        BlockPos clicked = ctx.getClickedPos();
        Direction face = ctx.getClickedFace();
        ItemStack wand = ctx.getItemInHand();
        int mode = getMode(wand);
        int step = getStep(wand);

        if (mode == 0) fillModeClick(player, wand, clicked, step, level);
        else if (mode == 1) copyPasteModeClick(player, wand, clicked, face, step, level);
        else if (mode == 2) replaceModeClick(player, wand, clicked, step, level);
        else if (mode == 3) moveModeClick(player, wand, clicked, face, step, level);
        else if (mode == 4) supplyLinkModeClick(player, wand, clicked, step, level);
        else harvestModeClick(player, wand, clicked, step, level);
        return InteractionResult.SUCCESS;
    }

    private void fillModeClick(Player player, ItemStack wand, BlockPos clicked, int step, Level level) {
        if (step == 0) {
            if (getSel(wand) != 0) {
                SelectionData selection = selectStructure(wand, level, clicked);
                if (selection == null) {
                    player.sendSystemMessage(Component.translatable("message.buildingwand.smart.failed"));
                    return;
                }
                storeSmartSelection(player, wand, selection);
                setField(wand, K_STEP, 1);
                player.sendSystemMessage(Component.translatable("message.buildingwand.smart.locked", fmt(selection.min()), fmt(selection.max())));
                return;
            }
            setPos1(wand, clicked);
            setField(wand, K_STEP, 1);
            player.sendSystemMessage(Component.translatable("message.buildingwand.fill.corner1", fmt(clicked)));
        } else {
            BlockPos pos1 = getPos1(wand);
            BlockPos pos2 = getSel(wand) != 0 ? getPos2(wand) : clicked;
            BlockState fill = pickMaterial(player, wand);
            if (fill == null) {
                player.sendSystemMessage(Component.translatable("message.buildingwand.fill.no_material"));
                resetStep(wand);
                return;
            }
            doFill(player, level, pos1, pos2, fill);
            resetStep(wand);
        }
    }

    private BlockState pickMaterial(Player player, ItemStack wand) {
        ItemStack off = player.getOffhandItem();
        if (off.getItem() instanceof BlockItem bi) return bi.getBlock().defaultBlockState();
        for (int i = 0; i < 9; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack == wand || stack.isEmpty()) continue;
            if (stack.getItem() instanceof BlockItem bi) return bi.getBlock().defaultBlockState();
        }
        return null;
    }

    private void doFill(Player player, Level level, BlockPos pos1, BlockPos pos2, BlockState fill) {
        int x1 = Math.min(pos1.getX(), pos2.getX());
        int x2 = Math.max(pos1.getX(), pos2.getX());
        int y1 = Math.min(pos1.getY(), pos2.getY());
        int y2 = Math.max(pos1.getY(), pos2.getY());
        int z1 = Math.min(pos1.getZ(), pos2.getZ());
        int z2 = Math.max(pos1.getZ(), pos2.getZ());
        long vol = (long) (x2 - x1 + 1) * (y2 - y1 + 1) * (z2 - z1 + 1);
        if (vol > MAX_BLOCKS) {
            player.sendSystemMessage(Component.translatable("message.buildingwand.region_too_large", vol, MAX_BLOCKS));
            return;
        }

        int fillable = 0;
        for (int x = x1; x <= x2; x++) for (int y = y1; y <= y2; y++) for (int z = z1; z <= z2; z++) {
            if (canOverwrite(level.getBlockState(new BlockPos(x, y, z)))) fillable++;
        }

        if (fillable == 0) {
            player.sendSystemMessage(Component.translatable("message.buildingwand.fill.no_air"));
            return;
        }
        if (!player.isCreative()) {
            int have = countBlocks(player, fill);
            if (have < fillable) {
                player.sendSystemMessage(Component.translatable("message.buildingwand.materials.insufficient"));
                player.sendSystemMessage(Component.translatable("message.buildingwand.materials.detail", fill.getBlock().getName(), fillable, have));
                return;
            }
        }

        int placed = 0;
        outer:
        for (int x = x1; x <= x2; x++) for (int y = y1; y <= y2; y++) for (int z = z1; z <= z2; z++) {
            BlockPos bp = new BlockPos(x, y, z);
            if (!canOverwrite(level.getBlockState(bp))) continue;
            if (!player.isCreative() && !consumeBlock(player, fill)) break outer;
            level.setBlock(bp, fill, 3);
            placed++;
        }
        player.sendSystemMessage(Component.translatable("message.buildingwand.fill.done", placed));
    }

    private void replaceModeClick(Player player, ItemStack wand, BlockPos clicked, int step, Level level) {
        if (step == 0) {
            if (getSel(wand) != 0) {
                SelectionData selection = selectStructure(wand, level, clicked);
                if (selection == null) {
                    player.sendSystemMessage(Component.translatable("message.buildingwand.smart.failed"));
                    return;
                }
                storeSmartSelection(player, wand, selection);
                setField(wand, K_STEP, 2);
                player.sendSystemMessage(Component.translatable("message.buildingwand.replace.smart_ready"));
                return;
            }
            setPos1(wand, clicked);
            setField(wand, K_STEP, 1);
            player.sendSystemMessage(Component.translatable("message.buildingwand.replace.corner1", fmt(clicked)));
        } else if (step == 1) {
            setPos2(wand, clicked);
            setField(wand, K_STEP, 2);
            player.sendSystemMessage(Component.translatable("message.buildingwand.replace.range_ready"));
        } else {
            BlockState target = level.getBlockState(clicked);
            if (target.isAir()) {
                player.sendSystemMessage(Component.translatable("message.buildingwand.replace.no_air_target"));
                resetStep(wand);
                return;
            }
            BlockState replacement = pickMaterial(player, wand);
            if (replacement == null) {
                player.sendSystemMessage(Component.translatable("message.buildingwand.replace.no_material"));
                resetStep(wand);
                return;
            }
            doReplace(player, level, getPos1(wand), getPos2(wand), target.getBlock(), replacement);
            resetStep(wand);
        }
    }

    private void doReplace(Player player, Level level, BlockPos pos1, BlockPos pos2, Block targetBlock, BlockState replacement) {
        if (getSel(player.getMainHandItem()) != 0 || getSel(player.getOffhandItem()) != 0) {
            ItemStack wand = getHeldWand(player);
            if (wand != null && !readSmartPositions(player, wand).isEmpty()) {
                doReplaceSmart(player, level, readSmartPositions(player, wand), targetBlock, replacement);
                return;
            }
        }
        int x1 = Math.min(pos1.getX(), pos2.getX());
        int x2 = Math.max(pos1.getX(), pos2.getX());
        int y1 = Math.min(pos1.getY(), pos2.getY());
        int y2 = Math.max(pos1.getY(), pos2.getY());
        int z1 = Math.min(pos1.getZ(), pos2.getZ());
        int z2 = Math.max(pos1.getZ(), pos2.getZ());
        long vol = (long) (x2 - x1 + 1) * (y2 - y1 + 1) * (z2 - z1 + 1);
        if (vol > MAX_BLOCKS) {
            player.sendSystemMessage(Component.translatable("message.buildingwand.region_too_large", vol, MAX_BLOCKS));
            return;
        }

        List<BlockPos> targets = new ArrayList<>();
        for (int x = x1; x <= x2; x++) for (int y = y1; y <= y2; y++) for (int z = z1; z <= z2; z++) {
            BlockPos bp = new BlockPos(x, y, z);
            BlockState current = level.getBlockState(bp);
            if (current.getBlock() != targetBlock) continue;
            if (current.getBlock() == replacement.getBlock()) continue;
            if (current.getDestroySpeed(level, bp) < 0) continue;
            targets.add(bp);
        }

        if (targets.isEmpty()) {
            player.sendSystemMessage(Component.translatable("message.buildingwand.replace.empty"));
            return;
        }
        if (!player.isCreative()) {
            int have = countBlocks(player, replacement);
            if (have < targets.size()) {
                player.sendSystemMessage(Component.translatable("message.buildingwand.materials.insufficient"));
                player.sendSystemMessage(Component.translatable("message.buildingwand.materials.detail", replacement.getBlock().getName(), targets.size(), have));
                return;
            }
        }

        int replaced = 0;
        for (BlockPos bp : targets) {
            BlockState old = level.getBlockState(bp);
            if (old.getBlock() != targetBlock || old.getDestroySpeed(level, bp) < 0) continue;
            if (!player.isCreative() && !consumeBlock(player, replacement)) break;
            BlockState placed = inheritSharedProperties(old, replacement);
            if (!player.isCreative()) collectDrops(player, level, bp, old);
            level.setBlock(bp, placed, 3);
            replaced++;
        }
        player.sendSystemMessage(Component.translatable("message.buildingwand.replace.done", replaced));
    }

    private void doReplaceSmart(Player player, Level level, List<BlockPos> selected, Block targetBlock, BlockState replacement) {
        List<BlockPos> targets = new ArrayList<>();
        for (BlockPos pos : selected) {
            BlockState current = level.getBlockState(pos);
            if (current.getBlock() != targetBlock) continue;
            if (current.getBlock() == replacement.getBlock()) continue;
            if (current.getDestroySpeed(level, pos) < 0) continue;
            targets.add(pos);
        }
        if (targets.isEmpty()) {
            player.sendSystemMessage(Component.translatable("message.buildingwand.replace.smart_empty"));
            return;
        }
        if (!player.isCreative()) {
            int have = countBlocks(player, replacement);
            if (have < targets.size()) {
                player.sendSystemMessage(Component.translatable("message.buildingwand.materials.insufficient"));
                player.sendSystemMessage(Component.translatable("message.buildingwand.materials.detail", replacement.getBlock().getName(), targets.size(), have));
                return;
            }
        }

        int replaced = 0;
        for (BlockPos pos : targets) {
            BlockState old = level.getBlockState(pos);
            if (!player.isCreative() && !consumeBlock(player, replacement)) break;
            BlockState placed = inheritSharedProperties(old, replacement);
            if (!player.isCreative()) collectDrops(player, level, pos, old);
            level.setBlock(pos, placed, 3);
            replaced++;
        }
        player.sendSystemMessage(Component.translatable("message.buildingwand.replace.done", replaced));
    }

    private static BlockState inheritSharedProperties(BlockState oldState, BlockState newState) {
        BlockState result = newState;
        for (Property<?> oldProp : oldState.getProperties()) {
            Property<?> newProp = result.getBlock().getStateDefinition().getProperty(oldProp.getName());
            if (newProp != null) result = copyPropertyIfCompatible(oldState, result, oldProp, newProp);
        }
        return result;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static BlockState copyPropertyIfCompatible(BlockState oldState, BlockState newState, Property oldProp, Property newProp) {
        Comparable value = oldState.getValue(oldProp);
        if (!newProp.getPossibleValues().contains(value)) return newState;
        return newState.setValue(newProp, value);
    }

    private void moveModeClick(Player player, ItemStack wand, BlockPos clicked, Direction face, int step, Level level) {
        if (step == 0) {
            if (getSel(wand) != 0) {
                SelectionData selection = selectStructure(wand, level, clicked);
                if (selection == null) {
                    player.sendSystemMessage(Component.translatable("message.buildingwand.smart.failed"));
                    return;
                }
                setPos1(wand, clicked);
                storeSmartSelection(player, wand, selection);
                int copied = copySmartRegion(player, wand, level, clicked, selection.positions());
                if (copied < 0) {
                    player.sendSystemMessage(Component.translatable("message.buildingwand.region_too_large_max", MAX_BLOCKS));
                    resetStep(wand);
                    return;
                }
                if (!allBlocksMovable(level, selection.positions())) {
                    player.sendSystemMessage(Component.translatable("message.buildingwand.move.immovable"));
                    resetStep(wand);
                    return;
                }
                setField(wand, K_STEP, 2);
                player.sendSystemMessage(Component.translatable("message.buildingwand.move.smart_copied", copied));
                return;
            }
            setPos1(wand, clicked);
            setField(wand, K_STEP, 1);
            player.sendSystemMessage(Component.translatable("message.buildingwand.move.corner1", fmt(clicked)));
        } else if (step == 1) {
            BlockPos pos1 = getPos1(wand);
            int copied = copyRegion(player, wand, level, pos1, clicked);
            if (copied < 0) {
                player.sendSystemMessage(Component.translatable("message.buildingwand.region_too_large_max", MAX_BLOCKS));
                resetStep(wand);
                return;
            }
            if (!allBlocksMovable(level, pos1, clicked)) {
                player.sendSystemMessage(Component.translatable("message.buildingwand.move.immovable"));
                resetStep(wand);
                return;
            }
            setField(wand, K_STEP, 2);
            player.sendSystemMessage(Component.translatable("message.buildingwand.move.copied", copied));
        } else {
            doMove(player, wand, level, placementTarget(clicked, face));
        }
    }

    private void supplyLinkModeClick(Player player, ItemStack wand, BlockPos clicked, int step, Level level) {
        if (step == 0) {
            setPos1(wand, clicked);
            setField(wand, K_STEP, 1);
            player.sendSystemMessage(Component.translatable("message.buildingwand.supply.corner1", fmt(clicked)));
        } else if (step == 1) {
            setPos2(wand, clicked);
            setField(wand, K_STEP, 2);
            player.sendSystemMessage(Component.translatable("message.buildingwand.supply.range_ready"));
        } else {
            if (!(level.getBlockState(clicked).getBlock() instanceof WorksiteBlock)
                    || !(level instanceof net.minecraft.server.level.ServerLevel serverLevel)) {
                player.sendSystemMessage(Component.translatable("message.buildingwand.supply.need_post"));
                return;
            }
            WorksiteBlockEntity info = WorksiteManager.get(serverLevel, clicked);
            if (info == null) {
                player.sendSystemMessage(Component.translatable("message.buildingwand.worksite.missing_data"));
                return;
            }
            List<BlockPos> containers = scanContainers(serverLevel, getPos1(wand), getPos2(wand));
            if (containers == null) {
                player.sendSystemMessage(Component.translatable("message.buildingwand.region_too_large_max", MAX_SUPPLY_SCAN));
                return;
            }
            if (containers.isEmpty()) {
                player.sendSystemMessage(Component.translatable("message.buildingwand.supply.no_containers"));
                resetStep(wand);
                return;
            }
            info.setSupplyContainers(containers);
            WorksiteManager.markDirty(serverLevel);
            resetStep(wand);
            player.sendSystemMessage(Component.translatable("message.buildingwand.supply.linked", containers.size()));
        }
    }

    private void harvestModeClick(Player player, ItemStack wand, BlockPos clicked, int step, Level level) {
        if (step == 0) {
            if (getSel(wand) != 0) {
                // Smart selection: outline the connected structure, then lock & wait for confirm.
                SelectionData selection = selectStructure(wand, level, clicked);
                if (selection == null) {
                    player.sendSystemMessage(Component.translatable("message.buildingwand.smart.failed"));
                    return;
                }
                storeSmartSelection(player, wand, selection);
                setField(wand, K_STEP, 2);
                player.sendSystemMessage(Component.translatable("message.buildingwand.harvest.smart_ready", selection.positions().size()));
                return;
            }
            setPos1(wand, clicked);
            setField(wand, K_STEP, 1);
            player.sendSystemMessage(Component.translatable("message.buildingwand.harvest.corner1", fmt(clicked)));
        } else if (step == 1) {
            // Box selection: second corner only locks the range; a third click confirms.
            setPos2(wand, clicked);
            setField(wand, K_STEP, 2);
            player.sendSystemMessage(Component.translatable("message.buildingwand.harvest.range_ready", fmt(getPos1(wand)), fmt(clicked)));
        } else {
            // Confirm click: mine the locked selection.
            if (getSel(wand) != 0) doHarvestSmart(player, level, readSmartPositions(player, wand));
            else doHarvest(player, level, getPos1(wand), getPos2(wand));
            resetStep(wand);
        }
    }

    private void doHarvest(Player player, Level level, BlockPos pos1, BlockPos pos2) {
        long vol = calcVol(pos1, pos2);
        if (vol > MAX_BLOCKS) {
            player.sendSystemMessage(Component.translatable("message.buildingwand.region_too_large", vol, MAX_BLOCKS));
            return;
        }
        int x1 = Math.min(pos1.getX(), pos2.getX()), x2 = Math.max(pos1.getX(), pos2.getX());
        int y1 = Math.min(pos1.getY(), pos2.getY()), y2 = Math.max(pos1.getY(), pos2.getY());
        int z1 = Math.min(pos1.getZ(), pos2.getZ()), z2 = Math.max(pos1.getZ(), pos2.getZ());
        List<BlockPos> positions = new ArrayList<>();
        for (int x = x1; x <= x2; x++) for (int y = y1; y <= y2; y++) for (int z = z1; z <= z2; z++) {
            positions.add(new BlockPos(x, y, z));
        }
        harvestRegion(player, level, positions);
    }

    private void doHarvestSmart(Player player, Level level, List<BlockPos> positions) {
        harvestRegion(player, level, positions);
    }

    /**
     * Mine the given blocks using the player's OFFHAND tool, so all tool enchantments
     * (Fortune, Silk Touch, Unbreaking) apply exactly as if mined by hand. Requires a damageable
     * tool in the offhand; stops early — mid-region — once that tool is down to its last point of
     * durability so the wand never snaps the tool.
     */
    private void harvestRegion(Player player, Level level, List<BlockPos> positions) {
        if (!(level instanceof net.minecraft.server.level.ServerLevel serverLevel)) return;
        ItemStack tool = player.getOffhandItem();
        if (tool.isEmpty() || !tool.isDamageableItem()) {
            player.sendSystemMessage(Component.translatable("message.buildingwand.harvest.no_tool"));
            return;
        }

        int broken = 0;
        boolean toolSpent = false;
        for (BlockPos bp : positions) {
            BlockState state = level.getBlockState(bp);
            if (state.isAir()) continue;
            if (state.getDestroySpeed(level, bp) < 0) continue; // bedrock & other unbreakable
            // Stop while the tool still has one point of durability left (it may leave a region half-harvested).
            if (tool.getMaxDamage() - tool.getDamageValue() <= 1) { toolSpent = true; break; }
            harvestOne(serverLevel, player, tool, bp, state);
            broken++;
        }

        if (broken == 0 && !toolSpent) {
            player.sendSystemMessage(Component.translatable("message.buildingwand.harvest.empty"));
            return;
        }
        player.sendSystemMessage(Component.translatable("message.buildingwand.harvest.done", broken));
        if (toolSpent) {
            player.sendSystemMessage(Component.translatable("message.buildingwand.harvest.tool_spent"));
        }
    }

    private void harvestOne(net.minecraft.server.level.ServerLevel level, Player player, ItemStack tool, BlockPos pos, BlockState state) {
        BlockEntity be = level.getBlockEntity(pos);
        // Drops are computed with the offhand tool so Fortune/Silk Touch are honored.
        if (!player.isCreative()) {
            for (ItemStack drop : Block.getDrops(state, level, pos, be, player, tool)) {
                if (drop.isEmpty()) continue;
                ItemStack copy = drop.copy();
                if (!player.getInventory().add(copy)) player.drop(copy, false);
            }
        }
        level.addDestroyBlockEffect(pos, state); // break particles + sound
        level.removeBlock(pos, false);
        // Apply mining wear to the tool (respects Unbreaking; instant-break blocks cost nothing).
        tool.mineBlock(level, state, pos, player);
    }

    /** A position the wand may build into: air, or a replaceable block such as grass, water or snow. */
    private static boolean canOverwrite(BlockState state) {
        return state.canBeReplaced();
    }

    private List<BlockPos> scanContainers(net.minecraft.server.level.ServerLevel level, BlockPos a, BlockPos b) {
        if (calcVol(a, b) > MAX_SUPPLY_SCAN) return null;
        int x1 = Math.min(a.getX(), b.getX()), x2 = Math.max(a.getX(), b.getX());
        int y1 = Math.min(a.getY(), b.getY()), y2 = Math.max(a.getY(), b.getY());
        int z1 = Math.min(a.getZ(), b.getZ()), z2 = Math.max(a.getZ(), b.getZ());
        List<BlockPos> out = new ArrayList<>();
        for (int x = x1; x <= x2; x++) for (int y = y1; y <= y2; y++) for (int z = z1; z <= z2; z++) {
            BlockPos pos = new BlockPos(x, y, z);
            if (level.getBlockEntity(pos) instanceof Container) {
                out.add(pos.immutable());
                if (out.size() >= MAX_SUPPLY_CONTAINERS) return out;
            }
        }
        return out;
    }

    private void copyPasteModeClick(Player player, ItemStack wand, BlockPos clicked, Direction face, int step, Level level) {
        if (step == 0) {
            if (getSel(wand) != 0) {
                SelectionData selection = selectStructure(wand, level, clicked);
                if (selection == null) {
                    player.sendSystemMessage(Component.translatable("message.buildingwand.smart.failed"));
                    return;
                }
                setPos1(wand, clicked);
                storeSmartSelection(player, wand, selection);
                int copied = copySmartRegion(player, wand, level, clicked, selection.positions());
                if (copied < 0) {
                    player.sendSystemMessage(Component.translatable("message.buildingwand.region_too_large_max", MAX_BLOCKS));
                    resetStep(wand);
                    return;
                }
                setField(wand, K_STEP, 2);
                player.sendSystemMessage(Component.translatable("message.buildingwand.copy.smart_copied", copied));
                return;
            }
            setPos1(wand, clicked);
            setField(wand, K_STEP, 1);
            player.sendSystemMessage(Component.translatable("message.buildingwand.copy.corner1", fmt(clicked)));
        } else if (step == 1) {
            BlockPos pos1 = getPos1(wand);
            int copied = copyRegion(player, wand, level, pos1, clicked);
            if (copied < 0) {
                player.sendSystemMessage(Component.translatable("message.buildingwand.region_too_large_max", MAX_BLOCKS));
                resetStep(wand);
                return;
            }
            setField(wand, K_STEP, 2);
            player.sendSystemMessage(Component.translatable("message.buildingwand.copy.copied", copied));
        } else {
            CompoundTag tag = getTag(wand);
            BlockPos origin = placementTarget(clicked, face);
            if (tag.getIntOr(K_SCHEMATIC, 0) != 0) {
                createWorksite(player, wand, level, origin);
            } else {
                doPaste(player, wand, level, origin);
            }
        }
    }

    private int copyRegion(Player player, ItemStack wand, Level level, BlockPos pos1, BlockPos pos2) {
        if (calcVol(pos1, pos2) > MAX_BLOCKS) return -1;
        int x1 = Math.min(pos1.getX(), pos2.getX());
        int x2 = Math.max(pos1.getX(), pos2.getX());
        int y1 = Math.min(pos1.getY(), pos2.getY());
        int y2 = Math.max(pos1.getY(), pos2.getY());
        int z1 = Math.min(pos1.getZ(), pos2.getZ());
        int z2 = Math.max(pos1.getZ(), pos2.getZ());

        ListTag list = new ListTag();
        for (int x = x1; x <= x2; x++) for (int y = y1; y <= y2; y++) for (int z = z1; z <= z2; z++) {
            BlockState bs = level.getBlockState(new BlockPos(x, y, z));
            if (bs.isAir()) continue;
            CompoundTag e = new CompoundTag();
            e.putShort("dx", (short) (x - pos1.getX()));
            e.putShort("dy", (short) (y - pos1.getY()));
            e.putShort("dz", (short) (z - pos1.getZ()));
            e.put("s", NbtUtils.writeBlockState(bs));
            list.add(e);
        }
        CompoundTag tag = getTag(wand);
        setServerClipboard(player, list);
        if (list.size() <= MAX_PERSISTED_CLIP_BLOCKS) tag.put(K_CLIP, list);
        else tag.remove(K_CLIP);
        maybeSyncClipToClient(player, list);
        tag.putInt(K_OX, pos1.getX());
        tag.putInt(K_OY, pos1.getY());
        tag.putInt(K_OZ, pos1.getZ());
        tag.putInt(K_ROT, 0);
        tag.putInt(K_MIR, 0);
        tag.remove(K_SCHEMATIC);
        saveTag(wand, tag);
        return list.size();
    }

    private int copySmartRegion(Player player, ItemStack wand, Level level, BlockPos origin, List<BlockPos> positions) {
        if (positions.size() > BuildingWandConfig.smartBlockLimit()) return -1;
        ListTag list = new ListTag();
        for (BlockPos pos : positions) {
            BlockState bs = level.getBlockState(pos);
            CompoundTag e = new CompoundTag();
            e.putShort("dx", (short) (pos.getX() - origin.getX()));
            e.putShort("dy", (short) (pos.getY() - origin.getY()));
            e.putShort("dz", (short) (pos.getZ() - origin.getZ()));
            e.put("s", NbtUtils.writeBlockState(bs));
            list.add(e);
        }
        CompoundTag tag = getTag(wand);
        setServerClipboard(player, list);
        if (list.size() <= MAX_PERSISTED_CLIP_BLOCKS) tag.put(K_CLIP, list);
        else tag.remove(K_CLIP);
        maybeSyncClipToClient(player, list);
        tag.putInt(K_OX, origin.getX());
        tag.putInt(K_OY, origin.getY());
        tag.putInt(K_OZ, origin.getZ());
        tag.putInt(K_ROT, 0);
        tag.putInt(K_MIR, 0);
        tag.remove(K_SCHEMATIC);
        saveTag(wand, tag);
        return list.size();
    }

    private void doPaste(Player player, ItemStack wand, Level level, BlockPos origin) {
        CompoundTag tag = getTag(wand);
        ListTag list = getClipboard(player, wand);
        if (list.isEmpty()) {
            player.sendSystemMessage(Component.translatable("message.buildingwand.clipboard.empty"));
            return;
        }

        List<PlacementEntry> entries = buildPlacementEntries(level, list, origin, tag);
        List<PlacementEntry> solidEntries = entries.stream().filter(en -> !en.state().isAir()).toList();
        if (solidEntries.isEmpty()) {
            player.sendSystemMessage(Component.translatable("message.buildingwand.paste.no_blocks"));
            return;
        }

        if (!player.isCreative()) {
            Map<BlockState, Integer> needed = new HashMap<>();
            for (PlacementEntry en : solidEntries) needed.merge(en.state(), 1, Integer::sum);
            List<Component> missing = new ArrayList<>();
            for (Map.Entry<BlockState, Integer> req : needed.entrySet()) {
                int have = countBlocks(player, req.getKey());
                if (have < req.getValue()) {
                    missing.add(Component.translatable("message.buildingwand.materials.missing_line", req.getKey().getBlock().getName(), (req.getValue() - have)));
                }
            }
            if (!missing.isEmpty()) {
                player.sendSystemMessage(Component.translatable("message.buildingwand.paste.auto_worksite"));
                missing.forEach(player::sendSystemMessage);
                createWorksite(player, wand, level, origin);
                return;
            }
        }

        int placed = 0;
        List<BlockPos> placedPositions = new ArrayList<>();
        for (PlacementEntry en : entries) {
            BlockState existing = level.getBlockState(en.target());
            if (en.state().isAir()) continue;
            if (!canOverwrite(existing)) continue;
            if (!player.isCreative() && !consumeBlock(player, en.state())) continue;
            level.setBlock(en.target(), en.state(), PLACE_FLAGS);
            placedPositions.add(en.target());
            placed++;
        }
        settlePlacedBlocks(level, placedPositions);
        player.sendSystemMessage(Component.translatable("message.buildingwand.paste.done", placed));
    }

    private void doMove(Player player, ItemStack wand, Level level, BlockPos origin) {
        CompoundTag tag = getTag(wand);
        ListTag list = getClipboard(player, wand);
        if (list.isEmpty()) {
            player.sendSystemMessage(Component.translatable("message.buildingwand.clipboard.empty"));
            return;
        }

        BlockPos sourceOrigin = getCopyOrigin(wand);
        List<PlacementEntry> entries = buildPlacementEntries(level, list, origin, tag);
        Set<BlockPos> sourcePositions = sourcePositions(list, sourceOrigin);
        Set<BlockPos> targetPositions = new HashSet<>();
        Set<BlockPos> sourceOnly = new HashSet<>(sourcePositions);
        Set<BlockPos> targetOnly = new HashSet<>();
        Set<BlockPos> overlap = new HashSet<>();
        for (PlacementEntry entry : entries) {
            targetPositions.add(entry.target());
            if (sourcePositions.contains(entry.target())) overlap.add(entry.target());
            else targetOnly.add(entry.target());
        }
        sourceOnly.removeAll(targetPositions);


        for (BlockPos sourcePos : sourcePositions) {
            BlockState sourceState = level.getBlockState(sourcePos);
            if (sourceState.isAir()) continue;
            if (!isMovable(sourceState, level, sourcePos)) {
                player.sendSystemMessage(Component.translatable("message.buildingwand.move.source_immovable"));
                resetStep(wand);
                return;
            }
        }

        Map<BlockPos, BlockPos> sourceToTarget = new HashMap<>();
        Set<BlockPos> blockedTargets = new HashSet<>();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag e = list.getCompoundOrEmpty(i);
            BlockPos sourcePos = sourceOrigin.offset(
                    e.getShortOr("dx", (short) 0),
                    e.getShortOr("dy", (short) 0),
                    e.getShortOr("dz", (short) 0));
            PlacementEntry entry = entries.get(i);
            sourceToTarget.put(sourcePos, entry.target());
            if (entry.state().isAir()) continue;
            BlockState existing = level.getBlockState(entry.target());
            if (!canOverwrite(existing) && !sourcePositions.contains(entry.target())) blockedTargets.add(entry.target());
        }

        for (BlockPos sourcePos : sourceOnly) {
            BlockState sourceState = level.getBlockState(sourcePos);
            if (sourceState.isAir()) continue;
            BlockPos targetPos = sourceToTarget.get(sourcePos);
            if (targetPos != null && blockedTargets.contains(targetPos)) {
                if (!player.isCreative()) collectDrops(player, level, sourcePos, sourceState);
                level.setBlock(sourcePos, Blocks.AIR.defaultBlockState(), CLEAR_FLAGS);
                continue;
            }
            level.setBlock(sourcePos, Blocks.AIR.defaultBlockState(), CLEAR_FLAGS);
        }

        int moved = 0;
        List<BlockPos> placedPositions = new ArrayList<>();
        for (PlacementEntry entry : entries) {
            if (entry.state().isAir()) continue;
            if (blockedTargets.contains(entry.target())) continue;
            if (!canOverwrite(level.getBlockState(entry.target())) && !sourcePositions.contains(entry.target())) continue;
            level.setBlock(entry.target(), entry.state(), PLACE_FLAGS);
            placedPositions.add(entry.target());
            moved++;
        }
        settlePlacedBlocks(level, placedPositions);

        resetStep(wand);
        player.sendSystemMessage(Component.translatable("message.buildingwand.move.done", moved));
    }

    private record PlacementEntry(BlockPos target, BlockState state) {}

    /**
     * Run a single physics pass over a batch of blocks that were placed silently with
     * {@link #PLACE_FLAGS}. Because the whole structure already exists, this settles everything
     * without washing anything away: fences/walls/panes/redstone connect (shape pass), then water
     * flows, supported blocks stay put and unsupported ones drop naturally (neighbor pass).
     */
    public static void settlePlacedBlocks(Level level, Collection<BlockPos> positions) {
        // Pass 1: shape connections in both directions.
        for (BlockPos pos : positions) {
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) continue;
            state.updateNeighbourShapes(level, pos, Block.UPDATE_CLIENTS);
        }
        // Pass 2: neighbor signal updates + kick off fluid flow.
        for (BlockPos pos : positions) {
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) continue;
            level.updateNeighborsAt(pos, state.getBlock());
            FluidState fluid = state.getFluidState();
            if (!fluid.isEmpty()) {
                level.scheduleTick(pos, fluid.getType(), 2);
            }
        }
    }

    private List<PlacementEntry> buildPlacementEntries(Level level, ListTag list, BlockPos origin, CompoundTag tag) {
        Rotation rot = ROTATIONS[Math.floorMod(tag.getIntOr(K_ROT, 0), 4)];
        int mirInt = Math.floorMod(tag.getIntOr(K_MIR, 0), 4);
        Mirror mcMir = MC_MIRRORS[mirInt];

        List<PlacementEntry> entries = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            CompoundTag e = list.getCompoundOrEmpty(i);
            int dx = e.getShortOr("dx", (short) 0);
            int dy = e.getShortOr("dy", (short) 0);
            int dz = e.getShortOr("dz", (short) 0);
            BlockState bs = NbtUtils.readBlockState(
                    level.registryAccess().lookupOrThrow(Registries.BLOCK), e.getCompoundOrEmpty("s"));
            entries.add(new PlacementEntry(
                    origin.offset(transformOffset(dx, dy, dz, rot, mirInt)),
                    bs.mirror(mcMir).rotate(rot)));
        }
        return entries;
    }

    private Set<BlockPos> sourcePositions(ListTag list, BlockPos sourceOrigin) {
        Set<BlockPos> positions = new HashSet<>();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag e = list.getCompoundOrEmpty(i);
            positions.add(sourceOrigin.offset(
                    e.getShortOr("dx", (short) 0),
                    e.getShortOr("dy", (short) 0),
                    e.getShortOr("dz", (short) 0)));
        }
        return positions;
    }

    private boolean allBlocksMovable(Level level, BlockPos pos1, BlockPos pos2) {
        int x1 = Math.min(pos1.getX(), pos2.getX());
        int x2 = Math.max(pos1.getX(), pos2.getX());
        int y1 = Math.min(pos1.getY(), pos2.getY());
        int y2 = Math.max(pos1.getY(), pos2.getY());
        int z1 = Math.min(pos1.getZ(), pos2.getZ());
        int z2 = Math.max(pos1.getZ(), pos2.getZ());
        for (int x = x1; x <= x2; x++) for (int y = y1; y <= y2; y++) for (int z = z1; z <= z2; z++) {
            BlockPos bp = new BlockPos(x, y, z);
            BlockState state = level.getBlockState(bp);
            if (state.isAir()) continue;
            if (!isMovable(state, level, bp)) return false;
        }
        return true;
    }

    private boolean allBlocksMovable(Level level, List<BlockPos> positions) {
        for (BlockPos pos : positions) {
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) continue;
            if (!isMovable(state, level, pos)) return false;
        }
        return true;
    }

    private boolean isMovable(BlockState state, Level level, BlockPos pos) {
        return state.getDestroySpeed(level, pos) >= 0 && !IMMOVABLE_BLOCKS.contains(state.getBlock());
    }

    /** Pick the structure-selection strategy based on the wand's selection mode (1=smart, 2=chain). */
    private SelectionData selectStructure(ItemStack wand, Level level, BlockPos clicked) {
        return getSel(wand) == 2 ? chainSelect(level, clicked) : smartSelect(level, clicked);
    }

    /**
     * Chain (vein) selection: flood-fill outward through blocks of the SAME type as the clicked one,
     * in all 26 directions (so diagonal ore veins connect). Capped by the same scan/block limits as
     * smart selection to prevent runaway selections (e.g. clicking into a huge stone mass).
     * Returns null if the target is air or the connected mass exceeds the limits.
     */
    private SelectionData chainSelect(Level level, BlockPos clicked) {
        BlockState anchor = level.getBlockState(clicked);
        if (anchor.isAir()) return null;
        Block anchorBlock = anchor.getBlock();

        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Set<Long> visited = new HashSet<>();
        List<BlockPos> positions = new ArrayList<>();
        queue.add(clicked);

        int minX = clicked.getX(), minY = clicked.getY(), minZ = clicked.getZ();
        int maxX = clicked.getX(), maxY = clicked.getY(), maxZ = clicked.getZ();
        int scanned = 0;

        while (!queue.isEmpty()) {
            BlockPos pos = queue.removeFirst();
            if (!visited.add(pos.asLong())) continue;
            if (level.getBlockState(pos).getBlock() != anchorBlock) continue;

            scanned++;
            if (scanned > BuildingWandConfig.smartScanLimit()) return null;
            positions.add(pos);
            if (positions.size() > BuildingWandConfig.smartBlockLimit()) return null;

            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());

            for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dy == 0 && dz == 0) continue;
                BlockPos next = pos.offset(dx, dy, dz);
                if (visited.contains(next.asLong())) continue;
                if (level.getBlockState(next).getBlock() != anchorBlock) continue;
                queue.addLast(next);
            }
        }

        if (positions.isEmpty()) return null;
        return new SelectionData(new BlockPos(minX, minY, minZ), new BlockPos(maxX, maxY, maxZ), positions);
    }

    private SelectionData smartSelect(Level level, BlockPos clicked) {
        if (level.getBlockState(clicked).isAir()) return null;
        Block anchorBlock = level.getBlockState(clicked).getBlock();

        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Set<Long> visited = new HashSet<>();
        List<BlockPos> positions = new ArrayList<>();
        queue.add(clicked);

        int minX = clicked.getX();
        int minY = clicked.getY();
        int minZ = clicked.getZ();
        int maxX = clicked.getX();
        int maxY = clicked.getY();
        int maxZ = clicked.getZ();
        int scanned = 0;

        while (!queue.isEmpty()) {
            BlockPos pos = queue.removeFirst();
            long key = pos.asLong();
            if (!visited.add(key)) continue;
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) continue;

            scanned++;
            if (scanned > BuildingWandConfig.smartScanLimit()) return null;
            positions.add(pos);

            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());

            for (BlockPos next : new BlockPos[]{
                    pos.north(), pos.south(), pos.east(), pos.west(), pos.above(), pos.below()
            }) {
                if (visited.contains(next.asLong())) continue;
                BlockState nextState = level.getBlockState(next);
                if (nextState.isAir()) continue;
                if (next.getY() < clicked.getY() && nextState.getBlock() != anchorBlock) continue;
                queue.addLast(next);
            }
        }

        if (positions.isEmpty()) return null;
        if (positions.size() > BuildingWandConfig.smartBlockLimit()) return null;

        Set<Long> solidSet = new HashSet<>();
        for (BlockPos pos : positions) solidSet.add(pos.asLong());
        List<BlockPos> enclosedAir = findEnclosedAir(level, minX, minY, minZ, maxX, maxY, maxZ, solidSet);
        positions.addAll(enclosedAir);
        if (positions.size() > BuildingWandConfig.smartBlockLimit()) return null;

        return new SelectionData(new BlockPos(minX, minY, minZ), new BlockPos(maxX, maxY, maxZ), positions);
    }

    private List<BlockPos> findEnclosedAir(Level level, int minX, int minY, int minZ, int maxX, int maxY, int maxZ, Set<Long> solidSet) {
        int exMinX = minX - 1, exMinY = minY - 1, exMinZ = minZ - 1;
        int exMaxX = maxX + 1, exMaxY = maxY + 1, exMaxZ = maxZ + 1;
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Set<Long> outsideAir = new HashSet<>();
        queue.add(new BlockPos(exMinX, exMinY, exMinZ));

        while (!queue.isEmpty()) {
            BlockPos pos = queue.removeFirst();
            if (pos.getX() < exMinX || pos.getX() > exMaxX
                    || pos.getY() < exMinY || pos.getY() > exMaxY
                    || pos.getZ() < exMinZ || pos.getZ() > exMaxZ) continue;
            long key = pos.asLong();
            if (!outsideAir.add(key)) continue;
            if (solidSet.contains(key)) continue;
            if (!level.getBlockState(pos).isAir()) continue;

            queue.add(pos.north());
            queue.add(pos.south());
            queue.add(pos.east());
            queue.add(pos.west());
            queue.add(pos.above());
            queue.add(pos.below());
        }

        List<BlockPos> enclosed = new ArrayList<>();
        for (int x = minX; x <= maxX; x++) for (int y = minY; y <= maxY; y++) for (int z = minZ; z <= maxZ; z++) {
            BlockPos pos = new BlockPos(x, y, z);
            long key = pos.asLong();
            if (solidSet.contains(key)) continue;
            if (!level.getBlockState(pos).isAir()) continue;
            if (!outsideAir.contains(key)) enclosed.add(pos);
        }
        return enclosed;
    }

    private void createWorksite(Player player, ItemStack wand, Level level, BlockPos origin) {
        CompoundTag tag = getTag(wand);
        ListTag clip = getClipboard(player, wand);
        if (clip.isEmpty()) {
            player.sendSystemMessage(Component.translatable("message.buildingwand.clipboard.empty"));
            return;
        }
        int rot = Math.floorMod(tag.getIntOr(K_ROT, 0), 4);
        int mir = Math.floorMod(tag.getIntOr(K_MIR, 0), 4);

        BlockPos postPos = origin.above();
        if (!level.getBlockState(postPos).isAir()) {
            player.sendSystemMessage(Component.translatable("message.buildingwand.worksite.post_blocked"));
            return;
        }

        level.setBlock(postPos, ModItems.WORKSITE_POST.defaultBlockState(), 3);
        WorksiteBlockEntity be = new WorksiteBlockEntity(clip, rot, mir, level, player, origin.subtract(postPos), postPos);
        if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            WorksiteManager.add(serverLevel, postPos, be);
            be.refreshDecoration(serverLevel, postPos);
        }

        resetStep(wand);
        player.sendSystemMessage(Component.translatable("message.buildingwand.worksite.created"));
    }

    public static int countBlocks(Player player, BlockState state) {
        Item target = state.getBlock().asItem();
        if (target == Items.AIR) return 0;
        int count = 0;
        Inventory inv = player.getInventory();
        for (int i = 0; i < 36; i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.is(target)) count += stack.getCount();
            else if (isShulkerBox(stack)) count += countInShulker(stack, target);
        }
        ItemStack off = player.getOffhandItem();
        if (off.is(target)) count += off.getCount();
        else if (isShulkerBox(off)) count += countInShulker(off, target);
        return count;
    }

    public static boolean consumeBlock(Player player, BlockState state) {
        Item target = state.getBlock().asItem();
        if (target == Items.AIR) return false;
        Inventory inv = player.getInventory();
        ItemStack off = player.getOffhandItem();
        // Shulker boxes FIRST (carried bulk material), so loose hotbar stacks are spared.
        for (int i = 0; i < 36; i++) {
            ItemStack stack = inv.getItem(i);
            if (isShulkerBox(stack) && consumeFromShulker(stack, target)) return true;
        }
        if (isShulkerBox(off) && consumeFromShulker(off, target)) return true;
        // Then loose items: offhand -> hotbar -> main inventory.
        if (!off.isEmpty() && off.is(target)) {
            off.shrink(1);
            return true;
        }
        for (int i = 0; i < 9; i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty() && stack.is(target)) {
                stack.shrink(1);
                return true;
            }
        }
        for (int i = 9; i < 36; i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty() && stack.is(target)) {
                stack.shrink(1);
                return true;
            }
        }
        return false;
    }

    private static boolean isShulkerBox(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof BlockItem bi && bi.getBlock() instanceof ShulkerBoxBlock;
    }

    private static int countInShulker(ItemStack shulker, Item target) {
        ItemContainerContents contents = shulker.get(DataComponents.CONTAINER);
        if (contents == null) return 0;
        int[] total = {0};
        contents.nonEmptyItemCopyStream().forEach(stack -> {
            if (stack.is(target)) total[0] += stack.getCount();
        });
        return total[0];
    }

    private static boolean consumeFromShulker(ItemStack shulker, Item target) {
        ItemContainerContents contents = shulker.get(DataComponents.CONTAINER);
        if (contents == null) return false;
        NonNullList<ItemStack> slots = NonNullList.withSize(27, ItemStack.EMPTY);
        contents.copyInto(slots);
        for (ItemStack stack : slots) {
            if (!stack.isEmpty() && stack.is(target)) {
                stack.shrink(1);
                shulker.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(slots));
                return true;
            }
        }
        return false;
    }

    private static void collectDrops(Player player, Level level, BlockPos pos, BlockState state) {
        if (!(level instanceof net.minecraft.server.level.ServerLevel serverLevel)) return;
        List<ItemStack> drops = Block.getDrops(state, serverLevel, pos, null, player, ItemStack.EMPTY);
        for (ItemStack drop : drops) {
            if (drop.isEmpty()) continue;
            ItemStack copy = drop.copy();
            if (!player.getInventory().add(copy)) player.drop(copy, false);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, TooltipDisplay display, Consumer<Component> tips, TooltipFlag flag) {
        int mode = getMode(stack);
        int step = getStep(stack);
        if (mode == 0) {
            tips.accept(Component.translatable("tooltip.buildingwand.mode.fill").withStyle(ChatFormatting.ITALIC));
            tips.accept(Component.translatable("tooltip.buildingwand.fill.hint").withStyle(ChatFormatting.ITALIC));
         } else if (mode == 1) {
            tips.accept(Component.translatable("tooltip.buildingwand.mode.copy").withStyle(ChatFormatting.ITALIC));
            if (step == 2) {
                CompoundTag tag = getTag(stack);
                int rot = Math.floorMod(tag.getIntOr(K_ROT, 0), 4);
                int mir = Math.floorMod(tag.getIntOr(K_MIR, 0), 4);
                tips.accept(Component.translatable("tooltip.buildingwand.transform", ROT_NAMES[rot], Component.translatable(MIR_NAMES[mir])).withStyle(ChatFormatting.ITALIC));
                if (tag.getIntOr(K_SCHEMATIC, 0) != 0) {
                    tips.accept(Component.translatable("tooltip.buildingwand.schematic").withStyle(ChatFormatting.ITALIC));
                } else {
                    tips.accept(Component.translatable("tooltip.buildingwand.copy.controls").withStyle(ChatFormatting.ITALIC));
                }
            }
         } else if (mode == 2) {
            tips.accept(Component.translatable("tooltip.buildingwand.mode.replace").withStyle(ChatFormatting.ITALIC));
            tips.accept(Component.translatable("tooltip.buildingwand.replace.hint").withStyle(ChatFormatting.ITALIC));
         } else if (mode == 3) {
            tips.accept(Component.translatable("tooltip.buildingwand.mode.move").withStyle(ChatFormatting.ITALIC));
            tips.accept(Component.translatable("tooltip.buildingwand.move.hint").withStyle(ChatFormatting.ITALIC));
         } else if (mode == 4) {
            tips.accept(Component.translatable("tooltip.buildingwand.mode.supply").withStyle(ChatFormatting.ITALIC));
            tips.accept(Component.translatable("tooltip.buildingwand.supply.hint").withStyle(ChatFormatting.ITALIC));
         } else {
            tips.accept(Component.translatable("tooltip.buildingwand.mode.harvest").withStyle(ChatFormatting.ITALIC));
            tips.accept(Component.translatable("tooltip.buildingwand.harvest.hint").withStyle(ChatFormatting.ITALIC));
        }
        tips.accept(Component.translatable("tooltip.buildingwand.common.controls").withStyle(ChatFormatting.ITALIC));
    }

    private static long calcVol(BlockPos a, BlockPos b) {
        return (long) (Math.abs(a.getX() - b.getX()) + 1)
                * (Math.abs(a.getY() - b.getY()) + 1)
                * (Math.abs(a.getZ() - b.getZ()) + 1);
    }

    private static String fmt(BlockPos pos) {
        return "(" + pos.getX() + "," + pos.getY() + "," + pos.getZ() + ")";
    }

    private record SelectionData(BlockPos min, BlockPos max, List<BlockPos> positions) {}
}
