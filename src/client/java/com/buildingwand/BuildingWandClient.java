package com.buildingwand;

import com.buildingwand.item.BuildingWandItem;
import com.buildingwand.network.*;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.arguments.StringArgumentType;
import fi.dy.masa.litematica.config.Configs;
import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.data.SchematicHolder;
import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.litematica.schematic.container.LitematicaBlockStateContainer;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.litematica.util.FileType;
import fi.dy.masa.malilib.gui.Message;
import fi.dy.masa.malilib.gui.interfaces.IMessageConsumer;
import fi.dy.masa.malilib.interfaces.IStringConsumer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.KeyMapping.Category;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.gizmos.DrawableGizmoPrimitives;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class BuildingWandClient implements ClientModInitializer {

    public static KeyMapping KEY_ROTATE;
    public static KeyMapping KEY_MIRROR;
    public static KeyMapping KEY_MODE;
    public static KeyMapping KEY_SELECTION;

    private static final int COLOR_SEL_FILL = 0xCCFFEE00;
    private static final int COLOR_SEL_COPY = 0xCC00EEFF;
    private static final int COLOR_SEL_REPLACE = 0xCCFF55FF;
    private static final int COLOR_SEL_MOVE = 0xCC55FF88;

    // ── Litematica state ──────────────────────────────────────────────────────
    private static LitematicaSchematic currentSchematic = null;
    private static SchematicPlacement  activePlacement  = null;
    private static BlockPos lastPlacementPos            = null;
    private static int      lastRot                     = -1;
    private static int      lastMir                     = -1;
    private static int      lastClipHash                = 0;
    private static ListTag  clientClipboard             = new ListTag();

    private static final IStringConsumer   SILENT     = msg -> {};
    private static final IMessageConsumer MSG_SILENT = new IMessageConsumer() {
        @Override public void addMessage(Message.MessageType t, String m, Object... a) {}
        @Override public void addMessage(Message.MessageType t, int e, String m, Object... a) {}
    };
    private record ClipBlock(int x, int y, int z, BlockState state) {}

    @Override
    public void onInitializeClient() {
        KEY_ROTATE = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.buildingwand.rotate", GLFW.GLFW_KEY_R, Category.GAMEPLAY));
        KEY_MIRROR = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.buildingwand.mirror", GLFW.GLFW_KEY_B, Category.GAMEPLAY));
        KEY_MODE = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.buildingwand.mode", GLFW.GLFW_KEY_N, Category.GAMEPLAY));
        KEY_SELECTION = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.buildingwand.selection", GLFW.GLFW_KEY_V, Category.GAMEPLAY));
        migrateMirrorKeyBinding();

        // ── Key presses → server ───────────────────────────────────────────────
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (mc.player == null) return;
            ItemStack wand = BuildingWandItem.getHeldWand(mc.player);
            if (wand == null) return;
            while (KEY_MODE.consumeClick()) ClientPlayNetworking.send(new WandKeyPayload(2));
            while (KEY_SELECTION.consumeClick()) ClientPlayNetworking.send(new WandKeyPayload(3));
            int mode = BuildingWandItem.getMode(wand);
            int step = BuildingWandItem.getStep(wand);
            int sel = BuildingWandItem.getSel(wand);
            if (sel != 0 && ((mode == 0 && step > 0) || (mode == 2 && step == 2))) {
                while (KEY_MIRROR.consumeClick()) ClientPlayNetworking.send(new WandKeyPayload(4));
            }
            if ((mode != 1 && mode != 3) || step != 2) return;
            while (KEY_ROTATE.consumeClick()) ClientPlayNetworking.send(new WandKeyPayload(0));
            while (KEY_MIRROR.consumeClick()) ClientPlayNetworking.send(new WandKeyPayload(1));
        });

        // ── Litematica placement: follow cursor + sync rotation/mirror ─────────
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (mc.player == null) return;
            ItemStack wand = BuildingWandItem.getHeldWand(mc.player);
            if (wand == null
                    || (BuildingWandItem.getMode(wand) != 1 && BuildingWandItem.getMode(wand) != 3)
                    || BuildingWandItem.getStep(wand) != 2) {
                removePlacement();
                return;
            }

            CompoundTag tag = BuildingWandItem.getTag(wand);
            ListTag clip = tag.getListOrEmpty(BuildingWandItem.K_CLIP);
            if (clip.isEmpty()) {
                // K_CLIP is stripped from the wand NBT for big clips; the client
                // keeps its own copy (from /wand load or the WandClipPayload sync).
                clip = clientClipboard;
            }
            int rot = tag.getIntOr(BuildingWandItem.K_ROT, 0);
            int mir = tag.getIntOr(BuildingWandItem.K_MIR, 0);
            int clipHash = 31 * clip.hashCode() + (Math.floorMod(mir, 4) == 3 ? 1 : 0);
            if (activePlacement == null || currentSchematic == null || clipHash != lastClipHash) {
                createPlacementFromClip(clip, tag.getIntOr(BuildingWandItem.K_SCHEMATIC, 0) != 0
                        ? "Building Wand Load" : (BuildingWandItem.getMode(wand) == 3 ? "Building Wand Move" : "Building Wand Clipboard"), mir);
            }
            if (activePlacement == null || currentSchematic == null) return;

            // Sync rotation/mirror from wand NBT → Litematica placement
            if (rot != lastRot || mir != lastMir) {
                lastRot = rot;
                lastMir = mir;
                activePlacement.setRotation(BuildingWandItem.ROTATIONS[Math.floorMod(rot, 4)], MSG_SILENT);
                activePlacement.setMirror(previewMirror(mir), MSG_SILENT);
            }

            // Follow cursor position
            if (!(mc.hitResult instanceof BlockHitResult bhr)) return;
            BlockPos target = BuildingWandItem.placementTarget(bhr.getBlockPos(), bhr.getDirection());
            if (target.equals(lastPlacementPos)) return;
            lastPlacementPos = target;
            activePlacement.setOrigin(target, SILENT);
        });

        // ── S2C packet handlers ────────────────────────────────────────────────
        ClientPlayNetworking.registerGlobalReceiver(WorksiteClearPayload.TYPE, (payload, ctx) ->
                ctx.client().execute(() -> {
                    removePlacement();
                    if (ctx.client().screen instanceof WorksiteScreen) {
                        ctx.client().setScreen(null);
                    }
                }));

        ClientPlayNetworking.registerGlobalReceiver(WorksiteOpenPayload.TYPE, (payload, ctx) ->
                ctx.client().execute(() ->
                        WorksiteScreen.openOrRefresh(payload.pos(), payload.needed(),
                                payload.deposited(), payload.building())));

        // Large copy/move clips can't ride in the wand NBT (inventory-sync size
        // limit); the server pushes them here so the ghost preview can render.
        ClientPlayNetworking.registerGlobalReceiver(WandClipPayload.TYPE, (payload, ctx) ->
                ctx.client().execute(() -> clientClipboard = payload.clipboard().copy()));

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> client.execute(BuildingWandClient::clearPreviewState));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(BuildingWandClient::clearPreviewState));

        // ── /wand load <path> ─────────────────────────────────────────────────
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, ctx) ->
            dispatcher.register(ClientCommands.literal("wand")
                .then(ClientCommands.literal("settings")
                    .executes(context -> {
                        Minecraft.getInstance().execute(() ->
                                Minecraft.getInstance().setScreen(new BuildingWandSettingsScreen()));
                        return 1;
                    }))
                .then(ClientCommands.literal("load")
                    .then(ClientCommands.argument("path", StringArgumentType.greedyString())
                        .executes(context -> {
                            String path = StringArgumentType.getString(context, "path").trim();
                            if (path.length() > 1 && path.charAt(0) == '"' && path.charAt(path.length() - 1) == '"')
                                path = path.substring(1, path.length() - 1);
                            final String finalPath = path;
                            Minecraft.getInstance().execute(() -> loadSchematic(finalPath));
                            return 1;
                        })))));

        // ── Gizmo: show selection box in step-1 ───────────────────────────────
        LevelRenderEvents.BEFORE_GIZMOS.register(BuildingWandClient::renderSelectionGizmo);
    }

    private static void migrateMirrorKeyBinding() {
        if ("key.keyboard.m".equals(KEY_MIRROR.saveString())) {
            KEY_MIRROR.setKey(InputConstants.getKey("key.keyboard.b"));
            KeyMapping.resetMapping();
            Minecraft.getInstance().options.save();
        }
    }

    // ── Schematic loading (Litematica SchematicHolder flow) ───────────────────

    private static void loadSchematic(String filePath) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        mc.player.sendSystemMessage(Component.translatable("message.buildingwand.client.loading", filePath));
        try {
            Path path = Path.of(filePath);

            // Use SchematicHolder.getOrLoad — registers the schematic and returns it.
            // This matches the flow Litematica's own GUI uses, so ghost rendering works.
            LitematicaSchematic schematic = SchematicHolder.getInstance().getOrLoad(path);
            if (schematic == null)
                throw new Exception("Litematica could not load this file. Check the path and format (.litematic only).");

            ListTag clipList = extractClip(schematic);
            if (clipList.isEmpty())
                throw new Exception("The schematic is empty.");

            // Replace old placement with new one
            clientClipboard = clipList.copy();
            createPlacementFromClip(clipList, schematic.getMetadata().getName(), 0);

            // bool1=renderEnclosingBox, bool2=enabled — both true: show box AND render blocks
            // Send clipboard to server
            ClientPlayNetworking.send(new WandLoadPayload(clipList));
            mc.player.sendSystemMessage(Component.translatable(
                    "message.buildingwand.client.loaded", clipList.size()));

        } catch (Exception e) {
            mc.player.sendSystemMessage(Component.translatable("message.buildingwand.client.load_failed", e.getMessage()));
        }
    }

    private static void createPlacementFromClip(ListTag clip, String placementName, int mir) {
        if (clip.isEmpty()) {
            removePlacement();
            return;
        }
        try {
            LitematicaSchematic schematic = schematicFromClip(clip, placementName, mir);
            removePlacement();
            currentSchematic = schematic;
            activePlacement = SchematicPlacement.createTemporary(currentSchematic, BlockPos.ZERO);
            activePlacement.setName(placementName);
            activePlacement.setShouldBeSaved(false);
            DataManager.getSchematicPlacementManager().addSchematicPlacement(activePlacement, false);
            DataManager.getSchematicPlacementManager().setSelectedSchematicPlacement(activePlacement);
            lastPlacementPos = null;
            lastRot = -1;
            lastMir = -1;
            lastClipHash = 31 * clip.hashCode() + (Math.floorMod(mir, 4) == 3 ? 1 : 0);

            Configs.Visuals.ENABLE_RENDERING.setBooleanValue(true);
            Configs.Visuals.ENABLE_SCHEMATIC_RENDERING.setBooleanValue(true);
            Configs.Visuals.ENABLE_SCHEMATIC_BLOCKS.setBooleanValue(true);
        } catch (Exception e) {
            removePlacement();
        }
    }

    private static int previewDy(int dy, int mir) {
        return Math.floorMod(mir, 4) == 3 ? -dy : dy;
    }

    private static net.minecraft.world.level.block.Mirror previewMirror(int mir) {
        int normalized = Math.floorMod(mir, 4);
        return normalized == 3 ? net.minecraft.world.level.block.Mirror.NONE : BuildingWandItem.MC_MIRRORS[normalized];
    }

    private static LitematicaSchematic schematicFromClip(ListTag clip, String name, int mir) throws Exception {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (int i = 0; i < clip.size(); i++) {
            CompoundTag e = clip.getCompoundOrEmpty(i);
            int dx = e.getShortOr("dx", (short) 0);
            int dy = previewDy(e.getShortOr("dy", (short) 0), mir);
            int dz = e.getShortOr("dz", (short) 0);
            minX = Math.min(minX, dx); minY = Math.min(minY, dy); minZ = Math.min(minZ, dz);
            maxX = Math.max(maxX, dx); maxY = Math.max(maxY, dy); maxZ = Math.max(maxZ, dz);
        }
        int sizeX = maxX - minX + 1;
        int sizeY = maxY - minY + 1;
        int sizeZ = maxZ - minZ + 1;
        if (sizeX <= 0 || sizeY <= 0 || sizeZ <= 0) throw new IllegalArgumentException("empty clipboard");

        Minecraft mc = Minecraft.getInstance();
        var blockLookup = mc.level.registryAccess().lookupOrThrow(Registries.BLOCK);
        LitematicaBlockStateContainer container = new LitematicaBlockStateContainer(sizeX, sizeY, sizeZ);
        for (int i = 0; i < clip.size(); i++) {
            CompoundTag e = clip.getCompoundOrEmpty(i);
            int dx = e.getShortOr("dx", (short) 0);
            int dy = previewDy(e.getShortOr("dy", (short) 0), mir);
            int dz = e.getShortOr("dz", (short) 0);
            BlockState state = NbtUtils.readBlockState(blockLookup, e.getCompoundOrEmpty("s"));
            container.set(dx - minX, dy - minY, dz - minZ, state);
        }

        LitematicaSchematic schematic = newEmptySchematic();
        String region = "BuildingWand";
        getPrivateMap(schematic, "blockContainers").put(region, container);
        getPrivateMap(schematic, "subRegionPositions").put(region, new BlockPos(minX, minY, minZ));
        getPrivateMap(schematic, "subRegionSizes").put(region, new BlockPos(sizeX, sizeY, sizeZ));
        getPrivateMap(schematic, "tileEntities").put(region, new HashMap<BlockPos, CompoundTag>());
        getPrivateMap(schematic, "pendingBlockTicks").put(region, new HashMap<>());
        getPrivateMap(schematic, "pendingFluidTicks").put(region, new HashMap<>());
        getPrivateMap(schematic, "entities").put(region, new ArrayList<>());

        var metadata = schematic.getMetadata();
        metadata.setName(name);
        metadata.setRegionCount(1);
        metadata.setTotalBlocks(clip.size());
        metadata.setTotalVolume(sizeX * sizeY * sizeZ);
        metadata.setEnclosingSize(new BlockPos(sizeX, sizeY, sizeZ));
        metadata.setTimeCreated(System.currentTimeMillis());
        metadata.setTimeModified(System.currentTimeMillis());
        metadata.setFileType(FileType.LITEMATICA_SCHEMATIC);
        metadata.setSchema();
        return schematic;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> getPrivateMap(LitematicaSchematic schematic, String fieldName) throws Exception {
        Field field = LitematicaSchematic.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return (Map<String, Object>) field.get(schematic);
    }

    private static LitematicaSchematic newEmptySchematic() throws Exception {
        Constructor<LitematicaSchematic> ctor =
                LitematicaSchematic.class.getDeclaredConstructor(Path.class, FileType.class);
        ctor.setAccessible(true);
        return ctor.newInstance(Path.of("buildingwand_clipboard.litematic"), FileType.LITEMATICA_SCHEMATIC);
    }

    /** Extract a ListTag clipboard from a loaded LitematicaSchematic. */
    private static ListTag extractClip(LitematicaSchematic schematic) {
        Map<String, BlockPos> sizes = schematic.getAreaSizes();
        java.util.List<ClipBlock> blocks = new ArrayList<>();

        for (String region : sizes.keySet()) {
            BlockPos size   = sizes.get(region);
            BlockPos regPos = schematic.getSubRegionPosition(region);
            var container   = schematic.getSubRegionContainer(region);
            if (container == null) continue;
            int szX  = Math.abs(size.getX()), szY = Math.abs(size.getY()), szZ = Math.abs(size.getZ());
            for (int x = 0; x < szX; x++) {
                for (int y = 0; y < szY; y++) {
                    for (int z = 0; z < szZ; z++) {
                        var bs = container.get(x, y, z);
                        if (bs == null || bs.isAir()) continue;
                        int wx = signedRegionCoord(regPos.getX(), size.getX(), x);
                        int wy = signedRegionCoord(regPos.getY(), size.getY(), y);
                        int wz = signedRegionCoord(regPos.getZ(), size.getZ(), z);
                        blocks.add(new ClipBlock(wx, wy, wz, bs));
                    }
                }
            }
        }
        if (blocks.isEmpty()) return new ListTag();
        ListTag clip = new ListTag();
        for (ClipBlock b : blocks) {
            CompoundTag e = new CompoundTag();
            e.putShort("dx", (short)b.x());
            e.putShort("dy", (short)b.y());
            e.putShort("dz", (short)b.z());
            e.put("s", NbtUtils.writeBlockState(b.state()));
            clip.add(e);
        }
        return clip;
    }

    private static int signedRegionCoord(int regionOrigin, int signedSize, int containerCoord) {
        return regionOrigin + (signedSize < 0 ? signedSize + 1 : 0) + containerCoord;
    }

    private static void removePlacement() {
        if (activePlacement != null) {
            try {
                DataManager.getSchematicPlacementManager().removeSchematicPlacement(activePlacement);
            } catch (Exception ignored) {}
            activePlacement  = null;
        }
        currentSchematic = null;
        lastPlacementPos = null;
        lastRot = -1;
        lastMir = -1;
        lastClipHash = 0;
    }

    private static void clearPreviewState() {
        removePlacement();
        clientClipboard = new ListTag();
    }

    // ── Gizmo: selection box during step-1 ───────────────────────────────────

    private static void renderSelectionGizmo(LevelRenderContext context) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.hitResult == null) return;
        ItemStack wand = BuildingWandItem.getHeldWand(mc.player);
        if (wand == null) return;
        int step = BuildingWandItem.getStep(wand);
        if ((step != 1 && step != 2) || !(mc.hitResult instanceof BlockHitResult bhr)) return;
        int mode = BuildingWandItem.getMode(wand);
        int sel = BuildingWandItem.getSel(wand);
        if (step == 2 && mode != 2 && sel == 0) return;
        CompoundTag tag = BuildingWandItem.getTag(wand);
        BlockPos pos1 = new BlockPos(
                tag.getIntOr(BuildingWandItem.K_P1X, 0),
                tag.getIntOr(BuildingWandItem.K_P1Y, 0),
                tag.getIntOr(BuildingWandItem.K_P1Z, 0));
        boolean useStoredBounds = sel != 0 || (step == 2 && mode == 2);
        BlockPos pos2 = useStoredBounds
                ? new BlockPos(
                tag.getIntOr(BuildingWandItem.K_P2X, 0),
                tag.getIntOr(BuildingWandItem.K_P2Y, 0),
                tag.getIntOr(BuildingWandItem.K_P2Z, 0))
                : bhr.getBlockPos();
        int color = mode == 0 ? COLOR_SEL_FILL : (mode == 1 ? COLOR_SEL_COPY : (mode == 2 ? COLOR_SEL_REPLACE : COLOR_SEL_MOVE));
        DrawableGizmoPrimitives gizmos = new DrawableGizmoPrimitives();
        if (sel != 0 && tag.getListOrEmpty(BuildingWandItem.K_SMART).size() > 0) {
            addSmartSelection(gizmos, tag.getListOrEmpty(BuildingWandItem.K_SMART), color);
        }
        addSelectionBox(gizmos, pos1, pos2, color);
        CameraRenderState cam = context.levelState().cameraRenderState;
        gizmos.render(context.poseStack(), context.bufferSource(), cam, cam.projectionMatrix);
    }

    private static void addSmartSelection(DrawableGizmoPrimitives g, ListTag list, int color) {
        int limit = Math.min(list.size(), 2000);
        for (int i = 0; i < limit; i++) {
            CompoundTag e = list.getCompoundOrEmpty(i);
            BlockPos pos = new BlockPos(e.getIntOr("x", 0), e.getIntOr("y", 0), e.getIntOr("z", 0));
            addSelectionBox(g, pos, pos, color);
        }
    }

    private static void addSelectionBox(DrawableGizmoPrimitives g, BlockPos p1, BlockPos p2, int c) {
        double x0=Math.min(p1.getX(),p2.getX()),y0=Math.min(p1.getY(),p2.getY()),z0=Math.min(p1.getZ(),p2.getZ());
        double x1=Math.max(p1.getX(),p2.getX())+1, y1=Math.max(p1.getY(),p2.getY())+1, z1=Math.max(p1.getZ(),p2.getZ())+1;
        float w=2.5f;
        Vec3 p000=new Vec3(x0,y0,z0),p100=new Vec3(x1,y0,z0),p010=new Vec3(x0,y1,z0),p110=new Vec3(x1,y1,z0),
             p001=new Vec3(x0,y0,z1),p101=new Vec3(x1,y0,z1),p011=new Vec3(x0,y1,z1),p111=new Vec3(x1,y1,z1);
        g.addLine(p000,p100,c,w);g.addLine(p100,p101,c,w);g.addLine(p101,p001,c,w);g.addLine(p001,p000,c,w);
        g.addLine(p010,p110,c,w);g.addLine(p110,p111,c,w);g.addLine(p111,p011,c,w);g.addLine(p011,p010,c,w);
        g.addLine(p000,p010,c,w);g.addLine(p100,p110,c,w);g.addLine(p101,p111,c,w);g.addLine(p001,p011,c,w);
    }
}
