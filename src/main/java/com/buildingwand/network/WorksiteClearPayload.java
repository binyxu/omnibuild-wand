package com.buildingwand.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** S2C: clear worksite ghost blocks on the client (construction complete or cancelled). */
public record WorksiteClearPayload() implements CustomPacketPayload {

    public static final Type<WorksiteClearPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("buildingwand", "worksite_clear"));

    public static final StreamCodec<RegistryFriendlyByteBuf, WorksiteClearPayload> CODEC = StreamCodec.of(
            (buf, p) -> {},
            buf -> new WorksiteClearPayload()
    );

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
