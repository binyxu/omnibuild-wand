package com.buildingwand.worksite;

import com.buildingwand.network.WorksiteClearPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.*;

public class WorksiteManager {

    private static int tickCounter = 0;

    private static WorksiteSavedData data(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(WorksiteSavedData.TYPE);
    }

    private static WorksiteSavedData data(ServerLevel level) {
        return data(level.getServer());
    }

    public static void add(ServerLevel level, BlockPos pos, WorksiteBlockEntity info) {
        WorksiteSavedData data = data(level);
        data.worksites().put(pos.asLong(), info);
        data.setDirty();
    }

    public static WorksiteBlockEntity get(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel)) return null;
        return data(serverLevel).worksites().get(pos.asLong());
    }

    public static void markDirty(ServerLevel level) {
        data(level).setDirty();
    }

    public static void remove(ServerLevel level, BlockPos pos) {
        WorksiteSavedData data = data(level);
        data.worksites().remove(pos.asLong());
        data.setDirty();
    }

    public static boolean cancel(ServerLevel level, BlockPos pos) {
        WorksiteSavedData data = data(level);
        WorksiteBlockEntity info = data.worksites().remove(pos.asLong());
        if (info == null) return false;
        info.cancel(level, pos);
        data.setDirty();
        WorksiteClearPayload pkt = new WorksiteClearPayload();
        for (var p : level.players()) ServerPlayNetworking.send(p, pkt);
        return true;
    }

    /** Called every server tick via {@code ServerTickEvents.END_SERVER_TICK}. */
    public static void tick(MinecraftServer server) {
        WorksiteSavedData data = data(server);
        Map<Long, WorksiteBlockEntity> worksites = data.worksites();
        if (worksites.isEmpty()) return;
        tickCounter++;
        if (tickCounter < 20) return;
        tickCounter = 0;

        List<Long> done = new ArrayList<>();
        boolean dirty = false;
        for (Map.Entry<Long, WorksiteBlockEntity> entry : worksites.entrySet()) {
            WorksiteBlockEntity info = entry.getValue();
            if (!info.isBuilding()) continue;

            BlockPos postPos = BlockPos.of(entry.getKey());
            ResourceKey<Level> dim = info.getDimension();
            ServerLevel level = server.getLevel(dim);
            if (level == null) continue;

            // Top up from linked supply containers before laying this second's blocks.
            if (info.hasSupply() && info.pullFromContainers(level, postPos) > 0) dirty = true;

            boolean finished = false;
            int blocksThisSecond = info.blocksPerSecond();
            for (int i = 0; i < blocksThisSecond; i++) {
                finished = info.placeNextBlock(level, postPos);
                dirty = true;
                if (finished) break;
            }
            if (finished) {
                info.settle(level, postPos);
                WorksiteClearPayload pkt = new WorksiteClearPayload();
                for (var p : level.players()) ServerPlayNetworking.send(p, pkt);
                info.cleanup(level, postPos);
                level.removeBlock(postPos, false);
                done.add(entry.getKey());
            }
        }
        done.forEach(worksites::remove);
        if (dirty || !done.isEmpty()) data.setDirty();
    }
}
