package com.buildingwand.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** C2S: player pressed a worksite button. action 0=deposit-all, 1=start-build, 2=replace-material,
 *  3=pull-from-linked-chests, 4=withdraw-deposited. */
public record WorksiteActionPayload(BlockPos pos, int action, String materialId) implements CustomPacketPayload {

    public static final Type<WorksiteActionPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("buildingwand", "worksite_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, WorksiteActionPayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeBlockPos(p.pos());
                buf.writeVarInt(p.action());
                buf.writeUtf(p.materialId(), 256);
            },
            buf -> new WorksiteActionPayload(buf.readBlockPos(), buf.readVarInt(), buf.readUtf(256))
    );

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
