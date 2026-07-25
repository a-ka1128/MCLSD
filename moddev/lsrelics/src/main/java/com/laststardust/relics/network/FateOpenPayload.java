package com.laststardust.relics.network;

import com.laststardust.relics.LSRelics;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// 서버 → 클라: 가호 선택 화면을 열어라.
// current = 이미 선택한 가호 키(없으면 빈 문자열). 화면이 "이미 선택함" 상태를 표시하는 데 쓴다.
public record FateOpenPayload(String current) implements CustomPacketPayload {

    public static final Type<FateOpenPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(LSRelics.MODID, "fate_open"));

    public static final StreamCodec<FriendlyByteBuf, FateOpenPayload> STREAM_CODEC =
        StreamCodec.composite(ByteBufCodecs.STRING_UTF8, FateOpenPayload::current, FateOpenPayload::new);

    @Override
    public Type<FateOpenPayload> type() {
        return TYPE;
    }
}
