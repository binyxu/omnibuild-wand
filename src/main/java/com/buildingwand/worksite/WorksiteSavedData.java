package com.buildingwand.worksite;

import com.mojang.serialization.Codec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.Identifier;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.LinkedHashMap;
import java.util.Map;

public class WorksiteSavedData extends SavedData {

    public static final Codec<WorksiteSavedData> CODEC =
            CompoundTag.CODEC.xmap(WorksiteSavedData::load, WorksiteSavedData::save);

    public static final SavedDataType<WorksiteSavedData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath("buildingwand", "worksites"),
            WorksiteSavedData::new,
            CODEC,
            DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

    private final Map<Long, WorksiteBlockEntity> worksites = new LinkedHashMap<>();

    public Map<Long, WorksiteBlockEntity> worksites() {
        return worksites;
    }

    private CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (Map.Entry<Long, WorksiteBlockEntity> entry : worksites.entrySet()) {
            CompoundTag e = entry.getValue().save();
            e.putLong("pos", entry.getKey());
            list.add(e);
        }
        tag.put("worksites", list);
        return tag;
    }

    private static WorksiteSavedData load(CompoundTag tag) {
        WorksiteSavedData data = new WorksiteSavedData();
        ListTag list = tag.getListOrEmpty("worksites");
        for (int i = 0; i < list.size(); i++) {
            CompoundTag e = list.getCompoundOrEmpty(i);
            long pos = e.getLongOr("pos", 0L);
            data.worksites.put(pos, WorksiteBlockEntity.load(e));
        }
        return data;
    }
}
