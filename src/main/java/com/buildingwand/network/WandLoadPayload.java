package com.buildingwand.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

/**
 * C2S: client parses the schematic and sends the clipboard to the server.
 * Encoded as gzip-compressed NBT to avoid the 2 MB readNbt() hard limit.
 */
public record WandLoadPayload(ListTag clipboard) implements CustomPacketPayload {

    public static final Type<WandLoadPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("buildingwand", "wand_load"));

    public static final StreamCodec<RegistryFriendlyByteBuf, WandLoadPayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                try {
                    CompoundTag wrapper = new CompoundTag();
                    wrapper.put("d", p.clipboard());
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    NbtIo.writeCompressed(wrapper, baos);
                    byte[] bytes = baos.toByteArray();
                    buf.writeVarInt(bytes.length);
                    buf.writeBytes(bytes);
                } catch (Exception e) {
                    throw new RuntimeException("Failed to encode WandLoadPayload", e);
                }
            },
            buf -> {
                try {
                    int len = buf.readVarInt();
                    byte[] bytes = new byte[len];
                    buf.readBytes(bytes);
                    CompoundTag wrapper = NbtIo.readCompressed(
                            new ByteArrayInputStream(bytes), NbtAccounter.unlimitedHeap());
                    Tag data = wrapper.get("d");
                    return new WandLoadPayload(data instanceof ListTag lt ? lt : new ListTag());
                } catch (Exception e) {
                    throw new RuntimeException("Failed to decode WandLoadPayload", e);
                }
            }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
