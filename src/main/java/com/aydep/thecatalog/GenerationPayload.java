package com.aydep.thecatalog;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server to client: the ore generation data from {@link OreGeneration#scan}, as JSON. */
public record GenerationPayload(String json) implements CustomPacketPayload {
    public static final Type<GenerationPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(TheCatalog.MODID, "generation"));
    public static final StreamCodec<ByteBuf, GenerationPayload> STREAM_CODEC =
            ByteBufCodecs.stringUtf8(1 << 22).map(GenerationPayload::new, GenerationPayload::json);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
