package com.buildingwand.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Client-to-server packet for R (rotate) and M (mirror) key presses.
 * action: 0 = cycle rotation, 1 = cycle mirror
 */
public record WandKeyPayload(int action) implements CustomPacketPayload {

    public static final Type<WandKeyPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("buildingwand", "wand_key"));

    public static final StreamCodec<RegistryFriendlyByteBuf, WandKeyPayload> CODEC = StreamCodec.of(
            (buf, p) -> buf.writeVarInt(p.action()),
            buf -> new WandKeyPayload(buf.readVarInt())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
