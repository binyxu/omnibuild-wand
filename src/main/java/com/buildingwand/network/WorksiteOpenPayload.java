package com.buildingwand.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * S2C: open the worksite management screen on the client.
 * Carries the material data so the screen can display it immediately.
 */
public record WorksiteOpenPayload(
        BlockPos pos,
        Map<String, Integer> needed,
        Map<String, Integer> deposited,
        boolean building
) implements CustomPacketPayload {

    public static final Type<WorksiteOpenPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("buildingwand", "worksite_open"));

    public static final StreamCodec<RegistryFriendlyByteBuf, WorksiteOpenPayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeBlockPos(p.pos());
                writeMap(buf, p.needed());
                writeMap(buf, p.deposited());
                buf.writeBoolean(p.building());
            },
            buf -> {
                BlockPos pos = buf.readBlockPos();
                Map<String, Integer> needed    = readMap(buf);
                Map<String, Integer> deposited = readMap(buf);
                boolean building = buf.readBoolean();
                return new WorksiteOpenPayload(pos, needed, deposited, building);
            }
    );

    private static void writeMap(RegistryFriendlyByteBuf buf, Map<String, Integer> map) {
        buf.writeVarInt(map.size());
        for (Map.Entry<String, Integer> e : map.entrySet()) {
            buf.writeUtf(e.getKey());
            buf.writeVarInt(e.getValue());
        }
    }

    private static Map<String, Integer> readMap(RegistryFriendlyByteBuf buf) {
        int size = buf.readVarInt();
        Map<String, Integer> map = new LinkedHashMap<>();
        for (int i = 0; i < size; i++) map.put(buf.readUtf(), buf.readVarInt());
        return map;
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
