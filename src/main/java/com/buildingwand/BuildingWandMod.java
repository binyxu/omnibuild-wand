package com.buildingwand;

import com.buildingwand.item.BuildingWandItem;
import com.buildingwand.network.WandClipPayload;
import com.buildingwand.network.WandKeyPayload;
import com.buildingwand.network.WandLoadPayload;
import com.buildingwand.network.WorksiteActionPayload;
import com.buildingwand.network.WorksiteClearPayload;
import com.buildingwand.network.WorksiteOpenPayload;
import com.buildingwand.worksite.WorksiteBlockEntity;
import com.buildingwand.worksite.WorksiteManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BuildingWandMod implements ModInitializer {

    public static final String MOD_ID = "buildingwand";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        BuildingWandConfig.load();
        ModItems.register();

        PayloadTypeRegistry.serverboundPlay().register(WandKeyPayload.TYPE, WandKeyPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(WorksiteActionPayload.TYPE, WorksiteActionPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(WandLoadPayload.TYPE, WandLoadPayload.CODEC);

        PayloadTypeRegistry.clientboundPlay().register(WorksiteOpenPayload.TYPE, WorksiteOpenPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(WorksiteClearPayload.TYPE, WorksiteClearPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(WandClipPayload.TYPE, WandClipPayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(WandKeyPayload.TYPE, (payload, context) ->
                context.server().execute(() -> {
                    ServerPlayer player = context.player();
                    ItemStack wand = BuildingWandItem.getHeldWand(player);
                    if (wand == null) return;
                    if (payload.action() == 0) BuildingWandItem.serverRotate(wand, player);
                    else if (payload.action() == 1) BuildingWandItem.serverMirror(wand, player);
                    else if (payload.action() == 2) BuildingWandItem.serverToggleMode(wand, player);
                    else if (payload.action() == 3) BuildingWandItem.serverToggleSelectionMode(wand, player);
                    else if (payload.action() == 4) BuildingWandItem.serverCancelSelection(wand, player);
                }));

        ServerPlayNetworking.registerGlobalReceiver(WorksiteActionPayload.TYPE, (payload, context) ->
                context.server().execute(() -> {
                    ServerPlayer player = context.player();
                    if (!(player.level() instanceof ServerLevel serverLevel)) return;

                    BlockPos pos = payload.pos();
                    WorksiteBlockEntity info = WorksiteManager.get(serverLevel, pos);
                    if (info == null) return;

                    if (payload.action() == 0) {
                        info.depositFromPlayer(player, player.isCreative(), serverLevel, pos);
                        info.refreshDecoration(serverLevel, pos);
                        WorksiteManager.markDirty(serverLevel);
                    } else if (payload.action() == 1) {
                        boolean started = info.startBuilding(player, serverLevel, pos);
                        if (started) {
                            WorksiteManager.markDirty(serverLevel);
                            player.sendSystemMessage(Component.translatable("message.buildingwand.worksite.started"));
                        }
                    } else if (payload.action() == 2) {
                        ItemStack held = player.getMainHandItem();
                        if (!(held.getItem() instanceof BlockItem)) held = player.getOffhandItem();
                        if (!(held.getItem() instanceof BlockItem blockItem)) {
                            player.sendSystemMessage(Component.translatable("message.buildingwand.worksite.replace_hold"));
                        } else if (info.isBuilding()) {
                            player.sendSystemMessage(Component.translatable("message.buildingwand.worksite.replace_locked"));
                        } else if (info.replaceMaterial(serverLevel, pos, payload.materialId(), blockItem.getBlock().defaultBlockState())) {
                            info.refreshDecoration(serverLevel, pos);
                            WorksiteManager.markDirty(serverLevel);
                            player.sendSystemMessage(Component.translatable("message.buildingwand.worksite.replaced"));
                        } else {
                            player.sendSystemMessage(Component.translatable("message.buildingwand.worksite.replace_failed"));
                        }
                    }

                    ServerPlayNetworking.send(player, new WorksiteOpenPayload(
                            pos, info.getNeeded(), info.getDeposited(), info.isBuilding()));
                }));

        ServerPlayNetworking.registerGlobalReceiver(WandLoadPayload.TYPE, (payload, context) ->
                context.server().execute(() -> {
                    ServerPlayer player = context.player();
                    ListTag clipList = payload.clipboard();
                    if (clipList.isEmpty()) {
                        player.sendSystemMessage(Component.translatable("message.buildingwand.schematic.clipboard_empty"));
                        return;
                    }

                    ItemStack wand = BuildingWandItem.getHeldWand(player);
                    if (wand == null) {
                        player.sendSystemMessage(Component.translatable("message.buildingwand.wand_required"));
                        return;
                    }

                    CompoundTag tag = new CompoundTag();
                    tag.putInt(BuildingWandItem.K_MODE, 1);
                    tag.putInt(BuildingWandItem.K_STEP, 2);
                    tag.putInt(BuildingWandItem.K_ROT, 0);
                    tag.putInt(BuildingWandItem.K_MIR, 0);
                    tag.putInt(BuildingWandItem.K_SCHEMATIC, 1);
                    BuildingWandItem.saveTag(wand, tag);
                    BuildingWandItem.setServerClipboard(player, clipList);
                    player.sendSystemMessage(Component.translatable(
                            "message.buildingwand.schematic.loaded", clipList.size()));
                }));

        ServerTickEvents.END_SERVER_TICK.register(WorksiteManager::tick);

        LOGGER.info("Building Wand loaded.");
    }
}
