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
 * S2C: server sends the copy/move clipboard to the client so it can render a
 * ghost preview, used when the clip is too large to persist in the wand item
 * NBT (which would blow the 2 MB inventory-sync limit). Encoded as
 * gzip-compressed NBT to avoid the readNbt() hard limit, exactly like
 * {@link WandLoadPayload}.
 */
public record WandClipPayload(ListTag clipboard) implements CustomPacketPayload {

    public static final Type<WandClipPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("buildingwand", "wand_clip"));

    public static final StreamCodec<RegistryFriendlyByteBuf, WandClipPayload> CODEC = StreamCodec.of(
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
                    throw new RuntimeException("Failed to encode WandClipPayload", e);
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
                    return new WandClipPayload(data instanceof ListTag lt ? lt : new ListTag());
                } catch (Exception e) {
                    throw new RuntimeException("Failed to decode WandClipPayload", e);
                }
            }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
